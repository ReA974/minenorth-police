package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import fr.minenorth.police.network.ModNetwork.Dossier;
import fr.minenorth.police.network.ModNetwork.RecView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Tablette de police (charte MineNorth / Permis). Toutes les données viennent du serveur, qui revérifie chaque droit. */
public class PoliceScreen extends Screen {
    private static final int W = 420, H = 260, ROWS = 6;
    private static final String[] GRADES = {"Commissaire", "Officier", "Sous-officier"};
    private static final String[] TYPES = {"AMENDE", "CONDAMNATION", "SAISIE", "NOTE", "PERMIS", "PERQUISITION", "GARDE À VUE"};
    private static final String[] JAIL = {"EN GARDE À VUE", "EN PRISON"};

    private ModNetwork.ViewPacket v;
    private int left, top;
    /** Onglet du dossier : 0 fiche, 1 casier, 2 permis/véhicules, 3 actions. */
    private int tab;
    /** Page locale (casier, inventaire, demandes, effectifs). */
    private int page;
    private String message = "";
    private boolean messageOk = true;

    private EditBox bSearch, bReason, bAmount, bPoints, bName, bPlate, bImmat, bDuration, bMsg;
    private String kSearch = "", kReason = "", kAmount = "", kPoints = "", kName = "", kPlate = "", kImmat = "", kDuration = "", kMsg = "";
    /** Policier vise par le prochain message du dispatch (null = tous les policiers en service). */
    private UUID dispatchTarget;
    private static final int RED = 0xFFB3263E;

    private record Label(String text, int x, int y, int color) {}
    private record Card(int x, int y, int w, int h, int accent) {}
    private final List<Label> labels = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private record Face(UUID id, String pseudo, int x, int y, int size) {}
    private final List<Face> faces = new ArrayList<>();
    /** Pseudos vus dans la liste : sert à retrouver le skin d'un citoyen déconnecté dans son dossier. */
    private final java.util.Map<UUID, String> pseudos = new java.util.HashMap<>();

    public PoliceScreen(ModNetwork.ViewPacket v) {
        super(Component.literal("Police"));
        this.v = v;
        this.kSearch = v.query();
    }

    public void update(ModNetwork.ViewPacket n) {
        keep();
        boolean sameDossier = v.dossier() != null && n.dossier() != null && v.dossier().id().equals(n.dossier().id());
        if (n.view() != v.view()) page = 0;
        if (n.view() == ModNetwork.V_DOSSIER && !sameDossier) { tab = 0; page = 0; kReason = kAmount = kPoints = kDuration = ""; }
        this.v = n;
        this.message = n.message();
        this.messageOk = n.ok();
        if (n.ok() && !n.message().isEmpty()) { kAmount = kPoints = kDuration = ""; kName = ""; kMsg = ""; if (n.view() != ModNetwork.V_INVENTORY) kReason = ""; }
        if (n.view() == ModNetwork.V_LIST) kSearch = n.query();
        if (n.view() == ModNetwork.V_PLATES) kImmat = n.query();
        rebuild();
    }

