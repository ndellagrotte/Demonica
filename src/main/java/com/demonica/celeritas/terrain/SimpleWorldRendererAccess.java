package com.demonica.celeritas.terrain;

import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;

/**
 * S7: implemented on Celeritas's world renderer by the quarantine (SimpleWorldRendererAccessMixin). Without the patch
 * the renderer does not implement it, and {@link CeleritasWorldRendererCompat} offers no terrain to the shadow pass.
 */
public interface SimpleWorldRendererAccess {
    void demonica$setCurrentViewport(Viewport viewport);

    ChunkRenderMatrices demonica$createChunkRenderMatrices();
}
