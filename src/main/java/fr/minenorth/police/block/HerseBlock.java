package fr.minenorth.police.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;

/**
 * Bloc « de rendu » de la herse : jamais posé dans le monde (WorldGuard refuse les poses de blocs de la police). Il ne sert qu'à fournir le
 * modèle et la texture que {@link fr.minenorth.police.client.HerseRenderer} dessine pour l'entité {@link fr.minenorth.police.HerseEntity}.
 */
public class HerseBlock extends HorizontalDirectionalBlock {
    public HerseBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.CHAIN).noOcclusion().noCollission());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
}
