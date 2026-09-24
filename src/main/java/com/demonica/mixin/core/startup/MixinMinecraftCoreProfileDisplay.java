package com.demonica.mixin.core.startup;

import com.demonica.debug.DemonicaStartupDebugConfig;
import com.demonica.debug.CoreProfileContextAttributes;
import com.demonica.debug.OpenGlVersion;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftCoreProfileDisplay {
    @Unique
    private static final Logger demonica$LOGGER = LogManager.getLogger("Demonica");

    @Shadow
    private boolean fullscreen;

    @Inject(method = "createDisplay", at = @At("HEAD"), cancellable = true)
    private void demonica$createCoreProfileDisplay(CallbackInfo ci) throws LWJGLException {
        final int originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
        final int originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
        final boolean originalDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
        try {
            demonica$createCoreProfileDisplayInner(ci);
        } finally {
            // Don't leave the core-profile request persisted in forge_early.cfg: without Demonica,
            // Cleanroom's default path expects the compatibility profile and fails to create a context.
            // LWJGLXX syncs the request to the file while creating the context, so restore the fields
            // and sync them back (see CoreProfileContextAttributes.persistForgeEarlyCompatProfile).
            CoreProfileContextAttributes.restoreForgeEarlyCompatProfile(originalMajor, originalMinor, originalDebug);
            CoreProfileContextAttributes.persistForgeEarlyCompatProfile();
        }
    }

    @Unique
    private void demonica$createCoreProfileDisplayInner(CallbackInfo ci) throws LWJGLException {
        Display.setResizable(true);
        Display.setTitle("Cleanroom");

        PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(8);
        int maxMajor = 4;
        boolean macos = LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX;
        int maxMinor = macos ? 1 : 6;
        boolean lwjglDebug = DemonicaStartupDebugConfig.enableLwjglDebug();
        Exception lastException = null;

        for (int major = maxMajor; major >= 3; --major) {
            int startMinor = major == 4 ? maxMinor : 3;
            int endMinor = major == 3 ? 3 : 0;

            for (int minor = startMinor; minor >= endMinor; --minor) {
                // LWJGLXX ignores ContextAttribs and reads these fields instead, so keep them in sync on every platform.
                CoreProfileContextAttributes.applyForgeEarlyCoreProfile(major, minor, lwjglDebug);
                ContextAttribs attribs = CoreProfileContextAttributes.create(major, minor, lwjglDebug);
                try {
                    demonica$createDisplay(format, attribs);
                } catch (Exception e) {
                    lastException = e;
                    demonica$LOGGER.debug(
                        "Failed to create requested OpenGL {}.{} core profile context (debug={})",
                        major,
                        minor,
                        lwjglDebug,
                        e
                    );
                    demonica$destroyDisplayAfterFailure();
                    continue;
                }

                String actualVersionString = null;
                OpenGlVersion actualVersion;
                try {
                    actualVersionString = GL11.glGetString(GL11.GL_VERSION);
                    actualVersion = OpenGlVersion.parse(actualVersionString);
                    if (!actualVersion.isAtLeast(3, 3)) {
                        throw new IllegalStateException(
                            "OpenGL 3.3 or newer is required, but the created context reports " + actualVersion
                        );
                    }
                } catch (RuntimeException e) {
                    lastException = demonica$createContextValidationFailure(e);
                    demonica$LOGGER.warn(
                        "Created requested OpenGL {}.{} core profile context, but actual GL_VERSION is unusable: {}",
                        major,
                        minor,
                        actualVersionString,
                        e
                    );
                    demonica$destroyDisplayAfterFailure();
                    continue;
                }

                demonica$LOGGER.info(
                    "Created OpenGL core profile context: requested={}.{} (debug={}), actual={}.{} ({})",
                    major,
                    minor,
                    lwjglDebug,
                    actualVersion.major(),
                    actualVersion.minor(),
                    actualVersionString
                );
                ForgeHooksClient.initializeWindowsInformation();
                ForgeHooksClient.setWindowStyle(this.fullscreen);
                ForgeHooksClient.initializeTaskbarAPI();
                ci.cancel();
                return;
            }
        }

        throw new LWJGLException("Failed to create an OpenGL 3.3+ core profile context", lastException);
    }

    @Unique
    private static Exception demonica$createContextValidationFailure(RuntimeException e) {
        // Keep the return type as Exception so CleanMix can still resolve the lastException frame during transformation.
        return new LWJGLException("Created context does not provide valid OpenGL 3.3+", e);
    }

    @Unique
    private static void demonica$createDisplay(PixelFormat format, ContextAttribs attribs) throws LWJGLException {
        Display.create(format, attribs);
        if (!Display.isCreated()) {
            throw new LWJGLException("Display.create returned without creating an OpenGL context");
        }
    }

    @Unique
    private static void demonica$destroyDisplayAfterFailure() {
        try {
            Display.destroy();
        } catch (RuntimeException destroyFailure) {
            demonica$LOGGER.warn("Failed to destroy an unsuccessful OpenGL context", destroyFailure);
        }
    }
}
