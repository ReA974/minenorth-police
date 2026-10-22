package fr.minenorth.police.cuff;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import fr.minenorth.police.item.PoliceTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Écran de fouille d'un joueur menotté : son inventaire complet (41 emplacements : 36 + armure + main gauche) en lecture, objets illégaux en rouge.
 * Un clic sur un objet lance son retrait (délai de la config, barre de progression) ; l'objet passe ensuite dans l'inventaire du fouilleur.
 * Le client ne fait qu'afficher : droits, distance, état menotté et délai sont revérifiés ici à chaque tick.
 */
public class SearchMenu extends AbstractContainerMenu {
    public static final int N = 41;
    /** Ordre d'affichage : 3 rangées de l'inventaire, la barre rapide, puis armure (tête, torse, jambes, pieds) et main gauche. */
    private static final int[] ORDER = new int[N];
    static {
        int k = 0;
        for (int i = 9; i < 36; i++) ORDER[k++] = i;
        for (int i = 0; i < 9; i++) ORDER[k++] = i;
        for (int i = 39; i >= 36; i--) ORDER[k++] = i;
        ORDER[k++] = 40;
    }

    private final Container view;
    @Nullable private final ServerPlayer actor;
    @Nullable private final ServerPlayer target;
    public final UUID targetId;
    public final String targetName;
    public final boolean police;
    private final DataSlot mask0 = DataSlot.standalone(), mask1 = DataSlot.standalone(), pending = DataSlot.standalone(), progress = DataSlot.standalone();
    private int pendingEnd;

    /** Côté serveur. */
    private SearchMenu(int id, Inventory playerInv, ServerPlayer actor, ServerPlayer target, boolean police) {
        super(ModMenus.FOUILLE.get(), id);
        this.view = target.getInventory();
        this.actor = actor; this.target = target;
        this.targetId = target.getUUID(); this.targetName = PoliceService.display(actor.server, target.getUUID());
        this.police = police;
        init(playerInv);
    }

    /** Côté client : conteneur vide, rempli par les paquets de synchronisation des emplacements. */
    private SearchMenu(int id, Inventory playerInv, UUID targetId, String targetName, boolean police) {
        super(ModMenus.FOUILLE.get(), id);
        this.view = new SimpleContainer(N);
        this.actor = null; this.target = null;
        this.targetId = targetId; this.targetName = targetName; this.police = police;
        init(playerInv);
    }

