package com.demonica.compat.chunkanimator;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimatedSectionPartitionTest {
    /** The sections of one region by local index, made on demand; the region itself is not needed. */
    private static final class Sections implements IntFunction<RenderSection> {
        private final Map<Integer, RenderSection> byIndex = new HashMap<>();
        private final Map<RenderSection, Integer> indices = new IdentityHashMap<>();

        @Override
        public RenderSection apply(int index) {
            return this.byIndex.computeIfAbsent(index, i -> {
                RenderSection section = new RenderSection(null, i & 7, (i >> 3) & 3, i >> 5);
                this.indices.put(section, i);
                return section;
            });
        }

        /** Animates the sections of the given indices, each with the offset (index, 2 * index, 3 * index). */
        ChunkAnimationProvider animating(int... animatedIndices) {
            Map<RenderSection, Integer> animated = new IdentityHashMap<>();
            for (int index : animatedIndices) {
                animated.put(this.apply(index), index);
            }
            return new ChunkAnimationProvider() {
                @Override
                public boolean getSectionOffset(@NotNull RenderSection section, @NotNull float[] out) {
                    Integer index = animated.get(section);
                    if (index == null) {
                        return false;
                    }
                    out[0] = index;
                    out[1] = 2 * index;
                    out[2] = 3 * index;
                    return true;
                }
            };
        }
    }

    @Test
    void nothingAnimatingKeepsUpstreamsPath() {
        Sections sections = new Sections();
        byte[] list = {3, 7, 11};
        assertNull(AnimatedSectionPartition.of(list, 3, sections, sections.animating()));
        // Beyond the count is not looked at.
        assertNull(AnimatedSectionPartition.of(list, 2, sections, sections.animating(11)));
    }

    @Test
    void splitsAnimatingSectionsOutInOrder() {
        Sections sections = new Sections();
        byte[] list = {3, 7, 11, 40, 5};
        AnimatedSectionPartition partition = AnimatedSectionPartition.of(list, 5, sections, sections.animating(7, 40));

        assertEquals(3, partition.staticCount());
        byte[] staticSections = new byte[3];
        System.arraycopy(partition.staticSections(), 0, staticSections, 0, 3);
        assertArrayEquals(new byte[] {3, 11, 5}, staticSections);

        List<AnimatedSectionPartition.AnimatedSection> animated = partition.animated();
        assertEquals(2, animated.size());
        assertEquals(new AnimatedSectionPartition.AnimatedSection(7, 7f, 14f, 21f), animated.get(0));
        assertEquals(new AnimatedSectionPartition.AnimatedSection(40, 40f, 80f, 120f), animated.get(1));
    }

    @Test
    void readsSectionIndicesAsUnsignedBytes() {
        Sections sections = new Sections();
        byte[] list = {(byte) 200, (byte) 255};
        AnimatedSectionPartition partition = AnimatedSectionPartition.of(list, 2, sections, sections.animating(255));

        assertEquals(1, partition.staticCount());
        assertEquals((byte) 200, partition.staticSections()[0]);
        assertEquals(255, partition.animated().get(0).sectionIndex());
    }

    @Test
    void everySectionAnimatingLeavesAnEmptyStaticList() {
        Sections sections = new Sections();
        byte[] list = {1, 2};
        AnimatedSectionPartition partition = AnimatedSectionPartition.of(list, 2, sections, sections.animating(1, 2));

        assertEquals(0, partition.staticCount());
        assertEquals(2, partition.animated().size());
    }

    @Test
    void aMissingSectionStaysStatic() {
        Sections sections = new Sections();
        ChunkAnimationProvider provider = sections.animating(0, 1);
        IntFunction<RenderSection> onlySectionOne = index -> index == 1 ? sections.apply(1) : null;
        byte[] list = {0, 1};
        AnimatedSectionPartition partition = AnimatedSectionPartition.of(list, 2, onlySectionOne, provider);

        assertEquals(1, partition.staticCount());
        assertEquals(0, partition.staticSections()[0]);
        assertEquals(1, partition.animated().get(0).sectionIndex());
    }

    @Test
    void cameraTranslationMatchesUpstream() {
        // (chunkBlockPos - cameraBlockPos) - cameraFraction, as DefaultChunkRenderer.getCameraTranslation computes it.
        assertEquals(-16.25f, AnimatedSectionRenderer.cameraTranslation(32, 48, 0.25f));
    }
}
