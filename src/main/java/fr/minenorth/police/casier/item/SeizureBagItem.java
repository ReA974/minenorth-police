package fr.minenorth.police.casier.item;

import fr.minenorth.police.casier.CasierConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Sac de saisie : reçoit les preuves et objets ramassés pendant le service ; son contenu va à la consigne du service au casier. */
public class SeizureBagItem extends Item {
    public static final String KEY = "Saisie";

    public SeizureBagItem() { super(new Item.Properties().stacksTo(1)); }

    public static boolean isBag(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof SeizureBagItem; }

    public static int count(ItemStack bag) {
        return bag.hasTag() ? bag.getTag().getList(KEY, Tag.TAG_COMPOUND).size() : 0;
    }

    /** Range ce qui rentre ; renvoie ce qui reste (vide si tout est rentré). */
    public static ItemStack insert(ItemStack bag, ItemStack in) {
        ItemStack rest = in.copy();
        ListTag l = bag.getOrCreateTag().getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && !rest.isEmpty(); i++) {
            ItemStack ex = ItemStack.of(l.getCompound(i));
            if (ItemStack.isSameItemSameTags(ex, rest) && ex.getCount() < ex.getMaxStackSize()) {
                int k = Math.min(rest.getCount(), ex.getMaxStackSize() - ex.getCount());
                ex.grow(k);
                rest.shrink(k);
                l.set(i, ex.save(new CompoundTag()));
            }
        }
        while (!rest.isEmpty() && l.size() < CasierConfig.SAC_CAPACITE.get()) {
            ItemStack part = rest.split(Math.min(rest.getCount(), rest.getMaxStackSize()));
            l.add(part.save(new CompoundTag()));
        }
        bag.getOrCreateTag().put(KEY, l);
        return rest;
    }

    /** Vide le sac dans la liste donnée. */
    public static void drain(ItemStack bag, List<ItemStack> out) {
        if (!bag.hasTag()) return;
        ListTag l = bag.getTag().getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            ItemStack st = ItemStack.of(l.getCompound(i));
            if (!st.isEmpty()) out.add(st);
        }
        bag.getTag().remove(KEY);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level lv, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.minenorth_casier.sac_saisie.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(count(st) + " / " + CasierConfig.SAC_CAPACITE.get()).withStyle(ChatFormatting.YELLOW));
    }
}
