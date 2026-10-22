package fr.minenorth.police.casier;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Configuration : config/minenorth_casier-common.toml (créé au premier lancement, commentaires en français).
 * Les listes de stuff peuvent aussi être modifiées EN JEU (écran admin du casier) : l'écran l'emporte alors sur le fichier,
 * jusqu'au bouton « Revenir au stuff du fichier ».
 */
public final class CasierConfig {
    private CasierConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue FIN_SANS_CASIER;
    public static final ForgeConfigSpec.BooleanValue PRISE_AU_CASIER;
    public static final ForgeConfigSpec.BooleanValue CHEF_PEUT_POSER;
    public static final ForgeConfigSpec.BooleanValue CHEF_ADMIN;
    public static final ForgeConfigSpec.BooleanValue JET_INTERDIT;
    public static final ForgeConfigSpec.ConfigValue<String> RAMASSAGE;
    public static final ForgeConfigSpec.IntValue SAC_CAPACITE;
    public static final ForgeConfigSpec.ConfigValue<String> ETRANGERS_VERS;
    public static final ForgeConfigSpec.ConfigValue<String> MORT_EQUIPEMENT;
    public static final ForgeConfigSpec.IntValue EXPIRATION_HORS_LIGNE;
    public static final ForgeConfigSpec.IntValue DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONSERVES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STUFF_POLICE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STUFF_POMPIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STUFF_AUTRES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SERVICES_SUPPL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("=== FIN DE SERVICE ===").push("fin_de_service");
        FIN_SANS_CASIER = b.comment(
                "Fin de service sans passer par le casier si le joueur n'a RIEN d'étranger sur lui (que de l'équipement de service).",
                "true  : la tablette / l'accueil peut terminer le service, le serveur reprend le stuff et rend l'inventaire civil tout seul.",
                "false : il faut TOUJOURS passer par un casier pour terminer son service.",
                "Dans tous les cas, s'il reste des affaires étrangères (saisies, preuves, objets civils reçus), le serveur refuse :",
                "« Passe par ton casier : tu as encore des affaires sur toi ».")
                .define("fin_de_service_sans_casier_si_rien_d_etranger_sur_soi", true);
        PRISE_AU_CASIER = b.comment(
                "true  : un membre du service peut prendre son service directement en ouvrant le casier (défaut).",
                "false : il doit d'abord prendre son service (tablette / accueil), puis aller au casier.")
                .define("prise_de_service_au_casier", true);
        EXPIRATION_HORS_LIGNE = b.comment(
                "Minutes hors ligne au bout desquelles un service est considéré comme expiré : à la reconnexion, le serveur reprend le stuff",
                "et rend l'inventaire civil automatiquement. 0 = jamais (le joueur reprend son service tel quel, quel que soit le temps passé).")
                .defineInRange("expiration_hors_ligne_minutes", 30, 0, 100000);
        b.pop();

        b.comment("=== POSE ET UTILISATION DES CASIERS ===").push("casiers");
        CHEF_PEUT_POSER = b.comment("Le chef du service (commissaire, chef des secours…) peut poser et retirer les casiers de son service. Les OP le peuvent toujours.")
                .define("chef_peut_poser", true);
        CHEF_ADMIN = b.comment("Le chef du service peut modifier le stuff, relier le casier et ouvrir la consigne (écran admin du casier). Les OP le peuvent toujours.")
                .define("chef_peut_administrer", true);
        DISTANCE = b.comment("Distance maximum (en blocs) entre le joueur et le casier pour utiliser son écran.")
                .defineInRange("distance_max", 8, 2, 64);
        b.pop();

