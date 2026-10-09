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

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).

## Dispatch et service
- Bouton **PRENDRE SON POSTE / FIN DE SERVICE** dans l'en-tête de la tablette (tous les policiers).
- Onglet **DISPATCH** : réservé au plus haut gradé en service (à grade égal, le premier arrivé). Liste des policiers en service avec position et distance, message à tous ou à un seul.

## Transport de suspects (véhicules du pack `minenorthpolicecar`)
Onglet **TRANSPORT** du dossier : **EMBARQUER LE SUSPECT** l'installe sur un siège `seat_suspect` du véhicule de police le plus proche (8 blocs, suspect à portée). S'il quitte son siège, il y est remis ; **FAIRE SORTIR** le libère. Une mise en détention lève l'embarquement.
