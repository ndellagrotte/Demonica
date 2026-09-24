package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.fog.GLStateManagerFogService;

/**
 * S15 (docs/celeritas/patches/S15.md): Celeritas's fog service reads vanilla's {@code GlStateManager.fogState}, which
 * GLSM leaves stale once the redirector sends vanilla's fog calls to GLSM; every terrain section would then be drawn
 * in solid fog colour. Each accessor returns GLSM's fog state instead. The fog shape is planar, matching vanilla
 * 1.12's eye-plane fog.
 */
@Patch(value = "S15", group = PatchGroup.BASE)
@Mixin(value = GLStateManagerFogService.class, remap = false, priority = 1100)
public abstract class GLStateManagerFogServiceMixin {
    @Unique
    private final float[] demonica$fogColor = new float[4];

    @Inject(method = "getFogEnd", at = @At("HEAD"), cancellable = true)
    private void demonica$fogEnd(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GLStateManager.getFogState().getEnd());
    }

    @Inject(method = "getFogStart", at = @At("HEAD"), cancellable = true)
    private void demonica$fogStart(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GLStateManager.getFogState().getStart());
    }

    @Inject(method = "getFogDensity", at = @At("HEAD"), cancellable = true)
    private void demonica$fogDensity(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GLStateManager.getFogState().getDensity());
    }

    @Inject(method = "getFogShapeIndex", at = @At("HEAD"), cancellable = true)
    private void demonica$fogShape(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FogService.FOG_SHAPE_PLANAR);
    }

    @Inject(method = "getFogCutoff", at = @At("HEAD"), cancellable = true)
    private void demonica$fogCutoff(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GLStateManager.getFogState().getEnd());
    }

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void demonica$fogColor(CallbackInfoReturnable<float[]> cir) {
        FogState state = GLStateManager.getFogState();
        Vector3d color = state.getFogColor();
        this.demonica$fogColor[0] = (float) color.x;
        this.demonica$fogColor[1] = (float) color.y;
        this.demonica$fogColor[2] = (float) color.z;
        this.demonica$fogColor[3] = state.getFogAlpha();
        cir.setReturnValue(this.demonica$fogColor);
    }

    @Inject(method = "getFogMode", at = @At("HEAD"), cancellable = true)
    private void demonica$fogMode(CallbackInfoReturnable<ChunkShaderComponent.Factory<?>> cir) {
        if (!GLStateManager.getFogMode().isEnabled()) {
            cir.setReturnValue(ChunkFogMode.NONE);
        } else {
            cir.setReturnValue(ChunkFogMode.fromGLMode(GLStateManager.getFogState().getFogMode()));
        }
    }
}
