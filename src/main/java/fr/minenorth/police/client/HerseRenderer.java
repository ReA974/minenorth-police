package fr.minenorth.police.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.minenorth.police.HerseEntity;
import fr.minenorth.police.MineNorthPolice;
import fr.minenorth.police.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dessine l'entité herse avec le modèle du bloc herse (strip de pointes, 16 px de long, orientée par la rotation de l'entité). */
public class HerseRenderer extends EntityRenderer<HerseEntity> {
    public HerseRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(HerseEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        BlockState state = ModBlocks.HERSE.get().defaultBlockState();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        ps.translate(-0.5, 0, -0.5);
        // le modèle fait 1 bloc de large ; on l'étire à 2 blocs (hitbox) dans le sens de la bande
        ps.translate(0.5, 0, 0.5);
        ps.scale(2.0f, 1.0f, 1.0f);
        ps.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HerseEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegister(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(fr.minenorth.police.ModEntities.HERSE.get(), HerseRenderer::new);
        }
    }
}
