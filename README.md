# MineNorth Police

Mod Forge 1.20.1 pour MineNorth RP. Dépend de `minenorth_eurobank`.
Optionnels (détectés automatiquement) : Identité, Permis, Véhicules, Portes, MTS (Minecraft Transport Simulator), Admin.

## Commandes (OP / console)
- `/police grade <joueur> <commissaire|officier|sousofficier|aucun>` : nomme ou retire un policier.
- `/police tablette <joueur>` : donne une tablette de police.
- `/police equipement <joueur>` : donne le kit (radar à main, détecteur, taser, 16 cartouches, 8 tests) — liste dans `kit_equipement`.
- `/police reload` : recharge `config/minenorth_police.json`.
- `/police cellule ajouter <nom>` : place une cellule à votre position (et orientation).
- `/police cellule supprimer <nom>` : supprime une cellule (refusé si elle est occupée).
- `/police cellule liste` : cellules, position, occupant, et point de sortie.
- `/police cellule sortie` : place le point de libération à votre position.
- `/police liberer <joueur>` : libère immédiatement un détenu (même hors ligne).

## Tablette (`minenorthpolice:tablette_police`)
Clic droit, réservée aux policiers enregistrés. Un OP doit d'abord se donner un grade avec `/police grade`.

| Grade | Droits |
|---|---|
| Sous-officier | dossiers, amende simple, note, saisie d'objets, demandes de perquisition / d'annulation de permis, garde à vue |
| Officier | + amende avec retrait de points, condamnation, avis de recherche, prison, libération anticipée |
| Commissaire | + accepter / refuser les demandes, gérer les effectifs, supprimer une entrée de casier |

## Configuration
`duree_perquisition_minutes`, `amende_max_euros`, `points_max_par_amende`, `distance_saisie`,
`tag_police` (tag donné aux policiers, utilisé par le mod Véhicules pour la fourrière),
`garde_a_vue_max_minutes`, `prison_max_minutes`, `prison_rayon_evasion`, `prison_commandes_bloquees` (voir ci-dessous).

## Garde à vue et prison
**Mise en place (OP)** : construisez les cellules, placez-vous dans chacune et tapez `/police cellule ajouter <nom>`
(ex. `gav1`, `prison1`), puis `/police cellule sortie` devant le commissariat. Sans point de sortie, les détenus libérés vont au spawn du monde.

**En jeu** : tablette → dossier du citoyen → onglet **ACTIONS**. Remplir le **motif** et la **durée (min)**, puis :
- **GARDE À VUE** (Sous-officier et plus) : au plus `garde_a_vue_max_minutes` (24 par défaut), inscrite au casier (type GARDE À VUE).
- **INCARCÉRER (PRISON)** (Officier et plus) : au plus `prison_max_minutes` (120 par défaut), inscrite au casier comme **condamnation**.

Le citoyen doit être connecté et à moins de `distance_saisie` blocs. Il est téléporté dans la première cellule libre (une cellule = un détenu).

| Règle | Fonctionnement |
|---|---|
| Durée | Ne s'écoule que lorsque le détenu est **connecté** ; temps restant affiché dans sa barre d'action et sur sa fiche. |
| Évasion | S'il s'éloigne de plus de `prison_rayon_evasion` blocs (8) ou change de dimension, il est ramené en cellule. Idem à la reconnexion et après une mort. |
| Commandes | Les commandes de `prison_commandes_bloquees` (`home`, `spawn`, `tpa`, `tpaccept`, `back`, `warp`, `rtp`) sont refusées, y compris `mod:commande`. |
| Libération | En fin de peine, ou avant via **LIBÉRER** (Officier et plus) / `/police liberer`. Téléporté au point de sortie, **inventaire conservé** (utilisez la saisie de la tablette si besoin). |
| Cellule inutilisable | Si la cellule d'un détenu n'existe plus (dimension supprimée…), il est déplacé dans une autre cellule libre, sinon libéré. |

