package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension;
import com.demonica.celeritas.terrain.ShaderBlockContexts;
import com.demonica.celeritas.terrain.ShaderPassConfigurations;
import com.demonica.runtime.DemonicaRuntime;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;

/**
 * The meshing loop of a section:
 * <ul>
 *   <li>S10 (docs/celeritas/patches/S10.md): in a build for the shader passes, a block the pack moves to another
 *   layer renders in that layer only, and the quads of each block that takes the vanilla path carry its
 *   {@link ShaderBlockContexts context} into the buffer, for S11 to hand to the extended vertex encoder.</li>
 *   <li>S13 (docs/celeritas/patches/S13.md): Demonica's option picks Celeritas's fast block renderer, which upstream
 *   turns on in dev only.</li>
 * </ul>
 * The method selectors carry the full descriptor: an erased bridge {@code execute(...)Object} calls this one.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false, priority = 1100)
public abstract class ChunkBuilderMeshingTaskMixin {
    @Unique
    private static final String EXECUTE = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
        + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;";

    // Whether this task meshes into the passes built for a shader pack (S14). A task runs once, on one thread.
    @Unique
    private boolean demonica$shaderBuild;

    @Inject(method = EXECUTE, at = @At("HEAD"))
    private void demonica$beginBuild(ChunkBuildContext context, CancellationToken cancellationToken,
                                     CallbackInfoReturnable<ChunkBuildOutput> cir) {
        this.demonica$shaderBuild = ShaderPassConfigurations.isShaderConfiguration(context.buffers.getRenderPassConfiguration());
    }

    @WrapOperation(method = EXECUTE, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z"))
    private boolean demonica$packLayer(Block block, IBlockState state, BlockRenderLayer layer, Operation<Boolean> original) {
        BlockRenderLayer packLayer = this.demonica$shaderBuild ? ShaderBlockContexts.layerOverride(block) : null;
        if (packLayer == null) {
            return original.call(block, state, layer);
        }
        if (layer != packLayer) {
            return false;
        }
        // As in Actinium: the block is still asked about the layer it renders in.
        original.call(block, state, layer);
        return true;
    }

    @WrapOperation(method = EXECUTE, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlock(Lnet/minecraft/block/state/IBlockState;"
            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
        remap = true))
    private boolean demonica$vanillaBlockContext(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos, IBlockAccess world,
                                                 BufferBuilder buffer, Operation<Boolean> original) {
        if (!this.demonica$shaderBuild || !(buffer instanceof BufferBuilderExtension extension)) {
            return original.call(dispatcher, state, pos, world, buffer);
        }
        extension.demonica$setActiveQuadContext(ShaderBlockContexts.of(state, pos, world));
        try {
            return original.call(dispatcher, state, pos, world, buffer);
        } finally {
            extension.demonica$setActiveQuadContext(null);
        }
    }

    @ModifyExpressionValue(method = EXECUTE, at = @At(value = "FIELD",
        target = "Lorg/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask;USE_NEW_BLOCK_RENDERER:Z"))
    private boolean demonica$fastBlockRenderer(boolean upstreamDefault) {
        return DemonicaRuntime.options().performance.useFastBlockRenderer;
    }
}
