package com.demonica.celeritas;

/**
 * Implemented on Celeritas's {@code ChunkTracker} by the quarantine patch S19 (docs/celeritas/patches/S19.md), which
 * exposes the neighbour gate that upstream keeps private. Read it through {@link ChunkTrackers}, which falls back when
 * the patch is not applied.
 */
public interface ChunkTrackerAccess {
    /** The radius of loaded neighbours a chunk needs before Celeritas meshes it. */
    int demonica$getRequiredNeighborRadius();
}
