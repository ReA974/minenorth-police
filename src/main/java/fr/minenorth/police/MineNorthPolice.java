package fr.minenorth.police;

import fr.minenorth.police.block.ModBlocks;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MineNorthPolice.MOD_ID)
public class MineNorthPolice {
    public static final String MOD_ID = "minenorthpolice";

    public MineNorthPolice() {
        PoliceConfig.load();
        ModNetwork.register();
        fr.minenorth.api.MineNorth.provide(fr.minenorth.api.PoliceService.class, new fr.minenorth.police.api.PoliceProvider());
        ModBlocks.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModItems.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
