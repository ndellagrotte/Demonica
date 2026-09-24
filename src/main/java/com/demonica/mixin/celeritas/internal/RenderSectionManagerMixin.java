package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.terrain.ShaderTerrain;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * S1 (docs/celeritas/patches/S1.md): a section manager built while a shader pack is active gets a shadow pass, the
 * second render list and search that Iris's shadow map is drawn from. forge122's section manager calls the deprecated
 * constructor, which passes {@code hasShadowPass = false} on to the full one; this sets that argument. The handler is
 * static because it runs before {@code this(...)} has initialised the object.
 */
@Patch(value = "S1", group = PatchGroup.SHADOW, context = {
    // forge122's section manager is built through the deprecated constructor, whose delegate call this patches.
    "Lorg/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager;<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"
        + "Lnet/minecraft/client/multiplayer/WorldClient;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V calls "
        + "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"
        + "Ljava/util/function/Supplier;Ljava/util/function/BiFunction;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;III)V"
})
@Mixin(value = RenderSectionManager.class, remap = false, priority = 1100)
public abstract class RenderSectionManagerMixin {
    @ModifyArg(
        method = "<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;Ljava/util/function/Supplier;"
            + "Ljava/util/function/BiFunction;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;III)V",
        at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"
                + "Ljava/util/function/Supplier;Ljava/util/function/BiFunction;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;IIIZ)V"),
        index = 8
    )
    private static boolean demonica$shadowPass(boolean hasShadowPass) {
        return hasShadowPass || ShaderTerrain.needsShadowPass();
    }
}
