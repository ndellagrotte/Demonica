package net.coderbot.iris.celeritas.vertices;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.taumc.glsl.grammar.GLSLLexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@link TerrainVertexFormatRequirements#countIdentifiers}, the library-free identifier scan of Step 6
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md), against the TauMC {@code GLSLLexer} scan it replaced (reproduced
 * here as the oracle, {@link #taumc}): the same counts where the lexer reported no error, an exception where it
 * reported one. With {@code -PglslCorpusDir=<abs>}, {@link #corpusVertexOutputs} does the same for every recorded
 * vertex output (the transformed sources {@code analyze} reads in game). Removed with TauMC in Step 11.
 */
class TerrainVertexFormatScanParityTest {
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
            Arguments.of("declaration only", "in vec3 iris_Normal; void main() { gl_Position = vec4(0.0); }"),
            Arguments.of("every attribute read", "in uint mc_Entity; in vec2 mc_midTexCoord; in vec4 at_midBlock; in vec4 at_tangent; in vec3 iris_Normal;\n"
                + "void main() { gl_Position = vec4(float(mc_Entity) + mc_midTexCoord.x + at_midBlock.x + at_tangent.x + iris_Normal.x); }"),
            Arguments.of("comments", "in vec4 at_tangent; // at_tangent at_tangent\n/* at_midBlock at_midBlock\n at_tangent */ void main() {}"),
            Arguments.of("a line comment continued by a backslash", "in vec4 at_tangent; // at_tangent \\\nat_tangent\nvoid main() {}"),
            Arguments.of("macro text", "#define X at_tangent + at_tangent\nin vec4 at_tangent; void main() {}"),
            Arguments.of("a continued define", "#define X at_tangent \\\n  + at_tangent\nin vec4 at_tangent; void main() {}"),
            Arguments.of("CRLF line ends", "#version 330 core\r\n#define X at_tangent\r\nin vec4 at_tangent;\r\nvoid main() { vec4 t = at_tangent; }\r\n"),
            Arguments.of("numbers with suffixes", "in vec4 at_tangent; void main() { float a = 1.0f + 1e-3 + .5e+2F + 0x1Fu + 07 + 1.0lf; vec4 t = at_tangent; }"),
            Arguments.of("swizzles and members", "in vec4 at_tangent; void main() { vec2 a = at_tangent.xy; float b = at_tangent.w; }"),
            Arguments.of("longer and shorter names", "in vec4 at_tangentX; in vec4 xat_tangent; in vec4 at_tangen; void main() { at_tangentX; }"),
            Arguments.of("a line continuation in code", "in vec4 at_tangent; void main() { vec4 t = at_\\\ntangent; vec4 u = at_tangent; }"),
            Arguments.of("the Celeritas header's shape", CELERITAS_HEADER_SHAPE),
            // Step 7b: line ends and directive lines as TauMC's lexer modes read them.
            Arguments.of("a line comment ended by a lone CR", "in vec4 at_tangent;\n// c\rvoid main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a line comment continued over a backslash and a lone CR", "in vec4 at_tangent;\n// c \\\rvoid main() { vec4 t = at_tangent; }\n"),
            Arguments.of("#error with a trailing backslash", "#error foo \\\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("#endif with a trailing backslash", "#ifdef B\n#endif \\\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("#endif ended by a lone CR", "#ifdef B\n#endif\rin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("#else ended by a lone CR", "#ifdef B\n#else\r#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a block comment over two lines on a #define line", "#define X /* a\n at_tangent */ 1\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("a block comment over two lines on an #if line", "#if A /* a\n at_tangent */\n#endif\nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("comments on #define, #if, #elif, #else, #endif lines", "#define X 1 // c\n#if A /* c */\n#elif B // c\n#else /* c */\n#endif // c\n"
                + "in vec4 at_tangent; void main() { vec4 t = at_tangent; }\n"),
            Arguments.of("an indented directive and a null directive", "   #define X 1\n#\n#   \nin vec4 at_tangent; void main() { vec4 t = at_tangent; }\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("agreeing")
    void sameCountsAsTheTauMCLexer(String name, String source) {
        final int[] expected = taumc(source);
        assertTrue(expected != null, "the TauMC lexer reported an error on this row");
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

    /** Where TauMC's lexer reported an error, {@code analyze} kept the complete format; the scan throws, with the same result. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("lexerErrors")
    void lexerErrorsKeepTheCompleteFormat(String name, String source) {
        assertEquals(null, taumc(source), "the TauMC lexer reported no error on this row");
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
        assertArrayEquals(new int[] {0, 0, 2, 1, 0}, taumc(unterminated));
        assertThrows(IllegalArgumentException.class, () -> TerrainVertexFormatRequirements.countIdentifiers(unterminated));

        final String conditional = "in vec4 at_tangent;\n#ifdef TANGENTS\nvoid f() { vec4 t = at_tangent; }\n#endif\nvoid main() {}\n";
        assertArrayEquals(new int[] {0, 0, 1, 0, 0}, taumc(conditional));
        assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(conditional));

        final String code = "in vec4 at_tangent; void main() { vec4 t = at_tangent; }\n";
        for (String countedNothingMore : List.of("#line 5\n" + code, "#line abc\n" + code, "#else\n" + code, "#elif A\n" + code,
            "#ifdef A\n#endif\n#else\n" + code, "#ifdef_X\n" + code)) {
            assertArrayEquals(new int[] {0, 0, 0, 0, 0}, taumc(countedNothingMore), countedNothingMore);
            assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(countedNothingMore), countedNothingMore);
        }
        for (String unchecked : List.of("#version abc\n" + code, "#version 330 foo\n" + code, "#pragma @@\n" + code, "#pragma \"x\"\n" + code)) {
            assertEquals(null, taumc(unchecked), unchecked);
            assertArrayEquals(new int[] {0, 0, 2, 0, 0}, TerrainVertexFormatRequirements.countIdentifiers(unchecked), unchecked);
        }
    }

    /**
     * Every recorded vertex output of a corpus ({@code out.taumc.vertex.glsl} or {@code out.douira.vertex.glsl}, what
     * {@code analyze} read in game with that engine): the scan's counts equal the TauMC lexer's, file by file. The
     * summary gives, per engine, how many CELERITAS_TERRAIN vertex outputs require each attribute.
     */
    @Test
    void corpusVertexOutputs() throws IOException {
        final String dir = System.getProperty("demonica.glsl.corpus.dir", "").trim();
        assumeFalse(dir.isEmpty(), "no transform corpus configured (-PglslCorpusDir)");
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(Paths.get(dir))) {
            files = walk.filter(p -> p.getFileName().toString().matches("out\\.(taumc|douira)\\.vertex\\.glsl")).sorted()
                .collect(Collectors.toList());
        }
        int differing = 0;
        final java.util.Map<String, int[]> referencing = new java.util.TreeMap<>();
        final java.util.Map<String, Integer> terrain = new java.util.TreeMap<>();
        for (Path file : files) {
            final String source = Files.readString(file, StandardCharsets.UTF_8);
            final int[] expected = taumc(source);
            final int[] actual = TerrainVertexFormatRequirements.countIdentifiers(source);
            if (expected == null || !Arrays.equals(expected, actual)) {
                differing++;
                System.out.println("tvfr-parity:   DIFFERS " + file + " TauMC " + Arrays.toString(expected) + " scan "
                    + Arrays.toString(actual));
            }
            if (Files.readString(file.resolveSibling("case.properties")).contains("patch=CELERITAS_TERRAIN")) {
                final String engine = file.getFileName().toString().split("\\.")[1];
                terrain.merge(engine, 1, Integer::sum);
                final int[] counts = referencing.computeIfAbsent(engine, e -> new int[actual.length]);
                for (int i = 0; i < actual.length; i++) {
                    counts[i] += actual[i] > 1 ? 1 : 0;
                }
            }
        }
        System.out.println("tvfr-parity: corpus=" + dir + " vertexOutputs=" + files.size() + " differing=" + differing);
        terrain.forEach((engine, count) -> System.out.println("tvfr-parity:   " + engine + " celeritasTerrain=" + count
            + " requiring" + Arrays.toString(TerrainVertexFormatRequirements.Attribute.values()) + "="
            + Arrays.toString(referencing.get(engine))));
        assertEquals(0, differing);
    }

    /**
     * The scan {@code TerrainVertexFormatRequirements} did before Step 6: TauMC's lexer; null when it reported an error
     * or threw (a top-level {@code #endif}: {@code EmptyStackException}), which {@code analyze} treated alike, keeping
     * the complete format.
     */
    static int[] taumc(String source) {
        final TerrainVertexFormatRequirements.Attribute[] attributes = TerrainVertexFormatRequirements.Attribute.values();
        final int[] occurrences = new int[attributes.length];
        final int[] errors = new int[1];
        final GLSLLexer lexer = new GLSLLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                                    String message, RecognitionException exception) {
                errors[0]++;
            }
        });
        final List<? extends Token> tokens;
        try {
            tokens = lexer.getAllTokens();
        } catch (RuntimeException exception) {
            return null;
        }
        for (Token token : tokens) {
            if (token.getType() != GLSLLexer.IDENTIFIER) {
                continue;
            }
            for (TerrainVertexFormatRequirements.Attribute attribute : attributes) {
                if (attribute.shaderName().equals(token.getText())) {
                    occurrences[attribute.ordinal()]++;
                }
            }
        }
        return errors[0] == 0 ? occurrences : null;
    }
}
