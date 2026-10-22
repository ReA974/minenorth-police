package fr.minenorth.police.cuff;

import fr.minenorth.police.MineNorthPolice;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineNorthPolice.MOD_ID);
    public static final RegistryObject<MenuType<SearchMenu>> FOUILLE = MENUS.register("fouille", () -> IForgeMenuType.create(SearchMenu::fromNetwork));

    public static void register(IEventBus bus) { MENUS.register(bus); }
}
