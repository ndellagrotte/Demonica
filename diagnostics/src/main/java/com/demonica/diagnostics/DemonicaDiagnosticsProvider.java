package com.demonica.diagnostics;

import com.gtnewhorizons.angelica.glsm.hooks.GpuCommandRecorder;
import net.coderbot.iris.debug.DiagnosticsProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** The diagnostics jar's side of {@link net.coderbot.iris.debug.Diagnostics}. */
public final class DemonicaDiagnosticsProvider implements DiagnosticsProvider {
    @Override
    public boolean install() {
        return DiagnosticsCoremod.usable();
    }

    @Override
    public @Nullable GpuCommandRecorder gpuCommandRecorder() {
        return null;
    }

    @Override
    public String version() {
        return Objects.requireNonNullElse(DiagnosticsCoremod.jarVersion(DemonicaDiagnosticsProvider.class), "dev");
    }
}
