package net.coderbot.iris.celeritas.vertices;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TerrainVertexFormatRequirements#countIdentifiers}, the library-free identifier scan of Step 6
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md), against the TauMC {@code GLSLLexer} scan it replaced: the same
 * counts where the lexer reported no error, an exception where it reported one. Until Step 11 this was
 * {@code TerrainVertexFormatScanParityTest}, which ran TauMC's lexer as the oracle (and, with {@code -PglslCorpusDir},
 * over every recorded vertex output: 387 files of {@code run/transform-corpus}, none differing, at its last run); Step 11
 * wrote the lexer's answers into the rows and removed the library.
 */
class TerrainVertexFormatScanTest {
    private static final String CELERITAS_HEADER_SHAPE = """
        #version 330 core
        #extension GL_ARB_shader_draw_parameters : enable


        #define VERT_POS_SCALE 1.0
        vec3 _vert_position;
        #ifdef USE_VERTEX_COMPRESSION
        in uvec4 a_PosId;
        #if !defined(VERT_POS_SCALE)
        #error "VERT_POS_SCALE not defined"
        #elif !defined(VERT_POS_OFFSET)
        #error "VERT_POS_OFFSET not defined"
        #endif
        void _vert_init() { _vert_position = vec3(a_PosId.xyz) * VERT_POS_SCALE; }
        #else
        in vec3 a_PosId;
        void _vert_init() { _vert_position = a_PosId; }
        #endif


        in vec4 mc_midTexCoord;
        in uint mc_Entity;
        in vec3 iris_Normal;
        void main() { vec2 iris_MidTex = mc_midTexCoord.xy * 3.0517578E-5; uint e = mc_Entity >> 1u; gl_Position = vec4(float(0x1Fu)); }
        """;

