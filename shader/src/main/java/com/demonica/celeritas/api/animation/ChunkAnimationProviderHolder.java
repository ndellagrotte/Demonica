package com.demonica.celeritas.api.animation;

import org.jetbrains.annotations.Nullable;

/**
 * The single active {@link ChunkAnimationProvider}, set by the compat class of the mod that animates chunks (Chunk
 * Animator's {@code ChunkAnimatorCompat}) and read by patch C2 in the quarantine. The renderer holds no compile-time
 * reference to any mod class; while no provider is set, the patch does nothing. Same pattern as
 * {@link com.demonica.celeritas.api.shader.ShaderProviderHolder}.
 */
public final class ChunkAnimationProviderHolder {
    private static ChunkAnimationProvider provider;

    private ChunkAnimationProviderHolder() {
    }

    /** Replaces the active provider; {@code null} turns per-section animations off. */
    public static void setProvider(@Nullable ChunkAnimationProvider provider) {
        ChunkAnimationProviderHolder.provider = provider;
    }

    /** @return the active provider, or {@code null} when none is set */
    @Nullable
    public static ChunkAnimationProvider getProvider() {
        return provider;
    }
}
