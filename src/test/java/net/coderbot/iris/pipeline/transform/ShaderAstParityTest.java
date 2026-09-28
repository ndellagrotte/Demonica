package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import io.github.douira.glsl_transformer.util.Type;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

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
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, Step 3): every case runs the verb on both libraries over the same
 * source and compares the printed programs as {@link GlslTokens}. TauMC's side is parsed with
 * {@link ShaderParser#parseShader} and printed with {@link GlslTransformUtils#getFormattedShader}, as the old engine
 * does; the adapter's side with {@link ShaderAst#parse} and {@link ShaderAst#printBody()}.
 *
 * <p>The fixtures are hand-written: GLSL 120 and 330 styles, names that are both a function and a variable,
 * multi-declarator declarations, arrays, nested calls, struct fields and swizzles, parameters, interface blocks and
 * comments. Deliberate deviations (where TauMC throws or writes broken GLSL) are separate tests named
 * {@code deviation...} that assert both behaviours.</p>
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
            replaceParity("an array constructor whose longest name is its size", ARRAY_SIZE_330, "vec2[PAIR_SIZE](a, b)", "iris_pair")
        );
    }

    private static DynamicTest replaceParity(String name, String source, String oldCode, String newCode) {
        return parity(name, source, t -> t.replaceExpression(oldCode, newCode), a -> a.replaceExpression(oldCode, newCode));
    }

    @Test
    void deviationReplacementKeepsItsPrecedence() {
        final String source = "#version 330 core\nuniform float x, c;\nout float y;\nvoid main() { y = x * c; y += x.x; }\n";
        // TauMC splices the text: 'a + b * c' changes the meaning, and in postfix position it keeps only 'a'.
        final String taumc = viaTauMC(source, t -> t.replaceExpression("x", "a + b"));
        assertTrue(GlslTokens.contains(taumc, "y = a + b * c ;"), taumc);
        assertTrue(GlslTokens.contains(taumc, "y += a . x ;"), taumc);
        final String adapter = viaShaderAst(source, a -> a.replaceExpression("x", "a + b"));
        assertTrue(GlslTokens.contains(adapter, "y = ( a + b ) * c ;"), adapter);
        assertTrue(GlslTokens.contains(adapter, "y += ( a + b ) . x ;"), adapter);
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

    @Test
    void deviationTexture2DIsAnIdentifier() {
        // TauMC's lexer makes texture2D and texture3D keywords, so only renameFunctionCall sees them; containsCall and
        // rename never do. glsl-transformer lexes them as identifiers. No Demonica caller asks either verb about them.
        final Transformer transformer = new Transformer(ShaderParser.parseShader(FRAGMENT_120).full());
        assertFalse(transformer.containsCall("texture2D"));
        assertTrue(ShaderAst.parse(FRAGMENT_120).containsCall("texture2D"));
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
