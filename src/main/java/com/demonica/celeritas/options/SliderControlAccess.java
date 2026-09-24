package com.demonica.celeritas.options;

import org.taumc.celeritas.api.options.control.ControlValueFormatter;

/**
 * Implemented on Celeritas's {@code SliderControl} by the quarantine patch O1 (docs/celeritas/patches/O1.md), which
 * exposes what upstream keeps private: the range, step and value formatter that Reese's Sodium Options draws its own
 * slider with. Read it through {@link OptionControls}.
 */
public interface SliderControlAccess {
    int demonica$getMin();

    int demonica$getMax();

    int demonica$getInterval();

    ControlValueFormatter demonica$getFormatter();
}
