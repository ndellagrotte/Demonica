package com.demonica.mixin.core;

import com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension;
import com.demonica.render.TileEntityBatchDrawGuard;
import com.demonica.render.TileEntityGlStateGuard;
import com.demonica.runtime.DemonicaRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge's shared tile-entity batch ({@code preDrawBatch} ... {@code drawBatch}): each batch runs inside a
 * {@link TileEntityGlStateGuard}, and a batch that a nested renderer already flushed is not drawn a second time.
 */
@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcherBatch {
    @Unique
    private static boolean demonica$warnedFinishedBatch;

    @Inject(method = "preDrawBatch", at = @At("RETURN"))
    private void demonica$saveStateForBatch(CallbackInfo ci) {
        TileEntityGlStateGuard.onBatchOpened();
    }

    @Inject(method = "drawBatch", at = @At("HEAD"))
    private void demonica$restoreStateForBatchDraw(int pass, CallbackInfo ci) {
        TileEntityGlStateGuard.onBatchDrawing();
    }

    @Inject(method = "drawBatch", at = @At("RETURN"))
    private void demonica$restoreStateAfterBatch(int pass, CallbackInfo ci) {
        TileEntityGlStateGuard.onBatchDrawn();
    }

    @WrapOperation(
        method = "drawBatch",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V")
    )
    private void demonica$drawBatchIfBuilding(Tessellator tessellator, Operation<Void> original) {
        BufferBuilder buffer = tessellator.getBuffer();
        boolean building = ((BufferBuilderExtension) buffer).demonica$isDrawing();
        if (!TileEntityBatchDrawGuard.drawIfBuilding(building, () -> original.call(tessellator))
            && !demonica$warnedFinishedBatch) {
            demonica$warnedFinishedBatch = true;
            DemonicaRuntime.logger().warn(
                "Skipping an already-finished Forge tile entity batch after a nested renderer flushed it"
            );
        }
    }
}
