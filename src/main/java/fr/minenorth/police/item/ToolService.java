package fr.minenorth.police.item;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.block.ModBlocks;
import fr.minenorth.police.compat.Mts;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Logique serveur des objets de police : mesures du radar à main (sur une demi-seconde),
 * tests salivaires (quelques secondes), mémorisation des drogues consommées, protection des radars fixes.
 */
@Mod.EventBusSubscriber
public final class ToolService {
    private ToolService() {}

    private static final String DRUG_KEY = "minenorthpolice_drogues";
    /** Durée d'une mesure du radar à main quand la vitesse n'est pas lisible directement (ticks). */
    private static final int MEASURE_TICKS = 10;

    private record Measure(UUID officer, Entity target, Vec3 start, long startTick, int limit) {}
    private record Test(UUID officer, UUID target, long endTick) {}

    private static final List<Measure> MEASURES = new ArrayList<>();
    private static final Map<UUID, Test> TESTS = new HashMap<>();

    // ================================================================== radar à main

    public static void measure(ServerPlayer officer, Entity target, int limit) {
        MEASURES.removeIf(m -> m.officer().equals(officer.getUUID()));
        if (Mts.isBuilder(target)) {
            double v = Mts.speedKmh(target, PoliceConfig.get().radar_mts_vitesse_compteur);
            if (!Double.isNaN(v)) { report(officer, target, v, limit); return; }
        }
        officer.displayClientMessage(Component.literal("§7Mesure en cours…"), true);
        MEASURES.add(new Measure(officer.getUUID(), target, target.position(), officer.server.getTickCount(), limit));
    }

