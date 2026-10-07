# Garde à vue et prison — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enfermer un citoyen dans une cellule pour une durée limitée (garde à vue / prison), cellules placées par commande OP.

**Architecture:** Données dans `PoliceData` (cellules, sortie, détenus), logique dans un nouveau `JailService` (incarcération, libération, tick, événements), commandes OP dans `PoliceCommands`, interface dans la tablette existante (onglet ACTIONS + FICHE).

**Tech Stack:** Forge 1.20.1 (47.4.10), Java 17, mappings officiels, SavedData NBT, SimpleChannel.

**Spec:** `docs/superpowers/specs/2026-10-07-prison-garde-a-vue-design.md`

## Global Constraints
- Textes joueurs en français, codes couleur `§` comme le reste du mod.
- Config : `garde_a_vue_max_minutes = 24` (≥1), `prison_max_minutes = 120` (≥1), `prison_rayon_evasion = 8` (2..64), `prison_commandes_bloquees = ["home","spawn","tpa","tpaccept","back","warp","rtp"]`.
- GAV : Sous-officier+ ; Prison : Officier+ ; libération anticipée : Officier+.
- Temps décompté uniquement quand le détenu est connecté.
- `ModNetwork.PROTOCOL` "3" → "4".
- Pas de framework de test : vérification = compilation (`gradlew compileJava -I <init.gradle>`) + checklist en jeu.

## Review Focus
- Détenu déconnecté pendant le décompte : son temps ne bouge pas ; à la reconnexion il est remis en cellule.
- Cellule dans une dimension déchargée/inexistante : ignorée à l'attribution, détenu réaffecté ou libéré sans crash.
- Mort du détenu : réapparition dans la cellule (`PlayerRespawnEvent`), pas au lit/spawn.
- Commande bloquée avec namespace (`/essentials:home`) : la racine après `:` est aussi comparée.
- `/police liberer` d'un joueur non détenu : message d'erreur, pas d'exception.

---

### Task 1: Données et config

**Files:** Modify `data/PoliceData.java`, `config/PoliceConfig.java`

**Produces:**
- `PoliceData.JAIL_GAV = 0, JAIL_PRISON = 1`, `PoliceData.GARDE_A_VUE = 6` (type de casier)
- `PoliceData.Cell { String name, dim; double x, y, z; float yaw, pitch; }`
- `PoliceData.Detainee { int type, secondsLeft; String cell, reason, officer; long since; }`
- champs `List<Cell> cells`, `Cell jailExit` (nullable), `Map<UUID, Detainee> detainees`
- `Cell cell(String name)` (insensible à la casse, null sinon), `UUID occupant(String cellName)`
- Config : les 4 clés des Global Constraints + bornage dans `load()`.

- [ ] Ajouter types, champs, helpers ; sérialiser dans `save`/`load` (listes `cells`, `detainees` avec `id` UUID, compound `jailExit` si non null).
- [ ] Ajouter les clés de config et leur bornage (`prison_commandes_bloquees` null → défaut).
- [ ] Compiler. Attendu : BUILD SUCCESSFUL.
- [ ] Commit `feat(prison): données et config des cellules`.

### Task 2: JailService

**Files:** Create `JailService.java`

**Consumes:** Task 1. `PoliceService.rank(ServerPlayer)`, `PoliceService.display(server, uuid)`, `PoliceService.addEntry(...)`, `PoliceService.alertPolice(server, text)`.

**Produces:**
- `public record Result(String msg, boolean ok)`
- `static Result jail(ServerPlayer officer, UUID target, int type, int minutes, String reason)`
- `static Result release(MinecraftServer s, UUID id, String by)`
- `static void toCell(ServerPlayer p)` / `static boolean teleport(ServerPlayer p, PoliceData.Cell c)`
- `static PoliceData.Cell freeCell(MinecraftServer s)` (première cellule à dimension chargée sans occupant)
- `static String addCell(ServerPlayer at, String name)`, `static String removeCell(MinecraftServer s, String name)`, `static List<String> listCells(MinecraftServer s)`, `static String setExit(ServerPlayer at)`

