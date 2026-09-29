package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.GlslTokens;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShaderAst#print(String, int)}: glsl-transformer prints every {@code float} literal with an {@code f} suffix,
 * which GLSL has only from 1.30 on, so output that declares a lower version prints floats without it (Step 10 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md; the S3 report's open question 2). GLSM's
 * {@code CompatShaderTransformer} passes its output version; today that is at least 330, so the legacy branch guards a
 * backend whose minimum GLSL version is below 130.
 */
class ShaderAstLegacyPrintTest {
    private static final String SOURCE = """
        uniform float scale;
        void main() {
            float a = 1.0;
            float b = 0.0005 * scale;
            float c = 2.5e3;
            int i = 3;
            gl_FragColor = vec4(a, b, c, float(i));
        }
        """;

    /** A float literal with a suffix: a digit or a dot followed by {@code f} or {@code F}, not part of a name. */
    private static final Pattern SUFFIXED_FLOAT = Pattern.compile("(?<![A-Za-z_])[0-9.][0-9.eE+-]*[fF]\\b");

    @Test
    void below130FloatsHaveNoSuffix() {
        final String printed = ShaderAst.parse(SOURCE, 120).print("#version 120", 120);

        assertFalse(SUFFIXED_FLOAT.matcher(printed).find(), printed);
        assertTrue(printed.startsWith("#version 120\n"), printed);
        // The same program, token for token (GlslTokens compares floats by value).
        assertEquals("", GlslTokens.diff("#version 120\n" + SOURCE, printed), printed);
        // Integers are untouched, and the output parses again at 120.
        assertTrue(GlslTokens.contains(printed, "int i = 3 ;"), printed);
        ShaderAst.parse(printed, 120);
    }

    @Test
    void from130TheSuffixedPrintIsKept() {
        for (int version : new int[] {130, 330}) {
            final String legacy = ShaderAst.parse(SOURCE, 120).print("#version " + version, version);
            final String plain = ShaderAst.parse(SOURCE, 120).print("#version " + version);

            assertEquals(plain, legacy);
            assertTrue(legacy.contains("1.0f"), legacy);
        }
    }

    @Test
    void theVersionAndExtensionStatementsAreRemovedAsByPrint() {
        final String source = "#version 120\n#extension GL_EXT_gpu_shader4 : enable\nvoid main() { gl_FragColor = vec4(0.5); }\n";
        final String printed = ShaderAst.parse(source, 120).print("HEADER", 120);

        assertTrue(printed.startsWith("HEADER\n"), printed);
        assertFalse(printed.contains("#version"), printed);
        assertFalse(printed.contains("#extension"), printed);
        assertTrue(GlslTokens.contains(printed, "gl_FragColor = vec4 ( 0.5 ) ;"), printed);
        assertFalse(SUFFIXED_FLOAT.matcher(printed).find(), printed);
    }
}
