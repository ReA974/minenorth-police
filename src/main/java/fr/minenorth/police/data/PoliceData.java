package fr.minenorth.police.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Effectifs, casiers judiciaires, avis de recherche et demandes. Sauvegardé avec le monde. */
public class PoliceData extends SavedData {
    private static final String NAME = "minenorth_police";

    public static final int COMMISSAIRE = 0, OFFICIER = 1, SOUS_OFFICIER = 2;
    public static final String[] GRADES = {"Commissaire", "Officier", "Sous-officier"};

    /** Types d'entrée du casier. */
    public static final int AMENDE = 0, CONDAMNATION = 1, SAISIE = 2, NOTE = 3, PERMIS = 4, PERQUISITION = 5;
    /** Types de demande. */
    public static final int REQ_PERQUISITION = 0, REQ_PERMIS = 1;
    public static final int PENDING = 0, ACCEPTED = 1, REFUSED = 2;

    public static final class Rec {
        public int id, type, points; public long time, amount; public String officer = "", text = ""; public boolean paid;
    }
    public static final class Req {
        public int id, type, status; public UUID target, by; public String targetName = "", byName = "", reason = "", decidedBy = "";
        public long time, expires;
    }

    /** Flash d'un radar fixe (driver / driverName / fine : champs d'anciennes versions, plus utilisés). */
    public static final class Flash {
        public int id, speed, limit; public long time; public UUID driver;
        public String plate = "", model = "", where = "", driverName = "", fine = "";
    }

    /** Flashs des radars fixes, du plus ancien au plus récent. */
    public final List<Flash> flashes = new ArrayList<>();

    /** Tous les joueurs déjà vus sur le serveur (uuid -> pseudo). */
    public final Map<UUID, String> names = new LinkedHashMap<>();
    /** Policiers (uuid -> grade). */
    public final Map<UUID, Integer> officers = new LinkedHashMap<>();
    public final Map<UUID, List<Rec>> records = new LinkedHashMap<>();
    /** Avis de recherche (uuid -> motif). */
    public final Map<UUID, String> wanted = new LinkedHashMap<>();
    public final List<Req> requests = new ArrayList<>();
    private int nextId = 1;

    public static PoliceData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PoliceData::load, PoliceData::new, NAME);
    }

    public int nextId() { setDirty(); return nextId++; }
    public List<Rec> recordsOf(UUID id) { return records.computeIfAbsent(id, k -> new ArrayList<>()); }
    public String name(UUID id) { String n = names.get(id); return n != null ? n : id.toString().substring(0, 8); }

    public UUID byName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        return null;
    }

    /** Perquisition acceptée et encore valable sur ce citoyen : minutes restantes, 0 sinon. */
    public int searchMinutesLeft(UUID target) {
        long now = System.currentTimeMillis(), best = 0;
        for (Req r : requests) {
            if (r.type == REQ_PERQUISITION && r.status == ACCEPTED && target.equals(r.target) && r.expires > now) best = Math.max(best, r.expires - now);
        }
        return (int) ((best + 59_999) / 60_000);
    }

    public static PoliceData load(CompoundTag tag) {
        PoliceData d = new PoliceData();
        d.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag nl = tag.getList("names", Tag.TAG_COMPOUND);
        for (int i = 0; i < nl.size(); i++) { CompoundTag t = nl.getCompound(i); d.names.put(t.getUUID("id"), t.getString("name")); }
        ListTag ol = tag.getList("officers", Tag.TAG_COMPOUND);
        for (int i = 0; i < ol.size(); i++) { CompoundTag t = ol.getCompound(i); d.officers.put(t.getUUID("id"), t.getInt("grade")); }
        ListTag wl = tag.getList("wanted", Tag.TAG_COMPOUND);
        for (int i = 0; i < wl.size(); i++) { CompoundTag t = wl.getCompound(i); d.wanted.put(t.getUUID("id"), t.getString("reason")); }
        ListTag rl = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag t = rl.getCompound(i);
            Rec r = new Rec();
            r.id = t.getInt("id"); r.type = t.getInt("type"); r.points = t.getInt("points"); r.time = t.getLong("time");
            r.amount = t.getLong("amount"); r.officer = t.getString("officer"); r.text = t.getString("text"); r.paid = t.getBoolean("paid");
            d.recordsOf(t.getUUID("citizen")).add(r);
        }
        ListTag ql = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < ql.size(); i++) {
            CompoundTag t = ql.getCompound(i);
            Req r = new Req();
            r.id = t.getInt("id"); r.type = t.getInt("type"); r.status = t.getInt("status");
            r.target = t.getUUID("target"); r.by = t.getUUID("by");
            r.targetName = t.getString("targetName"); r.byName = t.getString("byName"); r.reason = t.getString("reason");
            r.decidedBy = t.getString("decidedBy"); r.time = t.getLong("time"); r.expires = t.getLong("expires");
            d.requests.add(r);
        }
        ListTag fl = tag.getList("flashes", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag t = fl.getCompound(i);
            Flash f = new Flash();
            f.id = t.getInt("id"); f.speed = t.getInt("speed"); f.limit = t.getInt("limit"); f.time = t.getLong("time");
            if (t.hasUUID("driver")) f.driver = t.getUUID("driver");
            f.plate = t.getString("plate"); f.model = t.getString("model"); f.where = t.getString("where");
            f.driverName = t.getString("driverName"); f.fine = t.getString("fine");
            d.flashes.add(f);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", nextId);
        ListTag nl = new ListTag();
        names.forEach((id, n) -> { CompoundTag t = new CompoundTag(); t.putUUID("id", id); t.putString("name", n); nl.add(t); });
        tag.put("names", nl);
        ListTag ol = new ListTag();
        officers.forEach((id, g) -> { CompoundTag t = new CompoundTag(); t.putUUID("id", id); t.putInt("grade", g); ol.add(t); });
        tag.put("officers", ol);
        ListTag wl = new ListTag();
        wanted.forEach((id, r) -> { CompoundTag t = new CompoundTag(); t.putUUID("id", id); t.putString("reason", r); wl.add(t); });
        tag.put("wanted", wl);
        ListTag rl = new ListTag();
        records.forEach((id, list) -> {
            for (Rec r : list) {
                CompoundTag t = new CompoundTag();
                t.putUUID("citizen", id); t.putInt("id", r.id); t.putInt("type", r.type); t.putInt("points", r.points); t.putLong("time", r.time);
                t.putLong("amount", r.amount); t.putString("officer", r.officer); t.putString("text", r.text); t.putBoolean("paid", r.paid);
                rl.add(t);
            }
        });
        tag.put("records", rl);
        ListTag ql = new ListTag();
        for (Req r : requests) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", r.id); t.putInt("type", r.type); t.putInt("status", r.status); t.putUUID("target", r.target); t.putUUID("by", r.by);
            t.putString("targetName", r.targetName); t.putString("byName", r.byName); t.putString("reason", r.reason);
            t.putString("decidedBy", r.decidedBy); t.putLong("time", r.time); t.putLong("expires", r.expires);
            ql.add(t);
        }
        tag.put("requests", ql);
        ListTag fl = new ListTag();
        for (Flash f : flashes) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", f.id); t.putInt("speed", f.speed); t.putInt("limit", f.limit); t.putLong("time", f.time);
            if (f.driver != null) t.putUUID("driver", f.driver);
            t.putString("plate", f.plate); t.putString("model", f.model); t.putString("where", f.where);
            t.putString("driverName", f.driverName); t.putString("fine", f.fine);
            fl.add(t);
        }
        tag.put("flashes", fl);
        return tag;
    }
}
