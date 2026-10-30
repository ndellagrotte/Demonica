package com.demonica.mixin.mod.hbm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads and writes HBM's {@code GeneralConfig.instancedParticles} switch. */
@Mixin(targets = "com.hbm.config.GeneralConfig", remap = false)
public interface GeneralConfigAccessor {
    @Accessor(value = "instancedParticles", remap = false)
    static boolean demonica$getInstancedParticles() {
        throw new UnsupportedOperationException("replaced by Mixin");
    }

    @Accessor(value = "instancedParticles", remap = false)
    static void demonica$setInstancedParticles(boolean value) {
        throw new UnsupportedOperationException("replaced by Mixin");
    }
}
