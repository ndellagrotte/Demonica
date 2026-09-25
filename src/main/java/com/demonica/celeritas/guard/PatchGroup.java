package com.demonica.celeritas.guard;

/**
 * What the failure of a quarantine patch costs (docs/celeritas/LEDGER.md, "Groups"). When Celeritas is not the pinned
 * build and a patch's anchors moved, {@link QuarantineGuard} turns off the whole group, or only the failing mixin where
 * a group's patches do not depend on each other.
 */
public enum PatchGroup {
    /** S15. Never turned off: each of its injectors stands alone, and every one that still applies keeps fog right. */
    BASE(Gate.NEVER),
    /** Shader packs cannot draw terrain: shaders are turned off, with the reason (level L2). */
    CORE_TERRAIN(Gate.GROUP),
    /** Shader packs draw no terrain into their shadow map (level L1). */
    SHADOW(Gate.GROUP),
    /** Shader packs get no block IDs from terrain, and water is drawn as translucent terrain. */
    MESHING(Gate.GROUP),
    /** Independent patches that each degrade one feature. */
    DEGRADE(Gate.MIXIN),
    /** Mod compat on Celeritas's classes: each mixin serves its own mods. */
    COMPAT(Gate.MIXIN);

    /** What the guard turns off when one of the group's anchors is missing. */
    public enum Gate {
        /** Nothing: Mixin skips the injectors whose targets are gone, and the rest still apply. */
        NEVER,
        /** Every mixin of the group. */
        GROUP,
        /** The mixin whose anchor is missing. */
        MIXIN
    }

    private final Gate gate;

    PatchGroup(Gate gate) {
        this.gate = gate;
    }

    public Gate gate() {
        return this.gate;
    }
}
