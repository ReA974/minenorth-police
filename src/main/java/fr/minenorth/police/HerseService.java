package fr.minenorth.police;

import fr.minenorth.api.MtsTires;
import fr.minenorth.police.block.HerseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herses : chaque tick, pour chaque véhicule MTS en mouvement PROCHE d'une herse, on regarde si une roue (ou le trajet qu'elle vient de faire
 * depuis le tick précédent, une voiture rapide avance de plus d'un bloc par tick) touche un bloc herse. La roue crève alors.
 * Les herses posées sont mémorisées par dimension : sans herse (ou loin d'elles), un véhicule ne coûte qu'une comparaison de distances.
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID)
public final class HerseService {
    private HerseService() {}

    /** Distance (blocs) en dessous de laquelle un véhicule est examiné autour d'une herse. */
    private static final double NEAR = 6;

    /** Positions des herses d'une dimension, sauvegardées avec le monde. */
    public static final class Data extends SavedData {
        public final java.util.Set<Long> positions = ConcurrentHashMap.newKeySet();

        static Data load(CompoundTag t) {
            Data d = new Data();
            for (long l : t.getLongArray("p")) d.positions.add(l);
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            long[] a = new long[positions.size()];
            int i = 0;
            for (long l : positions) a[i++] = l;
            t.put("p", new LongArrayTag(a));
            return t;
        }
    }

    public static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, "minenorth_herses");
    }

    public static void added(ServerLevel level, BlockPos pos) { Data d = data(level); d.positions.add(pos.asLong()); d.setDirty(); }

    public static void removed(ServerLevel level, BlockPos pos) { Data d = data(level); if (d.positions.remove(pos.asLong())) d.setDirty(); }

    /** Véhicules chargés avec leur position au tick précédent. */
    private static final Map<Entity, Vec3> VEHICLES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && MtsTires.isVehicle(e.getEntity())) VEHICLES.put(e.getEntity(), e.getEntity().position());
    }

    private static boolean nearAny(Data d, Vec3 p) {
        for (long l : d.positions) {
            BlockPos b = BlockPos.of(l);
            double dx = b.getX() + 0.5 - p.x, dz = b.getZ() + 0.5 - p.z;
            if (dx * dx + dz * dz < NEAR * NEAR && Math.abs(b.getY() - p.y) < NEAR) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || VEHICLES.isEmpty()) return;
        for (Iterator<Map.Entry<Entity, Vec3>> it = VEHICLES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Vec3> en = it.next();
            Entity v = en.getKey();
            if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) { it.remove(); continue; }
            Vec3 now = v.position(), delta = now.subtract(en.getValue());
            en.setValue(now);
            if (delta.lengthSqr() < 1e-5) continue;   // à l'arrêt : rien à vérifier
            Data d = data(level);
            if (d.positions.isEmpty() || !nearAny(d, now)) continue;
            for (Object wheel : MtsTires.wheels(v)) {
                if (MtsTires.isFlat(wheel)) continue;
                double[] p = MtsTires.position(wheel);
                if (p == null || Double.isNaN(p[0])) continue;
                double bottom = p[1] - MtsTires.radius(wheel);
                if (!onHerse(level, p, bottom, delta)) continue;
                if (MtsTires.puncture(wheel))
                    level.playSound(null, BlockPos.containing(p[0], p[1], p[2]), SoundEvents.GENERIC_BURN, SoundSource.NEUTRAL, 1.0f, 1.6f);
            }
        }
    }

    /** La roue est sur une herse à son emplacement actuel, ou l'a croisée depuis le tick précédent. */
    private static boolean onHerse(ServerLevel level, double[] p, double bottom, Vec3 delta) {
        for (double k = 0; k <= 1.0001; k += 0.34) {
            double x = p[0] - delta.x * k, z = p[2] - delta.z * k, y = bottom - delta.y * k;
            for (double dy : new double[] {0.15, -0.3})
                if (level.getBlockState(BlockPos.containing(x, y + dy, z)).getBlock() instanceof HerseBlock) return true;
        }
        return false;
    }
}
