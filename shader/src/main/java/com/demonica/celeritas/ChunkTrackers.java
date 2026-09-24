package com.demonica.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;

/**
 * Reads {@link ChunkTracker}'s neighbour gate, which upstream keeps private (Actinium's fork added a getter). The
 * quarantine patch S19 exposes it through {@link ChunkTrackerAccess}; without the patch the radius reads as 0 and
 * the Distant Horizons uniform falls back to its default.
 */
public final class ChunkTrackers {
    private ChunkTrackers() {
    }

    public static int requiredNeighborRadius(ChunkTracker tracker) {
        return tracker instanceof ChunkTrackerAccess access ? access.demonica$getRequiredNeighborRadius() : 0;
    }
}
