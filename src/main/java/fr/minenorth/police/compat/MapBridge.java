package fr.minenorth.police.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Pont (par réflexion) vers l'API serveur du mod carte minenorth_map. Sans la carte, tous les appels sont sans effet. */
public final class MapBridge {
    private static boolean tried;
    private static Method set;

    private MapBridge() {}

    private static boolean init() {
        if (!tried) {
            tried = true;
            try {
                if (ModList.get().isLoaded("minenorth_map")) {
                    Class<?> api = Class.forName("fr.minenorth.map.api.MapApi");
                    set = api.getMethod("setMarker", ServerPlayer.class, String.class, String.class, int.class, int.class, int.class, int.class, boolean.class);
                }
            } catch (Throwable t) {
                set = null;
            }
        }
        return set != null;
    }

    public static boolean available() { return init(); }

    /** Pose / déplace un repère sur la carte du joueur ; guide = lance le guidage dessus. */
    public static void set(ServerPlayer p, String name, String dim, int x, int y, int z, int color, boolean guide) {
        if (!init()) return;
        try { set.invoke(null, p, name, dim, x, y, z, color, guide); } catch (Throwable ignored) {}
    }
}
