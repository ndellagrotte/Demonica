package me.flashyreese.mods.reeses_sodium_options.client.gui.option;

import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.api.options.structure.Option;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Declared default values, for the reset-to-default button. Celeritas's option API has no notion of a default
 * (Actinium's fork added one to {@code OptionImpl}), so pages that know their defaults declare them here. An option
 * without a declared default resets to its applied value, as RSO's undo does.
 *
 * <p>Options are held weakly: Celeritas builds its pages anew for every video settings screen, and so do the
 * construction-event listeners that declare defaults.
 */
public final class OptionDefaults {
    private static final Map<Option<?>, Object> DEFAULTS = Collections.synchronizedMap(new WeakHashMap<>());

    private OptionDefaults() {
    }

    /** Declares {@code defaultValue} as the option's default and returns the option. */
    public static <T, O extends Option<T>> O declare(O option, T defaultValue) {
        DEFAULTS.put(Objects.requireNonNull(option, "option"), Objects.requireNonNull(defaultValue, "defaultValue"));
        return option;
    }

    /** The declared default, or null when the option declares none. */
    public static @Nullable Object get(Option<?> option) {
        return DEFAULTS.get(option);
    }
}
