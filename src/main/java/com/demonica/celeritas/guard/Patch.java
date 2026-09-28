package com.demonica.celeritas.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The ledger patches a quarantine mixin carries (docs/celeritas/LEDGER.md) and their group. {@link QuarantinePlugin}
 * reads the group from the class file, to keep {@link PatchGroup#BASE} applying on a Celeritas that is not the pin;
 * QuarantineLedgerTest checks the ids and the group against the ledger.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Patch {
    /** The ledger ids, such as {@code "S5"}. */
    String[] value();

    PatchGroup group();
}
