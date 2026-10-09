package fr.minenorth.police;

import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import fr.minenorth.police.data.PoliceData.Cell;
import fr.minenorth.police.data.PoliceData.Detainee;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Garde à vue et prison.
 *
 * Les cellules sont placées par un OP (/police cellule ajouter <nom>) ; une cellule accueille un seul détenu.
 * Le temps ne s'écoule que lorsque le détenu est connecté. Il est ramené dans sa cellule s'il s'en éloigne,
 * change de dimension, meurt ou se reconnecte, et certaines commandes lui sont interdites.
 * À la fin de la peine, il est téléporté au point de sortie (/police cellule sortie), sinon au spawn du monde.
 */
@Mod.EventBusSubscriber
public final class JailService {
    private JailService() {}

    public record Result(String msg, boolean ok) {
        static Result ok(String m) { return new Result(m, true); }
        static Result err(String m) { return new Result(m, false); }
    }

    // ------------------------------------------------------------------ cellules
    private static ServerLevel level(MinecraftServer s, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Cell here(ServerPlayer p, String name) {
        Cell c = new Cell();
        c.name = name; c.dim = p.level().dimension().location().toString();
        c.x = p.getX(); c.y = p.getY(); c.z = p.getZ(); c.yaw = p.getYRot(); c.pitch = p.getXRot();
        return c;
    }

    private static String where(Cell c) {
        return (int) Math.floor(c.x) + " " + (int) Math.floor(c.y) + " " + (int) Math.floor(c.z) + " (" + c.dim + ")";
    }

    public static String addCell(ServerPlayer at, String name) {
        PoliceData d = PoliceData.get(at.server);
        if (d.cell(name) != null) return "§cUne cellule « " + name + " » existe déjà.";
        Cell c = here(at, name.trim());
        d.cells.add(c);
        d.setDirty();
        return "§aCellule « " + c.name + " » placée en " + where(c) + ".";
    }

    public static String removeCell(MinecraftServer s, String name) {
        PoliceData d = PoliceData.get(s);
        Cell c = d.cell(name);
        if (c == null) return "§cCellule « " + name + " » introuvable.";
        UUID who = d.occupant(c.name);
        if (who != null) return "§cLa cellule « " + c.name + " » est occupée par " + PoliceService.display(s, who) + " : libérez-le d'abord.";
        d.cells.remove(c);
        d.setDirty();
        return "§aCellule « " + c.name + " » supprimée.";
    }

    public static List<String> listCells(MinecraftServer s) {
        PoliceData d = PoliceData.get(s);
        List<String> out = new ArrayList<>();
        for (Cell c : d.cells) {
            UUID who = d.occupant(c.name);
            out.add("§b" + c.name + " §7" + where(c) + (level(s, c.dim) == null ? " §c(dimension introuvable)" : "")
                    + (who == null ? " §alibre" : " §coccupée par " + PoliceService.display(s, who)));
        }
        out.add("§7Sortie : " + (d.jailExit == null ? "non définie (spawn du monde)" : where(d.jailExit)));
        return out;
    }

    public static String setExit(ServerPlayer at) {
        PoliceData d = PoliceData.get(at.server);
        d.jailExit = here(at, "");
        d.setDirty();
        return "§aPoint de sortie de la prison placé en " + where(d.jailExit) + ".";
    }

    /** Première cellule libre dont la dimension existe, ou null. */
    public static Cell freeCell(MinecraftServer s) {
        PoliceData d = PoliceData.get(s);
        for (Cell c : d.cells) if (d.occupant(c.name) == null && level(s, c.dim) != null) return c;
        return null;
    }

    private static boolean teleport(ServerPlayer p, Cell c) {
        ServerLevel lvl = level(p.server, c.dim);
        if (lvl == null) return false;
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(lvl, c.x, c.y, c.z, c.yaw, c.pitch);
        p.fallDistance = 0;
        return true;
    }

    /** Ramène le détenu dans sa cellule ; si elle n'existe plus, lui en attribue une autre ou le libère. */
    public static void toCell(ServerPlayer p) {
        PoliceData d = PoliceData.get(p.server);
        Detainee x = d.detainees.get(p.getUUID());
        if (x == null) return;
        Cell c = d.cell(x.cell);
        if (c == null || level(p.server, c.dim) == null) {
            // La cellule n'appartient plus à personne : on la retire temporairement du détenu pour chercher une cellule libre.
            x.cell = "";
            c = freeCell(p.server);
            if (c == null) { release(p.server, p.getUUID(), "plus aucune cellule disponible"); return; }
            x.cell = c.name;
            d.setDirty();
        }
        teleport(p, c);
    }

    // ------------------------------------------------------------------ détention
    public static boolean isJailed(MinecraftServer s, UUID id) { return PoliceData.get(s).detainees.containsKey(id); }

    public static Result jail(ServerPlayer officer, UUID target, int type, int minutes, String reason) {
        MinecraftServer s = officer.server;
        PoliceData d = PoliceData.get(s);
        PoliceConfig cfg = PoliceConfig.get();
        int rank = PoliceService.rank(officer);
        if (rank < 0) return Result.err("Réservé à la police.");
        if (reason.length() < 3) return Result.err("Indiquez le motif de la détention.");
        if (type != PoliceData.JAIL_GAV && type != PoliceData.JAIL_PRISON) return Result.err("Type de détention invalide.");
        if (type == PoliceData.JAIL_PRISON && rank > PoliceData.OFFICIER) return Result.err("Seuls les Officiers et le Commissaire peuvent incarcérer.");
        int max = type == PoliceData.JAIL_GAV ? cfg.garde_a_vue_max_minutes : cfg.prison_max_minutes;
        if (minutes < 1 || minutes > max) return Result.err("Durée : entre 1 et " + max + " min.");
        if (target.equals(officer.getUUID())) return Result.err("Vous ne pouvez pas vous placer vous-même en détention.");
        ServerPlayer on = s.getPlayerList().getPlayer(target);
        if (on == null) return Result.err("Ce citoyen n'est pas connecté.");
        double near = cfg.distance_saisie;
        if (on.level() != officer.level() || on.distanceToSqr(officer) > near * near) return Result.err("Le citoyen doit être à moins de " + cfg.distance_saisie + " blocs de vous.");
        if (d.detainees.containsKey(target)) return Result.err("Ce citoyen est déjà en détention.");
        Cell c = freeCell(s);
        if (c == null) return Result.err(d.cells.isEmpty() ? "Aucune cellule n'a été placée (/police cellule ajouter)." : "Toutes les cellules sont occupées.");

        String officerName = PoliceService.display(s, officer.getUUID());
        Detainee x = new Detainee();
        x.type = type; x.secondsLeft = minutes * 60; x.since = System.currentTimeMillis();
        x.cell = c.name; x.reason = reason; x.officer = officerName;
        d.detainees.put(target, x);
        d.setDirty();
        String what = PoliceData.JAIL_TYPES[type];
        PoliceService.addEntry(s, target, on.getGameProfile().getName(),
                type == PoliceData.JAIL_GAV ? PoliceData.GARDE_A_VUE : PoliceData.CONDAMNATION, officerName, what + " " + minutes + " min : " + reason);
        PoliceService.releaseHold(s, target);
        teleport(on, c);
        on.sendSystemMessage(Component.literal("§c" + what + " : " + minutes + " min (temps de jeu connecté). Motif : " + reason));
        PoliceService.alertPolice(s, "§b[Police] " + PoliceService.display(s, target) + " : " + what.toLowerCase(Locale.ROOT) + " " + minutes
                + " min, cellule « " + c.name + " » (par " + officerName + ").");
        return Result.ok(what + " : " + minutes + " min, cellule « " + c.name + " ».");
    }

    /** Libère un détenu (fin de peine si by est vide). Il est téléporté au point de sortie s'il est connecté. */
    public static Result release(MinecraftServer s, UUID id, String by) {
        PoliceData d = PoliceData.get(s);
        Detainee x = d.detainees.remove(id);
        if (x == null) return Result.err("Ce citoyen n'est pas en détention.");
        d.setDirty();
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) {
            if (d.jailExit == null || !teleport(p, d.jailExit)) {
                BlockPos sp = s.overworld().getSharedSpawnPos();
                if (p.isPassenger()) p.stopRiding();
                p.teleportTo(s.overworld(), sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
            p.sendSystemMessage(Component.literal("§aVous êtes libéré" + (by.isEmpty() ? " : fin de votre " + PoliceData.JAIL_TYPES[x.type].toLowerCase(Locale.ROOT) + "." : " (" + by + ").")));
        }
        PoliceService.alertPolice(s, "§b[Police] " + PoliceService.display(s, id) + " a été libéré" + (by.isEmpty() ? " (fin de peine)." : " (" + by + ")."));
        return Result.ok(PoliceService.display(s, id) + " a été libéré.");
    }

    private static String clock(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ------------------------------------------------------------------ événements
    /** Toutes les secondes : décompte des détenus connectés, rappel en cellule, libération en fin de peine. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        PoliceData d = PoliceData.get(s);
        if (d.detainees.isEmpty()) return;
        double radius = PoliceConfig.get().prison_rayon_evasion;
        for (Map.Entry<UUID, Detainee> en : new ArrayList<>(d.detainees.entrySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null) continue;
            Detainee x = en.getValue();
            x.secondsLeft--;
            d.setDirty();
            if (x.secondsLeft <= 0) { release(s, en.getKey(), ""); continue; }
            p.displayClientMessage(Component.literal("§c" + PoliceData.JAIL_TYPES[x.type] + " : " + clock(x.secondsLeft) + " restant"), true);
            Cell c = d.cell(x.cell);
            if (c == null || !p.level().dimension().location().toString().equals(c.dim) || p.distanceToSqr(c.x, c.y, c.z) > radius * radius) {
                toCell(p);
                if (d.detainees.containsKey(p.getUUID())) p.sendSystemMessage(Component.literal("§cVous ne pouvez pas quitter votre cellule."));
            }
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isJailed(p.server, p.getUUID())) {
            toCell(p);
            Detainee x = PoliceData.get(p.server).detainees.get(p.getUUID());
            if (x != null) p.sendSystemMessage(Component.literal("§c" + PoliceData.JAIL_TYPES[x.type] + " : encore " + clock(x.secondsLeft) + ". Motif : " + x.reason));
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isJailed(p.server, p.getUUID())) toCell(p);
    }

    /** Commandes interdites aux détenus (home, spawn, tpa…), y compris sous la forme « mod:commande ». */
    @SubscribeEvent
    public static void command(CommandEvent e) {
        ServerPlayer p = e.getParseResults().getContext().getSource().getPlayer();
        if (p == null || !isJailed(p.server, p.getUUID())) return;
        String cmd = e.getParseResults().getReader().getString().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String root = cmd.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        for (String blocked : PoliceConfig.get().prison_commandes_bloquees) {
            if (blocked != null && root.equals(blocked.trim().toLowerCase(Locale.ROOT).replace("/", ""))) {
                e.setCanceled(true);
                p.sendSystemMessage(Component.literal("§cCommande interdite pendant votre détention."));
                return;
            }
        }
    }
}
