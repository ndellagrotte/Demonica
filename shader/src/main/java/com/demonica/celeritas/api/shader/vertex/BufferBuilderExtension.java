package com.demonica.celeritas.api.shader.vertex;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Demonica's additions to vanilla's BufferBuilder (BufferBuilderMixin). */
public interface BufferBuilderExtension {
    /** Attaches the block context of the quads drawn from now on, or none; quads drawn before keep theirs. */
    void demonica$setActiveQuadContext(@Nullable VanillaQuadContext context);

    /**
     * The block context of every quad drawn since {@code begin}, entry n for quad n (null for a quad drawn without
     * one), which the buffer then forgets. Empty if no context was attached.
     */
    List<VanillaQuadContext> demonica$consumeQuadContexts();

    boolean demonica$isDrawing();

    void demonica$discard();
}
