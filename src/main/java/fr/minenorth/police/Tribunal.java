package fr.minenorth.police;

import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import fr.minenorth.police.data.PoliceData.Rec;
import fr.minenorth.police.data.PoliceData.Req;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Contestation d'une amende ou d'une condamnation : seul un avocat (membre de l'entreprise « avocat_entreprise » de la config) peut la déposer
 * pour son client, avec la mallette d'avocat. Elle arrive dans l'onglet DEMANDES de la tablette, où le Commissaire l'accepte (l'entrée est annulée,
 * l'amende payée est remboursée) ou la refuse.
 */
public final class Tribunal {
    private Tribunal() {}

    /** Membre (patron ou employé) de l'entreprise d'avocats de la config. */
    public static boolean isLawyer(ServerPlayer p) {
        String name = PoliceConfig.get().avocat_entreprise;
        if (name == null || name.isBlank()) return false;
        var companies = fr.minenorth.api.MineNorth.companies();
        int id = companies.idOf(p.server, name.trim());
        return id > 0 && companies.isMember(p, id, false);
    }

    private static boolean contestable(PoliceData d, UUID client, Rec r) {
        if (r.type != PoliceData.AMENDE && r.type != PoliceData.CONDAMNATION) return false;
        long maxAge = PoliceConfig.get().contestation_jours * 86_400_000L;
        if (maxAge > 0 && System.currentTimeMillis() - r.time > maxAge) return false;
        for (Req q : d.requests) {
            if (q.type == PoliceData.REQ_CONTESTATION && q.status == PoliceData.PENDING && client.equals(q.target) && q.rec == r.id) return false;
        }
        return true;
    }

    /** Ouvre l'écran de la mallette d'avocat sur ce client (message d'erreur à l'avocat sinon). */
    public static void open(ServerPlayer lawyer, ServerPlayer client) {
        if (!isLawyer(lawyer)) {
            lawyer.displayClientMessage(Component.literal(PoliceConfig.get().avocat_entreprise.isBlank()
                    ? "§cAucune entreprise d'avocats n'est configurée sur ce serveur."
                    : "§cRéservé aux avocats (membres de « " + PoliceConfig.get().avocat_entreprise + " »)."), true);
            return;
        }
        PoliceData d = PoliceData.get(lawyer.server);
        List<ModNetwork.LawRec> list = new ArrayList<>();
        List<Rec> recs = d.recordsOf(client.getUUID());
        for (int i = recs.size() - 1; i >= 0 && list.size() < 40; i--) {
            Rec r = recs.get(i);
            if (contestable(d, client.getUUID(), r)) list.add(new ModNetwork.LawRec(r.id, r.type, r.time, r.text, r.amount, r.paid));
        }
        if (list.isEmpty()) {
            lawyer.displayClientMessage(Component.literal("§7Rien à contester pour " + PoliceService.display(lawyer.server, client.getUUID()) + "."), true);
            return;
        }
        client.sendSystemMessage(Component.literal("§e[Justice] " + PoliceService.display(lawyer.server, lawyer.getUUID()) + " consulte votre dossier en tant qu'avocat."));
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> lawyer),
                new ModNetwork.LawyerOpenPacket(client.getUUID(), PoliceService.display(lawyer.server, client.getUUID()), list));
    }

    /** Dépôt de la contestation (serveur : tout est revérifié). Renvoie le message à afficher à l'avocat. */
    public static String submit(ServerPlayer lawyer, UUID clientId, int recId, String text) {
        if (!isLawyer(lawyer)) return "§cRéservé aux avocats.";
        MinecraftServer s = lawyer.server;
        ServerPlayer client = s.getPlayerList().getPlayer(clientId);
        if (client == null || client == lawyer || client.level() != lawyer.level() || client.distanceToSqr(lawyer) > 100) return "§cLe client doit être à moins de 10 blocs.";
        text = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (text.length() < 10) return "§cExpliquez les arguments de la défense (10 caractères au moins).";
        if (text.length() > 120) text = text.substring(0, 120);
        PoliceData d = PoliceData.get(s);
        Rec rec = null;
        for (Rec r : d.recordsOf(clientId)) if (r.id == recId) rec = r;
        if (rec == null || !contestable(d, clientId, rec)) return "§cCette entrée n'est plus contestable.";
        Req q = new Req();
        q.id = d.nextId(); q.type = PoliceData.REQ_CONTESTATION; q.target = clientId; q.targetName = PoliceService.display(s, clientId);
        q.by = lawyer.getUUID(); q.byName = PoliceService.display(s, lawyer.getUUID()); q.rec = recId; q.time = System.currentTimeMillis();
        q.reason = (rec.type == PoliceData.AMENDE ? "Amende" : "Condamnation") + " : " + rec.text + " — Défense : " + text;
        d.requests.add(q);
        PoliceService.trimRequests(d);
        d.setDirty();
        for (ServerPlayer o : s.getPlayerList().getPlayers()) {
            if (PoliceService.rank(o) == PoliceData.COMMISSAIRE) {
                o.sendSystemMessage(Component.literal("§e[Justice] Maître " + q.byName + " conteste une décision visant " + q.targetName + " — à traiter dans l'onglet DEMANDES."));
            }
        }
        client.sendSystemMessage(Component.literal("§e[Justice] Votre avocat a déposé une contestation. Le Commissaire va statuer."));
        return "§aContestation déposée : le Commissaire va statuer.";
    }

    /** Décision du Commissaire. Renvoie le message affiché. */
    public static String decide(MinecraftServer s, PoliceData d, Req q, boolean accept) {
        UUID client = q.target;
        if (!accept) {
            ServerPlayer l = s.getPlayerList().getPlayer(q.by);
            if (l != null) l.sendSystemMessage(Component.literal("§e[Justice] Votre contestation pour " + q.targetName + " a été rejetée."));
            ServerPlayer c = s.getPlayerList().getPlayer(client);
            if (c != null) c.sendSystemMessage(Component.literal("§e[Justice] La contestation déposée par votre avocat a été rejetée."));
            return "Contestation rejetée.";
        }
        List<Rec> recs = d.recordsOf(client);
        Rec rec = null;
        for (Rec r : recs) if (r.id == q.rec) rec = r;
        String note = "Contestation acceptée (avocat " + q.byName + ")";
        if (rec != null) {
            recs.remove(rec);
            if (rec.type == PoliceData.AMENDE && rec.paid && rec.amount > 0) {
                fr.minenorth.api.MineNorth.bank().refund(s, client, rec.amount, "police:contestation");
                note += ", amende remboursée " + (rec.amount / 100) + " €";
            }
            if (rec.points > 0) {
                int left = fr.minenorth.police.compat.Compat.addPoints(s, client, rec.points);
                note += left >= 0 ? ", " + rec.points + " point(s) rendu(s) (total " + left + ")"
                        : ", points non rendus (pas de permis de conduire valide)";
            }
            note += " : " + rec.text;
        }
        PoliceService.addEntry(s, client, q.targetName, PoliceData.NOTE, "Tribunal", note);
        ServerPlayer l = s.getPlayerList().getPlayer(q.by);
        if (l != null) l.sendSystemMessage(Component.literal("§a[Justice] Contestation acceptée pour " + q.targetName + "."));
        ServerPlayer c = s.getPlayerList().getPlayer(client);
        if (c != null) c.sendSystemMessage(Component.literal("§a[Justice] Votre contestation est acceptée : la décision est annulée"
                + (rec != null && rec.points > 0 ? " et vos points sont rendus (si vous avez un permis de conduire valide)." : ".")));
        d.setDirty();
        return "Contestation acceptée : entrée annulée.";
    }
}
