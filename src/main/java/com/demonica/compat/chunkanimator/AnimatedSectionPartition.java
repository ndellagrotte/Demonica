package com.demonica.compat.chunkanimator;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A region's sections with geometry split into those drawn with the region's batch and those an animation moves
 * (patch C2). Pure index arithmetic, so that it can be tested without a {@code RenderRegion}; the GL side is
 * {@link AnimatedSectionRenderer}.
 */
public final class AnimatedSectionPartition {
    /** One animating section: its index within the region and Chunk Animator's offset in blocks. */
    public record AnimatedSection(int sectionIndex, float dx, float dy, float dz) {
    }

    private final byte[] staticSections;
    private final int staticCount;
    private final List<AnimatedSection> animated;

    private AnimatedSectionPartition(byte[] staticSections, int staticCount, List<AnimatedSection> animated) {
        this.staticSections = staticSections;
        this.staticCount = staticCount;
        this.animated = animated;
    }

    /**
     * Asks the provider about each of the first {@code count} section indices, in order.
     *
     * @param sections the region-local indices of the sections with geometry, as the render list holds them
     * @param count    how many of them are in use
     * @param lookup   the region's section by local index; a {@code null} section is kept static
     * @return {@code null} when no section is animating (the caller keeps upstream's path untouched), otherwise the
     *         split, with the static sections in their original order
     */
    @Nullable
    public static AnimatedSectionPartition of(byte[] sections, int count, IntFunction<RenderSection> lookup,
                                              ChunkAnimationProvider provider) {
        float[] offset = new float[3];
        byte[] staticSections = null;
        int staticCount = 0;
        List<AnimatedSection> animated = null;
        for (int i = 0; i < count; i++) {
            int sectionIndex = sections[i] & 0xFF;
            RenderSection section = lookup.apply(sectionIndex);
            if (section != null && provider.getSectionOffset(section, offset)) {
                if (animated == null) {
                    animated = new ArrayList<>(4);
                    staticSections = new byte[count];
                    System.arraycopy(sections, 0, staticSections, 0, i);
                    staticCount = i;
                }
                animated.add(new AnimatedSection(sectionIndex, offset[0], offset[1], offset[2]));
            } else if (animated != null) {
                staticSections[staticCount++] = sections[i];
            }
        }
        if (animated == null) {
            return null;
        }
        return new AnimatedSectionPartition(staticSections, staticCount, animated);
    }

    /** The region-local indices of the sections that stay in the region's batch; read the first {@link #staticCount()}. */
    public byte[] staticSections() {
        return this.staticSections;
    }

    public int staticCount() {
        return this.staticCount;
    }

    /** The animating sections, in render-list order. Never empty. */
    public List<AnimatedSection> animated() {
        return this.animated;
    }
}
