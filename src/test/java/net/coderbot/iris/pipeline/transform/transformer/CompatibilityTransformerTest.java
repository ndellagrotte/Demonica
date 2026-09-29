package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
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
 * through {@link ShaderAst#findQualifiers}.
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
        // Each injected output is initialized in main, as TauMC's transformGrouped did.
        final GlslTokens tokens = GlslTokens.of(transformedVertex);
        assertTrue(tokens.contains("color = vec3 ( 0.0 ) ;"), tokens.text());
        assertTrue(tokens.contains("texCoord = vec2 ( 0.0 ) ;"), tokens.text());
        assertTrue(tokens.contains("isMoon = 0.0 ;"), tokens.text());
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
