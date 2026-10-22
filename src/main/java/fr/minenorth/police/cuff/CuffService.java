package fr.minenorth.police.cuff;

import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.PoliceService;
import fr.minenorth.police.compat.Mts;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.item.PoliceTool;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menottes, escorte et sac de jute. Tout est décidé ici, côté serveur : le client ne fait que l'affichage (pose, menottes visibles, toile de jute).
 * Un OP est un joueur comme un autre : aucune exemption (les droits viennent du grade police, jamais des permissions Minecraft).
 * État mémorisé dans les données persistantes du joueur : les menottes survivent à la déconnexion et au redémarrage (sauf si la config les libère).
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID)
public final class CuffService {
    private CuffService() {}

    public static final int POLICE = 0, ARTISANAL = 1;
    private static final String TAG = "mnpolice_cuff";
    /** Repère posé sur le sac de jute porté (emplacement casque) : il ne peut pas être retiré par le joueur et se restitue à la libération. */
    public static final String MARK = "mnpolice_sac_porte";
    private static final UUID SPEED_ID = UUID.fromString("5b0f1f4e-8d57-4c63-9f4b-0c1b1d2a7e31");

    /** Menotté et/ou encagoulé. */
    public static final class State {
        public UUID captor = new UUID(0, 0);
        public String captorName = "";
        public int type;
        public boolean cuffed;
        public long cuffStart;
        public boolean hooded;
        public long hoodStart;
        /** Casque que portait la victime avant le sac (NBT vide = aucun). */
        public CompoundTag helmet = new CompoundTag();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("captor", captor); t.putString("name", captorName); t.putInt("type", type);
            t.putBoolean("cuffed", cuffed); t.putLong("cs", cuffStart); t.putBoolean("hooded", hooded); t.putLong("hs", hoodStart);
            t.put("helmet", helmet);
            return t;
        }

