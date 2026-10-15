package fr.minenorth.police.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Herse : seuls les policiers (ou tout le monde si « objets_reserves_police » est désactivé) peuvent la poser. */
public class HerseItem extends BlockItem {
    public HerseItem(Block block, Properties props) { super(block, props); }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getPlayer() instanceof ServerPlayer p && !PoliceTool.allowed(p)) return InteractionResult.FAIL;
        return super.useOn(c);
    }
}
