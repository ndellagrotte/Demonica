package com.demonica.mixin.mod.hbm;

import com.demonica.compat.hbm.HbmInstancedParticlesCompat;
import net.minecraftforge.common.config.Configuration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures HBM's configured {@code instancedParticles} value (see {@link HbmInstancedParticlesCompat}). */
@Mixin(targets = "com.hbm.config.GeneralConfig", remap = false)
public abstract class MixinGeneralConfig {
    @Inject(
        method = "loadFromConfig(Lnet/minecraftforge/common/config/Configuration;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void demonica$captureInstancedParticles(Configuration config, CallbackInfo ci) {
        HbmInstancedParticlesCompat.onConfigLoaded(GeneralConfigAccessor.demonica$getInstancedParticles());
    }
}
