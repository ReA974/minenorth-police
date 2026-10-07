# Garde à vue et prison — design

Date : 2026-10-07 • Branche : `feat/prison`

## Objectif
Permettre à la police d'enfermer un citoyen dans une cellule pour une durée limitée (garde à vue ou prison),
avec des cellules placées par les admins, une libération automatique et des protections contre l'évasion.

## Décisions validées
- Cellules placées **par commande à la position de l'OP** (pas de bloc).
- **Deux types** : garde à vue (courte, Sous-officier+) et prison (longue, Officier+, condamnation au casier).
- Le temps ne s'écoule **que lorsque le détenu est connecté**.
- Anti-évasion : **rappel en cellule** (distance / dimension / reconnexion / mort) + **commandes bloquées**.
- Libération : téléportation au **point de sortie**, **inventaire conservé** (la saisie existe déjà via la tablette).
- Le README est mis à jour.

## Hypothèses
- Incarcération depuis la tablette, onglet ACTIONS du dossier. Le citoyen doit être connecté et à moins de `distance_saisie` blocs.
- Une cellule = un détenu. Attribution automatique de la première cellule libre ; refus si aucune.
- Un Officier+ peut libérer avant la fin ; un OP peut libérer via `/police liberer <joueur>`.

## Architecture
### `JailService` (nouveau, `fr.minenorth.police.JailService`)
`@Mod.EventBusSubscriber`. Responsabilités :
- `jail(officer, target, type, minutes, reason)` → résultat (message, ok) ; vérifie grade, durée, proximité, cellule libre, déjà détenu.
- `release(server, id, reason)` → téléporte au point de sortie (ou spawn du monde), retire de `detainees`, prévient le citoyen et la police.
- Tick serveur toutes les 20 ticks : pour chaque détenu connecté, `-1 s` ; message en barre d'action ; si distance > `prison_rayon_evasion` ou autre dimension → retour en cellule ; à 0 → `release`.
- Événements : `PlayerLoggedInEvent` et `PlayerRespawnEvent` → retour en cellule ; `CommandEvent` → annule les commandes de `prison_commandes_bloquees` pour un détenu (racine de la commande, sans `/`, insensible à la casse).
- Cellule supprimée pendant une détention → réaffectation à une cellule libre, sinon libération.

### Données (`PoliceData`)
- `Cell { String name; String dim; double x, y, z; float yaw, pitch; }` — `List<Cell> cells`.
- `Cell jailExit` (nullable, même structure, nom vide).
- `Detainee { int type; String cell; int secondsLeft; String reason, officer; long since; }` — `Map<UUID, Detainee> detainees`.
- Types : `JAIL_GAV = 0`, `JAIL_PRISON = 1`. Nouveau type de casier `GARDE_A_VUE = 6`.
- Sérialisés dans `save`/`load` (listes `cells`, `detainees`, compound `jailExit`).

### Config (`PoliceConfig`)
- `garde_a_vue_max_minutes = 24` (≥1), `prison_max_minutes = 120` (≥1)
- `prison_rayon_evasion = 8` (2..64)
- `prison_commandes_bloquees = ["home","spawn","tpa","tpaccept","back","warp","rtp"]`

### Commandes (`PoliceCommands`, OP niveau 2)
- `/police cellule ajouter <nom>` (position + orientation du joueur ; nom unique)
- `/police cellule supprimer <nom>` (refus si occupée)
- `/police cellule liste`
- `/police cellule sortie`
- `/police liberer <joueur>`

### Réseau (`ModNetwork`)
- Actions : `A_JAIL = 19` (n = type, m = minutes, a = motif), `A_RELEASE = 20`.
- `Dossier` : + `int jailType` (-1 = libre), `int jailMinutes`, `String jailCell`.
- `PROTOCOL` "3" → "4".

### Tablette (`PoliceScreen`)
- ACTIONS : nouvelle ligne à `y0 + 148` : champ **DURÉE (min)**, boutons **GARDE À VUE** (tous grades) et **INCARCÉRER** (Officier+, désactivé sinon). Si détenu : texte du temps restant + **LIBÉRER** (Officier+).
- FICHE : ligne « EN GARDE À VUE / EN PRISON : X min restantes (cellule Y) ».
- CASIER : libellé `GARDE À VUE` pour le type 6.

## Règles
| | Garde à vue | Prison |
|---|---|---|
| Grade minimum | Sous-officier | Officier |
| Durée max | `garde_a_vue_max_minutes` | `prison_max_minutes` |
| Casier | entrée GARDE À VUE | entrée CONDAMNATION « Prison X min : motif » |

Messages : le détenu est prévenu (type, durée, motif), la police connectée aussi à l'incarcération et à la libération.

## Erreurs gérées
Pas de cellule définie / toutes occupées ; citoyen hors ligne ou trop loin ; déjà détenu ; durée ≤ 0 ou > max ;
motif < 3 caractères ; grade insuffisant ; dimension de cellule introuvable (cellule ignorée) ; policier qui s'incarcère lui-même.

## README
Nouvelle section « Garde à vue et prison » : commandes `/police cellule …` et `/police liberer`, droits, fonctionnement
(temps connecté, rappel, commandes bloquées, sortie), clés de config. Tableau des grades mis à jour.

## Vérification
Pas de tests automatisés dans le projet : `gradlew build` doit passer, puis checklist en jeu
(placer cellules + sortie, GAV, prison, évasion à pied, autre dimension, déconnexion/reconnexion, mort, commande bloquée,
libération anticipée, fin de peine, suppression de cellule occupée).
