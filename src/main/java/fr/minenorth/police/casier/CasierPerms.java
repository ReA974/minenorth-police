package fr.minenorth.police.casier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Qui peut poser, retirer et administrer les casiers (jamais un droit de jeu : un OP reste soumis aux règles du service). */
public final class CasierPerms {
    private CasierPerms() {}

    public static boolean isOp(Player p) { return p.hasPermissions(2); }

    /** OP, ou chef du service (si la config l'autorise). */
    public static boolean isAdmin(Player p, String service) {
        if (isOp(p)) return true;
        if (!CasierConfig.CHEF_ADMIN.get() || !(p instanceof ServerPlayer sp)) return false;
        ServiceDef sv = Services.get(service);
        return sv != null && sv.chief(sp);
    }

    public static boolean canPlaceOrBreak(Player p, String service) {
        if (isOp(p)) return true;
        if (!CasierConfig.CHEF_PEUT_POSER.get() || !(p instanceof ServerPlayer sp)) return false;
        ServiceDef sv = Services.get(service);
        return sv != null && sv.chief(sp);
    }
}
