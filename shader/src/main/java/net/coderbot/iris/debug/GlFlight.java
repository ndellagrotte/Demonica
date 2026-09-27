package net.coderbot.iris.debug;

/**
 * The GL flight recorder's markers in the pipeline and the frame loop. Every method is a no-op until the diagnostics
 * jar installs its implementation ({@link Diagnostics}), com.demonica.diagnostics.flight.GlFlightRecording, which
 * records only with {@code -Ddemonica.glFlightRecorder=crash}.
 */
public final class GlFlight {
    /** The implementation's side. */
    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void beginTessellatorSync() {
        }

        default void endTessellatorSync() {
        }

        default void beginSwap() {
        }

        default void dimensionChange(String previousDimension, String currentDimension) {
        }

        default void beginPipelineCreate(String dimension) {
        }

        default void endPipelineCreate(String dimension) {
        }

        default void beginPipelineDestroy(String dimension) {
        }

        default void endPipelineDestroy(String dimension) {
        }
    }

    private static Hooks hooks = Hooks.NOOP;

    private GlFlight() {
    }

    /** Installs the implementation. Called once, on the render thread, by the diagnostics jar. */
    public static void install(Hooks implementation) {
        hooks = implementation != null ? implementation : Hooks.NOOP;
    }

    /** Before the tessellator's streaming buffers are synchronized at the end of a frame. */
    public static void beginTessellatorSync() {
        hooks.beginTessellatorSync();
    }

    public static void endTessellatorSync() {
        hooks.endTessellatorSync();
    }

    /** Before the frame's buffer swap. */
    public static void beginSwap() {
        hooks.beginSwap();
    }

    public static void dimensionChange(String previousDimension, String currentDimension) {
        hooks.dimensionChange(previousDimension, currentDimension);
    }

    public static void beginPipelineCreate(String dimension) {
        hooks.beginPipelineCreate(dimension);
    }

    public static void endPipelineCreate(String dimension) {
        hooks.endPipelineCreate(dimension);
    }

    public static void beginPipelineDestroy(String dimension) {
        hooks.beginPipelineDestroy(dimension);
    }

    public static void endPipelineDestroy(String dimension) {
        hooks.endPipelineDestroy(dimension);
    }
}
