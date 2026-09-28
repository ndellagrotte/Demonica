package com.demonica.mixin.core.startup;

import com.demonica.config.OpenGlProfile;
import com.demonica.debug.CoreProfileContextAttributes;
import com.demonica.debug.DemonicaStartupConfig;
import com.demonica.debug.OpenGlContextReport;
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
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates the display on the configured OpenGL profile ({@code advanced.opengl_profile}).
 *
 * <p>Compatibility (the default on Windows and Linux): Cleanroom's own {@code createDisplay} runs untouched and
 * creates the context {@code forge_early.cfg} asks for, a 4.6 compatibility context by default. Demonica only raises
 * the debug flag for the LWJGL debug option beforehand, and afterwards checks for OpenGL 3.3 and records the context.
 *
 * <p>Core (the default on macOS, whose compatibility profile stops at OpenGL 2.1): Demonica replaces
 * {@code createDisplay} and walks down from 4.6 (4.1 on macOS) to 3.3 until a core context is created.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftDisplay {
    @Unique
    private static final Logger demonica$LOGGER = LogManager.getLogger("Demonica");

    @Unique
    private static boolean demonica$debugContextRaised;

    @Shadow
    private boolean fullscreen;

    @Inject(method = "createDisplay", at = @At("HEAD"), cancellable = true)
    private void demonica$onCreateDisplay(CallbackInfo ci) throws LWJGLException {
        OpenGlProfile requested = demonica$requestedProfile();
        if (requested != OpenGlProfile.CORE) {
            boolean lwjglDebug = DemonicaStartupConfig.enableLwjglDebug();
            demonica$debugContextRaised = CoreProfileContextAttributes.raiseForgeEarlyDebugContext(lwjglDebug);
            demonica$LOGGER.info(
                "Requesting Cleanroom's OpenGL context (configured={}, profile={}, forge_early.cfg={}.{} compat={}, debug={})",
                DemonicaStartupConfig.openGlProfile().id(),
                requested.id(),
                ForgeEarlyConfig.OPENGL_VERSION_MAJOR,
                ForgeEarlyConfig.OPENGL_VERSION_MINOR,
                ForgeEarlyConfig.OPENGL_COMPAT_PROFILE,
                ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT
            );
            return;
        }

        final int originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
        final int originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
        final boolean originalDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
        try {
            demonica$createCoreProfileDisplayInner(ci);
        } finally {
            // Don't leave the core-profile request persisted in forge_early.cfg: without Demonica,
            // Cleanroom's default path expects the compatibility profile and fails to create a context.
            // LWJGLXX syncs the request to the file while creating the context, so restore the fields
            // and sync them back (see CoreProfileContextAttributes.persistForgeEarlyConfig).
            CoreProfileContextAttributes.restoreForgeEarlyCompatProfile(originalMajor, originalMinor, originalDebug);
            CoreProfileContextAttributes.persistForgeEarlyConfig();
        }
    }

    /**
     * Checks the context Cleanroom created. TAIL is the method's own last return, not the one the HEAD injector's
     * cancel inserts, so the core path never gets here; the guard only makes that explicit.
     */
    @Inject(method = "createDisplay", at = @At("TAIL"))
    private void demonica$checkCleanroomDisplay(CallbackInfo ci) throws LWJGLException {
        OpenGlProfile requested = demonica$requestedProfile();
        if (requested == OpenGlProfile.CORE) {
            return;
        }
        CoreProfileContextAttributes.restoreForgeEarlyDebugContext(demonica$debugContextRaised);
        demonica$debugContextRaised = false;

        String versionString = GL11.glGetString(GL11.GL_VERSION);
        OpenGlVersion version;
        try {
            version = OpenGlVersion.parse(versionString);
        } catch (RuntimeException e) {
            throw new LWJGLException("Cleanroom's OpenGL context reports an unusable GL_VERSION: " + versionString, e);
        }
        if (!version.isAtLeast(3, 3)) {
            throw new LWJGLException("Demonica needs OpenGL 3.3 or newer, but the compatibility context Cleanroom created reports "
                + versionString + "; set advanced.opengl_profile to core");
        }
        demonica$recordContext(requested, version, versionString, ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT || DemonicaStartupConfig.enableLwjglDebug());
    }

    @Unique
    private static OpenGlProfile demonica$requestedProfile() {
        return DemonicaStartupConfig.openGlProfile().forPlatform(LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX);
    }

    /** Logs and records the context created; its profile comes from {@code GL_CONTEXT_PROFILE_MASK}. */
    @Unique
    private static void demonica$recordContext(OpenGlProfile requested, OpenGlVersion version, String versionString, boolean debug) {
        int mask = GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK);
        String actual = (mask & GL32.GL_CONTEXT_CORE_PROFILE_BIT) != 0 ? "core"
            : (mask & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0 ? "compatibility"
            : "unknown";
        OpenGlProfile configured = DemonicaStartupConfig.openGlProfile();
        demonica$LOGGER.info(
            "Created OpenGL {} profile context: configured={}, requested={} (debug={}), actual={}.{} ({})",
            actual,
            configured.id(),
            requested.id(),
            debug,
            version.major(),
            version.minor(),
            versionString
        );
        if (!requested.id().equals(actual)) {
            demonica$LOGGER.warn("Requested a {} profile context but the driver created a {} one", requested.id(), actual);
        }
        OpenGlContextReport.record(new OpenGlContextReport(configured, requested, actual, version, versionString));
    }

    @Unique
    private void demonica$createCoreProfileDisplayInner(CallbackInfo ci) throws LWJGLException {
        Display.setResizable(true);
        Display.setTitle("Cleanroom");

        PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(8);
        int maxMajor = 4;
        boolean macos = LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX;
        int maxMinor = macos ? 1 : 6;
        boolean lwjglDebug = DemonicaStartupConfig.enableLwjglDebug();
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

                demonica$recordContext(OpenGlProfile.CORE, actualVersion, actualVersionString, lwjglDebug);
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
