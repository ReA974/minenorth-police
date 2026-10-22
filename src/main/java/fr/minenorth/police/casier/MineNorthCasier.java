package fr.minenorth.police.casier;

import com.mojang.logging.LogUtils;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** Casier de service, fusionné dans le mod Police : registres sous l'espace de noms « minenorth_casier » (noms conservés), branchés sur le bus de Police. */
public class MineNorthCasier {
    public static final String MOD_ID = "minenorth_casier";
    public static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final RegistryObject<MenuType<ServiceMenu>> STUFF_MENU = MENUS.register("stuff",
            () -> IForgeMenuType.create((id, inv, buf) -> ServiceMenu.fromBuffer(MineNorthCasier.STUFF_MENU.get(), id, inv, buf)));
    public static final RegistryObject<MenuType<ServiceMenu>> CONSIGNE_MENU = MENUS.register("consigne",
            () -> IForgeMenuType.create((id, inv, buf) -> ServiceMenu.fromBuffer(MineNorthCasier.CONSIGNE_MENU.get(), id, inv, buf)));

    public static void init(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CasierConfig.SPEC, "minenorth_casier-common.toml");
        ModBlocks.register(bus);
        ModItems.register(bus);
        MENUS.register(bus);
        bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(CasierNet::register));
        MinecraftForge.EVENT_BUS.register(new CasierEvents());
    }
}
