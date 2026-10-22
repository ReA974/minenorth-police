package fr.minenorth.police.casier;

import fr.minenorth.police.casier.item.CasierBlockItem;
import fr.minenorth.police.casier.item.SeizureBagItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthCasier.MOD_ID);

    public static final RegistryObject<Item> CASIER_SERVICE = ITEMS.register("casier_service", () -> new CasierBlockItem(ModBlocks.CASIER_SERVICE.get()));
    public static final RegistryObject<Item> SAC_SAISIE = ITEMS.register("sac_saisie", SeizureBagItem::new);

    /** Objets ajoutés à l'onglet créatif commun « MineNorth » (voir SharedTab). */
    public static final net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator TAB_ITEMS = (p, out) -> {
        out.accept(CASIER_SERVICE.get());
        out.accept(SAC_SAISIE.get());
    };

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
