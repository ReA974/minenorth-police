package fr.minenorth.police.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Objet dont tout le comportement est géré côté serveur par {@link fr.minenorth.police.cuff.CuffService} (clic droit sur un joueur) :
 * menottes, clé de menottes, sac de jute. L'objet ne porte que son infobulle.
 */
public class TipItem extends Item {
    private final String[] lines;

    public TipItem(Properties props, String... lines) {
        super(props);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        for (String l : lines) tip.add(Component.literal(l));
    }
}
