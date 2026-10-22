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

    public static final RegistryObject<Block> RADAR_FIXE = BLOCKS.register("radar_fixe", RadarMobileBlock::new);

    /** Radar fixe : borne de 1 x 2 blocs (le trépied ci-dessus est le « radar mobile »). */
    public static final RegistryObject<Block> RADAR_BORNE = BLOCKS.register("radar_borne", RadarBorneBlock::new);

    public static final RegistryObject<Block> HERSE = BLOCKS.register("herse", HerseBlock::new);

    /** Accessoires de contrôle posés par la police : barrière et plot de chantier (voir {@link PropBlock}). */
    public static final RegistryObject<Block> BARRIERE = BLOCKS.register("barriere_chantier", PropBlock::barrier);
    public static final RegistryObject<Block> PLOT = BLOCKS.register("plot_chantier", PropBlock::cone);

    /** Poste de douane : borne de contrôle (voir {@link DouaneBlock}). */
    public static final RegistryObject<Block> DOUANE = BLOCKS.register("douane", DouaneBlock::new);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RadarBlockEntity>> RADAR_FIXE_BE = BLOCK_ENTITIES.register("radar_fixe",
            () -> BlockEntityType.Builder.of(RadarBlockEntity::new, RADAR_FIXE.get(), RADAR_BORNE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DouaneBlockEntity>> DOUANE_BE = BLOCK_ENTITIES.register("douane",
            () -> BlockEntityType.Builder.of(DouaneBlockEntity::new, DOUANE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
