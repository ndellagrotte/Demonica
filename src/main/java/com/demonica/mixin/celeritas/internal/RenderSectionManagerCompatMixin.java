package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import com.demonica.celeritas.api.animation.ChunkAnimationProviderHolder;
import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C2 (docs/celeritas/patches/C2.md): a newly created section is reported to the {@link ChunkAnimationProvider}, the
 * counterpart of vanilla's {@code RenderChunk#setOrigin}, which Chunk Animator hooks to start a chunk's animation.
 * Injected at the method's tail: its early return, for a section that already exists, creates none.
 */
@Patch(value = "C2", group = PatchGroup.COMPAT)
@Mixin(value = RenderSectionManager.class, remap = false, priority = 1100)
public abstract class RenderSectionManagerCompatMixin {
    @Inject(method = "onSectionAdded(III)V", at = @At("TAIL"))
    private void demonica$notifyAnimationProvider(int x, int y, int z, CallbackInfo ci, @Local RenderSection renderSection) {
        ChunkAnimationProvider provider = ChunkAnimationProviderHolder.getProvider();
        if (provider != null) {
            provider.onSectionAdded(renderSection);
        }
    }
}
