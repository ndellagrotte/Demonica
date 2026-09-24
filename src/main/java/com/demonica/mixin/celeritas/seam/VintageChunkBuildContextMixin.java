package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.api.shader.vertex.BlockRenderContext;
import com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension;
import com.demonica.celeritas.api.shader.vertex.ContextAwareChunkVertexEncoder;
import com.demonica.celeritas.api.shader.vertex.VanillaQuadContext;
import com.demonica.celeritas.terrain.ShaderBlockContexts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * S11 (docs/celeritas/patches/S11.md): the quads of the vanilla meshing path carry their block's context into the
 * shader passes. Celeritas copies each layer's vanilla buffer into its own vertices ({@code copyBlockData}); this
 * takes the contexts S10 left in that buffer (one per quad, in draw order), moves translucent fluid quads to the
 * pack's water pass, and has the extended vertex encoder write each quad with its block's context.
 */
@Mixin(value = VintageChunkBuildContext.class, remap = false, priority = 1100)
public abstract class VintageChunkBuildContextMixin {
    // The contexts of the layer being copied, and the quad being copied. A build context serves one thread at a time.
    @Unique
    private List<VanillaQuadContext> demonica$quadContexts = List.of();

    @Unique
    private int demonica$quadIndex;

    @Unique
    private final BlockRenderContext demonica$renderContext = new BlockRenderContext();

    @WrapOperation(method = "convertVanillaDataToCeleritasData", at = @At(value = "INVOKE",
        target = "Lorg/taumc/celeritas/impl/render/terrain/compile/VintageChunkBuildContext;copyBlockData(Ljava/nio/ByteBuffer;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;)V"))
    private void demonica$copyWithContexts(VintageChunkBuildContext context, ByteBuffer source, ChunkBuildBuffers buffers, Material material,
                                           Operation<Void> original, @Local BufferBuilder buffer) {
        this.demonica$quadContexts = buffer instanceof BufferBuilderExtension extension ? extension.demonica$consumeQuadContexts() : List.of();
        this.demonica$quadIndex = 0;
        try {
            original.call(context, source, buffers, material);
        } finally {
            this.demonica$quadContexts = List.of();
        }
    }

    @WrapOperation(method = "copyBlockData", at = @At(value = "INVOKE",
        target = "Lorg/taumc/celeritas/impl/render/terrain/compile/VintageChunkBuildContext;selectMaterial("
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;"))
    private Material demonica$fluidMaterial(VintageChunkBuildContext context, Material material, TextureAtlasSprite sprite,
                                            Operation<Material> original, @Local(argsOnly = true) ChunkBuildBuffers buffers) {
        if (ShaderBlockContexts.isFluid(this.demonica$currentContext())) {
            return ShaderBlockContexts.fluidMaterial(buffers.getRenderPassConfiguration(), material);
        }
        return original.call(context, material, sprite);
    }

    @WrapOperation(method = "copyBlockData", at = @At(value = "INVOKE",
        target = "Lorg/embeddedt/embeddium/impl/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push("
            + "[Lorg/embeddedt/embeddium/impl/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;)V"))
    private void demonica$pushWithContext(ChunkMeshBufferBuilder vertexBuffer, ChunkVertexEncoder.Vertex[] quad, Material material,
                                          Operation<Void> original, @Local(argsOnly = true) ChunkBuildBuffers buffers) {
        VanillaQuadContext quadContext = this.demonica$currentContext();
        // copyBlockData pushes each quad once, in buffer order.
        this.demonica$quadIndex++;
        if (quadContext == null || !(buffers.get(material).getEncoder() instanceof ContextAwareChunkVertexEncoder encoder)) {
            original.call(vertexBuffer, quad, material);
            return;
        }
        encoder.prepareToRenderVanilla(ShaderBlockContexts.apply(quadContext, this.demonica$renderContext));
        try {
            original.call(vertexBuffer, quad, material);
        } finally {
            encoder.finishRenderingBlock();
        }
    }

    @Unique
    private @Nullable VanillaQuadContext demonica$currentContext() {
        int index = this.demonica$quadIndex;
        return index < this.demonica$quadContexts.size() ? this.demonica$quadContexts.get(index) : null;
    }
}
