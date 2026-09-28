package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.util.Type;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Proves that {@link ShaderAst}'s verbs do what TauMC's {@link Transformer} verbs do
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, Steps 3 and 4): every case runs the verb on both libraries over the same
 * source and compares the printed programs as {@link GlslTokens}. TauMC's side is parsed with
 * {@link ShaderParser#parseShader} and printed with {@link GlslTransformUtils#getFormattedShader}, as the old engine
 * does; the adapter's side with {@link ShaderAst#parse} and {@link ShaderAst#printBody()}.
 *
 * <p>The fixtures are hand-written: GLSL 120 and 330 styles, names that are both a function and a variable,
 * multi-declarator declarations, arrays, nested calls, struct fields and swizzles, parameters, interface blocks and
 * comments. Deliberate deviations (where TauMC throws or writes broken GLSL) are separate tests named
 * {@code deviation...} that assert both behaviours.</p>
 *
 * <p>Step 4 adds the structural verbs (renameAndWrapShadow, removeUnusedFunctions, removeConstAssignment,
 * findQualifiers, hasAssignment, initialize, replaceFunctionDefinition) with their own fixtures, the queries that have
 * no TauMC verb (functions, isDeclaredGlobal) against the parse-tree reads they replace, {@link #transformGrouped}
 * (TauMC's CompatibilityTransformer.transformGrouped written on ShaderAst) against the real one, and the order TauMC's
 * rule-context cache gives after verbs have added nodes. Cases built with {@link #changingParity} also check that
 * TauMC's verb changed the program.</p>
 *
 * <p>With {@code -PglslCorpusDir=<abs>} the test {@link #corpusDifferential} also runs every verb on every recorded input
 * under that directory with arguments drawn from the input ({@link ShaderAstCorpusDifferential}).</p>
 */
class ShaderAstParityTest {
    // ---------------------------------------------------------------------------------------------------------------
    // Fixtures

    static final String VERTEX_120 = """
        #version 120
        // A legacy gbuffers vertex shader; the comments must not matter.
        attribute vec4 mc_Entity;
        attribute vec2 mc_midTexCoord;
        varying vec2 texcoord;
        varying vec4 color, tint; /* two declarators */
        uniform float frameTimeCounter;
        uniform vec3 chunkOffset;
        const float PI = 3.14159;
        struct Light { vec3 position; float strength; };
        uniform Light lights[2];
        float wave(float x) { return sin(x * PI + frameTimeCounter); }
        vec4 shade(vec4 c, float wave) { return c * wave; }
        void main() {
            float offset = wave(gl_Vertex.x); // a local that calls wave
            texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            gl_TexCoord[0] = gl_MultiTexCoord0;
            gl_TexCoord[1].xy = gl_MultiTexCoord1.xy;
            color = shade(gl_Color, wave(offset));
            tint = vec4(lights[0].position * lights[1].strength, 1.0);
            gl_FogFragCoord = length((gl_ModelViewMatrix * gl_Vertex).xyz);
            gl_Position = ftransform();
        }
        """;

    static final String FRAGMENT_120 = """
        #version 120
        uniform sampler2D texture;
        uniform sampler2D lightmap;
        uniform sampler2DShadow shadow;
        varying vec2 texcoord;
        varying vec2 lmcoord;
        varying vec4 glcolor;
        void main() {
            vec4 color = texture2D(texture, texcoord) * glcolor;
            color *= texture2D(lightmap, lmcoord);
            color.rgb *= shadow2D(shadow, vec3(texcoord, 0.5)).r;
            color += texture2DLod(lightmap, lmcoord, 0.0) * 0.0;
            /* DRAWBUFFERS:01 */
            gl_FragData[0] = color;
            gl_FragData[1] = vec4(lmcoord, 0.0, 1.0);
        }
        """;

    static final String FRAGCOLOR_120 = """
        #version 120
        uniform sampler2D gcolor;
        uniform float centerDepthSmooth;
        uniform vec3 worldpos;
        varying vec4 texcoord;
        void main() {
            vec3 c = texture2D(gcolor, texcoord.st).rgb;
            c *= centerDepthSmooth * 0.5 + 0.5;
            gl_FragColor = vec4(c, 1.0);
            gl_FragColor.a = fract(worldpos.y + 0.001);
        }
        """;

    static final String FRAGMENT_330 = """
        #version 330 core
        in vec2 texcoord;
        in vec4 color;
        uniform sampler2D gcolor;
        uniform usampler2D noisetex;
        uniform isampler3D volume;
        uniform sampler1D gradient;
        uniform sampler2DRect rect;
        uniform uint frameCounter;
        uniform ivec2 atlasSize;
        uniform int entityId;
        uniform bool hand;
        uniform mat2x2 rotation;
        uniform vec3 fogColor;
        layout(location = 0) out vec4 outColor0;
        out float a, b;
        float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
        vec3 fogColorAt(vec3 fogColor) { return fogColor * luma(fogColor); }
        void main() {
            vec4 albedo = texture(gcolor, texcoord) * color;
            float luma = luma(albedo.rgb); // a local named like the function it calls
            float weights[4];
            for (int i = 0; i < 4; i++) {
                weights[i] = float(i) * 0.25;
                albedo.rgb += fogColorAt(fogColor) * weights[i];
            }
            a = albedo.a * luma;
            b = a * 0.5;
            outColor0 = vec4(albedo.rgb * a, b);
        }
        """;

    /** Names in every position TauMC's rename treats differently: fields, parameters, blocks, types. */
    static final String NAMES_330 = """
        #version 330 core
        struct Material { vec4 color; float roughness; };
        uniform Material material;
        uniform Block { vec4 blockColor; float blockScale; };
        layout(std140) uniform Named { vec4 namedColor; } named;
        in vec4 color;
        out vec4 fragColor;
        vec4 tint(vec4 color) { return color * material.color; }
        float scaled(float x);
        void main() {
            vec4 color2 = color;
            Material local = material;
            fragColor = tint(color2) + material.color * color.r + blockColor * blockScale + named.namedColor;
            fragColor.xy *= scaled(local.roughness);
        }
        float scaled(float x) { return x * 2.0; }
        """;

    static final String COMPOSITE_120 = """
        #version 120
        uniform mat4 gbufferModelView;
        uniform mat4 gbufferProjection;
        uniform vec3 worldpos;
        uniform float viewWidth, viewHeight;
        varying vec4 texcoord;
        vec4 toClipSpace3(vec3 v) { return gbufferProjection * vec4(v, 1.0); }
        void main() {
            vec3 vertex = gl_Vertex.xyz;
            texcoord = gl_TextureMatrix[0] * gl_MultiTexCoord0;
            texcoord += gl_TextureMatrix[1] * gl_MultiTexCoord1 + gl_TextureMatrix[2] * gl_MultiTexCoord0;
            vec4 clip = toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz);
            gl_Position = vec4(worldpos, 0.0);
            gl_Position += clip * gl_ModelViewProjectionMatrix[0][0] + gl_FrontLightModelProduct.sceneColor;
            gl_Position.x += ftransform().x;
        }
        """;

    /** A function before the first qualified declaration, so TauMC anchors injected variables on the function. */
    static final String FUNCTION_FIRST_330 = """
        #version 330 core
        precision highp float;
        float helper(float x) { return x * 2.0; }
        uniform float scale;
        out vec4 fragColor;
        void main() { fragColor = vec4(helper(scale)); }
        """;

    static final String ARRAY_SIZE_330 = """
        #version 330 core
        const int PAIR_SIZE = 2;
        uniform vec2 a, b;
        out vec4 c;
        void main() { vec2 pair[2] = vec2[PAIR_SIZE](a, b); c = vec4(pair[0], vec2[PAIR_SIZE](a, b)[1]); }
        """;

    static final List<String> FIXTURES = List.of(VERTEX_120, FRAGMENT_120, FRAGCOLOR_120, FRAGMENT_330, NAMES_330,
        COMPOSITE_120, FUNCTION_FIRST_330, ARRAY_SIZE_330);

    @AfterAll
    static void restoreGlobalState() {
        ShaderTransformer.resetVersionHoistingForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Harness

    static String viaTauMC(String source, Consumer<Transformer> verb) {
        final Transformer transformer = new Transformer(ShaderParser.parseShader(source).full());
        verb.accept(transformer);
        final StringBuilder printed = new StringBuilder();
        transformer.mutateTree(tree -> printed.append(GlslTransformUtils.getFormattedShader(tree, "")));
        return printed.toString();
    }

    static String viaShaderAst(String source, Consumer<ShaderAst> verb) {
        final ShaderAst ast = ShaderAst.parse(source);
        verb.accept(ast);
        return ast.printBody();
    }

    static void assertParity(String source, Consumer<Transformer> taumc, Consumer<ShaderAst> adapter) {
        final String expected = viaTauMC(source, taumc);
        final String actual = viaShaderAst(source, adapter);
        final String diff = GlslTokens.diff(expected, actual);
        assertTrue(diff.isEmpty(), () -> "TauMC and ShaderAst differ (- TauMC, + ShaderAst):\n" + diff
            + "\n--- TauMC\n" + expected + "\n--- ShaderAst\n" + actual);
    }

    static DynamicTest parity(String name, String source, Consumer<Transformer> taumc, Consumer<ShaderAst> adapter) {
        return DynamicTest.dynamicTest(name, () -> assertParity(source, taumc, adapter));
    }

    /** {@link #parity}, and TauMC's verb must have changed the program (a case that two no-ops would pass is useless). */
    static DynamicTest changingParity(String name, String source, Consumer<Transformer> taumc, Consumer<ShaderAst> adapter) {
        return DynamicTest.dynamicTest(name, () -> {
            assertFalse(GlslTokens.of(viaTauMC(source, taumc)).equals(GlslTokens.of(viaTauMC(source, t -> { }))),
                "TauMC's verb left the program unchanged");
            assertParity(source, taumc, adapter);
        });
    }

    static DynamicTest queryParity(String name, String source, List<String> names,
                                   BiFunction<Transformer, String, Object> taumc,
                                   BiFunction<ShaderAst, String, Object> adapter) {
        return DynamicTest.dynamicTest(name, () -> {
            final Transformer transformer = new Transformer(ShaderParser.parseShader(source).full());
            final ShaderAst ast = ShaderAst.parse(source);
            final Map<String, Object> expected = new LinkedHashMap<>();
            final Map<String, Object> actual = new LinkedHashMap<>();
            for (String n : names) {
                expected.put(n, taumc.apply(transformer, n));
                actual.put(n, adapter.apply(ast, n));
            }
            assertEquals(expected, actual);
        });
    }

    /** TauMC's findType result as the keyword {@link ShaderAst.DeclaredType#keyword()} gives, or "none" for 0. */
    static String taumcTypeKeyword(int token) {
        if (token == 0) {
            return "none";
        }
        final String literal = GLSLLexer.VOCABULARY.getLiteralName(token);
        final String keyword = literal == null ? GLSLLexer.VOCABULARY.getSymbolicName(token) : literal.substring(1, literal.length() - 1);
        // glsl-transformer names a square matrix by its short name only.
        return keyword.matches("d?mat([234])x\\1") ? keyword.substring(0, keyword.length() - 2) : keyword;
    }

    static String adapterTypeKeyword(ShaderAst.DeclaredType type) {
        return type == null ? "none" : type.keyword();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Life cycle

    @TestFactory
    Stream<DynamicTest> printIdentity() {
        final List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < FIXTURES.size(); i++) {
            tests.add(parity("fixture " + i, FIXTURES.get(i), t -> { }, a -> { }));
        }
        return tests.stream();
    }

    @Test
    void syntaxErrorsBecomeOneExceptionWithTheLine() {
        // A token ANTLR's recovery can insert: glsl-transformer rethrows ANTLR's ParseCancellationException.
        final ShaderAst.SyntaxException missing = assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse("""
            #version 330 core
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0) vec4(0.0);
            }
            """));
        // No viable alternative: also the ParseCancellationException itself.
        final ShaderAst.SyntaxException noViable = assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse("""
            #version 330 core
            void main() {
                float = 1.0;
            }
            """));
        // A token match that neither deleting nor inserting one token can fix (a word where the version number goes):
        // ANTLR throws an InputMismatchException, which glsl-transformer turns into its own ParsingException
        // ("Unexpected token 'foo'"). Most errors are caught earlier, by prediction, as the two above.
        final ShaderAst.SyntaxException mismatch = assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse("""
            #version foo
            void main() { }
            """));
        for (ShaderAst.SyntaxException e : List.of(missing, noViable, mismatch)) {
            System.out.println("[ShaderAstParityTest] " + e.getMessage() + " / cause " + e.getCause().getClass().getName());
            assertTrue(e.getMessage().startsWith("line "), e.getMessage());
        }
        assertEquals(org.antlr.v4.runtime.misc.ParseCancellationException.class, missing.getCause().getClass());
        assertEquals(org.antlr.v4.runtime.misc.ParseCancellationException.class, noViable.getCause().getClass());
        assertEquals(io.github.douira.glsl_transformer.parser.ParsingException.class, mismatch.getCause().getClass());
        assertEquals(4, missing.line());
        assertEquals(3, noViable.line());
        assertEquals(1, mismatch.line());
    }

    @Test
    void directivesAreDroppedAndRecordedOnEveryParse() {
        final String source = """
            #version 330 core
            #extension GL_ARB_shader_image_load_store : enable
            #define STRENGTH 2
            #pragma optimize(on)
            out vec4 fragColor;
            #ifdef STRENGTH
            void main() { fragColor = vec4(1.0); }
            #endif
            """;
        // Twice: a parser with a parse-tree cache would skip the filter on the second parse of the same text.
        for (int i = 0; i < 2; i++) {
            final ShaderAst ast = ShaderAst.parse(source);
            assertEquals(List.of("line 3: #define", "line 6: #ifdef", "line 8: #endif", "#pragma optimize(on)"),
                ast.droppedDirectives(), "parse " + i);
            final String printed = ast.print("#version 460 core");
            final GlslTokens tokens = GlslTokens.of(printed);
            assertEquals(1, tokens.count("#version 460 core"), printed);
            assertFalse(printed.contains("#version 330"), printed);
            assertFalse(printed.contains("#extension"), printed);
            assertFalse(printed.contains("#pragma"), printed);
            assertTrue(tokens.contains("void main ( ) { fragColor = vec4 ( 1.0 ) ; }"), printed);
        }
    }

    /**
     * Why {@link ShaderAst} parses with {@code ParsingCacheStrategy.NONE} (its {@code newParser} javadoc): with Iris's
     * two-tier cache the filter sees no tokens when a translation unit is parsed a second time, and the strategy that
     * would exclude translation units recurses without end in 3.0.0-pre3. If this test fails after a library upgrade,
     * revisit the choice.
     */
    @Test
    void parsingCacheStrategiesThatHideOrBreakTheFilter() {
        final String source = "#version 330 core\n#define A 1\nvoid main() { }\n";
        final int[] dropped = {0};
        final io.github.douira.glsl_transformer.token_filter.ChannelFilter<io.github.douira.glsl_transformer.ast.transform.JobParameters> counting =
            new io.github.douira.glsl_transformer.token_filter.ChannelFilter<>(io.github.douira.glsl_transformer.token_filter.TokenChannel.PREPROCESSOR) {
                @Override
                public boolean isTokenAllowed(org.antlr.v4.runtime.Token token) {
                    if (super.isTokenAllowed(token)) {
                        return true;
                    }
                    dropped[0]++;
                    return false;
                }
            };
        final io.github.douira.glsl_transformer.ast.transform.ASTParser twoTier = new io.github.douira.glsl_transformer.ast.transform.ASTParser();
        twoTier.setParsingCacheStrategy(io.github.douira.glsl_transformer.ast.transform.ASTParser.ParsingCacheStrategy.TWO_TIER);
        twoTier.setTokenFilter(counting);
        twoTier.parseTranslationUnit(ShaderAst.ROOT_SUPPLIER.get(), source);
        final int first = dropped[0];
        twoTier.parseTranslationUnit(ShaderAst.ROOT_SUPPLIER.get(), source);
        assertTrue(first > 0, "the first parse filters the #define");
        assertEquals(first, dropped[0], "the second parse of the same text is served from the cache, unfiltered");

        final io.github.douira.glsl_transformer.ast.transform.ASTParser excluding = new io.github.douira.glsl_transformer.ast.transform.ASTParser();
        excluding.setParsingCacheStrategy(io.github.douira.glsl_transformer.ast.transform.ASTParser.ParsingCacheStrategy.ALL_EXCLUDING_TRANSLATION_UNIT);
        assertThrows(StackOverflowError.class, () -> excluding.parseTranslationUnit(ShaderAst.ROOT_SUPPLIER.get(), source));
    }

    @Test
    void lexerVersionFollowsTheArgument() {
        // 'sample' is a keyword from GLSL 400 on.
        final String source = "#version 330 core\nuniform float sample;\nvoid main() { }\n";
        assertTrue(GlslTokens.contains(ShaderAst.parse(source, 330).printBody(), "uniform float sample ;"));
        assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse(source, 460));
    }

    @Test
    void concurrentUseOnSeparateInstancesIsSafe() throws Exception {
        // glsl-transformer keeps the root of the nodes being built on a static stack; ShaderAst serializes builds.
        final List<String> sources = List.of(VERTEX_120, FRAGMENT_330, NAMES_330, COMPOSITE_120);
        final List<String> expected = new ArrayList<>();
        for (String source : sources) {
            expected.add(viaShaderAst(source, ShaderAstParityTest::busyWork));
        }
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                final String source = sources.get(i % sources.size());
                results.add(pool.submit(() -> viaShaderAst(source, ShaderAstParityTest::busyWork)));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals(expected.get(i % sources.size()), results.get(i).get(), "run " + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void busyWork(ShaderAst ast) {
        ast.injectVariable("uniform float iris_busy;");
        ast.injectFunction("float iris_busyFn(float x) { return x * iris_busy; }");
        ast.prependMain("float iris_local = iris_busyFn(1.0);");
        ast.replaceExpression("gl_Vertex", "vec4(iris_local)");
        // The index must still resolve names to nodes of this tree.
        assertTrue(ast.hasVariable("iris_busyFn"));
        assertTrue(ast.containsCall("iris_busyFn"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Injection

    @TestFactory
    Stream<DynamicTest> injectVariable() {
        return Stream.of(
            parity("two uniforms into a 120 vertex shader (the second lands first)", VERTEX_120,
                t -> { t.injectVariable("uniform mat4 iris_ModelViewMatrix;"); t.injectVariable("uniform mat4 iris_ProjectionMatrix;"); },
                a -> { a.injectVariable("uniform mat4 iris_ModelViewMatrix;"); a.injectVariable("uniform mat4 iris_ProjectionMatrix;"); }),
            parity("layout outputs and a uniform into a 330 fragment shader", FRAGMENT_330,
                t -> { t.injectVariable("layout (location = 1) out vec4 iris_FragData1;"); t.injectVariable("uniform float iris_currentAlphaTest;"); },
                a -> { a.injectVariable("layout (location = 1) out vec4 iris_FragData1;"); a.injectVariable("uniform float iris_currentAlphaTest;"); }),
            parity("unqualified, then qualified (only the qualified one moves the anchor)", VERTEX_120,
                t -> { t.injectVariable("vec4 iris_LightTexCoord;"); t.injectVariable("uniform vec3 u_RegionOffset;"); t.injectVariable("in vec3 iris_Normal;"); t.injectVariable("vec2 iris_Other;"); },
                a -> { a.injectVariable("vec4 iris_LightTexCoord;"); a.injectVariable("uniform vec3 u_RegionOffset;"); a.injectVariable("in vec3 iris_Normal;"); a.injectVariable("vec2 iris_Other;"); }),
            parity("a function before the first qualified declaration is the anchor", FUNCTION_FIRST_330,
                t -> t.injectVariable("uniform float iris_FogDensity;"),
                a -> a.injectVariable("uniform float iris_FogDensity;")),
            parity("const counts as a storage qualifier", NAMES_330,
                t -> { t.injectVariable("const float iris_Pi = 3.14159;"); t.injectVariable("float iris_Plain;"); t.injectVariable("uniform float iris_U;"); },
                a -> { a.injectVariable("const float iris_Pi = 3.14159;"); a.injectVariable("float iris_Plain;"); a.injectVariable("uniform float iris_U;"); }),
            parity("after an injected function, variables go before it", FRAGMENT_120,
                t -> { t.injectFunction("vec4 iris_f() { return vec4(1.0); }"); t.injectVariable("uniform float iris_u;"); t.injectVariable("uniform float iris_v;"); },
                a -> { a.injectFunction("vec4 iris_f() { return vec4(1.0); }"); a.injectVariable("uniform float iris_u;"); a.injectVariable("uniform float iris_v;"); }),
            parity("no qualified declaration and no function: nothing is inserted", "#version 330 core\nvec4 a;\nfloat b;\n",
                t -> t.injectVariable("uniform float iris_lost;"),
                a -> a.injectVariable("uniform float iris_lost;")),
            parity("a compute layout declaration is a qualified declaration (its qualifier has no parent in glsl-transformer)",
                "#version 430\ninvariant gl_Position;\nlayout(local_size_x = 8, local_size_y = 8) in;\nlayout(rgba16f) uniform image2D img;\nvoid main() { imageStore(img, ivec2(0), vec4(1.0)); }\n",
                t -> { t.injectVariable("uniform float iris_u0;"); t.injectVariable("vec4 iris_g0;"); },
                a -> { a.injectVariable("uniform float iris_u0;"); a.injectVariable("vec4 iris_g0;"); }),
            parity("an unqualified global before the first uniform stays first", "#version 120\nvec3 sunVec;\nconst int steps = 4;\nuniform float rainStrength;\nvoid main() { gl_FragColor = vec4(sunVec * rainStrength, float(steps)); }\n",
                t -> t.injectVariable("uniform vec4 iris_FogColor;"),
                a -> a.injectVariable("uniform vec4 iris_FogColor;"))
        );
    }

    @TestFactory
    Stream<DynamicTest> injectFunction() {
        return Stream.of(
            parity("ftransform replacement", VERTEX_120,
                t -> t.injectFunction("vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }"),
                a -> a.injectFunction("vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }")),
            parity("two functions (the second lands first)", FRAGMENT_330,
                t -> { t.injectFunction("float iris_a() { return 1.0; }"); t.injectFunction("float iris_b() { return iris_a(); }"); },
                a -> { a.injectFunction("float iris_a() { return 1.0; }"); a.injectFunction("float iris_b() { return iris_a(); }"); }),
            parity("CommonTransformer's fog struct and its initialized global", FRAGMENT_120,
                t -> {
                    t.injectVariable("uniform float iris_FogStart;");
                    t.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
                    t.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");
                },
                a -> {
                    a.injectVariable("uniform float iris_FogStart;");
                    a.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
                    a.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");
                }),
            parity("an array constant (DH normals)", VERTEX_120,
                t -> t.injectFunction("const vec3 irisNormals[6] = vec3[](vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0), vec3(0,-1,0), vec3(0,1,0));"),
                a -> a.injectFunction("const vec3 irisNormals[6] = vec3[](vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0), vec3(0,-1,0), vec3(0,1,0));")),
            parity("a declaration after an injected function goes before it", NAMES_330,
                t -> { t.injectFunction("float iris_f(float x) { return x; }"); t.injectFunction("float iris_MidTex = (mc_midTexCoord.x * 1.0).x;"); t.injectFunction("uniform vec4 iris_later;"); },
                a -> { a.injectFunction("float iris_f(float x) { return x; }"); a.injectFunction("float iris_MidTex = (mc_midTexCoord.x * 1.0).x;"); a.injectFunction("uniform vec4 iris_later;"); }),
            parity("a function whose parameters carry qualifiers", FUNCTION_FIRST_330,
                t -> { t.injectFunction("void iris_out(in vec2 p, out float r) { r = p.x; }"); t.injectVariable("uniform float iris_after;"); },
                a -> { a.injectFunction("void iris_out(in vec2 p, out float r) { r = p.x; }"); a.injectVariable("uniform float iris_after;"); })
        );
    }

    @Test
    void deviationInjectFunctionWithoutAnyFunction() {
        // TauMC anchors on the first function definition; without one, List.add(-1, ...) throws. ShaderAst appends.
        final String source = "#version 330 core\nuniform float a;\n";
        assertThrows(IndexOutOfBoundsException.class, () -> viaTauMC(source, t -> t.injectFunction("float iris_f() { return a; }")));
        final String printed = viaShaderAst(source, a -> a.injectFunction("float iris_f() { return a; }"));
        assertTrue(GlslTokens.contains(printed, "uniform float a ; float iris_f ( ) { return a ; }"), printed);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Renaming

    @TestFactory
    Stream<DynamicTest> rename() {
        return Stream.of(
            parity("gl_TexCoord (array references)", VERTEX_120,
                t -> t.rename("gl_TexCoord", "iris_TexCoord"), a -> a.rename("gl_TexCoord", "iris_TexCoord")),
            parity("a varying that is also a parameter and a struct field", NAMES_330,
                t -> t.rename("color", "iris_color"), a -> a.rename("color", "iris_color")),
            parity("a function name (prototype, forward declaration and calls)", NAMES_330,
                t -> t.rename("scaled", "iris_scaled"), a -> a.rename("scaled", "iris_scaled")),
            parity("a function name that is also a local variable", FRAGMENT_330,
                t -> t.rename("luma", "iris_luma"), a -> a.rename("luma", "iris_luma")),
            parity("a function name that is also a parameter name", VERTEX_120,
                t -> t.rename("wave", "iris_wave"), a -> a.rename("wave", "iris_wave")),
            parity("a swizzle name", VERTEX_120,
                t -> t.rename("xy", "zw"), a -> a.rename("xy", "zw")),
            parity("struct type, block, block member and block instance names", NAMES_330,
                t -> t.rename(Map.of("Material", "M2", "Block", "B2", "blockColor", "bc2", "named", "n2", "namedColor", "nc2")),
                a -> a.rename(Map.of("Material", "M2", "Block", "B2", "blockColor", "bc2", "named", "n2", "namedColor", "nc2"))),
            parity("a layout qualifier name and a loop variable", FRAGMENT_330,
                t -> t.rename(Map.of("location", "loc", "i", "j")), a -> a.rename(Map.of("location", "loc", "i", "j"))),
            parity("the matrix map of CoreTransformHelper", VERTEX_120,
                t -> t.rename(Map.of("gl_ModelViewMatrix", "iris_ModelViewMatrix", "gl_ProjectionMatrix", "iris_ProjectionMatrix", "gl_NormalMatrix", "iris_NormalMatrix")),
                a -> a.rename(Map.of("gl_ModelViewMatrix", "iris_ModelViewMatrix", "gl_ProjectionMatrix", "iris_ProjectionMatrix", "gl_NormalMatrix", "iris_NormalMatrix"))),
            parity("a swap through the map happens at once", FRAGMENT_330,
                t -> t.rename(Map.of("a", "b", "b", "a")), a -> a.rename(Map.of("a", "b", "b", "a"))),
            parity("a multi-declarator varying", VERTEX_120,
                t -> t.rename("tint", "iris_tint"), a -> a.rename("tint", "iris_tint")),
            parity("texture as a sampler name (CommonTransformer)", FRAGMENT_120,
                t -> t.rename("texture", "gtexture"), a -> a.rename("texture", "gtexture"))
        );
    }

    @TestFactory
    Stream<DynamicTest> renameFunctionCall() {
        return Stream.of(
            parity("ftransform", VERTEX_120,
                t -> t.renameFunctionCall("ftransform", "iris_ftransform"), a -> a.renameFunctionCall("ftransform", "iris_ftransform")),
            parity("TEXTURE_RENAMES on a 120 fragment shader", FRAGMENT_120,
                t -> t.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES), a -> a.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES)),
            parity("TEXTURE_RENAMES on a 120 composite shader", FRAGCOLOR_120,
                t -> t.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES), a -> a.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES)),
            parity("a user function (definition and calls)", VERTEX_120,
                t -> t.renameFunctionCall("shade", "iris_shade"), a -> a.renameFunctionCall("shade", "iris_shade")),
            parity("a variable: references change, the declarator does not", NAMES_330,
                t -> t.renameFunctionCall("color", "iris_color"), a -> a.renameFunctionCall("color", "iris_color")),
            parity("a function that is also a local and a parameter", FRAGMENT_330,
                t -> t.renameFunctionCall(Map.of("luma", "iris_luma", "fogColor", "iris_fog")),
                a -> a.renameFunctionCall(Map.of("luma", "iris_luma", "fogColor", "iris_fog")))
        );
    }

    @TestFactory
    Stream<DynamicTest> renameArray() {
        return Stream.of(
            renameArrayParity("gl_FragData with two indices", FRAGMENT_120, "gl_FragData", "iris_FragData"),
            renameArrayParity("gl_TexCoord, also with a swizzle after the index", VERTEX_120, "gl_TexCoord", "actinium_TexCoord"),
            renameArrayParity("a declared uniform array: the declaration stays", VERTEX_120, "lights", "iris_lights"),
            renameArrayParity("a name with no array access", VERTEX_120, "texcoord", "iris_texcoord"),
            renameArrayParity("gl_TextureMatrix inside a product", COMPOSITE_120, "gl_TextureMatrix", "iris_TextureMatrix"),
            renameArrayParity("a matrix column index", COMPOSITE_120, "gbufferModelView", "iris_gbufferModelView")
        );
    }

    private static DynamicTest renameArrayParity(String name, String source, String oldName, String newName) {
        return DynamicTest.dynamicTest(name, () -> {
            final Set<Integer> foundTauMC = new TreeSet<>();
            final Set<Integer> foundAst = new TreeSet<>();
            assertParity(source, t -> t.renameArray(oldName, newName, foundTauMC), a -> a.renameArray(oldName, newName, foundAst));
            assertEquals(foundTauMC, foundAst, "indices found");
        });
    }

    @Test
    void renameArrayWithANonLiteralIndexThrowsLikeTauMC() {
        // TauMC reads the index with Integer.parseInt of its text; weights[i] throws NumberFormatException.
        assertThrows(NumberFormatException.class, () -> viaTauMC(FRAGMENT_330, t -> t.renameArray("weights", "w", new TreeSet<>())));
        assertThrows(NumberFormatException.class, () -> viaShaderAst(FRAGMENT_330, a -> a.renameArray("weights", "w", new TreeSet<>())));
    }

    @Test
    void deviationRenameArrayWithAUnaryPlusIndexThrows() {
        // TauMC's Integer.parseInt("+1") succeeds; it records 1 and writes the token 'a+1', which reads as 'a + 1'.
        final String source = "#version 330 core\nuniform float arr[4];\nout vec4 o;\nvoid main() { o = vec4(arr[+1]); }\n";
        final Set<Integer> found = new TreeSet<>();
        final String taumc = viaTauMC(source, t -> t.renameArray("arr", "a", found));
        assertEquals(Set.of(1), found);
        assertTrue(GlslTokens.contains(taumc, "o = vec4 ( a + 1 ) ;"), taumc);
        assertThrows(NumberFormatException.class, () -> viaShaderAst(source, a -> a.renameArray("arr", "a", new TreeSet<>())));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Expressions and main

    @TestFactory
    Stream<DynamicTest> replaceExpression() {
        return Stream.of(
            replaceParity("gl_FragColor, also as an assignment target with a swizzle", FRAGCOLOR_120, "gl_FragColor", "gl_FragData[0]"),
            replaceParity("centerDepthSmooth: references change, the declaration does not", FRAGCOLOR_120,
                "centerDepthSmooth", "texture2D(iris_centerDepthSmooth, vec2(0.5)).r"),
            replaceParity("a literal inside a call pattern", FRAGCOLOR_120, "fract(worldpos.y + 0.001)", "fract(worldpos.y + 0.01)"),
            replaceParity("an array access", COMPOSITE_120, "gl_TextureMatrix[0]", "mat4(1.0)"),
            replaceParity("an identifier with the rest of an array (a matrix array constructor)", COMPOSITE_120, "gl_TextureMatrix",
                "mat4[8](iris_TextureMatrix, iris_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))"),
            replaceParity("a constructor call replaced by a product", COMPOSITE_120, "vec4(worldpos, 0.0)",
                "iris_ProjectionMatrix * gbufferModelView * vec4(worldpos, 1.0)"),
            replaceParity("a nested call pattern whose longest identifier sits in an inner call", COMPOSITE_120,
                "toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz)", "vertex"),
            replaceParity("a call with no arguments, also before a swizzle", COMPOSITE_120, "ftransform()",
                "(actinium_ProjectionMatrix * actinium_ModelViewMatrix * actinium_Vertex)"),
            replaceParity("a member access", COMPOSITE_120, "gl_FrontLightModelProduct.sceneColor", "actinium_SceneColor"),
            replaceParity("a parenthesized product", COMPOSITE_120, "gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)"),
            replaceParity("an identifier that is also called, replaced by an identifier", VERTEX_120, "wave", "iris_wave"),
            replaceParity("a varying assigned through a member", VERTEX_120, "gl_TexCoord[1]", "iris_TexCoord1"),
            replaceParity("a name that does not occur", FRAGMENT_330, "gl_Nothing", "vec4(0.0)"),
            replaceParity("an array constructor whose longest name is its size", ARRAY_SIZE_330, "vec2[PAIR_SIZE](a, b)", "iris_pair"),
            // glsl-transformer's Matcher accepts a candidate whose items are a prefix of the pattern's and has no list
            // boundaries; these three failed with it (S3 verification follow-up).
            replaceParity("a call pattern with more arguments than a call of the same overloaded name", OVERLOADS_330,
                "f(a, b)", "iris_z"),
            replaceParity("a call pattern with fewer arguments than a call of the same overloaded name", OVERLOADS_330,
                "f(a)", "iris_z"),
            replaceParity("Celeritas's constructor pattern on a one-argument constructor", CELERITAS_VEC4_330,
                "vec4(worldpos, 0.0)", "iris_ProjectionMatrix * gbufferModelView * vec4(worldpos, 1.0)"),
            replaceParity("a nested call pattern against a call whose argument sits one level up", OVERLOADS_330,
                "f(g(a, b))", "iris_z"),
            replaceParity("a nested call pattern against a call whose argument sits one level down", OVERLOADS_330,
                "f(g(a), b)", "iris_z")
        );
    }

    /** Overloads of two functions and calls whose argument lists are prefixes of each other or regroup the same names. */
    static final String OVERLOADS_330 = """
        #version 330 core
        uniform float a, b;
        out vec4 o;
        float g(float x) { return x; }
        float g(float x, float y) { return x + y; }
        float f(float x) { return x; }
        float f(float x, float y) { return x * y; }
        void main() {
            o = vec4(f(a), f(a, b), f(b), 0.0);
            o += vec4(f(g(a), b), f(g(a, b)), 0.0, 0.0);
        }
        """;

    /** CeleritasTransformer's replaceExpression pattern, on a pack that passes a vec4 to the constructor. */
    static final String CELERITAS_VEC4_330 = """
        #version 330 core
        uniform vec4 worldpos;
        uniform mat4 gbufferModelView, iris_ProjectionMatrix;
        void main() {
            gl_Position = vec4(worldpos);
        }
        """;

    private static DynamicTest replaceParity(String name, String source, String oldCode, String newCode) {
        return parity(name, source, t -> t.replaceExpression(oldCode, newCode), a -> a.replaceExpression(oldCode, newCode));
    }

    @Test
    void deviationReplacementKeepsItsPrecedence() {
        final String source = "#version 330 core\nuniform float x, c;\nout float y;\nvoid main() { y = x * c; y += x.x; y = -x; }\n";
        // TauMC splices the text: 'a + b * c' changes the meaning, and in postfix position (after '.' or under a unary
        // operator) it keeps only 'a'.
        final String taumc = viaTauMC(source, t -> t.replaceExpression("x", "a + b"));
        assertTrue(GlslTokens.contains(taumc, "y = a + b * c ;"), taumc);
        assertTrue(GlslTokens.contains(taumc, "y += a . x ;"), taumc);
        assertTrue(GlslTokens.contains(taumc, "y = - a ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("x", "a + b"));
        assertTrue(GlslTokens.contains(adapter, "y = ( a + b ) * c ;"), adapter);
        assertTrue(GlslTokens.contains(adapter, "y += ( a + b ) . x ;"), adapter);
        assertTrue(GlslTokens.contains(adapter, "y = - ( a + b ) ;"), adapter);
    }

    @Test
    void deviationTernaryReplacementIsKeptWhole() {
        final String source = "#version 330 core\nuniform float x, c, u;\nout float y;\nvoid main() { y = !(x > c) ? x : c; }\n";
        // TauMC's binary pass reparses the replacement as a binary expression, which drops '? 1.0 : 2.0'.
        final String taumc = viaTauMC(source, t -> t.replaceExpression("c", "u > 0.0 ? 1.0 : 2.0"));
        assertTrue(GlslTokens.contains(taumc, "y = ! ( x > u > 0.0 ) ? x : u > 0.0 ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("c", "u > 0.0 ? 1.0 : 2.0"));
        assertTrue(GlslTokens.contains(adapter, "y = ! ( x > ( u > 0.0 ? 1.0 : 2.0 ) ) ? x : u > 0.0 ? 1.0 : 2.0 ;"), adapter);
    }

    @Test
    void deviationSelfReferentialReplacementAppliesOnce() {
        final String source = "#version 330 core\nuniform float v;\nout float y;\nfloat f(float x) { return x; }\nvoid main() { y = f(f(v)); }\n";
        // TauMC's postfix pass finds the pattern again inside what its binary pass inserted and replaces it a second time.
        final String taumc = viaTauMC(source, t -> t.replaceExpression("f(v)", "f(f(v))"));
        assertTrue(GlslTokens.contains(taumc, "y = f ( f ( f ( f ( v ) ) ) ) ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("f(v)", "f(f(v))"));
        assertTrue(GlslTokens.contains(adapter, "y = f ( f ( f ( v ) ) ) ;"), adapter);
    }

    @Test
    void deviationReplaceExpressionSeesRenamedIdentifiers() {
        final String source = "#version 330 core\nuniform float a, c;\nout float y;\nvoid main() { y = a + c; }\n";
        // TauMC's replaceExpression finds nodes through a by-text cache (cachedContextsByText) that rename,
        // renameFunctionCall and renameArray do not update, so the second replaceExpression misses the 'a' that was 'c'.
        // Production shape: CELERITAS_TERRAIN renames gl_MultiTexCoord3 to mc_midTexCoord (patchMultiTexCoord3), then
        // replaces mc_midTexCoord.
        final String taumc = viaTauMC(source, t -> { t.replaceExpression("a", "b"); t.rename("c", "a"); t.replaceExpression("a", "d"); });
        assertTrue(GlslTokens.contains(taumc, "y = b + a ;"), taumc);
        final String adapter = viaShaderAst(source, a -> { a.replaceExpression("a", "b"); a.rename("c", "a"); a.replaceExpression("a", "d"); });
        assertTrue(GlslTokens.contains(adapter, "y = b + d ;"), adapter);

        // The stale cache also still finds a renamed node under its old name.
        final String taumcOld = viaTauMC(source, t -> { t.replaceExpression("a", "b"); t.rename("c", "e"); t.replaceExpression("c", "d"); });
        assertTrue(GlslTokens.contains(taumcOld, "y = b + d ;"), taumcOld);
        final String adapterOld = viaShaderAst(source, a -> { a.replaceExpression("a", "b"); a.rename("c", "e"); a.replaceExpression("c", "d"); });
        assertTrue(GlslTokens.contains(adapterOld, "y = b + e ;"), adapterOld);
    }

    @Test
    void deviationBinaryPatternAlsoReplacesItsFirstOperandInTauMC() {
        // TauMC's second pass parses the pattern as a postfix expression, which keeps 'colorSample' of
        // 'colorSample * mult', and replaces every remaining 'colorSample'. No Demonica pattern is a bare binary
        // expression (they are names, accesses and calls), so production output never showed this.
        final String source = "#version 330 core\nuniform vec3 colorSample;\nuniform float mult;\nout vec3 c;\n"
            + "void main() { c = colorSample * mult; c = max(c, colorSample); }\n";
        final String taumc = viaTauMC(source, t -> t.replaceExpression("colorSample * mult", "iris_product"));
        assertTrue(GlslTokens.contains(taumc, "c = iris_product ; c = max ( c , iris_product ) ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("colorSample * mult", "iris_product"));
        assertTrue(GlslTokens.contains(adapter, "c = iris_product ; c = max ( c , colorSample ) ;"), adapter);
    }

    @Test
    void deviationLiteralsMatchByValue() {
        final String source = "#version 330 core\nuniform vec3 p;\nout vec4 o;\nvoid main() { o = vec4(p, 0.); }\n";
        final String taumc = viaTauMC(source, t -> t.replaceExpression("vec4(p, 0.0)", "vec4(1.0)"));
        assertTrue(GlslTokens.contains(taumc, "o = vec4 ( p , 0.0 ) ;"), "TauMC matches the spelling only: " + taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("vec4(p, 0.0)", "vec4(1.0)"));
        assertTrue(GlslTokens.contains(adapter, "o = vec4 ( 1.0 ) ;"), adapter);
    }

    @TestFactory
    Stream<DynamicTest> prependAndAppendMain() {
        final String withReturn = "#version 330 core\nout vec4 c;\nvoid main() { c = vec4(1.0); return; }\n";
        final String noMain = "#version 330 core\nfloat helper() { return 1.0; }\n";
        final String clipPlanes = "{ if (actinium_ClipPlanesEnabled) { vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; "
            + "gl_ClipDistance[0] = dot(actinium_ClipPlane[0], _cp_ep); gl_ClipDistance[1] = dot(actinium_ClipPlane[1], _cp_ep); } }";
        return Stream.of(
            parity("prepend an assignment", VERTEX_120, t -> t.prependMain("iris_FogFragCoord = 0.0f;"), a -> a.prependMain("iris_FogFragCoord = 0.0f;")),
            parity("prepend twice (the second lands first)", FRAGMENT_330,
                t -> { t.prependMain("iris_FrontColor = vec4(1.0);"); t.prependMain("_celeritas_init();"); },
                a -> { a.prependMain("iris_FrontColor = vec4(1.0);"); a.prependMain("_celeritas_init();"); }),
            parity("append a discard", FRAGMENT_120,
                t -> t.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;"),
                a -> a.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;")),
            parity("append after a trailing return", withReturn, t -> t.appendMain("c.a = 0.5;"), a -> a.appendMain("c.a = 0.5;")),
            parity("append a compound statement (the clip planes)", VERTEX_120, t -> t.appendMain(clipPlanes), a -> a.appendMain(clipPlanes)),
            parity("no main: nothing happens", noMain,
                t -> { t.prependMain("x = 1.0;"); t.appendMain("y = 2.0;"); },
                a -> { a.prependMain("x = 1.0;"); a.appendMain("y = 2.0;"); }),
            parity("a declaration statement", COMPOSITE_120, t -> t.prependMain("vec4 iris_tmp = vec4(0.0);"), a -> a.prependMain("vec4 iris_tmp = vec4(0.0);"))
        );
    }

    @Test
    void deviationEmptyMainGetsTheStatement() {
        final String source = "#version 330 core\nvoid main() {}\n";
        assertThrows(NullPointerException.class, () -> viaTauMC(source, t -> t.prependMain("float x = 1.0;")));
        assertThrows(NullPointerException.class, () -> viaTauMC(source, t -> t.appendMain("float x = 1.0;")));
        final String printed = viaShaderAst(source, a -> { a.prependMain("float x = 1.0;"); a.appendMain("float y = 2.0;"); });
        assertTrue(GlslTokens.contains(printed, "void main ( ) { float x = 1.0 ; float y = 2.0 ; }"), printed);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Declarations

    @TestFactory
    Stream<DynamicTest> removeVariable() {
        final String globalAndLocal = "#version 330 core\nuniform float t;\nout vec4 c;\nvoid main() { float t = 2.0; c = vec4(t); }\n";
        final String localThenShared = "#version 330 core\nout vec4 c;\nvoid main() { float t = 2.0; c = vec4(t); }\nfloat u, t;\n";
        return Stream.of(
            parity("a sole global declarator", VERTEX_120, t -> t.removeVariable("chunkOffset"), a -> a.removeVariable("chunkOffset")),
            parity("an attribute", VERTEX_120, t -> t.removeVariable("mc_Entity"), a -> a.removeVariable("mc_Entity")),
            parity("the first of two declarators (no initializer)", FRAGMENT_330, t -> t.removeVariable("a"), a -> a.removeVariable("a")),
            parity("the second of two declarators", FRAGMENT_330, t -> t.removeVariable("b"), a -> a.removeVariable("b")),
            parity("the second of two varyings", VERTEX_120, t -> t.removeVariable("tint"), a -> a.removeVariable("tint")),
            parity("a sole local declarator", FRAGMENT_330, t -> t.removeVariable("albedo"), a -> a.removeVariable("albedo")),
            parity("a local array", FRAGMENT_330, t -> t.removeVariable("weights"), a -> a.removeVariable("weights")),
            parity("a global and a local of one name: the last sole declarator goes", globalAndLocal,
                t -> t.removeVariable("t"), a -> a.removeVariable("t")),
            parity("a shared declarator stops the scan", localThenShared, t -> t.removeVariable("t"), a -> a.removeVariable("t")),
            parity("a parameter is not a declarator", VERTEX_120, t -> t.removeVariable("c"), a -> a.removeVariable("c")),
            parity("a name that does not occur", VERTEX_120, t -> t.removeVariable("nothing"), a -> a.removeVariable("nothing"))
        );
    }

    @Test
    void deviationRemovingAnInitializedFirstDeclarator() {
        final String source = "#version 330 core\nout vec4 c;\nvoid main() { float a = 1.0, b; b = 2.0; c = vec4(b); }\n";
        // TauMC writes the second declarator's text into the first one's name and keeps the first one's initializer.
        final String taumc = viaTauMC(source, t -> t.removeVariable("a"));
        assertTrue(GlslTokens.contains(taumc, "float b = 1.0 ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.removeVariable("a"));
        assertTrue(GlslTokens.contains(adapter, "float b ;"), adapter);
    }

    @Test
    void deviationRemovingAForInitializerKeepsTheLoop() {
        final String source = "#version 330 core\nout vec4 c;\nvoid main() { float s = 0.0; for (int i = 0; i < 4; i++) { s += 1.0; } c = vec4(s); }\n";
        // Both outputs leave 'i' undeclared. TauMC drops the declaration with its ';', which is not GLSL; ShaderAst
        // empties the initializer and keeps the loop.
        final String taumc = viaTauMC(source, t -> t.removeVariable("i"));
        assertTrue(GlslTokens.contains(taumc, "for ( i < 4 ; i ++ ) {"), taumc);
        final String adapter = viaShaderAst(source, a -> a.removeVariable("i"));
        assertTrue(GlslTokens.contains(adapter, "for ( ; i < 4 ; i ++ ) { s += 1.0 ; }"), adapter);
    }

    /**
     * TauMC scans its rule-context cache: the original program in document order, then what the verbs added, in the
     * order added. S3 scanned the document instead (its test deviationDeclarationOrderAfterAnInjection); since S4
     * ShaderAst rebuilds TauMC's order, and these are parity cases.
     */
    @TestFactory
    Stream<DynamicTest> declarationOrderAfterAnInjection() {
        final String source = "#version 330 core\nout vec4 o;\nvoid main() { vec2 w = vec2(1.0); o = vec4(w, 0.0, 1.0); }\n";
        final String twoLocals = "#version 330 core\nout vec4 o;\nfloat f() { float w = 2.0; return w; }\n"
            + "void main() { vec2 w = vec2(1.0); o = vec4(w, f(), 1.0); }\n";
        return Stream.of(
            DynamicTest.dynamicTest("findType finds the local, which TauMC scanned before the injected uniform", () -> {
                final Transformer transformer = new Transformer(ShaderParser.parseShader(source).full());
                transformer.injectVariable("uniform float w;");
                assertEquals(GLSLLexer.VEC2, transformer.findType("w"));
                final ShaderAst ast = ShaderAst.parse(source);
                ast.injectVariable("uniform float w;");
                assertTrue(ast.findType("w").is(Type.F32VEC2), () -> ast.findType("w").keyword());
            }),
            parity("removeVariable removes the injected uniform, the last TauMC scanned", source,
                t -> { t.injectVariable("uniform float w;"); t.removeVariable("w"); },
                a -> { a.injectVariable("uniform float w;"); a.removeVariable("w"); }),
            parity("a declaration prepended to main is scanned after the program's own", twoLocals,
                t -> { t.prependMain("float w = 3.0;"); t.removeVariable("w"); },
                a -> { a.prependMain("float w = 3.0;"); a.removeVariable("w"); }),
            parity("two injections are scanned in the order they were made", source,
                t -> { t.injectVariable("uniform float w;"); t.injectFunction("float w = 4.0;"); t.removeVariable("w"); },
                a -> { a.injectVariable("uniform float w;"); a.injectFunction("float w = 4.0;"); a.removeVariable("w"); })
        );
    }

    @TestFactory
    Stream<DynamicTest> findType() {
        final List<String> names330 = List.of("texcoord", "color", "gcolor", "noisetex", "volume", "gradient", "rect",
            "frameCounter", "atlasSize", "entityId", "hand", "rotation", "fogColor", "outColor0", "a", "b", "albedo",
            "luma", "weights", "i", "c", "nothing");
        final List<String> names120 = List.of("mc_Entity", "mc_midTexCoord", "texcoord", "color", "tint", "frameTimeCounter",
            "chunkOffset", "PI", "lights", "offset", "wave", "x", "position", "gl_Vertex");
        final String structFirst = "#version 330 core\nstruct S { float v; };\nvoid f() { S t; }\nuniform vec2 t;\nvoid main() { }\n";
        return Stream.of(
            queryParity("330 fragment names", FRAGMENT_330, names330,
                (t, n) -> taumcTypeKeyword(t.findType(n)), (a, n) -> adapterTypeKeyword(a.findType(n))),
            queryParity("120 vertex names", VERTEX_120, names120,
                (t, n) -> taumcTypeKeyword(t.findType(n)), (a, n) -> adapterTypeKeyword(a.findType(n))),
            queryParity("120 fragment samplers", FRAGMENT_120, List.of("texture", "lightmap", "shadow", "color"),
                (t, n) -> taumcTypeKeyword(t.findType(n)), (a, n) -> adapterTypeKeyword(a.findType(n))),
            queryParity("a struct-typed declaration is skipped", structFirst, List.of("t", "S", "v"),
                (t, n) -> taumcTypeKeyword(t.findType(n)), (a, n) -> adapterTypeKeyword(a.findType(n)))
        );
    }

    @Test
    void findTypeReportsGlslTransformerTypes() {
        final ShaderAst ast = ShaderAst.parse(FRAGMENT_330);
        assertTrue(ast.findType("frameCounter").is(Type.UINT32));
        assertTrue(ast.findType("atlasSize").is(Type.I32VEC2));
        assertTrue(ast.findType("fogColor").is(Type.F32VEC3));
        assertTrue(ast.findType("gcolor") instanceof ShaderAst.DeclaredType.Fixed fixed
            && fixed.type() == io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier.BuiltinType.SAMPLER2D);
        assertNull(ast.findType("nothing"));
    }

    @TestFactory
    Stream<DynamicTest> hasVariable() {
        return Stream.of(
            queryParity("120 vertex", VERTEX_120, List.of("mc_Entity", "color", "tint", "PI", "lights", "wave", "shade",
                    "main", "offset", "x", "c", "Light", "position", "gl_Vertex", "gl_TexCoord", "ftransform", "nothing"),
                Transformer::hasVariable, ShaderAst::hasVariable),
            queryParity("names in blocks, structs and prototypes", NAMES_330, List.of("Material", "color", "roughness",
                    "material", "Block", "blockColor", "Named", "named", "namedColor", "tint", "scaled", "x", "color2", "local"),
                Transformer::hasVariable, ShaderAst::hasVariable),
            queryParity("330 fragment", FRAGMENT_330, List.of("a", "b", "luma", "fogColorAt", "fogColor", "i", "weights",
                    "albedo", "texture", "location"),
                Transformer::hasVariable, ShaderAst::hasVariable)
        );
    }

    @TestFactory
    Stream<DynamicTest> containsCall() {
        return Stream.of(
            queryParity("120 vertex", VERTEX_120, List.of("ftransform", "wave", "shade", "sin", "gl_Vertex", "gl_TexCoord",
                    "position", "strength", "xy", "texcoord", "mc_Entity", "chunkOffset", "c", "x", "Light", "nothing"),
                Transformer::containsCall, ShaderAst::containsCall),
            queryParity("names in blocks, structs and prototypes", NAMES_330, List.of("color", "roughness", "material",
                    "blockColor", "namedColor", "named", "tint", "scaled", "Material", "local", "r"),
                Transformer::containsCall, ShaderAst::containsCall),
            queryParity("330 fragment", FRAGMENT_330, List.of("texture", "gcolor", "luma", "fogColorAt", "float", "vec4",
                    "i", "weights", "rgb", "location", "outColor0"),
                Transformer::containsCall, ShaderAst::containsCall),
            queryParity("120 fragment (texture2D left out: see deviationTexture2DIsAnIdentifier)", FRAGMENT_120,
                List.of("texture", "shadow2D", "texture2DLod", "gl_FragData", "gl_FragColor"),
                Transformer::containsCall, ShaderAst::containsCall)
        );
    }

    /** Every query verb on every fixture, for every identifier that occurs in it (and one that does not). */
    @TestFactory
    Stream<DynamicTest> queriesOverEveryIdentifier() {
        final List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < FIXTURES.size(); i++) {
            final String source = FIXTURES.get(i);
            final List<String> names = new ArrayList<>(new TreeSet<>(GlslTokens.of(source).tokens().stream()
                .filter(token -> token.matches("[A-Za-z_][A-Za-z0-9_]*")).toList()));
            names.add("iris_absent");
            // texture2D and texture3D: see deviationTexture2DIsAnIdentifier.
            final List<String> callNames = names.stream().filter(n -> !n.equals("texture2D") && !n.equals("texture3D")).toList();
            tests.add(queryParity("findType, fixture " + i, source, names,
                (t, n) -> taumcTypeKeyword(t.findType(n)), (a, n) -> adapterTypeKeyword(a.findType(n))));
            tests.add(queryParity("hasVariable, fixture " + i, source, names, Transformer::hasVariable, ShaderAst::hasVariable));
            tests.add(queryParity("containsCall, fixture " + i, source, callNames, Transformer::containsCall, ShaderAst::containsCall));
        }
        return tests.stream();
    }

    @Test
    void deviationTexture2DIsAnIdentifier() {
        // TauMC's lexer makes texture2D and texture3D keywords, so only renameFunctionCall sees them; containsCall and
        // rename never do. glsl-transformer lexes them as identifiers. No Demonica caller asks either verb about them.
        final Transformer transformer = new Transformer(ShaderParser.parseShader(FRAGMENT_120).full());
        assertFalse(transformer.containsCall("texture2D"));
        assertTrue(ShaderAst.parse(FRAGMENT_120).containsCall("texture2D"));

        final String taumc = viaTauMC(FRAGMENT_120, t -> t.rename("texture2D", "texture"));
        assertTrue(GlslTokens.contains(taumc, "vec4 color = texture2D ( texture , texcoord ) * glcolor ;"), taumc);
        final String adapter = viaShaderAst(FRAGMENT_120, a -> a.rename("texture2D", "texture"));
        assertTrue(GlslTokens.contains(adapter, "vec4 color = texture ( texture , texcoord ) * glcolor ;"), adapter);
    }

    @Test
    void deviationRenameLeavesTypeNamesAndLength() {
        // A struct name in an array constructor is a type reference in glsl-transformer and a variable_identifier in
        // TauMC; the struct's declaration keeps its name in both, so neither output compiles. The length() method is
        // an identifier in TauMC and a node of its own in glsl-transformer.
        final String struct = "#version 330 core\nstruct S { float a; };\nuniform float u;\nout vec4 o;\n"
            + "void main() { S t = S(u); S both[2] = S[2](t, t); o = vec4(both[1].a); }\n";
        final String taumc = viaTauMC(struct, t -> t.rename("S", "S2"));
        assertTrue(GlslTokens.contains(taumc, "S t = S2 ( u ) ; S both [ 2 ] = S2 [ 2 ] ( t , t ) ;"), taumc);
        final String adapter = viaShaderAst(struct, a -> a.rename("S", "S2"));
        assertTrue(GlslTokens.contains(adapter, "S t = S2 ( u ) ; S both [ 2 ] = S [ 2 ] ( t , t ) ;"), adapter);

        final String length = "#version 430\nuniform float arr[4];\nout vec4 o;\nvoid main() { float length = 1.0; o = vec4(float(arr.length()) + length); }\n";
        final String taumcLength = viaTauMC(length, t -> t.rename("length", "len"));
        assertTrue(GlslTokens.contains(taumcLength, "o = vec4 ( float ( arr . len ( ) ) + len ) ;"), taumcLength);
        final String adapterLength = viaShaderAst(length, a -> a.rename("length", "len"));
        assertTrue(GlslTokens.contains(adapterLength, "o = vec4 ( float ( arr . length ( ) ) + len ) ;"), adapterLength);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Step 4 fixtures

    /** CompatShaderTransformer's and CommonTransformer's shadow calls in every shape they take in packs. */
    static final String SHADOW_120 = """
        #version 120
        uniform sampler2DShadow shadowtex0;
        uniform sampler2DShadow shadowtex1;
        uniform sampler1DShadow shadow1d;
        varying vec4 shadowPos;
        float weight(float v) { return v * 0.5; }
        float pcf(vec3 p) {
            float s = shadow2D(shadowtex0, p).r;
            s += shadow2D(shadowtex1, p + vec3(0.001, 0.0, 0.0)).x * 0.25;
            return s;
        }
        void main() {
            float lit = shadow2DProj(shadowtex0, shadowPos).r;
            lit *= weight(shadow2D(shadowtex1, shadowPos.xyz).r);
            lit += pcf(shadowPos.xyz) + shadow2DLod(shadowtex0, shadowPos.xyz, 0.0).r;
            lit += shadow1D(shadow1d, shadowPos.xyz).r + shadow1DProj(shadow1d, shadowPos).r + shadow1DLod(shadow1d, shadowPos.xyz, 1.0).r;
            vec4 nested = shadow2D(shadowtex0, vec3(shadow2D(shadowtex1, shadowPos.xyz).r));
            vec4 whole = shadow2D(shadowtex0, shadowPos.xyz);
            gl_FragColor = vec4(lit) * nested * whole * max(shadow2DProj(shadowtex1, shadowPos), vec4(0.1));
        }
        """;

    /** Functions with const parameters that initialize const locals, as CompatibilityTransformer.transformEach sees them. */
    static final String CONST_PARAMS_330 = """
        #version 330 core
        out vec4 fragColor;
        float scale(const float a, const in float b, in const float c, float d) {
            const float x = a * 2.0;
            const highp float y = x + 1.0;
            float w = b;
            const float z = w;
            const float u = 1.0, v = c;
            const float e = 3.0, h = a;
            const float k = 4.0;
            return x + y + z + u + v + e + h + k + d;
        }
        float scale(const vec2 a) { const float x = a.x; return x; }
        vec3 other(const vec3 p) { const vec3 q = p * 2.0; const float r = length(q); float s[2] = float[2](r, r); return q * s[1]; }
        vec4 field(const float g, vec4 c) { const float q = c.g; const float early = t0; const float t0 = g; return vec4(q + early + t0); }
        void main() {
            const float x = 1.0;
            fragColor = vec4(scale(1.0, 2.0, 3.0, 4.0) + scale(vec2(x)), other(vec3(x))) + field(x, vec4(x));
        }
        """;

    /** Unused functions: a chain, a prototype, a name used only as a local, overloads. */
    static final String UNUSED_330 = """
        #version 330 core
        out vec4 fragColor;
        float helperOfHelper(float x) { return x * 2.0; }
        float unusedHelper(float x) { return helperOfHelper(x); }
        float forward(float x);
        float onlyDeclared(float x);
        float used(float x) { return forward(x); }
        float forward(float x) { return x + 1.0; }
        float sharesALocalsName(float x) { return x; }
        float overloaded(float x) { return x; }
        float overloaded(vec2 x) { return x.x; }
        float recursiveA(float x);
        float recursiveB(float x) { return recursiveA(x); }
        float recursiveA(float x) { return recursiveB(x); }
        void main() {
            float sharesALocalsName = 1.0;
            fragColor = vec4(used(sharesALocalsName) + overloaded(2.0));
        }
        """;

    /** AdaptiveShadowBoundsTransformer's two PCF helper shapes, with the brief's shadow-sampler overload. */
    static final String PCF_330 = """
        #version 330 core
        uniform sampler2D shadowtex0;
        uniform sampler2DShadow shadowtex1;
        const int shadowMapResolution = 2048;
        in vec3 worldPos;
        out vec4 fragColor;
        float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
            vec2 texel = shadowPos.xy * shadowMapResolution;
            return step(shadowPos.z, texture(shadowtex, texel / shadowMapResolution).r);
        }
        float texture2DShadow2x2(sampler2DShadow shadowtex, vec3 shadowPos) {
            return texture(shadowtex, shadowPos);
        }
        vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float bias) {
            if (abs(shadowPos.x) > 1.0) return vec3(1.0);
            return vec3(texture(shadowtex1, shadowPos + vec3(offset, 0.0, -bias)));
        }
        highp float lowered(in highp vec3 p, float w[2], vec3[2] q, float) { return p.x + w[0] + q[1].x; }
        void main() {
            fragColor = vec4(SampleFilteredShadow(worldPos, 0.001, 0.0002) * texture2DShadow2x2(shadowtex0, worldPos)
                * texture2DShadow2x2(shadowtex1, worldPos) * lowered(worldPos, float[2](1.0, 2.0), vec3[2](worldPos, worldPos), 0.0), 1.0);
        }
        """;

    /** transformGrouped's previous stage: multi-declarator outs, arrays on the name and on the type, a flat out. */
    static final String GROUPED_VERTEX_330 = """
        #version 330 core
        in vec3 position;
        in vec4 vaColor;
        out float mat, recolor;
        flat out float isMoon;
        out vec3 normals[2];
        out vec3[2] tangents;
        out vec2 texCoord;
        out vec4 tintOut;
        layout(location = 3) out vec4 extra;
        uniform mat4 mvp;
        void main() {
            mat = 1.0;
            texCoord = position.xy;
            tintOut = vaColor;
            normals[0] = position;
            gl_Position = mvp * vec4(position, 1.0);
        }
        """;

    /** transformGrouped's current stage: ins that match, ins the previous stage lacks, gl_ names, unused ins. */
    static final String GROUPED_FRAGMENT_330 = """
        #version 330 core
        in float mat, recolor;
        flat in float isMoon;
        in vec3 normals[2];
        in vec3[2] tangents;
        in vec2 texCoord;
        in vec4 tint;
        in vec4 color;
        in vec3 unusedIn;
        in ivec2 cell;
        flat in uint id;
        in mat3 tbn;
        in vec4 gl_SecondaryColor;
        layout(location = 3) in vec4 extra;
        in float a, b;
        out vec4 fragColor;
        void main() {
            fragColor = color * mat * recolor * isMoon + vec4(normals[0] + tangents[1], 1.0) + vec4(texCoord, float(cell.x), float(id))
                + tint + vec4(tbn[0], a) + gl_SecondaryColor + extra;
        }
        """;

    /** Every type TauMC's initialize knows, and a struct. */
    static final String INITIALIZE_400 = """
        #version 400 core
        in float f;
        in vec3 v3;
        in int i;
        in ivec2 iv;
        in uint u;
        in uvec4 uv;
        in bool b;
        in bvec2 bv;
        in mat3 m;
        in mat2x2 m22;
        in mat2x3 m23;
        in mat4 m4;
        struct S { float a; };
        in S s;
        out vec4 o;
        void main() { o = vec4(1.0); }
        """;

    static final List<String> STEP4_FIXTURES = List.of(SHADOW_120, CONST_PARAMS_330, UNUSED_330, PCF_330,
        GROUPED_VERTEX_330, GROUPED_FRAGMENT_330, INITIALIZE_400);

    // ---------------------------------------------------------------------------------------------------------------
    // Step 4: the structural verbs

    /** A shadow2DLod call inside a shadow2D call, and one after it, in const initializers fed by a const parameter. */
    static final String WRAP_ORDER_330 = """
        #version 330 core
        uniform sampler2DShadow s;
        out vec4 o;
        float f(const vec3 x) {
            const float a = shadow2D(s, vec3(shadow2DLod(s, x, 0.0).r)).r;
            const float b = shadow2DLod(s, vec3(a), 0.0).r;
            return b;
        }
        void main() { o = vec4(f(vec3(0.5))); }
        """;

    /**
     * Named deviation (S4 verification): TauMC rebuilt the wrapped call from its parse-tree text, whose tokens are joined
     * without whitespace, so {@code p.z - -0.001} became {@code p.z--0.001}, a decrement: TauMC's re-parse fails and its
     * error recovery leaves broken GLSL (the call not even renamed). ShaderAst prints the call from the AST.
     */
    @Test
    void deviationWrappedShadowCallKeepsANegatedLiteral() {
        final String source = "#version 330 core\nuniform sampler2DShadow s;\nin vec3 p;\nout vec4 o;\n"
            + "void main() { o = vec4(shadow2D(s, vec3(p.xy, p.z - -0.001)).r); }\n";
        final String taumc = viaTauMC(source, t -> t.renameAndWrapShadow("shadow2D", "texture"));
        final String adapter = viaShaderAst(source, a -> a.renameAndWrapShadow("shadow2D", "texture"));
        assertTrue(GlslTokens.contains(taumc, "p . z -- 0.001"), taumc);
        assertTrue(GlslTokens.contains(adapter, "o = vec4 ( vec4 ( texture ( s , vec3 ( p . xy , p . z - - 0.001 ) ) ) . r ) ;"), adapter);
        ShaderAst.parse("#version 330 core\n" + adapter);
        assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse("#version 330 core\n" + taumc));
    }

    private static final String[] COMMON_SHADOW_RENAMES = {"shadow2D", "texture", "shadow2DLod", "textureLod"};
    private static final String[] COMPAT_SHADOW_RENAMES = {"shadow2D", "texture", "shadow2DLod", "textureLod", "shadow1D", "texture",
        "shadow1DProj", "textureProj", "shadow2DProj", "textureProj", "shadow1DLod", "textureLod"};

    static void wrapShadowsTauMC(Transformer transformer, String[] renames) {
        for (int i = 0; i < renames.length; i += 2) {
            transformer.renameAndWrapShadow(renames[i], renames[i + 1]);
        }
    }

    static void wrapShadows(ShaderAst ast, String[] renames) {
        for (int i = 0; i < renames.length; i += 2) {
            ast.renameAndWrapShadow(renames[i], renames[i + 1]);
        }
    }

    @TestFactory
    Stream<DynamicTest> renameAndWrapShadow() {
        final String userOverload = "#version 120\nuniform sampler2D s;\nvarying vec3 p;\n"
            + "vec4 shadow2DLod(sampler2D t, vec3 q, float l) { return texture2DLod(t, q.xy, l); }\n"
            + "void main() { gl_FragColor = shadow2DLod(s, p, 0.0).rrrr; }\n";
        return Stream.of(
            changingParity("CommonTransformer's two renames", SHADOW_120,
                t -> wrapShadowsTauMC(t, COMMON_SHADOW_RENAMES), a -> wrapShadows(a, COMMON_SHADOW_RENAMES)),
            changingParity("CompatShaderTransformer's six renames", SHADOW_120,
                t -> wrapShadowsTauMC(t, COMPAT_SHADOW_RENAMES), a -> wrapShadows(a, COMPAT_SHADOW_RENAMES)),
            changingParity("shadow2DProj(s, p).r alone", SHADOW_120,
                t -> t.renameAndWrapShadow("shadow2DProj", "textureProj"), a -> a.renameAndWrapShadow("shadow2DProj", "textureProj")),
            changingParity("the nested call: only the outer one is wrapped", SHADOW_120,
                t -> t.renameAndWrapShadow("shadow2D", "texture"), a -> a.renameAndWrapShadow("shadow2D", "texture")),
            changingParity("a 120 gbuffers shader's shadow2D(...).r in a compound assignment", FRAGMENT_120,
                t -> wrapShadowsTauMC(t, COMPAT_SHADOW_RENAMES), a -> wrapShadows(a, COMPAT_SHADOW_RENAMES)),
            parity("a program without shadow calls is left alone", FRAGMENT_330,
                t -> wrapShadowsTauMC(t, COMPAT_SHADOW_RENAMES), a -> wrapShadows(a, COMPAT_SHADOW_RENAMES)),
            changingParity("a user function of the name: its calls are wrapped, its prototype renamed", userOverload,
                t -> t.renameAndWrapShadow("shadow2DLod", "textureLod"), a -> a.renameAndWrapShadow("shadow2DLod", "textureLod")),
            // Step 5 (S4 verification): the second rename wraps the program's own shadow2DLod call before the one inside
            // the first rename's wrapper, as TauMC's cache order has it; removeConstAssignment then reaches x (in the
            // later wrapper) after a, so b keeps its const. In document order both would lose it.
            changingParity("a second rename's wrappers in TauMC's cache order, as removeConstAssignment sees them", WRAP_ORDER_330,
                t -> { wrapShadowsTauMC(t, COMMON_SHADOW_RENAMES); t.removeConstAssignment(); },
                a -> { wrapShadows(a, COMMON_SHADOW_RENAMES); a.removeConstAssignment(); }),
            changingParity("after an injection and a replacement (added nodes are wrapped too)", SHADOW_120,
                t -> { t.injectFunction("float iris_s(vec3 q) { return shadow2D(shadowtex0, q).r; }"); t.replaceExpression("whole", "shadow2D(shadowtex1, shadowPos.xyz)"); wrapShadowsTauMC(t, COMPAT_SHADOW_RENAMES); },
                a -> { a.injectFunction("float iris_s(vec3 q) { return shadow2D(shadowtex0, q).r; }"); a.replaceExpression("whole", "shadow2D(shadowtex1, shadowPos.xyz)"); wrapShadows(a, COMPAT_SHADOW_RENAMES); })
        );
    }

    @TestFactory
    Stream<DynamicTest> removeUnusedFunctions() {
        final List<DynamicTest> tests = new ArrayList<>();
        tests.add(changingParity("a chain of unused helpers, prototypes, a local of a function's name, overloads, recursion", UNUSED_330,
            Transformer::removeUnusedFunctions, ShaderAst::removeUnusedFunctions));
        tests.add(changingParity("after an injected unused function", UNUSED_330,
            t -> { t.injectFunction("float iris_unused(float x) { return x; }"); t.removeUnusedFunctions(); },
            a -> { a.injectFunction("float iris_unused(float x) { return x; }"); a.removeUnusedFunctions(); }));
        tests.add(parity("only main", "#version 330 core\nout vec4 o;\nvoid main() { o = vec4(1.0); }\n",
            Transformer::removeUnusedFunctions, ShaderAst::removeUnusedFunctions));
        for (int i = 0; i < FIXTURES.size(); i++) {
            tests.add(parity("fixture " + i, FIXTURES.get(i), Transformer::removeUnusedFunctions, ShaderAst::removeUnusedFunctions));
        }
        for (int i = 0; i < STEP4_FIXTURES.size(); i++) {
            tests.add(parity("step 4 fixture " + i, STEP4_FIXTURES.get(i), Transformer::removeUnusedFunctions, ShaderAst::removeUnusedFunctions));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> removeConstAssignment() {
        final List<DynamicTest> tests = new ArrayList<>();
        tests.add(changingParity("first qualifier const, chains, a second declarator, a member name, overloads", CONST_PARAMS_330,
            Transformer::removeConstAssignment, ShaderAst::removeConstAssignment));
        tests.add(changingParity("transformEach's order: unused functions, then const assignments", CONST_PARAMS_330,
            t -> { t.removeUnusedFunctions(); t.removeConstAssignment(); }, a -> { a.removeUnusedFunctions(); a.removeConstAssignment(); }));
        tests.add(changingParity("a const parameter in a prototype and its definition", "#version 330 core\nout vec4 o;\n"
                + "float f(const float a);\nvoid main() { o = vec4(f(1.0)); }\nfloat f(const float a) { const float b = a; const float c = b * b; return c; }\n",
            Transformer::removeConstAssignment, ShaderAst::removeConstAssignment));
        tests.add(changingParity("a for initializer and an array size", "#version 330 core\nout vec4 o;\n"
                + "float f(const int n) { const int m = n; float s = 0.0; for (int i = m; i < 4; i++) { s += 1.0; } return s; }\nvoid main() { o = vec4(f(2)); }\n",
            Transformer::removeConstAssignment, ShaderAst::removeConstAssignment));
        tests.add(parity("a const in main initialized from a const global stays", "#version 330 core\nconst float g = 1.0;\nout vec4 o;\n"
                + "float f(const float a) { return a; }\nvoid main() { const float x = g; o = vec4(f(x)); }\n",
            Transformer::removeConstAssignment, ShaderAst::removeConstAssignment));
        for (int i = 0; i < FIXTURES.size(); i++) {
            tests.add(parity("fixture " + i, FIXTURES.get(i), Transformer::removeConstAssignment, ShaderAst::removeConstAssignment));
        }
        return tests.stream();
    }

    // TauMC's findQualifiers as rows: name | type as ShaderPrinter prints it | type getText | type keyword | type array.
    static List<String> taumcQualifiers(Transformer transformer, int token) {
        final List<String> rows = new ArrayList<>();
        for (Map.Entry<String, GLSLParser.Single_declarationContext> entry : transformer.findQualifiers(token).entrySet()) {
            final GLSLParser.Fully_specified_typeContext type = entry.getValue().fully_specified_type();
            final GLSLParser.Type_specifierContext specifier = type.type_specifier();
            rows.add(entry.getKey() + " | " + GlslTokens.of(ShaderPrinter.getFormattedShader(type)).text()
                + " | " + squareMatrix(type.getText())
                + " | " + specifier.type_specifier_nonarray().children.get(0).getText()
                + " | " + (specifier.array_specifier() == null ? "-" : specifier.array_specifier().getText()));
        }
        return rows;
    }

    static List<String> adapterQualifiers(ShaderAst ast, StorageQualifier.StorageType type) {
        final List<String> rows = new ArrayList<>();
        for (Map.Entry<String, ShaderAst.QualifiedDeclaration> entry : ast.findQualifiers(type).entrySet()) {
            final ShaderAst.QualifiedDeclaration declaration = entry.getValue();
            assertEquals(entry.getKey(), declaration.name());
            assertEquals(entry.getKey(), declaration.member().getName().getName());
            rows.add(entry.getKey() + " | " + GlslTokens.of(declaration.typeText()).text()
                + " | " + declaration.typeText().replaceAll("\\s+", "")
                + " | " + declaration.typeName()
                + " | " + (declaration.arraySpecifierText() == null ? "-" : declaration.arraySpecifierText().replaceAll("\\s+", "")));
        }
        return rows;
    }

    // TauMC keeps a square matrix's spelling; glsl-transformer prints it by its short name (typeText; typeName keeps
    // the spelling since Step 5).
    private static String squareMatrix(String text) {
        return text.replaceAll("(d?mat)([234])x\\2", "$1$2");
    }

    /**
     * The answers AdaptiveShadowBoundsTransformer's hasBoundsGuard and isSupportedPcfBody read from a body's text without
     * whitespace (TauMC's getText(), ShaderAst's FunctionInfo.bodyText()), for every parameter as the coordinate: the
     * text searches Step 7 ports. The texts themselves differ in literal spelling (1.0 against 1.0f), which no needle
     * contains.
     */
    static String boundsNeedles(String body, List<String> parameters) {
        final StringBuilder flags = new StringBuilder();
        for (String needle : List.of("shadowMapResolution", "texture", "shadow2D", "getShadow", "shadowtex")) {
            flags.append(body.contains(needle) ? '1' : '0');
        }
        final String normalized = body.toLowerCase(java.util.Locale.ROOT);
        flags.append(normalized.contains("shadowbounds") ? '1' : '0').append(normalized.contains("issampleinshadowmap") ? '1' : '0');
        for (String parameter : parameters) {
            final String p = parameter.toLowerCase(java.util.Locale.ROOT);
            for (String needle : List.of("abs(" + p + ".x)", p + ".x>", p + ".x<", p + ".y>", p + ".y<")) {
                flags.append(normalized.contains(needle) ? '1' : '0');
            }
        }
        return flags.toString();
    }

    static final Map<StorageQualifier.StorageType, Integer> STORAGE_TOKENS = Map.of(
        StorageQualifier.StorageType.IN, GLSLLexer.IN, StorageQualifier.StorageType.OUT, GLSLLexer.OUT,
        StorageQualifier.StorageType.UNIFORM, GLSLLexer.UNIFORM, StorageQualifier.StorageType.CONST, GLSLLexer.CONST,
        StorageQualifier.StorageType.ATTRIBUTE, GLSLLexer.ATTRIBUTE, StorageQualifier.StorageType.VARYING, GLSLLexer.VARYING,
        StorageQualifier.StorageType.CENTROID, GLSLLexer.CENTROID);

    /** Key order (TauMC's HashMap order) and, per name, the type text, keyword and array, for every storage type. */
    @TestFactory
    Stream<DynamicTest> findQualifiers() {
        final List<DynamicTest> tests = new ArrayList<>();
        final List<String> sources = new ArrayList<>(FIXTURES);
        sources.addAll(STEP4_FIXTURES);
        sources.add("#version 330 core\nout float mat, recolor;\nout vec3 v[2];\nout vec3[2] w;\nlayout(location = 0) out vec4 c;\n"
            + "out Block { vec4 q; } blk;\nconst float K = 1.0;\nin vec3 gl_Thing;\ncentroid out vec2 cv;\nlayout(std140) uniform U { float f; };\n"
            + "uniform struct Light { vec3 p; } light;\nvoid f(in vec3 pp, out float r) { const float local = 2.0; r = pp.x * local; }\nvoid main() { }\n");
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            for (Map.Entry<StorageQualifier.StorageType, Integer> type : STORAGE_TOKENS.entrySet()) {
                tests.add(DynamicTest.dynamicTest("source " + i + ", " + type.getKey(), () -> {
                    final Transformer transformer = new Transformer(ShaderParser.parseShader(source).full());
                    final ShaderAst ast = ShaderAst.parse(source);
                    assertEquals(taumcQualifiers(transformer, type.getValue()), adapterQualifiers(ast, type.getKey()));
                }));
            }
        }
        // After injections: TauMC inserts the injected names in its cache order, which decides collisions in the map.
        final String many = "#version 330 core\n" + String.join("", java.util.stream.IntStream.range(0, 14)
            .mapToObj(i -> "out float o" + i + ";\n").toList()) + "void main() { }\n";
        tests.add(DynamicTest.dynamicTest("after injections (collisions in the map follow TauMC's cache order)", () -> {
            final Transformer transformer = new Transformer(ShaderParser.parseShader(many).full());
            final ShaderAst ast = ShaderAst.parse(many);
            for (String name : List.of("pa", "iris_FogFragCoord", "iris_FrontColor", "qa", "o3")) {
                transformer.injectVariable("out vec4 " + name + ";");
                ast.injectVariable("out vec4 " + name + ";");
            }
            assertEquals(taumcQualifiers(transformer, GLSLLexer.OUT), adapterQualifiers(ast, StorageQualifier.StorageType.OUT));
        }));
        return tests.stream();
    }

    @Test
    void findQualifiersReportsTheTypeWithItsQualifiers() {
        // CompatibilityTransformerTest expects TauMC's getText() of isMoon's type to be "flatoutfloat".
        final Map<String, ShaderAst.QualifiedDeclaration> outs = ShaderAst.parse(GROUPED_VERTEX_330).findQualifiers(StorageQualifier.StorageType.OUT);
        assertEquals("flat out float", outs.get("isMoon").typeText());
        assertEquals("float", outs.get("isMoon").typeName());
        assertEquals("out float", outs.get("recolor").typeText());
        assertTrue(outs.get("mat").declaration() == outs.get("recolor").declaration());
        assertNull(outs.get("normals").arraySpecifierText());
        assertNotNull(outs.get("normals").member().getArraySpecifier());
        assertEquals("[2]", outs.get("tangents").arraySpecifierText());
        assertEquals("layout(location = 3) out vec4", outs.get("extra").typeText());
        assertThrows(UnsupportedOperationException.class, () -> outs.remove("mat"));
    }

    @TestFactory
    Stream<DynamicTest> hasAssignment() {
        final String prefixes = "#version 330 core\nout vec4 color; out vec4 colorOut; out vec4 tint; out float k; out vec4 arr[2];\n"
            + "void main() { colorOut = vec4(1.0); tint.rgb = vec3(1.0); k += 1.0; arr[0] = vec4(0.0); tint++; }\n";
        final List<DynamicTest> tests = new ArrayList<>();
        tests.add(queryParity("text prefixes, members, compound assignments, increments", prefixes,
            List.of("color", "colorOut", "colorO", "col", "tint", "k", "arr", "o", "main"), Transformer::hasAssigment, ShaderAst::hasAssignment));
        final List<String> sources = new ArrayList<>(FIXTURES);
        sources.addAll(STEP4_FIXTURES);
        for (int i = 0; i < sources.size(); i++) {
            tests.add(queryParity("every identifier of source " + i, sources.get(i), identifierNames(sources.get(i)),
                Transformer::hasAssigment, ShaderAst::hasAssignment));
        }
        return tests.stream();
    }

    static List<String> identifierNames(String source) {
        final List<String> names = new ArrayList<>(new TreeSet<>(GlslTokens.of(source).tokens().stream()
            .filter(token -> token.matches("[A-Za-z_][A-Za-z0-9_]*")).toList()));
        names.add("iris_absent");
        return names;
    }

    @TestFactory
    Stream<DynamicTest> initialize() {
        final List<DynamicTest> tests = new ArrayList<>();
        for (String name : List.of("f", "v3", "i", "iv", "u", "uv", "b", "bv", "m", "m22", "m23", "m4", "s")) {
            final Consumer<Transformer> taumc = t -> t.initialize(t.findQualifiers(GLSLLexer.IN).get(name), name + "_out");
            final Consumer<ShaderAst> adapter = a -> a.initialize(a.findQualifiers(StorageQualifier.StorageType.IN).get(name), name + "_out");
            // A struct type initializes nothing, in TauMC as here.
            tests.add(name.equals("s") ? parity(name, INITIALIZE_400, taumc, adapter) : changingParity(name, INITIALIZE_400, taumc, adapter));
        }
        tests.add(changingParity("a second declarator and a flat declaration", GROUPED_VERTEX_330,
            t -> { t.initialize(t.findQualifiers(GLSLLexer.OUT).get("recolor"), "recolor"); t.initialize(t.findQualifiers(GLSLLexer.OUT).get("isMoon"), "isMoon"); },
            a -> { a.initialize(a.findQualifiers(StorageQualifier.StorageType.OUT).get("recolor"), "recolor"); a.initialize(a.findQualifiers(StorageQualifier.StorageType.OUT).get("isMoon"), "isMoon"); }));
        tests.add(changingParity("an array on the type initializes its element type, as in TauMC", GROUPED_VERTEX_330,
            t -> t.initialize(t.findQualifiers(GLSLLexer.OUT).get("tangents"), "tangents"),
            a -> a.initialize(a.findQualifiers(StorageQualifier.StorageType.OUT).get("tangents"), "tangents")));
        return tests.stream();
    }

    @Test
    void deviationInitializeDoubles() {
        // TauMC writes 0.0d, which its own parser reads as 0.0 and an error (and a vector initializer as "v = ;").
        final String source = "#version 400 core\nin double d;\nin dvec2 dv;\nout vec4 o;\nvoid main() { o = vec4(1.0); }\n";
        final String taumc = viaTauMC(source, t -> {
            t.initialize(t.findQualifiers(GLSLLexer.IN).get("d"), "d_out");
            t.initialize(t.findQualifiers(GLSLLexer.IN).get("dv"), "dv_out");
        });
        assertTrue(GlslTokens.contains(taumc, "d_out = 0.0 d"), taumc);
        assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse("#version 400 core\n" + taumc), taumc);
        final String adapter = viaShaderAst(source, a -> {
            a.initialize(a.findQualifiers(StorageQualifier.StorageType.IN).get("d"), "d_out");
            a.initialize(a.findQualifiers(StorageQualifier.StorageType.IN).get("dv"), "dv_out");
        });
        assertTrue(GlslTokens.contains(adapter, "dv_out = dvec2 ( 0.0lf ) ; d_out = 0.0lf ;"), adapter);
    }

    /**
     * The glsl-transformer engine's {@code transformGrouped}
     * ({@link net.coderbot.iris.pipeline.transform.transformer.CompatibilityTransformer#transformGrouped}), which
     * Step 5 lifted from this class, where Step 4 wrote it against {@link ShaderAst} line for line from TauMC's.
     */
    static void transformGrouped(Map<PatchShaderType, ShaderAst> trees) {
        net.coderbot.iris.pipeline.transform.transformer.CompatibilityTransformer.transformGrouped(trees, null);
    }

    /** Runs TauMC's transformGrouped and {@link #transformGrouped} on the same stages; the printed stages, or the throw. */
    static Map<PatchShaderType, String[]> groupedOnBoth(Map<PatchShaderType, String> stages) {
        final Map<PatchShaderType, Transformer> taumc = new java.util.EnumMap<>(PatchShaderType.class);
        final Map<PatchShaderType, ShaderAst> adapter = new java.util.EnumMap<>(PatchShaderType.class);
        stages.forEach((stage, source) -> {
            taumc.put(stage, new Transformer(ShaderParser.parseShader(source).full()));
            adapter.put(stage, ShaderAst.parse(source));
        });
        CompatibilityTransformer.transformGrouped(taumc, null);
        transformGrouped(adapter);
        final Map<PatchShaderType, String[]> printed = new java.util.EnumMap<>(PatchShaderType.class);
        stages.keySet().forEach(stage -> {
            final StringBuilder text = new StringBuilder();
            taumc.get(stage).mutateTree(tree -> text.append(GlslTransformUtils.getFormattedShader(tree, "")));
            printed.put(stage, new String[]{text.toString(), adapter.get(stage).printBody()});
        });
        return printed;
    }

    @TestFactory
    Stream<DynamicTest> transformGroupedWrittenOnShaderAst() {
        final String geometry = """
            #version 330 core
            layout(triangles) in;
            layout(triangle_strip, max_vertices = 3) out;
            in vec2 texCoord[];
            in vec4 color[];
            out vec2 texCoord;
            out vec4 color;
            out float mat;
            void main() { texCoord = texCoord[0]; color = color[0]; EmitVertex(); }
            """;
        final String varyings = "#version 120\nvarying vec2 lm;\nvoid main() { gl_Position = ftransform(); }\n";
        final String varyingsFragment = "#version 120\nvarying vec2 lm;\nvarying vec4 missing;\nvoid main() { gl_FragColor = missing * lm.x; }\n";
        final Map<String, Map<PatchShaderType, String>> cases = new LinkedHashMap<>();
        cases.put("vertex and fragment", Map.of(PatchShaderType.VERTEX, GROUPED_VERTEX_330, PatchShaderType.FRAGMENT, GROUPED_FRAGMENT_330));
        cases.put("vertex, geometry and fragment", Map.of(PatchShaderType.VERTEX, GROUPED_VERTEX_330, PatchShaderType.GEOMETRY, geometry,
            PatchShaderType.FRAGMENT, GROUPED_FRAGMENT_330));
        cases.put("120 varyings (no in or out: nothing to pair)", Map.of(PatchShaderType.VERTEX, varyings, PatchShaderType.FRAGMENT, varyingsFragment));
        cases.put("fixtures: 330 fragment after the 120 vertex", Map.of(PatchShaderType.VERTEX, VERTEX_120, PatchShaderType.FRAGMENT, FRAGMENT_330));
        // S4 verification: TauMC compared the spelled types, so mat2x2 against mat2 (and mat3 against mat3x3) differ and
        // the unassigned outs are not initialized; comparing glsl-transformer's Type would initialize both.
        cases.put("square matrices spelled differently: nothing initialized", Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 pos;\nout mat2x2 m;\nout mat3 k;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nin mat2 m;\nin mat3x3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n"));
        cases.put("square matrices spelled alike: both initialized", Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 pos;\nout mat2x2 m;\nout mat3 k;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nin mat2x2 m;\nin mat3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n"));
        final List<DynamicTest> tests = new ArrayList<>();
        cases.forEach((name, stages) -> tests.add(DynamicTest.dynamicTest(name, () -> {
            final Map<PatchShaderType, String[]> printed = groupedOnBoth(stages);
            printed.forEach((stage, both) -> {
                final String diff = GlslTokens.diff(both[0], both[1]);
                assertTrue(diff.isEmpty(), () -> stage + ": TauMC and ShaderAst differ (- TauMC, + ShaderAst):\n" + diff);
            });
            // Every case but the one without ins and outs and the differently spelled matrices changes the vertex stage.
            final boolean changed = !GlslTokens.of(printed.get(PatchShaderType.VERTEX)[0])
                .equals(GlslTokens.of(viaTauMC(stages.get(PatchShaderType.VERTEX), t -> { })));
            assertEquals(!name.startsWith("120 varyings") && !name.contains("spelled differently"), changed,
                "TauMC changed the vertex stage");
        })));
        return tests.stream();
    }

    // TauMC's side of replaceFunctionDefinition: AdaptiveShadowBoundsTransformer's print and three-argument replace.
    static void replaceFunctionTauMC(Transformer transformer, String name, int overload, java.util.function.UnaryOperator<String> patch) {
        final List<GLSLParser.Function_definitionContext> definitions = new ArrayList<>();
        transformer.mutateTree(tree -> org.antlr.v4.runtime.tree.ParseTreeWalker.DEFAULT.walk(new org.taumc.glsl.grammar.GLSLParserBaseListener() {
            @Override
            public void enterFunction_definition(GLSLParser.Function_definitionContext context) {
                if (context.function_prototype().IDENTIFIER().getText().equals(name)) {
                    definitions.add(context);
                }
            }
        }, tree));
        final String source = GlslTransformUtils.getFormattedShader(definitions.get(overload), "");
        transformer.replaceExpression(source, patch.apply(source), GLSLParser::function_definition);
    }

    static void replaceFunction(ShaderAst ast, String name, int overload, java.util.function.UnaryOperator<String> patch) {
        final ShaderAst.FunctionInfo function = ast.functions().stream().filter(f -> f.name().equals(name)).toList().get(overload);
        assertEquals(1, ast.replaceFunctionDefinition(name, patch.apply(ShaderAst.source(function.node()))));
    }

    static java.util.function.UnaryOperator<String> afterFirstBrace(String statement) {
        return source -> source.substring(0, source.indexOf('{') + 1) + statement + source.substring(source.indexOf('{') + 1);
    }

    @TestFactory
    Stream<DynamicTest> replaceFunctionDefinition() {
        final String guard = "if (!(shadowPos.x > 1.5 / shadowMapResolution && shadowPos.z < 1.0)) return 1.0;";
        return Stream.of(
            changingParity("the sampler2D overload of texture2DShadow2x2", PCF_330,
                t -> replaceFunctionTauMC(t, "texture2DShadow2x2", 0, afterFirstBrace(guard)),
                a -> replaceFunction(a, "texture2DShadow2x2", 0, afterFirstBrace(guard))),
            changingParity("the sampler2DShadow overload of texture2DShadow2x2", PCF_330,
                t -> replaceFunctionTauMC(t, "texture2DShadow2x2", 1, afterFirstBrace(guard)),
                a -> replaceFunction(a, "texture2DShadow2x2", 1, afterFirstBrace(guard))),
            changingParity("SampleFilteredShadow with the instrumented guard, then the stats buffer", PCF_330,
                t -> { replaceFunctionTauMC(t, "SampleFilteredShadow", 0, afterFirstBrace("atomicCounterIncrement(iris_calls); if (!(shadowPos.x > 0.0)) { return vec3(1.0); }")); t.injectVariable("layout(binding = 95) uniform atomic_uint iris_calls;"); },
                a -> { replaceFunction(a, "SampleFilteredShadow", 0, afterFirstBrace("atomicCounterIncrement(iris_calls); if (!(shadowPos.x > 0.0)) { return vec3(1.0); }")); a.injectVariable("layout(binding = 95) uniform atomic_uint iris_calls;"); }),
            changingParity("the first function replaced before the first injection: TauMC's anchor moves on", FUNCTION_FIRST_330,
                t -> { replaceFunctionTauMC(t, "helper", 0, afterFirstBrace("x += 1.0;")); t.injectVariable("uniform float iris_after;"); },
                a -> { replaceFunction(a, "helper", 0, afterFirstBrace("x += 1.0;")); a.injectVariable("uniform float iris_after;"); }),
            changingParity("the anchor function replaced after an injection: the anchor stays with it", FUNCTION_FIRST_330,
                t -> { t.injectVariable("uniform float iris_before;"); replaceFunctionTauMC(t, "helper", 0, afterFirstBrace("x += 1.0;")); t.injectVariable("uniform float iris_after;"); t.injectFunction("float iris_f() { return 1.0; }"); },
                a -> { a.injectVariable("uniform float iris_before;"); replaceFunction(a, "helper", 0, afterFirstBrace("x += 1.0;")); a.injectVariable("uniform float iris_after;"); a.injectFunction("float iris_f() { return 1.0; }"); })
        );
    }

    @Test
    void replaceFunctionDefinitionRejectsOtherSources() {
        final ShaderAst ast = ShaderAst.parse(PCF_330);
        assertThrows(IllegalArgumentException.class, () -> ast.replaceFunctionDefinition("lowered", "uniform float lowered;"));
        assertEquals(0, ast.replaceFunctionDefinition("lowered", "float lowered(float p) { return p; }"), "no overload of that signature");
        assertEquals(0, ast.replaceFunctionDefinition("absent", "float absent() { return 1.0; }"));
        // The node form parses into the program's root first; a rejected parse must leave the index clean.
        assertThrows(IllegalArgumentException.class,
            () -> ast.replaceFunctionDefinition(ast.functions().get(0).node(), "uniform float iris_rejected;"));
        assertFalse(ast.hasVariable("iris_rejected"));
        assertTrue(GlslTokens.of(ast.printBody()).equals(GlslTokens.of(viaShaderAst(PCF_330, a -> { }))), "nothing changed");
    }

    // TauMC's side of functions(): what AdaptiveShadowBoundsTransformer.FunctionCandidate.from read, as rows.
    static List<String> taumcFunctions(Transformer transformer) {
        final List<String> rows = new ArrayList<>();
        transformer.mutateTree(tree -> org.antlr.v4.runtime.tree.ParseTreeWalker.DEFAULT.walk(new org.taumc.glsl.grammar.GLSLParserBaseListener() {
            @Override
            public void enterFunction_definition(GLSLParser.Function_definitionContext context) {
                final GLSLParser.Function_prototypeContext prototype = context.function_prototype();
                final List<String> parameters = new ArrayList<>();
                if (prototype.function_parameters() != null) {
                    for (GLSLParser.Parameter_declarationContext declaration : prototype.function_parameters().parameter_declaration()) {
                        final GLSLParser.Parameter_declaratorContext declarator = declaration.parameter_declarator();
                        final String type = declarator != null ? declarator.type_specifier().getText()
                            : declaration.parameter_type_specifier().type_specifier().getText();
                        parameters.add(type + " " + (declarator == null ? null : declarator.IDENTIFIER().getText()));
                    }
                }
                final List<String> names = new ArrayList<>();
                parameters.forEach(p -> names.add(p.substring(p.indexOf(' ') + 1)));
                rows.add(prototype.IDENTIFIER().getText() + " | " + prototype.fully_specified_type().getText() + " | " + parameters
                    + " | " + GlslTokens.of(GlslTransformUtils.getFormattedShader(context.compound_statement_no_new_scope(), "")).text()
                    + " | " + boundsNeedles(context.compound_statement_no_new_scope().getText(), names));
            }
        }, tree));
        return rows;
    }

    static List<String> adapterFunctions(ShaderAst ast) {
        final List<String> rows = new ArrayList<>();
        for (ShaderAst.FunctionInfo function : ast.functions()) {
            final List<String> parameters = new ArrayList<>();
            for (ShaderAst.Parameter parameter : function.parameters()) {
                parameters.add(parameter.type().replaceAll("\\s+", "") + " " + parameter.name());
            }
            rows.add(function.name() + " | " + function.returnType().replaceAll("\\s+", "") + " | " + parameters
                + " | " + GlslTokens.of(ShaderAst.text(function.node().getBody())).text()
                + " | " + boundsNeedles(function.bodyText(), function.parameters().stream().map(p -> String.valueOf(p.name())).toList()));
            // bodyText is the printed body without whitespace (its literals are glsl-transformer's: 1.0f, 2.0E-4f).
            assertEquals(ShaderAst.text(function.node().getBody()).replaceAll("\\s+", ""), function.bodyText());
        }
        return rows;
    }

    @TestFactory
    Stream<DynamicTest> functions() {
        final List<DynamicTest> tests = new ArrayList<>();
        final List<String> sources = new ArrayList<>(FIXTURES);
        sources.addAll(STEP4_FIXTURES);
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            tests.add(DynamicTest.dynamicTest("source " + i, () -> assertEquals(
                taumcFunctions(new Transformer(ShaderParser.parseShader(source).full())), adapterFunctions(ShaderAst.parse(source)))));
        }
        return tests.stream();
    }

    // TauMC has no isDeclaredGlobal: a typeless_declaration of the name outside every function definition.
    static boolean taumcDeclaredGlobal(Transformer transformer, String name) {
        final boolean[] found = {false};
        transformer.mutateTree(tree -> org.antlr.v4.runtime.tree.ParseTreeWalker.DEFAULT.walk(new org.taumc.glsl.grammar.GLSLParserBaseListener() {
            @Override
            public void enterTypeless_declaration(GLSLParser.Typeless_declarationContext context) {
                if (context.IDENTIFIER() == null || !context.IDENTIFIER().getText().equals(name)) {
                    return;
                }
                for (org.antlr.v4.runtime.ParserRuleContext parent = context.getParent(); parent != null; parent = parent.getParent()) {
                    if (parent instanceof GLSLParser.Function_definitionContext) {
                        return;
                    }
                }
                found[0] = true;
            }
        }, tree));
        return found[0];
    }

    @TestFactory
    Stream<DynamicTest> isDeclaredGlobal() {
        final List<DynamicTest> tests = new ArrayList<>();
        final List<String> sources = new ArrayList<>(FIXTURES);
        sources.addAll(STEP4_FIXTURES);
        for (int i = 0; i < sources.size(); i++) {
            tests.add(queryParity("every identifier of source " + i, sources.get(i), identifierNames(sources.get(i)),
                ShaderAstParityTest::taumcDeclaredGlobal, ShaderAst::isDeclaredGlobal));
        }
        return tests.stream();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Step 3 leftovers closed in Step 4

    @TestFactory
    Stream<DynamicTest> injectionAnchorFollowsTauMCsCacheOrder() {
        final String qualifiersInMain = "#version 330 core\nvoid main() { const float k = 1.0; gl_FragDepth = k; }\n";
        final String noQualifiers = "#version 330 core\nvec4 g;\nvoid main() { g = vec4(1.0); }\n";
        return Stream.of(
            // The S3 verifier's repro: TauMC 'iris_q; iris_r; main', S3's ShaderAst 'iris_r; iris_q; main'.
            changingParity("a qualified declaration injected as a function does not anchor before the program's own qualifier",
                qualifiersInMain,
                t -> { t.injectFunction("uniform float iris_q;"); t.injectVariable("uniform float iris_r;"); },
                a -> { a.injectFunction("uniform float iris_q;"); a.injectVariable("uniform float iris_r;"); }),
            changingParity("with no qualifier of its own, the injected one is the first", noQualifiers,
                t -> { t.injectFunction("uniform float iris_q;"); t.injectVariable("uniform float iris_r;"); t.injectVariable("vec4 iris_s;"); },
                a -> { a.injectFunction("uniform float iris_q;"); a.injectVariable("uniform float iris_r;"); a.injectVariable("vec4 iris_s;"); }),
            changingParity("a const prepended to main does not count before the program's qualifiers", FUNCTION_FIRST_330,
                t -> { t.prependMain("const float iris_c = 1.0;"); t.injectVariable("uniform float iris_r;"); },
                a -> { a.prependMain("const float iris_c = 1.0;"); a.injectVariable("uniform float iris_r;"); }),
            changingParity("a function injected first anchors everything", qualifiersInMain,
                t -> { t.injectFunction("float iris_f() { return 1.0; }"); t.injectFunction("uniform float iris_q;"); t.injectVariable("uniform float iris_r;"); },
                a -> { a.injectFunction("float iris_f() { return 1.0; }"); a.injectFunction("uniform float iris_q;"); a.injectVariable("uniform float iris_r;"); })
        );
    }

    @Test
    void deviationRemovingADeclarationThatIsAnUnbracedBody() {
        final String source = "#version 330 core\nuniform bool c;\nout vec4 o;\n"
            + "void main() { float y = 0.0; if (c) float x = 1.0; y = 2.0; for (int i = 0; i < 2; i++) float z = 3.0; o = vec4(y); }\n";
        // TauMC removes the declaration and leaves the if without a body, so 'y = 2.0;' becomes the body.
        final String taumc = viaTauMC(source, t -> { t.removeVariable("x"); t.removeVariable("z"); });
        assertTrue(GlslTokens.contains(taumc, "if ( c ) y = 2.0 ;"), taumc);
        // ShaderAst leaves an empty statement (S3 left a null body, and printing threw NullPointerException).
        final String adapter = viaShaderAst(source, a -> { a.removeVariable("x"); a.removeVariable("z"); });
        assertTrue(GlslTokens.contains(adapter, "if ( c ) ; y = 2.0 ;"), adapter);
        assertTrue(GlslTokens.contains(adapter, "for ( int i = 0 ; i < 2 ; i ++ ) ; o = vec4 ( y ) ;"), adapter);
        assertFalse(adapter.contains("float x"), adapter);
        assertFalse(adapter.contains("float z"), adapter);
    }

    @Test
    void deviationContainsCallDoesNotSeeLength() {
        // TauMC's grammar makes the length of arr.length() a variable_identifier; glsl-transformer has a
        // LengthAccessExpression without an identifier (the same gap as deviationRenameLeavesTypeNamesAndLength).
        final String source = "#version 430\nuniform float arr[4];\nout vec4 o;\nvoid main() { o = vec4(float(arr.length())); }\n";
        assertTrue(new Transformer(ShaderParser.parseShader(source).full()).containsCall("length"));
        assertFalse(ShaderAst.parse(source).containsCall("length"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Corpus

    @Test
    void corpusDifferential() throws Exception {
        final String dirValue = System.getProperty(TransformCorpusReplayTest.CORPUS_DIR_PROPERTY, "").trim();
        assumeFalse(dirValue.isEmpty(), "no transform corpus configured (-PglslCorpusDir)");
        final Path corpus = Paths.get(dirValue).toAbsolutePath().normalize();
        final Path reports = Paths.get(System.getProperty("demonica.projectRoot", "."), "build", "reports", "shader-ast-parity");
        final ShaderAstCorpusDifferential.Summary summary = ShaderAstCorpusDifferential.run(corpus, reports);
        summary.lines().forEach(System.out::println);
        assertEquals(0, summary.unexplained(), () -> "unexplained differences; see " + reports);
    }
}
