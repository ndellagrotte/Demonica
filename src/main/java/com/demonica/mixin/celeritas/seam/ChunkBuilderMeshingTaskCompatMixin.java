package com.demonica.mixin.celeritas.seam;

import com.demonica.compat.componentmodelhider.ComponentModelHiderCompat;
import com.demonica.compat.littletiles.LittleTilesCompat;
import com.gtnewhorizon.gtnhlib.compat.Mods;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;

/**
 * C1 (docs/celeritas/patches/C1.md): mods that hook vanilla's chunk rebuild, which Celeritas's meshing task replaces,
 * each gated at runtime by its mod's presence. Every call into a mod's compat class sits behind a {@link Mods} flag, so
 * no mod class loads without its mod.
 * <ul>
 *   <li>Component Model Hider: its culling hooks are armed for the whole build, and a hidden position emits no
 *   geometry. Its neighbours take the vanilla path (S13's switch), where the hider's own redirects apply.</li>
 *   <li>LittleTiles: the tiles' cached geometry joins the section's vanilla-format buffers before they are
 *   converted.</li>
 * </ul>
 * The method selectors carry the full descriptor: an erased bridge {@code execute(...)Object} calls this one.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false, priority = 1100)
public abstract class ChunkBuilderMeshingTaskCompatMixin {
    @Unique
    private static final String EXECUTE = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
        + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;";

    @WrapMethod(method = EXECUTE)
    private ChunkBuildOutput demonica$armComponentModelHider(ChunkBuildContext context, CancellationToken cancellationToken,
                                                            Operation<ChunkBuildOutput> original) {
        if (!Mods.COMPONENT_MODEL_HIDER) {
            return original.call(context, cancellationToken);
        }
        ComponentModelHiderCompat.beginBuild();
        try {
            return original.call(context, cancellationToken);
        } finally {
            ComponentModelHiderCompat.endBuild();
        }
    }

    // The hider's own redirect replaces vanilla's renderBlock call for a hidden position and nothing else: the block
    // still collects its tile entity and still occludes.
    @WrapOperation(method = EXECUTE, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z"))
    private boolean demonica$skipHiddenBlock(Block block, IBlockState state, BlockRenderLayer layer, Operation<Boolean> original,
                                             @Local BlockPos.MutableBlockPos pos) {
        if (Mods.COMPONENT_MODEL_HIDER && ComponentModelHiderCompat.isHidden(pos)) {
            return false;
        }
        return original.call(block, state, layer);
    }

    @WrapOperation(method = EXECUTE, at = @At(value = "INVOKE",
        target = "Lorg/taumc/celeritas/impl/render/terrain/compile/VintageChunkBuildContext;convertVanillaDataToCeleritasData(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;)V"))
    private void demonica$appendLittleTilesGeometry(VintageChunkBuildContext context, ChunkBuildBuffers buffers, Operation<Void> original) {
        if (Mods.LITTLETILES) {
            LittleTilesCompat.appendSectionGeometry(context);
        }
        original.call(context, buffers);
    }
}
