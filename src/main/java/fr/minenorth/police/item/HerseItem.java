package fr.minenorth.police.item;

import net.minecraft.world.item.Item;

/**
 * Herse. Volontairement un objet simple et NON un BlockItem : WorldGuard refuse toute pose de bloc hors de ses zones autorisées dès qu'un objet
 * de type bloc est en main. La pose et la reprise sont faites par {@link fr.minenorth.police.HerseService#onUse} (setBlock direct, sans évènement de pose).
 */
public class HerseItem extends Item {
    public HerseItem(Properties props) { super(props); }
}
