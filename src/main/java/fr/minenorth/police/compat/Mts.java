package fr.minenorth.police.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Lien avec Minecraft Transport Simulator (modid « mts »), par réflexion : rien n'est requis à la compilation.
 * Un véhicule MTS est une entité « mts:builder_existing » qui porte l'objet MTS dans son champ « entity ».
 * Les sièges sont des entités « mts:builder_seat » (le joueur est passager du siège, pas du véhicule).
 * Si une version de MTS change ces noms, on retombe sur la mesure par déplacement (aucun plantage).
 */
public final class Mts {
    private Mts() {}

    private static boolean is(Entity e, String path) {
        if (e == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && "mts".equals(k.getNamespace()) && path.equals(k.getPath());
    }

    /** Entité principale d'un objet MTS (véhicule, pièce posée…). */
    public static boolean isBuilder(Entity e) { return is(e, "builder_existing"); }

    /** Siège MTS. */
    public static boolean isSeat(Entity e) { return is(e, "builder_seat"); }

    public static boolean isMts(Entity e) {
        if (e == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && "mts".equals(k.getNamespace());
    }

    @Nullable
    private static Object field(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static Object inner(Entity e) { return isBuilder(e) ? field(e, "entity") : null; }

    /** Vrai si cette entité MTS est un véhicule (et pas un objet posé). */
    public static boolean isVehicle(Entity e) {
        Object in = inner(e);
        if (in == null) return false;
        for (Class<?> c = in.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getSimpleName().equals("EntityVehicleF_Physics")) return true;
        }
        return false;
    }

    /**
     * Vitesse en km/h lue dans MTS, ou NaN si illisible.
     * compteur = vrai : vitesse du compteur (indicatedSpeed, en m/s) ; faux : déplacement réel (velocity, en blocs/tick).
     */
    public static double speedKmh(Entity e, boolean compteur) {
        Object in = inner(e);
        if (in == null) return Double.NaN;
        try {
            if (compteur) {
                Object v = field(in, "indicatedSpeed");
                if (v instanceof Number n) return Math.abs(n.doubleValue()) * 3.6;
            }
            Object v = field(in, "velocity");
            if (v instanceof Number n) return Math.abs(n.doubleValue()) * 20 * 3.6;
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /** UUID du conducteur (siège « contrôleur ») du véhicule, ou null. */
    @Nullable
    public static UUID controller(Entity e) {
        Object in = inner(e);
        if (in == null) return null;
        try {
            Method m = in.getClass().getMethod("getController");
            Object rider = m.invoke(in);
            if (rider == null) return null;
            Object id = rider.getClass().getMethod("getID").invoke(rider);
            return id instanceof UUID u ? u : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Plaque d'immatriculation : texte MTS dont le nom de champ figure dans {@code champs} (ex. « Code » pour mts:gvp.eu_plate),
     * cherché sur le véhicule puis sur toutes ses pièces ; sinon premier texte d'une pièce dont le nom contient « plate ».
     * Renvoie "" si le véhicule n'a pas de plaque.
     */
    public static String plate(Entity e, java.util.List<String> champs) {
        Object in = inner(e);
        if (in == null) return "";
        try {
            String found = plateIn(in, champs);
            if (!found.isEmpty()) return found;
            Object parts = field(in, "allParts");
            if (parts instanceof Iterable<?> list) {
                for (Object part : list) {
                    found = plateIn(part, champs);
                    if (!found.isEmpty()) return found;
                }
                for (Object part : list) {
                    Object def = field(part, "definition");
                    Object sys = field(def, "systemName");
                    if (sys instanceof String s && s.toLowerCase(java.util.Locale.ROOT).contains("plate")) {
                        Object text = field(part, "text");
                        if (text instanceof java.util.Map<?, ?> m) {
                            for (Object v : m.values()) if (v instanceof String str && !str.isBlank()) return str.trim();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String plateIn(Object entity, java.util.List<String> champs) {
        Object text = field(entity, "text");
        if (!(text instanceof java.util.Map<?, ?> m)) return "";
        for (java.util.Map.Entry<?, ?> en : m.entrySet()) {
            Object name = field(en.getKey(), "fieldName");
            if (name instanceof String n && en.getValue() instanceof String v && !v.isBlank()) {
                for (String c : champs) if (c != null && c.equalsIgnoreCase(n.trim())) return v.trim();
            }
        }
        return "";
    }

    /** Nom du modèle (pack MTS), sinon nom de l'entité. */
    public static String vehicleName(Entity e) {
        Object in = inner(e);
        try {
            Object def = field(in, "definition");
            Object general = field(def, "general");
            Object name = field(general, "name");
            if (name instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}
        return "Véhicule";
    }
}
