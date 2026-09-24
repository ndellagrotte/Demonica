package com.demonica.mixin.core;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobalDestroyBlock {
    @Unique
    private int demonica$blockDamageMinFilter = GL11.GL_NEAREST_MIPMAP_LINEAR;

    @Unique
    private int demonica$blockDamageMagFilter = GL11.GL_NEAREST;

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;preRenderDamagedBlocks()V")
    )
    private void demonica$beginBlockDamageNearest(Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
        TextureInfo info = TextureInfoCache.INSTANCE.getInfo(GLStateManager.getBoundTextureForServerState());
        this.demonica$blockDamageMinFilter = info.getMinFilter();
        this.demonica$blockDamageMagFilter = info.getMagFilter();
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;postRenderDamagedBlocks()V")
    )
    private void demonica$endBlockDamageNearest(Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, this.demonica$blockDamageMinFilter);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, this.demonica$blockDamageMagFilter);
    }
}
