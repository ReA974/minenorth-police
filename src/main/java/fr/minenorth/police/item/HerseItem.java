package fr.minenorth.police.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Herse. La pose et la reprise ne passent PAS par ce BlockItem : {@link fr.minenorth.police.HerseService#onUse} les gère à la main (sans
 * évènement de pose), pour que WorldGuard ne les bloque pas là où les policiers n'ont pas de droits de construction.
 */
public class HerseItem extends BlockItem {
    public HerseItem(Block block, Properties props) { super(block, props); }
}
