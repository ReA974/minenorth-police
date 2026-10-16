package fr.minenorth.police.client;

import fr.minenorth.police.HerseEntity;
import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.item.HandRadarItem;
import fr.minenorth.police.item.ModItems;
import fr.minenorth.police.item.TaserItem;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Objets de police côté client :
 * taser = clic gauche tire (visée au clic droit maintenu) ; radar à main = clic gauche mesure pendant la visée aux jumelles ;
 * herse = le clic droit est envoyé par un paquet dédié (le clic vanilla est annulé : WorldGuard ne voit aucune interaction).
 */
@Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (e.isAttack()) {
            if (p.getMainHandItem().is(ModItems.TASER.get())) {
                // Pas de coup de poing ni de bloc cassé : le clic gauche devient un tir.
                e.setCanceled(true);
                e.setSwingHand(false);
                if (!p.getCooldowns().isOnCooldown(ModItems.TASER.get())) ModNetwork.CHANNEL.sendToServer(new ModNetwork.TaserFirePacket());
            } else if (HandRadarItem.scoping(p)) {
                e.setCanceled(true);
                e.setSwingHand(false);
                if (!p.getCooldowns().isOnCooldown(ModItems.RADAR_MAIN.get())) ModNetwork.CHANNEL.sendToServer(new ModNetwork.RadarMeasurePacket());
            }
        } else if (e.isUseItem() && e.getHand() == InteractionHand.MAIN_HAND
                && (p.getMainHandItem().is(ModItems.HERSE.get()) || p.getMainHandItem().getItem() instanceof fr.minenorth.police.item.RadarPlacerItem)) {
            e.setCanceled(true);
            e.setSwingHand(true);
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                var v = b.getLocation();
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.HerseUsePacket(b.getBlockPos(), b.getDirection().ordinal(), v.x, v.y, v.z));
            } else if (hit instanceof EntityHitResult en && en.getEntity() instanceof HerseEntity h) {
                var v = h.position();
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.HerseUsePacket(h.blockPosition(), 1, v.x, v.y, v.z));
            }
        }
    }

    /** Viser au taser ne ralentit pas : on annule le ralentissement vanilla de l'utilisation d'un objet (0,2 x 5 = 1). */
    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (e.getEntity() instanceof LocalPlayer p && TaserItem.aiming(p) && !p.isPassenger()) {
            e.getInput().leftImpulse *= 5f;
            e.getInput().forwardImpulse *= 5f;
        }
    }
}
