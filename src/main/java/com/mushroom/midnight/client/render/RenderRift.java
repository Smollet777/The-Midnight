package com.mushroom.midnight.client.render;

import com.mushroom.midnight.Midnight;
import com.mushroom.midnight.client.shader.ShaderHandle;
import com.mushroom.midnight.client.shader.WorldShader;
import com.mushroom.midnight.common.config.MidnightConfig;
import com.mushroom.midnight.common.entity.EntityRift;
import com.mushroom.midnight.common.entity.RiftBridge;
import com.mushroom.midnight.common.entity.RiftGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.vecmath.Point2f;

public class RenderRift extends Render<EntityRift> {
    private static final double QUAD_WIDTH = 1.8;
    private static final double QUAD_HEIGHT = 2.6;

    private static final ResourceLocation RIFT_NOISE = new ResourceLocation(Midnight.MODID, "textures/effects/rift_noise.png");
    private static final ResourceLocation RIFT_TEXTURE = new ResourceLocation(Midnight.MODID, "textures/entities/rift.png");

    private final WorldShader shader = new WorldShader(new ResourceLocation(Midnight.MODID, "rift"))
            .withTextureSampler("NoiseSampler", RIFT_NOISE);

    public RenderRift(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityRift entity, double x, double y, double z, float entityYaw, float partialTicks) {
        RiftBridge bridge = entity.getBridge();
        if (bridge == null) {
            return;
        }

        float openProgress = bridge.getOpenAnimation(partialTicks);
        float unstableTime = bridge.getUnstableAnimation(partialTicks);

        if (openProgress > 0.0F) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);

            GlStateManager.pushMatrix();
            GlStateManager.disableCull();
            GlStateManager.disableLighting();

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            GlStateManager.translate(x, y + entity.height / 2.0F, z);
            GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);

            float time = entity.ticksExisted + partialTicks;

            float openAnimation = this.computeOpenAnimation(openProgress);
            float unstableAnimation = this.computeUnstableAnimation(unstableTime);

            if (this.shader.isAvailable() && MidnightConfig.client.riftShaders) {
                this.renderShader(entity, time, openAnimation, unstableAnimation);
            } else {
                this.renderTexture(entity, time, openAnimation, unstableAnimation);
            }

            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.popMatrix();

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY);
        }
    }

    private void renderShader(EntityRift entity, float time, float openAnimation, float unstableAnimation) {
        RiftGeometry geometry = entity.getGeometry();
        Point2f[] ring = geometry.computePath(openAnimation, unstableAnimation, time);

        GlStateManager.enableBlend();

        try (ShaderHandle ignored = this.shader.use(shader -> {
            for (int i = 0; i < ring.length; i++) {
                Point2f point = ring[i];
                shader.get("vertices[" + i + "]").set(point.x, point.y);
            }
            shader.get("Time").set(time);
        })) {
            GlStateManager.disableCull();

            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();

            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            builder.pos(-QUAD_WIDTH, QUAD_HEIGHT, 0.0).endVertex();
            builder.pos(QUAD_WIDTH, QUAD_HEIGHT, 0.0).endVertex();
            builder.pos(QUAD_WIDTH, -QUAD_HEIGHT, 0.0).endVertex();
            builder.pos(-QUAD_WIDTH, -QUAD_HEIGHT, 0.0).endVertex();
            tessellator.draw();

            GlStateManager.enableCull();
        }

        GlStateManager.disableBlend();
    }

    private void renderTexture(EntityRift entity, float time, float openAnimation, float unstableAnimation) {
        Minecraft client = Minecraft.getMinecraft();

        client.getTextureManager().bindTexture(RIFT_TEXTURE);

        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();

        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();

        double width = (64.0 * 0.0625) / 2.0;
        double height = (128.0 * 0.0625) / 2.0;

        float idleSpeed = 0.08F;
        float idleIntensity = 0.1F * openAnimation * (unstableAnimation * 5.0F + 1.0F);
        float idleAnimation = (MathHelper.sin(time * idleSpeed) + 1.0F) * 0.4F * idleIntensity;

        GlStateManager.scale(openAnimation, openAnimation, 1.0F);
        GlStateManager.scale(1.0F + idleAnimation, 1.0F + (idleAnimation * 0.5F), 1.0F);

        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        builder.pos(-width, height, 0.0).tex(0.0, 1.0).endVertex();
        builder.pos(width, height, 0.0).tex(1.0, 1.0).endVertex();
        builder.pos(width, -height, 0.0).tex(1.0, 0.0).endVertex();
        builder.pos(-width, -height, 0.0).tex(0.0, 0.0).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
    }

    private float computeOpenAnimation(float openProgress) {
        float openAnimation = openProgress / EntityRift.OPEN_TIME;
        openAnimation = (float) (1.0F - Math.pow(1.0 - openAnimation, 3.0));
        openAnimation = MathHelper.clamp(openAnimation, 0.0F, 1.0F);
        return openAnimation;
    }

    private float computeUnstableAnimation(float unstableTime) {
        float unstableAnimation = unstableTime / EntityRift.UNSTABLE_TIME;
        unstableAnimation = (float) (1.0F - Math.pow(1.0 - unstableAnimation, 2.0));
        unstableAnimation = MathHelper.clamp(unstableAnimation, 0.0F, 1.0F);
        return unstableAnimation;
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityRift entity) {
        return null;
    }
}
