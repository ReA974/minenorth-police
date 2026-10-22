package fr.minenorth.police.casier;

import fr.minenorth.api.PlayerWipeEvent;
import fr.minenorth.police.casier.block.CasierBlock;
import fr.minenorth.police.casier.block.CasierBlockEntity;
import fr.minenorth.police.casier.item.SeizureBagItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Règles du service appliquées côté serveur : pas de jet, de rangement, de don ni de ramassage libre ; mort, connexion, changement de métier. */
public class CasierEvents {
    private int tick;

    @SubscribeEvent
    public void started(ServerStartedEvent e) {
        Services.reload(e.getServer());
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (Services.get("police") == null) Services.reload(p.server);
            ServiceManager.onLogin(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServiceManager.onLogout(p);
    }

    @SubscribeEvent
    public void wipe(PlayerWipeEvent e) {
        CasierData d = CasierData.get(e.server());
        boolean any = d.sessions.remove(e.player()) != null;
        any |= d.pending.remove(e.player()) != null;
        any |= d.customDuty.remove(e.player());
        if (any) { d.flush(e.server()); e.cleaned("Casier"); }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        boolean second = tick % 20 == 0;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            boolean in = ServiceManager.inSession(p);
            if (in) guardContainers(p);
            if (!second) continue;
            ServiceManager.purgeStray(p);
            if (in) ServiceManager.check(p);
            if (tick % 100 == 0) ServiceManager.deliverPending(p);
        }
    }

    /** L'équipement de service ne quitte jamais l'inventaire du joueur : coffre, ender chest, établi, cheval… tout conteneur est nettoyé. */
    private void guardContainers(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        if (m == null || m == p.inventoryMenu || !CasierConfig.JET_INTERDIT.get()) return;
        boolean moved = false;
        for (Slot s : m.slots) {
            if (s.container instanceof Inventory || s.getClass().getName().startsWith("top.theillusivec4.curios")) continue;   // le menu G (Curios) est le corps du joueur
            ItemStack st = s.getItem();
            if (!Marks.is(st)) continue;
            s.set(ItemStack.EMPTY);
            ItemStack back = st.copy();
            p.getInventory().add(back);   // sans place : l'équipement se reprend gratuitement au casier
            moved = true;
        }
        ItemStack carried = m.getCarried();
        if (Marks.is(carried)) {
            m.setCarried(ItemStack.EMPTY);
            p.getInventory().add(carried.copy());
            moved = true;
        }
        if (moved) {
            m.broadcastChanges();
            p.displayClientMessage(Component.literal("L'équipement de service ne se range pas dans un conteneur.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void toss(ItemTossEvent e) {
        ItemStack st = e.getEntity().getItem();
        if (!Marks.is(st) || !CasierConfig.JET_INTERDIT.get()) return;
        e.setCanceled(true);
        Player p = e.getPlayer();
        p.getInventory().add(st.copy());
        p.displayClientMessage(Component.literal("Tu ne peux pas jeter ton équipement de service.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void pickup(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemEntity ie = e.getItem();
        ItemStack st = ie.getItem();
        boolean in = ServiceManager.inSession(p);
        if (Marks.is(st)) {
            if (!in || !p.getUUID().equals(Marks.owner(st))) e.setCanceled(true);
            return;
        }
        if (!in) return;
        String mode = CasierConfig.RAMASSAGE.get();
        if (mode.equals("LIBRE")) return;
        e.setCanceled(true);
        if (!mode.equals("SAC")) return;
        Inventory inv = p.getInventory();
        ItemStack rest = st.copy();
        boolean hadBag = false;
        for (int i = 0; i < inv.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack bag = inv.getItem(i);
            if (SeizureBagItem.isBag(bag) && Marks.is(bag)) {
                hadBag = true;
                rest = SeizureBagItem.insert(bag, rest);
            }
        }
        if (rest.getCount() != st.getCount()) {
            if (rest.isEmpty()) ie.discard(); else ie.setItem(rest);
            p.displayClientMessage(Component.literal("Rangé dans ton sac de saisie : " + st.getHoverName().getString()).withStyle(ChatFormatting.YELLOW), true);
        } else if (p.tickCount % 20 == 0) {
            p.displayClientMessage(Component.literal(hadBag ? "Ton sac de saisie est plein." : "Il te faut un sac de saisie pour ramasser en service.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void drops(LivingDropsEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServiceManager.onDeath(p, e.getDrops());
    }

    /** Curios ajoute ses objets aux drops après nous : second passage pour retirer l'équipement marqué du menu G. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void dropsLate(LivingDropsEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServiceManager.onDeath(p, e.getDrops());
    }

    @SubscribeEvent
    public void interactEntity(PlayerInteractEvent.EntityInteract e) {
        denyMarkedOnStorage(e, e.getTarget());
    }

    @SubscribeEvent
    public void interactEntitySpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        denyMarkedOnStorage(e, e.getTarget());
    }

    private void denyMarkedOnStorage(PlayerInteractEvent e, Entity target) {
        if (!(target instanceof ItemFrame) && !(target instanceof ArmorStand) && !(target instanceof Allay)) return;
        if (!CasierConfig.JET_INTERDIT.get()) return;
        if (Marks.is(e.getEntity().getMainHandItem()) || Marks.is(e.getEntity().getOffhandItem())) {
            e.setCanceled(true);
            if (!e.getLevel().isClientSide) e.getEntity().displayClientMessage(Component.literal("L'équipement de service ne se donne pas.").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Accroupi avec un objet en main, le clic droit sur un casier ouvrirait sinon rien : on force l'ouverture (écran admin). */
    @SubscribeEvent
    public void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().getBlockState(e.getPos()).getBlock() instanceof CasierBlock && e.getEntity().isShiftKeyDown()) {
            e.setUseBlock(Event.Result.ALLOW);
            e.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getState().getBlock() instanceof CasierBlock)) return;
        var pos = e.getState().getValue(CasierBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? e.getPos() : e.getPos().below();
        String svc = e.getLevel().getBlockEntity(pos) instanceof CasierBlockEntity be ? be.service() : "";
        if (!CasierPerms.canPlaceOrBreak(e.getPlayer(), svc)) {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.literal("Seuls un OP ou le chef du service peuvent retirer un casier.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void placeBlock(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && (Marks.is(p.getMainHandItem()) || Marks.is(p.getOffhandItem())) && !(e.getPlacedBlock().getBlock() instanceof CasierBlock)) {
            e.setCanceled(true);
        }
    }
}
