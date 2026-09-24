package com.demonica.gui.options;

import com.demonica.config.DemonicaOptions;
import com.demonica.runtime.DemonicaRuntime;
import org.taumc.celeritas.api.options.structure.OptionStorage;

/** Demonica's settings ({@code config/demonica-options.json}) as a storage of Celeritas's option API. */
public final class DemonicaOptionsStorage implements OptionStorage<DemonicaOptions> {
    @Override
    public DemonicaOptions getData() {
        return DemonicaRuntime.options();
    }

    @Override
    public void save() {
        DemonicaOptions options = this.getData();
        if (options.isReadOnly()) {
            // Loading failed at startup and Demonica runs on defaults (DemonicaRuntime); changes hold until the game closes.
            DemonicaRuntime.logger().warn("Not saving {}: it could not be loaded at startup", options.getFileName());
            return;
        }
        options.save();
    }
}
