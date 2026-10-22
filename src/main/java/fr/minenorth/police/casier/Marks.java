package fr.minenorth.police.casier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Marquage « service » des objets d'équipement (NBT « service » + service + propriétaire). */
public final class Marks {
    private Marks() {}

    public static final String KEY = "service";

    public static boolean is(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(KEY);
    }

    public static ItemStack mark(ItemStack s, String service, UUID owner) {
        CompoundTag t = s.getOrCreateTag();
        t.putBoolean(KEY, true);
        t.putString("service_id", service);
        t.putString("service_owner", owner.toString());
        return s;
    }

    public static UUID owner(ItemStack s) {
        if (!is(s)) return null;
        try { return UUID.fromString(s.getTag().getString("service_owner")); } catch (Exception e) { return null; }
    }

    public static String service(ItemStack s) {
        return is(s) ? s.getTag().getString("service_id") : "";
    }

    /** Retire récursivement les piles marquées rangées dans un sac, un shulker, une ceinture… Renvoie vrai si quelque chose a été retiré. */
    public static boolean strip(CompoundTag t) {
        boolean changed = false;
        for (String k : new java.util.ArrayList<>(t.getAllKeys())) {
            Tag v = t.get(k);
            if (v instanceof ListTag l && l.getElementType() == Tag.TAG_COMPOUND) {
                for (int i = l.size() - 1; i >= 0; i--) {
                    CompoundTag c = l.getCompound(i);
                    CompoundTag inner = c.contains("tag", Tag.TAG_COMPOUND) ? c.getCompound("tag") : null;
                    if (inner != null && inner.getBoolean(KEY)) { l.remove(i); changed = true; }
                    else if (strip(c)) changed = true;
                }
            } else if (v instanceof CompoundTag c && strip(c)) changed = true;
        }
        return changed;
    }
}
