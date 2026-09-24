package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.api.debug.RenderDebugHooksHolder;
import com.demonica.celeritas.api.shader.PassSemantics;
import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.terrain.ShaderTerrain;
import net.coderbot.iris.celeritas.IrisCeleritasChunkProgramOverrides;
import net.coderbot.iris.celeritas.IrisCeleritasChunkShaderInterface;
import net.coderbot.iris.celeritas.IrisCeleritasShaderProvider;
import net.coderbot.iris.celeritas.IrisTerrainPass;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S2 (docs/celeritas/patches/S2.md): the shader pack's programs draw terrain. Every pass goes through
 * {@code begin}/{@code end} around its draws; for a pass of the shader configuration (S14) while a pack is active,
 * these bind the pack's program instead of compiling Celeritas's own. Passes of upstream's configuration, and passes
 * the pack has no program for, keep Celeritas's program.
 */
@Patch(value = "S2", group = PatchGroup.CORE_TERRAIN, context = {
    // Every pass's draws go through begin and end, and forge122's own renderer does not override them.
    "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"
        + "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;"
        + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;"
        + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V calls "
        + "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;begin(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V",
    "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"
        + "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;"
        + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;"
        + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V calls "
        + "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;end(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V",
    "absent Lorg/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager$ChunkRenderer;"
        + "begin(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V",
    "absent Lorg/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager$ChunkRenderer;"
        + "end(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V"
}, uses = {ShaderTerrain.class, IrisCeleritasShaderProvider.class, IrisCeleritasChunkProgramOverrides.class,
    IrisCeleritasChunkShaderInterface.class, IrisTerrainPass.class, PassSemantics.class})
@Mixin(value = ShaderChunkRenderer.class, remap = false, priority = 1100)
public abstract class ShaderChunkRendererMixin {
    @Shadow
    @Final
    protected RenderPassConfiguration<?> renderPassConfiguration;

    @Shadow
    protected GlProgram<ChunkShaderInterface> activeProgram;

    @Unique
    private GlProgram<? extends ChunkShaderInterface> demonica$packProgram;

    @Inject(method = "begin", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void demonica$bindPackProgram(TerrainRenderPass pass, CallbackInfo ci) {
        GlProgram<? extends ChunkShaderInterface> program = ShaderTerrain.packProgram(pass, this.renderPassConfiguration);
        if (program == null) {
            return;
        }
        pass.startDrawing();
        program.bind();
        program.getInterface().setupState(pass);
        this.activeProgram = (GlProgram<ChunkShaderInterface>) program;
        this.demonica$packProgram = program;
        ci.cancel();
    }

    @Inject(method = "end", at = @At("HEAD"), cancellable = true)
    private void demonica$unbindPackProgram(TerrainRenderPass pass, CallbackInfo ci) {
        GlProgram<? extends ChunkShaderInterface> program = this.demonica$packProgram;
        if (program == null) {
            return;
        }
        this.demonica$packProgram = null;
        RenderDebugHooksHolder.logCurrentFramebufferSamples("terrain:" + pass.name(), 1);
        program.getInterface().restoreState();
        program.unbind();
        this.activeProgram = null;
        pass.endDrawing();
        ci.cancel();
    }
}
