package fr.minenorth.police.casier;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pont facultatif vers Curios (bijoux, accessoires) par réflexion : sans Curios, ne fait rien. */
public final class Curios {
    private Curios() {}

    /** Une case d'accessoire. */
    public record SlotRef(String type, boolean cosmetic, int index, IItemHandlerModifiable handler) {
        public ItemStack get() { return handler.getStackInSlot(index); }
        public void set(ItemStack s) { handler.setStackInSlot(index, s); }
    }

    public static boolean present() { return ModList.get().isLoaded("curios"); }

    public static List<SlotRef> slots(LivingEntity e) {
        List<SlotRef> out = new ArrayList<>();
        if (!present()) return out;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object res = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, e);
            Object inv = null;
            if (res instanceof Optional<?> o) inv = o.orElse(null);
            else if (res != null) {
                Method resolve = res.getClass().getMethod("resolve");
                Object r = resolve.invoke(res);
                if (r instanceof Optional<?> o2) inv = o2.orElse(null);
            }
            if (inv == null) return out;
            Object map = inv.getClass().getMethod("getCurios").invoke(inv);
            if (!(map instanceof Map<?, ?> m)) return out;
            for (Map.Entry<?, ?> en : m.entrySet()) {
                Object handler = en.getValue();
                add(out, en.getKey().toString(), false, handler, "getStacks");
                add(out, en.getKey().toString(), true, handler, "getCosmeticStacks");
            }
        } catch (Throwable t) {
            MineNorthCasier.LOG.warn("[Casier] Curios illisible : {}", t.toString());
        }
        return out;
    }

    /** Équipe l'objet dans une case libre de Curios (menu G) qui l'accepte ; type null = n'importe lequel. Faux si aucune case ne convient. */
    public static boolean equip(LivingEntity e, ItemStack st, String type) {
        if (!present()) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> ctxC = Class.forName("top.theillusivec4.curios.api.SlotContext");
            var ctor = ctxC.getConstructor(String.class, LivingEntity.class, int.class, boolean.class, boolean.class);
            Method valid = api.getMethod("isStackValid", ctxC, ItemStack.class);
            for (SlotRef r : slots(e)) {
                if (r.cosmetic() || !r.get().isEmpty()) continue;
                if (type != null && !type.equals(r.type())) continue;
                Object ctx = ctor.newInstance(r.type(), e, r.index(), false, true);
                if (Boolean.TRUE.equals(valid.invoke(null, ctx, st))) {
                    r.set(st);
                    return true;
                }
            }
        } catch (Throwable t) {
            MineNorthCasier.LOG.warn("[Casier] Équipement Curios impossible : {}", t.toString());
        }
        return false;
    }

    private static void add(List<SlotRef> out, String type, boolean cosmetic, Object stacksHandler, String method) throws Exception {
        Object h = stacksHandler.getClass().getMethod(method).invoke(stacksHandler);
        if (h instanceof IItemHandlerModifiable mod) {
            for (int i = 0; i < mod.getSlots(); i++) out.add(new SlotRef(type, cosmetic, i, mod));
        }
    }
}
