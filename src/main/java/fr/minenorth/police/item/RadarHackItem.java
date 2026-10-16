package fr.minenorth.police.item;

import fr.minenorth.police.RadarHack;
import fr.minenorth.police.block.RadarBlock;
import fr.minenorth.police.block.RadarBorneBlock;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Pirate de radar : clic droit MAINTENU face à un radar (mobile ou fixe) à portée. Au bout du temps de piratage, le radar est désactivé
 * (durée de la config) et la police est alertée (voir {@link RadarHack}). L'objet est consommé. Il faut garder le radar en visée.
 */
public class RadarHackItem extends Item {
    private static final double RANGE = 4.0;

    public RadarHackItem() { super(new Item.Properties().stacksTo(1)); }

    @Override public int getUseDuration(ItemStack st) { return Math.max(1, PoliceConfig.get().radar_hack_secondes) * 20; }
    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.NONE; }

    /** Radar visé à portée, sinon null. */
    @Nullable
    private static BlockPos target(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(RANGE));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        var state = p.level().getBlockState(pos);
        if (!(state.getBlock() instanceof RadarBlock)) return null;
        // Borne : la moitié haute renvoie vers la basse, qui porte les données.
        if (state.getBlock() instanceof RadarBorneBlock && state.getValue(RadarBorneBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (target(player) == null) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("§7Visez un radar de près pour le pirater."), true);
            return InteractionResultHolder.fail(st);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    /** Pendant le piratage : on doit garder le radar en visée ; barre de progression et bips. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack st, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return;
        BlockPos pos = target(p);
        if (pos == null) {
            p.stopUsingItem();
            p.displayClientMessage(Component.literal("§cPiratage interrompu : gardez le radar en visée."), true);
            return;
        }
        int total = getUseDuration(st), done = total - remaining;
        if (done % 10 == 0) {
            int pct = Math.min(100, done * 100 / total);
            p.displayClientMessage(Component.literal("§bPiratage du radar… §f" + pct + " %"), true);
            ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, 0.6f + 1.4f * done / total);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return st;
        BlockPos pos = target(p);
        if (pos != null && RadarHack.start(p, pos) && !p.getAbilities().instabuild) st.shrink(1);
        return st;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit maintenu face à un radar : le désactive " + PoliceConfig.get().radar_hack_minutes + " min"));
        tip.add(Component.literal("§8Usage unique · la police est alertée"));
    }
}
