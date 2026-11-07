package fr.minenorth.police.client.phone;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import fr.minenorth.police.client.Faces;
import fr.minenorth.police.client.MineNorthStyle;
import fr.minenorth.police.client.PhoneAppClient;
import fr.minenorth.police.network.ModNetwork;
import fr.minenorth.police.network.ModNetwork.Dossier;
import fr.minenorth.police.network.ModNetwork.RecView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Application « Police » dessinée dans l'écran du téléphone. Mêmes fonctions que la tablette de police ({@link fr.minenorth.police.client.PoliceScreen}) :
 * citoyens et dossiers, demandes, alertes, radars, plaques, effectifs, dispatch, et un onglet Service (prise de service, téléphone 17,
 * bureau, brigade canine). Ce qui se fait directement sur une personne ou un véhicule (menottés, fouille, fourrière du véhicule visé)
 * reste dans le monde (shift + clic droit, outils) : pas dans le téléphone. Le bureau et le K9 s'ouvrent dans le téléphone (mods Accueil, Animaux). Les onglets demandent la vue au serveur, qui revérifie chaque droit. Mise en page portrait.
 */
public class PhonePoliceScreen extends PhoneAppBase {
    private static final String[] GRADES = {"Commissaire", "Officier", "Sous-officier"};
    private static final String[] TYPES = {"AMENDE", "CONDAMNATION", "SAISIE", "NOTE", "PERMIS", "PERQUISITION", "GARDE À VUE"};
    private static final String[] JAIL = {"EN GARDE À VUE", "EN PRISON"};
    /** Vue du serveur de chaque onglet ; -1 = onglet Service (local). */
    private static final int SERVICE = -1;

    private ModNetwork.ViewPacket v;
    private String message = "";
    private boolean messageOk = true;
    private long receivedAt = System.currentTimeMillis();
    /** Onglet Service affiché (il n'a pas de vue côté serveur). */
    private boolean service;
    /** Onglet du dossier : 0 fiche, 1 casier, 2 permis/véhicules, 3 actions. */
    private int dtab;
    private UUID dispatchTarget;
    private final Map<UUID, String> pseudos = new HashMap<>();
    private final Field search = new Field("Nom ou prénom", "", 32), reason = new Field("Motif (ex : excès de vitesse)", "", 90),
            amount = new Field("Montant €", "", 9), points = new Field("Points", "", 2), name = new Field("Prénom Nom du citoyen", "", 32),
            plate = new Field("Filtrer : plaque ou modèle", "", 24), immat = new Field("Plaque, nom ou modèle", "", 32),
            msg = new Field("Ordre ou message pour les policiers", "", 96);

    public PhonePoliceScreen(ModNetwork.ViewPacket v) {
        super("Police");
        this.v = v;
        this.search.value = v.query();
        this.message = v.message();
        this.messageOk = v.ok();
    }

    public void update(ModNetwork.ViewPacket n) {
        boolean sameDossier = v.dossier() != null && n.dossier() != null && v.dossier().id().equals(n.dossier().id());
        if (n.view() != v.view()) { service = false; resetScroll(); }
        if (n.view() == ModNetwork.V_DOSSIER && !sameDossier) { dtab = 0; reason.value = amount.value = points.value = ""; resetScroll(); }
        this.v = n;
        this.receivedAt = System.currentTimeMillis();
        this.message = n.message();
        this.messageOk = n.ok();
        if (n.ok() && !n.message().isEmpty()) {
            amount.value = points.value = name.value = msg.value = "";
            if (n.view() != ModNetwork.V_INVENTORY) reason.value = "";
        }
        if (n.view() == ModNetwork.V_LIST) search.value = n.query();
        if (n.view() == ModNetwork.V_PLATES) immat.value = n.query();
    }

