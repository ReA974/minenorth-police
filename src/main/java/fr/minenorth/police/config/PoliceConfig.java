package fr.minenorth.police.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuration : config/minenorth_police.json — rechargée avec /police reload. */
public final class PoliceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static PoliceConfig current = new PoliceConfig();

    /** Durée pendant laquelle la police ouvre les portes du citoyen après une perquisition acceptée. */
    public int duree_perquisition_minutes = 30;
    public double amende_max_euros = 50000;
    /** Nombre maximum de points retirés par une seule amende. */
    public int points_max_par_amende = 6;
    /** Distance maximum (en blocs) entre le policier et le citoyen pour saisir ses objets. */
    public int distance_saisie = 6;
    /** Tag donné aux policiers (utilisé par le mod Véhicules pour la mise en fourrière). Vide = aucun tag. */
    public String tag_police = "police.check";

    // ------------------------------------------------------------------ garde à vue et prison
    /** Durée maximum d'une garde à vue (minutes de jeu connecté). */
    public int garde_a_vue_max_minutes = 24;
    /** Durée maximum d'une peine de prison (minutes de jeu connecté). */
    public int prison_max_minutes = 120;
    /** Au-delà de cette distance (blocs) de sa cellule, le détenu y est ramené. */
    public int prison_rayon_evasion = 8;
    /** Commandes interdites aux détenus (sans « / »). */
    public List<String> prison_commandes_bloquees = new ArrayList<>(List.of("home", "spawn", "tpa", "tpaccept", "back", "warp", "rtp"));

    // ------------------------------------------------------------------ équipement
    /** Vrai : radar, test salivaire, détecteur et taser ne fonctionnent que pour les policiers enregistrés. */
    public boolean objets_reserves_police = true;
    /** Kit donné par /police equipement et par le panneau admin : "id_objet quantité". */
    public List<String> kit_equipement = new ArrayList<>(List.of(
            "minenorthpolice:radar_main 1", "minenorthpolice:detecteur_metaux 1", "minenorthpolice:taser 1",
            "minenorthpolice:cartouche_taser 16", "minenorthpolice:test_salivaire 8"));

    // ------------------------------------------------------------------ radars
    /** Portée du radar à main (blocs). */
    public int radar_portee = 120;
    /** Marge avant flash / excès (km/h). */
    public int radar_tolerance_kmh = 5;
    /** Limites proposées (clic accroupi sur le radar à main, clic droit d'un policier sur le radar fixe). */
    public List<Integer> radar_limites = new ArrayList<>(List.of(30, 50, 70, 80, 90, 110, 130));
    /** Véhicules MTS : vrai = vitesse affichée au compteur du véhicule ; faux = vitesse mesurée sur le déplacement réel. */
    public boolean radar_mts_vitesse_compteur = true;
    /** Champs de texte MTS qui contiennent la plaque (« Code » = plaque mts:gvp.eu_plate du mod Véhicules). */
    public List<String> plaque_champs = new ArrayList<>(List.of("Code", "License Plate", "Plate", "Plaque", "Immatriculation"));
    /** Nombre de flashs radar gardés dans la tablette (les plus anciens sont effacés). */
    public int radar_historique_max = 300;
    /** Limite d'un radar fixe fraîchement posé. */
    public int radar_fixe_limite_defaut = 50;
    /** Rayon de détection du radar fixe (blocs). */
    public int radar_fixe_rayon = 12;
    /** Un même véhicule ne peut pas être flashé deux fois par le même radar pendant ce délai. */
    public int radar_fixe_delai_secondes = 30;
    /** Prévenir les policiers connectés à chaque flash. */
    public boolean radar_fixe_alerte_police = true;
    // ------------------------------------------------------------------ test salivaire
    /** Objets considérés comme drogues : consommés (mangés, bus, utilisés), ils rendent le test positif. Ex. "monmod:joint". */
    public List<String> drogue_objets = new ArrayList<>();
    /** Effets considérés comme drogues (actifs au moment du test). */
    public List<String> drogue_effets = new ArrayList<>(List.of("minecraft:nausea"));
    /** Tags joueur considérés comme drogues (ajoutés par un script, /tag, etc.). */
    public List<String> drogue_tags = new ArrayList<>(List.of("drogue"));
    /** Durée pendant laquelle une consommation reste détectable (minutes, temps réel). */
    public int drogue_detection_minutes = 30;
    public int test_duree_secondes = 3;
    public int test_portee = 4;
    /** Un test positif est inscrit automatiquement au dossier (note). */
    public boolean test_positif_au_casier = true;

    // ------------------------------------------------------------------ détecteur de métaux
    /** Objets détectés : id exact, joker ("minecraft:*_sword", "tacz:*") ou tag ("#forge:ingots"). */
    public List<String> detecteur_objets = new ArrayList<>(List.of(
            "minecraft:*_sword", "minecraft:*_axe", "minecraft:bow", "minecraft:crossbow", "minecraft:trident",
            "minecraft:shears", "minecraft:flint_and_steel", "minenorthpolice:taser", "tacz:*"));
    public int detecteur_portee = 4;
    /** Vrai : le policier voit le nom des objets détectés. Faux : seulement le nombre. */
    public boolean detecteur_afficher_objets = true;

    // ------------------------------------------------------------------ taser
    public int taser_portee = 10;
    public float taser_degats = 1.0f;
    /** Temps de recharge entre deux tirs (ticks, 20 = 1 s). */
    public int taser_recharge_ticks = 40;
    /** Tolérance de visée (degrés) : en visant (clic droit maintenu) et en tirant « à la hanche ». */
    public double taser_precision_visee = 1.5;
    public double taser_precision_hanche = 5.0;
    /** Vrai : la cible touchée descend de son véhicule / sa monture. */
    public boolean taser_descendre_vehicule = false;
    public List<Effet> taser_effets = new ArrayList<>(List.of(
            new Effet("minecraft:slowness", 6, 4), new Effet("minecraft:weakness", 6, 2),
            new Effet("minecraft:mining_fatigue", 6, 2), new Effet("minecraft:nausea", 8, 0)));

    public static final class Effet {
        public String effet = "";
        public int duree_secondes;
        /** 0 = niveau I. */
        public int niveau;
        public Effet() {}
        public Effet(String effet, int secondes, int niveau) { this.effet = effet; this.duree_secondes = secondes; this.niveau = niveau; }
    }

    public static PoliceConfig get() { return current; }

    public static boolean load() {
        // Config côté serveur uniquement : le client ne crée ni ne lit aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) return true;
        Path f = FMLPaths.CONFIGDIR.get().resolve("minenorth_police.json");
        boolean ok = true;
        try {
            if (Files.exists(f)) {
                PoliceConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PoliceConfig.class);
                if (c != null) current = c;
            }
        } catch (Exception e) {
            ok = false;
        }
        PoliceConfig c = current;
        PoliceConfig def = new PoliceConfig();
        c.duree_perquisition_minutes = Math.max(1, c.duree_perquisition_minutes);
        c.points_max_par_amende = Math.max(0, c.points_max_par_amende);
        c.distance_saisie = Math.max(1, c.distance_saisie);
        if (c.tag_police == null) c.tag_police = "";
        if (c.kit_equipement == null) c.kit_equipement = def.kit_equipement;
        c.garde_a_vue_max_minutes = Math.max(1, c.garde_a_vue_max_minutes);
        c.prison_max_minutes = Math.max(1, c.prison_max_minutes);
        c.prison_rayon_evasion = Math.max(2, Math.min(64, c.prison_rayon_evasion));
        if (c.prison_commandes_bloquees == null) c.prison_commandes_bloquees = def.prison_commandes_bloquees;
        c.radar_portee = Math.max(8, c.radar_portee);
        c.radar_tolerance_kmh = Math.max(0, c.radar_tolerance_kmh);
        if (c.radar_limites == null || c.radar_limites.isEmpty()) c.radar_limites = def.radar_limites;
        c.radar_limites.removeIf(v -> v == null || v <= 0);
        if (c.radar_limites.isEmpty()) c.radar_limites = def.radar_limites;
        if (c.plaque_champs == null || c.plaque_champs.isEmpty()) c.plaque_champs = def.plaque_champs;
        c.radar_historique_max = Math.max(10, Math.min(5000, c.radar_historique_max));
        c.radar_fixe_limite_defaut = Math.max(5, c.radar_fixe_limite_defaut);
        c.radar_fixe_rayon = Math.max(3, Math.min(48, c.radar_fixe_rayon));
        c.radar_fixe_delai_secondes = Math.max(1, c.radar_fixe_delai_secondes);
        if (c.drogue_objets == null) c.drogue_objets = new ArrayList<>();
        if (c.drogue_effets == null) c.drogue_effets = new ArrayList<>();
        if (c.drogue_tags == null) c.drogue_tags = new ArrayList<>();
        c.drogue_detection_minutes = Math.max(1, c.drogue_detection_minutes);
        c.test_duree_secondes = Math.max(0, c.test_duree_secondes);
        c.test_portee = Math.max(2, c.test_portee);
        if (c.detecteur_objets == null) c.detecteur_objets = new ArrayList<>();
        c.detecteur_portee = Math.max(2, c.detecteur_portee);
        c.taser_portee = Math.max(2, Math.min(32, c.taser_portee));
        c.taser_degats = Math.max(0, c.taser_degats);
        c.taser_recharge_ticks = Math.max(0, c.taser_recharge_ticks);
        c.taser_precision_visee = Math.max(0.1, Math.min(30, c.taser_precision_visee));
        c.taser_precision_hanche = Math.max(0.1, Math.min(30, c.taser_precision_hanche));
        if (c.taser_effets == null) c.taser_effets = new ArrayList<>();
        c.taser_effets.removeIf(e -> e == null || e.effet == null || e.effet.isBlank());
        if (ok) {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return ok;
    }

    public long maxFineCents() { return Math.max(0, Math.round(amende_max_euros * 100.0)); }

}
