package com.demonica.mixin.features.iris;

import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.SkyRenderDistance;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class RenderGlobalIrisMixin {
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void demonica$beginSky(float partialTicks, int pass, CallbackInfo ci) {
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void demonica$endSky(float partialTicks, int pass, CallbackInfo ci) {
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderClouds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;shouldRenderClouds()I")
    )
    private int demonica$forceCloudsBelowMinimumDistance(GameSettings settings) {
        return settings.renderDistanceChunks < SkyRenderDistance.MINIMUM_RENDER_DISTANCE_CHUNKS
            && settings.clouds == 0 ? 2 : settings.clouds;
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V",
            ordinal = 0
        )
    )
    private void demonica$beginVanillaSky(float partialTicks, int pass, CallbackInfo ci) {
        demonica$setSkyPhase(WorldRenderingPhase.SKY);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F")
    )
    private void demonica$beginSunset(float partialTicks, int pass, CallbackInfo ci) {
        demonica$setSkyPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;enableTexture2D()V",
            ordinal = 0
        )
    )
    private void demonica$endSunset(float partialTicks, int pass, CallbackInfo ci) {
        demonica$setSkyPhase(WorldRenderingPhase.SKY);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F",
            ordinal = 1
        )
    )
    private void demonica$applySunPathRotation(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        if (pipeline != null) {
            GlStateManager.rotate(pipeline.getSunPathRotation(), 0.0F, 0.0F, 1.0F);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 0
        )
    )
    private void demonica$bindSun(TextureManager textureManager, ResourceLocation location) {
        demonica$setSkyPhase(WorldRenderingPhase.SUN);
        textureManager.bindTexture(location);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 1
        )
    )
    private void demonica$bindMoon(TextureManager textureManager, ResourceLocation location) {
        demonica$setSkyPhase(WorldRenderingPhase.MOON);
        textureManager.bindTexture(location);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1)
    )
    private void demonica$drawSun(Tessellator tessellator) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSun();
        if (directive) {
            demonica$refreshCurrentProgramCompatUniforms(pipeline);
            tessellator.draw();
        } else {
            demonica$discard(tessellator.getBuffer());
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 2)
    )
    private void demonica$drawMoon(Tessellator tessellator) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderMoon();
        if (directive) {
            demonica$refreshCurrentProgramCompatUniforms(pipeline);
            tessellator.draw();
        } else {
            demonica$discard(tessellator.getBuffer());
        }
        demonica$setSkyPhase(WorldRenderingPhase.STARS);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V", ordinal = 0)
    )
    private void demonica$drawSkyDiscVbo(VertexBuffer vertexBuffer, int mode) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSkyDisc();
        if (directive) {
            vertexBuffer.drawArrays(mode);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V", ordinal = 0)
    )
    private void demonica$drawSkyDiscList(int list) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSkyDisc();
        if (directive) {
            GlStateManager.callList(list);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V", ordinal = 1)
    )
    private void demonica$drawStarsVbo(VertexBuffer vertexBuffer, int mode) {
        demonica$setSkyPhase(WorldRenderingPhase.STARS);
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderStars();
        if (directive) {
            vertexBuffer.drawArrays(mode);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V", ordinal = 1)
    )
    private void demonica$drawStarsList(int list) {
        demonica$setSkyPhase(WorldRenderingPhase.STARS);
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderStars();
        if (directive) {
            GlStateManager.callList(list);
        }
    }

    @Inject(method = "drawSelectionBox", at = @At("HEAD"))
    private void demonica$beginOutline(EntityPlayer player, RayTraceResult movingObjectPositionIn, int execute, float partialTicks, CallbackInfo ci) {
        if (!Iris.enabled) {
            return;
        }

        if (IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            GbufferPrograms.beginOutline();
        }
    }

    @Inject(method = "drawSelectionBox", at = @At("RETURN"))
    private void demonica$endOutline(EntityPlayer player, RayTraceResult movingObjectPositionIn, int execute, float partialTicks, CallbackInfo ci) {
        if (!Iris.enabled) {
            return;
        }

        if (IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            GbufferPrograms.endOutline();
        }
    }

    private static WorldRenderingPipeline demonica$getPipeline() {
        return Iris.enabled ? Iris.getPipelineManager().getPipelineNullable() : null;
    }

    private static void demonica$setSkyPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = demonica$getPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    private static void demonica$refreshCurrentProgramCompatUniforms(WorldRenderingPipeline pipeline) {
        if (pipeline instanceof DeferredWorldRenderingPipeline deferredPipeline) {
            int program = deferredPipeline.getActivePassProgramId();
            if (program > 0) {
                CompatUniformManager.onUseProgram(program);
            }
        }
    }

    private static void demonica$discard(BufferBuilder bufferBuilder) {
        try {
            bufferBuilder.finishDrawing();
        } catch (IllegalStateException ignored) {
        }
        bufferBuilder.reset();
    }
}
