package fr.minenorth.police.casier;

import fr.minenorth.police.casier.block.CasierBlock;
import fr.minenorth.police.casier.block.CasierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Réseau et ouverture des écrans. Le client demande, le serveur décide et revérifie tout (distance, appartenance, droits). */
public final class CasierNet {
    private CasierNet() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthCasier.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static final int A_EQUIP = 1, A_RETAKE = 2, A_DEPOSIT = 3, A_FINISH = 4, A_CLAIM = 5, A_PERSO = 6,
            A_NEXT_SERVICE = 10, A_STUFF = 11, A_CONSIGNE = 12, A_RESET_STUFF = 13, A_REFRESH = 20;

    public static void register() {
        CHANNEL.registerMessage(0, Act.class, Act::encode, Act::decode, Act::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, View.class, View::encode, View::decode, View::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    // ------------------------------------------------------------------ messages

    /** Action demandée par l'écran du casier. */
    public record Act(int action, BlockPos pos) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(action); b.writeBlockPos(pos); }
        static Act decode(FriendlyByteBuf b) { return new Act(b.readVarInt(), b.readBlockPos()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            NetworkEvent.Context ctx = c.get();
            ctx.enqueueWork(() -> { if (ctx.getSender() != null) CasierNet.handle(ctx.getSender(), this); });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * État affiché par l'écran. state : 0 accès refusé, 1 prêt à s'équiper, 2 équipé, 3 équipement à reprendre (après une mort),
     * 4 équipement perdu (terminer le service), 5 en service pour un autre service.
     */
    public record View(BlockPos pos, String svcId, String svcName, int color, int state, boolean admin, boolean override,
                       int pending, int consigne, boolean freeLeave, List<String> foreign, String message, List<String> services) {
        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos); b.writeUtf(svcId); b.writeUtf(svcName); b.writeInt(color); b.writeVarInt(state);
            b.writeBoolean(admin); b.writeBoolean(override); b.writeVarInt(pending); b.writeVarInt(consigne); b.writeBoolean(freeLeave);
            b.writeVarInt(foreign.size()); foreign.forEach(s -> b.writeUtf(s, 200));
            b.writeUtf(message, 300);
            b.writeVarInt(services.size()); services.forEach(s -> b.writeUtf(s, 120));
        }
        static View decode(FriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            String id = b.readUtf(), name = b.readUtf();
            int color = b.readInt(), state = b.readVarInt();
            boolean admin = b.readBoolean(), ov = b.readBoolean();
            int pend = b.readVarInt(), cons = b.readVarInt();
            boolean free = b.readBoolean();
            List<String> f = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) f.add(b.readUtf(200));
            String msg = b.readUtf(300);
            List<String> sv = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) sv.add(b.readUtf(120));
            return new View(pos, id, name, color, state, admin, ov, pend, cons, free, f, msg, sv);
        }
        void handle(Supplier<NetworkEvent.Context> c) {
            NetworkEvent.Context ctx = c.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.police.casier.client.ClientHooks.view(this)));
            ctx.setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ côté serveur

    private static final int[] COLORS = {0xFF2D5FD0, 0xFFD03A2D, 0xFF2FA65A, 0xFF8A939B};

    private static CasierBlockEntity casierAt(ServerPlayer p, BlockPos pos) {
        if (!p.level().isLoaded(pos)) return null;
        double max = CasierConfig.DISTANCE.get() + 1.5;
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > max * max) return null;
        return p.level().getBlockEntity(pos) instanceof CasierBlockEntity be ? be : null;
    }

    private static int colorFor(CasierBlockEntity be) {
        return switch (be.service()) { case "police" -> COLORS[0]; case "pompiers" -> COLORS[1]; default -> COLORS[3]; };
    }

    /** Clic droit sur un casier. adminMode = clic accroupi (réservé aux OP / chef de service). */
    public static void open(ServerPlayer p, BlockPos low, boolean adminMode) {
        CasierBlockEntity be = casierAt(p, low);
        if (be == null) return;
        View v = build(p, low, be, "");
        boolean showAdmin = adminMode && v.admin();
        if (v.state() == 0 && !v.admin()) {
            p.displayClientMessage(Component.literal(v.message().isEmpty() ? "Ce casier est réservé aux membres du service." : v.message()), true);
            return;
        }
        if (showAdmin || !adminMode || v.state() != 0) CasierBlock.animate((ServerLevel) p.level(), low);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), v);
    }

    static View build(ServerPlayer p, BlockPos pos, CasierBlockEntity be, String message) {
        CasierData d = CasierData.get(p.server);
        ServiceDef sv = Services.get(be.service());
        boolean admin = CasierPerms.isAdmin(p, be.service());
        CompoundTag sess = d.sessions.get(p.getUUID());
        int state;
        String msg = message;
        List<String> foreign = new ArrayList<>();
        if (sess != null) {
            if (!sess.getString("Service").equals(be.service())) {
                state = 5;
                if (msg.isEmpty()) msg = "Tu es en service pour un autre service : utilise un casier du tien.";
            } else {
                state = sess.getBoolean("Given") ? 2 : sess.getBoolean("Lost") ? 4 : 3;
                foreign = ServiceManager.foreignLines(p);
            }
        } else if (sv == null) {
            state = 0;
            if (msg.isEmpty()) msg = "Ce casier n'est relié à aucun service.";
        } else if (!sv.member(p)) {
            state = 0;
            if (msg.isEmpty()) msg = "Ce casier est réservé aux membres : " + sv.name() + ".";
        } else if (!sv.onDuty(p) && !(CasierConfig.PRISE_AU_CASIER.get() || ServiceManager.isCompany(sv))) {
            state = 0;
            if (msg.isEmpty()) msg = "Prends d'abord ton service (tablette ou accueil).";
        } else {
            state = 1;
        }
        List<String> services = new ArrayList<>();
        if (admin) for (ServiceDef s : Services.all()) services.add(s.id() + "|" + s.name());
        boolean free = CasierConfig.FIN_SANS_CASIER.get();
        return new View(pos, be.service(), sv == null ? "(aucun service)" : sv.name(), colorFor(be), state, admin,
                ServiceManager.hasOverride(p.server, be.service()), d.pendingCount(p.getUUID()),
                d.consigneCount(be.service()), free, foreign, msg, services);
    }

    private static void send(ServerPlayer p, BlockPos pos, CasierBlockEntity be, String msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), build(p, pos, be, msg));
    }

    static void handle(ServerPlayer p, Act a) {
        CasierBlockEntity be = casierAt(p, a.pos());
        if (be == null) return;
        ServiceDef sv = Services.get(be.service());
        CasierData d = CasierData.get(p.server);
        String msg = "";
        switch (a.action()) {
            case A_REFRESH -> { }
            case A_EQUIP -> {
                String e = sv == null ? "Ce casier n'est relié à aucun service." : ServiceManager.begin(p, sv);
                msg = e != null ? e : "Équipement pris : ton inventaire civil est en sécurité.";
            }
            case A_RETAKE -> {
                String e = ServiceManager.retake(p);
                msg = e != null ? e : "Équipement repris.";
            }
            case A_DEPOSIT -> {
                CompoundTag s = d.sessions.get(p.getUUID());
                if (s == null || !s.getString("Service").equals(be.service())) msg = "Tu n'es pas en service pour ce casier.";
                else {
                    int n = ServiceManager.deposit(p, s);
                    msg = n == 0 ? "Tu n'as rien à déposer." : n + " pile(s) déposée(s) au casier.";
                }
            }
            case A_FINISH -> {
                CompoundTag s = d.sessions.get(p.getUUID());
                if (s == null || !s.getString("Service").equals(be.service())) msg = "Tu n'es pas en service pour ce casier.";
                else {
                    String e = ServiceManager.finish(p, true);
                    msg = e != null ? e : "Service terminé. Bon repos !";
                }
            }
            case A_PERSO -> {
                View v = build(p, a.pos(), be, "");
                if (v.state() == 0 || v.state() == 5) { msg = "Rangement indisponible."; break; }
                openPerso(p);
                return;
            }
            case A_CLAIM -> {
                int n = ServiceManager.deliverPending(p);
                msg = n > 0 ? "Affaires rendues." : "Pas assez de place dans ton inventaire.";
            }
            case A_NEXT_SERVICE -> {
                if (!CasierPerms.isAdmin(p, be.service())) { msg = "Réservé aux OP et au chef du service."; break; }
                List<ServiceDef> all = Services.all();
                int idx = -1;
                for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(be.service())) idx = i;
                ServiceDef next = all.isEmpty() ? null : all.get((idx + 1) % all.size());
                if (next != null && !CasierPerms.isOp(p) && !CasierPerms.isAdmin(p, next.id())) {
                    // un chef ne peut relier le casier qu'à un service dont il est chef (sinon il pourrait l'offrir à un autre service)
                    msg = "Tu ne peux relier ce casier qu'à un service dont tu es le chef.";
                } else if (next != null) {
                    be.setService(next.id());
                    msg = "Casier relié : " + next.name() + ".";
                }
            }
            case A_STUFF -> {
                if (!CasierPerms.isAdmin(p, be.service()) || sv == null) { msg = "Réservé aux OP et au chef du service."; break; }
                openStuff(p, sv);
                return;
            }
            case A_CONSIGNE -> {
                if (!CasierPerms.isAdmin(p, be.service()) || sv == null) { msg = "Réservé aux OP et au chef du service."; break; }
                if (!openConsigne(p, sv)) msg = "La consigne est déjà ouverte par quelqu'un d'autre.";
                else return;
            }
            case A_RESET_STUFF -> {
                if (!CasierPerms.isAdmin(p, be.service())) { msg = "Réservé aux OP et au chef du service."; break; }
                d.stuff.remove(be.service());
                d.flush(p.server);
                msg = "Stuff remis aux valeurs du fichier de configuration.";
            }
            default -> { }
        }
        send(p, a.pos(), be, msg);
    }

    // ------------------------------------------------------------------ menus admin

    private static final Map<String, UUID> CONSIGNE_LOCK = new HashMap<>();

    private static void openStuff(ServerPlayer p, ServiceDef sv) {
        CasierData d = CasierData.get(p.server);
        SimpleContainer c = new SimpleContainer(27);
        int i = 0;
        for (ItemStack t : ServiceManager.template(p.server, sv.id())) if (i < 27) c.setItem(i++, t);
        c.addListener(cc -> {
            List<ItemStack> l = new ArrayList<>();
            for (int k = 0; k < cc.getContainerSize(); k++) if (!cc.getItem(k).isEmpty()) l.add(cc.getItem(k).copy());
            d.stuff.put(sv.id(), l);
            d.setDirty();
        });
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new ServiceMenu(MineNorthCasier.STUFF_MENU.get(), id, inv, c, true, 3, sv.id()),
                Component.translatable("menu.minenorth_casier.stuff", sv.name())), b -> { b.writeBoolean(true); b.writeVarInt(3); b.writeUtf(sv.id()); });
    }

    /** Rangement personnel : 27 cases à soi, chacun y met ce qu'il veut (sauf l'équipement de service), à tout moment. */
    private static void openPerso(ServerPlayer p) {
        CasierData d = CasierData.get(p.server);
        List<ItemStack> list = d.lockers.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        while (list.size() < 27) list.add(ItemStack.EMPTY);
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) c.setItem(i, list.get(i));
        c.addListener(cc -> {
            List<ItemStack> l = d.lockers.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
            while (l.size() < 27) l.add(ItemStack.EMPTY);
            for (int k = 0; k < 27; k++) l.set(k, cc.getItem(k));
            d.setDirty();
        });
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new ServiceMenu(MineNorthCasier.PERSO_MENU.get(), id, inv, c, false, 3, ""),
                Component.translatable("menu.minenorth_casier.perso")), b -> { b.writeBoolean(false); b.writeVarInt(3); b.writeUtf(""); });
    }

    private static boolean openConsigne(ServerPlayer p, ServiceDef sv) {
        UUID holder = CONSIGNE_LOCK.get(sv.id());
        if (holder != null && !holder.equals(p.getUUID())) {
            ServerPlayer other = p.server.getPlayerList().getPlayer(holder);
            if (other != null && other.containerMenu instanceof ServiceMenu m && !m.ghost && !m.perso && m.service.equals(sv.id())) return false;
        }
        CasierData d = CasierData.get(p.server);
        List<ItemStack> list = d.consigne(sv.id());
        while (list.size() < 54) list.add(ItemStack.EMPTY);
        SimpleContainer c = new SimpleContainer(54);
        for (int i = 0; i < 54; i++) c.setItem(i, list.get(i));
        c.addListener(cc -> {
            List<ItemStack> l = d.consigne(sv.id());
            while (l.size() < 54) l.add(ItemStack.EMPTY);
            for (int k = 0; k < 54; k++) l.set(k, cc.getItem(k));
            d.setDirty();
        });
        CONSIGNE_LOCK.put(sv.id(), p.getUUID());
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new ServiceMenu(MineNorthCasier.CONSIGNE_MENU.get(), id, inv, c, false, 6, sv.id()),
                Component.translatable("menu.minenorth_casier.consigne", sv.name())), b -> { b.writeBoolean(false); b.writeVarInt(6); b.writeUtf(sv.id()); });
        return true;
    }

    static void menuClosed(Player p, ServiceMenu m) {
        if (p instanceof ServerPlayer sp) {
            if (!m.ghost && !m.perso && p.getUUID().equals(CONSIGNE_LOCK.get(m.service))) CONSIGNE_LOCK.remove(m.service);
            CasierData.get(sp.server).flush(sp.server);
        }
    }
}
