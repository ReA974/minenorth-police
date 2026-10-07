package fr.minenorth.police.item;

import fr.minenorth.police.compat.Mts;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Radar à main (jumelles laser) : clic droit en visant un véhicule = vitesse en km/h.
 * Clic droit accroupi = changer la limite affichée. Les véhicules MTS donnent la vitesse de leur compteur.
 */
public class HandRadarItem extends Item {
    public HandRadarItem() { super(new Item.Properties().stacksTo(1)); }

    public static int limit(ItemStack st) {
        int v = st.getOrCreateTag().getInt("Limite");
        return v > 0 ? v : 50;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        if (!PoliceTool.allowed(p)) return InteractionResultHolder.fail(st);
        if (p.isShiftKeyDown()) {
            int next = ToolService.nextLimit(limit(st));
            st.getOrCreateTag().putInt("Limite", next);
            p.displayClientMessage(Component.literal("§bLimite du radar : §f" + next + " km/h"), true);
            return InteractionResultHolder.success(st);
        }
        Entity target = PoliceTool.target(p, PoliceConfig.get().radar_portee, 2.5, HandRadarItem::measurable);
        p.getCooldowns().addCooldown(this, 15);
        if (target == null) {
            p.displayClientMessage(Component.literal("§7Aucun véhicule visé."), true);
            return InteractionResultHolder.success(st);
        }
        // Un joueur assis dans un véhicule MTS : on mesure le véhicule. Sur une monture / un bateau : la monture.
        if (Mts.isSeat(target.getVehicle()) || Mts.isSeat(target)) {
            Entity v = nearestMtsVehicle(target);
            if (v != null) target = v;
        } else if (target.isPassenger()) {
            target = target.getRootVehicle();
        }
        ToolService.measure(p, target, limit(st));
        return InteractionResultHolder.success(st);
    }

    private static boolean measurable(Entity e) {
        if (Mts.isMts(e)) return Mts.isBuilder(e) || Mts.isSeat(e);
        return e instanceof LivingEntity || e instanceof Boat || e instanceof AbstractMinecart;
    }

    @Nullable
    private static Entity nearestMtsVehicle(Entity near) {
        Entity best = null;
        double bd = 64;
        for (Entity e : near.level().getEntities(near, near.getBoundingBox().inflate(8), Mts::isBuilder)) {
            double d = e.distanceToSqr(near);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Limite : §f" + limit(st) + " km/h"));
        tip.add(Component.literal("§8Clic droit : mesurer · Accroupi : changer la limite"));
    }
}
