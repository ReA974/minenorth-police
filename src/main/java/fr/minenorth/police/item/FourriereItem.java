package fr.minenorth.police.item;

import fr.minenorth.police.compat.Compat;
import fr.minenorth.police.compat.Mts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Ordre de fourrière : clic droit sur le véhicule visé, il ouvre le menu de mise en fourrière du mod Véhicules (propriétaire, plaque, appel du mécano, mise en fourrière). */
public class FourriereItem extends Item {
    public FourriereItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        if (!PoliceTool.allowed(p)) return InteractionResultHolder.fail(st);
        if (!Compat.hasVehicles()) {
            p.displayClientMessage(Component.literal("§cMod Véhicules non installé."), true);
            return InteractionResultHolder.fail(st);
        }
        Entity v = PoliceTool.target(p, 8, 20, Mts::isBuilder);
        if (v == null) {
            p.displayClientMessage(Component.literal("§7Visez un véhicule (8 blocs maximum)."), true);
            return InteractionResultHolder.fail(st);
        }
        p.getCooldowns().addCooldown(this, 10);
        if (!Compat.openImpound(p, v)) p.displayClientMessage(Component.literal("§cMise en fourrière indisponible (mod Véhicules à mettre à jour)."), true);
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur un véhicule : menu de mise en fourrière"));
    }
}
