package fr.minenorth.police.block;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Radar fixe : flashe les véhicules (MTS ou montures) qui passent trop vite dans son rayon.
 * Clic droit d'un policier = écran (limite, rangement) ; accroupi = infos. Démontable seulement par la police.
 */
public class RadarBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 8, 12), Block.box(2, 8, 1, 14, 16, 11));

    public RadarBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 1200f).sound(SoundType.METAL)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RadarBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.RADAR_FIXE_BE.get(), RadarBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof RadarBlockEntity be) {
            p.displayClientMessage(Component.literal("§bRadar posé — limite " + be.limit() + " km/h (clic droit : réglages et rangement)."), true);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || !(level.getBlockEntity(pos) instanceof RadarBlockEntity be)) {
            return InteractionResult.PASS;
        }
        boolean police = PoliceService.rank(p) >= 0;
        // Borne fixe : posée et gérée par les admins seulement ; radar mobile : par les policiers.
        boolean manager = state.getBlock() instanceof RadarBorneBlock ? isAdmin(p) : police;
        if (!manager || p.isShiftKeyDown()) {
            p.displayClientMessage(Component.literal("§bRadar automatique §7— limite §f" + be.limit() + " km/h"
                    + (police ? " §7· " + be.flashes() + " flash(s)" : "")
                    + (be.disabled(level) ? " §c· PIRATÉ (" + be.disabledMinutesLeft(level) + " min)" : "")), true);
            return InteractionResult.SUCCESS;
        }
        fr.minenorth.police.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new fr.minenorth.police.network.ModNetwork.RadarOpenPacket(pos, be.limit(), be.flashes(), !(state.getBlock() instanceof RadarBorneBlock)));
        return InteractionResult.SUCCESS;
    }

    /**
     * Application d'un choix de l'écran (côté serveur) : droit de police, distance, bloc réellement un radar. Limite bornée ;
     * « ranger » retire le radar (les deux blocs pour la borne) et rend l'objet, sans passer par un évènement de casse.
     */
    private static boolean isAdmin(ServerPlayer p) { return p.hasPermissions(2) || p.getAbilities().instabuild; }

    public static void apply(ServerPlayer p, BlockPos pos, int limit, boolean pickup) {
        Level level = p.level();
        if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(Vec3.atCenterOf(pos)) > 64 || !level.isLoaded(pos)) return;
        BlockState st = level.getBlockState(pos);
        boolean police = PoliceService.rank(p) >= 0;
        if (!(st.getBlock() instanceof RadarBorneBlock ? isAdmin(p) : police)) return;
        if (!(st.getBlock() instanceof RadarBlock) || !(level.getBlockEntity(pos) instanceof RadarBlockEntity be)) return;
        if (!pickup) {
            be.setLimit(Math.max(5, Math.min(200, limit)));
            p.displayClientMessage(Component.literal("§bLimite du radar : §f" + be.limit() + " km/h"), true);
            return;
        }
        boolean borne = st.getBlock() instanceof RadarBorneBlock;
        if (borne) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 35);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (!p.getAbilities().instabuild) {
            p.getInventory().placeItemBackInInventory(new ItemStack((borne ? ModItems.RADAR_FIXE_POLICE : ModItems.RADAR_MOBILE).get()));
        }
        level.playSound(null, pos, st.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1f, 1f);
        p.displayClientMessage(Component.literal("§7Radar rangé."), true);
    }
}
