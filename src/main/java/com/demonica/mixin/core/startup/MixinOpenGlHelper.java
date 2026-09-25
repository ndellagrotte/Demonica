package com.demonica.mixin.core.startup;

import com.demonica.debug.DemonicaStartupDebugConfig;
import net.coderbot.iris.debug.flight.GlFlightRecording;
import net.coderbot.iris.debug.flight.GlFlightGpuCommandRecorder;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.demonica.config.DemonicaRuntimeOptions;
import com.mitchej123.lwjgl.LWJGLServiceProvider;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.demonica.runtime.DemonicaRuntime;
import com.demonica.render.VanillaBufferBuilderRenderer;
import com.demonica.render.EndPortalCompositeRenderer;

import java.util.Objects;

@Mixin(value = OpenGlHelper.class, priority = 100)
public class MixinOpenGlHelper {
    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void demonica$initializeGLStateManager(CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();

        // The LWJGL service reads the calling thread's GL capabilities when it is created, so it is created here, on
        // the render thread. Otherwise its first user creates it: with a shader pack enabled at startup, that is a
        // chunk builder thread writing Iris's vertex format, which has no GL context.
        Objects.requireNonNull(LWJGLServiceProvider.LWJGL, "LWJGL service");

        GLStateManager.setDrawableGL(Display.getDrawable());
        GLStateManager.initialize(GLSMInitConfig.builder()
            .lwjglDebug(DemonicaStartupDebugConfig.enableLwjglDebug())
            .displaySize(mc.displayWidth, mc.displayHeight)
            .framebufferSupported(OpenGlHelper.framebufferSupported)
            .fboEnabled(mc.gameSettings.fboEnable)
            .streamingUploadStrategy(demonica$streamingUploadStrategy())
            .gpuCommandRecorder(GlFlightRecording.isEnabled() ? GlFlightGpuCommandRecorder.INSTANCE : null)
            .directDrawer(TessellatorStreamingDrawer::drawDirect)
            .streamingDrawerDestroy(() -> {
                TessellatorStreamingDrawer.destroy();
                VanillaBufferBuilderRenderer.destroy();
                EndPortalCompositeRenderer.destroy();
            })
            .build());

        GLSMHooks.LIGHTMAP_COORDS.addListener(event -> {
            OpenGlHelper.lastBrightnessX = event.x;
            OpenGlHelper.lastBrightnessY = event.y;
        });

        if (Iris.enabled && Thread.currentThread() == GLStateManager.getMainThread()) {
            Iris.onRenderSystemInit();
        }
    }

    @Unique
    private static StreamingUploader.UploadStrategy demonica$streamingUploadStrategy() {
        if (!DemonicaRuntimeOptions.allowDirectMemoryAccess()) {
            return StreamingUploader.UploadStrategy.BUFFER_DATA;
        }

        return DemonicaRuntime.options().advanced.streamingUploadStrategy.glsmStrategy();
    }
}

