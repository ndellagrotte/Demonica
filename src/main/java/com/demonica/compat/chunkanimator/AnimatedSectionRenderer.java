package com.demonica.compat.chunkanimator;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionFlags;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the sections of a region that an animation moves (patch C2, docs/celeritas/patches/C2.md).
 *
 * <p>Celeritas draws a region with one cached multi-draw batch under one model offset, {@code u_RegionOffset}, so a
 * section cannot be translated inside the batch. When {@link #partition} finds animating sections, the quarantine's
 * {@code DefaultChunkRendererCompatMixin} has upstream build the region's cached batch from {@link #staticRenderList}
 * instead of the full list, and {@link #draw} then draws each animating section with its own one-section batch, the
 * region offset plus the animation offset as the model offset. The cached batch remembers the filtered section list,
 * so upstream's validity check fails against the full list on the next frame and the split is redone until nothing
 * animates; then the full batch is cached again and this class is not called for that region.</p>
 *
 * <p>Mentions no mod class: it depends on {@link ChunkAnimationProvider} only.</p>
 */
public final class AnimatedSectionRenderer {
    private static final int GEOMETRY_FLAG = 1 << RenderSectionFlags.HAS_BLOCK_GEOMETRY;

    private AnimatedSectionRenderer() {
    }

    /** @see AnimatedSectionPartition#of */
    @Nullable
    public static AnimatedSectionPartition partition(RenderRegion region, ChunkRenderList renderList,
                                                     ChunkAnimationProvider provider) {
        return AnimatedSectionPartition.of(renderList.getSectionsWithGeometry(), renderList.getSectionsWithGeometryCount(),
            region::getSection, provider);
    }

    /** A render list of the region's static sections, for upstream's batch assembler. */
    public static ChunkRenderList staticRenderList(RenderRegion region, AnimatedSectionPartition partition) {
        ChunkRenderList list = new ChunkRenderList(region);
        byte[] sections = partition.staticSections();
        for (int i = 0, n = partition.staticCount(); i < n; i++) {
            list.add(sections[i] & 0xFF, GEOMETRY_FLAG);
        }
        return list;
    }

    /**
     * Draws the animating sections one by one. Leaves the model offset at the region's own offset, as
     * {@code setModelMatrixUniforms} would set it.
     *
     * @param tessellation        the region's tessellation from the cached batch, or {@code null} when the static
     *                            batch was empty and upstream made none; then the provider makes it
     * @param sharedIndexBuffer   the pass's shared index buffer, or {@code null} for a sorted pass, which draws from
     *                            its own indices
     */
    public static void draw(AnimatedSectionPartition partition,
                            RenderRegion region,
                            SectionRenderDataStorage storage,
                            CameraTransform occlusionCamera,
                            CameraTransform camera,
                            TerrainRenderPass pass,
                            boolean useBlockFaceCulling,
                            CommandList commandList,
                            @Nullable GlTessellation tessellation,
                            BatchAssembler.TessellationProvider tessellationProvider,
                            @Nullable SharedQuadIndexBuffer sharedIndexBuffer,
                            ChunkShaderInterface shader,
                            GlPrimitiveType primitiveType,
                            long timestamp) {
        float baseX = cameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float baseY = cameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float baseZ = cameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        boolean agesSet = false;
        for (AnimatedSectionPartition.AnimatedSection section : partition.animated()) {
            ChunkRenderList list = new ChunkRenderList(region);
            list.add(section.sectionIndex(), GEOMETRY_FLAG);
            MultiDrawBatch batch = BatchAssembler.fillRegion(region, storage, list, occlusionCamera, pass, useBlockFaceCulling);
            if (batch == null) {
                continue;
            }
            try {
                if (batch.isEmpty()) {
                    continue;
                }
                if (tessellation == null) {
                    tessellation = tessellationProvider.getTessellation(commandList, region, pass);
                }
                if (sharedIndexBuffer != null) {
                    sharedIndexBuffer.ensureCapacity(commandList, batch.getIndexBufferSize());
                }
                batch.upload(commandList);
                if (!agesSet) {
                    shader.setSectionAges(timestamp, region.getSectionLoadTimes());
                    agesSet = true;
                }
                shader.setRegionOffset(baseX + section.dx(), baseY + section.dy(), baseZ + section.dz());
                batch.execute(commandList, tessellation, primitiveType);
            } finally {
                batch.delete();
            }
        }
        shader.setRegionOffset(baseX, baseY, baseZ);
    }

    /** Upstream's {@code DefaultChunkRenderer.getCameraTranslation}. */
    static float cameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }
}
