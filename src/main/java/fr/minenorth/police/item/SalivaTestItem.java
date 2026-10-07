package fr.minenorth.police.item;

import fr.minenorth.police.config.PoliceConfig;
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

/** Test salivaire (drogues) : clic droit sur un joueur proche ; résultat après quelques secondes ; usage unique. */
public class SalivaTestItem extends Item {
    public SalivaTestItem() { super(new Item.Properties().stacksTo(16)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        if (!PoliceTool.allowed(p)) return InteractionResultHolder.fail(st);
        if (ToolService.testing(p)) {
            p.displayClientMessage(Component.literal("§7Un test est déjà en cours."), true);
            return InteractionResultHolder.fail(st);
        }
        Entity t = PoliceTool.target(p, PoliceConfig.get().test_portee, 8, e -> e instanceof ServerPlayer);
        if (!(t instanceof ServerPlayer target)) {
            p.displayClientMessage(Component.literal("§7Visez un citoyen proche."), true);
            return InteractionResultHolder.fail(st);
        }
        p.getCooldowns().addCooldown(this, 20);
        ToolService.startTest(p, target);
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur un citoyen : test de dépistage (usage unique)"));
    }
}
