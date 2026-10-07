package fr.minenorth.police;

import fr.minenorth.police.compat.Compat;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import fr.minenorth.police.data.PoliceData.Rec;
import fr.minenorth.police.data.PoliceData.Req;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.network.ModNetwork;
import fr.minenorth.police.network.ModNetwork.ActionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Logique serveur de la police.
 *
 * Droits par grade :
 *  - Sous-officier : consulter les dossiers, amende simple, note, saisie d'objets, faire une demande.
 *  - Officier      : + amende avec retrait de points, condamnation, avis de recherche.
 *  - Commissaire   : + accepter/refuser les demandes, gérer les effectifs, supprimer une entrée de casier.
 * Un OP n'a aucun accès à la tablette tant qu'il n'a pas de grade (/police grade).
 */
@Mod.EventBusSubscriber
public final class PoliceService {
    private PoliceService() {}

    private static final int PAGE = 6;

    /** Ce que chaque policier regarde sur sa tablette (présent = tablette ouverte). */
    private static final class Session { String query = ""; int page; }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record R(String msg, boolean ok) {
        static R ok(String m) { return new R(m, true); }
        static R err(String m) { return new R(m, false); }
    }

    // ------------------------------------------------------------------ grades
    /** Grade du joueur (0 = Commissaire), ou -1 s'il n'est pas policier. */
    public static int rank(ServerPlayer p) {
        Integer g = PoliceData.get(p.server).officers.get(p.getUUID());
        // Seuls les policiers enregistrés ouvrent la tablette, OP compris : un OP se nomme avec /police grade.
        return g == null ? -1 : Math.max(0, Math.min(2, g));
    }

    private static boolean hasTablet(ServerPlayer p) { return p.getInventory().contains(new ItemStack(ModItems.TABLET.get())); }

    public static void giveTablet(ServerPlayer p) {
        if (hasTablet(p)) return;
        ItemStack tablet = new ItemStack(ModItems.TABLET.get());
        if (!p.getInventory().add(tablet)) p.drop(tablet, false);
    }

    private static void syncTag(ServerPlayer p, boolean police) {
        String tag = PoliceConfig.get().tag_police;
        if (tag.isBlank()) return;
        if (police) p.addTag(tag); else p.removeTag(tag);
    }

    /** grade : 0..2, ou -1 pour retirer de la police. Renvoie un message de résultat. */
    public static String setGrade(MinecraftServer s, UUID id, String name, int grade) {
        PoliceData d = PoliceData.get(s);
        ServerPlayer on = s.getPlayerList().getPlayer(id);
        if (grade < 0) {
            if (d.officers.remove(id) == null) return name + " ne fait pas partie de la police.";
            d.setDirty();
            if (on != null) { syncTag(on, false); on.sendSystemMessage(Component.literal("§eVous ne faites plus partie de la police.")); }
            return name + " a été retiré de la police.";
        }
        int g = Math.min(2, grade);
        boolean isNew = !d.officers.containsKey(id);
        d.officers.put(id, g);
        d.names.put(id, name);
        d.setDirty();
        if (on != null) {
            syncTag(on, true);
            if (isNew) giveTablet(on);
            on.sendSystemMessage(Component.literal("§bPolice : votre grade est maintenant " + PoliceData.GRADES[g] + "."));
        }
        return name + " est maintenant " + PoliceData.GRADES[g] + ".";
    }

    // ------------------------------------------------------------------ ouverture
    public static void open(ServerPlayer p) {
        if (rank(p) < 0) {
            p.displayClientMessage(Component.literal("§cCette tablette est réservée à la police."), true);
            return;
        }
        SESSIONS.put(p.getUUID(), new Session());
        importCitizens(p.server, PoliceData.get(p.server));
        sendList(p, "", true);
    }

    // ------------------------------------------------------------------ vues
    /** Nom affiché sur la tablette (fichier administratif) : « NOM Prénom » de la carte d'identité, sinon le pseudo. */
    public static String display(MinecraftServer s, UUID id) {
        String rp = rpName(s, id);
        return rp.isBlank() ? PoliceData.get(s).name(id) : rp;
    }