        b.comment("=== PENDANT LE SERVICE ===").push("pendant_le_service");
        JET_INTERDIT = b.comment("Interdit de jeter l'équipement de service, de le ranger dans un coffre / conteneur, de le poser dans un cadre ou de le donner.",
                "Désactiver n'est pas recommandé : le serveur supprime de toute façon l'équipement de service trouvé chez un autre joueur.")
                .define("equipement_non_jetable", true);
        RAMASSAGE = b.comment("Que fait un joueur en service quand il ramasse un objet qui n'est pas de son métier ?",
                "SAC      : l'objet va dans son sac de saisie (preuves, saisies) ; sans sac sur lui, il ne peut pas ramasser.",
                "INTERDIT : il ne ramasse rien.",
                "LIBRE    : il ramasse normalement (il devra tout déposer au casier à la fin du service).")
                .define("ramassage", "LIBRE", o -> o instanceof String s && List.of("SAC", "INTERDIT", "LIBRE").contains(s));
        SAC_CAPACITE = b.comment("Nombre de piles d'objets que contient un sac de saisie.")
                .defineInRange("sac_de_saisie_capacite", 27, 1, 120);
        ETRANGERS_VERS = b.comment("Où vont les objets étrangers déposés au casier (hors contenu du sac de saisie, qui va toujours à la consigne) ?",
                "CONSIGNE : à la consigne du service (le chef peut les rendre) : pour un serveur où tout objet ramassé en service est une saisie.",
                "CIVIL    : rendus au joueur avec son inventaire civil (défaut : on ne bloque pas l'inventaire du joueur).")
                .define("objets_etrangers_deposes_vers", "CIVIL", o -> o instanceof String s && List.of("CONSIGNE", "CIVIL").contains(s));
        MORT_EQUIPEMENT = b.comment("À la mort en service :",
                "CASIER  : l'équipement de service disparaît du joueur, il peut le reprendre gratuitement au casier (« Reprendre mon équipement »).",
                "DETRUIT : l'équipement est perdu pour cette prise de service (il devra terminer son service puis le reprendre).",
                "Dans les deux cas l'inventaire civil reste sauvegardé et n'est jamais lâché au sol. Le contenu du sac de saisie va à la consigne.")
                .define("mort_equipement", "CASIER", o -> o instanceof String s && List.of("CASIER", "DETRUIT").contains(s));
        CONSERVES = b.comment("Objets (identifiants) que le joueur GARDE sur lui en service au lieu de les ranger au casier, et qui ne comptent pas comme « étrangers ».",
                "Exemple : [\"mrphone:phone\"] pour garder son téléphone. Vide par défaut.")
                .defineListAllowEmpty("objets_conserves", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("=== STUFF COMPLET PAR MÉTIER ===",
                "Une ligne par objet : identifiant|quantité|NBT facultatif (format SNBT).",
                "Exemple : minecraft:leather_chestplate|1|{display:{color:1713022}}",
                "Emplacement (facultatif) : ajoute @emplacement à la quantité. @inventaire = reste dans l'inventaire ; @curios = première case libre du menu G",
                "(Curios : gilet, casque tactique, équipement ZeroContact...) ; @back, @head, @belt... = un type de case précis ; sans rien (auto) : armure si c'est une pièce d'armure,",
                "sinon case du menu G si l'objet y va, sinon inventaire. Exemple : zerocontact:un_gilet|1@curios",
                "Les identifiants inconnus (mod absent) sont ignorés. Chaque objet donné est automatiquement marqué « service ».",
                "Arme TACZ, par exemple : tacz:modern_kinetic_gun|1|{GunId:\"tacz:glock_17\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:17}",
                "Ces listes sont les valeurs par défaut : l'écran admin du casier (clic droit accroupi, main vide) permet de les modifier en jeu.").push("stuff");
        STUFF_POLICE = b.comment("Stuff de la police (tenue bleue, matériel, menottes si le mod Police les fournit).")
                .defineList("police", List.of(
                        "minecraft:leather_helmet|1|{display:{color:1713022},Unbreakable:1b}",
                        "minecraft:leather_chestplate|1|{display:{color:1713022},Unbreakable:1b}",
                        "minecraft:leather_leggings|1|{display:{color:1713022},Unbreakable:1b}",
                        "minecraft:leather_boots|1|{display:{color:1713022},Unbreakable:1b}",
                        "minenorthpolice:tablette_police|1",
                        "minenorthpolice:menottes_police|2",
                        "minenorthpolice:cle_menottes|1",
                        "minenorthpolice:taser|1",
                        "minenorthpolice:cartouche_taser|16",
                        "minenorthpolice:radar_main|1",
                        "minenorthpolice:detecteur_metaux|1",
                        "minenorthpolice:test_salivaire|8",
                        "minenorth_casier:sac_saisie|1"), o -> o instanceof String);
        STUFF_POMPIERS = b.comment("Stuff des pompiers / secours / ambulanciers (tenue rouge, trousse, défibrillateur).")
                .defineList("pompiers", List.of(
                        "minecraft:leather_helmet|1|{display:{color:12000284},Unbreakable:1b}",
                        "minecraft:leather_chestplate|1|{display:{color:12000284},Unbreakable:1b}",
                        "minecraft:leather_leggings|1|{display:{color:12000284},Unbreakable:1b}",
                        "minecraft:leather_boots|1|{display:{color:12000284},Unbreakable:1b}",
                        "minenorthsecours:tablette_secours|1",
                        "minenorthsecours:bandage|16",
                        "minenorthsecours:trousse_soins|4",
                        "minenorthsecours:defibrillateur|1",
                        "minenorth_casier:sac_saisie|1"), o -> o instanceof String);
        STUFF_AUTRES = b.comment("Stuff des autres services (garde, ambulanciers séparés…), une ligne : identifiant_du_service|identifiant_objet|quantité|NBT facultatif.",
                "Exemple : garde|minecraft:iron_sword|1")
                .defineList("autres_services", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("=== AUTRES SERVICES ===").push("services");
        SERVICES_SUPPL = b.comment("Services supplémentaires reliés à une entreprise du mod Entreprises (leurs membres sont les agents du service).",
                "Une ligne : identifiant|Nom affiché|Nom exact de l'entreprise. Exemple : garde|Garde municipale|Sécurité de la ville",
                "Un service de ce type n'a pas de tablette : le joueur prend et quitte son service directement au casier.",
                "Police et Pompiers/Secours existent toujours (ils lisent les mods Police et Secours).")
                .defineList("services_supplementaires", List.of(), o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }
}
