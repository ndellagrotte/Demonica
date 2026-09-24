package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.ChunkTrackerAccess;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * S19 (docs/celeritas/patches/S19.md): exposes the tracker's neighbour gate, which the Distant Horizons uniforms
 * read (Iris's CameraUniforms, through ChunkTrackers).
 */
@Mixin(value = ChunkTracker.class, remap = false, priority = 1100)
public abstract class ChunkTrackerMixin implements ChunkTrackerAccess {
    @Shadow
    private int requiredNeighborRadius;

    @Override
    public int demonica$getRequiredNeighborRadius() {
        return this.requiredNeighborRadius;
    }
}
