package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.api.shader.vertex.BlockRenderContext;
import com.demonica.celeritas.api.shader.vertex.ContextAwareChunkVertexEncoder;
import com.demonica.celeritas.api.shader.vertex.VanillaQuadContext;
import com.demonica.celeritas.terrain.ShaderBlockContexts;
import com.demonica.celeritas.terrain.ShaderPassConfigurations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadOrientation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BakedQuadGroupAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;
import org.taumc.celeritas.impl.render.terrain.compile.pipeline.VintageBlockRenderer;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

/**
 * S13 (docs/celeritas/patches/S13.md): Celeritas's fast block renderer in a build for the shader passes. Each quad
 * carries its block's {@link ShaderBlockContexts context}, as on the vanilla path (S10, S11); fluid quads keep their
 * material and translucent ones mesh into the pack's water pass; a block the pack moves to another layer keeps it
 * (no render-pass optimisation); and the pack's lighting settings apply: no directional shading if it turns that
 * off, and the ambient occlusion in the colour's alpha if it wants it separate. Whether the fast renderer runs at all
 * is ChunkBuilderMeshingTaskMixin's switch.
 */
@Mixin(value = VintageBlockRenderer.class, remap = false, priority = 1100)
public abstract class VintageBlockRendererMixin {
    @Shadow
    @Final
    private VintageChunkBuildContext context;

    // The block being rendered, in a build for the shader passes; null otherwise. A renderer serves one thread.
    @Unique
    private @Nullable VanillaQuadContext demonica$blockContext;

    @Unique
    private final BlockRenderContext demonica$renderContext = new BlockRenderContext();

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void demonica$beginBlock(IBlockState state, BlockPos pos, CeleritasBlockAccess world, BlockRenderLayer layer, CallbackInfo ci) {
        boolean shaderBuild = ShaderPassConfigurations.isShaderConfiguration(this.context.buffers.getRenderPassConfiguration());
        this.demonica$blockContext = shaderBuild ? ShaderBlockContexts.of(state, pos, world) : null;
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void demonica$endBlock(IBlockState state, BlockPos pos, CeleritasBlockAccess world, BlockRenderLayer layer, CallbackInfo ci) {
        this.demonica$blockContext = null;
    }

    // Actinium rendered such blocks with renderBlock(..., false).
    @ModifyArg(method = "renderBlock", at = @At(value = "INVOKE",
        target = "Lorg/embeddedt/embeddium/impl/render/chunk/compile/pipeline/BakedQuadGroupAnalyzer;setDefaultRenderingFlags(I)V"))
    private int demonica$keepPackLayer(int flags, @Local(argsOnly = true) IBlockState state) {
        if (this.demonica$blockContext != null && ShaderBlockContexts.layerOverride(state.getBlock()) != null) {
            return flags & ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        }
        return flags;
    }

    @ModifyExpressionValue(method = "renderBlock", at = @At(value = "INVOKE",
        target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;getMaterialForRenderType(Ljava/lang/Object;)"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;"))
    private Material demonica$fluidMaterial(Material material) {
        if (ShaderBlockContexts.isFluid(this.demonica$blockContext)) {
            return ShaderBlockContexts.fluidMaterial(this.context.buffers.getRenderPassConfiguration(), material);
        }
        return material;
    }

    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
        target = "Lorg/embeddedt/embeddium/impl/render/chunk/compile/pipeline/BakedQuadGroupAnalyzer;chooseOptimalMaterial(I"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"
            + "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;)Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;"))
    private Material demonica$keepFluidMaterial(int flags, Material material, RenderPassConfiguration<?> configuration, BakedQuadView quad,
                                                Operation<Material> original) {
        return ShaderBlockContexts.isFluid(this.demonica$blockContext) ? material : original.call(flags, material, configuration, quad);
    }

    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
        target = "Lorg/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer;writeGeometry(III"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/minecraft/util/math/Vec3d;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;[I"
            + "Lorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadOrientation;)V"))
    private void demonica$writeWithContext(VintageBlockRenderer renderer, int localX, int localY, int localZ, ChunkModelBuilder builder,
                                           Vec3d offset, Material material, BakedQuadView quad, int[] colors, QuadLightData light,
                                           ModelQuadOrientation orientation, Operation<Void> original) {
        VanillaQuadContext blockContext = this.demonica$blockContext;
        if (blockContext == null || !(builder.getEncoder() instanceof ContextAwareChunkVertexEncoder encoder)) {
            original.call(renderer, localX, localY, localZ, builder, offset, material, quad, colors, light, orientation);
            return;
        }
        encoder.prepareToRenderVanilla(ShaderBlockContexts.apply(blockContext, this.demonica$renderContext));
        try {
            original.call(renderer, localX, localY, localZ, builder, offset, material, quad, colors, light, orientation);
        } finally {
            encoder.finishRenderingBlock();
        }
    }

    @ModifyArg(method = "getVertexLight", at = @At(value = "INVOKE",
        target = "Lorg/embeddedt/embeddium/impl/model/light/LightPipeline;calculate(Lorg/embeddedt/embeddium/impl/model/quad/ModelQuadView;III"
            + "Lorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;"
            + "Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;ZZ)V"),
        index = 7)
    private boolean demonica$directionalShading(boolean shade) {
        return shade && !BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }

    @ModifyExpressionValue(method = "writeGeometry", at = @At(value = "FIELD",
        target = "Lorg/embeddedt/embeddium/impl/render/chunk/ChunkColorWriter;EMBEDDIUM:Lorg/embeddedt/embeddium/impl/render/chunk/ChunkColorWriter;"))
    private ChunkColorWriter demonica$separateAo(ChunkColorWriter writer) {
        return BlockRenderingSettings.INSTANCE.shouldUseSeparateAo() ? ChunkColorWriter.SEPARATE_AO : writer;
    }
}
