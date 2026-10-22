package fr.minenorth.police.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.minenorth.police.MineNorthPolice;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Couche ajoutée au rendu des joueurs (le modèle du joueur n'est JAMAIS remplacé) : menottes aux poignets avec leur chaîne dans le dos,
 * et sac de jute (cube ample, sans fente pour les yeux) sur la tête. Géométrie de tools/gen_menottes.py (aperçu logiciel vérifié).
 */
public class CuffLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation CUFFS = new ModelLayerLocation(new ResourceLocation(MineNorthPolice.MOD_ID, "menottes"), "main");
    public static final ModelLayerLocation HOOD = new ModelLayerLocation(new ResourceLocation(MineNorthPolice.MOD_ID, "sac_jute"), "main");
    private static final ResourceLocation TEX_POLICE = new ResourceLocation(MineNorthPolice.MOD_ID, "textures/entity/menottes_police.png");
    private static final ResourceLocation TEX_ARTISANAL = new ResourceLocation(MineNorthPolice.MOD_ID, "textures/entity/menottes_artisanales.png");
    private static final ResourceLocation TEX_HOOD = new ResourceLocation(MineNorthPolice.MOD_ID, "textures/entity/sac_jute.png");

    private final ModelPart ring, chain, hood;

    public CuffLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        ModelPart cuffs = models.bakeLayer(CUFFS);
        this.ring = cuffs.getChild("ring");
        this.chain = cuffs.getChild("chain");
        this.hood = models.bakeLayer(HOOD).getChild("hood");
    }

    /** Bracelet autour du poignet (repère du bras : y vers le bas, bras de 12 de long) + chaîne entre les deux poignets (repère du torse). */
    public static LayerDefinition cuffDefinition() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        root.addOrReplaceChild("ring", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.6f, 8.4f, -2.6f, 5.2f, 1.8f, 0.5f)
                .texOffs(0, 8).addBox(-2.6f, 8.4f, 2.1f, 5.2f, 1.8f, 0.5f)
                .texOffs(0, 16).addBox(-2.6f, 8.4f, -2.1f, 0.5f, 1.8f, 4.2f)
                .texOffs(12, 16).addBox(2.1f, 8.4f, -2.1f, 0.5f, 1.8f, 4.2f)
                .texOffs(0, 24).addBox(2.3f, 8.1f, -1.0f, 1.3f, 2.4f, 2.0f), PartPose.ZERO);
        root.addOrReplaceChild("chain", CubeListBuilder.create()
                .texOffs(16, 24).addBox(-1.7f, 8.9f, 3.9f, 3.4f, 1.0f, 1.0f)
                .texOffs(0, 24).addBox(-0.6f, 8.6f, 3.6f, 1.2f, 1.6f, 1.6f), PartPose.ZERO);
        return LayerDefinition.create(md, 32, 32);
    }

    /** Sac : cube de 10 (plus ample que la tête et son calque de chapeau), bande de corde au bas, petit fronçage au sommet. Aucune fente pour les yeux. */
    public static LayerDefinition hoodDefinition() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        root.addOrReplaceChild("hood", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5f, -9.5f, -5f, 10f, 10f, 10f)
                .texOffs(0, 32).addBox(-5.25f, -0.6f, -5.25f, 10.5f, 1.1f, 10.5f)
                .texOffs(40, 0).addBox(-2f, -10.8f, -2f, 4f, 1.3f, 4f), PartPose.ZERO);
        return LayerDefinition.create(md, 64, 64);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partial, float age, float yaw, float pitch) {
        int f = CuffClient.flags(p.getUUID());
        if (f == 0 || p.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        if ((f & 1) != 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull((f & 4) != 0 ? TEX_ARTISANAL : TEX_POLICE));
            for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
                ps.pushPose();
                arm.translateAndRotate(ps);
                ring.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.pushPose();
            m.body.translateAndRotate(ps);
            chain.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if ((f & 2) != 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_HOOD));
            ps.pushPose();
            m.head.translateAndRotate(ps);
            hood.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = MineNorthPolice.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(CUFFS, CuffLayer::cuffDefinition);
            e.registerLayerDefinition(HOOD, CuffLayer::hoodDefinition);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                EntityRenderer<?> r = e.getSkin(skin);
                if (r instanceof PlayerRenderer pr) pr.addLayer(new CuffLayer(pr, e.getEntityModels()));
            }
        }
    }
}