## Équipement (réservé aux policiers si `objets_reserves_police` = true)
| Objet | Utilisation |
|---|---|
| Radar à main `radar_main` | Clic droit en visant un véhicule : vitesse en km/h, modèle et **plaque** (jamais le conducteur). Accroupi + clic droit : changer la limite. Véhicules MTS : vitesse du compteur (`radar_mts_vitesse_compteur`), sinon mesurée sur 0,5 s. Rien n'est enregistré. |
| Radar fixe `radar_fixe` | Bloc à poser. Flashe tout véhicule MTS qui dépasse la limite (+ tolérance) dans `radar_fixe_rayon` et relève **uniquement la plaque** : pas de conducteur, pas d'amende automatique. Chaque flash (date, plaque, modèle, vitesse, limite, lieu) va dans la tablette, onglet **RADARS** (filtre par plaque ; le Commissaire peut supprimer). Alerte aux policiers (`radar_fixe_alerte_police`). Clic droit policier : limite suivante ; accroupi : infos. Seule la police (ou un créatif) peut le casser. |
| Test salivaire `test_salivaire` | Clic droit sur un citoyen proche : résultat après `test_duree_secondes`. Positif si effet de `drogue_effets`, tag de `drogue_tags`, ou objet de `drogue_objets` consommé depuis moins de `drogue_detection_minutes`. Usage unique. Positif = note au casier (`test_positif_au_casier`). |
| Détecteur de métaux `detecteur_metaux` | Clic droit sur un citoyen proche : bip si son inventaire contient un objet de `detecteur_objets` (id, joker `mod:*_sword`, `mod:*` ou tag `#mod:tag`). |
| Taser `taser` + `cartouche_taser` | Clic droit maintenu : viser (précision `taser_precision_visee`, sinon `taser_precision_hanche`). Clic gauche : tirer (1 cartouche), portée `taser_portee`. La cible reçoit `taser_effets` + `taser_degats`. Recharge `taser_recharge_ticks`. |

Pour déclarer les drogues d'un autre mod : `"drogue_objets": ["monmod:joint", "monmod:*_pills"]`.
Les policiers peuvent aussi être nommés depuis le panneau admin (`/mnadmin` → fiche joueur → onglet Police).

Plaques : lues dans les textes MTS dont le nom de champ est dans `plaque_champs` (« Code » = plaque `mts:gvp.eu_plate` posée par le vendeur de véhicules).
Tous les objets sont dans l'onglet créatif « Police MineNorth ».

## Bureau du commissariat
L'onglet **BUREAU** de la tablette ouvre le bureau du mod `minenorthaccueil` (plaintes, historique, rendez-vous, objets trouvés, fourrière).
Sans ce mod, l'onglet affiche simplement un message.

## Menottes, fouille, escorte et sac de jute

Aucune commande : tout se fait au clic droit avec l'objet en main (onglet créatif commun « MineNorth »). Tout est décidé et revérifié côté serveur.

| Objet | Rôle |
|---|---|
| **Menottes de police** (`menottes_police`) | Acier chromé. Réservées aux policiers enregistrés (`menottes_reservees_police`). Un OP n'est **pas** exempté. Dans le kit `/police equipement`. |
| **Clé de menottes** (`cle_menottes`) | Clic droit sur un menotté : libère (policier, ou ravisseur). |
| **Menottes artisanales** (`menottes_artisanales`) | Illégales : colliers serflex et chaîne rouillée, fabriquées par les mafieux (recette : 1 lingot de fer + 3 pépites + 2 ficelles). Utilisables par tous. Le ravisseur les retire en cliquant sur son captif avec les menottes en main. |
| **Sac de jute** (`sac_de_jute`) | Recette : 8 ficelles. Sur un joueur menotté, à genoux ou inconscient. |

