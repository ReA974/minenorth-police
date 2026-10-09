package fr.minenorth.police.api;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.data.PoliceData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API pour les autres mods MineNorth. Thread serveur uniquement.
 * Les mods Portes et Admin appellent ces méthodes par réflexion : ne pas changer leurs noms ni leurs paramètres.
 */
public final class PoliceApi {
    private PoliceApi() {}

    /** Vrai si le joueur fait partie des effectifs de la police. */
    public static boolean isPolice(ServerPlayer p) {
        return PoliceData.get(p.server).officers.containsKey(p.getUUID());
    }

    /** Vrai si ce policier a pris son service (utilisé par le mod Véhicules : concessionnaires et garages de police). */
    public static boolean onDuty(UUID id) { return id != null && PoliceService.onDuty(id); }

    /** Vrai si ce policier peut ouvrir les portes de ce citoyen (perquisition acceptée et encore valable). */
    public static boolean canSearch(ServerPlayer officer, UUID owner) {
        // Uniquement tant que le citoyen est connecté : pas de perquisition chez un joueur absent.
        return owner != null && PoliceService.rank(officer) >= 0 && officer.server.getPlayerList().getPlayer(owner) != null
                && PoliceData.get(officer.server).searchMinutesLeft(owner) > 0;
    }

    // ------------------------------------------------------------------ utilisé par le panneau admin

    /** Noms des grades, du plus haut (index 0) au plus bas. */
    public static String[] grades() { return PoliceData.GRADES.clone(); }

    /** Grade du joueur (0 = Commissaire … 2 = Sous-officier), -1 s'il n'est pas policier. Fonctionne hors ligne. */
    public static int grade(MinecraftServer s, UUID id) {
        Integer g = PoliceData.get(s).officers.get(id);
        return g == null ? -1 : Math.max(0, Math.min(2, g));
    }

    /** Effectifs : uuid -> grade (copie). */
    public static Map<UUID, Integer> officers(MinecraftServer s) {
        return new LinkedHashMap<>(PoliceData.get(s).officers);
    }

    /** Nom affiché (carte d'identité, sinon pseudo). */
    public static String name(MinecraftServer s, UUID id) { return PoliceService.display(s, id); }

    /** Nomme (grade 0..2) ou retire (-1) un policier, connecté ou non. Renvoie le message de résultat. */
    public static String setGrade(MinecraftServer s, UUID id, String name, int grade) {
        return PoliceService.setGrade(s, id, name, grade);
    }

    /** Donne une tablette (joueur connecté). Faux s'il est hors ligne. */
    public static boolean giveTablet(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p == null) return false;
        PoliceService.giveTablet(p);
        return true;
    }

    /** Donne le kit d'équipement (joueur connecté). Renvoie le nombre d'objets, -1 s'il est hors ligne. */
    public static int giveEquipment(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        return p == null ? -1 : PoliceService.giveKit(p);
    }
}
