package fr.minenorth.police.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Accessoire de chantier / de contrôle posé par la police (barrière, plot) : bloc simple orienté, sans données. Posé et repris par code
 * (voir RadarPlacerItem : accroupi + clic droit sur l'accessoire, un objet de pose en main), donc sans évènement que WorldGuard puisse refuser.
 */
public class PropBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Forme quand l'accessoire fait face au nord / sud, et à l'est / ouest. */
    private final VoxelShape shapeNS, shapeEW;

    public PropBlock(VoxelShape shapeNS, VoxelShape shapeEW, MapColor color) {
        super(BlockBehaviour.Properties.of().mapColor(color).strength(1.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
        this.shapeNS = shapeNS;
        this.shapeEW = shapeEW;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Barrière : large selon l'axe de visée, fine dans l'autre sens. */
    public static PropBlock barrier() {
        return new PropBlock(Block.box(0, 0, 6, 16, 13, 10), Block.box(6, 0, 0, 10, 13, 16), MapColor.COLOR_RED);
    }

    /** Plot (cône de chantier). */
    public static PropBlock cone() {
        VoxelShape s = Block.box(4, 0, 4, 12, 15, 12);
        return new PropBlock(s, s, MapColor.COLOR_ORANGE);
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
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(FACING).getAxis() == Direction.Axis.Z ? shapeNS : shapeEW;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
}
