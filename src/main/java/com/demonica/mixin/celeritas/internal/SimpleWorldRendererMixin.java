package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.terrain.DemonicaFrameClock;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * I2 (docs/celeritas/patches/I2.md): both terrain searches take their frame stamp from {@link DemonicaFrameClock}.
 * The player search is stamped with vanilla's frame counter and Iris's shadow search with its own, which restarts at
 * 0 with every pipeline; a stamp that goes backwards makes the search skip every section it visited before.
 */
@Mixin(value = SimpleWorldRenderer.class, remap = false, priority = 1100)
public abstract class SimpleWorldRendererMixin {
    @ModifyVariable(
        method = {
            "setupTerrain(Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;"
                + "Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;IZZ)V",
            "setupShadowTerrain(Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;"
                + "Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;IZ)V"
        },
        at = @At("HEAD"),
        argsOnly = true
    )
    private int demonica$nextFrameStamp(int frame) {
        return DemonicaFrameClock.next();
    }
}