    /** Ajoute au fichier les citoyens connus des mods Identité et Permis, même s'ils ne se sont pas reconnectés depuis. */
    private static void importCitizens(MinecraftServer s, PoliceData d) {
        for (Map.Entry<UUID, String> e : Compat.knownCitizens(s).entrySet()) {
            if (d.names.containsKey(e.getKey())) continue;
            String name = e.getValue();
            if (name.isBlank() && s.getProfileCache() != null) {
                name = s.getProfileCache().get(e.getKey()).map(com.mojang.authlib.GameProfile::getName).orElse("");
            }
            d.names.put(e.getKey(), name.isBlank() ? e.getKey().toString().substring(0, 8) : name);
            d.setDirty();
        }
    }

    private static String rpName(MinecraftServer s, UUID id) {
        return fr.minenorth.api.MineNorth.identity().get(s, id).map(fr.minenorth.api.Identity::officialName).orElse("");
    }

    private static int pending(PoliceData d) {
        int n = 0;
        for (Req r : d.requests) if (r.status == PoliceData.PENDING) n++;
        return n;
    }

    private static ModNetwork.ViewPacket packet(ServerPlayer p, int view, String msg, boolean ok, String query, int page, int pages,
                                                List<ModNetwork.Citizen> citizens, ModNetwork.Dossier dossier, List<ModNetwork.ReqView> requests,
                                                List<ModNetwork.Officer> officers, List<ModNetwork.Inv> inventory) {
        return new ModNetwork.ViewPacket(view, rank(p), msg, ok, query, page, pages, pending(PoliceData.get(p.server)),
                citizens, dossier, requests, officers, inventory, List.of(), List.of());
    }

    /** Onglet RADARS : flashs des radars fixes, les plus récents d'abord (200 max). */
    private static void sendRadars(ServerPlayer p, String msg, boolean ok) {
        PoliceData d = PoliceData.get(p.server);
        List<ModNetwork.FlashView> out = new ArrayList<>();
        for (int i = d.flashes.size() - 1; i >= 0 && out.size() < 200; i--) {
            PoliceData.Flash f = d.flashes.get(i);
            out.add(new ModNetwork.FlashView(f.id, f.time, f.plate, f.model, f.speed, f.limit, f.where));
        }
        ModNetwork.send(p, new ModNetwork.ViewPacket(ModNetwork.V_RADARS, rank(p), msg, ok, "", 0, 1, pending(d),
                List.of(), null, List.of(), List.of(), List.of(), out, List.of()));
    }

    /** Onglet IMMAT. : fichier des immatriculations du mod Véhicules (300 lignes max), filtré par plaque, nom ou modèle. */
    private static void sendPlates(ServerPlayer p, String query, String msg, boolean ok) {
        MinecraftServer s = p.server;
        PoliceData d = PoliceData.get(s);
        String q = query.toLowerCase(Locale.ROOT).trim();
        String qp = q.replaceAll("[\\s\\-]", "");
        List<ModNetwork.PlateView> out = new ArrayList<>();
        for (Compat.Plate pl : Compat.plates(s)) {
            if (out.size() >= 300) break;
            String owner = pl.owner == null ? "" : rpName(s, pl.owner);
            if (owner.isBlank()) owner = (pl.lastName.toUpperCase(Locale.ROOT) + " " + pl.firstName).trim();
            if (owner.isBlank()) owner = pl.ownerName;
            String plateNorm = pl.plate.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
            if (!q.isEmpty() && !(!qp.isEmpty() && plateNorm.contains(qp))
                    && !(owner + " " + pl.ownerName + " " + pl.model).toLowerCase(Locale.ROOT).contains(q)) continue;
            boolean known = false;
            if (pl.owner != null) {
                // Le propriétaire devient un citoyen du fichier police : son dossier est ouvrable depuis la ligne.
                if (!d.names.containsKey(pl.owner)) {
                    d.names.put(pl.owner, pl.ownerName.isBlank() ? pl.owner.toString().substring(0, 8) : pl.ownerName);
                    d.setDirty();
                }
                known = true;
            }
            String[] id = pl.owner == null ? null : Compat.identity(s, pl.owner);
            String birth = id != null ? id[2] : pl.birthDate;
            out.add(new ModNetwork.PlateView(pl.plate, pl.model + (pl.color.isEmpty() ? "" : " (" + pl.color + ")"),
                    pl.owner == null ? ModNetwork.NONE : pl.owner, pl.ownerName, owner, birth == null ? "" : birth, pl.time, pl.shop, known));
        }
        ModNetwork.send(p, new ModNetwork.ViewPacket(ModNetwork.V_PLATES, rank(p), msg, ok, query, 0, 1, pending(d),
                List.of(), null, List.of(), List.of(), List.of(), List.of(), out));
    }

