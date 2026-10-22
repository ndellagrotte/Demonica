package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.api.debug.RenderDebugHooksHolder;
import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.terrain.ShaderTerrain;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.util.Locale;

/**
 * S8 (docs/celeritas/patches/S8.md): the terrain layers of a shader-pack frame. Upstream's RenderGlobalMixin
 * {@code @Overwrite}s {@code renderBlockLayer(BlockRenderLayer, double, int, Entity)} to draw through Celeritas; this
 * wraps that draw with Iris's terrain phases and translucent prelude, runs vanilla's one-argument overload for the
 * translucent layer (third-party injections, Distant Horizons' deferred LOD pass among them, are anchored inside it),
 * and moves the camera of the draw from the feet to the render origin of gbufferModelView and cameraPosition while a
 * pack is active (the eye in first person, the real camera in third person; see S6m). With the diagnostics jar and
 * the GL debug option on, the GL state is compared across the draw and across that overload
 * ({@link RenderDebugHooksHolder}); the perf-debug report times the draw.
 *
 * <p>The phases are entered first at the {@code renderWorldPass} call site (EntityRendererIrisMixin's redirect of
 * this method), around the call: a mod's own HEAD injection into this method (Moving Elevators draws its moving cage
 * there) runs before this mixin's, whatever the priorities, since Mixin inserts HEAD callbacks before the original
 * first instruction in priority order. The HEAD/RETURN pair here is the fallback for callers outside
 * {@code renderWorldPass} (GregTech CEu's bloom wrapper calls this method itself) and a no-op inside the call site's
 * phase ({@link ShaderTerrain#beginLayer} returns false; {@link ShaderTerrain#endLayer} then does nothing).
 *
 * <p>The injections inside the overwritten body need a priority above upstream's 1000; HEAD and RETURN do not.
 */
@Patch(value = "S8", group = PatchGroup.CORE_TERRAIN)
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class RenderGlobalTerrainMixin {
    /** Vanilla's one-argument overload, which upstream's overwrite no longer calls. */
    @Shadow
    private void renderBlockLayer(BlockRenderLayer blockLayerIn) {
        throw new AssertionError();
    }

    /** Whether the HEAD injection entered the phase (false inside the call site's). Render thread only. */
    @Unique
    private boolean demonica$enteredLayer;

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
    private void demonica$beginTerrainLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                            CallbackInfoReturnable<Integer> cir) {
        this.demonica$enteredLayer = ShaderTerrain.beginLayer(layer, (float) partialTicks);
    }

    // Its renderContainer draws nothing, since Celeritas owns chunk rendering and never fills it. Distant Horizons
    // anchors its deferred transparent LOD pass inside it, so the GL state is sampled across it (the diagnostics jar's
    // GL state probe, a no-op without it or while the GL debug option is off): a difference here belongs to DH, one
    // across the draw to Celeritas.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;enableLightmap()V"))
    private void demonica$runVanillaTranslucentLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                                     CallbackInfoReturnable<Integer> cir) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            Object before = RenderDebugHooksHolder.captureGlState();
            this.renderBlockLayer(layer);
            if (before != null) {
                RenderDebugHooksHolder.compareGlState("dh-injection-translucent-pass" + pass, before);
            }
        }
    }

    // One handler for all three camera arguments, so the origin S6m takes is handed over exactly once per draw.
    @ModifyArgs(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        at = @At(value = "INVOKE",
            target = "Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer;drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V",
            remap = false))
    private void demonica$originCamera(Args args) {
        Vector3fc origin = ShaderTerrain.terrainOrigin();
        if (origin != null) {
            args.set(1, args.<Double>get(1) + origin.x());
            args.set(2, args.<Double>get(2) + origin.y());
            args.set(3, args.<Double>get(3) + origin.z());
        }
    }

    // Debug only: the draw's GL state difference and its time in the perf-debug report.
    @WrapOperation(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        at = @At(value = "INVOKE",
            target = "Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer;drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V",
            remap = false))
    private void demonica$observeTerrainDraw(CeleritasWorldRenderer renderer, BlockRenderLayer layer, double x, double y,
                                             double z, Operation<Void> original, @Local(argsOnly = true) int pass) {
        long startNanos = RenderDebugHooksHolder.beginRenderGlobalStageTiming();
        Object before = RenderDebugHooksHolder.captureGlState();
        try {
            original.call(renderer, layer, x, y, z);
        } finally {
            // Nothing to name while neither the probe nor the timing is on, as without the diagnostics jar.
            if (before != null || startNanos != 0L) {
                String layerName = layer.name().toLowerCase(Locale.ROOT);
                RenderDebugHooksHolder.compareGlState("celeritas-draw-" + layerName + "-pass" + pass, before);
                RenderDebugHooksHolder.recordRenderGlobalStageTiming("terrain-" + layerName, pass, startNanos);
            }
        }
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void demonica$endTerrainLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                          CallbackInfoReturnable<Integer> cir) {
        boolean entered = this.demonica$enteredLayer;
        this.demonica$enteredLayer = false;
        ShaderTerrain.endLayer(entered);
    }
}
