package com.demonica.diagnostics;

import com.demonica.celeritas.api.debug.RenderDebugHooksHolder;
import com.demonica.diagnostics.flight.GlFlightGpuCommandRecorder;
import com.demonica.diagnostics.flight.GlFlightHooks;
import com.demonica.diagnostics.flight.GlFlightRecording;
import com.demonica.diagnostics.iris.IrisGlDebugHooks;
import com.demonica.diagnostics.iris.IrisRenderDebugHooks;
import com.demonica.diagnostics.iris.PbrDebugHooks;
import com.demonica.diagnostics.iris.RegressionDebugHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GpuCommandRecorder;
import net.coderbot.iris.debug.DiagnosticsProvider;
import net.coderbot.iris.debug.GlFlight;
import net.coderbot.iris.debug.IrisGlDebug;
import net.coderbot.iris.debug.PBRDebug;
import net.coderbot.iris.debug.ShaderRegressionDebug;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** The diagnostics jar's side of {@link net.coderbot.iris.debug.Diagnostics}. */
public final class DemonicaDiagnosticsProvider implements DiagnosticsProvider {
    @Override
    public boolean install() {
        if (!DiagnosticsCoremod.usable()) {
            return false;
        }
        IrisGlDebug.install(IrisGlDebugHooks.INSTANCE);
        GlFlight.install(GlFlightHooks.INSTANCE);
        PBRDebug.install(PbrDebugHooks.INSTANCE);
        ShaderRegressionDebug.install(RegressionDebugHooks.INSTANCE);
        RenderDebugHooksHolder.setHooks(IrisRenderDebugHooks.INSTANCE);
        return true;
    }

    @Override
    public @Nullable GpuCommandRecorder gpuCommandRecorder() {
        return GlFlightRecording.isEnabled() ? GlFlightGpuCommandRecorder.INSTANCE : null;
    }

    @Override
    public String version() {
        return Objects.requireNonNullElse(DiagnosticsCoremod.jarVersion(DemonicaDiagnosticsProvider.class), "dev");
    }
}
