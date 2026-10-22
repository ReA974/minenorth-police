package fr.minenorth.police.casier;

import fr.minenorth.police.casier.item.SeizureBagItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cœur du mod : prise d'équipement (sauvegarde de l'inventaire civil + stuff), dépôt, fin de service (restitution 1 pour 1).
 * Tout se passe ici côté serveur ; les écrans ne font que demander.
 * Ordre de chaque étape : écrire la sauvegarde sur disque AVANT de toucher à l'inventaire, forcer l'enregistrement du joueur APRÈS,
 * pour qu'un crash ne puisse jamais perdre ni dupliquer un objet civil.
 */
public final class ServiceManager {
    private ServiceManager() {}

    /** Vrai pendant que le mod coupe lui-même le service (le contrôle « passe par ton casier » ne doit pas se refuser à lui-même). */
    private static boolean releasing;

    public static final String MSG_PASSE_PAR_CASIER = "Passe par ton casier : tu as encore des affaires sur toi";

    // ------------------------------------------------------------------ session

    public static CompoundTag session(MinecraftServer s, UUID id) { return CasierData.get(s).sessions.get(id); }

    public static boolean inSession(ServerPlayer p) { return session(p.server, p.getUUID()) != null; }

    public static ServiceDef serviceOf(CompoundTag sess) { return sess == null ? null : Services.get(sess.getString("Service")); }

    // ------------------------------------------------------------------ stuff

    /** Modèle du stuff (objets non marqués) : écran admin s'il existe, sinon fichier de config. */
    public static List<ItemStack> template(MinecraftServer s, String service) {
        CasierData d = CasierData.get(s);
        List<ItemStack> out = new ArrayList<>();
        if (d.stuff.containsKey(service)) {
            for (ItemStack st : d.stuff.get(service)) if (!st.isEmpty()) out.add(st.copy());
            return out;
        }
        List<String> lines = new ArrayList<>();
        if (service.equals("police")) lines.addAll(CasierConfig.STUFF_POLICE.get());
        else if (service.equals("pompiers")) lines.addAll(CasierConfig.STUFF_POMPIERS.get());
        else {
            for (String l : CasierConfig.STUFF_AUTRES.get()) {
                int i = l.indexOf('|');
                if (i > 0 && l.substring(0, i).trim().equals(service)) lines.add(l.substring(i + 1));
            }
        }
        for (String l : lines) {
            ItemStack st = parse(l);
            if (st != null) out.add(st);
        }
        return out;
    }

    public static boolean hasOverride(MinecraftServer s, String service) { return CasierData.get(s).stuff.containsKey(service); }

    /** « id|quantité|nbt » -> pile, ou null si l'objet n'existe pas (mod absent) ou la ligne est illisible. */
    public static ItemStack parse(String line) {
        String[] f = line.split("\\|", 3);
        try {
            ResourceLocation rl = new ResourceLocation(f[0].trim());
            if (!BuiltInRegistries.ITEM.containsKey(rl)) return null;
            Item it = BuiltInRegistries.ITEM.get(rl);
            String cnt = f.length > 1 ? f[1].trim() : "";
            String hint = "";
            int at = cnt.indexOf('@');
            if (at >= 0) { hint = cnt.substring(at + 1).trim(); cnt = cnt.substring(0, at).trim(); }
            int n = !cnt.isBlank() ? Math.max(1, Integer.parseInt(cnt)) : 1;
            ItemStack st = new ItemStack(it, n);
            if (f.length > 2 && !f[2].isBlank()) st.setTag(TagParser.parseTag(f[2].trim()));
            if (!hint.isEmpty()) st.getOrCreateTag().putString("casier_slot", hint);
            return st;
        } catch (Exception e) {
            MineNorthCasier.LOG.warn("[Casier] Ligne de stuff illisible « {} » : {}", line, e.toString());
            return null;
        }
    }

