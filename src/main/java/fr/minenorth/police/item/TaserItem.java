package fr.minenorth.police.item;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Taser : clic droit maintenu = viser (tir plus précis), clic gauche = tirer.
 * Chaque tir consomme une cartouche (minenorthpolice:cartouche_taser).
 * La cible touchée reçoit les effets de la config (lenteur, faiblesse…) et quelques dégâts.
 */
public class TaserItem extends Item {
    public TaserItem() { super(new Item.Properties().stacksTo(1)); }

    private static int ammoSlot(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.CARTOUCHE_TASER.get())) return i;
        }
        return -1;
    }

    private static int ammo(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(ModItems.CARTOUCHE_TASER.get())) n += st.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ visée (clic droit maintenu)
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(st);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public int getUseDuration(ItemStack st) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack st) { return UseAnim.BOW; }

    /** Clic gauche = tir : on ne casse pas de bloc avec le taser. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) { return false; }

    /** Pas de coup de poing avec le taser (le clic gauche est intercepté côté client, ceci est une sécurité). */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }

    public static boolean aiming(Player p) {
        return p.isUsingItem() && p.getUseItem().is(ModItems.TASER.get());
    }

    // ------------------------------------------------------------------ tir (clic gauche, paquet TaserFirePacket)
    /** Tir : appelé côté serveur quand le joueur fait clic gauche avec le taser en main. Plus précis en visant (clic droit). */
    public static void fire(ServerPlayer p) {
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof TaserItem item) || p.isSpectator() || !p.isAlive()) return;
        if (p.getCooldowns().isOnCooldown(item)) return;
        if (!PoliceTool.allowed(p)) return;
        Level level = p.level();
        PoliceConfig cfg = PoliceConfig.get();
        boolean creative = p.getAbilities().instabuild;
        int slot = ammoSlot(p);
        if (slot < 0 && !creative) {
            level.playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 1.6f);
            p.displayClientMessage(Component.literal("§cPlus de cartouches de taser."), true);
            p.getCooldowns().addCooldown(item, 10);
            return;
        }
        if (!creative) p.getInventory().getItem(slot).shrink(1);
        p.getCooldowns().addCooldown(item, cfg.taser_recharge_ticks);
        level.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9f, 1.8f);

        double cone = aiming(p) ? cfg.taser_precision_visee : cfg.taser_precision_hanche;
        Entity t = PoliceTool.target(p, cfg.taser_portee, cone, e -> e instanceof LivingEntity);
        Vec3 from = p.getEyePosition().add(0, -0.25, 0);
        Vec3 to;
        if (t != null) {
            to = t.getBoundingBox().getCenter();
        } else {
            Vec3 end = p.getEyePosition().add(p.getLookAngle().scale(cfg.taser_portee));
            HitResult hit = level.clip(new ClipContext(p.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            to = hit.getLocation();
        }
        sparks((ServerLevel) level, from, to);
        int left = creative ? -1 : ammo(p);
        if (!(t instanceof LivingEntity target)) {
            p.displayClientMessage(Component.literal("§7Manqué." + (left >= 0 ? " §8(" + left + " cartouche(s))" : "")), true);
            return;
        }
        for (PoliceConfig.Effet e : cfg.taser_effets) {
            ResourceLocation rl = ResourceLocation.tryParse(e.effet.trim());
            MobEffect effect = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (effect != null && e.duree_secondes > 0) {
                target.addEffect(new MobEffectInstance(effect, e.duree_secondes * 20, Math.max(0, e.niveau), false, true, true), p);
            }
        }
        if (cfg.taser_degats > 0) target.hurt(p.damageSources().playerAttack(p), cfg.taser_degats);
        target.setSprinting(false);
        if (cfg.taser_descendre_vehicule && target.isPassenger()) target.stopRiding();
        level.playSound(null, target.blockPosition(), SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 1.0f, 2.0f);
        String name = target instanceof ServerPlayer sp ? PoliceService.display(p.server, sp.getUUID()) : target.getName().getString();
        p.displayClientMessage(Component.literal("§eCible neutralisée : " + name + (left >= 0 ? " §8(" + left + " cartouche(s))" : "")), true);
        if (target instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("§cVous avez été touché par un taser !"));
    }

    private static void sparks(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int n = Math.max(2, (int) (d.length() * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 12, 0.25, 0.4, 0.25, 0.2);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit maintenu : viser · Clic gauche : tirer (1 cartouche)"));
    }
}