    /** Enregistre un flash de radar fixe (visible dans l'onglet RADARS de la tablette) : plaque uniquement, pas de conducteur. */
    public static void recordFlash(MinecraftServer s, String plate, String model, int speed, int limit, String where) {
        PoliceData d = PoliceData.get(s);
        PoliceData.Flash f = new PoliceData.Flash();
        f.id = d.nextId(); f.time = System.currentTimeMillis(); f.plate = plate == null ? "" : plate; f.model = model == null ? "" : model;
        f.speed = speed; f.limit = limit; f.where = where;
        d.flashes.add(f);
        while (d.flashes.size() > PoliceConfig.get().radar_historique_max) d.flashes.remove(0);
        d.setDirty();
    }

    private static void sendList(ServerPlayer p, String msg, boolean ok) {
        MinecraftServer s = p.server;
        PoliceData d = PoliceData.get(s);
        Session ses = SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
        String q = ses.query.toLowerCase(Locale.ROOT).trim();
        List<ModNetwork.Citizen> all = new ArrayList<>();
        for (Map.Entry<UUID, String> e : d.names.entrySet()) {
            String rp = rpName(s, e.getKey());
            if (!q.isEmpty() && !e.getValue().toLowerCase(Locale.ROOT).contains(q) && !rp.toLowerCase(Locale.ROOT).contains(q)) continue;
            all.add(new ModNetwork.Citizen(e.getKey(), e.getValue(), rp, s.getPlayerList().getPlayer(e.getKey()) != null, d.wanted.containsKey(e.getKey())));
        }
        // Recherchés d'abord, puis connectés, puis ordre alphabétique.
        all.sort(Comparator.comparing((ModNetwork.Citizen c) -> !c.wanted()).thenComparing(c -> !c.online())
                .thenComparing(c -> c.name().toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        ses.page = Math.max(0, Math.min(pages - 1, ses.page));
        List<ModNetwork.Citizen> rows = new ArrayList<>(all.subList(ses.page * PAGE, Math.min(all.size(), ses.page * PAGE + PAGE)));
        ModNetwork.send(p, packet(p, ModNetwork.V_LIST, msg, ok, ses.query, ses.page, pages, rows, null, List.of(), List.of(), List.of()));
    }

    private static boolean near(ServerPlayer officer, ServerPlayer target) {
        double max = PoliceConfig.get().distance_saisie;
        return target != null && target != officer && officer.level() == target.level() && officer.distanceToSqr(target) <= max * max;
    }

    private static ModNetwork.Dossier dossier(ServerPlayer p, UUID target) {
        MinecraftServer s = p.server;
        PoliceData d = PoliceData.get(s);
        ServerPlayer on = s.getPlayerList().getPlayer(target);
        String[] identity = Compat.identity(s, target);
        List<ModNetwork.RecView> recs = new ArrayList<>();
        long unpaid = 0;
        List<Rec> list = d.recordsOf(target);
        // Les plus récentes d'abord ; on limite l'envoi aux 120 dernières entrées.
        for (int i = list.size() - 1; i >= 0 && recs.size() < 120; i--) {
            Rec r = list.get(i);
            recs.add(new ModNetwork.RecView(r.id, r.type, r.time, r.officer, r.text, r.amount, r.points, r.paid));
        }
        for (Rec r : list) if (r.type == PoliceData.AMENDE && !r.paid) unpaid += r.amount;
        return new ModNetwork.Dossier(target, display(s, target), identity == null ? List.of() : List.of(identity), on != null, near(p, on),
                d.wanted.getOrDefault(target, ""), Compat.points(s, target), Compat.licences(s, target), Compat.impound(s, target),
                recs, d.searchMinutesLeft(target), unpaid, Compat.hasPermis(), Compat.hasVehicles(), Compat.registered(s, target));
    }

    private static void sendDossier(ServerPlayer p, UUID target, String msg, boolean ok) {
        ModNetwork.send(p, packet(p, ModNetwork.V_DOSSIER, msg, ok, "", 0, 1, List.of(), dossier(p, target), List.of(), List.of(), List.of()));
    }

    private static void sendRequests(ServerPlayer p, String msg, boolean ok) {
        PoliceData d = PoliceData.get(p.server);
        List<ModNetwork.ReqView> out = new ArrayList<>();
        // En attente d'abord, puis les plus récentes.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = d.requests.size() - 1; i >= 0 && out.size() < 60; i--) {
                Req r = d.requests.get(i);
                if ((r.status == PoliceData.PENDING) == (pass == 0)) {
                    out.add(new ModNetwork.ReqView(r.id, r.type, display(p.server, r.target), display(p.server, r.by), r.reason, r.time, r.status, r.decidedBy));
                }
            }
        }
        ModNetwork.send(p, packet(p, ModNetwork.V_REQUESTS, msg, ok, "", 0, 1, List.of(), null, out, List.of(), List.of()));
    }