    static Stream<Arguments> agreeing() {
        return Stream.of(
            Arguments.of("declaration only", "in vec3 iris_Normal; void main() { gl_Position = vec4(0.0); }", new int[] {0, 0, 0, 0, 1}),
            Arguments.of("every attribute read", "in uint mc_Entity; in vec2 mc_midTexCoord; in vec4 at_midBlock; in vec4 at_tangent; in vec3 iris_Normal;\n"
                + "void main() { gl_Position = vec4(float(mc_Entity) + mc_midTexCoord.x + at_midBlock.x + at_tangent.x + iris_Normal.x); }", new int[] {2, 2, 2, 2, 2}),
            Arguments.of("comments", "in vec4 at_tangent; // at_tangent at_tangent\n/* at_midBlock at_midBlock\n at_tangent */ void main() {}", new int[] {0, 0, 1, 0, 0}),
            Arguments.of("a line comment continued by a backslash", "in vec4 at_tangent; // at_tangent \\\nat_tangent\nvoid main() {}", new int[] {0, 0, 1, 0, 0}),
            Arguments.of("macro text", "#define X at_tangent + at_tangent\nin vec4 at_tangent; void main() {}", new int[] {0, 0, 1, 0, 0}),
            Arguments.of("a continued define", "#define X at_tangent \\\n  + at_tangent\nin vec4 at_tangent; void main() {}", new int[] {0, 0, 1, 0, 0}),
            Arguments.of("CRLF line ends", "#version 330 core\r\n#define X at_tangent\r\nin vec4 at_tangent;\r\nvoid main() { vec4 t = at_tangent; }\r\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("numbers with suffixes", "in vec4 at_tangent; void main() { float a = 1.0f + 1e-3 + .5e+2F + 0x1Fu + 07 + 1.0lf; vec4 t = at_tangent; }", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("swizzles and members", "in vec4 at_tangent; void main() { vec2 a = at_tangent.xy; float b = at_tangent.w; }", new int[] {0, 0, 3, 0, 0}),
            Arguments.of("longer and shorter names", "in vec4 at_tangentX; in vec4 xat_tangent; in vec4 at_tangen; void main() { at_tangentX; }", new int[] {0, 0, 0, 0, 0}),
            Arguments.of("a line continuation in code", "in vec4 at_tangent; void main() { vec4 t = at_\\\ntangent; vec4 u = at_tangent; }", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("the Celeritas header's shape", CELERITAS_HEADER_SHAPE, new int[] {2, 2, 0, 0, 1}),
            // Step 7b: line ends and directive lines as TauMC's lexer modes read them.
            Arguments.of("a line comment ended by a lone CR", "in vec4 at_tangent;\n// c\rvoid main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("a line comment continued over a backslash and a lone CR", "in vec4 at_tangent;\n// c \\\rvoid main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 1, 0, 0}),
            Arguments.of("#error with a trailing backslash", "#error foo \\\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("#endif with a trailing backslash", "#ifdef B\n#endif \\\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("#endif ended by a lone CR", "#ifdef B\n#endif\rin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("#else ended by a lone CR", "#ifdef B\n#else\r#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("a block comment over two lines on a #define line", "#define X /* a\n at_tangent */ 1\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("a block comment over two lines on an #if line", "#if A /* a\n at_tangent */\n#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("comments on #define, #if, #elif, #else, #endif lines", "#define X 1 // c\n#if A /* c */\n#elif B // c\n#else /* c */\n#endif // c\n"
                + "in vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0}),
            Arguments.of("an indented directive and a null directive", "   #define X 1\n#\n#   \nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n", new int[] {0, 0, 2, 0, 0})
        );
    }

    /** {@code expected} is the TauMC lexer's count per attribute (in {@code Attribute} order), recorded at Step 11. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("agreeing")
    void sameCountsAsTheTauMCLexer(String name, String source, int[] expected) {
        assertArrayEquals(expected, TerrainVertexFormatRequirements.countIdentifiers(source), () -> name + ": TauMC "
            + Arrays.toString(expected) + ", scan " + Arrays.toString(TerrainVertexFormatRequirements.countIdentifiers(source)));
    }

    static Stream<Arguments> lexerErrors() {
        return Stream.of(
            Arguments.of("an at sign", "in vec4 at_tangent; void main() { @ }"),
            Arguments.of("a dollar sign", "in vec4 at_tangent$; void main() {}"),
            Arguments.of("a backquote", "in vec4 at_tangent; void main() { ` }"),
            Arguments.of("a string literal in code", "in vec4 at_tangent; void main() { \"at_tangent\"; }"),
            Arguments.of("a character literal in code", "in vec4 at_tangent; void main() { 'a'; }"),
            Arguments.of("a stray backslash", "in vec4 at_tangent; void main() { \\ }"),
            Arguments.of("a form feed", "in vec4 at_tangent;\f void main() {}"),
            Arguments.of("a non-ASCII letter", "in vec4 at_tangént; void main() {}"),
            Arguments.of("an unknown directive", "#include \"foo.glsl\"\nin vec4 at_tangent; void main() {}"),
            // Step 7b: directive lines TauMC's lexer rejected.
            Arguments.of("a comment on an #ifdef line", "#ifdef A // c\n#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a comment on an #undef line", "#undef A // c\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a block comment on a #version line", "#version 330 core /* c */\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a comment on an #extension line", "#extension GL_X : enable // c\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a #pragma with a trailing backslash", "#pragma optimize(on) \\\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("an #ifndef with a trailing backslash", "#ifndef A \\\n#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a lone CR ending a #define line", "#define A\rin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a lone CR ending an #if line", "#if A\r#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a lone CR ending an #error line", "#error A\rin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a top-level #endif (TauMC threw EmptyStackException)", "#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("one #endif too many", "#ifdef A\n#endif\n#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a # inside a line of code", "in vec4 at_tangent; void main() { # }\nvoid f() { vec4 t = at_tangent; }\n"),
            Arguments.of("an upper-case directive", "#DEFINE X 1\nin vec4 at_tangent; void main() {}")
        );
    }

    /**
     * Where TauMC's lexer reported an error (every row here; checked at Step 11), {@code analyze} kept the complete
     * format; the scan throws, with the same result.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("lexerErrors")
    void lexerErrorsKeepTheCompleteFormat(String name, String source) {
        assertThrows(IllegalArgumentException.class, () -> TerrainVertexFormatRequirements.countIdentifiers(source));
        assertEquals(TerrainVertexFormatRequirements.all(), TerrainVertexFormatRequirements.analyze(List.of(source)));
    }

    /**
     * The differences that remain, each as {@code countIdentifiers}' javadoc lists it: an unterminated block comment
     * (the TauMC lexer read the rest as code; the scan throws); an attribute read only inside an {@code #ifdef} block
     * (the TauMC lexer read the block as one opaque token and did not count it); code after a {@code #line} directive,
     * after an {@code #else} without an open block or after {@code #ifdef_X} (the TauMC lexer counted nothing more);
     * and {@code #version}/{@code #pragma} contents the TauMC lexer rejected (the scan does not check them). In the
     * rows where the scan counts more, TauMC's count left out attributes the program reads.
     */
    @Test
    void remainingDifferences() {
        final String unterminated = "in vec4 at_tangent; void main() { vec4 t = at_tangent; } /* at_midBlock";
        // TauMC's lexer counted {0, 0, 2, 1, 0}.
        assertThrows(IllegalArgumentException.class, () -> TerrainVertexFormatRequirements.countIdentifiers(unterminated));

        final String conditional = "in vec4 at_tangent;\n#ifdef TANGENTS\nvoid f() { vec4 t = at_tangent; }\n#endif\nvoid main() {}\n";
        // TauMC's lexer counted {0, 0, 1, 0, 0}.
        assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(conditional));

        final String code = "in vec4 at_tangent; void main() { vec4 t = at_tangent; }\n";
        for (String countedNothingMore : List.of("#line 5\n" + code, "#line abc\n" + code, "#else\n" + code, "#elif A\n" + code,
            "#ifdef A\n#endif\n#else\n" + code, "#ifdef_X\n" + code)) {
            // TauMC's lexer counted {0, 0, 0, 0, 0}.
            assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(countedNothingMore), countedNothingMore);
        }
        for (String unchecked : List.of("#version abc\n" + code, "#version 330 foo\n" + code, "#pragma @@\n" + code, "#pragma \"x\"\n" + code)) {
            // TauMC's lexer reported an error.
            assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(unchecked), unchecked);
        }
    }
}
