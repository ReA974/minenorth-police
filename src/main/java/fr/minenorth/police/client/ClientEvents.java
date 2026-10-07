package fr.minenorth.police.client;

import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Taser côté client : clic gauche = tir (envoyé au serveur). La visée (clic droit maintenu) est gérée par TaserItem. */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.getMainHandItem().is(ModItems.TASER.get())) return;
        // Pas de coup de poing ni de bloc cassé : le clic gauche devient un tir.
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!p.getCooldowns().isOnCooldown(ModItems.TASER.get())) ModNetwork.CHANNEL.sendToServer(new ModNetwork.TaserFirePacket());
    }

}