    private static void sendRoster(ServerPlayer p, String msg, boolean ok) {
        PoliceData d = PoliceData.get(p.server);
        List<ModNetwork.Officer> out = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : d.officers.entrySet()) {
            out.add(new ModNetwork.Officer(e.getKey(), display(p.server, e.getKey()), Math.max(0, Math.min(2, e.getValue())),
                    p.server.getPlayerList().getPlayer(e.getKey()) != null));
        }
        out.sort(Comparator.comparingInt(ModNetwork.Officer::grade).thenComparing(o -> o.name().toLowerCase(Locale.ROOT)));
        ModNetwork.send(p, packet(p, ModNetwork.V_ROSTER, msg, ok, "", 0, 1, List.of(), null, List.of(), out, List.of()));
    }

    private static void sendInventory(ServerPlayer p, UUID target, String msg, boolean ok) {
        ServerPlayer on = p.server.getPlayerList().getPlayer(target);
        if (!near(p, on)) {
            sendDossier(p, target, msg.isEmpty() ? "Le citoyen doit être à moins de " + PoliceConfig.get().distance_saisie + " blocs de vous." : msg, !msg.isEmpty() && ok);
            return;
        }
        List<ModNetwork.Inv> items = new ArrayList<>();
        for (int i = 0; i < on.getInventory().getContainerSize(); i++) {
            ItemStack st = on.getInventory().getItem(i);
            if (!st.isEmpty()) items.add(new ModNetwork.Inv(i, st.getHoverName().getString(), st.getCount()));
        }
        ModNetwork.send(p, packet(p, ModNetwork.V_INVENTORY, msg, ok, "", 0, 1, List.of(), dossier(p, target), List.of(), List.of(), items));
    }

    // ------------------------------------------------------------------ outils
    private static void tell(MinecraftServer s, UUID id, String text) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) p.sendSystemMessage(Component.literal(text));
    }

    private static void tellPolice(MinecraftServer s, String text) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (rank(p) >= 0) p.sendSystemMessage(Component.literal(text));
    }

    private static long euros(String text) {
        try {
            String v = text == null ? "" : text.trim().replace(',', '.').replace("€", "").trim();
            return v.isEmpty() ? -1 : Math.round(Double.parseDouble(v) * 100.0);
        } catch (NumberFormatException e) { return -1; }
    }

    private static String money(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + (a % 100 == 0 ? "" : "," + String.format("%02d", a % 100)) + " €";
    }

    private static String clean(String v) { return v == null ? "" : v.trim().replaceAll("\\s+", " "); }

    private static Rec record(PoliceData d, UUID citizen, int type, ServerPlayer officer, String text) {
        return entry(d, citizen, type, display(officer.server, officer.getUUID()), text);
    }

    private static Rec entry(PoliceData d, UUID citizen, int type, String officer, String text) {
        Rec r = new Rec();
        r.id = d.nextId(); r.type = type; r.time = System.currentTimeMillis();
        r.officer = officer; r.text = text;
        d.recordsOf(citizen).add(r);
        d.setDirty();
        return r;
    }

    /** Inscrit une entrée au dossier d'un citoyen au nom d'un agent ou d'un système (ex. « Radar automatique »). */
    public static void addEntry(MinecraftServer s, UUID citizen, String citizenName, int type, String officer, String text) {
        PoliceData d = PoliceData.get(s);
        if (!d.names.containsKey(citizen) && citizenName != null && !citizenName.isBlank()) d.names.put(citizen, citizenName);
        entry(d, citizen, type, officer, text);
    }

    /**
     * Amende automatique (radar fixe) : inscrite au casier, prélevée si le compte le permet (sinon plus tard),
     * points retirés si le citoyen a un permis de conduire. Prévient le citoyen. Renvoie un résumé pour la police.
     */
    public static String autoFine(MinecraftServer s, UUID citizen, String citizenName, long cents, int points, String reason, String officer) {
        PoliceData d = PoliceData.get(s);
        if (!d.names.containsKey(citizen) && citizenName != null && !citizenName.isBlank()) d.names.put(citizen, citizenName);
        Rec r = entry(d, citizen, PoliceData.AMENDE, officer, reason);
        r.amount = Math.max(0, cents);
        String pointsInfo = "";
        if (points > 0 && Compat.hasPermis() && Compat.hasDrivingLicence(s, citizen)) {
            int left = Compat.removePoints(s, citizen, points);
            if (left >= 0) { r.points = points; pointsInfo = " • -" + points + " point(s) (reste " + left + ")"; }
        }
        boolean paid = r.amount > 0 && tryPay(s, citizen, r);
        if (r.amount == 0) r.paid = true;
        tell(s, citizen, "§cAmende de " + money(r.amount) + " : " + reason + pointsInfo + (paid ? " §7(prélevée sur votre compte)"
                : r.amount == 0 ? "" : " §7(impayée : elle sera prélevée dès que votre compte le permet)"));
        return money(r.amount) + (paid ? " payée" : " impayée") + pointsInfo;
    }

    public static String euroText(long cents) { return money(cents); }

    /** Donne le kit d'équipement de la config (radar, détecteur, taser, cartouches, tests). Renvoie le nombre d'objets donnés. */
    public static int giveKit(ServerPlayer p) {
        int n = 0;
        for (String line : PoliceConfig.get().kit_equipement) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.trim().split("\\s+");
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(parts[0]);
            if (id == null) continue;
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            int count = 1;
            try { if (parts.length > 1) count = Math.max(1, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) {}
            while (count > 0) {
                ItemStack st = new ItemStack(item, Math.min(count, item.getMaxStackSize()));
                count -= st.getCount();
                n += st.getCount();
                if (!p.getInventory().add(st)) p.drop(st, false);
            }
        }
        return n;
    }

    /** Message à tous les policiers connectés. */
    public static void alertPolice(MinecraftServer s, String text) { tellPolice(s, text); }

    /** Prélève l'amende sur le compte bancaire si le solde le permet ; l'argent part au trésor public (MineNorth API). */
    private static boolean tryPay(MinecraftServer s, UUID citizen, Rec fine) {
        if (fine.paid) return false;
        if (!fr.minenorth.api.MineNorth.bank().debit(s, citizen, fine.amount, "police:amende", false).ok()) return false;
        fine.paid = true;
        return true;
    }

    // ------------------------------------------------------------------ actions
    public static void handle(ServerPlayer p, ActionPacket k) {
        if (k.action() == ModNetwork.A_CLOSE) { SESSIONS.remove(p.getUUID()); return; }
        int rank = rank(p);
        // Il faut être policier ET avoir ouvert la tablette.
        if (rank < 0 || !SESSIONS.containsKey(p.getUUID())) return;
        MinecraftServer s = p.server;
        PoliceData d = PoliceData.get(s);
        UUID t = k.target();
        boolean known = d.names.containsKey(t);
        Session ses = SESSIONS.get(p.getUUID());

        switch (k.action()) {
            case ModNetwork.A_LIST -> { ses.query = clean(k.a()); ses.page = Math.max(0, k.n()); sendList(p, "", true); }
            case ModNetwork.A_OPEN -> { if (known) sendDossier(p, t, "", true); }
            case ModNetwork.A_REQUESTS -> sendRequests(p, "", true);
            case ModNetwork.A_ROSTER -> { if (rank == PoliceData.COMMISSAIRE) sendRoster(p, "", true); }
            case ModNetwork.A_INVENTORY -> { if (known) sendInventory(p, t, "", true); }
            case ModNetwork.A_FINE -> { if (known) { R r = fine(p, rank, d, t, clean(k.a()), k.b(), k.n()); sendDossier(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_RECORD -> { if (known) { R r = addRecord(p, rank, d, t, k.n(), clean(k.a())); sendDossier(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_DELETE -> { if (known) { R r = deleteRecord(rank, d, t, k.n()); sendDossier(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_WANTED -> { if (known) { R r = wanted(p, rank, d, t, clean(k.a())); sendDossier(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_REQUEST -> { if (known) { R r = request(p, rank, d, t, k.n(), clean(k.a())); sendDossier(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_DECIDE -> { R r = decide(p, rank, d, k.n(), k.m() == 1); sendRequests(p, r.msg(), r.ok()); }
            case ModNetwork.A_SEIZE -> { if (known) { R r = seize(p, d, t, k.n()); sendInventory(p, t, r.msg(), r.ok()); } }
            case ModNetwork.A_GRADE -> { R r = grade(p, rank, d, t, clean(k.a()), k.n()); sendRoster(p, r.msg(), r.ok()); }
            case ModNetwork.A_RADARS -> sendRadars(p, "", true);
            // Bureau du commissariat : c'est le mod Accueil Police qui affiche son propre écran à la place de la tablette.
            case ModNetwork.A_DESK -> { if (!Compat.openAccueilDesk(p)) sendList(p, "Le mod Accueil Police n'est pas installé sur le serveur.", false); }
            case ModNetwork.A_PLATES -> {
                if (!Compat.hasVehicles()) { sendPlates(p, "", "Mod Véhicules non installé : pas de fichier des immatriculations.", false); break; }
                sendPlates(p, clean(k.a()), "", true);
            }
            case ModNetwork.A_FLASH_DELETE -> {
                if (rank != PoliceData.COMMISSAIRE) { sendRadars(p, "Seul le Commissaire peut supprimer un flash.", false); break; }
                boolean removed = d.flashes.removeIf(f -> f.id == k.n());
                if (removed) d.setDirty();
                sendRadars(p, removed ? "Flash supprimé." : "Flash introuvable.", removed);
            }
            default -> {}
        }
    }

    private static R fine(ServerPlayer p, int rank, PoliceData d, UUID t, String reason, String amountText, int points) {
        PoliceConfig cfg = PoliceConfig.get();
        if (reason.length() < 3) return R.err("Indiquez le motif de l'amende.");
        long cents = euros(amountText);
        if (cents <= 0) return R.err("Montant invalide.");
        if (cents > cfg.maxFineCents()) return R.err("Montant maximum : " + money(cfg.maxFineCents()) + ".");
        if (points < 0 || points > cfg.points_max_par_amende) return R.err("Retrait de points : entre 0 et " + cfg.points_max_par_amende + ".");
        if (points > 0 && rank > PoliceData.OFFICIER) return R.err("Seuls les Officiers et le Commissaire peuvent retirer des points.");
        if (points > 0 && !Compat.hasPermis()) return R.err("Le mod Permis n'est pas installé : retrait de points impossible.");
        if (points > 0 && !Compat.hasDrivingLicence(p.server, t)) return R.err("Ce citoyen n'a pas de permis de conduire : aucun point à retirer.");

        MinecraftServer s = p.server;
        Rec r = record(d, t, PoliceData.AMENDE, p, reason);
        r.amount = cents; r.points = points;
        boolean paid = tryPay(s, t, r);
        String pointsInfo = "";
        if (points > 0) {
            int left = Compat.removePoints(s, t, points);
            pointsInfo = " • -" + points + " point(s)" + (left >= 0 ? " (reste " + left + ")" : "");
        }
        tell(s, t, "§cAmende de " + money(cents) + " : " + reason + pointsInfo + (paid ? " §7(prélevée sur votre compte)"
                : " §7(impayée : elle sera prélevée dès que votre compte le permet)"));
        return R.ok("Amende de " + money(cents) + (paid ? " payée" : " enregistrée (impayée)") + pointsInfo + ".");
    }

    private static R addRecord(ServerPlayer p, int rank, PoliceData d, UUID t, int type, String text) {
        if (type != PoliceData.CONDAMNATION && type != PoliceData.NOTE) return R.err("Type invalide.");
        if (text.length() < 3) return R.err("Indiquez un texte dans « motif ».");
        if (type == PoliceData.CONDAMNATION && rank > PoliceData.OFFICIER) return R.err("Seuls les Officiers et le Commissaire peuvent ajouter une condamnation.");
        record(d, t, type, p, text);
        if (type == PoliceData.CONDAMNATION) tell(p.server, t, "§cUne condamnation a été inscrite à votre casier : " + text);
        return R.ok(type == PoliceData.CONDAMNATION ? "Condamnation inscrite au casier." : "Note ajoutée au dossier.");
    }

    private static R deleteRecord(int rank, PoliceData d, UUID t, int id) {
        if (rank != PoliceData.COMMISSAIRE) return R.err("Seul le Commissaire peut supprimer une entrée du casier.");
        if (!d.recordsOf(t).removeIf(r -> r.id == id)) return R.err("Entrée introuvable.");
        d.setDirty();
        return R.ok("Entrée supprimée du casier.");
    }

    private static R wanted(ServerPlayer p, int rank, PoliceData d, UUID t, String reason) {
        if (rank > PoliceData.OFFICIER) return R.err("Seuls les Officiers et le Commissaire gèrent les avis de recherche.");
        if (d.wanted.remove(t) != null) {
            d.setDirty();
            tellPolice(p.server, "§b[Police] Avis de recherche levé pour " + display(p.server, t) + ".");
            return R.ok("Avis de recherche levé.");
        }
        if (reason.length() < 3) return R.err("Indiquez le motif de l'avis de recherche.");
        d.wanted.put(t, reason);
        d.setDirty();
        tellPolice(p.server, "§c[Police] AVIS DE RECHERCHE : " + display(p.server, t) + " — " + reason);
        return R.ok("Avis de recherche lancé.");
    }

    private static R request(ServerPlayer p, int rank, PoliceData d, UUID t, int type, String reason) {
        if (type != PoliceData.REQ_PERQUISITION && type != PoliceData.REQ_PERMIS) return R.err("Type invalide.");
        if (reason.length() < 3) return R.err("Indiquez le motif de la demande.");
        if (type == PoliceData.REQ_PERMIS && !Compat.hasPermis()) return R.err("Le mod Permis n'est pas installé.");
        if (type == PoliceData.REQ_PERMIS && !Compat.hasDrivingLicence(p.server, t)) return R.err("Ce citoyen n'a pas de permis de conduire.");
        if (type == PoliceData.REQ_PERQUISITION && p.server.getPlayerList().getPlayer(t) == null) return R.err("Perquisition impossible : ce citoyen n'est pas connecté.");
        for (Req r : d.requests) {
            if (r.status == PoliceData.PENDING && r.type == type && t.equals(r.target)) return R.err("Une demande identique est déjà en attente.");
        }
        Req q = new Req();
        q.id = d.nextId(); q.type = type; q.target = t; q.targetName = display(p.server, t);
        q.by = p.getUUID(); q.byName = display(p.server, p.getUUID()); q.reason = reason; q.time = System.currentTimeMillis();
        d.requests.add(q);
        while (d.requests.size() > 100) d.requests.remove(0);
        d.setDirty();
        // Le Commissaire n'a pas à se valider lui-même.
        if (rank == PoliceData.COMMISSAIRE) return decide(p, rank, d, q.id, true);
        String what = type == PoliceData.REQ_PERQUISITION ? "perquisition" : "annulation de permis";
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (rank(o) == PoliceData.COMMISSAIRE) o.sendSystemMessage(Component.literal("§e[Police] " + q.byName + " demande une " + what
                    + " pour " + q.targetName + " : " + reason));
        }
        return R.ok("Demande de " + what + " envoyée au Commissaire.");
    }

    private static R decide(ServerPlayer p, int rank, PoliceData d, int id, boolean accept) {
        if (rank != PoliceData.COMMISSAIRE) return R.err("Seul le Commissaire traite les demandes.");
        Req q = null;
        for (Req r : d.requests) if (r.id == id) q = r;
        if (q == null || q.status != PoliceData.PENDING) return R.err("Cette demande a déjà été traitée.");
        MinecraftServer s = p.server;
        boolean search = q.type == PoliceData.REQ_PERQUISITION;
        if (accept && search && s.getPlayerList().getPlayer(q.target) == null) {
            return R.err("Perquisition impossible : " + q.targetName + " n'est pas connecté. Réessayez quand il sera en ligne.");
        }
        q.decidedBy = display(s, p.getUUID());
        d.setDirty();
        if (!accept) {
            q.status = PoliceData.REFUSED;
            tell(s, q.by, "§e[Police] Votre demande de " + (search ? "perquisition" : "annulation de permis") + " pour " + q.targetName + " a été refusée.");
            return R.ok("Demande refusée.");
        }
        q.status = PoliceData.ACCEPTED;
        if (search) {
            int minutes = PoliceConfig.get().duree_perquisition_minutes;
            q.expires = System.currentTimeMillis() + minutes * 60_000L;
            record(d, q.target, PoliceData.PERQUISITION, p, "Perquisition autorisée (" + minutes + " min) : " + q.reason);
            tellPolice(s, "§b[Police] Perquisition autorisée chez " + q.targetName + " pendant " + minutes + " min : ses portes sont accessibles à la police.");
            return R.ok("Perquisition autorisée pour " + minutes + " min.");
        }
        if (!Compat.revokeDriving(s, q.target)) { q.status = PoliceData.PENDING; return R.err("Annulation impossible : mod Permis indisponible."); }
        record(d, q.target, PoliceData.PERMIS, p, "Permis de conduire annulé : " + q.reason);
        tell(s, q.target, "§cVotre permis de conduire a été annulé par la police : " + q.reason);
        tell(s, q.by, "§a[Police] Annulation du permis de " + q.targetName + " acceptée.");
        return R.ok("Permis de conduire de " + q.targetName + " annulé.");
    }

    private static R seize(ServerPlayer p, PoliceData d, UUID t, int slot) {
        ServerPlayer on = p.server.getPlayerList().getPlayer(t);
        if (!near(p, on)) return R.err("Le citoyen doit être à moins de " + PoliceConfig.get().distance_saisie + " blocs de vous.");
        if (slot < 0 || slot >= on.getInventory().getContainerSize()) return R.err("Objet introuvable.");
        ItemStack st = on.getInventory().removeItemNoUpdate(slot);
        if (st.isEmpty()) return R.err("Cet emplacement est vide.");
        String label = st.getCount() + "x " + st.getHoverName().getString();
        if (!p.getInventory().add(st)) p.drop(st, false);
        on.inventoryMenu.broadcastChanges();
        record(d, t, PoliceData.SAISIE, p, label);
        on.sendSystemMessage(Component.literal("§cLa police vous a confisqué : " + label));
        return R.ok("Saisi : " + label + " (dans votre inventaire).");
    }

    private static R grade(ServerPlayer p, int rank, PoliceData d, UUID t, String name, int grade) {
        if (rank != PoliceData.COMMISSAIRE) return R.err("Seul le Commissaire gère les effectifs.");
        UUID id = d.names.containsKey(t) ? t : null;
        if (id == null && !name.isEmpty()) {
            ServerPlayer on = p.server.getPlayerList().getPlayerByName(name);
            id = on != null ? on.getUUID() : d.byName(name);
            if (id == null) {
                for (UUID known : d.names.keySet()) if (rpName(p.server, known).equalsIgnoreCase(name)) { id = known; break; }
            }
        }
        if (id == null) return R.err("Joueur introuvable (il doit s'être déjà connecté au serveur).");
        if (id.equals(p.getUUID())) return R.err("Vous ne pouvez pas modifier votre propre grade.");
        if (grade > 2) return R.err("Grade invalide.");
        String shown = display(p.server, id);
        String result = setGrade(p.server, id, d.name(id), grade);
        return R.ok(result.replace(d.name(id), shown));
    }

    // ------------------------------------------------------------------ événements
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PoliceData d = PoliceData.get(p.server);
        String name = p.getGameProfile().getName();
        if (!name.equals(d.names.get(p.getUUID()))) { d.names.put(p.getUUID(), name); d.setDirty(); }
        if (d.officers.containsKey(p.getUUID())) syncTag(p, true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        SESSIONS.remove(e.getEntity().getUUID());
    }

    /** Toutes les minutes : nouvelle tentative de prélèvement des amendes impayées. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 1200 != 0) return;
        MinecraftServer s = e.getServer();
        PoliceData d = PoliceData.get(s);
        for (Map.Entry<UUID, List<Rec>> en : d.records.entrySet()) {
            for (Rec r : en.getValue()) {
                if (r.type == PoliceData.AMENDE && !r.paid && tryPay(s, en.getKey(), r)) {
                    d.setDirty();
                    tell(s, en.getKey(), "§eAmende impayée de " + money(r.amount) + " prélevée sur votre compte (" + r.text + ").");
                }
            }
        }
    }
}
