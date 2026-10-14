package com.demonica.debug;

import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.common.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Writes the OpenGL context request that Cleanroom reads from {@code ForgeEarlyConfig} during early Minecraft startup,
 * and puts the user's values back afterwards.
 *
 * <p>Cleanroom 0.7.4 creates the SDL window and its OpenGL context in the patched {@code Minecraft.init}, before
 * {@code createDisplay}: the version, profile and debug flag are read from {@code ForgeEarlyConfig.OPENGL_*} when
 * {@code Window.GLConfig} is built there. lwjgly's {@code Display.create} only validates that existing context, so
 * these fields are the only way to choose the context. Keeping this logic outside every Mixin configuration package
 * allows transformed classes to load it normally.
 */
public final class CoreProfileContextAttributes {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");

    /**
     * Prevents instantiation; every helper is static.
     */
    private CoreProfileContextAttributes() {
    }

    /**
     * Requests a core-profile context from Cleanroom's window creation.
     *
     * <p>Must run before {@code Window.GLConfig} is built in {@code Minecraft.init}. Cleanroom walks down from the
     * requested version itself, and SDL picks the macOS core profile from the profile mask alone, so no
     * forward-compatible flag is needed. Without this override macOS, whose compatibility profile stops at
     * OpenGL 2.1, gets a legacy context.</p>
     *
     * @param major requested OpenGL major version
     * @param minor requested OpenGL minor version
     * @param debug whether to request a debug context
     */
    public static void applyForgeEarlyCoreProfile(int major, int minor, boolean debug) {
        ForgeEarlyConfig.OPENGL_VERSION_MAJOR = major;
        ForgeEarlyConfig.OPENGL_VERSION_MINOR = minor;
        ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = false;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = debug;
    }

    /**
     * Restores the Forge early OpenGL config after the core-profile context was created.
     *
     * <p>Leaving the core-profile request in place would break a later launch without Demonica (Cleanroom's own
     * startup code expects the compatibility profile once it is written to {@code forge_early.cfg}), so every field
     * goes back to the value it had before {@link #applyForgeEarlyCoreProfile}. The compatibility flag is restored to
     * its original value, not forced on: it is user-editable in Forge's config GUI, and a user who chose the core
     * profile there keeps that choice.</p>
     *
     * @param originalMajor OpenGL major version before Demonica's request
     * @param originalMinor OpenGL minor version before Demonica's request
     * @param originalCompatProfile compatibility-profile flag before Demonica's request
     * @param originalDebug debug-context flag before Demonica's request
     */
    public static void restoreForgeEarlyProfile(
        int originalMajor,
        int originalMinor,
        boolean originalCompatProfile,
        boolean originalDebug
    ) {
        ForgeEarlyConfig.OPENGL_VERSION_MAJOR = originalMajor;
        ForgeEarlyConfig.OPENGL_VERSION_MINOR = originalMinor;
        ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = originalCompatProfile;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = originalDebug;
    }

    /**
     * Asks Cleanroom's own context creation for a debug context when the LWJGL debug option is on.
     *
     * <p>With the compatibility profile Demonica leaves the context request to Cleanroom, which reads
     * {@code OPENGL_DEBUG_CONTEXT} from {@code ForgeEarlyConfig} when it builds the window in {@code Minecraft.init}.
     * Raising it for this one creation keeps the "LWJGL Debug Context" option working without Demonica owning the
     * file.
     *
     * @param lwjglDebug whether the debug context was requested at startup
     * @return whether the flag was raised here, from {@code false} to {@code true}
     */
    public static boolean raiseForgeEarlyDebugContext(boolean lwjglDebug) {
        if (!lwjglDebug || ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT) {
            return false;
        }
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = true;
        return true;
    }

    /**
     * Lowers the debug flag that {@link #raiseForgeEarlyDebugContext} raised, and writes the restored state back to
     * {@code forge_early.cfg} (see {@link #persistForgeEarlyConfig}).
     *
     * @param raised what {@link #raiseForgeEarlyDebugContext} returned
     */
    public static void restoreForgeEarlyDebugContext(boolean raised) {
        if (!raised) {
            return;
        }
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        persistForgeEarlyConfig();
    }

    /**
     * Writes the restored fields to {@code forge_early.cfg}, as insurance that the file never keeps Demonica's
     * temporary request.
     *
     * <p>Cleanroom 0.6's LWJGLXX called {@code ConfigManager.sync(ForgeEarlyConfig.class)} while creating the
     * context, which wrote the in-memory core-profile request to the file before it could be restored; a later launch
     * without Demonica then created a core context whose fixed-pipeline startup code (SplashProgress texture setup)
     * failed with {@code GL_INVALID_ENUM} and aborted the JVM on the first {@code glAlphaFunc}. Cleanroom 0.7.4 does
     * not sync during window creation (the only syncs are its config GUI and Forge's preInit, which runs after
     * {@code createDisplay}, by which time the fields are restored), so this sync only guarantees the file matches
     * the restored state should a sync ever slip in between.</p>
     */
    public static void persistForgeEarlyConfig() {
        try {
            ConfigManager.sync(ForgeEarlyConfig.class);
        } catch (RuntimeException syncFailure) {
            // The in-memory state is already restored and this run is unaffected; only the file
            // could keep Demonica's request (a core profile would break the next launch without Demonica).
            LOGGER.error("Failed to persist the restored OpenGL context settings to forge_early.cfg", syncFailure);
        }
    }
}