Règles de `jail` (ordre des vérifications) : motif ≥ 3 car. ; type valide ; grade (GAV ≤ SOUS_OFFICIER, prison ≤ OFFICIER) ; 1 ≤ minutes ≤ max du type ; cible ≠ officier ; cible connectée et à ≤ `distance_saisie` ; pas déjà détenue ; cellule libre. Puis : enregistre `Detainee`, casier (GAV → `GARDE_A_VUE` « Garde à vue X min : motif » ; prison → `CONDAMNATION` « Prison X min : motif »), téléporte, prévient détenu + police.

Événements (`@Mod.EventBusSubscriber`) :
- `ServerTickEvent` END, tous les 20 ticks : détenus connectés → `secondsLeft--`, barre d'action « §cGarde à vue/Prison : mm:ss », hors dimension ou distance > rayon → `toCell` ; `secondsLeft <= 0` → `release(s, id, "")`. Cellule absente → réaffectation `freeCell` sinon `release`.
- `PlayerLoggedInEvent`, `PlayerRespawnEvent` → `toCell` si détenu.
- `CommandEvent` : joueur détenu, racine (`parseResults.getContext().getNodes()` premier nœud, ou premier mot de la commande, après `:`) dans la liste → `setCanceled(true)` + message.

`release` : retire de `detainees`, téléporte à `jailExit` (sinon spawn partagé de l'overworld), message au citoyen et police.

- [ ] Implémenter le fichier.
- [ ] Compiler. Attendu : BUILD SUCCESSFUL.
- [ ] Commit `feat(prison): service de détention`.

### Task 3: Commandes OP

**Files:** Modify `command/PoliceCommands.java`

**Consumes:** Task 2 (`addCell`, `removeCell`, `listCells`, `setExit`, `release`).

- [ ] Ajouter `/police cellule ajouter <nom>` (StringArgumentType.word), `supprimer <nom>` (suggestions = noms), `liste`, `sortie` (source doit être un joueur), `/police liberer <joueur>` (EntityArgument.player… ou GameProfileArgument pour hors ligne : utiliser `GameProfileArgument.gameProfile()`).
- [ ] Mettre à jour la javadoc de la classe.
- [ ] Compiler, commit `feat(prison): commandes des cellules`.

### Task 4: Réseau + tablette

**Files:** Modify `network/ModNetwork.java`, `PoliceService.java` (dossier + handle), `client/PoliceScreen.java`

**Produces:** `A_JAIL = 19` (n = type, m = minutes, a = motif), `A_RELEASE = 20` ; `Dossier` + `int jailType` (-1 libre), `int jailMinutes`, `String jailCell` en fin de record.

- [ ] `ModNetwork` : constantes, champs du Dossier (encode/decode), `PROTOCOL = "4"`.
- [ ] `PoliceService.dossier` : remplir les 3 champs depuis `detainees` (minutes arrondies au supérieur). `handle` : `A_JAIL` → `JailService.jail` ; `A_RELEASE` → Officier+ sinon erreur, puis `JailService.release(s, t, display(officer))` ; réponse `sendDossier`.
- [ ] `PoliceScreen` : `TYPES` + "GARDE À VUE" ; couleur type 6 = WARN ; FICHE : ligne détention ; ACTIONS ligne `y0 + 148` : si libre, label « DURÉE (min) », box 50 px (`bDuration`, conservée par `keep()`), boutons GARDE À VUE (tous) et INCARCÉRER (Officier+) ; si détenu, texte « En garde à vue/prison : X min (cellule Y) » + LIBÉRER (Officier+).
- [ ] Compiler, commit `feat(prison): garde à vue et prison dans la tablette`.

### Task 5: README

**Files:** Modify `README.md`

- [ ] Ajouter les commandes `/police cellule …` et `/police liberer` dans « Commandes », compléter le tableau des grades (GAV : Sous-officier ; prison + libération : Officier), section « Garde à vue et prison » (mise en place, fonctionnement, clés de config).
- [ ] Commit `docs: README garde à vue et prison`.
