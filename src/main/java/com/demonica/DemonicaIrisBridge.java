package com.demonica;

import com.demonica.celeritas.guard.QuarantineGuard;
import com.demonica.config.DemonicaConfig;
import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import net.coderbot.iris.debug.IrisDebugOptions;
import org.jetbrains.annotations.Nullable;

/**
 * The switches the Iris tree reads through {@link IrisDebugOptions}. Installed by the coremod, before {@code Iris} is
 * class-initialized: {@code Iris.enabled} is a static final read from {@link #enableIris()}. Each method reads
 * Demonica's settings only when it is called, so installing the bridge loads no configuration and no Celeritas class.
 * Shaders are off when the installed Celeritas is not the build this Demonica was made for ({@link QuarantineGuard}).
 */
public final class DemonicaIrisBridge implements IrisDebugOptions.Bridge {
    @Override
    public boolean ignoreFramebufferErrors() {
        return DemonicaRuntime.options().debug.ignoreFramebufferErrors;
    }

    @Override
    public boolean enableIris() {
        return DemonicaConfig.enableIris && QuarantineGuard.current().shadersAllowed();
    }

    @Override
    public boolean enableCeleritas() {
        return DemonicaConfig.enableCeleritas && QuarantineGuard.current().shadersAllowed();
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

    @Override
    public @Nullable String shadersUnavailableReason() {
        return QuarantineGuard.current().shaderReason();
    }
}
