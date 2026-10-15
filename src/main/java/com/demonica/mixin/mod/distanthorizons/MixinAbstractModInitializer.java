package com.demonica.mixin.mod.distanthorizons;

import com.demonica.runtime.DemonicaRuntime;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Distant Horizons binds its Iris integration ({@code IIrisAccessor}: the shader-pack LOD passes and the deferred
 * translucent pass) from its mod class's {@code initializeModCompat}, one {@code tryCreateModCompatAccessor} call per
 * mod id it accepts as the Iris provider. Demonica provides the same Iris API under its own id, so the accessor is bound
 * for {@code demonica}, and exactly once:
 * <ul>
 *   <li>DH 3.3.0 to 3.3.3 name only {@code actinium}: that call is made as {@code demonica} instead.</li>
 *   <li>A DH that names {@code actinium} and then {@code demonica}: the first call binds as {@code demonica}, the
 *       second is skipped. DH's injector throws {@code IllegalStateException} on a second bind of the same interface,
 *       so letting both through would stop the game.</li>
 *   <li>A DH that names only {@code demonica}, or passes the ids as an array: bound as {@code demonica}.</li>
 * </ul>
 * The hook sits in {@code AbstractModInitializer}, DH's shared superclass, rather than in the mod class: the mod class
 * moved from {@code cleanroom.CleanroomMain} (3.3.0) to {@code forge112.ForgeMain} (3.3.3), and a mixin on the old name
 * stopped applying without a word. Draft of the upstream request: docs/compat/distant-horizons.md.
 */
@Mixin(targets = "com.seibel.distanthorizons.common.AbstractModInitializer", remap = false)
public abstract class MixinAbstractModInitializer {
    @Unique
    private static final Logger demonica$LOGGER = LogManager.getLogger("Demonica");

    @Unique
    private static boolean demonica$irisAccessorBound;

    @Shadow
    protected abstract <T extends IModAccessor> void tryCreateModCompatAccessor(
        String modId, Class<? super T> accessorClass, Supplier<T> accessorConstructor
    );

    @Inject(
        method = "tryCreateModCompatAccessor(Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Supplier;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void demonica$bindIrisAccessorOnce(
        String modId, Class<?> accessorClass, Supplier<?> accessorConstructor, CallbackInfo ci
    ) {
        if (accessorClass != IIrisAccessor.class) {
            return;
        }
        if (demonica$irisAccessorBound) {
            ci.cancel();
            return;
        }
        if (DemonicaRuntime.MODID.equals(modId)) {
            // DH's own check and bind run from here; the re-entry below lands here too.
            demonica$irisAccessorBound = true;
            return;
        }
        ci.cancel();
        demonica$bindAsDemonica(modId, accessorClass, accessorConstructor);
    }

    @Inject(
        method = "tryCreateModCompatAccessor([Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Supplier;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void demonica$bindIrisAccessorOnceFromArray(
        String[] modIds, Class<?> accessorClass, Supplier<?> accessorConstructor, CallbackInfo ci
    ) {
        if (accessorClass != IIrisAccessor.class) {
            return;
        }
        ci.cancel();
        if (!demonica$irisAccessorBound) {
            demonica$bindAsDemonica(String.join(", ", modIds), accessorClass, accessorConstructor);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void demonica$bindAsDemonica(String requested, Class<?> accessorClass, Supplier<?> accessorConstructor) {
        demonica$LOGGER.info("Distant Horizons asked for its Iris accessor as [{}]; binding it as [{}]",
            requested, DemonicaRuntime.MODID);
        tryCreateModCompatAccessor(DemonicaRuntime.MODID, (Class) accessorClass, (Supplier) accessorConstructor);
    }
}
