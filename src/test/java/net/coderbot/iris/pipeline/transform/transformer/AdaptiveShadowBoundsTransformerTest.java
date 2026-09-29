package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glsl-transformer engine's {@link AdaptiveShadowBoundsTransformer} (Step 7 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), on the fixtures of the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.AdaptiveShadowBoundsTransformerTest}, which stays until Step 11 and keeps
 * testing the TauMC copy. The assertions use neither library's tree: every output is parsed again with
 * {@link ShaderAst#parse(String)} (it must parse), and read through {@link ShaderAst#functions()},
 * {@link ShaderAst#source} and {@link GlslTokens}.
 */
class AdaptiveShadowBoundsTransformerTest {
    private static final String HEADER = "#version 330 core";

    /** The guard for a {@code float} helper whose coordinate is {@code shadowPos}, as the transformer writes it. */
    private static final String FLOAT_GUARD = "{ if ( ! ( shadowPos . x > 1.5 / shadowMapResolution"
        + " && shadowPos . x < 1.0 - 1.5 / shadowMapResolution && shadowPos . y > 1.5 / shadowMapResolution"
        + " && shadowPos . y < 1.0 - 1.5 / shadowMapResolution && shadowPos . z > 0.0 && shadowPos . z < 1.0 ) )"
        + " return 1.0 ;";

    private static final String BOTH_HELPERS = """
        #version 330 core
        const float shadowMapResolution = 2048.0;
        float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
            return texture(shadowtex, shadowPos.xy).x + 0.0 / shadowMapResolution;
        }
        vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float subsurface) {
            return vec3(texture(shadowtex0, shadowPos.xy).x + offset + subsurface);
        }
        void main() {
            gl_FragColor = vec4(
                texture2DShadow2x2(shadowtex, vec3(0.0))
                + SampleFilteredShadow(vec3(0.0), 0.0, 0.0),
                1.0
            );
        }
        """;

    @Test
    void insertsBoundsGuardIntoRecognizedPcfHelper() {
        final String source = """
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                float shadowSample = texture(shadowtex, shadowPos.xy).x;
                return shadowSample + 0.0 / shadowMapResolution;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0);
            }
            """;
        final ShaderAst output = transform(source, ShaderType.FRAGMENT);

        final GlslTokens helper = definition(output, "texture2DShadow2x2");
        assertEquals(1, helper.count("if"), helper.text());
        // The guard is the body's first statement, and the rest of the body follows unchanged.
        assertTrue(helper.contains(FLOAT_GUARD + " float shadowSample = texture ( shadowtex , shadowPos . xy ) . x ;"),
            helper.text());
        assertTrue(definition(output, "main").contains("texture2DShadow2x2 ( shadowtex , vec3 ( 0.0 ) )"));
        // The replacement keeps the definition's place.
        assertEquals(List.of("texture2DShadow2x2", "main"), names(output));
    }

    @Test
    void insertsVec3GuardIntoSampleFilteredShadow() {
        final ShaderAst output = transform("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float subsurface) {
                return vec3(texture(shadowtex0, shadowPos.xy).x + offset + subsurface);
            }
            void main() {
                gl_FragColor = vec4(SampleFilteredShadow(vec3(0.0), 0.0, 0.0), 1.0);
            }
            """, ShaderType.FRAGMENT);

        final GlslTokens helper = definition(output, "SampleFilteredShadow");
        assertEquals(1, helper.count("if"), helper.text());
        assertTrue(helper.contains("shadowPos . z > 0.0 && shadowPos . z < 1.0 ) ) return vec3 ( 1.0 ) ;"), helper.text());
    }

    @Test
    void insertsGuardsIntoBothPcfHelpersInOneShader() {
        final ShaderAst output = transform(BOTH_HELPERS, ShaderType.FRAGMENT);

        assertEquals(1, definition(output, "texture2DShadow2x2").count("if"));
        assertEquals(1, definition(output, "SampleFilteredShadow").count("if"));
        assertTrue(definition(output, "texture2DShadow2x2").contains(FLOAT_GUARD));
        assertEquals(List.of("texture2DShadow2x2", "SampleFilteredShadow", "main"), names(output));
        // Without the runtime stats nothing else is added.
        assertEquals(0, GlslTokens.of(output.printBody()).count("atomicAdd"));
        assertEquals(0, GlslTokens.of(output.printBody()).count("buffer"));
    }

    @Test
    void instrumentsRecognizedHelpersWhenRuntimeStatsAreEnabled() {
        final ShaderAst ast = ShaderAst.parse(BOTH_HELPERS.replace("#version 330 core", "#version 430 core"));
        AdaptiveShadowBoundsTransformer.transform(ast, ShaderType.FRAGMENT, true, 7);
        final ShaderAst output = ShaderAst.parse(ast.print("#version 430 core"));

        final GlslTokens texture = definition(output, "texture2DShadow2x2");
        final GlslTokens filtered = definition(output, "SampleFilteredShadow");
        assertEquals(3, texture.count("atomicAdd"), texture.text());
        assertEquals(3, filtered.count("atomicAdd"), filtered.text());
        assertEquals(1, texture.count("if"));
        assertEquals(1, filtered.count("if"));
        // The call counter first, then the guard, which counts the rejection and the samples saved before returning.
        assertTrue(texture.contains("{ atomicAdd ( actiniumShadowBoundsStats . textureCalls , 1u ) ; if ( ! ("), texture.text());
        assertTrue(texture.contains("{ atomicAdd ( actiniumShadowBoundsStats . textureRejected , 1u ) ;"
            + " atomicAdd ( actiniumShadowBoundsStats . textureSamplesSaved , 4u ) ; return 1.0 ; }"), texture.text());
        assertTrue(filtered.contains("{ atomicAdd ( actiniumShadowBoundsStats . filteredRejected , 1u ) ;"
            + " atomicAdd ( actiniumShadowBoundsStats . filteredSamplesSaved , 1u ) ; return vec3 ( 1.0 ) ; }"), filtered.text());

        final GlslTokens program = GlslTokens.of(output.printBody());
        assertTrue(program.contains("layout ( std430 , binding = 7 ) buffer ActiniumShadowBoundsStats {"), program.text());
        assertEquals(1, program.count("buffer"), program.text());
    }

    @Test
    void leavesFunctionWithExistingBoundsGuardUntouched() {
        assertHelperUntouched("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                if (abs(shadowPos.x) < 1.0 - 1.5 / shadowMapResolution
                    && abs(shadowPos.y) < 1.0 - 1.5 / shadowMapResolution
                    && abs(shadowPos.z) < 6.0) {
                    return texture(shadowtex, shadowPos.xy).x;
                }
                return 1.0;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0);
            }
            """, ShaderType.FRAGMENT, "texture2DShadow2x2", 1);
    }

    @Test
    void leavesFunctionWithComparisonGuardUntouched() {
        assertHelperUntouched("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float subsurface) {
                if (shadowPos.x < 0.0 || shadowPos.x > 1.0 || shadowPos.y < 0.0 || shadowPos.y > 1.0) return vec3(1.0);
                return vec3(texture(shadowtex0, shadowPos.xy).x + offset + subsurface);
            }
            void main() {
                gl_FragColor = vec4(SampleFilteredShadow(vec3(0.0), 0.0, 0.0), 1.0);
            }
            """, ShaderType.FRAGMENT, "SampleFilteredShadow", 1);
    }

    @Test
    void ignoresWrongSignaturesAndNonPcfFunctions() {
        final String source = """
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2D shadowtex, vec2 shadowPos) {
                return texture(shadowtex, shadowPos).x + 0.0 / shadowMapResolution;
            }
            vec4 sampleColor(sampler2D colorTexture, vec2 uv) {
                return texture(colorTexture, uv);
            }
            void main() {
                gl_FragColor = sampleColor(colorTexture, vec2(0.0));
            }
            """;
        assertHelperUntouched(source, ShaderType.FRAGMENT, "texture2DShadow2x2", 0);
        assertHelperUntouched(source, ShaderType.FRAGMENT, "sampleColor", 0);
    }

    @Test
    void ignoresPcfNameWithoutShadowCoordinateParameter() {
        assertHelperUntouched("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2D shadowtex) {
                return texture(shadowtex, vec2(0.0)).x + 0.0 / shadowMapResolution;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex), 1.0);
            }
            """, ShaderType.FRAGMENT, "texture2DShadow2x2", 0);
    }

    /** Qualifiers on the return type: TauMC read {@code highpfloat}, the adapter {@code highp float}; neither matches. */
    @Test
    void ignoresQualifiedReturnType() {
        assertHelperUntouched("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            highp float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos.xy).x + 0.0 / shadowMapResolution;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0);
            }
            """, ShaderType.FRAGMENT, "texture2DShadow2x2", 0);
    }

    @Test
    void leavesShaderWithoutShadowResolutionUntouched() {
        assertHelperUntouched("""
            #version 330 core
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos.xy).x;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0);
            }
            """, ShaderType.FRAGMENT, "texture2DShadow2x2", 0);
    }

    @Test
    void leavesVertexShaderUntouched() {
        assertHelperUntouched("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos.xy).x + 0.0 / shadowMapResolution;
            }
            void main() {
                gl_Position = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)));
            }
            """, ShaderType.VERTEX, "texture2DShadow2x2", 0);
    }

    /** Of two overloads, only the recognized one is replaced, in its place; the other keeps its body. */
    @Test
    void rewritesOnlyTheRecognizedOverload() {
        final ShaderAst output = transform("""
            #version 330 core
            const float shadowMapResolution = 2048.0;
            float texture2DShadow2x2(sampler2DShadow shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos) + 0.0 / shadowMapResolution;
            }
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos.xy).x + 0.0 / shadowMapResolution;
            }
            void main() {
                gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0);
            }
            """, ShaderType.FRAGMENT);

        final List<ShaderAst.FunctionInfo> functions = output.functions();
        assertEquals(List.of("texture2DShadow2x2", "texture2DShadow2x2", "main"), names(output));
        assertEquals("sampler2DShadow", functions.get(0).parameters().get(0).type());
        assertEquals(0, GlslTokens.of(ShaderAst.source(functions.get(0).node())).count("if"));
        assertEquals("sampler2D", functions.get(1).parameters().get(0).type());
        assertTrue(GlslTokens.of(ShaderAst.source(functions.get(1).node())).contains(FLOAT_GUARD));
    }

    /**
     * A replacement must replace exactly one definition. Two definitions of the same overload (a program no driver
     * accepts, but one that parses) make the first replacement hit both, and the transform fails instead of guessing.
     */
    @Test
    void failsLoudlyWhenAReplacementDoesNotHitExactlyOneDefinition() {
        final String helper = """
            float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
                return texture(shadowtex, shadowPos.xy).x + 0.0 / shadowMapResolution;
            }
            """;
        final ShaderAst ast = ShaderAst.parse("#version 330 core\nconst float shadowMapResolution = 2048.0;\n" + helper + helper
            + "void main() { gl_FragColor = vec4(texture2DShadow2x2(shadowtex, vec3(0.0)), 1.0); }\n");
        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> AdaptiveShadowBoundsTransformer.transform(ast, ShaderType.FRAGMENT, false, -1));
        assertTrue(thrown.getMessage().contains("replaced 2 definitions, expected 1"), thrown.getMessage());
    }

    @Test
    void runtimeStatsPreCheck() {
        assertTrue(AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats("shadowMapResolution texture2DShadow2x2"));
        assertTrue(AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats("shadowMapResolution SampleFilteredShadow"));
        assertFalse(AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats("texture2DShadow2x2 SampleFilteredShadow"));
        assertFalse(AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats("shadowMapResolution shadow2D"));
    }

    /** Transforms without the runtime stats and returns the output parsed again, which must succeed. */
    private static ShaderAst transform(String source, ShaderType type) {
        final ShaderAst ast = ShaderAst.parse(source);
        AdaptiveShadowBoundsTransformer.transform(ast, type, false, -1);
        return ShaderAst.parse(ast.print(HEADER));
    }

    /** The first definition named {@code name}, printed, as tokens. */
    private static GlslTokens definition(ShaderAst ast, String name) {
        return ast.functions().stream()
            .filter(function -> function.name().equals(name))
            .findFirst()
            .map(function -> GlslTokens.of(ShaderAst.source(function.node())))
            .orElseThrow(() -> new AssertionError("Missing function " + name));
    }

    private static List<String> names(ShaderAst ast) {
        return ast.functions().stream().map(ShaderAst.FunctionInfo::name).toList();
    }

    /** The helper's tokens are the same after the transform, with {@code ifs} {@code if} statements. */
    private static void assertHelperUntouched(String source, ShaderType type, String name, int ifs) {
        final GlslTokens before = definition(ShaderAst.parse(source), name);
        final ShaderAst output = transform(source, type);
        final GlslTokens after = definition(output, name);
        assertEquals(before.text(), after.text());
        assertEquals(ifs, after.count("if"), after.text());
        assertEquals(0, GlslTokens.of(output.printBody()).count("atomicAdd"));
    }
}
