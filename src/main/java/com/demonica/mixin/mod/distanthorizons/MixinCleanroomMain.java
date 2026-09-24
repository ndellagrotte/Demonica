package com.demonica.mixin.mod.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Distant Horizons binds its Iris integration ({@code IIrisAccessor}: the shader-pack LOD passes and the deferred
 * translucent pass) only when a mod with id {@code actinium} is loaded. Demonica provides the same Iris API under its
 * own id, so DH's check asks for {@code demonica} instead. Draft of the upstream request: docs/compat/distant-horizons.md.
 */
@Mixin(targets = "com.seibel.distanthorizons.cleanroom.CleanroomMain", remap = false)
public abstract class MixinCleanroomMain {
    // Optional: a DH release that no longer names "actinium" there must not stop the game.
    @ModifyConstant(method = "initializeModCompat", constant = @Constant(stringValue = "actinium"), require = 0)
    private String demonica$irisProviderModId(String actinium) {
        return "demonica";
    }
}
