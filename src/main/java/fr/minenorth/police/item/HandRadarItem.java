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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Radar à main (jumelles laser) : clic droit maintenu = regarder dans les jumelles, clic gauche = vitesse en km/h du véhicule visé.
 * Clic droit accroupi = changer la limite affichée. Les véhicules MTS donnent la vitesse de leur compteur.
 */
public class HandRadarItem extends Item {
    public HandRadarItem() { super(new Item.Properties().stacksTo(1)); }

    public static int limit(ItemStack st) {
        int v = st.getOrCreateTag().getInt("Limite");
        return v > 0 ? v : 50;
    }

    /**
     * Clic droit maintenu = regarder dans les jumelles (zoom + viseur, côté client) ; clic gauche pendant ce temps = mesure
     * ({@link #measure}, via un paquet). Accroupi + clic droit = changer la limite affichée.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(st);
        if (player.isShiftKeyDown()) {
            // Accroupi + clic droit : écran de réglage de la limite (le serveur revérifie à la validation).
            if (level.isClientSide) {
                int cur = limit(st);
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> fr.minenorth.police.client.RadarLimitScreen.open(cur));
            }
            return InteractionResultHolder.success(st);
        }
        if (!level.isClientSide && player instanceof ServerPlayer p && !PoliceTool.allowed(p)) return InteractionResultHolder.fail(st);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override public int getUseDuration(ItemStack st) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.SPYGLASS; }

    /** Vrai tant que le joueur regarde dans le radar à main (zoom et viseur affichés par le client). */
    public static boolean scoping(Player p) { return p.isUsingItem() && p.getUseItem().getItem() instanceof HandRadarItem; }

    /** Applique la limite choisie dans l'écran (côté serveur) : objet en main, droit de police, valeur bornée. */
    public static void setLimit(ServerPlayer p, int value) {
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof HandRadarItem) || !p.isAlive() || p.isSpectator() || !PoliceTool.allowed(p)) return;
        int v = Math.max(5, Math.min(200, value));
        st.getOrCreateTag().putInt("Limite", v);
        p.displayClientMessage(Component.literal("§bLimite du radar : §f" + v + " km/h"), true);
    }

    /** Mesure du véhicule visé (côté serveur, appelée par le paquet du clic gauche en visée). */
    public static void measure(ServerPlayer p) {
        if (fr.minenorth.police.cuff.CuffService.isCuffed(p.getUUID())) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof HandRadarItem item) || !p.isAlive() || p.isSpectator() || !scoping(p)) return;
        if (p.getCooldowns().isOnCooldown(item) || !PoliceTool.allowed(p)) return;
        Entity target = PoliceTool.target(p, PoliceConfig.get().radar_portee, 2.5, HandRadarItem::measurable);
        p.getCooldowns().addCooldown(item, 15);
        if (target == null) {
            p.displayClientMessage(Component.literal("§7Aucun véhicule visé."), true);
            return;
        }
        // Un joueur assis dans un véhicule MTS : on mesure le véhicule. Sur une monture / un bateau : la monture.
        if (Mts.isSeat(target.getVehicle()) || Mts.isSeat(target)) {
            Entity v = nearestMtsVehicle(target);
            if (v != null) target = v;
        } else if (target.isPassenger()) {
            target = target.getRootVehicle();
        }
        ToolService.measure(p, target, limit(st));
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
        tip.add(Component.literal("§8Clic droit maintenu : viser · Clic gauche : mesurer · Accroupi : limite"));
    }
}
