package com.demonica.celeritas.api.shader;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

import java.util.Map;

/**
 * What a terrain pass is for, and whether it writes depth. Actinium's Celeritas fork added {@code semantic()} and
 * {@code writesDepth()} to {@link TerrainRenderPass}; upstream has neither, so Demonica's pass builder records both
 * in the pass's {@code extraDefines} and this class reads them back. {@code extraDefines} takes part in
 * {@code TerrainRenderPass.equals}, which also keeps the water pass distinct from the translucent one.
 *
 * <p>Passes built by upstream carry no such define; their semantic is derived from the legacy flags, exactly as
 * Actinium's fork did for passes that did not set one.
 */
public final class PassSemantics {
    /** {@code SOLID}, {@code CUTOUT}, {@code TRANSLUCENT} or {@code WATER}. */
    public static final String SEMANTIC_DEFINE = "DEMONICA_PASS_SEMANTIC";
    /** {@code 1} if the pass writes depth, {@code 0} if it does not. */
    public static final String WRITES_DEPTH_DEFINE = "DEMONICA_WRITES_DEPTH";

    /** The fixed-function terrain behaviour a pass stands for. */
    public enum Semantic {
        SOLID,
        CUTOUT,
        TRANSLUCENT,
        WATER;

        static Semantic fromLegacyFlags(boolean useReverseOrder, boolean fragmentDiscard) {
            if (useReverseOrder) {
                return TRANSLUCENT;
            }
            return fragmentDiscard ? CUTOUT : SOLID;
        }
    }

    private PassSemantics() {
    }

    public static Semantic semantic(TerrainRenderPass pass) {
        String value = pass.extraDefines().get(SEMANTIC_DEFINE);
        if (value != null) {
            try {
                return Semantic.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Not one of ours; fall back to the flags.
            }
        }
        return Semantic.fromLegacyFlags(pass.isReverseOrder(), pass.supportsFragmentDiscard());
    }

    public static boolean writesDepth(TerrainRenderPass pass) {
        String value = pass.extraDefines().get(WRITES_DEPTH_DEFINE);
        if (value != null) {
            return !value.equals("0");
        }
        return !pass.isReverseOrder();
    }

    /** Whether the pass was built by Demonica's pass builder, rather than upstream's. */
    public static boolean isTagged(TerrainRenderPass pass) {
        return pass.extraDefines().containsKey(SEMANTIC_DEFINE);
    }

    /** The defines that tag a pass; the builder adds them to {@code TerrainRenderPass.builder().extraDefines(...)}. */
    public static Map<String, String> defines(Semantic semantic, boolean writesDepth) {
        return Map.of(SEMANTIC_DEFINE, semantic.name(), WRITES_DEPTH_DEFINE, writesDepth ? "1" : "0");
    }
}
