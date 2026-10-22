package fr.minenorth.police.casier.item;

import fr.minenorth.police.casier.CasierPerms;
import fr.minenorth.police.casier.block.CasierBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Pose réservée aux OP et, si la config l'autorise, au chef du service du casier. */
public class CasierBlockItem extends BlockItem {
    public CasierBlockItem(Block b) { super(b, new Item.Properties().stacksTo(16)); }

    @Override
    public InteractionResult place(BlockPlaceContext c) {
        Player p = c.getPlayer();
        if (p != null && getBlock() instanceof CasierBlock cb && !CasierPerms.canPlaceOrBreak(p, cb.defaultService)) {
            if (!c.getLevel().isClientSide) p.displayClientMessage(Component.literal("Seuls un OP ou le chef du service peuvent poser un casier.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        return super.place(c);
    }
}
