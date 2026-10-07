package fr.minenorth.police.block;

import fr.minenorth.police.MineNorthPolice;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthPolice.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthPolice.MOD_ID);

    public static final RegistryObject<Block> RADAR_FIXE = BLOCKS.register("radar_fixe", RadarBlock::new);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RadarBlockEntity>> RADAR_FIXE_BE = BLOCK_ENTITIES.register("radar_fixe",
            () -> BlockEntityType.Builder.of(RadarBlockEntity::new, RADAR_FIXE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
