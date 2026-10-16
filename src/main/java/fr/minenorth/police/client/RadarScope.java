package fr.minenorth.police.client;

import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.item.HandRadarItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vue « jumelles » du radar à main : zoom, cercle noir façon longue-vue, viseur et rappel de la limite. */
public final class RadarScope {
    private RadarScope() {}

    private static final ResourceLocation SCOPE = new ResourceLocation(MineNorthPolice.MOD_ID, "textures/gui/radar_scope.png");
    private static final float ZOOM = 0.16f;

    static boolean active() {
        var p = Minecraft.getInstance().player;
        return p != null && Minecraft.getInstance().options.getCameraType().isFirstPerson() && HandRadarItem.scoping(p);
    }

    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("radar_scope", (gui, g, pt, w, h) -> { if (active()) draw(g, w, h); });
        }
    }

    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void fov(ComputeFovModifierEvent e) {
            if (HandRadarItem.scoping(e.getPlayer()) && active()) e.setNewFovModifier(e.getNewFovModifier() * ZOOM);
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent e) {
            if (e.getHand() == InteractionHand.MAIN_HAND && active()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void crosshair(RenderGuiOverlayEvent.Pre e) {
            if (e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && active()) e.setCanceled(true);
        }
    }

    private static void draw(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int size = Math.min(w, h);
        int l = (w - size) / 2, t = (h - size) / 2, r = l + size, b = t + size;
        g.blit(SCOPE, l, t, -90, 0f, 0f, size, size, size, size);
        g.fill(RenderType.guiOverlay(), 0, b, w, h, -90, 0xFF000000);
        g.fill(RenderType.guiOverlay(), 0, 0, w, t, -90, 0xFF000000);
        g.fill(RenderType.guiOverlay(), 0, t, l, b, -90, 0xFF000000);
        g.fill(RenderType.guiOverlay(), r, t, w, b, -90, 0xFF000000);
        int cx = w / 2, cy = h / 2, col = 0xCC20E0FF;
        g.fill(cx - 24, cy, cx - 4, cy + 1, col);
        g.fill(cx + 4, cy, cx + 24, cy + 1, col);
        g.fill(cx, cy - 24, cx + 1, cy - 4, col);
        g.fill(cx, cy + 4, cx + 1, cy + 24, col);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF4040);
        int limit = HandRadarItem.limit(mc.player.getMainHandItem());
        g.drawCenteredString(mc.font, "RADAR  ·  limite " + limit + " km/h  ·  clic gauche : mesurer", cx, b - 28, 0xFF20E0FF);
    }
}
