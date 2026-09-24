package com.demonica.mixin.mod.ichunutil;

import me.ichun.mods.ichunutil.common.module.worldportals.client.render.world.RenderGlobalProxy;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * iChun's portal {@code RenderGlobal} overrides {@code loadRenderers}, so Celeritas's own reload hook (injected into
 * {@code RenderGlobal.loadRenderers}) never runs for it. Reload the portal's Celeritas renderer here instead.
 */
@Mixin(value = RenderGlobalProxy.class, remap = false)
public class MixinRenderGlobalProxy {
    @Inject(method = "loadRenderers", remap = true, at = @At("TAIL"))
    private void demonica$reloadWorldRenderer(CallbackInfo ci) {
        SimpleWorldRenderer<?, ?, ?, ?, ?> renderer = SimpleWorldRenderer.Provider.getWorldRendererNullable(this);
        if (renderer == null) {
            return;
        }

        RenderDevice.enterManagedCode();
        try {
            // Does nothing until the proxy has a world.
            renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
}
