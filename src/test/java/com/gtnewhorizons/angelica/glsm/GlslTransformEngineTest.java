package com.gtnewhorizons.angelica.glsm;

import net.coderbot.iris.pipeline.transform.TransformPatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The engine switch that Iris's {@link TransformPatcher} and GLSM's {@link CompatShaderTransformer} share (Step 10 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md): one property, one default, one fallback.
 */
class GlslTransformEngineTest {

    @Test
    void theDefaultIsGlslTransformer() {
        assertSame(GlslTransformEngine.DOUIRA, GlslTransformEngine.DEFAULT);
        assertEquals("demonica.glsl.engine", GlslTransformEngine.PROPERTY);
        assertEquals(GlslTransformEngine.PROPERTY, TransformPatcher.ENGINE_PROPERTY);
    }

    @Test
    void valuesResolveTrimmedAndCaseInsensitive() {
        final List<String> unknown = new ArrayList<>();
        assertSame(GlslTransformEngine.TAUMC, GlslTransformEngine.resolve("taumc", unknown::add));
        assertSame(GlslTransformEngine.DOUIRA, GlslTransformEngine.resolve(" Douira ", unknown::add));
        assertSame(GlslTransformEngine.DEFAULT, GlslTransformEngine.resolve(null, unknown::add));
        assertEquals(List.of(), unknown);
    }

    @Test
    void anUnknownValueIsReportedAndFallsBackToTheDefault() {
        final List<String> unknown = new ArrayList<>();
        assertSame(GlslTransformEngine.DEFAULT, GlslTransformEngine.resolve(" Antlr ", unknown::add));
        assertSame(GlslTransformEngine.DEFAULT, GlslTransformEngine.resolve("", unknown::add));
        assertEquals(List.of("antlr", ""), unknown);
        assertNull(GlslTransformEngine.byId("antlr"));
    }

    @Test
    void irisAndGlsmUseTheSameEngineInOneJvm() {
        // The build's -PglslEngine sets the property for the test JVM; both classes resolve it through the same helper.
        assertSame(TransformPatcher.engine(), CompatShaderTransformer.engine());
        assertSame(GlslTransformEngine.fromSystemProperty(value -> { }), CompatShaderTransformer.engine());
    }
}