    private static void giveStuff(ServerPlayer p, String service) {
        Inventory inv = p.getInventory();
        for (ItemStack t : template(p.server, service)) {
            int left = t.getCount();
            while (left > 0) {
                ItemStack one = t.copy();
                String hint = "";
                if (one.hasTag() && one.getTag().contains("casier_slot")) {
                    hint = one.getTag().getString("casier_slot");
                    one.getTag().remove("casier_slot");
                    if (one.getTag().isEmpty()) one.setTag(null);
                }
                one.setCount(Math.min(left, Math.max(1, one.getMaxStackSize())));
                left -= one.getCount();
                Marks.mark(one, service, p.getUUID());
                EquipmentSlot es = Mob.getEquipmentSlotForItem(one);
                boolean inventoryOnly = hint.equalsIgnoreCase("inventaire");
                boolean curiosFirst = !hint.isEmpty() && !inventoryOnly && !hint.equalsIgnoreCase("auto") && !hint.equalsIgnoreCase("armure");
                String type = curiosFirst && !hint.equalsIgnoreCase("curios") ? hint : null;
                if (!inventoryOnly && curiosFirst && one.getCount() == 1 && Curios.equip(p, one, type)) continue;
                if (!inventoryOnly && es.getType() == EquipmentSlot.Type.ARMOR && inv.armor.get(es.getIndex()).isEmpty()) inv.armor.set(es.getIndex(), one);
                else if (!inventoryOnly && !curiosFirst && one.getCount() == 1 && Curios.equip(p, one, null)) continue;
                else if (!inv.add(one)) MineNorthCasier.LOG.warn("[Casier] Plus de place pour {} chez {}", one, p.getGameProfile().getName());
            }
        }
    }

    // ------------------------------------------------------------------ prise d'équipement

    /** Prend le stuff : sauvegarde l'inventaire civil puis équipe. Renvoie un message d'erreur, ou null si tout s'est bien passé. */
    public static String begin(ServerPlayer p, ServiceDef sv) {
        MinecraftServer s = p.server;
        CasierData d = CasierData.get(s);
        if (d.sessions.containsKey(p.getUUID())) return "Tu as déjà pris ton équipement.";
        if (!sv.member(p)) return "Ce casier est réservé aux membres : " + sv.name() + ".";
        if (!sv.onDuty(p)) {
            if (!(CasierConfig.PRISE_AU_CASIER.get() || isCompany(sv))) {
                return "Prends d'abord ton service (tablette ou accueil).";
            }
            sv.setDuty(p, true);
            if (!sv.onDuty(p)) return "Impossible de prendre ton service pour l'instant.";
        }
        CompoundTag sess = new CompoundTag();
        sess.putString("Service", sv.id());
        sess.putLong("Start", System.currentTimeMillis());
        sess.putLong("Offline", 0L);
        sess.put("Extras", new ListTag());
        sess.putBoolean("Given", false);
        sess.putBoolean("Lost", false);
        snapshot(p, sess);
        d.sessions.put(p.getUUID(), sess);
        d.flush(s);                       // 1. la sauvegarde est sur disque
        clearAll(p);                      // 2. seulement ensuite on vide l'inventaire
        giveStuff(p, sv.id());
        sess.putBoolean("Given", true);
        s.getPlayerList().saveAll();      // 3. le fichier du joueur reflète le stuff
        d.flush(s);
        return null;
    }

    public static boolean isCompany(ServiceDef sv) { return !sv.id().equals("police") && !sv.id().equals("pompiers"); }

    /** Reprend le stuff après une mort (le civil est déjà en sécurité). */
    public static String retake(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        CompoundTag sess = d.sessions.get(p.getUUID());
        if (sess == null) return "Tu n'as pas pris ton équipement.";
        if (sess.getBoolean("Given")) return "Tu as déjà ton équipement.";
        if (sess.getBoolean("Lost")) return "Ton équipement est perdu : termine ton service puis reprends-le.";
        giveStuff(p, sess.getString("Service"));
        sess.putBoolean("Given", true);
        p.server.getPlayerList().saveAll();
        d.flush(p.server);
        return null;
    }

    // ------------------------------------------------------------------ instantané de l'inventaire civil

