package com.demonica.api.render.terrain;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

import java.util.List;

/**
 * Transforms the baked quads of a block before terrain chunk upload.
 *
 * <p>Demonica calls registered transformers after the block model has produced
 * quads and before those quads are uploaded. This lets addons implement
 * runtime block quad work such as CTM or emissive overlays without Mixins into
 * the terrain renderer. Only blocks that Celeritas's fast block renderer meshes
 * go through transformers; blocks on the vanilla path render through vanilla's
 * {@code BlockModelRenderer} (docs/celeritas/patches/S20.md).</p>
 *
 * <p>Implementations must return a non-null list. An empty final result tells
 * the renderer to skip the current side; a non-empty result replaces the quads
 * passed to later transformers and to the renderer.</p>
 */
public interface BlockQuadTransformer {
    /**
     * Transforms the quads produced for one block face or the unassigned quads
     * of a block model.
     *
     * @param state the extended block state being rendered
     * @param pos the block position being rendered
     * @param blockAccess thread-safe access to the block state and neighbors
     * @param layer the block render layer currently being built
     * @param side the face these quads belong to, or {@code null} for unassigned quads
     * @param quads the current quad list to transform
     * @return the transformed quad list; must not be {@code null}, an empty
     *         final result tells the renderer to skip this side
     */
    List<BakedQuad> transform(IBlockState state, BlockPos pos,
                              CeleritasBlockAccess blockAccess, BlockRenderLayer layer,
                              EnumFacing side, List<BakedQuad> quads);
}
