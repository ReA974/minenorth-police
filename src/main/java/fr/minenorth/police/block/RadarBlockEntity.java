package fr.minenorth.police.block;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.compat.Mts;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Radar fixe : analyse toutes les 5 ticks les véhicules MTS dans son rayon, flashe les excès de vitesse et relève la plaque. */
public class RadarBlockEntity extends BlockEntity {
    private static final int SCAN_TICKS = 5;

    private int limit = -1;
    private int flashes;
    /** Dernière position connue de chaque véhicule (mesure par déplacement). */
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, Long> lastTick = new HashMap<>();
    /** Fin du délai avant de pouvoir reflasher ce véhicule. */
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public RadarBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.RADAR_FIXE_BE.get(), pos, state); }

    public int limit() { return limit > 0 ? limit : PoliceConfig.get().radar_fixe_limite_defaut; }
    public int flashes() { return flashes; }

    public void setLimit(int v) { limit = v; setChanged(); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || level.getGameTime() % SCAN_TICKS != 0) return;
        be.scan(sl);
    }

    /** Seuls les véhicules MTS sont flashés (ils ont une plaque) ; les objets MTS posés ne bougent pas. */
    private static boolean candidate(Entity e) { return Mts.isBuilder(e); }

    private void scan(ServerLevel level) {
        PoliceConfig cfg = PoliceConfig.get();
        long now = level.getGameTime();
        double r = cfg.radar_fixe_rayon;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB box = new AABB(worldPosition).inflate(r, Math.min(r, 8), r);
        Set<UUID> seen = new HashSet<>();
        for (Entity e : level.getEntities((Entity) null, box, RadarBlockEntity::candidate)) {
            if (e.position().distanceToSqr(center) > r * r) continue;
            UUID id = e.getUUID();
            seen.add(id);
            double kmh = Double.NaN;
            if (Mts.isBuilder(e)) kmh = Mts.speedKmh(e, cfg.radar_mts_vitesse_compteur);
            Vec3 prev = lastPos.get(id);
            Long prevTick = lastTick.get(id);
            lastPos.put(id, e.position());
            lastTick.put(id, now);
            if (Double.isNaN(kmh)) {
                if (prev == null || prevTick == null || now - prevTick <= 0 || now - prevTick > 40) continue;
                kmh = e.position().subtract(prev).horizontalDistance() / ((now - prevTick) / 20.0) * 3.6;
            }
            int v = (int) Math.round(kmh);
            if (v > limit() + cfg.radar_tolerance_kmh && cooldown.getOrDefault(id, 0L) <= now) {
                cooldown.put(id, now + cfg.radar_fixe_delai_secondes * 20L);
                flash(level, e, v);
            }
        }
        lastPos.keySet().retainAll(seen);
        lastTick.keySet().retainAll(seen);
        cooldown.values().removeIf(t -> t <= now);
    }

    private void flash(ServerLevel level, Entity vehicle, int kmh) {
        PoliceConfig cfg = PoliceConfig.get();
        int lim = limit();
        flashes++;
        setChanged();
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.4, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.7f, 1.8f);

        // Le radar ne relève QUE la plaque : pas de conducteur, pas d'amende automatique.
        String where = worldPosition.getX() + " " + worldPosition.getY() + " " + worldPosition.getZ();
        String model = Mts.vehicleName(vehicle);
        String plate = Mts.plate(vehicle, cfg.plaque_champs);
        PoliceService.recordFlash(level.getServer(), plate, model, kmh, lim, where);
        if (cfg.radar_fixe_alerte_police) {
            String plateText = plate.isEmpty() ? "sans plaque" : "plaque " + plate;
            PoliceService.alertPolice(level.getServer(), "§e[Radar " + where + "] §f" + model + " §7(" + plateText + ") §cflashé à " + kmh
                    + " km/h §7(limite " + lim + ") — détails dans la tablette, onglet RADARS.");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Limite", limit);
        tag.putInt("Flashs", flashes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        limit = tag.contains("Limite") ? tag.getInt("Limite") : -1;
        flashes = tag.getInt("Flashs");
    }
}
