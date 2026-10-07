# MineNorth Police

Mod Forge 1.20.1 pour MineNorth RP. Dépend de `minenorth_eurobank`.
Optionnels (détectés automatiquement) : Identité, Permis, Véhicules, Portes, MTS (Minecraft Transport Simulator), Admin.

## Commandes (OP / console)
- `/police grade <joueur> <commissaire|officier|sousofficier|aucun>` : nomme ou retire un policier.
- `/police tablette <joueur>` : donne une tablette de police.
- `/police equipement <joueur>` : donne le kit (radar à main, détecteur, taser, 16 cartouches, 8 tests) — liste dans `kit_equipement`.
- `/police reload` : recharge `config/minenorth_police.json`.

## Tablette (`minenorthpolice:tablette_police`)
Clic droit, réservée aux policiers enregistrés. Un OP doit d'abord se donner un grade avec `/police grade`.

| Grade | Droits |
|---|---|
| Sous-officier | dossiers, amende simple, note, saisie d'objets, demandes de perquisition / d'annulation de permis |
| Officier | + amende avec retrait de points, condamnation, avis de recherche |
| Commissaire | + accepter / refuser les demandes, gérer les effectifs, supprimer une entrée de casier |

## Configuration
`duree_perquisition_minutes`, `amende_max_euros`, `points_max_par_amende`, `distance_saisie`,
`tag_police` (tag donné aux policiers, utilisé par le mod Véhicules pour la fourrière).

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
