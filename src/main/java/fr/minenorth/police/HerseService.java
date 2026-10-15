package fr.minenorth.police;

import fr.minenorth.api.MtsTires;
import fr.minenorth.police.block.HerseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herses : chaque tick, pour chaque véhicule MTS en mouvement, on regarde si une roue (ou le trajet qu'elle vient de faire depuis le tick
 * précédent, une voiture rapide avance de plus d'un bloc par tick) touche un bloc herse. La roue crève alors.
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID)
public final class HerseService {
    private HerseService() {}

    /** Véhicules chargés avec leur position au tick précédent. */
    private static final Map<Entity, Vec3> VEHICLES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && MtsTires.isVehicle(e.getEntity())) VEHICLES.put(e.getEntity(), e.getEntity().position());
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
