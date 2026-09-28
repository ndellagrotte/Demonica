package com.demonica.celeritas.guard;

/**
 * What a quarantine patch provides (docs/celeritas/LEDGER.md, "Groups"). On a Celeritas that is not the pinned build,
 * {@link QuarantineGuard} applies only the {@link #BASE} group.
 */
public enum PatchGroup {
    /** S15: terrain fog. The only group that still applies on a Celeritas that is not the pin. */
    BASE,
    /** Shader packs draw terrain. */
    CORE_TERRAIN,
    /** Shader packs draw terrain into their shadow map. */
    SHADOW,
    /** Shader packs get block IDs from terrain, and water is meshed into the pack's water pass. */
    MESHING,
    /** Independent patches that each serve one feature. */
    DEGRADE,
    /** Mod compat on Celeritas's classes: each mixin serves its own mods. */
    COMPAT
}