    private static void snapshot(ServerPlayer p, CompoundTag sess) {
        sess.put("Inv", p.getInventory().save(new ListTag()));
        ListTag cur = new ListTag();
        for (Curios.SlotRef r : Curios.slots(p)) {
            ItemStack st = r.get();
            if (st.isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putString("Type", r.type());
            c.putBoolean("Cos", r.cosmetic());
            c.putInt("I", r.index());
            c.put("Item", st.save(new CompoundTag()));
            cur.add(c);
        }
        sess.put("Curios", cur);
    }

    private static void clearAll(ServerPlayer p) {
        p.getInventory().clearContent();
        for (Curios.SlotRef r : Curios.slots(p)) if (!r.get().isEmpty()) r.set(ItemStack.EMPTY);
    }

    private static void restoreSnapshot(ServerPlayer p, CompoundTag sess, List<ItemStack> overflow) {
        p.getInventory().load(sess.getList("Inv", Tag.TAG_COMPOUND));
        ListTag cur = sess.getList("Curios", Tag.TAG_COMPOUND);
        if (cur.isEmpty()) return;
        List<Curios.SlotRef> slots = Curios.slots(p);
        for (int i = 0; i < cur.size(); i++) {
            CompoundTag c = cur.getCompound(i);
            ItemStack st = ItemStack.of(c.getCompound("Item"));
            boolean placed = false;
            for (Curios.SlotRef r : slots) {
                if (r.type().equals(c.getString("Type")) && r.cosmetic() == c.getBoolean("Cos") && r.index() == c.getInt("I") && r.get().isEmpty()) {
                    r.set(st);
                    placed = true;
                    break;
                }
            }
            if (!placed) overflow.add(st);
        }
    }

    // ------------------------------------------------------------------ affaires étrangères

    private static Set<String> tolerated() {
        Set<String> out = new HashSet<>();
        for (String s : CasierConfig.CONSERVES.get()) out.add(s.trim());
        return out;
    }

    private static boolean foreign(ItemStack st, Set<String> ok) {
        if (st.isEmpty() || Marks.is(st)) return false;
        return !ok.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
    }

    /** Ce qui n'est pas de son métier sur le joueur : affichage « 3 x Pierre », sac de saisie compris. */
    public static List<String> foreignLines(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        Set<String> ok = tolerated();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (foreign(st, ok)) out.add(st.getCount() + " x " + st.getHoverName().getString());
            else if (SeizureBagItem.isBag(st)) {
                int n = SeizureBagItem.count(st);
                if (n > 0) out.add("Sac de saisie : " + n + " objet" + (n > 1 ? "s" : "") + " saisi" + (n > 1 ? "s" : ""));
            }
        }
        for (Curios.SlotRef r : Curios.slots(p)) {
            if (foreign(r.get(), ok)) out.add(r.get().getCount() + " x " + r.get().getHoverName().getString());
        }
        return out;
    }

    public static boolean hasForeign(ServerPlayer p) { return !foreignLines(p).isEmpty(); }

