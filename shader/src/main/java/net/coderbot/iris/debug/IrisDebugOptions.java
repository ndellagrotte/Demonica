package net.coderbot.iris.debug;

import org.jetbrains.annotations.Nullable;

/**
 * Switches the shader pipeline reads from Demonica: whether Iris and its Celeritas integration run, pack-facing
 * options, and why shaders are unavailable. The diagnostics' own switches are read by the diagnostics jar.
 *
 * <p>Read live from a bridge registered by the host mod (Demonica), so
 * in-game config changes take effect without a restart and the shader module
 * does not depend on the host's runtime.
 */
public final class IrisDebugOptions {

    /** Bridge implemented by the host mod; reads its own config live. */
    public interface Bridge {
        boolean ignoreFramebufferErrors();
        boolean enableIris();
        boolean enableCeleritas();
        boolean defineIsIris();
        boolean enableHardcodedCustomUniforms();
        boolean disableF3Additions();
        boolean useTotalWorldTime();
        void cycleAnimationsMode();

        /** Why shader packs cannot be used in this game, when the host turned the pipeline off; null otherwise. */
        @Nullable String shadersUnavailableReason();
    }

    private static volatile Bridge bridge;

    private IrisDebugOptions() {
    }

    public static void setBridge(Bridge newBridge) {
        bridge = newBridge;
    }

    public static boolean ignoreFramebufferErrors() {
        Bridge b = bridge;
        return b != null && b.ignoreFramebufferErrors();
    }

    public static boolean enableIris() {
        Bridge b = bridge;
        // Default matches ActiniumConfig's initial value; read once at Iris class load.
        return b != null ? b.enableIris() : true;
    }

    public static boolean enableCeleritas() {
        Bridge b = bridge;
        return b != null ? b.enableCeleritas() : true;
    }

    public static boolean defineIsIris() {
        Bridge b = bridge;
        return b != null ? b.defineIsIris() : true;
    }

    public static boolean enableHardcodedCustomUniforms() {
        Bridge b = bridge;
        return b != null && b.enableHardcodedCustomUniforms();
    }

    public static boolean disableF3Additions() {
        Bridge b = bridge;
        return b != null && b.disableF3Additions();
    }

    public static boolean useTotalWorldTime() {
        Bridge b = bridge;
        return b != null && b.useTotalWorldTime();
    }

    public static void cycleAnimationsMode() {
        Bridge b = bridge;
        if (b != null) {
            b.cycleAnimationsMode();
        }
    }

    public static @Nullable String shadersUnavailableReason() {
        Bridge b = bridge;
        return b != null ? b.shadersUnavailableReason() : null;
    }
}
