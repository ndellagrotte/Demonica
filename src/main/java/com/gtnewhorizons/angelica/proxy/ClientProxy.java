package com.gtnewhorizons.angelica.proxy;

import com.demonica.config.AnimationMode;
import com.demonica.config.DemonicaOptions;
import com.demonica.config.ManagedEnum;
import com.demonica.runtime.DemonicaRuntime;

public final class ClientProxy {
    public static final ManagedEnum<AnimationMode> animationsMode = new ManagedEnum<>(AnimationMode.VISIBLE_ONLY);

    private ClientProxy() {
    }

    public static DemonicaOptions options() {
        return DemonicaRuntime.options();
    }
}
