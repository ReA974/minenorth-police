package fr.minenorth.police.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liens avec les autres mods MineNorth (Identité, Permis, Véhicules), par réflexion :
 * le mod Police compile et démarre sans eux ; les informations correspondantes sont simplement absentes.
 */
public final class Compat {
    private Compat() {}

    public static boolean hasAccueil() { return ModList.get().isLoaded("minenorthaccueil"); }

    /**
     * Ouvre chez ce policier le bureau du mod Accueil Police (plaintes, historique, rendez-vous, objets trouvés, fourrière).
     * Par réflexion : la tablette fonctionne sans ce mod. Renvoie faux s'il est absent.
     */
    public static boolean openAccueilDesk(net.minecraft.server.level.ServerPlayer p) {
        if (!hasAccueil()) return false;
        try {
            Class.forName("fr.minenorth.accueil.AccueilService").getMethod("openDesk", net.minecraft.server.level.ServerPlayer.class).invoke(null, p);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermis() { return ModList.get().isLoaded("minenorth_permis"); }
    public static boolean hasVehicles() { return ModList.get().isLoaded("minenorth_rp_vehicles"); }
    public static boolean hasIdentity() { return ModList.get().isLoaded("minenorthidentite"); }

    /** {prénom, nom, date de naissance, lieu, nationalité, n° de carte}, ou null si pas d'identité (MineNorth API). */
    public static String[] identity(MinecraftServer s, UUID id) {
        if (s == null || id == null) return null;
        return fr.minenorth.api.MineNorth.identity().get(s, id)
                .map(i -> new String[]{i.firstName(), i.lastName(), i.birthDate(), i.birthPlace(), i.nationality(), i.cardNumber()})
                .orElse(null);
    }

    /** Citoyens connus des autres mods (carte d'identité, permis) : uuid -> pseudo ("" si inconnu). */
    public static Map<UUID, String> knownCitizens(MinecraftServer s) {
        Map<UUID, String> out = new HashMap<>();
        for (UUID u : fr.minenorth.api.MineNorth.identity().known(s)) out.put(u, "");
        if (hasPermis()) {
            try {
                Class<?> pd = Class.forName("com.minenorth_permis.data.PermisData");
                Object data = pd.getMethod("get", MinecraftServer.class).invoke(null, s);
                for (Map.Entry<?, ?> e : ((Map<?, ?>) pd.getMethod("holders").invoke(data)).entrySet()) {
                    if (e.getKey() instanceof UUID u) out.put(u, String.valueOf(e.getValue().getClass().getField("name").get(e.getValue())));
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** Vrai si le citoyen possède au moins une catégorie valide du permis de conduire (voiture, poids lourd, moto). */
    public static boolean hasDrivingLicence(MinecraftServer s, UUID id) {
        if (!hasPermis()) return false;
        try {
            Object root = Class.forName("com.minenorth_permis.PermisConfig").getMethod("get").invoke(null);
            java.lang.reflect.Method valid = Class.forName("com.minenorth_permis.Licences").getMethod("isValid", MinecraftServer.class, UUID.class, String.class);
            for (Object group : (List<?>) root.getClass().getField("cardGroups").get(root)) {
                // Seuls les groupes « à points » correspondent au permis de conduire.
                if (!Boolean.TRUE.equals(group.getClass().getField("points").get(group))) continue;
                Object groupId = group.getClass().getField("id").get(group);
                for (Object licence : (List<?>) root.getClass().getMethod("inGroup", String.class).invoke(root, groupId)) {
                    String licenceId = String.valueOf(licence.getClass().getField("id").get(licence));
                    if (Boolean.TRUE.equals(valid.invoke(null, s, id, licenceId))) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static final int NO_PERMIS_MOD = -1, NO_DRIVING_LICENCE = -2;

    /** Points du permis ; -1 si le mod Permis est absent ; -2 si le citoyen n'a aucun permis de conduire. */
    public static int points(MinecraftServer s, UUID id) {
        if (!hasPermis()) return NO_PERMIS_MOD;
        if (!hasDrivingLicence(s, id)) return NO_DRIVING_LICENCE;
        try {
            return (Integer) Class.forName("com.minenorth_permis.Licences").getMethod("points", MinecraftServer.class, UUID.class).invoke(null, s, id);
        } catch (Throwable t) {
            return NO_PERMIS_MOD;
        }
    }

    /** Retire des points. Renvoie le nouveau total, ou -1 si impossible. À 0 point, le mod Permis annule le permis. */
    public static int removePoints(MinecraftServer s, UUID id, int amount) {
        int now = points(s, id);
        if (now < 0) return -1;
        try {
            return (Integer) Class.forName("com.minenorth_permis.Licences").getMethod("setPoints", MinecraftServer.class, UUID.class, int.class)
                    .invoke(null, s, id, Math.max(0, now - amount));
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Annule toutes les catégories du permis de conduire (voiture, poids lourd, moto). */
    public static boolean revokeDriving(MinecraftServer s, UUID id) {
        if (!hasPermis()) return false;
        try {
            Class.forName("com.minenorth_permis.Licences").getMethod("revokePointLicences", MinecraftServer.class, UUID.class).invoke(null, s, id);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Permis et licences du citoyen : "Nom — jusqu'au 01.02.2027". */
    @SuppressWarnings("unchecked")
    public static List<String> licences(MinecraftServer s, UUID id) {
        List<String> out = new ArrayList<>();
        if (!hasPermis()) return out;
        try {
            Class<?> pd = Class.forName("com.minenorth_permis.data.PermisData");
            Object data = pd.getMethod("get", MinecraftServer.class).invoke(null, s);
            Object holder = pd.getMethod("peek", UUID.class).invoke(data, id);
            if (holder == null) return out;
            Map<String, Long> owned = (Map<String, Long>) holder.getClass().getField("licences").get(holder);
            Map<String, String> names = new HashMap<>();
            try {
                Object root = Class.forName("com.minenorth_permis.PermisConfig").getMethod("get").invoke(null);
                for (Object l : (List<?>) root.getClass().getField("licences").get(root)) {
                    names.put(String.valueOf(l.getClass().getField("id").get(l)), String.valueOf(l.getClass().getField("name").get(l)));
                }
            } catch (Throwable ignored) {}
            long now = System.currentTimeMillis();
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy");
            for (Map.Entry<String, Long> e : owned.entrySet()) {
                long exp = e.getValue();
                String validity = exp == Long.MAX_VALUE ? "permanent" : exp < now ? "expiré" : "jusqu'au " + fmt.format(new Date(exp));
                out.add(names.getOrDefault(e.getKey(), e.getKey()) + " — " + validity);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Modèles des véhicules du citoyen actuellement en fourrière. */
    public static List<String> impound(MinecraftServer s, UUID id) {
        List<String> out = new ArrayList<>();
        if (!hasVehicles()) return out;
        try {
            Class<?> c = Class.forName("com.minenorth.vehicles.fourriere.ImpoundData");
            Object data = c.getMethod("get", MinecraftServer.class).invoke(null, s);
            Object list = ((Map<?, ?>) c.getField("vehicles").get(data)).get(id);
            if (list instanceof List<?> l) {
                for (Object v : l) out.add(String.valueOf(v.getClass().getField("label").get(v)));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // ------------------------------------------------------------------ fichier des immatriculations (mod Véhicules)

    /** Une ligne du fichier des immatriculations, lue dans com.minenorth.vehicles.registry.PlateRegistry. */
    public static final class Plate {
        public String plate = "", model = "", color = "", ownerName = "", firstName = "", lastName = "", birthDate = "", shop = "", method = "";
        public UUID owner;
        public int price;
        public long time;
    }

    private static String str(Object o, String field) {
        try { Object v = o.getClass().getField(field).get(o); return v == null ? "" : String.valueOf(v); } catch (Throwable t) { return ""; }
    }

    /** Tout le fichier, les ventes les plus récentes d'abord. Vide si le mod Véhicules est absent ou trop ancien. */
    public static List<Plate> plates(MinecraftServer s) {
        List<Plate> out = new ArrayList<>();
        if (!hasVehicles()) return out;
        try {
            Class<?> c = Class.forName("com.minenorth.vehicles.registry.PlateRegistry");
            Object data = c.getMethod("get", MinecraftServer.class).invoke(null, s);
            List<?> entries = (List<?>) c.getField("entries").get(data);
            for (int i = entries.size() - 1; i >= 0; i--) {
                Object e = entries.get(i);
                Plate p = new Plate();
                p.plate = str(e, "plate"); p.model = str(e, "model"); p.color = str(e, "color");
                p.ownerName = str(e, "ownerName"); p.firstName = str(e, "firstName"); p.lastName = str(e, "lastName");
                p.birthDate = str(e, "birthDate"); p.shop = str(e, "shopId"); p.method = str(e, "method");
                Object o = e.getClass().getField("owner").get(e);
                p.owner = o instanceof UUID u ? u : null;
                p.price = e.getClass().getField("price").getInt(e);
                p.time = e.getClass().getField("time").getLong(e);
                out.add(p);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Véhicules immatriculés au nom du citoyen : "AB-123-CD — Modèle (couleur)". */
    public static List<String> registered(MinecraftServer s, UUID id) {
        List<String> out = new ArrayList<>();
        for (Plate p : plates(s)) {
            if (id.equals(p.owner)) out.add(p.plate + " — " + p.model + (p.color.isEmpty() ? "" : " (" + p.color + ")"));
        }
        return out;
    }
}
