package fr.minenorth.police;

import fr.minenorth.police.block.RadarBlock;
import fr.minenorth.police.block.RadarBlockEntity;
import fr.minenorth.police.compat.MapBridge;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Alertes « type appel d'urgence » créées par la police elle-même : piratage d'un radar (objet « pirate de radar ») et contrebande détectée
 * par un poste de douane. Chacune prévient la police : message dans le chat, repère sur la carte et entrée dans l'onglet ALERTES de la tablette
 * (avec guidage), retirés à l'expiration. Les alertes sont en mémoire : elles disparaissent au redémarrage du serveur.
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID)
public final class RadarHack {
    private RadarHack() {}

    /** Les identifiants d'alerte sont très grands : jamais en conflit avec ceux des appels du téléphone. */
    private static final long ID_BASE = 8_000_000_000L;
    private static final int MARKER_COLOR = 0xE53935;

    public record Alert(long id, String label, String description, String dim, int x, int y, int z, long createdMs, long untilMs) {
        public String marker() { return label + " #" + (id - ID_BASE); }
    }

    private static final List<Alert> ALERTS = new ArrayList<>();
    private static long counter;

    /** Crée une alerte police (chat, repère de carte, onglet ALERTES) valable {@code minutes} minutes. */
    public static Alert raise(MinecraftServer s, String label, String description, String dim, int x, int y, int z, long minutes) {
        long now = System.currentTimeMillis();
        Alert a = new Alert(ID_BASE + (++counter), label, description, dim, x, y, z, now, now + Math.max(1, minutes) * 60_000L);
        ALERTS.add(a);
        PoliceService.alertPolice(s, "§c§l[ALERTE] §r§c" + label + " en §f" + x + " " + y + " " + z + " §7— " + description
                + " Détails et guidage : tablette, onglet ALERTES.");
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (PoliceService.rank(p) < 0) continue;
            MapBridge.set(p, a.marker(), dim, x, y, z, MARKER_COLOR, false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        return a;
    }

    /** Pirate le radar en pos. Faux si ce n'est pas un radar ou s'il est déjà désactivé. */
    public static boolean start(ServerPlayer hacker, BlockPos pos) {
        ServerLevel level = hacker.serverLevel();
        if (!(level.getBlockState(pos).getBlock() instanceof RadarBlock) || !(level.getBlockEntity(pos) instanceof RadarBlockEntity be)) return false;
        if (be.disabled(level)) {
            hacker.displayClientMessage(Component.literal("§7Ce radar est déjà désactivé."), true);
            return false;
        }
        long minutes = Math.max(1, PoliceConfig.get().radar_hack_minutes);
        be.disable(level.getGameTime() + minutes * 60L * 20L);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 0.8f);
        raise(level.getServer(), "Radar piraté", "radar désactivé " + minutes + " min.", level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), minutes);
        hacker.displayClientMessage(Component.literal("§aRadar piraté : désactivé " + minutes + " min."), true);
        return true;
    }

    /** Alertes actives, vues comme des appels pour l'onglet ALERTES de la tablette. */
    public static List<ModNetwork.CallView> views(ServerPlayer p) {
        long now = System.currentTimeMillis();
        String dim = p.level().dimension().location().toString();
        List<ModNetwork.CallView> out = new ArrayList<>();
        for (Alert a : ALERTS) {
            int dist = a.dim().equals(dim) ? (int) Math.sqrt(p.distanceToSqr(a.x() + 0.5, a.y(), a.z() + 0.5)) : -1;
            long left = Math.max(0, (a.untilMs() - now) / 60_000L) + 1;
            out.add(new ModNetwork.CallView(a.id(), a.label(), "Inconnu", a.dim(), a.x(), a.y(), a.z(),
                    a.description() + " (encore environ " + left + " min)", (int) Math.max(0, (now - a.createdMs()) / 1000), dist));
        }
        return out;
    }

    public static Alert find(long id) {
        for (Alert a : ALERTS) if (a.id() == id) return a;
        return null;
    }

    /** Une fois par seconde : les alertes expirées sont retirées, avec leur repère. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ALERTS.isEmpty() || e.getServer().getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        for (Iterator<Alert> it = ALERTS.iterator(); it.hasNext(); ) {
            Alert a = it.next();
            if (a.untilMs() > now) continue;
            it.remove();
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) MapBridge.remove(p, a.marker());
        }
    }
}
