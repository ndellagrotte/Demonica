package com.demonica.mixin.core.startup;

import com.demonica.config.OpenGlProfile;
import com.demonica.debug.CoreProfileContextAttributes;
import com.demonica.debug.DemonicaStartupConfig;
import com.demonica.debug.OpenGlContextReport;
import com.demonica.debug.OpenGlVersion;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Requests the OpenGL context on the configured profile ({@code advanced.opengl_profile}).
 *
 * <p>Cleanroom 0.7.4 creates the SDL window and its context in {@code Minecraft.init}, from
 * {@code ForgeEarlyConfig.OPENGL_*}, before {@code createDisplay}; lwjgly's {@code Display.create} in
 * {@code createDisplay} only validates that context. So Demonica writes its request into those fields just before
 * {@code SDL.createWindow()}, puts the user's values back at {@code createDisplay} HEAD, and lets Cleanroom's own
 * {@code createDisplay} run for both profiles (a second window would be refused; Cleanroom walks down the versions
 * itself).
 *
 * <p>Compatibility (the default on Windows and Linux): the user's {@code forge_early.cfg} request stands, a 4.6
 * compatibility context by default. Demonica only raises the debug flag for the LWJGL debug option.
 *
 * <p>Core (the default on macOS, whose compatibility profile stops at OpenGL 2.1): Demonica requests a 4.6 (4.1 on
 * macOS) core context; Cleanroom walks down from there.
 *
 * <p>Both: afterwards Demonica checks for OpenGL 3.3 and records the context.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftDisplay {
    @Unique
    private static final Logger demonica$LOGGER = LogManager.getLogger("Demonica");

    /** Whether the core-profile request replaced the fields below, which createDisplay HEAD puts back. */
    @Unique
    private static boolean demonica$coreProfileApplied;
    @Unique
    private static int demonica$originalMajor;
    @Unique
    private static int demonica$originalMinor;
    @Unique
    private static boolean demonica$originalCompatProfile;
    @Unique
    private static boolean demonica$originalDebug;

    /** Whether the compatibility path raised the debug flag, which createDisplay HEAD lowers again. */
    @Unique
    private static boolean demonica$debugContextRaised;

    /** The profile and debug flag actually requested from Cleanroom, for the context record. */
    @Unique
    private static OpenGlProfile demonica$requested;
    @Unique
    private static boolean demonica$requestedDebug;

    /**
     * Writes Demonica's request into {@code ForgeEarlyConfig} right before Cleanroom builds the window: its
     * {@code Window.GLConfig} reads the fields when {@code SDL.createWindow()...openGL()} runs.
     */
    @Inject(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/client/sdl/SDL;createWindow()Lcom/cleanroommc/client/sdl/Window$Builder;",
            remap = false
        )
    )
    private void demonica$requestContext(CallbackInfo ci) {
        OpenGlProfile requested = demonica$requestedProfile();
        boolean lwjglDebug = DemonicaStartupConfig.enableLwjglDebug();
        demonica$requested = requested;
        if (requested == OpenGlProfile.CORE) {
            demonica$originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
            demonica$originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
            demonica$originalCompatProfile = ForgeEarlyConfig.OPENGL_COMPAT_PROFILE;
            demonica$originalDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
            // Keep a debug context the user asked for in forge_early.cfg as well as the LWJGL debug option's.
            boolean debug = lwjglDebug || demonica$originalDebug;
            boolean macos = LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX;
            CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, macos ? 1 : 6, debug);
            demonica$coreProfileApplied = true;
        } else {
            demonica$debugContextRaised = CoreProfileContextAttributes.raiseForgeEarlyDebugContext(lwjglDebug);
        }
        demonica$requestedDebug = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
        demonica$LOGGER.info(
            "Requesting Cleanroom's OpenGL context (configured={}, profile={}, forge_early.cfg={}.{} compat={}, debug={})",
            DemonicaStartupConfig.openGlProfile().id(),
            requested.id(),
            ForgeEarlyConfig.OPENGL_VERSION_MAJOR,
            ForgeEarlyConfig.OPENGL_VERSION_MINOR,
            ForgeEarlyConfig.OPENGL_COMPAT_PROFILE,
            ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT
        );
    }

    /**
     * The window and context exist by now; put the user's {@code ForgeEarlyConfig} values back before anything can
     * write them to {@code forge_early.cfg}.
     */
    @Inject(method = "createDisplay", at = @At("HEAD"))
    private void demonica$restoreForgeEarlyConfig(CallbackInfo ci) {
        if (demonica$coreProfileApplied) {
            demonica$coreProfileApplied = false;
            // Without Demonica, a core-profile request left in forge_early.cfg breaks Cleanroom's startup.
            CoreProfileContextAttributes.restoreForgeEarlyProfile(
                demonica$originalMajor,
                demonica$originalMinor,
                demonica$originalCompatProfile,
                demonica$originalDebug
            );
            CoreProfileContextAttributes.persistForgeEarlyConfig();
        } else {
            CoreProfileContextAttributes.restoreForgeEarlyDebugContext(demonica$debugContextRaised);
            demonica$debugContextRaised = false;
        }
    }

    /** Checks the context Cleanroom created, for both profiles. */
    @Inject(method = "createDisplay", at = @At("TAIL"))
    private void demonica$checkCleanroomDisplay(CallbackInfo ci) throws LWJGLException {
        OpenGlProfile requested = demonica$requested != null ? demonica$requested : demonica$requestedProfile();

        String versionString = GL11.glGetString(GL11.GL_VERSION);
        OpenGlVersion version;
        try {
            version = OpenGlVersion.parse(versionString);
        } catch (RuntimeException e) {
            throw new LWJGLException("Cleanroom's OpenGL context reports an unusable GL_VERSION: " + versionString, e);
        }
        if (!version.isAtLeast(3, 3)) {
            if (requested == OpenGlProfile.CORE) {
                throw new LWJGLException("Demonica needs OpenGL 3.3 or newer, but no OpenGL 3.3+ core profile context "
                    + "could be created; Cleanroom's context reports " + versionString);
            }
            throw new LWJGLException("Demonica needs OpenGL 3.3 or newer, but the compatibility context Cleanroom created reports "
                + versionString + "; set advanced.opengl_profile to core");
        }
        demonica$recordContext(requested, version, versionString, demonica$requestedDebug);
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
}
