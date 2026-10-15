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
            for (Object wheel : frontWheels(v)) {
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

    /** Roues avant : celles dont la position longitudinale dans le véhicule (axe Z de MTS = vers l'avant) est dans la moitié avant. */
    private static java.util.List<Object> frontWheels(Entity v) {
        java.util.List<Object> all = MtsTires.wheels(v), out = new java.util.ArrayList<>();
        double sum = 0;
        for (Object w : all) sum += MtsTires.number(MtsTires.get(w, "localOffset"), "z", 0);
        double mean = all.isEmpty() ? 0 : sum / all.size();
        for (Object w : all) if (MtsTires.number(MtsTires.get(w, "localOffset"), "z", 0) >= mean - 0.05) out.add(w);
        return out;
    }

    /**
     * Pose et reprise de la herse par les policiers, SANS passer par les évènements de pose / de casse : là où les policiers n'ont pas de droits
     * WorldGuard (la route), un BlockItem normal serait refusé. L'évènement est pris en tout premier et annulé, donc rien n'est transmis à WorldGuard.
     * Clic droit = poser (sur la case visée, ou à côté) ; accroupi + clic droit sur une herse = la reprendre.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        net.minecraft.world.item.ItemStack st = e.getItemStack();
        if (!st.is(fr.minenorth.police.item.ModItems.HERSE.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) || !fr.minenorth.police.item.PoliceTool.allowed(p)) return;
        ServerLevel level = p.serverLevel();
        BlockPos hit = e.getPos();
        if (p.isShiftKeyDown()) {
            if (level.getBlockState(hit).getBlock() instanceof HerseBlock) {
                level.removeBlock(hit, false);
                if (!p.getAbilities().instabuild) p.getInventory().add(new net.minecraft.world.item.ItemStack(fr.minenorth.police.item.ModItems.HERSE.get()));
                level.playSound(null, hit, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            return;
        }
        BlockPos target = level.getBlockState(hit).canBeReplaced() ? hit : hit.relative(e.getFace());
        if (!level.getBlockState(target).canBeReplaced() || !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), net.minecraft.core.Direction.UP)) return;
        level.setBlock(target, fr.minenorth.police.block.ModBlocks.HERSE.get().defaultBlockState().setValue(HerseBlock.FACING, p.getDirection()), 3);
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, target, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
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
