package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.terrain.CeleritasWorldRendererCompat;
import com.demonica.celeritas.terrain.SimpleWorldRendererAccess;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * S7 (docs/celeritas/patches/S7.md): what the shadow-terrain adapter ({@code CeleritasWorldRendererCompat}) needs
 * from the world renderer beyond its public methods: setting the viewport that visibility checks and draws use, and
 * the matrices of a terrain draw.
 */
@Patch(value = "S7", group = PatchGroup.SHADOW, uses = CeleritasWorldRendererCompat.class)
@Mixin(value = SimpleWorldRenderer.class, remap = false, priority = 1100)
public abstract class SimpleWorldRendererAccessMixin implements SimpleWorldRendererAccess {
    @Shadow
    protected Viewport currentViewport;

    @Shadow
    protected abstract ChunkRenderMatrices createChunkRenderMatrices();

    @Override
    public void demonica$setCurrentViewport(Viewport viewport) {
        this.currentViewport = viewport;
    }

    @Override
    public ChunkRenderMatrices demonica$createChunkRenderMatrices() {
        return this.createChunkRenderMatrices();
    }
}
