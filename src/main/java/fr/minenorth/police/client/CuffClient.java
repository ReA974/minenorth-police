package fr.minenorth.police.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.cuff.ModMenus;
import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Menottes et sac de jute côté client : état reçu du serveur (qui est menotté / encagoulé), pose des bras joints dans le dos
 * (appliquée par le crochet du mod Emotes, SANS jamais remplacer le modèle du joueur), blocage des clics, toile de jute plein écran.
 * Tout ce qui compte (droits, distances, interdits) est décidé et revérifié par le serveur.
 */
public final class CuffClient {
    private CuffClient() {}

    private static final Map<UUID, Integer> FLAGS = new HashMap<>();
    /** Pose : angle d'écartement vers l'arrière et vers le centre des bras (mêmes valeurs que tools/gen_menottes.py). */
    private static final float BACK = 0.65f, INWARD = 0.70f;
    public static final ResourceLocation JUTE = new ResourceLocation(MineNorthPolice.MOD_ID, "textures/gui/sac_vue.png");
    public static final KeyMapping KEY = new KeyMapping("key.minenorthpolice.menotte_vehicule", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.minenorthpolice");

    public static void sync(Map<UUID, Integer> m) {
        FLAGS.clear();
        FLAGS.putAll(m);
    }

    public static int flags(UUID id) { return FLAGS.getOrDefault(id, 0); }
    public static boolean cuffed(UUID id) { return (flags(id) & 1) != 0; }
    public static boolean hooded(UUID id) { return (flags(id) & 2) != 0; }
    public static boolean artisanal(UUID id) { return (flags(id) & 4) != 0; }

    private static boolean localCuffed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && cuffed(mc.player.getUUID());
    }

    // ------------------------------------------------------------------ pose (crochet du mod Emotes, par réflexion)
    private static boolean hooked;

    @SuppressWarnings("unchecked")
    private static void hookPose() {
        if (hooked) return;
        try {
            Class<?> c = Class.forName("fr.minenorth.emotes.client.EmoteModel");
            var list = (java.util.List<java.util.function.Consumer<Object[]>>) c.getField("POST").get(null);
            list.add(CuffClient::pose);
            hooked = true;
        } catch (Throwable t) {
            hooked = false;      // pas de mod Emotes : pas de pose (les menottes restent visibles), le modèle du joueur n'est jamais remplacé
        }
    }

    private static void pose(Object[] args) {
        if (!(args[0] instanceof PlayerModel<?> m) || !(args[1] instanceof AbstractClientPlayer p) || !cuffed(p.getUUID())) return;
        // Bras joints derrière le dos : balancés vers l'arrière puis ramenés vers le centre (rotation Z-Y-X de vanilla : X d'abord).
        m.rightArm.xRot = BACK; m.rightArm.yRot = INWARD; m.rightArm.zRot = 0f;
        m.leftArm.xRot = BACK; m.leftArm.yRot = -INWARD; m.leftArm.zRot = 0f;
        m.head.xRot = Math.max(m.head.xRot, 0.12f);      // la tête un peu baissée
    }

    // ------------------------------------------------------------------ enregistrements (bus du mod)
    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                MenuScreens.register(ModMenus.FOUILLE.get(), SearchScreen::new);
                hookPose();
                fr.minenorth.police.cuff.CuffService.clientCuffed = CuffClient::cuffed;
            });
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    // ------------------------------------------------------------------ évènements (bus Forge)
    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        private static boolean muffled;

        /** Un menotté ne clique plus : ni coup, ni utilisation, ni pose (le serveur l'interdit aussi). */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void click(InputEvent.InteractionKeyMappingTriggered e) {
            if (!localCuffed()) return;
            e.setCanceled(true);
            e.setSwingHand(false);
        }

        /** Pas de saut. La vitesse réduite et l'interdiction de courir viennent du serveur (attribut) et du tick ci-dessous. */
        @SubscribeEvent
        public static void move(MovementInputUpdateEvent e) {
            if (e.getEntity() == Minecraft.getInstance().player && localCuffed()) e.getInput().jumping = false;
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (cuffed(mc.player.getUUID())) mc.player.setSprinting(false);
            while (KEY.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.CuffKeyPacket());
            boolean hood = hooded(mc.player.getUUID());
            if (hood != muffled) {
                muffled = hood;
                setMuffle(mc, hood);
            }
        }

        /** Sons étouffés sous le sac : on baisse le volume général de l'écoute (sans toucher au réglage du joueur). */
        private static void setMuffle(Minecraft mc, boolean on) {
            try {
                float base = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, on ? base * 0.25f : base);
            } catch (Throwable ignored) {
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            FLAGS.clear();
            if (muffled) { muffled = false; setMuffle(Minecraft.getInstance(), false); }
        }

        /** Toile de jute plein écran, presque opaque : avec le sac sur la tête, on n'y voit presque rien. */
        @SubscribeEvent
        public static void overlay(RenderGuiEvent.Pre e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !hooded(mc.player.getUUID())) return;
            GuiGraphics g = e.getGuiGraphics();
            RenderSystem.enableBlend();
            g.setColor(0.42f, 0.35f, 0.24f, 0.97f);
            g.blit(JUTE, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }
}
