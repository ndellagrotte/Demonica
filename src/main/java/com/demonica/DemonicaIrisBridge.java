package com.demonica;

import com.demonica.config.DemonicaConfig;
import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import net.coderbot.iris.debug.IrisDebugOptions;

/**
 * The switches the Iris tree reads through {@link IrisDebugOptions}. Installed by the coremod, before {@code Iris} is
 * class-initialized: {@code Iris.enabled} is a static final read from {@link #enableIris()}. Each method reads
 * Demonica's settings only when it is called, so installing the bridge loads no configuration and no Celeritas class.
 */
public final class DemonicaIrisBridge implements IrisDebugOptions.Bridge {
    @Override
    public boolean pbrDebugEnabled() {
        return DemonicaRuntimeOptions.pbrDebugEnabled();
    }

    @Override
    public boolean enableActiniumGlDebug() {
        return DemonicaRuntime.options().debug.enableGlDebug;
    }

    @Override
    public boolean enableCloudControlDebug() {
        return DemonicaRuntime.options().debug.enableCloudControlDebug;
    }

    @Override
    public boolean enableFrameGlErrorCheck() {
        return DemonicaRuntime.options().debug.enableFrameGlErrorCheck;
    }

    @Override
    public boolean enablePostRenderGlErrorCheck() {
        return DemonicaRuntime.options().debug.enablePostRenderGlErrorCheck;
    }

    @Override
    public boolean enableActiniumPerfDebug() {
        return DemonicaRuntime.options().debug.enablePerfDebug;
    }

    @Override
    public boolean enableActiniumGpuPerfDebug() {
        return DemonicaRuntime.options().debug.enableGpuPerfDebug;
    }

    @Override
    public boolean ignoreFramebufferErrors() {
        return DemonicaRuntime.options().debug.ignoreFramebufferErrors;
    }

    @Override
    public boolean enableIris() {
        return DemonicaConfig.enableIris;
    }

    @Override
    public boolean enableCeleritas() {
        return DemonicaConfig.enableCeleritas;
    }

    @Override
    public boolean defineIsIris() {
        return DemonicaConfig.defineIsIris;
    }

    @Override
    public boolean enableHardcodedCustomUniforms() {
        return DemonicaConfig.enableHardcodedCustomUniforms;
    }

    @Override
    public boolean disableF3Additions() {
        return DemonicaConfig.disableF3Additions;
    }

    @Override
    public boolean useTotalWorldTime() {
        return DemonicaConfig.useTotalWorldTime;
    }

    @Override
    public void cycleAnimationsMode() {
        ClientProxy.animationsMode.next();
    }
}