    /** Conducteur d'un véhicule (MTS ou vanilla), ou null. */
    @Nullable
    public static ServerPlayer driverOf(Entity vehicle) {
        MinecraftServer s = vehicle.getServer();
        if (s == null) return null;
        if (vehicle instanceof ServerPlayer p) return p;
        if (Mts.isBuilder(vehicle)) {
            UUID id = Mts.controller(vehicle);
            if (id != null) return s.getPlayerList().getPlayer(id);
            // Repli : joueur assis dans un siège MTS le plus proche.
            ServerPlayer best = null;
            double bd = 25;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.level() != vehicle.level() || !Mts.isSeat(p.getVehicle())) continue;
                double d = p.distanceToSqr(vehicle);
                if (d < bd) { bd = d; best = p; }
            }
            return best;
        }
        if (vehicle.getControllingPassenger() instanceof ServerPlayer p) return p;
        for (Entity e : vehicle.getIndirectPassengers()) if (e instanceof ServerPlayer p) return p;
        return null;
    }

    /** Radar mobile : « Modèle — plaque AB-123-CD ». Le conducteur n'est jamais mentionné. */
    public static String describe(Entity target) {
        if (Mts.isBuilder(target)) {
            String plate = Mts.plate(target, PoliceConfig.get().plaque_champs);
            return Mts.vehicleName(target) + " — " + (plate.isEmpty() ? "sans plaque" : "plaque " + plate);
        }
        if (target instanceof Player) return "Piéton";
        return target.getName().getString();
    }

    private static void report(ServerPlayer officer, Entity target, double kmh, int limit) {
        int v = (int) Math.round(kmh);
        int tol = PoliceConfig.get().radar_tolerance_kmh;
        boolean over = v > limit + tol;
        String color = over ? "§c" : v > limit ? "§6" : "§a";
        String what = describe(target);
        officer.displayClientMessage(Component.literal(color + "§l" + v + " km/h §r§7(limite " + limit + ") §f" + what), true);
        officer.sendSystemMessage(Component.literal("§b[Radar] §f" + what + " : " + color + v + " km/h §7(limite " + limit
                + (over ? ", excès de " + (v - limit) + " km/h" : "") + ")"));
        officer.level().playSound(null, officer.blockPosition(), over ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6f, over ? 2.0f : 1.4f);
    }

    // ================================================================== test salivaire

    public static boolean testing(ServerPlayer officer) { return TESTS.containsKey(officer.getUUID()); }

    public static void startTest(ServerPlayer officer, ServerPlayer target) {
        int secs = PoliceConfig.get().test_duree_secondes;
        TESTS.put(officer.getUUID(), new Test(officer.getUUID(), target.getUUID(), officer.server.getTickCount() + secs * 20L));
        String name = PoliceService.display(officer.server, target.getUUID());
        officer.displayClientMessage(Component.literal("§7Test salivaire de " + name + " en cours… (" + secs + " s)"), true);
        target.sendSystemMessage(Component.literal("§eLa police vous fait passer un test salivaire."));
        if (secs == 0) finishTest(officer.server, TESTS.remove(officer.getUUID()));
    }

    private static void finishTest(MinecraftServer s, Test t) {
        ServerPlayer officer = s.getPlayerList().getPlayer(t.officer());
        ServerPlayer target = s.getPlayerList().getPlayer(t.target());
        if (officer == null) return;
        PoliceConfig cfg = PoliceConfig.get();
        if (target == null || target.level() != officer.level() || target.distanceTo(officer) > cfg.test_portee + 2) {
            officer.displayClientMessage(Component.literal("§cTest interrompu : le citoyen s'est éloigné."), true);
            return;
        }
        // Le test est consommé à la fin de l'analyse.
        ItemStack hand = officer.getMainHandItem().is(ModItems.TEST_SALIVAIRE.get()) ? officer.getMainHandItem() : officer.getOffhandItem();
        if (!hand.is(ModItems.TEST_SALIVAIRE.get())) {
            officer.displayClientMessage(Component.literal("§cTest interrompu : gardez le test en main."), true);
            return;
        }
        if (!officer.getAbilities().instabuild) hand.shrink(1);
        List<String> found = drugs(target);
        String name = PoliceService.display(s, target.getUUID());
        if (found.isEmpty()) {
            officer.sendSystemMessage(Component.literal("§b[Test salivaire] §f" + name + " : §aNÉGATIF"));
            officer.displayClientMessage(Component.literal("§a§lNÉGATIF"), true);
            target.sendSystemMessage(Component.literal("§aTest salivaire négatif."));
            return;
        }
        String list = String.join(", ", found);
        officer.sendSystemMessage(Component.literal("§b[Test salivaire] §f" + name + " : §c§lPOSITIF §r§c(" + list + ")"));
        officer.displayClientMessage(Component.literal("§c§lPOSITIF §r§c" + list), true);
        target.sendSystemMessage(Component.literal("§cTest salivaire positif."));
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
        if (cfg.test_positif_au_casier) {
            PoliceService.addEntry(s, target.getUUID(), target.getGameProfile().getName(), PoliceData.NOTE,
                    PoliceService.display(s, officer.getUUID()), "Test salivaire positif : " + list);
        }
    }

    /** Substances détectées sur ce joueur (effets actifs, tags, consommations récentes). */
    public static List<String> drugs(ServerPlayer t) {
        PoliceConfig cfg = PoliceConfig.get();
        Set<String> out = new LinkedHashSet<>();
        for (MobEffectInstance e : t.getActiveEffects()) {
            ResourceLocation k = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect());
            if (k != null && containsIgnoreCase(cfg.drogue_effets, k.toString())) out.add(e.getEffect().getDisplayName().getString());
        }
        for (String tag : t.getTags()) if (containsIgnoreCase(cfg.drogue_tags, tag)) out.add("substance illicite");
        CompoundTag drugs = t.getPersistentData().getCompound(DRUG_KEY);
        long now = System.currentTimeMillis(), window = cfg.drogue_detection_minutes * 60_000L;
        for (String k : drugs.getAllKeys()) {
            CompoundTag d = drugs.getCompound(k);
            if (now - d.getLong("t") <= window) out.add(d.getString("n"));
        }
        return new ArrayList<>(out);
    }

    private static boolean containsIgnoreCase(List<String> list, String v) {
        for (String s : list) if (s != null && s.trim().equalsIgnoreCase(v)) return true;
        return false;
    }

    private static void markDrug(Player p, ItemStack st) {
        if (!(p instanceof ServerPlayer sp) || st.isEmpty()) return;
        if (!PoliceTool.matchesAny(st, PoliceConfig.get().drogue_objets)) return;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (key == null) return;
        CompoundTag root = sp.getPersistentData();
        CompoundTag drugs = root.getCompound(DRUG_KEY);
        CompoundTag d = new CompoundTag();
        d.putLong("t", System.currentTimeMillis());
        d.putString("n", st.getHoverName().getString());
        drugs.put(key.toString(), d);
        // Nettoyage des vieilles entrées.
        long window = PoliceConfig.get().drogue_detection_minutes * 60_000L;
        for (String k : new ArrayList<>(drugs.getAllKeys())) {
            if (System.currentTimeMillis() - drugs.getCompound(k).getLong("t") > window * 4) drugs.remove(k);
        }
        root.put(DRUG_KEY, drugs);
    }

    // ================================================================== événements

    @SubscribeEvent
    public static void useFinished(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof Player p) markDrug(p, e.getItem());
    }

    /** Objets « drogue » sans animation (utilisation instantanée au clic droit). */
    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide || e.getItemStack().getUseDuration() > 0) return;
        markDrug(e.getEntity(), e.getItemStack());
    }

    /** La drogue reste détectable après la mort (comme dans la vraie vie, ça ne s'efface pas). */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(DRUG_KEY)) e.getEntity().getPersistentData().put(DRUG_KEY, old.getCompound(DRUG_KEY).copy());
    }

    /** Un radar fixe ne peut être démonté que par un policier (ou en créatif). */
    @SubscribeEvent
    public static void breakRadar(BlockEvent.BreakEvent e) {
        if (!e.getState().is(ModBlocks.RADAR_FIXE.get())) return;
        if (!(e.getPlayer() instanceof ServerPlayer p) || p.getAbilities().instabuild || PoliceService.rank(p) >= 0) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.literal("§cSeule la police peut démonter un radar."), true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        TESTS.remove(id);
        MEASURES.removeIf(m -> m.officer().equals(id));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        long now = s.getTickCount();
        for (Iterator<Measure> it = MEASURES.iterator(); it.hasNext(); ) {
            Measure m = it.next();
            if (now - m.startTick() < MEASURE_TICKS) continue;
            it.remove();
            ServerPlayer officer = s.getPlayerList().getPlayer(m.officer());
            if (officer == null) continue;
            Entity t = m.target();
            if (t.isRemoved() || t.level() != officer.level()) {
                officer.displayClientMessage(Component.literal("§cCible perdue."), true);
                continue;
            }
            double dist = t.position().subtract(m.start()).horizontalDistance();
            double seconds = (now - m.startTick()) / 20.0;
            report(officer, t, dist / seconds * 3.6, m.limit());
        }
        if (!TESTS.isEmpty()) {
            List<Test> done = new ArrayList<>();
            for (Test t : TESTS.values()) if (now >= t.endTick()) done.add(t);
            for (Test t : done) { TESTS.remove(t.officer()); finishTest(s, t); }
        }
    }

    /** Texte « 50 km/h » des limites disponibles. */
    public static String limitsText() {
        StringBuilder b = new StringBuilder();
        for (Integer v : PoliceConfig.get().radar_limites) b.append(b.length() == 0 ? "" : ", ").append(v);
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** Limite suivante dans la liste de la config. */
    public static int nextLimit(int current) {
        List<Integer> l = PoliceConfig.get().radar_limites;
        for (Integer v : l) if (v > current) return v;
        return l.get(0);
    }
}
