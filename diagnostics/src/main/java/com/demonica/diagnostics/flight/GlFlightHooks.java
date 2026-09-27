package com.demonica.diagnostics.flight;

import net.coderbot.iris.debug.GlFlight;

/** Connects the {@link GlFlight} facade to {@link GlFlightRecording}. */
public final class GlFlightHooks implements GlFlight.Hooks {
    public static final GlFlightHooks INSTANCE = new GlFlightHooks();

    private GlFlightHooks() {
    }

    @Override
    public void beginTessellatorSync() {
        GlFlightRecording.beginStreamingSync(GlFlightStreamingSource.TESSELLATOR);
    }

    @Override
    public void endTessellatorSync() {
        GlFlightRecording.endStreamingSync(GlFlightStreamingSource.TESSELLATOR);
    }

    @Override
    public void beginSwap() {
        GlFlightRecording.beginSwap();
    }

    @Override
    public void dimensionChange(String previousDimension, String currentDimension) {
        GlFlightRecording.dimensionChange(previousDimension, currentDimension);
    }

    @Override
    public void beginPipelineCreate(String dimension) {
        GlFlightRecording.beginPipelineCreate(dimension);
    }

    @Override
    public void endPipelineCreate(String dimension) {
        GlFlightRecording.endPipelineCreate(dimension);
    }

    @Override
    public void beginPipelineDestroy(String dimension) {
        GlFlightRecording.beginPipelineDestroy(dimension);
    }

    @Override
    public void endPipelineDestroy(String dimension) {
        GlFlightRecording.endPipelineDestroy(dimension);
    }
}
