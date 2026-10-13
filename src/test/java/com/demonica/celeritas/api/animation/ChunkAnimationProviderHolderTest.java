package com.demonica.celeritas.api.animation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChunkAnimationProviderHolderTest {
    private static final ChunkAnimationProvider PROVIDER = (section, out) -> false;

    @AfterEach
    void clear() {
        ChunkAnimationProviderHolder.setProvider(null);
    }

    @Test
    void startsWithoutAProvider() {
        assertNull(ChunkAnimationProviderHolder.getProvider());
    }

    @Test
    void holdsTheProviderItIsGiven() {
        ChunkAnimationProviderHolder.setProvider(PROVIDER);
        assertSame(PROVIDER, ChunkAnimationProviderHolder.getProvider());
    }

    @Test
    void nullTurnsAnimationsOff() {
        ChunkAnimationProviderHolder.setProvider(PROVIDER);
        ChunkAnimationProviderHolder.setProvider(null);
        assertNull(ChunkAnimationProviderHolder.getProvider());
    }
}
