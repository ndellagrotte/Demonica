package com.demonica.celeritas.options;

/**
 * Implemented on Celeritas's {@code CyclingControl} by the quarantine patch O1 (docs/celeritas/patches/O1.md), which
 * exposes the values the control cycles through; upstream exposes only their names. Read it through
 * {@link OptionControls}.
 */
public interface CyclingControlAccess {
    /** The control's own array, index-aligned with {@code getNames()}. */
    Object[] demonica$getAllowedValues();
}
