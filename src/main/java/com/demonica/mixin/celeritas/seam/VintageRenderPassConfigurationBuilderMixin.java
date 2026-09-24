package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.terrain.ShaderPassConfigurations;
import com.demonica.celeritas.terrain.ShaderTerrain;
import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.VintageRenderPassConfigurationBuilder;

/**
 * S14 (docs/celeritas/patches/S14.md): while a shader pack is active, Celeritas renders the passes the pack's
 * programs expect ({@link ShaderPassConfigurations}) instead of its own. The configuration is chosen when the section
 * manager is built; ShaderTerrain.reloadIfStale rebuilds it when the pack changes.
 */
@Mixin(value = VintageRenderPassConfigurationBuilder.class, remap = false, priority = 1100)
public abstract class VintageRenderPassConfigurationBuilderMixin {
    @Inject(method = "build", at = @At("HEAD"), cancellable = true)
    private static void demonica$shaderPasses(ChunkVertexType vertexType,
                                              CallbackInfoReturnable<RenderPassConfiguration<BlockRenderLayer>> cir) {
        if (ShaderTerrain.isPackActive()) {
            cir.setReturnValue(ShaderPassConfigurations.build(vertexType));
        }
    }
}
