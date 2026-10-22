package fr.minenorth.police.compat;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Mod « Casier de service » (facultatif, lu par réflexion) : tant que le policier a encore des affaires étrangères sur lui,
 * il ne peut pas quitter son service par la tablette ou l'accueil, il doit passer par son casier.
 */
public final class CasierHook {
    private CasierHook() {}

    private static Method allow;
    private static boolean looked;

    /** Vrai si le joueur peut quitter son service maintenant (toujours vrai sans le mod Casier). */
    public static boolean allowLeave(ServerPlayer p) {
        if (!looked) {
            looked = true;
            try {
                allow = Class.forName("fr.minenorth.casier.ServiceManager").getMethod("allowLeave", ServerPlayer.class);
            } catch (Throwable t) {
                allow = null;
            }
        }
        if (allow == null) return true;
        try {
            return (Boolean) allow.invoke(null, p);
        } catch (Throwable t) {
            return true;
        }
    }
}
