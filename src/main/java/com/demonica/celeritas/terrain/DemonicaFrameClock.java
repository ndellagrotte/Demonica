package com.demonica.celeritas.terrain;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * I2 (docs/celeritas/patches/I2.md): the frame stamp of every terrain search. Celeritas's section graph visits a
 * section only if its stamp is older than the search's, and the player and shadow searches share one lattice. The
 * player search is stamped with vanilla's frame counter and Iris's shadow search with its own, which restarts at 0,
 * so stamps went backwards and whole regions were skipped. Both searches draw from this one increasing counter
 * instead.
 */
public final class DemonicaFrameClock {
    private static final AtomicInteger FRAME = new AtomicInteger();

    private DemonicaFrameClock() {
    }

    /** A stamp later than every one handed out before. Celeritas reads it as an unsigned int. */
    public static int next() {
        return FRAME.incrementAndGet();
    }
}