    private void send(int action, UUID target, String a, String b, int n, int m) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target, cut(a, 96), cut(b, 32), n, m));
    }

    private static String cut(String s, int max) { return s == null ? "" : s.length() > max ? s.substring(0, max) : s; }

    private static int number(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return 0; } }

    private boolean inDossier() { return v.view() == ModNetwork.V_DOSSIER || v.view() == ModNetwork.V_INVENTORY; }

    // ------------------------------------------------------------------ onglets

    private record Tab(String label, int view) {}

    private List<Tab> tabList() {
        List<Tab> t = new ArrayList<>();
        t.add(new Tab("Citoyens", ModNetwork.V_LIST));
        t.add(new Tab(v.pendingRequests() > 0 ? "Demandes (" + v.pendingRequests() + ")" : "Demandes", ModNetwork.V_REQUESTS));
        t.add(new Tab(v.view() == ModNetwork.V_ALERTS && !v.calls().isEmpty() ? "Alertes (" + v.calls().size() + ")" : "Alertes", ModNetwork.V_ALERTS));
        t.add(new Tab("Menottés", ModNetwork.V_CUFFS));
        t.add(new Tab("Radars", ModNetwork.V_RADARS));
        t.add(new Tab("Plaques", ModNetwork.V_PLATES));
        t.add(new Tab("Service", SERVICE));
        if (v.grade() == 0) t.add(new Tab("Effectifs", ModNetwork.V_ROSTER));
        if (v.duty().dispatcher()) t.add(new Tab("Dispatch", ModNetwork.V_DISPATCH));
        return t;
    }

    @Override protected String title() { return "Police"; }

    @Override
    protected String[] tabs() {
        List<Tab> t = tabList();
        String[] out = new String[t.size()];
        for (int i = 0; i < out.length; i++) out[i] = t.get(i).label();
        return out;
    }

    /** Clic sur un onglet (la base a déjà changé {@code tab}) : Service est local, les autres demandent leur vue au serveur. */
    @Override
    protected void tabChanged() {
        List<Tab> t = tabList();
        if (tab < 0 || tab >= t.size()) return;
        message = "";
        int view = t.get(tab).view();
        service = view == SERVICE;
        switch (view) {
            case ModNetwork.V_LIST -> send(ModNetwork.A_LIST, null, search.value, "", 0, 0);
            case ModNetwork.V_REQUESTS -> send(ModNetwork.A_REQUESTS, null, "", "", 0, 0);
            case ModNetwork.V_ALERTS -> send(ModNetwork.A_ALERTS, null, "", "", 0, 0);
            case ModNetwork.V_RADARS -> send(ModNetwork.A_RADARS, null, "", "", 0, 0);
            case ModNetwork.V_CUFFS -> send(ModNetwork.A_CUFFS, null, "", "", 0, 0);
            case ModNetwork.V_PLATES -> send(ModNetwork.A_PLATES, null, "", "", 0, 0);
            case ModNetwork.V_ROSTER -> send(ModNetwork.A_ROSTER, null, "", "", 0, 0);
            case ModNetwork.V_DISPATCH -> send(ModNetwork.A_DISPATCH, null, "", "", 0, 0);
            default -> {}
        }
    }

    /** L'onglet en surbrillance suit la vue reçue du serveur (un dossier reste sous « Citoyens »). */
    private int currentTab() {
        List<Tab> t = tabList();
        int want = service ? SERVICE : inDossier() ? ModNetwork.V_LIST : v.view();
        for (int i = 0; i < t.size(); i++) if (t.get(i).view() == want) return i;
        return 0;
    }

    @Override protected String badge() { return GRADES[Math.max(0, Math.min(2, v.grade()))] + (v.duty().onDuty() ? " · service" : ""); }

    @Override protected int badgeColor() { return v.duty().onDuty() ? GREEN : DARK; }

    @Override protected String message() { return message; }

    @Override protected boolean messageError() { return !messageOk; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        tab = currentTab();
        super.render(g, mx, my, pt);
    }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        if (service) return service(g, mx, my, y);
        if (v.view() == ModNetwork.V_INVENTORY && v.dossier() != null) return inventory(g, mx, my, y, v.dossier());
        if (v.view() == ModNetwork.V_DOSSIER && v.dossier() != null) return dossier(g, mx, my, y, v.dossier());
        return switch (v.view()) {
            case ModNetwork.V_REQUESTS -> requests(g, mx, my, y);
            case ModNetwork.V_ALERTS -> alerts(g, mx, my, y);
            case ModNetwork.V_RADARS -> radars(g, mx, my, y);
            case ModNetwork.V_CUFFS -> cuffs(g, mx, my, y);
            case ModNetwork.V_PLATES -> plates(g, mx, my, y);
            case ModNetwork.V_ROSTER -> roster(g, mx, my, y);
            case ModNetwork.V_DISPATCH -> dispatch(g, mx, my, y);
            default -> citizens(g, mx, my, y);
        };
    }

    // ------------------------------------------------------------------ outils de dessin

    private float section(GuiGraphics g, String title, float y) {
        text(g, title, L, y, 9, R - L, CYAN);
        return y + 16;
    }

    /** Carte avec un liseré de couleur à gauche. */
    private void card(GuiGraphics g, float t, float b, int accent) {
        box(g, L, t, R, b, 12, CARD);
        box(g, L + 3, t + 6, L + 6, b - 6, 1.5f, accent);
    }

    private int lines(String s, float sz, float w) { return Math.max(1, PhoneModernText.wrap(s, size(sz), size(w)).size()); }

    private void face(GuiGraphics g, UUID id, String pseudo, float l, float t, float s) {
        if (visible(t, t + s)) Faces.draw(g, id, pseudo, x(l), y(t), size(s));
    }

    private static String displayName(String rp, String name) { return rp == null || rp.isBlank() ? name : rp; }

    private float kv(GuiGraphics g, String key, String value, float y, float w, int color) {
        text(g, key, L, y, 8, w, MUTED);
        return wrap(g, value, L, y + 11, 9.5f, w, color) + 4;
    }

    /** Pages d'une liste paginée par le serveur (citoyens). */
    private float serverPager(GuiGraphics g, int mx, int my, float y) {
        if (v.pages() <= 1) return y;
        int page = v.page();
        button(g, mx, my, L, y, L + 60, y + 22, "<", 10, DARK, page > 0, () -> send(ModNetwork.A_LIST, null, search.value, "", page - 1, 0));
        center(g, (page + 1) + " / " + v.pages(), 150, y + 6, 9.5f, 100, TEXT);
        button(g, mx, my, R - 60, y, R, y + 22, ">", 10, DARK, page < v.pages() - 1, () -> send(ModNetwork.A_LIST, null, search.value, "", page + 1, 0));
        return y + 30;
    }

    // ------------------------------------------------------------------ citoyens

    private float citizens(GuiGraphics g, int mx, int my, float y) {
        field(g, mx, my, search, L, y, R - 92);
        button(g, mx, my, R - 86, y, R, y + 22, "Rechercher", 9, CYAN, true, () -> send(ModNetwork.A_LIST, null, search.value, "", 0, 0));
        y += 30;
        if (v.citizens().isEmpty()) return wrap(g, "Aucun citoyen trouvé.", L, y, 9, R - L, MUTED);
        for (ModNetwork.Citizen c : v.citizens()) {
            pseudos.put(c.id(), c.name());
            boolean hov = hover(mx, my, L, y, R, y + 36);
            card(g, y, y + 36, c.wanted() ? ALERT : c.online() ? OK : DARK);
            if (hov) box(g, L + 8, y + 2, R - 2, y + 34, 10, HOVER);
            face(g, c.id(), c.name(), L + 12, y + 7, 22);
            text(g, displayName(c.rpName(), c.name()), L + 42, y + 7, 10.5f, R - L - 60, WHITE);
            text(g, c.wanted() ? "RECHERCHÉ" : c.online() ? "en ligne" : "hors ligne", L + 42, y + 21, 8.5f, 160, c.wanted() ? ALERT : c.online() ? OK : MUTED);
            text(g, "Dossier", R - 46, y + 13, 8.5f, 40, CYAN);
            hit(L, y, R, y + 36, () -> send(ModNetwork.A_OPEN, c.id(), "", "", 0, 0));
            y += 41;
        }
        return serverPager(g, mx, my, y + 2);
    }

    // ------------------------------------------------------------------ dossier

    private float dossier(GuiGraphics g, int mx, int my, float y, Dossier d) {
        button(g, mx, my, L, y, L + 70, y + 22, "Retour", 9, HOVER, true, () -> send(ModNetwork.A_LIST, null, search.value, "", 0, 0));
        String n = d.identity().size() >= 2 ? d.identity().get(0) + " " + d.identity().get(1) : d.name();
        text(g, n, L + 78, y + 6, 11, R - L - 78, WHITE);
        y += 28;
        String[] tabs = {"Fiche", "Casier", "Permis", "Actions"};
        float w = (R - L - 15) / 4f;
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            float l = L + i * (w + 5);
            button(g, mx, my, l, y, l + w, y + 22, tabs[i], 8.5f, dtab == i ? CYAN : DARK, true, () -> { dtab = t; message = ""; resetScroll(); });
        }
        y += 30;
        return switch (dtab) {
            case 1 -> casier(g, mx, my, y, d);
            case 2 -> permis(g, y, d);
            case 3 -> actions(g, mx, my, y, d);
            default -> fiche(g, y, d);
        };
    }

    private float fiche(GuiGraphics g, float y, Dossier d) {
        List<String> id = d.identity();
        int convictions = 0;
        for (RecView r : d.records()) if (r.type() == 1) convictions++;
        face(g, d.id(), pseudos.get(d.id()), R - 52, y, 48);
        float w = R - L - 60;
        if (id.size() >= 6) {
            y = kv(g, "Nom", id.get(1).toUpperCase(Locale.ROOT) + "  " + id.get(0), y, w, TEXT);
            y = kv(g, "Né(e) le", id.get(2) + " à " + id.get(3), y, w, TEXT);
            y = kv(g, "Nationalité", id.get(4), y, w, TEXT);
            y = kv(g, "N° de carte", id.get(5), y, R - L, TEXT);
        } else {
            y = wrap(g, "Aucune carte d'identité enregistrée.", L, y, 9.5f, w, WARN) + 34;
        }
        y = kv(g, "Statut", d.online() ? "en ligne" : "hors ligne (dossier consultable, perquisition et saisie impossibles)", y, R - L, d.online() ? OK : MUTED);
        if (d.points() >= 0) y = kv(g, "Points du permis", String.valueOf(d.points()), y, R - L, d.points() <= 3 ? ALERT : TEXT);
        else y = kv(g, "Permis de conduire", d.permisMod() ? "aucun" : "—", y, R - L, TEXT);
        y = kv(g, "Casier", d.records().size() + " entrée(s), dont " + convictions + " condamnation(s)", y, R - L, TEXT);
        y = kv(g, "Amendes impayées", d.unpaid() > 0 ? MineNorthStyle.euros(d.unpaid()) : "aucune", y, R - L, d.unpaid() > 0 ? ALERT : TEXT) + 4;
        if (!d.wantedReason().isEmpty()) y = wrap(g, "AVIS DE RECHERCHE : " + d.wantedReason(), L, y, 9.5f, R - L, ALERT) + 4;
        if (d.searchMinutes() > 0) y = wrap(g, "PERQUISITION AUTORISÉE : ses portes sont accessibles encore " + d.searchMinutes() + " min.", L, y, 9.5f, R - L, WARN) + 4;
        if (d.jailType() >= 0) y = wrap(g, jailText(d), L, y, 9.5f, R - L, ALERT) + 4;
        return y;
    }

    private static String jailText(Dossier d) {
        return JAIL[Math.max(0, Math.min(1, d.jailType()))] + " : " + d.jailMinutes() + " min restante(s) (cellule « " + d.jailCell() + " »)";
    }

    private static int typeColor(int type) {
        return switch (type) {
            case 0, 6 -> WARN;
            case 1 -> ALERT;
            case 2 -> PINK;
            case 4, 5 -> CYAN;
            default -> MUTED;
        };
    }

    private float casier(GuiGraphics g, int mx, int my, float y, Dossier d) {
        if (d.records().isEmpty()) return wrap(g, "Casier vierge.", L, y, 9.5f, R - L, OK);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy");
        boolean chief = v.grade() == 0;
        for (RecView r : d.records()) {
            int color = typeColor(r.type());
            String info = r.type() == 0
                    ? MineNorthStyle.euros(r.amount()) + (r.points() > 0 ? " -" + r.points() + " pt" : "") + (r.paid() ? " · payée" : " · IMPAYÉE")
                    : r.officer();
            float h = 34 + lines(r.text(), 9, R - L - 24) * 12;
            card(g, y, y + h, color);
            text(g, fmt.format(new Date(r.time())) + "  " + TYPES[Math.max(0, Math.min(TYPES.length - 1, r.type()))], L + 12, y + 7, 8.5f, 150, color);
            if (chief) button(g, mx, my, R - 28, y + 5, R - 6, y + 23, "X", 9, PINK, true, () -> send(ModNetwork.A_DELETE, d.id(), "", "", r.id(), 0));
            text(g, info, L + 12, y + 20, 8.5f, R - L - 50, r.type() == 0 ? (r.paid() ? OK : ALERT) : MUTED);
            wrap(g, r.text(), L + 12, y + 33, 9, R - L - 24, TEXT);
            y += h + 5;
        }
        return y;
    }

    private float permis(GuiGraphics g, float y, Dossier d) {
        y = section(g, "PERMIS ET LICENCES", y);
        if (!d.permisMod()) y = wrap(g, "Mod Permis non installé.", L, y, 9, R - L, MUTED) + 4;
        else {
            if (d.points() < 0) y = wrap(g, "Pas de permis de conduire.", L, y, 9.5f, R - L, WARN) + 2;
            else y = wrap(g, "Points : " + d.points(), L, y, 9.5f, R - L, d.points() <= 3 ? ALERT : d.points() <= 6 ? WARN : OK) + 2;
            if (d.licences().isEmpty()) y = wrap(g, "Aucune licence non plus.", L, y, 9, R - L, MUTED);
            for (String l : d.licences()) y = wrap(g, "• " + l, L, y, 9, R - L, TEXT);
        }
        y = section(g, "VÉHICULES IMMATRICULÉS", y + 8);
        if (!d.vehiclesMod()) return wrap(g, "Mod Véhicules non installé.", L, y, 9, R - L, MUTED);
        if (d.vehicles().isEmpty()) y = wrap(g, "Aucun véhicule à son nom.", L, y, 9, R - L, MUTED);
        for (String veh : d.vehicles()) y = wrap(g, "• " + veh, L, y, 9, R - L, TEXT);
        y = section(g, "EN FOURRIÈRE", y + 8);
        if (d.impound().isEmpty()) y = wrap(g, "Aucun véhicule en fourrière.", L, y, 9, R - L, MUTED);
        for (String veh : d.impound()) y = wrap(g, "• " + veh, L, y, 9, R - L, TEXT);
        return y;
    }

    private float actions(GuiGraphics g, int mx, int my, float y, Dossier d) {
        boolean officer = v.grade() <= 1, chief = v.grade() == 0, driving = d.points() >= 0;
        y = section(g, "MOTIF (pour toutes les actions)", y);
        field(g, mx, my, reason, L, y, R);
        y += 30;
        y = section(g, "AMENDE", y);
        field(g, mx, my, amount, L, y, L + 90);
        if (driving) field(g, mx, my, points, L + 96, y, L + 150);
        else text(g, "Pas de permis", L + 96, y + 7, 8, 56, MUTED);
        button(g, mx, my, L + 156, y, R, y + 22, "Mettre l'amende", 9, GREEN, true,
                () -> send(ModNetwork.A_FINE, d.id(), reason.value, amount.value, driving ? number(points.value) : 0, 0));
        y += 32;
        y = section(g, "CASIER ET DEMANDES", y);
        button(g, mx, my, L, y, R, y + 24, "Ajouter une condamnation", 9.5f, PINK, officer, () -> send(ModNetwork.A_RECORD, d.id(), reason.value, "", 1, 0));
        y += 30;
        button(g, mx, my, L, y, R, y + 24, "Ajouter une note", 9.5f, DARK, true, () -> send(ModNetwork.A_RECORD, d.id(), reason.value, "", 3, 0));
        y += 30;
        button(g, mx, my, L, y, R, y + 24, !d.online() ? "Perquisition : hors ligne" : chief ? "Autoriser une perquisition" : "Demander une perquisition",
                9.5f, CYAN, d.online(), () -> send(ModNetwork.A_REQUEST, d.id(), reason.value, "", 0, 0));
        y += 30;
        button(g, mx, my, L, y, R, y + 24, chief ? "Annuler son permis" : "Demander l'annulation du permis", 9.5f, CYAN, driving,
                () -> send(ModNetwork.A_REQUEST, d.id(), reason.value, "", 1, 0));
        y += 30;
        button(g, mx, my, L, y, R, y + 24, d.wantedReason().isEmpty() ? "Lancer un avis de recherche" : "Lever l'avis de recherche", 9.5f,
                d.wantedReason().isEmpty() ? PINK : DARK, officer, () -> send(ModNetwork.A_WANTED, d.id(), reason.value, "", 0, 0));
        y += 34;
        if (d.jailType() >= 0) {
            y = wrap(g, jailText(d), L, y, 9, R - L, ALERT) + 4;
            button(g, mx, my, L, y, R, y + 24, "Libérer", 9.5f, GREEN, officer, () -> send(ModNetwork.A_RELEASE, d.id(), "", "", 0, 0));
            return y + 32;
        }
        return wrap(g, "Garde à vue, prison, fouille et transport : shift + clic droit sur le menotté.", L, y, 8.5f, R - L, MUTED);
    }

    // ------------------------------------------------------------------ saisie d'objets

    private float inventory(GuiGraphics g, int mx, int my, float y, Dossier d) {
        button(g, mx, my, L, y, L + 70, y + 22, "Retour", 9, HOVER, true, () -> send(ModNetwork.A_OPEN, d.id(), "", "", 0, 0));
        text(g, "Saisie : " + d.name(), L + 78, y + 6, 11, R - L - 78, WHITE);
        y = wrap(g, "Chaque objet saisi est noté au casier et arrive dans votre inventaire.", L, y + 30, 8.5f, R - L, MUTED) + 6;
        if (v.inventory().isEmpty()) return wrap(g, "Son inventaire est vide.", L, y, 9.5f, R - L, MUTED);
        for (ModNetwork.Inv it : v.inventory()) {
            card(g, y, y + 30, PINK);
            text(g, it.count() + "x  " + it.label(), L + 12, y + 10, 9.5f, R - L - 100, WHITE);
            button(g, mx, my, R - 76, y + 5, R - 6, y + 25, "Saisir", 9, PINK, true, () -> send(ModNetwork.A_SEIZE, d.id(), "", "", it.slot(), 0));
            y += 35;
        }
        return y;
    }

    // ------------------------------------------------------------------ demandes

    private float requests(GuiGraphics g, int mx, int my, float y) {
        if (v.requests().isEmpty()) return wrap(g, "Aucune demande.", L, y, 9.5f, R - L, MUTED);
        boolean chief = v.grade() == 0;
        for (ModNetwork.ReqView r : v.requests()) {
            boolean pending = r.status() == 0, decide = pending && chief;
            int color = pending ? WARN : r.status() == 1 ? OK : ALERT;
            float h = 34 + lines(r.reason(), 9, R - L - 24) * 12 + (decide ? 30 : 16);
            card(g, y, y + h, color);
            text(g, (r.type() == 0 ? "PERQUISITION" : r.type() == 2 ? "CONTESTATION" : "PERMIS") + " · " + r.target(), L + 12, y + 7, 9.5f, R - L - 24, WHITE);
            text(g, "par " + r.by(), L + 12, y + 20, 8.5f, R - L - 24, MUTED);
            float yy = wrap(g, r.reason(), L + 12, y + 33, 9, R - L - 24, TEXT);
            if (decide) {
                float mid = (L + R) / 2;
                button(g, mx, my, L + 12, yy + 2, mid - 3, yy + 24, "Accepter", 9, GREEN, true, () -> send(ModNetwork.A_DECIDE, null, "", "", r.id(), 1));
                button(g, mx, my, mid + 3, yy + 2, R - 8, yy + 24, "Refuser", 9, PINK, true, () -> send(ModNetwork.A_DECIDE, null, "", "", r.id(), 0));
            } else {
                text(g, pending ? "EN ATTENTE" : r.status() == 1 ? "ACCEPTÉE" : "REFUSÉE", L + 12, yy + 1, 8.5f, R - L - 24, color);
            }
            y += h + 5;
        }
        return y;
    }

    // ------------------------------------------------------------------ alertes (appels d'urgence 17)

    private float alerts(GuiGraphics g, int mx, int my, float y) {
        button(g, mx, my, L, y, L + 100, y + 22, "Actualiser", 9, DARK, true, () -> send(ModNetwork.A_ALERTS, null, "", "", 0, 0));
        if (!v.duty().onDuty()) wrap(g, "Prenez votre service pour être alerté dans le chat.", L + 108, y + 1, 8, R - L - 108, WARN);
        y += 30;
        if (v.calls().isEmpty()) return wrap(g, "Aucun appel d'urgence en cours.", L, y, 9.5f, R - L, OK);
        for (ModNetwork.CallView c : v.calls()) {
            String desc = c.caller() + (c.description().isBlank() ? "" : " : " + c.description());
            float h = 46 + lines(desc, 9, R - L - 24) * 12 + 28;
            card(g, y, y + h, 0xFF4FC3F7);
            int min = c.age() / 60, sec = c.age() % 60;
            text(g, c.label(), L + 12, y + 7, 10, R - L - 100, WHITE);
            right(g, "il y a " + (min > 0 ? min + " min " : "") + sec + " s", R - 10, y + 8, 8.5f, MUTED);
            text(g, c.x() + " " + c.y() + " " + c.z() + (c.distance() >= 0 ? "  (" + c.distance() + " m)" : "  (autre dimension)"), L + 12, y + 22, 9, R - L - 24, TEXT);
            float yy = wrap(g, desc, L + 12, y + 37, 9, R - L - 24, CYAN);
            button(g, mx, my, L + 12, yy + 4, R - 8, yy + 26, "Guider", 9.5f, GREEN, true, () -> send(ModNetwork.A_CALL_GUIDE, null, String.valueOf(c.id()), "", 0, 0));
            y += h + 5;
        }
        return y;
    }

    // ------------------------------------------------------------------ radars fixes

    /** Menottés connectés (nom, type, ravisseur, temps restant), avec libération à distance : mêmes données que la tablette. */
    private float cuffs(GuiGraphics g, int mx, int my, float y) {
        button(g, mx, my, L, y, L + 100, y + 22, "Actualiser", 9, DARK, true, () -> send(ModNetwork.A_CUFFS, null, "", "", 0, 0));
        y += 30;
        text(g, v.citizens().size() + " menotté(s) connecté(s)", L, y, 8.5f, R - L, MUTED);
        y += 14;
        if (v.citizens().isEmpty()) return wrap(g, "Personne n'est menotté pour le moment.", L, y, 9.5f, R - L, MUTED);
        for (ModNetwork.Citizen c : v.citizens()) {
            float h = 34 + lines(c.rpName(), 8.5f, R - L - 24) * 11 + 28;
            card(g, y, y + h, c.wanted() ? PINK : CYAN);
            face(g, c.id(), c.name(), L + 12, y + 7, 14);
            text(g, c.name() + (c.wanted() ? "  (sac de jute)" : ""), L + 32, y + 9, 10, R - L - 44, WHITE);
            float yy = wrap(g, c.rpName(), L + 12, y + 27, 8.5f, R - L - 24, CYAN);
            button(g, mx, my, L + 12, yy + 3, R - 8, yy + 25, "Libérer", 9, PINK, true, () -> send(ModNetwork.A_UNCUFF, c.id(), "", "", 0, 0));
            y += h + 5;
        }
        return y;
    }

    private float radars(GuiGraphics g, int mx, int my, float y) {
        field(g, mx, my, plate, L, y, R);
        y += 28;
        String q = plate.value.trim().toLowerCase(Locale.ROOT);
        List<ModNetwork.FlashView> list = new ArrayList<>();
        for (ModNetwork.FlashView f : v.flashes()) if (q.isEmpty() || (f.plate() + " " + f.model()).toLowerCase(Locale.ROOT).contains(q)) list.add(f);
        text(g, list.size() + " flash(s)", L, y, 8.5f, R - L, MUTED);
        y += 14;
        if (list.isEmpty()) return wrap(g, q.isEmpty() ? "Aucun flash enregistré par les radars fixes." : "Aucun flash pour ce filtre.", L, y, 9.5f, R - L, MUTED);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy HH:mm");
        boolean chief = v.grade() == 0;
        for (ModNetwork.FlashView f : list) {
            int color = f.speed() - f.limit() >= 30 ? ALERT : WARN;
            card(g, y, y + 62, color);
            String pl = f.plate().isEmpty() ? "SANS PLAQUE" : f.plate().toUpperCase(Locale.ROOT);
            text(g, pl, L + 12, y + 7, 10.5f, 110, WHITE);
            right(g, fmt.format(new Date(f.time())), R - (chief ? 32 : 10), y + 8, 8.5f, MUTED);
            if (chief) button(g, mx, my, R - 28, y + 5, R - 6, y + 23, "X", 9, PINK, true, () -> send(ModNetwork.A_FLASH_DELETE, null, "", "", f.id(), 0));
            text(g, f.speed() + " km/h (limite " + f.limit() + ") · " + f.where(), L + 12, y + 22, 9, R - L - 24, color);
            text(g, f.model(), L + 12, y + 36, 9, R - L - 100, CYAN);
            if (!f.plate().isEmpty()) button(g, mx, my, R - 80, y + 34, R - 8, y + 54, "Propriétaire", 8.5f, DARK, true,
                    () -> send(ModNetwork.A_PLATES, null, f.plate(), "", 0, 0));
            y += 67;
        }
        return y;
    }

    // ------------------------------------------------------------------ fichier des immatriculations

    private float plates(GuiGraphics g, int mx, int my, float y) {
        field(g, mx, my, immat, L, y, R - 92);
        button(g, mx, my, R - 86, y, R, y + 22, "Rechercher", 9, CYAN, true, () -> send(ModNetwork.A_PLATES, null, immat.value, "", 0, 0));
        y += 30;
        text(g, v.plates().size() + " véhicule(s)", L, y, 8.5f, R - L, MUTED);
        y += 14;
        if (v.plates().isEmpty()) return wrap(g, immat.value.isBlank() ? "Aucun véhicule immatriculé pour le moment." : "Aucun véhicule pour cette recherche.", L, y, 9.5f, R - L, MUTED);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy");
        for (ModNetwork.PlateView pv : v.plates()) {
            card(g, y, y + 62, CYAN);
            text(g, pv.plate().toUpperCase(Locale.ROOT), L + 12, y + 7, 10.5f, 120, WHITE);
            right(g, "acheté le " + fmt.format(new Date(pv.time())), R - 10, y + 8, 8.5f, MUTED);
            text(g, pv.model(), L + 12, y + 22, 9, R - L - 24, CYAN);
            float ox = L + 12;
            if (!pv.owner().equals(ModNetwork.NONE)) {
                pseudos.putIfAbsent(pv.owner(), pv.pseudo());
                face(g, pv.owner(), pv.pseudo(), L + 12, y + 37, 14);
                ox += 18;
            }
            text(g, pv.ownerName() + (pv.birth().isBlank() ? "" : " · né(e) le " + pv.birth()), ox, y + 39, 9, R - 86 - ox, TEXT);
            if (pv.known()) button(g, mx, my, R - 74, y + 34, R - 8, y + 54, "Dossier", 8.5f, DARK, true, () -> send(ModNetwork.A_OPEN, pv.owner(), "", "", 0, 0));
            y += 67;
        }
        return y;
    }

    // ------------------------------------------------------------------ dispatch

    private float dispatch(GuiGraphics g, int mx, int my, float y) {
        List<ModNetwork.DutyView> list = v.duty().duty();
        if (dispatchTarget != null && list.stream().noneMatch(o -> o.id().equals(dispatchTarget))) dispatchTarget = null;
        text(g, v.duty().onDutyCount() + " policier(s) en service", L, y + 6, 9, R - L - 100, MUTED);
        button(g, mx, my, R - 90, y, R, y + 22, "Actualiser", 9, CYAN, true, () -> send(ModNetwork.A_DISPATCH, null, "", "", 0, 0));
        y += 30;
        String to = "Envoyer à tous";
        for (ModNetwork.DutyView o : list) {
            boolean target = o.id().equals(dispatchTarget);
            if (target) to = "Envoyer à " + o.name();
            card(g, y, y + 58, target ? CYAN : o.grade() == 0 ? WARN : OK);
            text(g, o.name(), L + 12, y + 7, 10, R - L - 100, WHITE);
            text(g, GRADES[Math.max(0, Math.min(2, o.grade()))], L + 12, y + 21, 8.5f, 120, TEXT);
            long min = o.since() / 60;
            right(g, "depuis " + (min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min"), R - 10, y + 8, 8.5f, MUTED);
            text(g, o.x() + " " + o.y() + " " + o.z() + (o.self() ? "" : o.distance() >= 0 ? " · " + o.distance() + " blocs" : " · autre dimension"),
                    L + 12, y + 37, 8.5f, R - L - 100, CYAN);
            if (!o.self()) button(g, mx, my, R - 74, y + 32, R - 8, y + 52, target ? "Ciblé" : "Cibler", 8.5f, target ? CYAN : DARK, true,
                    () -> dispatchTarget = target ? null : o.id());
            y += 63;
        }
        y = section(g, "ORDRE DU DISPATCH", y + 4);
        field(g, mx, my, msg, L, y, R);
        button(g, mx, my, L, y + 28, R, y + 52, to, 9.5f, GREEN, !msg.value.isBlank(), () -> send(ModNetwork.A_DISPATCH_MSG, dispatchTarget, msg.value, "", 0, 0));
        return y + 60;
    }

    // ------------------------------------------------------------------ effectifs (Commissaire)

    private float roster(GuiGraphics g, int mx, int my, float y) {
        var me = Minecraft.getInstance().player;
        UUID self = me == null ? ModNetwork.NONE : me.getUUID();
        if (v.officers().isEmpty()) y = wrap(g, "Aucun policier pour le moment.", L, y, 9.5f, R - L, MUTED) + 4;
        for (ModNetwork.Officer o : v.officers()) {
            card(g, y, y + 36, o.grade() == 0 ? WARN : o.online() ? OK : DARK);
            text(g, o.name(), L + 12, y + 6, 10, R - L - 110, o.online() ? WHITE : MUTED);
            text(g, GRADES[Math.max(0, Math.min(2, o.grade()))], L + 12, y + 20, 8.5f, 120, TEXT);
            if (!o.id().equals(self)) {
                // « + » = monter en grade, « - » = descendre, « X » = renvoyer de la police.
                button(g, mx, my, R - 86, y + 7, R - 62, y + 29, "+", 10, DARK, o.grade() > 0, () -> send(ModNetwork.A_GRADE, o.id(), "", "", o.grade() - 1, 0));
                button(g, mx, my, R - 58, y + 7, R - 34, y + 29, "-", 10, DARK, o.grade() < 2, () -> send(ModNetwork.A_GRADE, o.id(), "", "", o.grade() + 1, 0));
                button(g, mx, my, R - 30, y + 7, R - 6, y + 29, "X", 9, PINK, true, () -> send(ModNetwork.A_GRADE, o.id(), "", "", -1, 0));
            }
            y += 41;
        }
        y = section(g, "RECRUTER", y + 4);
        field(g, mx, my, name, L, y, R);
        button(g, mx, my, L, y + 28, R, y + 52, "Recruter (sous-officier)", 9.5f, GREEN, !name.value.isBlank(), () -> send(ModNetwork.A_GRADE, null, name.value, "", 2, 0));
        return y + 60;
    }

    // ------------------------------------------------------------------ service et outils

    private float service(GuiGraphics g, int mx, int my, float y) {
        ModNetwork.Duty duty = v.duty();
        y = section(g, "SERVICE", y);
        box(g, L, y, R, y + 50, 14, CARD);
        text(g, "Grade : " + GRADES[Math.max(0, Math.min(2, v.grade()))], L + 12, y + 9, 10, R - L - 24, WHITE);
        text(g, duty.dispatcherName().isEmpty() ? "Dispatch vacant (personne en service)" : "Dispatch : " + duty.dispatcherName() + " · " + duty.onDutyCount() + " en service",
                L + 12, y + 28, 8.5f, R - L - 24, MUTED);
        y += 58;
        boolean on = duty.onDuty();
        button(g, mx, my, L, y, R, y + 26, on ? "Fin de service" : "Prise de service", 10, on ? RED : GREEN, true,
                () -> send(ModNetwork.A_DUTY, null, "", "", v.view(), 0));
        y += 32;
        if (on && duty.phone() > 0) {
            boolean phone = duty.phone() == 2;
            button(g, mx, my, L, y, R, y + 26, phone ? "Téléphone 17 : oui" : "Téléphone 17 : non", 10, phone ? GREEN : DARK, true,
                    () -> send(ModNetwork.A_PHONE, null, "", "", v.view(), 0));
            y += 32;
        }
        y = section(g, "OUTILS", y + 6);
        button(g, mx, my, L, y, R, y + 26, "Bureau du commissariat", 10, DARK, true, () -> send(ModNetwork.A_DESK, null, "", "", 0, 0));
        y += 32;
        if (PhoneAppClient.pets()) {
            button(g, mx, my, L, y, R, y + 26, "Brigade canine (K9)", 10, CYAN, true, () -> send(ModNetwork.A_K9, null, "", "", v.view(), 0));
            y += 32;
        }
        return wrap(g, "Menottés, garde à vue, prison, fouille, transport et fourrière : directement sur la personne ou le véhicule (shift + clic droit, outils).", L, y + 4, 8.5f, R - L, MUTED);
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Les appels changent vite : l'onglet Alertes se recharge tout seul toutes les 5 secondes. */
    @Override
    public void tick() {
        super.tick();
        if (!service && v.view() == ModNetwork.V_ALERTS && System.currentTimeMillis() - receivedAt > 5000) {
            receivedAt = System.currentTimeMillis();
            send(ModNetwork.A_ALERTS, null, "", "", 0, 0);
        }
    }

    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(ModNetwork.A_CLOSE, ModNetwork.NONE, "", "", 0, 0));
        super.removed();
    }
}
