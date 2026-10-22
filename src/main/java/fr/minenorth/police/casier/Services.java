package fr.minenorth.police.casier;

import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Liste des services : Police et Pompiers/Secours (lus par l'API MineNorth) + services d'entreprise de la config. */
public final class Services {
    private Services() {}

    private static final Map<String, ServiceDef> ALL = new LinkedHashMap<>();
    private static MinecraftServer server;

    public static void reload(MinecraftServer s) {
        server = s;
        ALL.clear();
        ALL.put("police", new ServiceDef() {
            public String id() { return "police"; }
            public String name() { return "Police"; }
            public boolean member(ServerPlayer p) { return MineNorth.police().isPolice(p); }
            public boolean onDuty(ServerPlayer p) { return MineNorth.police().onDuty(p.server, p.getUUID()); }
            public boolean setDuty(ServerPlayer p, boolean on) { return MineNorth.police().setDuty(p.server, p.getUUID(), on); }
            public boolean chief(ServerPlayer p) { return MineNorth.police().hasRank(p.server, p.getUUID(), 0); }
        });
        ALL.put("pompiers", new ServiceDef() {
            public String id() { return "pompiers"; }
            public String name() { return "Pompiers / Secours"; }
            public boolean member(ServerPlayer p) { return MineNorth.secours().isSecours(p); }
            public boolean onDuty(ServerPlayer p) { return MineNorth.secours().onDuty(p.server, p.getUUID()); }
            public boolean setDuty(ServerPlayer p, boolean on) { return MineNorth.secours().setDuty(p.server, p.getUUID(), on); }
            public boolean chief(ServerPlayer p) { return secoursRank(p) == 0; }
        });
        for (String line : CasierConfig.SERVICES_SUPPL.get()) {
            String[] f = line.split("\\|");
            if (f.length < 3 || f[0].isBlank() || ALL.containsKey(f[0].trim())) continue;
            ALL.put(f[0].trim(), new CompanyService(f[0].trim(), f[1].trim(), f[2].trim()));
        }
    }

    public static ServiceDef get(String id) { return id == null ? null : ALL.get(id); }

    public static List<ServiceDef> all() { return new ArrayList<>(ALL.values()); }

    /** Rang dans le mod Secours (0 = chef), -1 si absent ; lu par réflexion pour ne rien dupliquer. */
    private static int secoursRank(ServerPlayer p) {
        try {
            Object r = Class.forName("fr.minenorth.secours.SecoursService").getMethod("rank", ServerPlayer.class).invoke(null, p);
            return r instanceof Integer i ? i : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Service relié à une entreprise : membre = patron ou employé ; le « service » est tenu par le casier lui-même. */
    private static final class CompanyService implements ServiceDef {
        private final String id, name, company;
        CompanyService(String id, String name, String company) { this.id = id; this.name = name; this.company = company; }
        public String id() { return id; }
        public String name() { return name; }
        public boolean member(ServerPlayer p) {
            int cid = MineNorth.companies().idOf(p.server, company);
            return cid > 0 && MineNorth.companies().isMember(p, cid, false);
        }
        public boolean onDuty(ServerPlayer p) { return CasierData.get(p.server).customDuty.contains(p.getUUID()); }
        public boolean setDuty(ServerPlayer p, boolean on) {
            CasierData d = CasierData.get(p.server);
            if (on) d.customDuty.add(p.getUUID()); else d.customDuty.remove(p.getUUID());
            d.setDirty();
            return on;
        }
        public boolean chief(ServerPlayer p) {
            int cid = MineNorth.companies().idOf(p.server, company);
            return cid > 0 && MineNorth.companies().isMember(p, cid, true);
        }
    }
}
