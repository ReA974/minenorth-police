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

    // ------------------------------------------------------------------ sièges : installer / retirer un joueur (par réflexion)

    /** Pièces « siège » (PartSeat) du véhicule ; name = systemName de la pièce (ex. « seat_suspect »), null = tous les sièges. */
    private static java.util.List<Object> seats(Entity vehicle, @Nullable String name) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        try {
            Object in = inner(vehicle);
            if (!(field(in, "allParts") instanceof Iterable<?> parts)) return out;
            for (Object part : parts) {
                if (!part.getClass().getSimpleName().equals("PartSeat")) continue;
                if (name != null && !(field(field(part, "definition"), "systemName") instanceof String s && s.equals(name))) continue;
                out.add(part);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static boolean occupiedBy(Object seat, UUID id) {
        try {
            Object rider = field(seat, "rider");
            if (rider == null) return false;
            return id == null || id.equals(rider.getClass().getMethod("getID").invoke(rider));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Vrai si ce véhicule MTS a au moins un siège libre de ce type. */
    public static boolean hasFreeSeat(Entity vehicle, @Nullable String name) {
        if (!isBuilder(vehicle)) return false;
        for (Object s : seats(vehicle, name)) if (field(s, "rider") == null) return true;
        return false;
    }

    /** Véhicule MTS le plus proche ayant un siège libre de ce type, ou null. */
    @Nullable
    public static Entity nearestWithSeat(net.minecraft.server.level.ServerPlayer p, String name, double radius) {
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), x -> isBuilder(x) && hasFreeSeat(x, name))) {
            double d = e.distanceToSqr(p);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /** Installe le joueur dans le premier siège libre de ce type (name null = n'importe quel siège). */
    public static boolean seatPlayer(net.minecraft.server.level.ServerPlayer p, Entity vehicle, @Nullable String name) {
        if (!isBuilder(vehicle)) return false;
        try {
            Object wrapper = Class.forName("mcinterface1201.WrapperPlayer")
                    .getMethod("getWrapperFor", net.minecraft.world.entity.player.Player.class).invoke(null, p);
            for (Object seat : seats(vehicle, name)) {
                if (field(seat, "rider") != null) continue;
                for (Method m : seat.getClass().getMethods()) {
                    if (m.getName().equals("setRider") && m.getParameterCount() == 2) {
                        if (Boolean.TRUE.equals(m.invoke(seat, wrapper, Boolean.TRUE))) return true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Retire le joueur de son siège MTS (sinon simple descente vanilla). */
    public static void unseat(net.minecraft.server.level.ServerPlayer p) {
        Entity ride = p.getVehicle();
        if (ride == null) return;
        try {
            if (isSeat(ride)) {
                for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24), Mts::isBuilder)) {
                    for (Object seat : seats(e, null)) {
                        if (occupiedBy(seat, p.getUUID())) { seat.getClass().getMethod("removeRider").invoke(seat); return; }
                    }
                }
            }
        } catch (Throwable ignored) {}
        p.stopRiding();
    }

    /** Vrai si le joueur est assis dans un siège MTS. */
    public static boolean isSeated(net.minecraft.server.level.ServerPlayer p) { return isSeat(p.getVehicle()); }

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
