package fr.minenorth.police.item;

import fr.minenorth.police.Tribunal;
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

/** Mallette d'avocat : clic droit sur son client (à portée) pour contester une de ses amendes ou condamnations. Réservée aux avocats (config). */
public class LawyerCaseItem extends Item {
    public LawyerCaseItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        Entity t = PoliceTool.target(p, 5, 10, e -> e instanceof ServerPlayer && e != p);
        if (!(t instanceof ServerPlayer client)) {
            p.displayClientMessage(Component.literal("§7Approchez-vous de votre client et visez-le."), true);
            return InteractionResultHolder.fail(st);
        }
        Tribunal.open(p, client);
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur son client : contester une amende ou une condamnation"));
        tip.add(Component.literal("§8Réservé aux avocats"));
    }
}
