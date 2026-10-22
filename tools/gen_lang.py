"""Ecrit les fichiers de langue (fr_fr ET en_us identiques : tout le serveur est en francais)."""
import json
import os

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "minenorth_casier", "lang")

LANG = {
    "itemGroup.minenorth_casier": "Casiers de service",
    "block.minenorth_casier.casier_service": "Casier de service",
    "item.minenorth_casier.sac_saisie": "Sac de saisie",
    "item.minenorth_casier.sac_saisie.desc": "Reçoit les preuves et objets ramassés en service.",
    "screen.minenorth_casier.titre": "Casier de service",
    "casier.etat.pret": "Prêt : prends ton équipement de service.",
    "casier.etat.equipe": "En service : ton inventaire civil est en sécurité.",
    "casier.etat.reprendre": "Équipement perdu : reprends-le gratuitement.",
    "casier.etat.perdu": "Équipement détruit : termine ton service pour le reprendre.",
    "casier.etat.autre": "Tu es en service pour un autre service.",
    "casier.etat.refuse": "Accès réservé aux membres en service.",
    "casier.rien": "Tu n'as aucune affaire étrangère sur toi.",
    "casier.adeposer": "À déposer avant de partir (saisies, objets civils reçus) :",
    "casier.tablette": "Tu peux aussi terminer ton service sur la tablette.",
    "casier.btn.prendre": "Prendre mon équipement",
    "casier.btn.reprendre": "Reprendre mon équipement",
    "casier.btn.deposer": "Déposer mes affaires",
    "casier.btn.terminer": "Terminer mon service",
    "casier.btn.perso": "Mon rangement (déposer ce que je veux)",
    "menu.minenorth_casier.perso": "Mon rangement",
    "casier.btn.attente": "Récupérer mes affaires en attente (%s)",
    "casier.btn.service": "Service : %s",
    "casier.btn.stuff": "Modifier le stuff",
    "casier.btn.consigne": "Consigne (%s)",
    "casier.btn.fichier": "Revenir au stuff du fichier",
    "casier.tooltip.service": "Équipement de service : à rendre au casier en fin de service",
    "menu.minenorth_casier.stuff": "Stuff : %s",
    "menu.minenorth_casier.consigne": "Consigne : %s",
    "menu.minenorth_casier.astuce": "Clic + objet = copie",
}

os.makedirs(ASSETS, exist_ok=True)
for code in ("fr_fr", "en_us"):
    with open(os.path.join(ASSETS, code + ".json"), "w", encoding="utf-8") as f:
        json.dump(LANG, f, ensure_ascii=False, indent=2)
print("langues ecrites")
