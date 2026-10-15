package fr.minenorth.police.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Herse de police : bande de pointes posée en travers de la route. Un bloc plat sans collision ; ce sont les roues des véhicules MTS qui la
 * franchissent qui crèvent (voir {@link fr.minenorth.police.HerseService}).
 */
public class HerseBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 3, 16, 3, 13), SHAPE_EW = Block.box(3, 0, 0, 13, 3, 16);

    public HerseBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.CHAIN).noOcclusion().noCollission());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection()); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(FACING);
        return d == Direction.EAST || d == Direction.WEST ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
}
