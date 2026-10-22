package fr.minenorth.police.casier;

import fr.minenorth.police.casier.block.CasierBlock;
import fr.minenorth.police.casier.block.CasierBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthCasier.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthCasier.MOD_ID);

    /** Un seul casier : le service est choisi à la pose (chef du service) ou dans l'écran admin. Les anciennes variantes sont remplacées par ce bloc (CasierEvents.missing). */
    public static final RegistryObject<Block> CASIER_SERVICE = BLOCKS.register("casier_service", () -> new CasierBlock(""));

    public static final RegistryObject<BlockEntityType<CasierBlockEntity>> CASIER_BE = BES.register("casier",
            () -> BlockEntityType.Builder.of(CasierBlockEntity::new, CASIER_SERVICE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BES.register(bus);
    }
}
