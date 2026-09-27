package com.gtnewhorizons.angelica.glsm.hooks;

/**
 * Receives allocation-free GPU command breadcrumbs immediately before native submission.
 */
public interface GpuCommandRecorder {
    /**
     * Records one command with the GLSM state active at submission time.
     *
     * @param type stable command type
     * @param phase submission boundary represented by this breadcrumb
     * @param program active GLSL program requested through GLSM
     * @param drawFramebuffer active draw framebuffer
     * @param operand0 command-specific first operand
     * @param operand1 command-specific second operand
     */
    void record(
        GpuCommandType type,
        GpuCommandPhase phase,
        int program,
        int drawFramebuffer,
        int operand0,
        int operand1
    );

    /**
     * Records a checkpoint lifecycle event without performing any native operation.
     *
     * @param type checkpoint lifecycle type
     * @param checkpointId monotonically increasing per-context checkpoint id
     * @param commandCode command or pass-boundary code associated with the fence
     */
    void checkpoint(GpuCheckpointType type, long checkpointId, int commandCode);

    /**
     * Polls prior GPU fences without waiting and inserts a completion checkpoint for a submitted command, reporting
     * both through {@link #checkpoint}. The diagnostics jar's flight recorder implements it with its checkpoint tracker.
     *
     * @param commandCode command or pass-boundary code associated with the checkpoint
     */
    default void insertCheckpoint(int commandCode) {
    }
}
