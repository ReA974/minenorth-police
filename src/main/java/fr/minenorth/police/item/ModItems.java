package fr.minenorth.police.item;

import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthPolice.MOD_ID);
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablette_police", TabletItem::new);
    public static final RegistryObject<Item> RADAR_MAIN = ITEMS.register("radar_main", HandRadarItem::new);
    public static final RegistryObject<Item> RADAR_FIXE = ITEMS.register("radar_fixe",
            () -> new BlockItem(ModBlocks.RADAR_FIXE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TEST_SALIVAIRE = ITEMS.register("test_salivaire", SalivaTestItem::new);
    public static final RegistryObject<Item> DETECTEUR_METAUX = ITEMS.register("detecteur_metaux", MetalDetectorItem::new);
    public static final RegistryObject<Item> TASER = ITEMS.register("taser", TaserItem::new);
    public static final RegistryObject<Item> CARTOUCHE_TASER = ITEMS.register("cartouche_taser", () -> new Item(new Item.Properties().stacksTo(64)));

    /** Onglet « Police MineNorth » du menu créatif. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthPolice.MOD_ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("police", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorthpolice"))
            .icon(() -> new ItemStack(TABLET.get()))
            .displayItems((params, out) -> {
                out.accept(TABLET.get());
                out.accept(RADAR_MAIN.get());
                out.accept(RADAR_FIXE.get());
                out.accept(TASER.get());
                out.accept(CARTOUCHE_TASER.get());
                out.accept(TEST_SALIVAIRE.get());
                out.accept(DETECTEUR_METAUX.get());
            })
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