    /**
     * Retire du joueur tout ce qui n'est pas de son métier : sac de saisie -> consigne, le reste -> consigne ou civil (config).
     * Les objets sont mis en lieu sûr dans les données AVANT la prochaine action.
     */
    public static int deposit(ServerPlayer p, CompoundTag sess) {
        MinecraftServer s = p.server;
        CasierData d = CasierData.get(s);
        String service = sess.getString("Service");
        boolean toCivil = CasierConfig.ETRANGERS_VERS.get().equals("CIVIL");
        Set<String> ok = tolerated();
        List<ItemStack> seized = new ArrayList<>();
        List<ItemStack> others = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (foreign(st, ok)) { others.add(st.copy()); inv.setItem(i, ItemStack.EMPTY); }
            else if (SeizureBagItem.isBag(st)) SeizureBagItem.drain(st, seized);
        }
        for (Curios.SlotRef r : Curios.slots(p)) {
            if (foreign(r.get(), ok)) { others.add(r.get().copy()); r.set(ItemStack.EMPTY); }
        }
        int n = seized.size() + others.size();
        if (n == 0) return 0;
        for (ItemStack st : seized) addConsigne(d, service, st);
        if (toCivil) {
            ListTag ex = sess.getList("Extras", Tag.TAG_COMPOUND);
            for (ItemStack st : others) ex.add(st.save(new CompoundTag()));
            sess.put("Extras", ex);
        } else {
            for (ItemStack st : others) addConsigne(d, service, st);
        }
        d.flush(s);
        return n;
    }

    /** Range une pile dans la consigne (fusionne avec une pile identique si possible). */
    public static void addConsigne(CasierData d, String service, ItemStack in) {
        if (in.isEmpty()) return;
        List<ItemStack> l = d.consigne(service);
        ItemStack rest = in.copy();
        for (ItemStack ex : l) {
            if (rest.isEmpty()) break;
            if (!ex.isEmpty() && ItemStack.isSameItemSameTags(ex, rest) && ex.getCount() < ex.getMaxStackSize()) {
                int k = Math.min(rest.getCount(), ex.getMaxStackSize() - ex.getCount());
                ex.grow(k);
                rest.shrink(k);
            }
        }
        if (rest.isEmpty()) return;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).isEmpty()) { l.set(i, rest); return; }
        }
        l.add(rest);
    }

    // ------------------------------------------------------------------ fin de service

    /**
     * Termine le service : dépôt des affaires étrangères, reprise du stuff, restitution de l'inventaire civil à l'identique, puis coupure du service.
     * {@code viaCasier} faux = demande par la tablette / l'accueil : refusée s'il reste des affaires étrangères.
     * Renvoie un message d'erreur, ou null si le service est terminé.
     */
    public static String finish(ServerPlayer p, boolean viaCasier) {
        MinecraftServer s = p.server;
        CasierData d = CasierData.get(s);
        CompoundTag sess = d.sessions.get(p.getUUID());
        ServiceDef sv = serviceOf(sess);
        if (sess == null) return null;
        if (!viaCasier && sv != null && d.casierCount(sv.id()) > 0) {
            if (!CasierConfig.FIN_SANS_CASIER.get() || hasForeign(p)) return MSG_PASSE_PAR_CASIER;
        }
        deposit(p, sess);
        List<ItemStack> overflow = new ArrayList<>();
        clearAll(p);
        restoreSnapshot(p, sess, overflow);
        for (ItemStack st : readList(sess.getList("Extras", Tag.TAG_COMPOUND))) giveOrPend(p, d, st, overflow);
        for (ItemStack st : overflow) giveOrPend(p, d, st, null);
        s.getPlayerList().saveAll();      // le fichier du joueur contient déjà le civil...
        d.sessions.remove(p.getUUID());   // ...avant que la sauvegarde ne disparaisse
        d.flush(s);
        deliverPending(p);
        if (sv != null) {
            releasing = true;
            try { if (sv.onDuty(p)) sv.setDuty(p, false); } finally { releasing = false; }
        }
        p.displayClientMessage(Component.literal("Service terminé : ton inventaire civil t'a été rendu.").withStyle(ChatFormatting.GREEN), false);
        return null;
    }

    private static List<ItemStack> readList(ListTag l) {
        List<ItemStack> out = new ArrayList<>();
        CasierData.readList(l, out);
        return out;
    }

    private static void giveOrPend(ServerPlayer p, CasierData d, ItemStack st, List<ItemStack> sink) {
        if (st.isEmpty()) return;
        ItemStack c = st.copy();
        p.getInventory().add(c);
        if (c.isEmpty()) return;
        d.pending.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(c);
    }

    /** Rend les affaires restées en attente dès qu'il y a de la place. Renvoie le nombre de piles rendues. */
    public static int deliverPending(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        List<ItemStack> l = d.pending.get(p.getUUID());
        if (l == null || l.isEmpty()) return 0;
        int given = 0;
        for (int i = l.size() - 1; i >= 0; i--) {
            ItemStack st = l.get(i).copy();
            p.getInventory().add(st);
            if (st.isEmpty()) { l.remove(i); given++; }
            else l.set(i, st);
        }
        if (l.isEmpty()) d.pending.remove(p.getUUID());
        if (given > 0) {
            d.flush(p.server);
            p.displayClientMessage(Component.literal("Tes affaires en attente t'ont été rendues.").withStyle(ChatFormatting.GREEN), false);
        }
        return given;
    }

    // ------------------------------------------------------------------ contrôle appelé par les tablettes / l'accueil (mods Police et Secours)

    /**
     * Le joueur veut quitter son service (tablette, accueil). Vrai = c'est permis (le stuff est repris et le civil rendu si besoin),
     * faux = refusé, le message « Passe par ton casier » est déjà affiché.
     */
    public static boolean allowLeave(ServerPlayer p) {
        if (releasing) return true;
        if (!inSession(p)) return true;
        String err = finish(p, false);
        if (err == null) return true;
        p.displayClientMessage(Component.literal(err).withStyle(ChatFormatting.RED), true);
        p.sendSystemMessage(Component.literal("§c[Casier] " + err + "."));
        return false;
    }

    // ------------------------------------------------------------------ contrôles périodiques et cas limites

    /** Secondes : appartenance, service coupé par ailleurs, instantané périmé après un crash. */
    public static void check(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        CompoundTag sess = d.sessions.get(p.getUUID());
        if (sess == null) return;
        ServiceDef sv = serviceOf(sess);
        if (sv == null || !sv.member(p)) {
            p.sendSystemMessage(Component.literal("§e[Casier] Tu n'es plus membre de ce service : ton inventaire civil t'est rendu."));
            finish(p, true);
            return;
        }
        if (!sv.onDuty(p)) sv.setDuty(p, true);
    }

    /** Connexion : reprise propre, ou restitution automatique si le service a expiré hors ligne. */
    public static void onLogin(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        deliverPending(p);
        CompoundTag sess = d.sessions.get(p.getUUID());
        if (sess == null) return;
        long off = sess.getLong("Offline");
        int max = CasierConfig.EXPIRATION_HORS_LIGNE.get();
        sess.putLong("Offline", 0L);
        if (max > 0 && off > 0 && System.currentTimeMillis() - off > max * 60_000L) {
            p.sendSystemMessage(Component.literal("§e[Casier] Ton service a expiré pendant ton absence : ton inventaire civil t'est rendu."));
            finish(p, true);
            return;
        }
        // Instantané périmé (crash entre la sauvegarde du casier et celle du joueur) : l'inventaire du fichier est encore le civil.
        if (sess.getBoolean("Given") && p.getInventory().save(new ListTag()).equals(sess.getList("Inv", Tag.TAG_COMPOUND))) {
            clearAll(p);
            giveStuff(p, sess.getString("Service"));
            p.sendSystemMessage(Component.literal("§e[Casier] Ton équipement de service a été remis après la coupure."));
        }
        d.flush(p.server);
        p.sendSystemMessage(Component.literal("§b[Casier] Tu reprends ton service. Passe par ton casier pour le terminer."));
    }

    public static void onLogout(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        CompoundTag sess = d.sessions.get(p.getUUID());
        if (sess == null) return;
        sess.putLong("Offline", System.currentTimeMillis());
        d.flush(p.server);
    }

    /** Mort en service : l'équipement marqué disparaît (selon la config), le sac de saisie va à la consigne, le civil n'est jamais lâché. */
    public static void onDeath(ServerPlayer p, java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        CasierData d = CasierData.get(p.server);
        CompoundTag sess = d.sessions.get(p.getUUID());
        if (sess == null) return;
        String service = sess.getString("Service");
        drops.removeIf(ie -> {
            ItemStack st = ie.getItem();
            if (!Marks.is(st)) return false;
            if (SeizureBagItem.isBag(st)) {
                List<ItemStack> in = new ArrayList<>();
                SeizureBagItem.drain(st, in);
                for (ItemStack x : in) addConsigne(d, service, x);
            }
            return true;
        });
        sess.putBoolean("Given", false);
        sess.putBoolean("Lost", CasierConfig.MORT_EQUIPEMENT.get().equals("DETRUIT"));
        d.flush(p.server);
    }

    /** Équipement de service trouvé là où il ne doit pas être (inventaire d'un autre, d'un joueur sans service) : supprimé. */
    public static void purgeStray(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        CompoundTag sess = d.sessions.get(p.getUUID());
        boolean in = sess != null;
        if (Curios.present()) {
            for (Curios.SlotRef r : Curios.slots(p)) {
                ItemStack c = r.get();
                if (Marks.is(c) && (!in || !p.getUUID().equals(Marks.owner(c)))) r.set(ItemStack.EMPTY);
            }
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !st.hasTag()) continue;
            if (Marks.is(st)) {
                if (!in || !p.getUUID().equals(Marks.owner(st))) {
                    MineNorthCasier.LOG.info("[Casier] Équipement de service retiré de l'inventaire de {} : {}", p.getGameProfile().getName(), st);
                    inv.setItem(i, ItemStack.EMPTY);
                }
            } else if (in && Marks.strip(st.getTag())) {
                p.displayClientMessage(Component.literal("L'équipement de service ne se range pas là.").withStyle(ChatFormatting.RED), true);
            }
        }
    }
}
