package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.terrain.ShaderTerrain;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S8 (docs/celeritas/patches/S8.md): the terrain layers of a shader-pack frame. Upstream's RenderGlobalMixin
 * {@code @Overwrite}s {@code renderBlockLayer(BlockRenderLayer, double, int, Entity)} to draw through Celeritas; this
 * wraps that draw with Iris's terrain phases and translucent prelude, runs vanilla's one-argument overload for the
 * translucent layer (third-party injections, Distant Horizons' deferred LOD pass among them, are anchored inside it),
 * and moves the camera of the draw to the eye while a pack is active (see S6m).
 *
 * <p>The injections inside the overwritten body need a priority above upstream's 1000; HEAD and RETURN do not.
 */
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class RenderGlobalTerrainMixin {
    /** Vanilla's one-argument overload, which upstream's overwrite no longer calls. */
    @Shadow
    private void renderBlockLayer(BlockRenderLayer blockLayerIn) {
        throw new AssertionError();
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
    private void demonica$beginTerrainLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                            CallbackInfoReturnable<Integer> cir) {
        ShaderTerrain.beginLayer(layer, (float) partialTicks);
    }

    // Its renderContainer draws nothing, since Celeritas owns chunk rendering and never fills it.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;enableLightmap()V"))
    private void demonica$runVanillaTranslucentLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                                     CallbackInfoReturnable<Integer> cir) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            this.renderBlockLayer(layer);
        }
    }

    @ModifyArg(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        at = @At(value = "INVOKE",
            target = "Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer;drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V",
            remap = false),
        index = 2)
    private double demonica$eyeCameraY(double y, @Local(argsOnly = true) Entity entity) {
        return ShaderTerrain.eyeCameraY(y, entity);
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void demonica$endTerrainLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                          CallbackInfoReturnable<Integer> cir) {
        ShaderTerrain.endLayer();
    }
}
