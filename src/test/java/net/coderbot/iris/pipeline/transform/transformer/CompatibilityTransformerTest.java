package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.external_declaration.EmptyDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.CompatibilityPatches;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glsl-transformer engine's side of the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.CompatibilityTransformerTest} (Step 8 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), which stays until Step 11 and keeps testing the TauMC copy. The
 * pack text patches ({@link CompatibilityPatches}) are engine-neutral; their "0 syntax errors" oracle is now
 * "{@link ShaderAst#parse(String)} succeeds" (it throws {@link ShaderAst.SyntaxException} on a syntax error, where TauMC
 * counted errors and recovered). The grouped transform is this package's {@link CompatibilityTransformer}, read back
 * through {@link ShaderAst#findQualifiers}. Step 12 added the tests of the Iris 26.1 port: empty declarations, the
 * tessellation pipeline, the type-mismatch cast, unsigned zeros, array types and a deterministic order.
 */
class CompatibilityTransformerTest {
    @Test
    void volumetricCloudReferenceDistanceAllowsFarPlaneRays() {
        final String patched = CompatibilityPatches.patchVolumetricCloudReferenceDistance("""
            #version 330 core
            uniform vec3 viewPos;
            uniform float far;
            uniform float maxdist;
            void main() {
                float lViewPosM = length(viewPos) < maxdist ? length(viewPos) - 1.0 : 100000000.0;
            }
            """);

        ShaderAst.parse(patched);
        assertEquals(1, occurrences(patched, "length(viewPos) >= far - 1.0"), patched);
    }

    @Test
    void volumetricCloudReferenceDistanceAllowsBlissFarPlaneRays() {
        final String patched = CompatibilityPatches.patchVolumetricCloudReferenceDistance("""
            #version 330 core
            uniform vec3 FragPosition;
            uniform float far;
            uniform float maxdist;
            void main() {
                float lViewPosM = length(FragPosition) < maxdist ? length(FragPosition) - 1.0 : 100000000.0;
            }
            """);

        ShaderAst.parse(patched);
        assertEquals(1, occurrences(patched, "length(FragPosition) >= far - 1.0"), patched);
    }

    @Test
    void volumetricCloudReferenceDistanceClampsLodDepth() {
        final String patched = CompatibilityPatches.patchVolumetricCloudReferenceDistance("""
            #version 330 core
            uniform sampler2D depthtex0;
            uniform sampler2D depthtex1;
            uniform sampler2D dhDepthTex;
            uniform sampler2D dhDepthTex1;
            uniform vec3 viewPos;
            uniform float far;
            uniform float maxdist;
            void main() {
                texelFetch(depthtex1, ivec2(0), 0);
                texelFetch(dhDepthTex, ivec2(0), 0);
                texelFetch(dhDepthTex1, ivec2(0), 0);
                float lViewPosM = length(viewPos) < maxdist ? length(viewPos) - 1.0 : 100000000.0;
            }
            """);

        ShaderAst.parse(patched);
        assertEquals(1, occurrences(patched, "texelFetch(dhDepthTex1, _irisCloudTexel, 0)"), patched);
        assertEquals(1, occurrences(patched, "lViewPosM = length(viewPos) - 1.0"), patched);
    }

    @Test
    void cloudMovementTimeUsesSmoothedWorldTimeForBliss() {
        final String patched = CompatibilityPatches.patchCloudMovementTime("""
            #version 330 core
            uniform int worldTime;
            uniform int worldDay;
            uniform float Cloud_Speed;
            void main() {
                float cloud_movement = (worldTime + mod(worldDay,100)*24000.0) / 24.0 * Cloud_Speed;
            }
            """);

        ShaderAst.parse(patched);
        assertEquals(1, occurrences(patched, "uniform float iris_worldTimeSmooth;"), patched);
        assertEquals(1, occurrences(patched, "(iris_worldTimeSmooth + mod(worldDay,100)*24000.0)"), patched);
        assertEquals(0, occurrences(patched, "worldTime + mod(worldDay,100)*24000.0"), patched);
    }

    @Test
    void cloudMovementTimeUsesSmoothedWorldTimeForEclipse() {
        final String patched = CompatibilityPatches.patchCloudMovementTime("""
            #version 330 core
            uniform int worldDay;
            uniform float worldTimeSmooth;
            uniform float Cloud_Speed;
            void main() {
                float cloud_movement = (worldTimeSmooth + mod(worldDay,100)*24000.0) / 24.0 * Cloud_Speed;
            }
            """);

        ShaderAst.parse(patched);
        assertEquals(1, occurrences(patched, "uniform float iris_worldTimeSmooth;"), patched);
        assertEquals(1, occurrences(patched, "(iris_worldTimeSmooth + mod(worldDay,100)*24000.0)"), patched);
        assertEquals(0, occurrences(patched, "= (worldTimeSmooth + mod(worldDay,100)*24000.0)"), patched);
    }

    @Test
    void groupedTransformAddsParseableOutputsForMissingFragmentInputs() {
        final ShaderAst vertex = ShaderAst.parse("""
            #version 330 core
            void main() {
                gl_Position = vec4(0.0);
            }
            """);
        final ShaderAst fragment = ShaderAst.parse("""
            #version 330 core
            in vec3 color;
            in vec2 texCoord;
            flat in float isMoon;
            layout(location = 0) out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(color * (texCoord.x + isMoon), 1.0);
            }
            """);
        final EnumMap<PatchShaderType, ShaderAst> stages = new EnumMap<>(PatchShaderType.class);
        stages.put(PatchShaderType.VERTEX, vertex);
        stages.put(PatchShaderType.FRAGMENT, fragment);

        CompatibilityTransformer.transformGrouped(stages,
            new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, false, false)));

        // Printed and parsed again: the injected outputs must be valid GLSL, not only nodes in the tree.
        final String transformedVertex = vertex.print("#version 330 core");
        final Map<String, ShaderAst.QualifiedDeclaration> outputs = ShaderAst.parse(transformedVertex)
            .findQualifiers(StorageQualifier.StorageType.OUT);

        assertEquals(Set.of("color", "texCoord", "isMoon"), outputs.keySet(), transformedVertex);
        assertEquals("vec3", outputs.get("color").typeName());
        assertEquals("vec2", outputs.get("texCoord").typeName());
        assertEquals("float", outputs.get("isMoon").typeName());
        assertEquals("flat out float", outputs.get("isMoon").typeText());
        assertNull(outputs.get("color").arraySpecifierText());
        // Each injected output is initialized in main, as TauMC's transformGrouped did and Iris 26.1's does (Step 12).
        final GlslTokens tokens = GlslTokens.of(transformedVertex);
        assertTrue(tokens.contains("color = vec3 ( 0.0 ) ;"), tokens.text());
        assertTrue(tokens.contains("texCoord = vec2 ( 0.0 ) ;"), tokens.text());
        assertTrue(tokens.contains("isMoon = 0.0 ;"), tokens.text());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Step 12: Iris 26.1's empty-declaration removal and transformGrouped (docs/glsl-transformer_adoption/PORTING_GUIDE.md)

    private static final AttributeParameters PARAMETERS =
        new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, false, false));

    private static AttributeParameters parameters(ShaderType type) {
        final AttributeParameters parameters = new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, false, false));
        parameters.type = type;
        return parameters;
    }

    /** Parses each stage, runs {@link CompatibilityTransformer#transformGrouped} and prints each stage again. */
    private static Map<PatchShaderType, String> grouped(Map<PatchShaderType, String> sources) {
        final EnumMap<PatchShaderType, ShaderAst> stages = new EnumMap<>(PatchShaderType.class);
        sources.forEach((stage, source) -> stages.put(stage, ShaderAst.parse(source)));
        CompatibilityTransformer.transformGrouped(stages, PARAMETERS);
        final EnumMap<PatchShaderType, String> printed = new EnumMap<>(PatchShaderType.class);
        stages.forEach((stage, ast) -> printed.put(stage, ast.print("#version 400 core")));
        // Every output is a program glsl-transformer reads back.
        printed.values().forEach(ShaderAst::parse);
        return printed;
    }

    @Test
    void emptyDeclarationsAreRemoved() {
        final ShaderAst ast = ShaderAst.parse("""
            #version 330 core
            out vec2 uv;;
            ;
            vec2 flip(vec2 p) {
                return vec2(p.x, 1.0 - p.y);
            };
            void main() {
                uv = flip(vec2(0.5));;
                gl_Position = vec4(0.0);
            }
            """);
        assertEquals(3, ast.root.nodeIndex.get(EmptyDeclaration.class).size());

        CompatibilityTransformer.transformEach(ast, parameters(ShaderType.VERTEX));

        assertTrue(ast.root.nodeIndex.get(EmptyDeclaration.class).isEmpty());
        final GlslTokens tokens = GlslTokens.of(ast.printBody());
        assertTrue(tokens.contains("out vec2 uv ; vec2 flip ( vec2 p ) { return vec2 ( p . x , 1.0 - p . y ) ; } void main ( ) {"),
            tokens.text());
        // An empty statement in a function body is not an external declaration; it stays.
        assertTrue(tokens.contains("uv = flip ( vec2 ( 0.5 ) ) ; ;"), tokens.text());
    }

    @Test
    void groupedPairsTheTessellationStages() {
        final Map<PatchShaderType, String> out = grouped(Map.of(
            PatchShaderType.VERTEX, "#version 400 core\nin vec3 p;\nout vec2 vUv;\nvoid main() { vUv = p.xy; gl_Position = vec4(p, 1.0); }\n",
            PatchShaderType.TESS_CONTROL, "#version 400 core\nlayout(vertices = 3) out;\nin vec2 vUv[];\nin vec3 vNormal[];\n"
                + "out vec2 tcUv[];\nout vec3 tcNormal[];\nvoid main() { tcUv[gl_InvocationID] = vUv[gl_InvocationID]; "
                + "tcNormal[gl_InvocationID] = vNormal[gl_InvocationID]; }\n",
            PatchShaderType.TESS_EVAL, "#version 400 core\nlayout(triangles) in;\nin vec2 tcUv[];\nin vec3 tcNormal[];\n"
                + "out vec2 uv;\nout vec3 normal;\nvoid main() { uv = tcUv[0]; normal = tcNormal[0]; }\n",
            PatchShaderType.FRAGMENT, "#version 400 core\nin vec2 uv;\nin vec3 normal;\nout vec4 color;\n"
                + "void main() { color = vec4(normal, uv.x); }\n"));

        // The vertex stage writes what the tessellation control stage reads, not the fragment stage's inputs.
        final GlslTokens vertex = GlslTokens.of(out.get(PatchShaderType.VERTEX));
        assertTrue(vertex.contains("out vec3 vNormal ;"), vertex.text());
        assertTrue(vertex.contains("void main ( ) { vNormal = vec3 ( 0.0 ) ;"), vertex.text());
        assertEquals(0, vertex.count("normal"), vertex.text());
        assertEquals(0, vertex.count("uv"), vertex.text());
        // The later pairs match already: nothing changes there.
        assertEquals(0, GlslTokens.of(out.get(PatchShaderType.TESS_EVAL)).count("iris_template_tcNormal"));
        assertTrue(GlslTokens.of(out.get(PatchShaderType.TESS_CONTROL)).contains("void main ( ) { tcUv [ gl_InvocationID ] ="));
    }

    @Test
    void groupedCastsAMismatchedType() {
        final Map<PatchShaderType, String> out = grouped(Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 p;\nout vec3 tint, glow;\n"
                + "void main() { tint = p; glow = vec3(0.1); gl_Position = vec4(p, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nin vec4 tint;\nin vec3 glow;\nout vec4 color;\n"
                + "void main() { color = tint + vec4(glow, 0.0); }\n"));

        final GlslTokens vertex = GlslTokens.of(out.get(PatchShaderType.VERTEX));
        assertTrue(vertex.contains("vec3 iris_template_tint ;"), vertex.text());
        assertTrue(vertex.contains("out vec4 tint ;"), vertex.text());
        assertTrue(vertex.contains("out vec3 glow ;"), vertex.text());
        assertTrue(vertex.contains("iris_template_tint = p ;"), vertex.text());
        assertTrue(vertex.contains("tint = vec4 ( iris_template_tint , vec4 ( 0 ) ) ; }"), vertex.text());
    }

    /** Demonica's fix to Iris 26.1: an unsigned output is initialized with {@code 0u} (Iris: {@code 0}). */
    @Test
    void groupedInitializesUnsignedOutputsWithAnUnsignedZero() {
        final Map<PatchShaderType, String> out = grouped(Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 p;\nflat out uint id;\nvoid main() { gl_Position = vec4(p, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nflat in uint id;\nflat in uvec2 cell;\nout vec4 color;\n"
                + "void main() { color = vec4(float(id + cell.x)); }\n"));

        final GlslTokens vertex = GlslTokens.of(out.get(PatchShaderType.VERTEX));
        assertTrue(vertex.contains("id = 0u ;"), vertex.text());
        assertTrue(vertex.contains("flat out uvec2 cell ;"), vertex.text());
        assertTrue(vertex.contains("cell = uvec2 ( 0u ) ;"), vertex.text());
    }

    /**
     * Iris 26.1's array handling: an array type ({@code out vec3[2]}) is left alone; an array declarator is not seen as
     * an array (Iris's comment: "It doesn't bother with array specifiers"), so a mismatched one is cast as a scalar.
     */
    @Test
    void groupedSkipsArrayTypes() {
        final Map<PatchShaderType, String> out = grouped(Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 p;\nout vec3[2] pair;\n"
                + "void main() { pair[0] = p; pair[1] = -p; gl_Position = vec4(p, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nin vec4[2] pair;\nout vec4 color;\nvoid main() { color = pair[0] + pair[1]; }\n"));

        final GlslTokens vertex = GlslTokens.of(out.get(PatchShaderType.VERTEX));
        assertTrue(vertex.contains("out vec3 [ 2 ] pair ;"), vertex.text());
        assertEquals(0, vertex.count("iris_template_pair"), vertex.text());
    }

    /**
     * Demonica's fix to Iris 26.1: the declarations are visited in document order, so the injected declarations and
     * initializations come out in the same order every time (Iris's node index is a {@code HashSet} of nodes, iterated
     * in identity-hash order). Fifty fresh parses of the same stages print the same program.
     */
    @Test
    void groupedOutputIsTheSameEveryTime() {
        final Map<PatchShaderType, String> sources = Map.of(
            PatchShaderType.VERTEX, "#version 330 core\nin vec3 p;\nvoid main() { gl_Position = vec4(p, 1.0); }\n",
            PatchShaderType.FRAGMENT, "#version 330 core\nin float a;\nin vec2 b;\nin vec3 c;\nin vec4 d;\nin float e, f, g;\n"
                + "out vec4 color;\nvoid main() { color = vec4(a + b.x + c.x + d.x + e + f + g); }\n");
        final String first = grouped(sources).get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.of(first).contains("void main ( ) { g = 0.0 ; f = 0.0 ; e = 0.0 ; d = vec4 ( 0.0 ) ; c = vec3 ( 0.0 ) ;"
            + " b = vec2 ( 0.0 ) ; a = 0.0 ;"), first);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, grouped(sources).get(PatchShaderType.VERTEX));
        }
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