    // ------------------------------------------------------------------ outils
    private void send(int action, UUID target, String a, String b, int n, int m) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target,
                cut(a, 96), cut(b, 32), n, m));
    }
    private static String cut(String s, int max) { return s == null ? "" : s.length() > max ? s.substring(0, max) : s; }
    private static int number(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return 0; } }

    private void keep() {
        if (bSearch != null) kSearch = bSearch.getValue();
        if (bReason != null) kReason = bReason.getValue();
        if (bAmount != null) kAmount = bAmount.getValue();
        if (bPoints != null) kPoints = bPoints.getValue();
        if (bName != null) kName = bName.getValue();
        if (bPlate != null) kPlate = bPlate.getValue();
        if (bImmat != null) kImmat = bImmat.getValue();
        if (bDuration != null) kDuration = bDuration.getValue();
        if (bMsg != null) kMsg = bMsg.getValue();
    }
    private void rebuild() { clearWidgets(); init(); }
    private void go(Runnable change) { keep(); message = ""; change.run(); rebuild(); }

    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }
    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        return b;
    }
    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }
    private void label(String text, int x, int y, int color, int maxWidth) { label(font.plainSubstrByWidth(text, maxWidth), x, y, color); }
    private void kv(String key, String value, int x, int y, int maxWidth) {
        label(key, x, y, MineNorthStyle.BLUE);
        label(value, x + font.width(key) + 4, y, MineNorthStyle.TEXT, maxWidth - font.width(key) - 4);
    }
    private void card(int x, int y, int w, int h, int accent) { cards.add(new Card(x, y, w, h, accent)); }

    private void pager(int pages, int right, int y, Runnable prev, Runnable next, int current) {
        if (pages <= 1) return;
        btn(right - 78, y, 20, 18, "<", MineNorthStyle.DARK, prev).enabled(current > 0);
        String p = (current + 1) + " / " + pages;
        label(p, right - 39 - font.width(p) / 2, y + 5, MineNorthStyle.TEXT);
        btn(right - 20, y, 20, 18, ">", MineNorthStyle.DARK, next).enabled(current < pages - 1);
    }
    /** Pagination locale d'une liste déjà reçue en entier. */
    private int localPages(int size) {
        int pages = Math.max(1, (size + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        return pages;
    }
    private void localPager(int pages) { pager(pages, left + W - 14, top + H - 38, () -> go(() -> page--), () -> go(() -> page++), page); }

    /** Nom RP (prénom + nom) ; le pseudo Minecraft ne sert que si le citoyen n'a pas de carte d'identité. */
    private String displayName(String rp, String name) { return rp == null || rp.isBlank() ? name : rp; }
    private void face(UUID id, String pseudo, int x, int y, int size) { faces.add(new Face(id, pseudo, x, y, size)); }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        labels.clear();
        cards.clear();
        faces.clear();
        bSearch = bReason = bAmount = bPoints = bName = bPlate = bImmat = bDuration = bMsg = null;

        boolean inDossier = v.view() == ModNetwork.V_DOSSIER || v.view() == ModNetwork.V_INVENTORY;
        btn(left + W - 100, top + 10, 86, 16, inDossier ? "Retour" : "Fermer", MineNorthButton.GHOST, this::back);

        int x = left + 14, w = W - 28;
        if (inDossier && v.dossier() != null) {
            if (v.view() == ModNetwork.V_INVENTORY) buildInventory(v.dossier(), x, w);
            else buildDossier(v.dossier(), x, w);
            return;
        }
        // Service : chaque policier prend ou quitte son poste quand il le souhaite.
        boolean duty = v.duty().onDuty();
        btn(left + W - 212, top + 9, 104, 18, duty ? "FIN DE SERVICE" : "PRENDRE SON POSTE", duty ? RED : MineNorthStyle.GREEN,
                () -> { keep(); send(ModNetwork.A_DUTY, null, "", "", v.view(), 0); });
        // Navigation principale : les largeurs s'adaptent au nombre d'onglets (DISPATCH pour le plus haut gradé en service).
        String req = v.pendingRequests() > 0 ? "DEMANDES (" + v.pendingRequests() + ")" : "DEMANDES";
        List<Nav> nav = new ArrayList<>();
        nav.add(new Nav("CITOYENS", v.view() == ModNetwork.V_LIST, () -> { keep(); send(ModNetwork.A_LIST, null, kSearch, "", 0, 0); }));
        nav.add(new Nav(req, v.view() == ModNetwork.V_REQUESTS, () -> send(ModNetwork.A_REQUESTS, null, "", "", 0, 0)));
        nav.add(new Nav("RADARS", v.view() == ModNetwork.V_RADARS, () -> send(ModNetwork.A_RADARS, null, "", "", 0, 0)));
        nav.add(new Nav("IMMAT.", v.view() == ModNetwork.V_PLATES,
                () -> { keep(); send(ModNetwork.A_PLATES, null, v.view() == ModNetwork.V_PLATES ? kImmat : "", "", 0, 0); }));
        // Ouvre le bureau du mod Accueil Police : plaintes, historique, rendez-vous, objets trouvés, fourrière.
        nav.add(new Nav("BUREAU", false, () -> send(ModNetwork.A_DESK, null, "", "", 0, 0)));
        if (v.grade() == 0) nav.add(new Nav("EFFECTIFS", v.view() == ModNetwork.V_ROSTER, () -> send(ModNetwork.A_ROSTER, null, "", "", 0, 0)));
        if (v.duty().dispatcher()) nav.add(new Nav("DISPATCH", v.view() == ModNetwork.V_DISPATCH, () -> send(ModNetwork.A_DISPATCH, null, "", "", 0, 0)));
        int pad = 12, gap = 3;
        while (pad > 4 && totalWidth(nav, pad, gap) > w) pad--;
        int tx = x;
        for (Nav n : nav) {
            int bw = font.width(n.label()) + pad;
            btn(tx, top + 46, bw, 18, n.label(), n.current() ? MineNorthStyle.CYAN : MineNorthStyle.DARK, n.run());
            tx += bw + gap;
        }
        String g = "Grade : " + GRADES[Math.max(0, Math.min(2, v.grade()))];
        label(g, left + W - 14 - font.width(g), top + 29, MineNorthStyle.MUTED);

        if (v.view() == ModNetwork.V_REQUESTS) buildRequests(x, w);
        else if (v.view() == ModNetwork.V_ROSTER) buildRoster(x, w);
        else if (v.view() == ModNetwork.V_RADARS) buildRadars(x, w);
        else if (v.view() == ModNetwork.V_PLATES) buildPlates(x, w);
        else if (v.view() == ModNetwork.V_DISPATCH) buildDispatch(x, w);
        else buildList(x, w);
    }

    private record Nav(String label, boolean current, Runnable run) {}

    private int totalWidth(List<Nav> nav, int pad, int gap) {
        int t = gap * (nav.size() - 1);
        for (Nav n : nav) t += font.width(n.label()) + pad;
        return t;
    }

    private void back() {
        if (v.view() == ModNetwork.V_INVENTORY && v.dossier() != null) send(ModNetwork.A_OPEN, v.dossier().id(), "", "", 0, 0);
        else if (v.view() == ModNetwork.V_DOSSIER) send(ModNetwork.A_LIST, null, kSearch, "", 0, 0);
        else onClose();
    }

    // ---------- liste des citoyens
    private void buildList(int x, int w) {
        int y0 = top + 72;
        bSearch = box(x, y0, 220, "Nom ou prénom", kSearch, 32);
        btn(x + 226, y0 - 1, 96, 20, "RECHERCHER", MineNorthStyle.CYAN, () -> send(ModNetwork.A_LIST, null, bSearch.getValue(), "", 0, 0));
        if (v.citizens().isEmpty()) label("Aucun citoyen trouvé.", x, y0 + 32, MineNorthStyle.MUTED);
        for (int i = 0; i < v.citizens().size() && i < ROWS; i++) {
            ModNetwork.Citizen c = v.citizens().get(i);
            int y = y0 + 26 + i * 20;
            card(x, y, w, 18, c.wanted() ? MineNorthStyle.ALERT : c.online() ? MineNorthStyle.OK : MineNorthStyle.DARK);
            pseudos.put(c.id(), c.name());
            face(c.id(), c.name(), x + 6, y + 2, 14);
            label(displayName(c.rpName(), c.name()), x + 26, y + 5, MineNorthStyle.WHITE, 196);
            if (c.wanted()) label("RECHERCHÉ", x + 230, y + 5, MineNorthStyle.ALERT);
            else label(c.online() ? "en ligne" : "hors ligne", x + 230, y + 5, c.online() ? MineNorthStyle.OK : MineNorthStyle.MUTED);
            btn(x + w - 64, y + 2, 62, 14, "DOSSIER", MineNorthStyle.DARK, () -> { keep(); send(ModNetwork.A_OPEN, c.id(), "", "", 0, 0); });
        }
        pager(v.pages(), x + w, top + H - 38,
                () -> { keep(); send(ModNetwork.A_LIST, null, kSearch, "", v.page() - 1, 0); },
                () -> { keep(); send(ModNetwork.A_LIST, null, kSearch, "", v.page() + 1, 0); }, v.page());
    }

    // ---------- dossier d'un citoyen
    private void buildDossier(Dossier d, int x, int w) {
        String[] tabs = {"FICHE", "CASIER", "PERMIS / VÉHICULES", "ACTIONS", "TRANSPORT"};
        int[] widths = {50, 56, 110, 62, 74};
        int tx = x;
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            btn(tx, top + 46, widths[i], 18, tabs[i], tab == i ? MineNorthStyle.CYAN : MineNorthStyle.DARK, () -> go(() -> { tab = t; page = 0; }));
            tx += widths[i] + 4;
        }
        int y0 = top + 72;
        if (tab == 1) buildCasier(d, x, y0, w);
        else if (tab == 2) buildPermis(d, x, y0, w);
        else if (tab == 3) buildActions(d, x, y0, w);
        else if (tab == 4) buildTransport(d, x, y0, w);
        else buildFiche(d, x, y0, w);
    }

    // ---------- transport : installer le suspect à l'arrière d'un véhicule de police
    private void buildTransport(Dossier d, int x, int y0, int w) {
        label("TRANSPORT À L'ARRIÈRE D'UN VÉHICULE DE POLICE", x, y0, MineNorthStyle.BLUE);
        label(d.boarded() ? "Ce citoyen est embarqué : s'il quitte son siège, il y est remis."
                : "Le suspect doit être à portée de vous et un véhicule de police avec place arrière à moins de 8 blocs.", x, y0 + 14, MineNorthStyle.TEXT, w);
        label(d.online() ? (d.near() ? "Suspect : à portée." : "Suspect : trop loin.") : "Suspect : hors ligne.", x, y0 + 30, d.near() ? MineNorthStyle.OK : MineNorthStyle.ALERT);
        label(d.vehicleNear() ? "Véhicule : place arrière disponible." : "Véhicule : aucun à proximité.", x, y0 + 44, d.vehicleNear() ? MineNorthStyle.OK : MineNorthStyle.ALERT);
        int bw = (w - 8) / 2;
        btn(x, y0 + 70, bw, 24, "EMBARQUER LE SUSPECT", MineNorthStyle.PINK, () -> send(ModNetwork.A_BOARD, d.id(), "", "", 1, 0))
                .enabled(!d.boarded() && d.online() && d.near() && d.vehicleNear());
        btn(x + bw + 8, y0 + 70, bw, 24, "FAIRE SORTIR", MineNorthStyle.GREEN, () -> send(ModNetwork.A_BOARD, d.id(), "", "", 0, 0)).enabled(d.boarded());
    }

    private void buildFiche(Dossier d, int x, int y0, int w) {
        List<String> id = d.identity();
        int convictions = 0;
        for (RecView r : d.records()) if (r.type() == 1) convictions++;
        card(x, y0, w, 112, d.wantedReason().isEmpty() ? MineNorthStyle.CYAN : MineNorthStyle.ALERT);
        int lx = x + 10, y = y0 + 8, mw = w - 78;
        face(d.id(), pseudos.get(d.id()), x + w - 58, y0 + 8, 48);
        if (id.size() >= 6) {
            kv("Nom :", id.get(1).toUpperCase(java.util.Locale.ROOT) + "  " + id.get(0), lx, y, mw);
            kv("Né(e) le :", id.get(2) + "  à  " + id.get(3), lx, y + 13, mw);
            kv("Nationalité :", id.get(4), lx, y + 26, mw);
            kv("N° de carte :", id.get(5), lx, y + 39, mw);
        } else {
            label("Aucune carte d'identité enregistrée.", lx, y, MineNorthStyle.WARN);
        }
        kv("Statut :", d.online() ? "en ligne" : "hors ligne (dossier consultable, perquisition et saisie impossibles)", lx, y + 52, mw);
        if (d.points() >= 0) kv("Points du permis :", String.valueOf(d.points()), lx, y + 65, mw);
        else kv("Permis de conduire :", d.permisMod() ? "aucun" : "—", lx, y + 65, mw);
        kv("Casier :", d.records().size() + " entrée(s), dont " + convictions + " condamnation(s)", lx, y + 78, mw);
        kv("Amendes impayées :", d.unpaid() > 0 ? MineNorthStyle.euros(d.unpaid()) : "aucune", lx, y + 91, mw);
        int y2 = y0 + 120;
        if (!d.wantedReason().isEmpty()) { label("AVIS DE RECHERCHE : " + d.wantedReason(), x, y2, MineNorthStyle.ALERT, w); y2 += 13; }
        if (d.searchMinutes() > 0) { label("PERQUISITION AUTORISÉE : ses portes sont accessibles encore " + d.searchMinutes() + " min.", x, y2, MineNorthStyle.WARN, w); y2 += 13; }
        if (d.jailType() >= 0) label(jailText(d), x, y2, MineNorthStyle.ALERT, w);
    }

    private static String jailText(Dossier d) {
        return JAIL[Math.max(0, Math.min(1, d.jailType()))] + " : " + d.jailMinutes() + " min restante(s) (cellule « " + d.jailCell() + " »)";
    }

    private static int typeColor(int type) {
        return switch (type) {
            case 0 -> MineNorthStyle.WARN;
            case 1 -> MineNorthStyle.ALERT;
            case 2 -> MineNorthStyle.PINK;
            case 4, 5 -> MineNorthStyle.CYAN;
            case 6 -> MineNorthStyle.WARN;
            default -> MineNorthStyle.MUTED;
        };
    }

    private void buildCasier(Dossier d, int x, int y0, int w) {
        List<RecView> list = d.records();
        int pages = localPages(list.size());
        if (list.isEmpty()) label("Casier vierge.", x, y0 + 6, MineNorthStyle.OK);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy");
        boolean chief = v.grade() == 0;
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            RecView r = list.get(idx);
            int y = y0 + i * 20, color = typeColor(r.type());
            card(x, y, w, 18, color);
            label(fmt.format(new Date(r.time())), x + 8, y + 5, MineNorthStyle.MUTED);
            label(TYPES[Math.max(0, Math.min(TYPES.length - 1, r.type()))], x + 56, y + 5, color, 76);
            String right = "";
            int rc = MineNorthStyle.MUTED;
            if (r.type() == 0) {
                right = MineNorthStyle.euros(r.amount()) + (r.points() > 0 ? " -" + r.points() + "pt" : "") + (r.paid() ? " payée" : " IMPAYÉE");
                rc = r.paid() ? MineNorthStyle.OK : MineNorthStyle.ALERT;
            } else right = r.officer();
            int rw = Math.min(110, font.width(right));
            int end = x + w - (chief ? 26 : 6);
            label(right, end - rw, y + 5, rc, 110);
            label(r.text(), x + 136, y + 5, MineNorthStyle.TEXT, end - rw - 6 - (x + 136));
            if (chief) btn(x + w - 20, y + 2, 18, 14, "X", MineNorthStyle.PINK, () -> send(ModNetwork.A_DELETE, d.id(), "", "", r.id(), 0));
        }
        localPager(pages);
    }

    private void buildPermis(Dossier d, int x, int y0, int w) {
        int half = (w - 8) / 2;
        card(x, y0, half, 140, MineNorthStyle.CYAN);
        label("PERMIS ET LICENCES", x + 10, y0 + 8, MineNorthStyle.BLUE);
        if (!d.permisMod()) label("Mod Permis non installé.", x + 10, y0 + 24, MineNorthStyle.MUTED);
        else {
            // Les points n'existent que si le citoyen a un permis de conduire (voiture, poids lourd ou moto).
            if (d.points() < 0) label("Pas de permis de conduire.", x + 10, y0 + 22, MineNorthStyle.WARN);
            else label("Points : " + d.points(), x + 10, y0 + 22, d.points() <= 3 ? MineNorthStyle.ALERT : d.points() <= 6 ? MineNorthStyle.WARN : MineNorthStyle.OK);
            if (d.licences().isEmpty()) label("Aucune licence non plus.", x + 10, y0 + 38, MineNorthStyle.MUTED);
            for (int i = 0; i < d.licences().size() && i < 8; i++) label(d.licences().get(i), x + 10, y0 + 38 + i * 12, MineNorthStyle.TEXT, half - 18);
        }
        int rx = x + half + 8;
        card(rx, y0, half, 140, MineNorthStyle.WARN);
        if (!d.vehiclesMod()) {
            label("VÉHICULES", rx + 10, y0 + 8, MineNorthStyle.BLUE);
            label("Mod Véhicules non installé.", rx + 10, y0 + 24, MineNorthStyle.MUTED);
            return;
        }
        label("VÉHICULES IMMATRICULÉS", rx + 10, y0 + 8, MineNorthStyle.BLUE);
        int yy = y0 + 22;
        if (d.vehicles().isEmpty()) { label("Aucun véhicule à son nom.", rx + 10, yy, MineNorthStyle.MUTED); yy += 12; }
        for (int i = 0; i < d.vehicles().size() && i < 5; i++, yy += 12) label("• " + d.vehicles().get(i), rx + 10, yy, MineNorthStyle.TEXT, half - 18);
        if (d.vehicles().size() > 5) { label("+ " + (d.vehicles().size() - 5) + " autre(s) : onglet IMMAT.", rx + 10, yy, MineNorthStyle.MUTED, half - 18); yy += 12; }
        yy += 4;
        label("EN FOURRIÈRE", rx + 10, yy, MineNorthStyle.BLUE);
        yy += 14;
        if (d.impound().isEmpty()) label("Aucun véhicule en fourrière.", rx + 10, yy, MineNorthStyle.MUTED);
        for (int i = 0; i < d.impound().size() && yy <= y0 + 128; i++, yy += 12) label("• " + d.impound().get(i), rx + 10, yy, MineNorthStyle.TEXT, half - 18);
    }

    private void buildActions(Dossier d, int x, int y0, int w) {
        int half = (w - 8) / 2, rx = x + half + 8;
        boolean officer = v.grade() <= 1, chief = v.grade() == 0, driving = d.points() >= 0;
        label("MOTIF (utilisé par toutes les actions ci-dessous)", x, y0, MineNorthStyle.BLUE);
        bReason = box(x, y0 + 11, w, "Ex : Excès de vitesse en agglomération", kReason, 90);

        label("MONTANT (€)", x, y0 + 36, MineNorthStyle.BLUE);
        bAmount = box(x, y0 + 47, 90, "Ex : 150", kAmount, 9);
        label("POINTS RETIRÉS", x + 100, y0 + 36, officer ? MineNorthStyle.BLUE : MineNorthStyle.MUTED);
        bPoints = box(x + 100, y0 + 47, 80, !driving ? "Pas de permis" : officer ? "0" : "Officier requis", driving ? kPoints : "", 2);
        bPoints.setEditable(officer && driving);
        btn(x + 190, y0 + 46, w - 190, 20, "METTRE L'AMENDE", MineNorthStyle.GREEN,
                () -> send(ModNetwork.A_FINE, d.id(), bReason.getValue(), bAmount.getValue(), number(bPoints.getValue()), 0));

        btn(x, y0 + 76, half, 20, "AJOUTER UNE CONDAMNATION", MineNorthStyle.PINK,
                () -> send(ModNetwork.A_RECORD, d.id(), bReason.getValue(), "", 1, 0)).enabled(officer);
        btn(rx, y0 + 76, half, 20, "AJOUTER UNE NOTE", MineNorthStyle.DARK, () -> send(ModNetwork.A_RECORD, d.id(), bReason.getValue(), "", 3, 0));

        btn(x, y0 + 100, half, 20, !d.online() ? "PERQUISITION : HORS LIGNE" : chief ? "AUTORISER UNE PERQUISITION" : "DEMANDER UNE PERQUISITION",
                MineNorthStyle.CYAN, () -> send(ModNetwork.A_REQUEST, d.id(), bReason.getValue(), "", 0, 0)).enabled(d.online());
        btn(rx, y0 + 100, half, 20, chief ? "ANNULER SON PERMIS" : "DEMANDER L'ANNULATION DU PERMIS", MineNorthStyle.CYAN,
                () -> send(ModNetwork.A_REQUEST, d.id(), bReason.getValue(), "", 1, 0)).enabled(driving);

        btn(x, y0 + 124, half, 20, d.near() ? "SAISIR DES OBJETS" : "SAISIE : CITOYEN TROP LOIN", MineNorthStyle.PINK,
                () -> { keep(); send(ModNetwork.A_INVENTORY, d.id(), "", "", 0, 0); }).enabled(d.near());
        btn(rx, y0 + 124, half, 20, d.wantedReason().isEmpty() ? "LANCER UN AVIS DE RECHERCHE" : "LEVER L'AVIS DE RECHERCHE",
                d.wantedReason().isEmpty() ? MineNorthStyle.PINK : MineNorthStyle.DARK,
                () -> send(ModNetwork.A_WANTED, d.id(), bReason.getValue(), "", 0, 0)).enabled(officer);

        // Garde à vue (tous grades) / prison (Officier+) ; si le citoyen est déjà détenu : temps restant et libération.
        int yj = y0 + 148;
        if (d.jailType() >= 0) {
            label(jailText(d), x, yj + 6, MineNorthStyle.ALERT, w - 110);
            btn(x + w - 100, yj, 100, 20, "LIBÉRER", MineNorthStyle.GREEN, () -> send(ModNetwork.A_RELEASE, d.id(), "", "", 0, 0)).enabled(officer);
        } else {
            int bw = (w - 80) / 2;
            bDuration = box(x, yj + 1, 72, "Durée (min)", kDuration, 4);
            btn(x + 76, yj, bw, 20, d.near() ? "GARDE À VUE" : "GARDE À VUE : TROP LOIN", MineNorthStyle.CYAN,
                    () -> send(ModNetwork.A_JAIL, d.id(), bReason.getValue(), "", 0, number(bDuration.getValue()))).enabled(d.near());
            btn(x + 80 + bw, yj, bw, 20, "INCARCÉRER (PRISON)", MineNorthStyle.PINK,
                    () -> send(ModNetwork.A_JAIL, d.id(), bReason.getValue(), "", 1, number(bDuration.getValue()))).enabled(officer && d.near());
        }
    }

    // ---------- saisie d'objets
    private void buildInventory(Dossier d, int x, int w) {
        List<ModNetwork.Inv> items = v.inventory();
        int y0 = top + 62, pages = localPages(items.size());
        label("Saisie sur " + d.name() + " — chaque objet saisi est noté au casier et arrive dans votre inventaire.", x, top + 48, MineNorthStyle.MUTED, w);
        if (items.isEmpty()) label("Son inventaire est vide.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= items.size()) break;
            ModNetwork.Inv it = items.get(idx);
            int y = y0 + i * 20;
            card(x, y, w, 18, MineNorthStyle.PINK);
            label(it.count() + "x", x + 8, y + 5, MineNorthStyle.MUTED);
            label(it.label(), x + 40, y + 5, MineNorthStyle.WHITE, w - 120);
            btn(x + w - 62, y + 2, 60, 14, "SAISIR", MineNorthStyle.PINK, () -> send(ModNetwork.A_SEIZE, d.id(), "", "", it.slot(), 0));
        }
        localPager(pages);
    }

    // ---------- demandes
    private void buildRequests(int x, int w) {
        List<ModNetwork.ReqView> list = v.requests();
        int y0 = top + 72, pages = localPages(list.size());
        boolean chief = v.grade() == 0;
        if (list.isEmpty()) label("Aucune demande.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            ModNetwork.ReqView r = list.get(idx);
            int y = y0 + i * 20;
            boolean pending = r.status() == 0;
            int color = pending ? MineNorthStyle.WARN : r.status() == 1 ? MineNorthStyle.OK : MineNorthStyle.ALERT;
            card(x, y, w, 18, color);
            label(r.type() == 0 ? "PERQUIS." : "PERMIS", x + 8, y + 5, MineNorthStyle.CYAN);
            label(r.target(), x + 60, y + 5, MineNorthStyle.WHITE, 78);
            label("par " + r.by(), x + 142, y + 5, MineNorthStyle.MUTED, 76);
            if (pending && chief) {
                label(r.reason(), x + 222, y + 5, MineNorthStyle.TEXT, w - 222 - 100);
                btn(x + w - 96, y + 2, 46, 14, "OUI", MineNorthStyle.GREEN, () -> send(ModNetwork.A_DECIDE, null, "", "", r.id(), 1));
                btn(x + w - 48, y + 2, 46, 14, "NON", MineNorthStyle.PINK, () -> send(ModNetwork.A_DECIDE, null, "", "", r.id(), 0));
            } else {
                String status = pending ? "EN ATTENTE" : r.status() == 1 ? "ACCEPTÉE" : "REFUSÉE";
                label(r.reason(), x + 222, y + 5, MineNorthStyle.TEXT, w - 222 - 12 - font.width(status));
                label(status, x + w - 6 - font.width(status), y + 5, color);
            }
        }
        localPager(pages);
    }

    // ---------- radars fixes : flashs (plaque, modèle, vitesse, lieu)
    private void buildRadars(int x, int w) {
        int y0 = top + 72, rows = 4;
        bPlate = box(x, y0, 150, "Filtrer : plaque ou modèle", kPlate, 24);
        btn(x + 156, y0 - 1, 80, 20, "FILTRER", MineNorthStyle.CYAN, () -> go(() -> page = 0));
        String q = kPlate.trim().toLowerCase(java.util.Locale.ROOT);
        List<ModNetwork.FlashView> list = new ArrayList<>();
        for (ModNetwork.FlashView f : v.flashes()) {
            if (q.isEmpty() || (f.plate() + " " + f.model()).toLowerCase(java.util.Locale.ROOT).contains(q)) list.add(f);
        }
        String count = list.size() + " flash(s)";
        label(count, x + w - font.width(count), y0 + 5, MineNorthStyle.MUTED);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        if (list.isEmpty()) label(q.isEmpty() ? "Aucun flash enregistré par les radars fixes." : "Aucun flash pour ce filtre.", x, y0 + 30, MineNorthStyle.MUTED);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy HH:mm");
        boolean chief = v.grade() == 0;
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            ModNetwork.FlashView f = list.get(idx);
            int y = y0 + 26 + i * 30;
            int excess = f.speed() - f.limit();
            int color = excess >= 30 ? MineNorthStyle.ALERT : MineNorthStyle.WARN;
            card(x, y, w, 28, color);
            label(fmt.format(new Date(f.time())), x + 8, y + 5, MineNorthStyle.MUTED);
            String plate = f.plate().isEmpty() ? "SANS PLAQUE" : f.plate().toUpperCase(java.util.Locale.ROOT);
            label(plate, x + 96, y + 5, MineNorthStyle.WHITE, 90);
            String speed = f.speed() + " km/h (limite " + f.limit() + ")";
            label(speed, x + 190, y + 5, color);
            label(f.where(), x + w - (chief ? 26 : 6) - Math.min(90, font.width(f.where())), y + 5, MineNorthStyle.MUTED, 90);
            int lookup = f.plate().isEmpty() ? 0 : 52;
            label(f.model(), x + 8, y + 16, MineNorthStyle.CYAN, w - 8 - (chief ? 26 : 6) - lookup);
            if (lookup > 0) btn(x + w - (chief ? 26 : 6) - 50, y + 15, 48, 12, "PROPRIO", MineNorthStyle.DARK,
                    () -> { keep(); send(ModNetwork.A_PLATES, null, f.plate(), "", 0, 0); });
            if (chief) btn(x + w - 20, y + 7, 18, 14, "X", MineNorthStyle.PINK, () -> send(ModNetwork.A_FLASH_DELETE, null, "", "", f.id(), 0));
        }
        pager(pages, x + w, top + H - 38, () -> go(() -> page--), () -> go(() -> page++), page);
    }

    // ---------- fichier des immatriculations (achats chez le vendeur de véhicules)
    private void buildPlates(int x, int w) {
        int y0 = top + 72, rows = 4;
        bImmat = box(x, y0, 190, "Plaque, nom, prénom ou modèle", kImmat, 32);
        btn(x + 196, y0 - 1, 96, 20, "RECHERCHER", MineNorthStyle.CYAN, () -> { keep(); send(ModNetwork.A_PLATES, null, kImmat, "", 0, 0); });
        List<ModNetwork.PlateView> list = v.plates();
        String count = list.size() + " véhicule(s)";
        label(count, x + w - font.width(count), y0 + 5, MineNorthStyle.MUTED);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        if (list.isEmpty()) label(kImmat.isBlank() ? "Aucun véhicule immatriculé pour le moment." : "Aucun véhicule pour cette recherche.", x, y0 + 30, MineNorthStyle.MUTED);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy");
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            ModNetwork.PlateView pv = list.get(idx);
            int y = y0 + 26 + i * 30;
            card(x, y, w, 28, MineNorthStyle.CYAN);
            label(pv.plate().toUpperCase(java.util.Locale.ROOT), x + 8, y + 5, MineNorthStyle.WHITE, 80);
            label(pv.model(), x + 92, y + 5, MineNorthStyle.CYAN, w - 92 - 72);
            String date = "acheté le " + fmt.format(new Date(pv.time()));
            label(date, x + w - 6 - font.width(date), y + 5, MineNorthStyle.MUTED);
            if (!pv.owner().equals(ModNetwork.NONE)) {
                pseudos.putIfAbsent(pv.owner(), pv.pseudo());
                face(pv.owner(), pv.pseudo(), x + 8, y + 14, 10);
            }
            String who = pv.ownerName() + (pv.birth().isBlank() ? "" : "  — né(e) le " + pv.birth());
            label(who, x + 22, y + 16, MineNorthStyle.TEXT, w - 22 - 72);
            if (pv.known()) btn(x + w - 66, y + 14, 62, 12, "DOSSIER", MineNorthStyle.DARK,
                    () -> { keep(); send(ModNetwork.A_OPEN, pv.owner(), "", "", 0, 0); });
        }
        pager(pages, x + w, top + H - 38, () -> go(() -> page--), () -> go(() -> page++), page);
    }

    // ---------- dispatch : policiers en service (plus haut gradé en service uniquement)
    private void buildDispatch(int x, int w) {
        int y0 = top + 72, rows = 4;
        List<ModNetwork.DutyView> list = v.duty().duty();
        if (dispatchTarget != null && list.stream().noneMatch(o -> o.id().equals(dispatchTarget))) dispatchTarget = null;
        label(v.duty().onDutyCount() + " policier(s) en service", x, y0 + 5, MineNorthStyle.MUTED);
        btn(x + w - 90, y0 - 1, 90, 20, "ACTUALISER", MineNorthStyle.CYAN, () -> { keep(); send(ModNetwork.A_DISPATCH, null, "", "", 0, 0); });
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        pager(pages, x + w - 96, y0 - 1, () -> go(() -> page--), () -> go(() -> page++), page);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            ModNetwork.DutyView o = list.get(idx);
            int y = y0 + 26 + i * 30;
            boolean target = o.id().equals(dispatchTarget);
            card(x, y, w, 28, target ? MineNorthStyle.CYAN : o.grade() == 0 ? MineNorthStyle.WARN : MineNorthStyle.OK);
            label(o.name(), x + 8, y + 5, MineNorthStyle.WHITE, 170);
            label(GRADES[Math.max(0, Math.min(2, o.grade()))], x + 184, y + 5, MineNorthStyle.TEXT);
            long min = o.since() / 60;
            label("en service depuis " + (min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min"), x + 8, y + 16, MineNorthStyle.MUTED, 150);
            String where = o.x() + " " + o.y() + " " + o.z() + " (" + o.dim() + ")"
                    + (o.self() ? "" : o.distance() >= 0 ? " - " + o.distance() + " blocs" : " - autre dimension");
            label(where, x + 160, y + 16, MineNorthStyle.CYAN, w - 160 - 70);
            if (!o.self()) btn(x + w - 62, y + 7, 58, 14, target ? "CIBLÉ" : "CIBLER", target ? MineNorthStyle.CYAN : MineNorthStyle.DARK,
                    () -> go(() -> dispatchTarget = target ? null : o.id()));
        }
        int yb = top + H - 38;
        bMsg = box(x, yb, 226, "Ordre ou message pour les policiers", kMsg, 96);
        String to = "ENVOYER À TOUS";
        if (dispatchTarget != null) for (ModNetwork.DutyView o : list)
            if (o.id().equals(dispatchTarget)) to = "ENVOYER À " + font.plainSubstrByWidth(o.name().toUpperCase(java.util.Locale.ROOT), 90);
        btn(x + 232, yb - 1, w - 232, 20, to, MineNorthStyle.GREEN, () -> { keep(); send(ModNetwork.A_DISPATCH_MSG, dispatchTarget, bMsg.getValue(), "", 0, 0); });
    }

    // ---------- effectifs (Commissaire)
    private void buildRoster(int x, int w) {
        List<ModNetwork.Officer> list = v.officers();
        int y0 = top + 72;
        // Une ligne de moins que les autres listes : le bas du panneau sert au recrutement.
        int rosterPages = Math.max(1, (list.size() + ROWS - 2) / (ROWS - 1));
        page = Math.max(0, Math.min(rosterPages - 1, page));
        var me = Minecraft.getInstance().player;
        UUID self = me == null ? ModNetwork.NONE : me.getUUID();
        if (list.isEmpty()) label("Aucun policier pour le moment.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < ROWS - 1; i++) {
            int idx = page * (ROWS - 1) + i;
            if (idx >= list.size()) break;
            ModNetwork.Officer o = list.get(idx);
            int y = y0 + i * 20;
            card(x, y, w, 18, o.grade() == 0 ? MineNorthStyle.WARN : o.online() ? MineNorthStyle.OK : MineNorthStyle.DARK);
            label(o.name(), x + 8, y + 5, o.online() ? MineNorthStyle.WHITE : MineNorthStyle.MUTED, 150);
            label(GRADES[Math.max(0, Math.min(2, o.grade()))], x + 170, y + 5, MineNorthStyle.TEXT);
            if (!o.id().equals(self)) {
                // "+" = monter en grade, "-" = descendre, "X" = renvoyer de la police.
                btn(x + w - 62, y + 2, 16, 14, "+", MineNorthStyle.DARK, () -> send(ModNetwork.A_GRADE, o.id(), "", "", o.grade() - 1, 0)).enabled(o.grade() > 0);
                btn(x + w - 44, y + 2, 16, 14, "-", MineNorthStyle.DARK, () -> send(ModNetwork.A_GRADE, o.id(), "", "", o.grade() + 1, 0)).enabled(o.grade() < 2);
                btn(x + w - 22, y + 2, 20, 14, "X", MineNorthStyle.PINK, () -> send(ModNetwork.A_GRADE, o.id(), "", "", -1, 0));
            }
        }
        int yb = top + H - 38;
        bName = box(x, yb, 150, "Prénom Nom du citoyen", kName, 32);
        btn(x + 156, yb - 1, 150, 20, "RECRUTER (SOUS-OFFICIER)", MineNorthStyle.GREEN, () -> send(ModNetwork.A_GRADE, null, bName.getValue(), "", 2, 0));
        pager(rosterPages, x + w, yb, () -> go(() -> page--), () -> go(() -> page++), page);
    }

    // ------------------------------------------------------------------ rendu
    private String heading() {
        boolean inDossier = v.view() == ModNetwork.V_DOSSIER || v.view() == ModNetwork.V_INVENTORY;
        if (inDossier && v.dossier() != null) {
            String n = v.dossier().identity().size() >= 2 ? v.dossier().identity().get(0) + " " + v.dossier().identity().get(1) : v.dossier().name();
            return (n.length() > 20 ? n.substring(0, 19) + "…" : n).toUpperCase(java.util.Locale.ROOT);
        }
        return "POLICE NATIONALE";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, heading(), "MINENORTH RP • FICHIER DES CITOYENS");
        for (Card c : cards) MineNorthStyle.card(g, c.x(), c.y(), c.w(), c.h(), false, c.accent());
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        for (Face f : faces) Faces.draw(g, f.id(), f.pseudo(), f.x(), f.y(), f.size());
        if (message.isEmpty() && v.view() != ModNetwork.V_DISPATCH && v.view() != ModNetwork.V_DOSSIER && v.view() != ModNetwork.V_INVENTORY) {
            String dsp = v.duty().dispatcherName().isEmpty() ? "Dispatch vacant (personne en service)"
                    : "Dispatch : " + v.duty().dispatcherName() + " - " + v.duty().onDutyCount() + " en service";
            g.drawString(font, font.plainSubstrByWidth(dsp, W - 28), left + 14, top + H - 14, MineNorthStyle.MUTED, false);
        }
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 28), left + 14, top + H - 14,
                    messageOk ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(ModNetwork.A_CLOSE, ModNetwork.NONE, "", "", 0, 0));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
