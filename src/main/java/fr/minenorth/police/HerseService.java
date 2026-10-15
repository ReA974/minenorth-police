package fr.minenorth.police;

import fr.minenorth.api.MtsTires;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.item.PoliceTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herses : entités {@link HerseEntity} posées par les policiers. Chaque tick, pour chaque véhicule MTS en mouvement proche d'une herse, on regarde
 * si une roue (ou le trajet qu'elle vient de faire depuis le tick précédent, une voiture rapide avance de plus d'un bloc par tick) passe sur la
 * bande. La roue crève alors.
 * La pose et la reprise ne sont pas des poses de bloc : WorldGuard n'a rien à refuser (une entité apparaît par code, sans évènement de pose).
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID)
public final class HerseService {
    private HerseService() {}

    /** Distance (blocs) en dessous de laquelle un véhicule est examiné autour d'une herse. */
    private static final double NEAR = 7;

    private static final Set<HerseEntity> HERSES = ConcurrentHashMap.newKeySet();
    /** Véhicules chargés avec leur position au tick précédent. */
    private static final Map<Entity, Vec3> VEHICLES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en instanceof HerseEntity h) HERSES.add(h);
        else if (MtsTires.isVehicle(en)) VEHICLES.put(en, en.position());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof HerseEntity h) HERSES.remove(h);
        else VEHICLES.remove(e.getEntity());
    }

    // ------------------------------------------------------------------ pose et reprise

    /**
     * Clic droit sur un bloc avec la herse : pose une herse à l'endroit visé (face du dessus : au point cliqué ; autre face : contre le bloc),
     * orientée en travers du regard. Pris en tout premier et annulé : aucun autre mod (WorldGuard) ne voit le clic.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack st = e.getItemStack();
        if (!st.is(ModItems.HERSE.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof ServerPlayer p) || !PoliceTool.allowed(p)) return;
        ServerLevel level = p.serverLevel();
        if (p.isShiftKeyDown()) { pickUpNearest(p, level, e.getHitVec().getLocation()); return; }
        Vec3 at = e.getFace() == Direction.UP ? e.getHitVec().getLocation()
                : Vec3.atBottomCenterOf(e.getPos().relative(e.getFace()));
        HerseEntity h = new HerseEntity(ModEntities.HERSE.get(), level);
        h.moveTo(at.x, at.y, at.z, Math.round(p.getYRot() / 90f) * 90f, 0);
        level.addFreshEntity(h);
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, BlockPos.containing(at), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    /** Accroupi + clic droit directement sur une herse : la reprend. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND || !(e.getTarget() instanceof HerseEntity h)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof ServerPlayer p) || !PoliceTool.allowed(p) || !p.isShiftKeyDown()) return;
        give(p, h);
    }

    private static void pickUpNearest(ServerPlayer p, ServerLevel level, Vec3 near) {
        HerseEntity best = null;
        double bd = 2.5 * 2.5;
        for (HerseEntity h : HERSES) {
            if (h.level() != level) continue;
            double d = h.position().distanceToSqr(near);
            if (d < bd) { bd = d; best = h; }
        }
        if (best != null) give(p, best);
    }

    private static void give(ServerPlayer p, HerseEntity h) {
        h.discard();
        if (!p.getAbilities().instabuild) p.getInventory().add(new ItemStack(ModItems.HERSE.get()));
        p.serverLevel().playSound(null, h.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ crevaison

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || VEHICLES.isEmpty()) return;
        boolean any = !HERSES.isEmpty();
        for (Iterator<Map.Entry<Entity, Vec3>> it = VEHICLES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Vec3> en = it.next();
            Entity v = en.getKey();
            if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) { it.remove(); continue; }
            Vec3 now = v.position(), delta = now.subtract(en.getValue());
            en.setValue(now);
            if (!any || delta.lengthSqr() < 1e-5) continue;   // pas de herse, ou à l'arrêt : rien à vérifier
            java.util.List<HerseEntity> near = new java.util.ArrayList<>();
            for (HerseEntity h : HERSES) {
                if (h.level() != level || h.isRemoved()) continue;
                double dx = h.getX() - now.x, dz = h.getZ() - now.z;
                if (dx * dx + dz * dz < NEAR * NEAR && Math.abs(h.getY() - now.y) < NEAR) near.add(h);
            }
            if (near.isEmpty()) continue;
            for (Object wheel : MtsTires.wheels(v)) {
                if (MtsTires.isFlat(wheel)) continue;
                double[] p = MtsTires.position(wheel);
                if (p == null || Double.isNaN(p[0])) continue;
                double bottom = p[1] - MtsTires.radius(wheel);
                if (!onHerse(near, p, bottom, delta)) continue;
                if (MtsTires.puncture(wheel))
                    level.playSound(null, BlockPos.containing(p[0], p[1], p[2]), SoundEvents.GENERIC_BURN, SoundSource.NEUTRAL, 1.0f, 1.6f);
            }
        }
    }

    /** La roue est sur une herse à son emplacement actuel, ou l'a croisée depuis le tick précédent. */
    private static boolean onHerse(java.util.List<HerseEntity> herses, double[] p, double bottom, Vec3 delta) {
        for (double k = 0; k <= 1.0001; k += 0.25) {
            double x = p[0] - delta.x * k, z = p[2] - delta.z * k, y = bottom - delta.y * k;
            for (HerseEntity h : herses)
                if (Math.abs(y - h.getY()) < 0.6 && h.covers(x, z)) return true;
        }
        return false;
    }
}
