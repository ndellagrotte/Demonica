package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.compat.FastBlockRendererCompat;
import com.demonica.runtime.DemonicaRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.ChunkBufferBuilder;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

/**
 * S13 (docs/celeritas/patches/S13.md), the switch: which blocks Celeritas's fast block renderer meshes. Since upstream
 * {@code 26ab2175} the meshing loop calls vanilla's {@code BlockRendererDispatcher.renderBlock} for every block, into a
 * {@link ChunkBufferBuilder} that knows its build, and upstream's own {@code BlockRendererDispatcherMixin} wraps the
 * {@code BlockModelRenderer.renderModel} call inside it: with {@code ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER}
 * (true only in a deobfuscated environment, or with {@code -Dceleritas.useVintageFastBlockRenderer}) it hands the model
 * to the fast renderer. Demonica holds that flag false ({@link ChunkBuilderMeshingTaskMixin}), so upstream's wrapper
 * always passes through, and this wrapper of the same call decides from Demonica's option, which is the same in dev
 * and production and off by default like upstream's production setting. The decision is per block: a block that a mod
 * renders or hides from inside vanilla's dispatcher stays on the vanilla path ({@link FastBlockRendererCompat}).
 *
 * <p>Both wrappers target the same instruction; MixinExtras chains them, and with upstream's flag false the order does
 * not matter. Outside a chunk build the buffer is never a {@code ChunkBufferBuilder}, so every other caller of
 * {@code renderBlock} (block damage, held blocks, mods) is untouched.
 */
@Patch(value = "S13", group = PatchGroup.MESHING)
@Mixin(value = BlockRendererDispatcher.class, priority = 1100)
public abstract class BlockRendererDispatcherMixin {
    @WrapOperation(method = "renderBlock", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/BlockModelRenderer;renderModel(Lnet/minecraft/world/IBlockAccess;"
            + "Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;"
            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;Z)Z"))
    private boolean demonica$fastBlockRenderer(BlockModelRenderer renderer, IBlockAccess world, IBakedModel model, IBlockState state,
                                               BlockPos pos, BufferBuilder buffer, boolean checkSides, Operation<Boolean> original) {
        if (buffer instanceof ChunkBufferBuilder chunkBuffer && world instanceof CeleritasBlockAccess blockAccess
            && DemonicaRuntime.options().performance.useFastBlockRenderer
            && !FastBlockRendererCompat.requiresVanillaRenderer(state.getBlock(), pos)) {
            return chunkBuffer.getContext().getBlockRenderer().renderModel(model, state, pos, blockAccess, chunkBuffer.getLayer());
        }
        return original.call(renderer, world, model, state, pos, buffer, checkSides);
    }
}
