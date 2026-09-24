package com.demonica.celeritas;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Reads {@link ChunkTracker}'s neighbour gate, which upstream keeps private (Actinium's fork added a getter).
 * Until the quarantine's accessor (S19) replaces it, the field is read reflectively; if it is missing, the
 * radius reads as 0 and the Distant Horizons uniform falls back to its default.
 */
public final class ChunkTrackers {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");
    private static final VarHandle REQUIRED_NEIGHBOR_RADIUS = find();

    private ChunkTrackers() {
    }

    public static int requiredNeighborRadius(ChunkTracker tracker) {
        return REQUIRED_NEIGHBOR_RADIUS != null ? (int) REQUIRED_NEIGHBOR_RADIUS.get(tracker) : 0;
    }

    private static VarHandle find() {
        try {
            return MethodHandles.privateLookupIn(ChunkTracker.class, MethodHandles.lookup())
                .findVarHandle(ChunkTracker.class, "requiredNeighborRadius", int.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Celeritas's ChunkTracker has no requiredNeighborRadius field; treating the neighbour gate as 0", e);
            return null;
        }
    }
}