    public static SearchMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new SearchMenu(id, inv, buf.readUUID(), buf.readUtf(64), buf.readBoolean());
    }

    private void init(Inventory playerInv) {
        for (int i = 0; i < N; i++) {
            int row = i / 9, col = i % 9;
            addSlot(new LockedSlot(view, ORDER[i], 8 + col * 18, 18 + row * 18));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new LockedSlot(playerInv, 9 + r * 9 + c, 8 + c * 18, 122 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new LockedSlot(playerInv, c, 8 + c * 18, 180));
        addDataSlot(mask0); addDataSlot(mask1); addDataSlot(pending); addDataSlot(progress);
        pending.set(-1);
    }

    /** Emplacement en lecture seule : aucun dépôt, aucun retrait direct (tout passe par {@link #clicked}). */
    private static final class LockedSlot extends Slot {
        LockedSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack st) { return false; }
        @Override public boolean mayPickup(Player p) { return false; }
    }

    // ------------------------------------------------------------------ ouverture
    public static void open(ServerPlayer actor, ServerPlayer target, boolean police) {
        String name = PoliceService.display(actor.server, target.getUUID());
        NetworkHooks.openScreen(actor, new SimpleMenuProvider((id, inv, pl) -> new SearchMenu(id, inv, actor, target, police),
                Component.literal("Fouille de " + name)), buf -> { buf.writeUUID(target.getUUID()); buf.writeUtf(name, 64); buf.writeBoolean(police); });
        target.sendSystemMessage(Component.literal("§cVous êtes fouillé par " + PoliceService.display(actor.server, actor.getUUID()) + "."));
        target.displayClientMessage(Component.literal("§cVous êtes fouillé"), true);
        SearchLog.write(actor.server, actor, target, "début de fouille");
    }

    // ------------------------------------------------------------------ accesseurs client
    public boolean illegal(int slot) {
        if (slot < 0 || slot >= N) return false;
        int bits = slot < 31 ? mask0.get() : mask1.get();
        return ((bits >> (slot < 31 ? slot : slot - 31)) & 1) != 0;
    }
    public int pendingSlot() { return pending.get(); }
    public int pendingProgress() { return progress.get(); }

    // ------------------------------------------------------------------ clics (serveur)
    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (!(p instanceof ServerPlayer sp) || sp != actor || target == null) return;
        if (slotId < 0 || slotId >= N || (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE)) return;
        ItemStack st = slots.get(slotId).getItem();
        if (st.isEmpty() || CuffService.isHoodMark(st)) return;
        if (pending.get() == slotId) { pending.set(-1); progress.set(0); return; }       // second clic : annule
        pending.set(slotId);
        int delay = PoliceConfig.get().fouille_delai_secondes * 20;
        pendingEnd = sp.server.getTickCount() + delay;
        if (delay <= 0) finish(sp);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player p) {
        if (actor == null || target == null) return true;       // client
        PoliceConfig cfg = PoliceConfig.get();
        if (p != actor || !actor.isAlive() || !target.isAlive() || target.hasDisconnected() || actor.hasDisconnected()) return false;
        if (!CuffService.isCuffed(target.getUUID()) || CuffService.isCuffed(actor.getUUID())) return false;
        if (actor.level() != target.level() || actor.distanceToSqr(target) > (cfg.fouille_portee + 1) * (cfg.fouille_portee + 1)) return false;
        CuffService.State ts = CuffService.state(target.getUUID());
        return PoliceService.rank(actor) >= 0 || (cfg.fouille_ravisseurs && ts != null && ts.type == CuffService.ARTISANAL && actor.getUUID().equals(ts.captor));
    }

    @Override
    public void broadcastChanges() {
        if (actor != null && target != null) {
            int now = actor.server.getTickCount();
            if (now % 5 == 0) {
                int m0 = 0, m1 = 0;
                for (int i = 0; i < N; i++) {
                    if (!PoliceTool.matchesAny(slots.get(i).getItem(), PoliceConfig.get().fouille_objets_illegaux)) continue;
                    if (i < 31) m0 |= 1 << i; else m1 |= 1 << (i - 31);
                }
                mask0.set(m0); mask1.set(m1);
            }
            int ps = pending.get();
            if (ps >= 0) {
                ItemStack st = slots.get(ps).getItem();
                int total = Math.max(1, PoliceConfig.get().fouille_delai_secondes * 20);
                if (st.isEmpty()) { pending.set(-1); progress.set(0); }
                else if (now >= pendingEnd) finish(actor);
                else progress.set(Math.max(0, Math.min(100, 100 - (pendingEnd - now) * 100 / total)));
            }
        }
        super.broadcastChanges();
    }

    private void finish(ServerPlayer sp) {
        int slot = pending.get();
        pending.set(-1); progress.set(0);
        if (target == null || slot < 0 || slot >= N) return;
        ItemStack st = slots.get(slot).getItem();
        if (st.isEmpty() || CuffService.isHoodMark(st)) return;
        int inv = ORDER[slot];
        ItemStack taken = target.getInventory().removeItemNoUpdate(inv);
        if (taken.isEmpty()) return;
        target.inventoryMenu.broadcastChanges();
        String label = taken.getCount() + "x " + taken.getHoverName().getString();
        boolean illegal = PoliceTool.matchesAny(taken, PoliceConfig.get().fouille_objets_illegaux);
        if (!sp.getInventory().add(taken)) sp.drop(taken, false);
        SearchLog.write(sp.server, sp, target, (police ? "saisie" : "retrait") + " : " + label + (illegal ? " [illégal]" : ""));
        if (police && PoliceConfig.get().fouille_inscrire_casier) {
            PoliceService.addEntry(sp.server, target.getUUID(), target.getGameProfile().getName(), PoliceData.SAISIE,
                    PoliceService.display(sp.server, sp.getUUID()), "Fouille : " + label + (illegal ? " (objet illégal)" : ""));
        }
        target.sendSystemMessage(Component.literal("§cOn vous a retiré : " + label));
        sp.displayClientMessage(Component.literal("§a" + label + (illegal ? " §c(illégal)" : "") + " §7: dans votre inventaire."), true);
    }
}
