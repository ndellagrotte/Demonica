package com.demonica.mixin.celeritas.seam;

import com.demonica.api.render.terrain.BlockQuadTransformer;
import com.demonica.api.render.terrain.BlockQuadTransformerHolder;
import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.pipeline.VintageBlockRenderer;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

import java.util.List;

/**
 * S20 (docs/celeritas/patches/S20.md): the {@link com.demonica.api.render.terrain.BlockQuadTransformer}s addons
 * register see the quads of every block Celeritas's fast block renderer meshes: each face that will be drawn, and
 * the unassigned quads. An empty result drops the face.
 */
@Patch(value = "S20", group = PatchGroup.DEGRADE, uses = {BlockQuadTransformerHolder.class, BlockQuadTransformer.class})
@Mixin(value = VintageBlockRenderer.class, remap = false, priority = 1100)
public abstract class VintageBlockRendererQuadsMixin {
    @WrapOperation(method = "renderBlock", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/model/IBakedModel;getQuads(Lnet/minecraft/block/state/IBlockState;"
            + "Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;",
        remap = true))
    private List<BakedQuad> demonica$transformQuads(IBakedModel model, IBlockState state, EnumFacing side, long rand,
                                                    Operation<List<BakedQuad>> original, @Local(argsOnly = true) BlockPos pos,
                                                    @Local(argsOnly = true) CeleritasBlockAccess world,
                                                    @Local(argsOnly = true) BlockRenderLayer layer) {
        List<BakedQuad> quads = original.call(model, state, side, rand);
        // Only faces the renderer then draws, as in Actinium: its neighbours cull the rest.
        if (quads.isEmpty() || !BlockQuadTransformerHolder.hasTransformers()
            || side != null && !state.shouldSideBeRendered(world, pos, side)) {
            return quads;
        }
        return BlockQuadTransformerHolder.transform(state, pos, world, layer, side, quads);
    }
}
