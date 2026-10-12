package com.demonica.mixin.mod.distanthorizons;

import com.seibel.distanthorizons.cleanroom.CleanroomMain;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Distant Horizons binds its Iris integration ({@code IIrisAccessor}: the shader-pack LOD passes and the deferred
 * translucent pass) through {@code CleanroomMain.initializeModCompat}, one {@code tryCreateModCompatAccessor} call per
 * mod id it accepts as the Iris provider. Demonica provides the same Iris API under its own id, so the accessor is bound
 * for {@code demonica}, and exactly once:
 * <ul>
 *   <li>DH 3.3.0 names only {@code actinium}: that call is made as {@code demonica} instead.</li>
 *   <li>A DH that names {@code actinium} and then {@code demonica}: the first call binds as {@code demonica}, the
 *       second is skipped. DH's injector throws {@code IllegalStateException} on a second bind of the same interface,
 *       so letting both through would stop the game.</li>
 *   <li>A DH that names only {@code demonica}: the call passes through.</li>
 * </ul>
 * The mixin extends {@code AbstractModInitializer}, the target's superclass, to reach the protected
 * {@code tryCreateModCompatAccessor} it inherits: Mixin resolves a {@code @Shadow} only in the target class itself.
 * Draft of the upstream request: docs/compat/distant-horizons.md.
 */
@Mixin(targets = "com.seibel.distanthorizons.cleanroom.CleanroomMain", remap = false)
public abstract class MixinCleanroomMain extends AbstractModInitializer {
    @Unique
    private static boolean demonica$irisAccessorBound;

    // Optional: a DH release that registers its accessors elsewhere must not stop the game.
    @Redirect(
        method = "initializeModCompat",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/cleanroom/CleanroomMain;tryCreateModCompatAccessor(Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Supplier;)V"
        ),
        require = 0
    )
    private <T extends IModAccessor> void demonica$bindIrisAccessorOnce(
        CleanroomMain self, String modId, Class<? super T> iface, Supplier<T> supplier
    ) {
        if (iface != IIrisAccessor.class) {
            tryCreateModCompatAccessor(modId, iface, supplier);
            return;
        }
        if (demonica$irisAccessorBound) {
            return;
        }
        demonica$irisAccessorBound = true;
        tryCreateModCompatAccessor("demonica", iface, supplier);
    }
}
