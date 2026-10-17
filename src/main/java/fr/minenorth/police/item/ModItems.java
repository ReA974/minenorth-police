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
    /**
     * Radars à poser : des objets simples (pas des BlockItem) et de nom différent du bloc, posés par code (voir {@link RadarPlacerItem}) :
     * WorldGuard ne voit aucune pose de bloc. Radar mobile = trépied (bloc radar_fixe), radar fixe = borne de 2 blocs (bloc radar_borne).
     */
    public static final RegistryObject<Item> RADAR_MOBILE = ITEMS.register("radar_mobile",
            () -> new RadarPlacerItem(ModBlocks.RADAR_FIXE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RADAR_FIXE_POLICE = ITEMS.register("radar_fixe_police",
            () -> new RadarPlacerItem(ModBlocks.RADAR_BORNE, new Item.Properties().stacksTo(16)));
    /** Objet illégal : désactive un radar posé pendant la durée de la config et alerte la police (tablette, carte, chat). */
    public static final RegistryObject<Item> RADAR_HACK = ITEMS.register("pirate_radar", RadarHackItem::new);
    /** Mallette d'avocat : contestation d'amendes / condamnations (réservée aux membres de l'entreprise d'avocats de la config). */
    public static final RegistryObject<Item> MALLETTE_AVOCAT = ITEMS.register("mallette_avocat", LawyerCaseItem::new);
    /** Poste de douane (borne de contrôle), posé par les admins. */
    public static final RegistryObject<Item> POSTE_DOUANE = ITEMS.register("poste_douane",
            () -> new RadarPlacerItem(ModBlocks.DOUANE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> HERSE = ITEMS.register("herse_police", () -> new HerseItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TEST_SALIVAIRE = ITEMS.register("test_salivaire", SalivaTestItem::new);
    public static final RegistryObject<Item> DETECTEUR_METAUX = ITEMS.register("detecteur_metaux", MetalDetectorItem::new);
    public static final RegistryObject<Item> FOURRIERE = ITEMS.register("ordre_fourriere", FourriereItem::new);
    public static final RegistryObject<Item> TASER = ITEMS.register("taser", TaserItem::new);
    public static final RegistryObject<Item> CARTOUCHE_TASER = ITEMS.register("cartouche_taser", () -> new Item(new Item.Properties().stacksTo(64)));

    /** Onglet « Police MineNorth » du menu créatif. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthPolice.MOD_ID);
    public static final net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator TAB_ITEMS = (params, out) -> {
                out.accept(TABLET.get());
                out.accept(RADAR_MAIN.get());
                out.accept(RADAR_MOBILE.get());
                out.accept(RADAR_FIXE_POLICE.get());
                out.accept(RADAR_HACK.get());
                out.accept(MALLETTE_AVOCAT.get());
                out.accept(POSTE_DOUANE.get());
                out.accept(HERSE.get());
                out.accept(FOURRIERE.get());
                out.accept(TASER.get());
                out.accept(CARTOUCHE_TASER.get());
                out.accept(TEST_SALIVAIRE.get());
                out.accept(DETECTEUR_METAUX.get());
            };

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
