package fr.minenorth.police.casier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu à cases : « stuff » (cases fantômes : l'admin dépose un modèle, rien n'est retiré de son inventaire, donc aucune duplication possible)
 * ou « consigne » (vraies cases, une seule personne à la fois). Les écrans sont dessinés par {@code ServiceScreen}.
 */
public class ServiceMenu extends AbstractContainerMenu {
    public final boolean ghost;
    /** Rangement personnel du joueur. */
    public final boolean perso;
    public final int rows;
    public final String service;
    private final Container container;

    public ServiceMenu(MenuType<?> type, int id, Inventory inv, Container container, boolean ghost, int rows, String service) {
        super(type, id);
        this.ghost = ghost;
        this.perso = type == MineNorthCasier.PERSO_MENU.get();
        this.rows = rows;
        this.service = service;
        this.container = container;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = c + r * 9;
                int x = 8 + c * 18, y = 18 + r * 18;
                addSlot(ghost ? new Slot(container, idx, x, y) {
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                    @Override public boolean mayPickup(Player p) { return false; }
                } : new Slot(container, idx, x, y));
            }
        }
        int top = 18 + rows * 18 + 14;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, top + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, top + 58));
    }

    public static ServiceMenu fromBuffer(MenuType<ServiceMenu> type, int id, Inventory inv, FriendlyByteBuf buf) {
        boolean ghost = buf.readBoolean();
        int rows = buf.readVarInt();
        String service = buf.readUtf();
        return new ServiceMenu(type, id, inv, new SimpleContainer(rows * 9), ghost, rows, service);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (ghost) {
            int size = rows * 9;
            if (slotId >= 0 && slotId < size) {
                if (!p.level().isClientSide) {
                    ItemStack carried = getCarried();
                    if (type == ClickType.PICKUP && !carried.isEmpty()) {
                        ItemStack c = carried.copy();
                        c.setCount(Math.min(c.getCount(), c.getMaxStackSize()));
                        container.setItem(slotId, c);
                    } else if (type == ClickType.PICKUP || type == ClickType.SWAP || type == ClickType.THROW) {
                        container.setItem(slotId, ItemStack.EMPTY);
                    }
                }
                return;
            }
            if (slotId >= size && type == ClickType.QUICK_MOVE) {
                if (!p.level().isClientSide) {
                    ItemStack src = slots.get(slotId).getItem();
                    if (!src.isEmpty()) {
                        for (int i = 0; i < size; i++) {
                            if (container.getItem(i).isEmpty()) { container.setItem(i, src.copy()); break; }
                        }
                    }
                }
                return;
            }
        }
        super.clicked(slotId, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (ghost) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            out = st.copy();
            int size = rows * 9;
            if (index < size) {
                if (!moveItemStackTo(st, size, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(st, 0, size, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        }
        return out;
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        return p.isAlive() && (perso || CasierPerms.isAdmin(p, service));
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide) CasierNet.menuClosed(p, this);
    }
}
