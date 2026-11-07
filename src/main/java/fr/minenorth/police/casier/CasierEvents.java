package fr.minenorth.police.casier;

import fr.minenorth.api.PlayerWipeEvent;
import fr.minenorth.police.casier.block.CasierBlock;
import fr.minenorth.police.casier.block.CasierBlockEntity;
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
        any |= d.lockers.remove(e.player()) != null;
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
            if (!second) continue;
            if (in) ServiceManager.check(p);
            if (tick % 100 == 0) ServiceManager.deliverPending(p);
        }
    }

    /** Ramassage en service : libre (défaut), ou interdit pour ce qui n'est pas de son métier (config). Le sac de saisie n'existe plus. */
    @SubscribeEvent
    public void pickup(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack st = e.getItem().getItem();
        if (!ServiceManager.inSession(p) || Marks.is(st) || ServiceManager.kept(st)) return;
        if (CasierConfig.RAMASSAGE.get().equals("INTERDIT")) e.setCanceled(true);
    }

    /** Ancien sac de saisie (retiré le 07/11) : ses exemplaires disparaissent sans erreur au chargement du monde. */
    @SubscribeEvent
    public void missing(net.minecraftforge.registries.MissingMappingsEvent e) {
        net.minecraft.resources.ResourceLocation old = new net.minecraft.resources.ResourceLocation(MineNorthCasier.MOD_ID, "sac_saisie");
        for (var m : e.getAllMappings(net.minecraftforge.registries.ForgeRegistries.Keys.ITEMS)) if (old.equals(m.getKey())) m.ignore();
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
}
