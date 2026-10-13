package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import com.demonica.celeritas.api.animation.ChunkAnimationProviderHolder;
import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.compat.chunkanimator.AnimatedSectionPartition;
import com.demonica.compat.chunkanimator.AnimatedSectionRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C2 (docs/celeritas/patches/C2.md): sections that a chunk animation moves are drawn apart from their region. Only
 * while a {@link ChunkAnimationProvider} is set (Chunk Animator's compat installs one); otherwise the wrapped call runs
 * as upstream wrote it.
 *
 * <p>Upstream draws a region with one cached multi-draw batch under one model offset, rebuilt only when the region's
 * visible sections change. This wraps that rebuild: when the provider reports an offset for any section of the region,
 * upstream builds the cached batch from the static sections only, and the animating sections are drawn here, one by
 * one, with the animation offset added to the region offset. The cached batch then holds the shorter section list, so
 * upstream's validity check fails on the next frame and the split is redone until nothing animates. The animating
 * sections are drawn just before the region's own batch; for the sorted translucent pass that is a momentary change
 * of order within the region while the animation runs.</p>
 */
@Patch(value = "C2", group = PatchGroup.COMPAT)
@Mixin(value = DefaultChunkRenderer.class, remap = false, priority = 1100)
public abstract class DefaultChunkRendererCompatMixin {
    @Shadow
    protected abstract SharedQuadIndexBuffer getSharedIndexBuffer(ChunkPrimitiveType primitiveType, CommandList commandList);

    @WrapOperation(
        method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;"
            + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler;createCachedBatch("
                + "Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegion;"
                + "Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;"
                + "Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderList;"
                + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;"
                + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Z"
                + "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;"
                + "Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler$TessellationProvider;)"
                + "Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/CachedBatch;")
    )
    private CachedBatch demonica$splitAnimatedSections(RenderRegion region,
                                                       SectionRenderDataStorage storage,
                                                       ChunkRenderList renderList,
                                                       CameraTransform occlusionCamera,
                                                       TerrainRenderPass pass,
                                                       boolean useBlockFaceCulling,
                                                       CommandList commandList,
                                                       BatchAssembler.TessellationProvider tessellationProvider,
                                                       Operation<CachedBatch> original,
                                                       @Local ChunkShaderInterface shader,
                                                       @Local GlPrimitiveType primitiveType,
                                                       @Local(argsOnly = true, ordinal = 1) CameraTransform camera,
                                                       @Local long timestamp) {
        ChunkAnimationProvider provider = ChunkAnimationProviderHolder.getProvider();
        AnimatedSectionPartition partition = provider == null ? null : AnimatedSectionRenderer.partition(region, renderList, provider);
        if (partition == null) {
            return original.call(region, storage, renderList, occlusionCamera, pass, useBlockFaceCulling, commandList, tessellationProvider);
        }
        ChunkRenderList staticList = AnimatedSectionRenderer.staticRenderList(region, partition);
        CachedBatch cached = original.call(region, storage, staticList, occlusionCamera, pass, useBlockFaceCulling, commandList, tessellationProvider);
        SharedQuadIndexBuffer sharedIndexBuffer = pass.isSorted() ? null
            : this.getSharedIndexBuffer(((DefaultChunkRenderer) (Object) this).getRenderPassConfiguration().getPrimitiveTypeForPass(pass), commandList);
        AnimatedSectionRenderer.draw(partition, region, storage, occlusionCamera, camera, pass, useBlockFaceCulling, commandList,
            cached.getTessellation(), tessellationProvider, sharedIndexBuffer, shader, primitiveType, timestamp);
        return cached;
    }
}
