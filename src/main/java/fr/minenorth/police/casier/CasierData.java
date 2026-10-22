package fr.minenorth.police.casier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données persistantes (monde principal) : inventaires civils en sécurité, consigne, stuff modifié en jeu, casiers posés.
 * Écrites sur disque tout de suite à chaque étape critique ({@link #flush}) : résiste à la déconnexion, au crash et à la mort.
 */
public class CasierData extends SavedData {
    public static final String NAME = "minenorth_casier";

    /** Service en cours par joueur : voir {@link Session}. */
    public final Map<UUID, CompoundTag> sessions = new LinkedHashMap<>();
    /** Stuff modifié en jeu, par service (absent = valeurs du fichier de config). */
    public final Map<String, List<ItemStack>> stuff = new HashMap<>();
    /** Consigne (saisies, preuves, objets déposés), par service. L'index de la liste = le numéro de case de l'écran. */
    public final Map<String, List<ItemStack>> consigne = new HashMap<>();
    /** Affaires à rendre à un joueur dès qu'il a de la place (inventaire plein à la restitution). */
    public final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    /** Rangement personnel de chaque joueur (27 cases). */
    public final Map<UUID, List<ItemStack>> lockers = new HashMap<>();
    /** Casiers posés : « dimension;x;y;z » -> service. */
    public final Map<String, String> casiers = new LinkedHashMap<>();
    /** Services d'entreprise : agents en service. */
    public final Set<UUID> customDuty = new HashSet<>();

    public static CasierData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(CasierData::load, CasierData::new, NAME);
    }

    /** Écriture immédiate sur disque. */
    public void flush(MinecraftServer s) {
        setDirty();
        s.overworld().getDataStorage().save();
    }

    public List<ItemStack> consigne(String service) {
        return consigne.computeIfAbsent(service, k -> new ArrayList<>());
    }

    public int consigneCount(String service) {
        int n = 0;
        for (ItemStack s : consigne.getOrDefault(service, List.of())) if (!s.isEmpty()) n++;
        return n;
    }

    public int pendingCount(UUID id) {
        return pending.getOrDefault(id, List.of()).size();
    }

    public int casierCount(String service) {
        int n = 0;
        for (String v : casiers.values()) if (v.equals(service)) n++;
        return n;
    }

    public static String key(String dim, int x, int y, int z) { return dim + ";" + x + ";" + y + ";" + z; }

    // ------------------------------------------------------------------ disque

    public static CasierData load(CompoundTag t) {
        CasierData d = new CasierData();
        CompoundTag ss = t.getCompound("sessions");
        for (String k : ss.getAllKeys()) {
            try { d.sessions.put(UUID.fromString(k), ss.getCompound(k)); } catch (IllegalArgumentException ignored) {}
        }
        readMap(t.getCompound("stuff"), d.stuff);
        readMap(t.getCompound("consigne"), d.consigne);
        CompoundTag pe = t.getCompound("pending");
        for (String k : pe.getAllKeys()) {
            try {
                List<ItemStack> l = new ArrayList<>();
                readList(pe.getList(k, Tag.TAG_COMPOUND), l);
                d.pending.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag lk = t.getCompound("lockers");
        for (String k : lk.getAllKeys()) {
            try {
                List<ItemStack> l = new ArrayList<>();
                readList(lk.getList(k, Tag.TAG_COMPOUND), l);
                d.lockers.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag ca = t.getCompound("casiers");
        for (String k : ca.getAllKeys()) d.casiers.put(k, ca.getString(k));
        ListTag cd = t.getList("customDuty", Tag.TAG_STRING);
        for (int i = 0; i < cd.size(); i++) {
            try { d.customDuty.add(UUID.fromString(cd.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        CompoundTag ss = new CompoundTag();
        sessions.forEach((k, v) -> ss.put(k.toString(), v));
        t.put("sessions", ss);
        t.put("stuff", writeMap(stuff));
        t.put("consigne", writeMap(consigne));
        CompoundTag pe = new CompoundTag();
        pending.forEach((k, v) -> { if (!v.isEmpty()) pe.put(k.toString(), writeList(v)); });
        t.put("pending", pe);
        CompoundTag lk = new CompoundTag();
        lockers.forEach((k, v) -> { if (v.stream().anyMatch(s -> !s.isEmpty())) lk.put(k.toString(), writeList(v)); });
        t.put("lockers", lk);
        CompoundTag ca = new CompoundTag();
        casiers.forEach(ca::putString);
        t.put("casiers", ca);
        ListTag cd = new ListTag();
        for (UUID u : customDuty) cd.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        t.put("customDuty", cd);
        return t;
    }

    private static void readMap(CompoundTag t, Map<String, List<ItemStack>> out) {
        for (String k : t.getAllKeys()) {
            List<ItemStack> l = new ArrayList<>();
            readList(t.getList(k, Tag.TAG_COMPOUND), l);
            out.put(k, l);
        }
    }

    private static CompoundTag writeMap(Map<String, List<ItemStack>> m) {
        CompoundTag t = new CompoundTag();
        m.forEach((k, v) -> t.put(k, writeList(v)));
        return t;
    }

    public static void readList(ListTag in, List<ItemStack> out) {
        for (int i = 0; i < in.size(); i++) out.add(ItemStack.of(in.getCompound(i)));
    }

    public static ListTag writeList(List<ItemStack> l) {
        ListTag out = new ListTag();
        for (ItemStack s : l) out.add(s.save(new CompoundTag()));
        return out;
    }
}
