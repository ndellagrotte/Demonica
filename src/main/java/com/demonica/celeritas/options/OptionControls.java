package com.demonica.celeritas.options;

import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;

/**
 * What Reese's Sodium Options reads from Celeritas's option controls beyond their public API: a slider's range, step
 * and formatter, and the values a cycling control offers. Both come from the quarantine patch O1; without it RSO cannot
 * draw these rows, and Video Settings keeps Celeritas's own screen ({@link #available()}).
 */
public final class OptionControls {
    private OptionControls() {
    }

    /** Whether O1 is applied to both controls. Loads the two control classes, so call it only once a screen opens. */
    public static boolean available() {
        return SliderControlAccess.class.isAssignableFrom(SliderControl.class)
            && CyclingControlAccess.class.isAssignableFrom(CyclingControl.class);
    }

    public static int sliderMin(SliderControl control) {
        return access(control).demonica$getMin();
    }

    public static int sliderMax(SliderControl control) {
        return access(control).demonica$getMax();
    }

    public static int sliderInterval(SliderControl control) {
        return access(control).demonica$getInterval();
    }

    public static ControlValueFormatter sliderFormatter(SliderControl control) {
        return access(control).demonica$getFormatter();
    }

    /** A copy of the values the control cycles through, index-aligned with its names. */
    public static Object[] allowedValues(CyclingControl<?> control) {
        return ((CyclingControlAccess) control).demonica$getAllowedValues().clone();
    }

    private static SliderControlAccess access(SliderControl control) {
        return (SliderControlAccess) control;
    }
}
