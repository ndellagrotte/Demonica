package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.compat.Mods;
import com.demonica.compat.movingelevators.MovingElevatorsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

/**
 * C3 (docs/celeritas/patches/C3.md): mods that draw block entities of their own in vanilla's block-entity batch,
 * after the {@code TileEntityRendererDispatcher.preDrawBatch()} call in {@code RenderGlobal.renderEntities}, which
 * Celeritas cancels before that call. They run at the same point of Celeritas's block-entity pass, each gated at
 * runtime by its mod's presence; every call into a mod's compat class sits behind a {@link Mods} flag, so no mod class
 * loads without its mod.
 * <ul>
 *   <li>Moving Elevators: the block entities of a moving cage.</li>
 * </ul>
 * S9 has entered Iris's block-entity phase at HEAD of the same method, so the draws take the pack's block-entity
 * program. The method selector carries the full descriptor: the erased bridge {@code renderBlockEntities(Object)}
 * calls this one.
 */
@Patch(value = "C3", group = PatchGroup.COMPAT)
@Mixin(value = CeleritasWorldRenderer.class, remap = false, priority = 1100)
public abstract class CeleritasWorldRendererCompatMixin {
    @Inject(method = "renderBlockEntities(Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer$TileEntityRenderContext;)I",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;preDrawBatch()V",
            shift = At.Shift.AFTER))
    private void demonica$renderModBlockEntities(CeleritasWorldRenderer.TileEntityRenderContext context,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (Mods.MOVINGELEVATORS) {
            MovingElevatorsCompat.renderCageBlockEntities(context.partialTicks());
        }
    }
}