**Menotter** : clic droit avec les menottes sur un joueur à moins de `menottes_portee` blocs. Immédiat si la cible est inconsciente (mod Secours), a les mains en l'air / est à genoux / assise (emote « Se rendre », « Prier », « S'asseoir par terre » du mod Emotes, liste `menottes_emotes_reddition`). Sinon : pose de `menottes_pose_secondes` s (barre dans la barre d'action) ; la cible échappe si elle s'éloigne de `menottes_pose_deplacement_max` blocs, et si elle tient une arme (liste du détecteur de métaux) sans être ralentie (taser), la pose est refusée.
Un menotté ne peut plus attaquer, utiliser d'objet, casser/poser, ouvrir de coffre, jeter d'objet, courir ni sauter ; il est ralenti (`menottes_ralentissement_pourcent`). Les menottes sont visibles sur lui, bras joints dans le dos (la pose passe par le crochet `EmoteModel.POST` du mod Emotes : sans le mod Emotes, pas de pose mais menottes visibles ; le modèle du joueur n'est jamais remplacé). Libération automatique après `menottes_liberation_minutes` (10 par défaut), à la mort (`menottes_liberer_mort`), à la déconnexion si `menottes_liberer_deconnexion`, et à la mise en garde à vue / prison.

**Fouille** : clic droit **accroupi** sur un menotté. Réservée aux policiers (et au ravisseur d'un captif aux menottes artisanales si `fouille_ravisseurs`). Écran de fouille : inventaire complet (armure et main gauche compris), objets illégaux (`fouille_objets_illegaux` : drogues, argent sale, faux plaques, outils de crochetage, armes TACZ...) entourés de rouge. Un clic sur un objet lance son retrait (`fouille_delai_secondes` s par objet, second clic = annule) : l'objet passe dans l'inventaire du fouilleur. La victime voit « Vous êtes fouillé ». Un policier voit ses saisies inscrites au casier (SAISIE, `fouille_inscrire_casier`). Journal serveur : `config/minenorth_police_fouilles.log`.

**Escorte** : clic droit à main vide sur un menotté = il vous suit à `escorte_distance` blocs (traction bornée à `escorte_vitesse_max`, relâché au-delà de `escorte_distance_max` ou s'il est bloqué). Nouveau clic = relâche. Clic droit sur un véhicule MTS en escortant (ou touche **U**, réglable : « Menotté : monter / faire descendre ») = installe le captif sur une place passager libre ; il ne peut pas en descendre (il est remis à sa place) ; la même touche (ou un policier) le fait descendre.

**Sac de jute** : sur la tête, remplace le casque (conservé dans l'état du joueur et restitué). Écran de toile de jute presque opaque et sons étouffés. Un autre joueur le retire par clic droit avec un sac en main sur l'encagoulé. Il se retire seul quand les menottes sautent ou après `sac_duree_secondes` (`sac_sans_menottes_secondes` si pas menotté).

**Tablette** : onglet « Menottes » = menottés connectés (ravisseur, type, temps restant) avec bouton LIBÉRER à distance.

Les objets de la liste sont générés par `tools/gen_menottes.py` (textures PIL, modèles JSON à éléments, aperçu logiciel hors jeu ; rappel : UV toujours dans le repère 0-16). Les réglages sont dans `config/minenorth_police.json` (fichier JSON, noms de champs explicites).

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).

## Dispatch et service
- Bouton **PRENDRE SON POSTE / FIN DE SERVICE** dans l'en-tête de la tablette (tous les policiers).
- Onglet **DISPATCH** : réservé au plus haut gradé en service (à grade égal, le premier arrivé). Liste des policiers en service avec position et distance, message à tous ou à un seul.

## Transport de suspects (véhicules du pack `minenorthpolicecar`)
Onglet **TRANSPORT** du dossier : **EMBARQUER LE SUSPECT** l'installe sur un siège `seat_suspect` du véhicule de police le plus proche (8 blocs, suspect à portée). S'il quitte son siège, il y est remis ; **FAIRE SORTIR** le libère. Une mise en détention lève l'embarquement.

## Mise en fourrière (13/10)
- Objet **Ordre de fourrière** (`ordre_fourriere`, onglet créatif Police) : clic droit sur le véhicule visé (8 blocs) ouvre le menu de mise en fourrière du mod Véhicules
  (propriétaire, plaque, appel du mécano, mise en fourrière forcée).
- Tablette : bouton **FOURRIÈRE** dans la barre du haut : même menu pour le véhicule visé (ou le plus proche à 8 blocs).
- Le menu est celui du mod Véhicules (`Impound.openPoliceMenu`, par réflexion) ; le véhicule doit avoir un propriétaire enregistré.
