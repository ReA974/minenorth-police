package fr.minenorth.police.network;

import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.PoliceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModNetwork {
    private ModNetwork() {}

    // Vues envoyées par le serveur.
    public static final int V_LIST = 0, V_DOSSIER = 1, V_REQUESTS = 2, V_ROSTER = 3, V_INVENTORY = 4, V_RADARS = 5, V_PLATES = 6, V_DISPATCH = 7;
    // Actions envoyées par la tablette.
    public static final int A_LIST = 1, A_OPEN = 2, A_REQUESTS = 3, A_ROSTER = 4, A_INVENTORY = 5, A_FINE = 6, A_RECORD = 7, A_DELETE = 8,
            A_WANTED = 9, A_REQUEST = 10, A_DECIDE = 11, A_SEIZE = 12, A_GRADE = 13, A_CLOSE = 14,
            A_RADARS = 15, A_FLASH_DELETE = 16, A_PLATES = 17,
            /** Ouvre le bureau du mod Accueil Police (plaintes, rendez-vous, objets trouvés, fourrière). */
            A_DESK = 18,
            /** Garde à vue (n = 0) ou prison (n = 1) de m minutes, motif a. */
            A_JAIL = 19, A_RELEASE = 20,
            /** Prise / fin de service (n = vue actuelle, pour la réafficher). */
            A_DUTY = 21,
            /** Ouvre l'onglet DISPATCH (réservé au plus haut gradé en service). */
            A_DISPATCH = 22,
            /** Message du dispatch : target = un policier en service (NONE = tous), a = texte. */
            A_DISPATCH_MSG = 23,
            /** Embarque (n = 1) ou fait sortir (n = 0) un suspect du véhicule de police le plus proche (sièges arrière). */
            A_BOARD = 24;
    public static final UUID NONE = new UUID(0, 0);

    private static final String PROTOCOL = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthPolice.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, ViewPacket.class, ViewPacket::encode, ViewPacket::decode, ViewPacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, TaserFirePacket.class, (p, b) -> {}, b -> new TaserFirePacket(), TaserFirePacket::handle);
    }

    public static void send(ServerPlayer p, ViewPacket v) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), v); }

    private static void strings(FriendlyByteBuf b, List<String> l) { b.writeCollection(l, (x, v) -> x.writeUtf(v)); }
    private static List<String> strings(FriendlyByteBuf b) { return b.readList(FriendlyByteBuf::readUtf); }

    public record Citizen(UUID id, String name, String rpName, boolean online, boolean wanted) {
        static void encode(FriendlyByteBuf b, Citizen c) { b.writeUUID(c.id); b.writeUtf(c.name); b.writeUtf(c.rpName); b.writeBoolean(c.online); b.writeBoolean(c.wanted); }
        static Citizen decode(FriendlyByteBuf b) { return new Citizen(b.readUUID(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean()); }
    }
    public record RecView(int id, int type, long time, String officer, String text, long amount, int points, boolean paid) {
        static void encode(FriendlyByteBuf b, RecView r) {
            b.writeVarInt(r.id); b.writeVarInt(r.type); b.writeLong(r.time); b.writeUtf(r.officer); b.writeUtf(r.text);
            b.writeLong(r.amount); b.writeVarInt(r.points); b.writeBoolean(r.paid);
        }
        static RecView decode(FriendlyByteBuf b) {
            return new RecView(b.readVarInt(), b.readVarInt(), b.readLong(), b.readUtf(), b.readUtf(), b.readLong(), b.readVarInt(), b.readBoolean());
        }
    }
    /** identity : {prénom, nom, naissance, lieu, nationalité, n° carte} ou liste vide. points : -1 = mod Permis absent.
     *  jailType : -1 = libre, sinon garde à vue / prison (PoliceData.JAIL_*), jailMinutes restantes dans la cellule jailCell. */
    public record Dossier(UUID id, String name, List<String> identity, boolean online, boolean near, String wantedReason, int points,
                          List<String> licences, List<String> impound, List<RecView> records, int searchMinutes, long unpaid,
                          boolean permisMod, boolean vehiclesMod, List<String> vehicles, int jailType, int jailMinutes, String jailCell,
                          boolean vehicleNear, boolean boarded) {
        static void encode(FriendlyByteBuf b, Dossier d) {
            b.writeUUID(d.id); b.writeUtf(d.name); strings(b, d.identity); b.writeBoolean(d.online); b.writeBoolean(d.near);
            b.writeUtf(d.wantedReason); b.writeInt(d.points); strings(b, d.licences); strings(b, d.impound);
            b.writeCollection(d.records, RecView::encode); b.writeVarInt(d.searchMinutes); b.writeLong(d.unpaid);
            b.writeBoolean(d.permisMod); b.writeBoolean(d.vehiclesMod); strings(b, d.vehicles);
            b.writeInt(d.jailType); b.writeVarInt(d.jailMinutes); b.writeUtf(d.jailCell);
            b.writeBoolean(d.vehicleNear); b.writeBoolean(d.boarded);
        }
        static Dossier decode(FriendlyByteBuf b) {
            return new Dossier(b.readUUID(), b.readUtf(), strings(b), b.readBoolean(), b.readBoolean(), b.readUtf(), b.readInt(),
                    strings(b), strings(b), b.readList(RecView::decode), b.readVarInt(), b.readLong(), b.readBoolean(), b.readBoolean(), strings(b),
                    b.readInt(), b.readVarInt(), b.readUtf(), b.readBoolean(), b.readBoolean());
        }
    }
    public record ReqView(int id, int type, String target, String by, String reason, long time, int status, String decidedBy) {
        static void encode(FriendlyByteBuf b, ReqView r) {
            b.writeVarInt(r.id); b.writeVarInt(r.type); b.writeUtf(r.target); b.writeUtf(r.by); b.writeUtf(r.reason);
            b.writeLong(r.time); b.writeVarInt(r.status); b.writeUtf(r.decidedBy);
        }
        static ReqView decode(FriendlyByteBuf b) {
            return new ReqView(b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf(), b.readUtf(), b.readLong(), b.readVarInt(), b.readUtf());
        }
    }
    public record Officer(UUID id, String name, int grade, boolean online) {
        static void encode(FriendlyByteBuf b, Officer o) { b.writeUUID(o.id); b.writeUtf(o.name); b.writeVarInt(o.grade); b.writeBoolean(o.online); }
        static Officer decode(FriendlyByteBuf b) { return new Officer(b.readUUID(), b.readUtf(), b.readVarInt(), b.readBoolean()); }
    }
    public record Inv(int slot, String label, int count) {
        static void encode(FriendlyByteBuf b, Inv i) { b.writeVarInt(i.slot); b.writeUtf(i.label); b.writeVarInt(i.count); }
        static Inv decode(FriendlyByteBuf b) { return new Inv(b.readVarInt(), b.readUtf(), b.readVarInt()); }
    }

    /** Flash d'un radar fixe, tel qu'affiché dans l'onglet RADARS. */
    public record FlashView(int id, long time, String plate, String model, int speed, int limit, String where) {
        static void encode(FriendlyByteBuf b, FlashView f) {
            b.writeVarInt(f.id); b.writeLong(f.time); b.writeUtf(f.plate); b.writeUtf(f.model); b.writeVarInt(f.speed); b.writeVarInt(f.limit);
            b.writeUtf(f.where);
        }
        static FlashView decode(FriendlyByteBuf b) {
            return new FlashView(b.readVarInt(), b.readLong(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readUtf());
        }
    }

    /** Ligne du fichier des immatriculations (onglet IMMAT.). owner = NONE si inconnu ; known = dossier ouvrable. */
    public record PlateView(String plate, String model, UUID owner, String pseudo, String ownerName, String birth, long time, String shop,
                            boolean known) {
        static void encode(FriendlyByteBuf b, PlateView v) {
            b.writeUtf(v.plate); b.writeUtf(v.model); b.writeUUID(v.owner); b.writeUtf(v.pseudo); b.writeUtf(v.ownerName); b.writeUtf(v.birth);
            b.writeLong(v.time); b.writeUtf(v.shop); b.writeBoolean(v.known);
        }
        static PlateView decode(FriendlyByteBuf b) {
            return new PlateView(b.readUtf(), b.readUtf(), b.readUUID(), b.readUtf(), b.readUtf(), b.readUtf(), b.readLong(), b.readUtf(), b.readBoolean());
        }
    }

    /** Policier en service vu par le dispatch : position et distance au dispatcher (-1 = autre dimension). */
    public record DutyView(UUID id, String name, int grade, long since, String dim, int x, int y, int z, int distance, boolean self) {
        static void encode(FriendlyByteBuf b, DutyView o) {
            b.writeUUID(o.id); b.writeUtf(o.name); b.writeVarInt(o.grade); b.writeLong(o.since); b.writeUtf(o.dim);
            b.writeInt(o.x); b.writeInt(o.y); b.writeInt(o.z); b.writeInt(o.distance); b.writeBoolean(o.self);
        }
        static DutyView decode(FriendlyByteBuf b) {
            return new DutyView(b.readUUID(), b.readUtf(), b.readVarInt(), b.readLong(), b.readUtf(), b.readInt(), b.readInt(), b.readInt(),
                    b.readInt(), b.readBoolean());
        }
    }

    /** État de service : onDuty = ce policier a pris son poste ; dispatcher = il est le plus haut gradé en service ; duty = liste (dispatcher seulement). */
    public record Duty(boolean onDuty, boolean dispatcher, String dispatcherName, int onDutyCount, List<DutyView> duty) {
        static final Duty NONE_DUTY = new Duty(false, false, "", 0, List.of());
        static void encode(FriendlyByteBuf b, Duty d) {
            b.writeBoolean(d.onDuty); b.writeBoolean(d.dispatcher); b.writeUtf(d.dispatcherName); b.writeVarInt(d.onDutyCount);
            b.writeCollection(d.duty, DutyView::encode);
        }
        static Duty decode(FriendlyByteBuf b) {
            return new Duty(b.readBoolean(), b.readBoolean(), b.readUtf(), b.readVarInt(), b.readList(DutyView::decode));
        }
    }

    /** Client -> serveur : tir du taser (clic gauche). Le serveur vérifie l'objet en main, le recharge et les cartouches. */
    public record TaserFirePacket() {
        static void handle(TaserFirePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) fr.minenorth.police.item.TaserItem.fire(sp); });
            c.get().setPacketHandled(true);
        }
    }

    /** Une vue complète de la tablette. Les listes qui ne concernent pas la vue sont vides ; dossier peut être null. */
    public record ViewPacket(int view, int grade, String message, boolean ok, String query, int page, int pages, int pendingRequests,
                             List<Citizen> citizens, Dossier dossier, List<ReqView> requests, List<Officer> officers, List<Inv> inventory,
                             List<FlashView> flashes, List<PlateView> plates, Duty duty) {
        static void encode(ViewPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.view); b.writeVarInt(p.grade); b.writeUtf(p.message); b.writeBoolean(p.ok); b.writeUtf(p.query);
            b.writeVarInt(p.page); b.writeVarInt(p.pages); b.writeVarInt(p.pendingRequests);
            b.writeCollection(p.citizens, Citizen::encode);
            b.writeBoolean(p.dossier != null);
            if (p.dossier != null) Dossier.encode(b, p.dossier);
            b.writeCollection(p.requests, ReqView::encode); b.writeCollection(p.officers, Officer::encode); b.writeCollection(p.inventory, Inv::encode);
            b.writeCollection(p.flashes, FlashView::encode);
            b.writeCollection(p.plates, PlateView::encode);
            Duty.encode(b, p.duty);
        }
        static ViewPacket decode(FriendlyByteBuf b) {
            int view = b.readVarInt(), grade = b.readVarInt();
            String message = b.readUtf(); boolean ok = b.readBoolean(); String query = b.readUtf();
            int page = b.readVarInt(), pages = b.readVarInt(), pending = b.readVarInt();
            List<Citizen> citizens = b.readList(Citizen::decode);
            Dossier dossier = b.readBoolean() ? Dossier.decode(b) : null;
            return new ViewPacket(view, grade, message, ok, query, page, pages, pending, citizens, dossier,
                    b.readList(ReqView::decode), b.readList(Officer::decode), b.readList(Inv::decode), b.readList(FlashView::decode),
                    b.readList(PlateView::decode), Duty.decode(b));
        }
        static void handle(ViewPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.police.client.ClientNetworkHandler.view(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, UUID target, String a, String b, int n, int m) {
        static void encode(ActionPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.action); b.writeUUID(p.target); b.writeUtf(p.a, 96); b.writeUtf(p.b, 32); b.writeInt(p.n); b.writeInt(p.m);
        }
        static ActionPacket decode(FriendlyByteBuf b) {
            return new ActionPacket(b.readVarInt(), b.readUUID(), b.readUtf(96), b.readUtf(32), b.readInt(), b.readInt());
        }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) PoliceService.handle(sp, p); });
            c.get().setPacketHandled(true);
        }
    }
}
