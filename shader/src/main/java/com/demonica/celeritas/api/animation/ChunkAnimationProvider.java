package com.demonica.celeritas.api.animation;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.jetbrains.annotations.NotNull;

/**
 * Per-section chunk animations for Celeritas's terrain, the counterpart of the per-{@code RenderChunk}
 * {@code glTranslate} that vanilla chunk rendering lets a mod such as Chunk Animator apply.
 *
 * <p>Vanilla gives such a mod two hooks: {@code RenderChunk#setOrigin}, when a chunk takes its position, and
 * {@code ChunkRenderContainer#preRenderChunk}, just before it is drawn. Celeritas creates no {@code RenderChunk} and
 * never calls either, so this interface re-attaches both to the section lifecycle: {@link #onSectionAdded} when a
 * section is created, and {@link #getSectionOffset} on the render thread for every section with geometry whenever its
 * region's draw batch is rebuilt (patch C2, docs/celeritas/patches/C2.md). A section that reports an offset is drawn on
 * its own, translated by it, instead of with its region's batch.</p>
 */
public interface ChunkAnimationProvider {
    /**
     * Called on the client thread when a render section is created, that is when it enters the render distance. The
     * section itself serves as the animation's identity token.
     */
    default void onSectionAdded(@NotNull RenderSection section) {
    }

    /**
     * Queries the animation offset of a section on the render thread.
     *
     * @param section the section about to be drawn
     * @param out     three floats receiving the offset in blocks; written only when this returns {@code true}
     * @return {@code true} if the section is animating and must be drawn apart from its region, {@code false} if it
     *         is drawn with the region's batch
     */
    boolean getSectionOffset(@NotNull RenderSection section, @NotNull float[] out);
}