        static State load(CompoundTag t) {
            State s = new State();
            if (t.hasUUID("captor")) s.captor = t.getUUID("captor");
            s.captorName = t.getString("name"); s.type = t.getInt("type");
            s.cuffed = t.getBoolean("cuffed"); s.cuffStart = t.getLong("cs"); s.hooded = t.getBoolean("hooded"); s.hoodStart = t.getLong("hs");
            s.helmet = t.getCompound("helmet");
            return s;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Captif -> ravisseur qui l'escorte. */
    private static final Map<UUID, UUID> ESCORT = new HashMap<>();
    /** Captif -> véhicule MTS dans lequel il est installé. */
    private static final Map<UUID, UUID> SEAT = new HashMap<>();
    private record Pending(UUID target, int type, long startTick, Vec3 origin) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> STUCK = new HashMap<>();

    // ------------------------------------------------------------------ requêtes
    @Nullable
    public static State state(UUID id) { return STATES.get(id); }

    public static boolean isCuffed(UUID id) { State s = STATES.get(id); return s != null && s.cuffed; }

    public static boolean isHooded(UUID id) { State s = STATES.get(id); return s != null && s.hooded; }

    public static boolean isHoodMark(ItemStack st) {
        return !st.isEmpty() && st.is(ModItems.SAC_JUTE.get()) && st.hasTag() && st.getTag().getBoolean(MARK);
    }

    private static boolean policeRank(ServerPlayer p) { return PoliceService.rank(p) >= 0; }

    private static ServerPlayer online(MinecraftServer s, UUID id) { return s.getPlayerList().getPlayer(id); }

    private static String name(MinecraftServer s, UUID id) { return PoliceService.display(s, id); }

    private static void say(ServerPlayer p, String msg) { if (p != null) p.displayClientMessage(Component.literal(msg), true); }

    private static void tell(ServerPlayer p, String msg) { if (p != null) p.sendSystemMessage(Component.literal(msg)); }

    // ------------------------------------------------------------------ liens avec les autres mods (réflexion)
    private static Method unconscious;
    private static boolean unconsciousLooked;

    /** Coma / inconscient (mod Secours). */
    public static boolean isUnconscious(ServerPlayer p) {
        if (!unconsciousLooked) {
            unconsciousLooked = true;
            try { unconscious = Class.forName("fr.minenorth.secours.api.SecoursApi").getMethod("isUnconscious", ServerPlayer.class); }
            catch (Throwable ignored) { unconscious = null; }
        }
        if (unconscious == null) return false;
        try { return Boolean.TRUE.equals(unconscious.invoke(null, p)); } catch (Throwable t) { return false; }
    }

    private static Field emoteActive;
    private static boolean emoteLooked;
    private static Method emoteGetter;

    /** Nom de l'emote en cours (mod Emotes : « BRAS_EN_L_AIR » = Se rendre, « PRIER » = à genoux...), ou "". */
    public static String activeEmote(ServerPlayer p) {
        if (!emoteLooked) {
            emoteLooked = true;
            try {
                emoteActive = Class.forName("fr.minenorth.emotes.EmoteServer").getDeclaredField("ACTIVE");
                emoteActive.setAccessible(true);
            } catch (Throwable t) { emoteActive = null; }
        }
        if (emoteActive == null) return "";
        try {
            Object active = ((Map<?, ?>) emoteActive.get(null)).get(p.getUUID());
            if (active == null) return "";
            if (emoteGetter == null) { emoteGetter = active.getClass().getDeclaredMethod("emote"); emoteGetter.setAccessible(true); }
            Object e = emoteGetter.invoke(active);
            return e instanceof Enum<?> en ? en.name() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Mains en l'air, à genoux, assis ou inconscient : la cible est menottable sur-le-champ. */
    public static boolean surrendered(ServerPlayer p) {
        if (isUnconscious(p)) return true;
        String e = activeEmote(p);
        return !e.isEmpty() && PoliceConfig.get().menottes_emotes_reddition.contains(e);
    }

    private static boolean armed(ServerPlayer p) {
        PoliceConfig cfg = PoliceConfig.get();
        return PoliceTool.matchesAny(p.getMainHandItem(), cfg.detecteur_objets) || PoliceTool.matchesAny(p.getOffhandItem(), cfg.detecteur_objets);
    }

    // ------------------------------------------------------------------ interactions (clic droit sur un joueur)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer actor)) return;
        if (isCuffed(actor.getUUID())) { e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL); return; }
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !PoliceConfig.get().menottes_actives || actor.isSpectator()) return;
        boolean done = false;
        if (e.getTarget() instanceof ServerPlayer target) {
            done = onPlayer(actor, target);
        } else if (Mts.isMts(e.getTarget())) {
            if (escortedBy(actor) != null) {
                Entity veh = e.getTarget();
                if (!Mts.isBuilder(veh)) veh = nearestBuilder(actor);
                done = veh != null && board(actor, veh);
            } else {
                // Menottes, clé ou main vide en accroupi sur le véhicule : faire descendre le prisonnier installé à l'intérieur.
                Item held = actor.getMainHandItem().getItem();
                boolean tool = held == ModItems.MENOTTES_POLICE.get() || held == ModItems.MENOTTES_ARTISANALES.get() || held == ModItems.CLE_MENOTTES.get()
                        || (actor.getMainHandItem().isEmpty() && actor.isShiftKeyDown());
                done = tool && dismount(actor);
            }
        }
        if (done) { e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS); }
    }

    @Nullable
    private static Entity nearestBuilder(ServerPlayer p) {
        Entity best = null; double bd = 36;
        for (Entity x : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(6), Mts::isBuilder)) {
            double d = x.distanceToSqr(p);
            if (d < bd) { bd = d; best = x; }
        }
        return best;
    }

    private static boolean onPlayer(ServerPlayer actor, ServerPlayer target) {
        if (target == actor || target.isSpectator() || !target.isAlive()) return false;
        ItemStack st = actor.getMainHandItem();
        Item it = st.getItem();
        State ts = STATES.get(target.getUUID());
        boolean cuffed = ts != null && ts.cuffed;
        if (it == ModItems.MENOTTES_POLICE.get()) return cuffAction(actor, target, st, POLICE, cuffed ? ts : null);
        if (it == ModItems.MENOTTES_ARTISANALES.get()) return cuffAction(actor, target, st, ARTISANAL, cuffed ? ts : null);
        if (it == ModItems.CLE_MENOTTES.get()) return keyAction(actor, target, ts);
        if (it == ModItems.SAC_JUTE.get()) return hoodAction(actor, target, st, ts);
        if (!cuffed) return ts != null && ts.hooded && st.isEmpty() && unhood(actor, target, ts);
        if (actor.isShiftKeyDown()) return searchAction(actor, target, ts);
        if (st.isEmpty()) return escortAction(actor, target, ts);
        return false;
    }

    private static boolean near(ServerPlayer a, ServerPlayer b, double range) {
        return a.level() == b.level() && a.distanceToSqr(b) <= range * range;
    }

    /** Peut-il agir sur ce captif (le libérer, l'escorter) : tout policier, ou le ravisseur lui-même. */
    private static boolean handler(ServerPlayer actor, State ts) {
        return actor.getUUID().equals(ts.captor) || policeRank(actor) || (!PoliceConfig.get().menottes_reservees_police && ts.type == POLICE);
    }

    // ------------------------------------------------------------------ menotter
    private static boolean cuffAction(ServerPlayer actor, ServerPlayer target, ItemStack st, int type, @Nullable State existing) {
        PoliceConfig cfg = PoliceConfig.get();
        if (existing != null) {
            // Menottes artisanales en main sur son propre captif : on les retire.
            if (type == ARTISANAL && existing.type == ARTISANAL && actor.getUUID().equals(existing.captor) && near(actor, target, cfg.menottes_portee + 0.5)) {
                release(target, actor, "retirées par " + name(actor.server, actor.getUUID()), true);
                return true;
            }
            say(actor, "§7" + name(actor.server, target.getUUID()) + " est déjà menotté.");
            return true;
        }
        if (type == POLICE && cfg.menottes_reservees_police && !policeRank(actor)) { say(actor, "§cMenottes de police : réservées aux policiers."); return true; }
        if (type == ARTISANAL && !cfg.menottes_artisanales_actives) { say(actor, "§cLes menottes artisanales sont désactivées sur ce serveur."); return true; }
        if (!near(actor, target, cfg.menottes_portee)) { say(actor, "§cApprochez-vous (moins de " + (int) cfg.menottes_portee + " blocs)."); return true; }
        if (PENDING.containsKey(actor.getUUID())) return true;
        for (Pending p : PENDING.values()) if (p.target.equals(target.getUUID())) { say(actor, "§7Quelqu'un essaie déjà de le menotter."); return true; }
        if (surrendered(target) || cfg.menottes_pose_secondes <= 0) {
            finishCuff(actor, target, st, type);
            return true;
        }
        if (cfg.menottes_refuser_si_arme && armed(target) && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            say(actor, "§cIl tient une arme : faites-le d'abord se rendre, ou ralentissez-le (taser).");
            return true;
        }
        PENDING.put(actor.getUUID(), new Pending(target.getUUID(), type, actor.server.getTickCount(), target.position()));
        say(actor, "§eVous menottez " + name(actor.server, target.getUUID()) + "... (ne bougez pas)");
        tell(target, "§c" + name(actor.server, actor.getUUID()) + " essaie de vous menotter ! Reculez ou débattez-vous !");
        return true;
    }

    private static void finishCuff(ServerPlayer actor, ServerPlayer target, ItemStack st, int type) {
        if (isCuffed(target.getUUID())) return;
        State s = STATES.get(target.getUUID());
        if (s == null) { s = new State(); STATES.put(target.getUUID(), s); }
        s.captor = actor.getUUID(); s.captorName = name(actor.server, actor.getUUID()); s.type = type;
        s.cuffed = true; s.cuffStart = System.currentTimeMillis();
        if (!st.isEmpty() && st.getItem() == (type == POLICE ? ModItems.MENOTTES_POLICE.get() : ModItems.MENOTTES_ARTISANALES.get())) st.shrink(1);
        applyCuffs(target);
        persist(target);
        target.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        target.level().playSound(null, target.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.6f);
        say(actor, "§a" + name(actor.server, target.getUUID()) + " est menotté.");
        tell(target, "§cVous êtes menotté par " + s.captorName + ". Vous ne pouvez plus attaquer, courir ni utiliser d'objets.");
        broadcast(actor.server);
    }

    /** Applique les effets des menottes : lenteur, plus de sprint, plus d'objet en cours d'utilisation, plus de menu ouvert. */
    private static void applyCuffs(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) {
            a.removeModifier(SPEED_ID);
            double pct = PoliceConfig.get().menottes_ralentissement_pourcent / 100.0;
            if (pct > 0) a.addTransientModifier(new AttributeModifier(SPEED_ID, "menottes", -pct, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        p.setSprinting(false);
        p.stopUsingItem();
        if (p.containerMenu != p.inventoryMenu) p.closeContainer();
        disarm(p);
    }

    private static void persist(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) p.getPersistentData().remove(TAG);
        else p.getPersistentData().put(TAG, s.save());
    }

    // ------------------------------------------------------------------ libérer
    private static boolean keyAction(ServerPlayer actor, ServerPlayer target, @Nullable State ts) {
        if (ts != null && ts.hooded && !ts.cuffed) return unhood(actor, target, ts);
        if (ts == null || !ts.cuffed) { say(actor, "§7Ce joueur n'est pas menotté."); return true; }
        if (!near(actor, target, PoliceConfig.get().menottes_portee + 0.5)) { say(actor, "§cApprochez-vous."); return true; }
        if (ts.hooded && handler(actor, ts)) return unhood(actor, target, ts);
        if (!handler(actor, ts)) { say(actor, "§cVous n'avez pas le droit de libérer ce joueur."); return true; }
        release(target, actor, "libérées par " + name(actor.server, actor.getUUID()), true);
        return true;
    }

    /** Libère un joueur (menottes et sac). by = qui récupère les menottes (sinon elles tombent au sol). */
    public static boolean release(ServerPlayer target, @Nullable ServerPlayer by, String why, boolean giveBack) {
        State s = STATES.get(target.getUUID());
        if (s == null) return false;
        MinecraftServer srv = target.server;
        boolean wasCuffed = s.cuffed;
        if (s.hooded) removeHood(target, s, null, true);
        STATES.remove(target.getUUID());
        ESCORT.remove(target.getUUID());
        PENDING.values().removeIf(p -> p.target.equals(target.getUUID()));
        SEAT.remove(target.getUUID());
        if (target.isPassenger()) { PoliceService.releaseHold(srv, target.getUUID()); if (target.isPassenger()) Mts.unseat(target); }
        AttributeInstance a = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(SPEED_ID);
        target.getPersistentData().remove(TAG);
        if (wasCuffed) {
            Item item = s.type == POLICE ? ModItems.MENOTTES_POLICE.get() : ModItems.MENOTTES_ARTISANALES.get();
            giveOrDrop(giveBack ? by : null, target, new ItemStack(item));
            target.level().playSound(null, target.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.8f, 1.5f);
            tell(target, "§aVos menottes sont retirées" + (why.isEmpty() ? "." : " (" + why + ")."));
            if (by != null && by != target) say(by, "§a" + name(srv, target.getUUID()) + " est libéré.");
        }
        broadcast(srv);
        return true;
    }

    /** Libération à distance depuis la tablette ou par le serveur (fin du délai, mort, détention...). */
    public static boolean release(MinecraftServer s, UUID id, String why) {
        ServerPlayer p = online(s, id);
        if (p == null) return false;
        return release(p, null, why, false);
    }

    private static void giveOrDrop(@Nullable ServerPlayer to, ServerPlayer at, ItemStack st) {
        if (st.isEmpty()) return;
        if (to != null && to.isAlive() && to.getInventory().add(st)) return;
        at.drop(st, false);
    }

    // ------------------------------------------------------------------ sac de jute
    private static boolean hoodAction(ServerPlayer actor, ServerPlayer target, ItemStack st, @Nullable State ts) {
        PoliceConfig cfg = PoliceConfig.get();
        if (!near(actor, target, cfg.menottes_portee + 0.5)) { say(actor, "§cApprochez-vous."); return true; }
        if (ts != null && ts.hooded) return unhood(actor, target, ts);
        boolean ok = (ts != null && ts.cuffed) || surrendered(target);
        if (!ok) { say(actor, "§7Le joueur doit être menotté, à genoux ou inconscient."); return true; }
        if (ts == null) { ts = new State(); ts.captor = actor.getUUID(); ts.captorName = name(actor.server, actor.getUUID()); STATES.put(target.getUUID(), ts); }
        ts.hooded = true; ts.hoodStart = System.currentTimeMillis();
        ts.helmet = target.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? new CompoundTag() : target.getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag());
        ItemStack mark = new ItemStack(ModItems.SAC_JUTE.get());
        mark.getOrCreateTag().putBoolean(MARK, true);
        target.setItemSlot(EquipmentSlot.HEAD, mark);
        st.shrink(1);
        persist(target);
        target.level().playSound(null, target.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        tell(target, "§cUn sac de jute vous est enfilé sur la tête. Vous n'y voyez presque plus.");
        say(actor, "§aVous avez mis le sac sur la tête de " + name(actor.server, target.getUUID()) + ".");
        broadcast(actor.server);
        return true;
    }

    private static boolean unhood(ServerPlayer actor, ServerPlayer target, State ts) {
        if (!near(actor, target, PoliceConfig.get().menottes_portee + 0.5)) { say(actor, "§cApprochez-vous."); return true; }
        removeHood(target, ts, actor, false);
        if (!ts.cuffed) { STATES.remove(target.getUUID()); persist(target); }
        say(actor, "§aVous retirez le sac de la tête de " + name(actor.server, target.getUUID()) + ".");
        broadcast(actor.server);
        return true;
    }

    /** Retire le sac : restitue le casque, rend (ou lâche) un sac. by = celui qui le récupère. */
    private static void removeHood(ServerPlayer target, State s, @Nullable ServerPlayer by, boolean timeout) {
        if (!s.hooded) return;
        s.hooded = false;
        ItemStack back = s.helmet.isEmpty() ? ItemStack.EMPTY : ItemStack.of(s.helmet);
        s.helmet = new CompoundTag();
        if (isHoodMark(target.getItemBySlot(EquipmentSlot.HEAD))) target.setItemSlot(EquipmentSlot.HEAD, back);
        else giveOrDrop(null, target, back);
        giveOrDrop(timeout ? null : by, target, new ItemStack(ModItems.SAC_JUTE.get()));
        tell(target, "§aLe sac de jute est retiré de votre tête.");
        persist(target);
    }

    // ------------------------------------------------------------------ fouille
    private static boolean searchAction(ServerPlayer actor, ServerPlayer target, State ts) {
        PoliceConfig cfg = PoliceConfig.get();
        boolean police = policeRank(actor);
        boolean captor = cfg.fouille_ravisseurs && ts.type == ARTISANAL && actor.getUUID().equals(ts.captor);
        if (!police && !captor) { say(actor, "§cSeuls les policiers (ou le ravisseur, avec des menottes artisanales) peuvent fouiller."); return true; }
        if (!near(actor, target, cfg.fouille_portee)) { say(actor, "§cApprochez-vous (moins de " + (int) cfg.fouille_portee + " blocs)."); return true; }
        SearchMenu.open(actor, target, police);
        return true;
    }

    // ------------------------------------------------------------------ escorte
    @Nullable
    private static UUID escortedBy(ServerPlayer actor) {
        for (Map.Entry<UUID, UUID> en : ESCORT.entrySet()) if (en.getValue().equals(actor.getUUID())) return en.getKey();
        return null;
    }

    private static boolean escortAction(ServerPlayer actor, ServerPlayer target, State ts) {
        PoliceConfig cfg = PoliceConfig.get();
        if (!handler(actor, ts)) { say(actor, "§cVous ne pouvez pas escorter ce joueur."); return true; }
        if (actor.getUUID().equals(ESCORT.get(target.getUUID()))) { stopEscort(target, "relâché"); return true; }
        if (!near(actor, target, cfg.menottes_portee + 0.5)) { say(actor, "§cApprochez-vous."); return true; }
        if (target.isPassenger()) { say(actor, "§7Il est installé dans un véhicule (touche « Faire descendre »)."); return true; }
        if (actor.isPassenger()) { say(actor, "§7Descendez de votre véhicule d'abord."); return true; }
        ESCORT.put(target.getUUID(), actor.getUUID());
        say(actor, "§aVous escortez " + name(actor.server, target.getUUID()) + ". Nouveau clic pour le relâcher.");
        tell(target, "§cVous êtes escorté par " + name(actor.server, actor.getUUID()) + ".");
        return true;
    }

    private static void stopEscort(ServerPlayer captive, String why) {
        UUID by = ESCORT.remove(captive.getUUID());
        LAST_POS.remove(captive.getUUID());
        STUCK.remove(captive.getUUID());
        if (by == null) return;
        ServerPlayer e = online(captive.server, by);
        say(e, "§7" + name(captive.server, captive.getUUID()) + " : " + why + ".");
        say(captive, "§7Vous n'êtes plus escorté.");
    }

    /** Fait monter le captif escorté par ce joueur dans le véhicule (place passager libre). */
    private static boolean board(ServerPlayer actor, Entity vehicle) {
        UUID cid = escortedBy(actor);
        ServerPlayer captive = cid == null ? null : online(actor.server, cid);
        if (captive == null) return false;
        if (!Mts.hasFreePassengerSeat(vehicle)) { say(actor, "§cAucune place passager libre dans ce véhicule."); return true; }
        stopEscort(captive, "monté dans un véhicule");
        if (!Mts.seatPassenger(captive, vehicle)) { say(actor, "§cImpossible de l'installer (siège indisponible)."); return true; }
        SEAT.put(captive.getUUID(), vehicle.getUUID());
        tell(captive, "§cVous êtes installé dans le véhicule. Vous ne pouvez pas en sortir.");
        say(actor, "§a" + name(actor.server, captive.getUUID()) + " est installé dans le véhicule.");
        return true;
    }

    /**
     * Touche « Monter / faire descendre le menotté » (paquet) : monte le captif escorté dans le véhicule le plus proche ;
     * sinon fait descendre un captif installé dans un véhicule proche. Rien n'est cru du client : tout est revérifié ici.
     */
    public static void vehicleKey(ServerPlayer actor) {
        if (!PoliceConfig.get().menottes_actives || isCuffed(actor.getUUID()) || actor.isSpectator()) return;
        if (escortedBy(actor) != null) {
            Entity veh = Mts.nearestWithPassengerSeat(actor, 6);
            if (veh == null) { say(actor, "§cAucun véhicule avec une place passager libre à moins de 6 blocs."); return; }
            board(actor, veh);
            return;
        }
        if (!dismount(actor)) say(actor, "§7Personne à faire descendre à proximité.");
    }

    /** Fait descendre le menotté assis le plus proche (10 blocs) que ce joueur a le droit de manipuler. */
    private static boolean dismount(ServerPlayer actor) {
        ServerPlayer best = null; double bd = 100;
        for (ServerPlayer c : actor.server.getPlayerList().getPlayers()) {
            State ts = STATES.get(c.getUUID());
            if (ts == null || !ts.cuffed || !c.isPassenger() || c.level() != actor.level() || !handler(actor, ts)) continue;
            double d = c.distanceToSqr(actor);
            if (d < bd) { bd = d; best = c; }
        }
        if (best == null) return false;
        SEAT.remove(best.getUUID());
        PoliceService.releaseHold(actor.server, best.getUUID());
        if (best.isPassenger()) Mts.unseat(best);
        tell(best, "§eVous êtes sorti du véhicule.");
        say(actor, "§a" + name(actor.server, best.getUUID()) + " descend du véhicule.");
        return true;
    }

    // ------------------------------------------------------------------ interdits au menotté
    private static boolean cuffed(net.minecraft.world.entity.Entity e) { return e instanceof ServerPlayer p && isCuffed(p.getUUID()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(AttackEntityEvent e) { if (cuffed(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attacked(LivingAttackEvent e) { if (cuffed(e.getSource().getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem e) { if (cuffed(e.getEntity())) { e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL); } }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!cuffed(e.getEntity())) return;
        e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        e.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock e) { if (cuffed(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void breakBlock(BlockEvent.BreakEvent e) { if (cuffed(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useItem(LivingEntityUseItemEvent.Start e) { if (cuffed(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void toss(ItemTossEvent e) { if (cuffed(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent e) {
        if (cuffed(e.getEntity())) {
            Vec3 m = e.getEntity().getDeltaMovement();
            e.getEntity().setDeltaMovement(m.x, Math.min(m.y, 0.0), m.z);
            e.getEntity().hurtMarked = true;
        }
    }

    /** Un menotté ne peut ouvrir aucun coffre ni menu (sauf l'inventaire de base, qui ne passe pas par cet évènement). */
    @SubscribeEvent
    public static void container(PlayerContainerEvent.Open e) {
        if (e.getEntity() instanceof ServerPlayer p && isCuffed(p.getUUID())) p.closeContainer();
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        if (!t.isEmpty() && t.contains("cuffed")) {
            State s = State.load(t);
            if (s.cuffed || s.hooded) {
                STATES.put(p.getUUID(), s);
                if (s.cuffed) applyCuffs(p);
            }
        }
        sendTo(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isCuffed(p.getUUID())) { applyCuffs(p); persist(p); sendTo(p); }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PENDING.remove(p.getUUID());
        stopEscortsOf(p);
        State s = STATES.get(p.getUUID());
        if (s != null) {
            if (s.cuffed && PoliceConfig.get().menottes_liberer_deconnexion) release(p, null, "déconnexion", false);
            else if (s.hooded && !s.cuffed) release(p, null, "", false);
            else { persist(p); STATES.remove(p.getUUID()); ESCORT.remove(p.getUUID()); SEAT.remove(p.getUUID()); broadcast(p.server); }
        }
    }

    private static void stopEscortsOf(ServerPlayer escorter) {
        for (UUID c : new ArrayList<>(ESCORT.keySet())) {
            if (!ESCORT.get(c).equals(escorter.getUUID())) continue;
            ServerPlayer cp = online(escorter.server, c);
            if (cp != null) stopEscort(cp, "ravisseur déconnecté"); else ESCORT.remove(c);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.hooded) removeHood(p, s, null, true);       // le casque d'origine est restitué avant les drops, le sac tombe au sol
        if (PoliceConfig.get().menottes_liberer_mort || !s.cuffed) release(p, null, "mort", false);
    }

    /** Détention (garde à vue, prison) : les menottes sautent. */
    public static void onJailed(MinecraftServer s, UUID id) { release(s, id, "détention"); }

    // ------------------------------------------------------------------ boucle
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (STATES.isEmpty() && PENDING.isEmpty()) return;
        int now = s.getTickCount();
        if (!PENDING.isEmpty()) tickPending(s, now);
        if (!ESCORT.isEmpty()) tickEscort(s);
        if (now % 5 == 0 && !SEAT.isEmpty()) tickSeats(s);
        if (now % 3 == 0) tickDisarm(s);
        if (now % 10 == 0) tickEnforce(s);
        if (now % 20 == 0) tickTimers(s);
    }

    private static void tickPending(MinecraftServer s, int now) {
        PoliceConfig cfg = PoliceConfig.get();
        for (Map.Entry<UUID, Pending> en : new ArrayList<>(PENDING.entrySet())) {
            ServerPlayer a = online(s, en.getKey());
            Pending p = en.getValue();
            ServerPlayer t = online(s, p.target);
            Item want = p.type == POLICE ? ModItems.MENOTTES_POLICE.get() : ModItems.MENOTTES_ARTISANALES.get();
            String fail = null;
            if (a == null || t == null || !a.isAlive() || !t.isAlive() || isCuffed(a.getUUID())) fail = "";
            else if (a.getMainHandItem().getItem() != want) fail = "Pose annulée.";
            else if (!near(a, t, cfg.menottes_portee + 0.75)) fail = "Il s'est éloigné : pose échouée.";
            else if (t.position().subtract(p.origin).horizontalDistance() > cfg.menottes_pose_deplacement_max) fail = "Il se débat : pose échouée.";
            if (fail != null) {
                PENDING.remove(en.getKey());
                if (a != null && !fail.isEmpty()) say(a, "§c" + fail);
                if (t != null && a != null && !fail.isEmpty()) say(t, "§aVous avez échappé aux menottes !");
                continue;
            }
            int total = cfg.menottes_pose_secondes * 20;
            int done = now - (int) p.startTick;
            if (done >= total) {
                PENDING.remove(en.getKey());
                finishCuff(a, t, a.getMainHandItem(), p.type);
            } else if (done % 4 == 0) {
                int bars = 12, on = Math.min(bars, done * bars / Math.max(1, total));
                say(a, "§eMenottage §8[§a" + "|".repeat(on) + "§7" + "|".repeat(bars - on) + "§8]");
            }
        }
    }

    private static void tickEscort(MinecraftServer s) {
        PoliceConfig cfg = PoliceConfig.get();
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(ESCORT.entrySet())) {
            ServerPlayer c = online(s, en.getKey());
            ServerPlayer h = online(s, en.getValue());
            if (c == null || !c.isAlive() || !isCuffed(c.getUUID())) { ESCORT.remove(en.getKey()); continue; }
            if (h == null || !h.isAlive() || isCuffed(h.getUUID())) { stopEscort(c, "ravisseur indisponible"); continue; }
            if (c.level() != h.level()) { stopEscort(c, "trop loin"); continue; }
            if (c.isPassenger()) { stopEscort(c, "installé dans un véhicule"); continue; }
            if (h.isPassenger()) { stopEscort(c, "le ravisseur est monté dans un véhicule"); continue; }
            double dist = c.distanceTo(h);
            if (dist > cfg.escorte_distance_max) { stopEscort(c, "trop loin"); continue; }
            // Point de suivi : à escorte_distance du ravisseur, dans la direction où se trouve déjà le captif.
            Vec3 away = new Vec3(c.getX() - h.getX(), 0, c.getZ() - h.getZ());
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(-Math.sin(Math.toRadians(h.getYRot())), 0, Math.cos(Math.toRadians(h.getYRot())));
            away = away.normalize().scale(cfg.escorte_distance);
            Vec3 goal = new Vec3(h.getX() + away.x, h.getY(), h.getZ() + away.z);
            Vec3 d = new Vec3(goal.x - c.getX(), 0, goal.z - c.getZ());
            double hd = d.length();
            if (hd > 0.2) {
                double speed = Math.min(cfg.escorte_vitesse_max, 0.04 + hd * 0.25);
                Vec3 m = d.scale(speed / hd);
                double dy = c.getDeltaMovement().y;
                if (c.onGround() && h.getY() - c.getY() > 0.6 && hd < 3.0) dy = 0.42;     // marche d'escalier : un petit saut
                c.setDeltaMovement(m.x, dy, m.z);
                c.hurtMarked = true;
            }
            // Bloqué (obstacle) : s'il ne progresse plus alors qu'il est loin de son point, on le relâche.
            Vec3 last = LAST_POS.get(c.getUUID());
            if (hd > 2.5 && last != null && last.distanceToSqr(c.position()) < 0.0004) {
                if (STUCK.merge(c.getUUID(), 1, Integer::sum) > 40) { stopEscort(c, "bloqué par un obstacle"); continue; }
            } else STUCK.remove(c.getUUID());
            LAST_POS.put(c.getUUID(), c.position());
        }
    }

    private static void tickSeats(MinecraftServer s) {
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(SEAT.entrySet())) {
            ServerPlayer c = online(s, en.getKey());
            if (c == null || !c.isAlive() || !isCuffed(c.getUUID())) { SEAT.remove(en.getKey()); continue; }
            if (c.isPassenger()) continue;
            Entity veh = ((ServerLevel) c.level()).getEntity(en.getValue());
            if (veh == null || !veh.isAlive()) { SEAT.remove(en.getKey()); continue; }
            Mts.seatPassenger(c, veh);       // il a quitté son siège : on l'y remet (il ne peut pas descendre menotté)
        }
    }

    /** Un menotté ne tient rien en main : l'objet est rangé dans l'inventaire et la main active est vidée (il ne peut de toute façon pas s'en servir). */
    private static void disarm(ServerPlayer p) {
        net.minecraft.world.entity.player.Inventory inv = p.getInventory();
        ItemStack off = inv.offhand.get(0);
        if (!off.isEmpty()) {
            int free = inv.getFreeSlot();
            if (free >= 0) { inv.setItem(free, off.copy()); inv.offhand.set(0, ItemStack.EMPTY); }
        }
        if (inv.getSelected().isEmpty()) return;
        int empty = -1;
        for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) { empty = i; break; }
        if (empty >= 0) {
            inv.selected = empty;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(empty));
        } else {
            int free = inv.getFreeSlot();
            if (free >= 0) { inv.setItem(free, inv.getSelected().copy()); inv.setItem(inv.selected, ItemStack.EMPTY); }
        }
        p.inventoryMenu.broadcastChanges();
    }

    private static void tickDisarm(MinecraftServer s) {
        for (Map.Entry<UUID, State> en : STATES.entrySet()) {
            if (!en.getValue().cuffed) continue;
            ServerPlayer p = online(s, en.getKey());
            if (p != null && p.isAlive() && !(p.containerMenu instanceof SearchMenu)) disarm(p);
        }
    }

    // ------------------------------------------------------------------ armes d'autres mods (TACZ) : aucun tir, rechargement ni dégât
    /** Côté client : renseigné par CuffClient (qui est menotté). */
    public static java.util.function.Predicate<UUID> clientCuffed;

    private static boolean shooterCuffed(Object entity, boolean client) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player pl)) return false;
        if (client) return clientCuffed != null && clientCuffed.test(pl.getUUID());
        return isCuffed(pl.getUUID());
    }

    /** Écoute par réflexion les évènements annulables de TACZ (tir, rechargement, mêlée, dégâts) : aucune dépendance dure. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerGunBlocks() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz")) return;
        String[] names = {"GunShootEvent", "GunFireEvent", "GunReloadEvent", "GunMeleeEvent", "GunFireSelectEvent", "GunDrawEvent", "EntityHurtByGunEvent$Pre"};
        for (String n : names) {
            try {
                Class cls = Class.forName("com.tacz.guns.api.event.common." + n);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, cls, (java.util.function.Consumer) (ev -> {
                    try {
                        net.minecraftforge.eventbus.api.Event e = (net.minecraftforge.eventbus.api.Event) ev;
                        if (!e.isCancelable()) return;
                        Object who = null;
                        for (String m : new String[]{"getShooter", "getEntity", "getAttacker"}) {
                            try { who = ev.getClass().getMethod(m).invoke(ev); break; } catch (NoSuchMethodException ignored) {}
                        }
                        Object side = null;
                        try { side = ev.getClass().getMethod("getLogicalSide").invoke(ev); } catch (NoSuchMethodException ignored) {}
                        boolean client = side != null && side.toString().equals("CLIENT");
                        if (shooterCuffed(who, client)) e.setCanceled(true);
                    } catch (Throwable ignored) {
                    }
                }));
            } catch (Throwable ignored) {
            }
        }
    }

    private static void tickEnforce(MinecraftServer s) {
        for (Map.Entry<UUID, State> en : new ArrayList<>(STATES.entrySet())) {
            ServerPlayer p = online(s, en.getKey());
            if (p == null) continue;
            State st = en.getValue();
            if (st.cuffed) {
                p.setSprinting(false);
                if (p.containerMenu != p.inventoryMenu && !(p.containerMenu instanceof SearchMenu)) p.closeContainer();
                if (p.isUsingItem()) p.stopUsingItem();
            }
            if (st.hooded) {
                ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
                if (!isHoodMark(head)) {
                    // Quelqu'un a retiré / remplacé le sac de la tête : on remet le sac, l'objet est rendu à son inventaire.
                    if (!head.isEmpty()) giveOrDrop(p, p, head.copy());
                    ItemStack mark = new ItemStack(ModItems.SAC_JUTE.get());
                    mark.getOrCreateTag().putBoolean(MARK, true);
                    p.setItemSlot(EquipmentSlot.HEAD, mark);
                }
            }
        }
    }

    private static void tickTimers(MinecraftServer s) {
        PoliceConfig cfg = PoliceConfig.get();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> en : new ArrayList<>(STATES.entrySet())) {
            ServerPlayer p = online(s, en.getKey());
            if (p == null) continue;
            State st = en.getValue();
            if (st.cuffed && cfg.menottes_liberation_minutes > 0 && now - st.cuffStart > cfg.menottes_liberation_minutes * 60_000L) {
                release(p, null, "délai écoulé", false);
                continue;
            }
            if (st.hooded) {
                long limit = (st.cuffed ? cfg.sac_duree_secondes : cfg.sac_sans_menottes_secondes) * 1000L;
                if (now - st.hoodStart > limit) {
                    removeHood(p, st, null, true);
                    if (!st.cuffed) { STATES.remove(en.getKey()); persist(p); }
                    broadcast(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ synchronisation vers les clients
    /** État affiché : bit 1 = menotté, 2 = encagoulé, 4 = menottes artisanales. */
    public static Map<UUID, Integer> flags() {
        Map<UUID, Integer> m = new HashMap<>();
        for (Map.Entry<UUID, State> en : STATES.entrySet()) {
            State s = en.getValue();
            int f = (s.cuffed ? 1 : 0) | (s.hooded ? 2 : 0) | (s.type == ARTISANAL ? 4 : 0);
            if (f != 0) m.put(en.getKey(), f);
        }
        return m;
    }

    public static void broadcast(MinecraftServer s) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new ModNetwork.CuffSyncPacket(flags()));
    }

    public static void sendTo(ServerPlayer p) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new ModNetwork.CuffSyncPacket(flags()));
    }

    // ------------------------------------------------------------------ tablette de police
    /** Menottés connectés pour la tablette : nom, « ravisseur · type · temps restant » (dans rpName), encagoulé (dans wanted). */
    public static List<ModNetwork.Citizen> list(MinecraftServer s) {
        List<ModNetwork.Citizen> out = new ArrayList<>();
        PoliceConfig cfg = PoliceConfig.get();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> en : STATES.entrySet()) {
            State st = en.getValue();
            ServerPlayer p = online(s, en.getKey());
            if (!st.cuffed || p == null) continue;
            String left = cfg.menottes_liberation_minutes <= 0 ? "sans limite"
                    : Math.max(0, (cfg.menottes_liberation_minutes * 60_000L - (now - st.cuffStart)) / 60_000L + 1) + " min restantes";
            String who = st.captorName.isEmpty() ? name(s, st.captor) : st.captorName;
            out.add(new ModNetwork.Citizen(en.getKey(), p.getGameProfile().getName(),
                    (st.type == POLICE ? "Police" : "Artisanales") + " - par " + who + " - " + left, true, st.hooded));
        }
        out.sort(java.util.Comparator.comparing(ModNetwork.Citizen::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
