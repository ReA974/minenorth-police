package fr.minenorth.police.casier.block;

import fr.minenorth.police.casier.CasierData;
import fr.minenorth.police.casier.CasierNet;
import fr.minenorth.police.casier.CasierPerms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Casier de service : vestiaire métallique de 2 blocs de haut. Étape de porte (0 fermée, 1 s'ouvre, 2 ouverte, 3 se referme) = 4 états de modèle.
 * Le service relié est dans le bloc-entité du bas ; les couleurs (police bleu, pompier rouge, ambulance vert, service gris) ne changent que l'habillage.
 */
public class CasierBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

    /** Service relié par défaut à la pose (vide = à choisir dans l'écran admin). */
    public final String defaultService;

    public CasierBlock(String defaultService) {
        super(BlockBehaviour.Properties.of().strength(5.0f, 1200.0f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        this.defaultService = defaultService;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF, STAGE); }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockPos pos = c.getClickedPos();
        Level lv = c.getLevel();
        if (pos.getY() >= lv.getMaxBuildHeight() - 1 || !lv.getBlockState(pos.above()).canBeReplaced(c)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level lv, BlockPos pos, BlockState st, @Nullable LivingEntity who, ItemStack stack) {
        lv.setBlock(pos.above(), st.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (!lv.isClientSide && lv.getBlockEntity(pos) instanceof CasierBlockEntity be) {
            String svc = defaultService;
            if (who instanceof ServerPlayer sp && !CasierPerms.isOp(sp)) { String c = CasierPerms.chiefService(sp); if (c != null) svc = c; }
            be.setService(svc);
            be.register();
        }
    }

    @Override
    public BlockState updateShape(BlockState st, Direction dir, BlockState other, LevelAccessor lv, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf h = st.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (h == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            return other.is(this) && other.getValue(HALF) != h ? st.setValue(STAGE, other.getValue(STAGE)) : Blocks.AIR.defaultBlockState();
        }
        return st;
    }

    @Override
    public void playerWillDestroy(Level lv, BlockPos pos, BlockState st, Player p) {
        if (!lv.isClientSide) {
            BlockPos o = st.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState os = lv.getBlockState(o);
            if (os.is(this) && os.getValue(HALF) != st.getValue(HALF)) {
                lv.setBlock(o, Blocks.AIR.defaultBlockState(), 35);
                lv.levelEvent(p, 2001, o, Block.getId(os));
            }
        }
        super.playerWillDestroy(lv, pos, st, p);
    }

    @Override
    public void onRemove(BlockState st, Level lv, BlockPos pos, BlockState ns, boolean moved) {
        if (!st.is(ns.getBlock()) && !lv.isClientSide && st.getValue(HALF) == DoubleBlockHalf.LOWER && lv.getServer() != null) {
            CasierData d = CasierData.get(lv.getServer());
            d.casiers.remove(CasierData.key(lv.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()));
            d.setDirty();
        }
        super.onRemove(st, lv, pos, ns, moved);
    }

    @Override
    public float getDestroyProgress(BlockState st, Player p, BlockGetter g, BlockPos pos) {
        BlockPos low = st.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        String svc = g.getBlockEntity(low) instanceof CasierBlockEntity be ? be.service() : "";
        return CasierPerms.canPlaceOrBreak(p, svc) ? super.getDestroyProgress(st, p, g, pos) : 0f;
    }

    @Override
    public InteractionResult use(BlockState st, Level lv, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (lv.isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        BlockPos low = st.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (p instanceof ServerPlayer sp) CasierNet.open(sp, low, sp.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    /** Lance l'animation d'ouverture (appelé quand un joueur ouvre l'écran). */
    public static void animate(ServerLevel lv, BlockPos low) {
        BlockState st = lv.getBlockState(low);
        if (!(st.getBlock() instanceof CasierBlock b)) return;
        int s = st.getValue(STAGE);
        if (s == 0) { setStage(lv, low, 1); lv.scheduleTick(low, b, 3); }
        else if (s == 2) lv.scheduleTick(low, b, 60);
    }

    private static void setStage(ServerLevel lv, BlockPos low, int stage) {
        BlockState a = lv.getBlockState(low);
        BlockState c = lv.getBlockState(low.above());
        if (a.getBlock() instanceof CasierBlock) lv.setBlock(low, a.setValue(STAGE, stage), 2);
        if (c.getBlock() instanceof CasierBlock) lv.setBlock(low.above(), c.setValue(STAGE, stage), 2);
    }

    @Override
    public void tick(BlockState st, ServerLevel lv, BlockPos pos, RandomSource r) {
        if (st.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        int s = st.getValue(STAGE);
        switch (s) {
            case 1 -> { setStage(lv, pos, 2); lv.scheduleTick(pos, this, 60); lv.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.3f); }
            case 2 -> { setStage(lv, pos, 3); lv.scheduleTick(pos, this, 3); }
            case 3 -> { setStage(lv, pos, 0); lv.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.3f); }
            default -> { }
        }
    }

    @Override
    public BlockState rotate(BlockState st, Rotation r) { return st.setValue(FACING, r.rotate(st.getValue(FACING))); }

    @Override
    public BlockState mirror(BlockState st, Mirror m) { return st.rotate(m.getRotation(st.getValue(FACING))); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return st.getValue(HALF) == DoubleBlockHalf.LOWER ? new CasierBlockEntity(pos, st) : null;
    }
}
