package net.coderbot.iris.debug;

import com.gtnewhorizons.angelica.glsm.hooks.GpuCommandRecorder;
import org.jetbrains.annotations.Nullable;

/**
 * The diagnostics jar (Demonica-diagnostics-&lt;version&gt;.jar), as a service: {@link Diagnostics} finds its one
 * implementation with {@link java.util.ServiceLoader}. The mod jar has none, and every diagnostics facade stays a
 * no-op without it.
 */
public interface DiagnosticsProvider {
    /**
     * Installs the diagnostics behind every facade. Called once, on the render thread, before GLSM is initialized.
     *
     * @return false when the diagnostics must stay off (Demonica inactive, or another version of Demonica)
     */
    boolean install();

    /** The recorder GLSM is initialized with, or null. */
    @Nullable GpuCommandRecorder gpuCommandRecorder();

    /** The diagnostics jar's version, for the log. */
    String version();
}
