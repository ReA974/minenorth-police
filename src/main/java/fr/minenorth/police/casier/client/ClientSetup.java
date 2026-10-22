package fr.minenorth.police.casier.client;

import fr.minenorth.police.casier.Marks;
import fr.minenorth.police.casier.MineNorthCasier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = "minenorthpolice", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                MenuScreens.register(MineNorthCasier.STUFF_MENU.get(), ServiceScreen::new);
                MenuScreens.register(MineNorthCasier.CONSIGNE_MENU.get(), ServiceScreen::new);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = "minenorthpolice", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent e) {
            if (Marks.is(e.getItemStack())) {
                e.getToolTip().add(Component.translatable("casier.tooltip.service").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
