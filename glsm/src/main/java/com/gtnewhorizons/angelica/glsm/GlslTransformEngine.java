package com.gtnewhorizons.angelica.glsm;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The GLSL transform engines and the system property that selects one (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
 * 3.2). Iris's {@code TransformPatcher} and GLSM's {@link CompatShaderTransformer} both resolve their engine here, so
 * they agree on the property, its values, its default and the fallback for an unknown value. It lives in {@code glsm}
 * because {@code shader} depends on {@code glsm} and not the other way round. Step 11 removes the switch with TauMC.
 */
public enum GlslTransformEngine {
    /** TauMC's glsl-transformation-lib: the old Iris engine ({@code ShaderTransformer}) and GLSM's old parse. */
    TAUMC("taumc"),
    /** douira's glsl-transformer: {@code AstShaderTransformer} and GLSM's parse on {@code ShaderAst}. */
    DOUIRA("douira");

    /** The system property that selects the engine: {@code taumc} or {@code douira}. */
    public static final String PROPERTY = "demonica.glsl.engine";

    /**
     * The engine used when {@value #PROPERTY} is unset or unknown: glsl-transformer since Step 8 (exit point A of the
     * adoption plan). {@code -Ddemonica.glsl.engine=taumc} selects the old engine until Step 11 removes it.
     */
    public static final GlslTransformEngine DEFAULT = DOUIRA;

    /** The property value that names this engine. */
    public final String id;

    GlslTransformEngine(String id) {
        this.id = id;
    }

    /** The engine {@code value} names (trimmed, case-insensitive), or null for null or an unknown value. */
    public static GlslTransformEngine byId(String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (GlslTransformEngine engine : values()) {
            if (engine.id.equals(normalized)) {
                return engine;
            }
        }
        return null;
    }

    /**
     * Resolves {@code value} as the property's value: {@link #DEFAULT} when it is null, the engine it names, or, for an
     * unknown value, {@link #DEFAULT} after passing the normalized value to {@code unknownValue} (the caller logs it).
     */
    public static GlslTransformEngine resolve(String value, Consumer<String> unknownValue) {
        if (value == null) {
            return DEFAULT;
        }
        final GlslTransformEngine engine = byId(value);
        if (engine == null) {
            unknownValue.accept(value.trim().toLowerCase(Locale.ROOT));
            return DEFAULT;
        }
        return engine;
    }

    /** {@link #resolve(String, Consumer)} of the system property {@value #PROPERTY}. */
    public static GlslTransformEngine fromSystemProperty(Consumer<String> unknownValue) {
        return resolve(System.getProperty(PROPERTY), unknownValue);
    }
}
