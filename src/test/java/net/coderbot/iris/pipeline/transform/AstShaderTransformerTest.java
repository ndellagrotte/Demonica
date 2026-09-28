package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.ComputeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glsl-transformer engine's orchestrator (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md) against the
 * TauMC engine, on the shapes the corpus replay does not cover: the case TauMC could not transform, the header's
 * extension lines, the matrix spellings {@code transformGrouped} compares, and the named behaviour differences. Outputs
 * are compared as {@link GlslTokens}.
 */
class AstShaderTransformerTest {

    @BeforeAll
    static void fullCapability() {
        // As the mini-corpus was recorded: GLSL 460 with SSBO and image load/store, full version hoisting.
        RenderSystem.initializeGlslCapabilityForTesting(460, true, true);
        ShaderTransformer.resetVersionHoistingForTesting();
        ShaderTransformer.init();
    }

    @AfterAll
    static void restoreGlobalState() {
        ShaderTransformer.resetVersionHoistingForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    private static Parameters composite() {
        return new TextureStageParameters(Patch.COMPOSITE, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    private static Map<PatchShaderType, String> taumc(String vertex, String fragment) {
        return ShaderTransformer.transform(vertex, null, null, null, fragment, composite());
    }

    private static Map<PatchShaderType, String> douira(String vertex, String fragment) {
        return AstShaderTransformer.transform(vertex, null, null, null, fragment, composite());
    }

    private static void assertSameProgram(Map<PatchShaderType, String> expected, Map<PatchShaderType, String> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((stage, text) -> {
            final String diff = GlslTokens.diff(text, actual.get(stage));
            assertTrue(diff.isEmpty(), () -> stage + ": TauMC (-) and glsl-transformer (+) engines differ:\n" + diff);
        });
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AstShaderTransformerTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing test resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The mini-corpus case {@code transform-grouped-330-undeclared}, accepted in the replay as "old engine threw". TauMC
     * throws because its variable anchor is gone: the vertex shader calls no {@code ftransform()}, so
     * {@code removeUnusedFunctions} removed the injected {@code iris_ftransform}, and {@code transformGrouped}'s
     * injection finds no anchor; with a call to {@code ftransform()} it transforms. The new engine fixes a removed
     * anchor again and declares and initializes the missing output.
     */
    @Test
    void theGroupedCaseTauMCCouldNotTransform() throws IOException {
        final String vertex = resource("/transform-corpus/transform-grouped-330-undeclared/in.vertex.glsl");
        final String fragment = resource("/transform-corpus/transform-grouped-330-undeclared/in.fragment.glsl");

        final IndexOutOfBoundsException thrown = assertThrows(IndexOutOfBoundsException.class, () -> taumc(vertex, fragment));
        assertEquals("Index: -1, Size: 23", thrown.getMessage());
        final String withFtransform = vertex.replace("gl_Position = ", "vec4 unusedQuad = ftransform();\n    gl_Position = ");
        assertTrue(GlslTokens.contains(taumc(withFtransform, fragment).get(PatchShaderType.VERTEX), "out vec3 viewDir ;"));

        final Map<PatchShaderType, String> output = douira(vertex, fragment);
        final String outVertex = output.get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.contains(outVertex, "out vec3 viewDir ;"), outVertex);
        assertTrue(GlslTokens.contains(outVertex, "void main ( ) { viewDir = vec3 ( 0.0 ) ;"), outVertex);
        assertFalse(GlslTokens.contains(outVertex, "iris_ftransform"), outVertex);
        // Both stages are programs glsl-transformer can read back.
        output.values().forEach(ShaderAst::parse);
    }

    /**
     * {@code transformGrouped} compares types as spelled, as TauMC did (S4 verification): an {@code out mat2x2} does not
     * pair with an {@code in mat2}, so the unassigned outputs are left alone; spelled alike, they are initialized.
     */
    @Test
    void groupedTypesCompareAsSpelled() {
        final String vertex = "#version 330 core\nin vec3 pos;\nout mat2x2 m;\nout mat3 k;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n";
        final String differently = "#version 330 core\nin mat2 m;\nin mat3x3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n";
        final String alike = "#version 330 core\nin mat2x2 m;\nin mat3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n";

        final Map<PatchShaderType, String> apart = douira(vertex, differently);
        assertSameProgram(taumc(vertex, differently), apart);
        assertFalse(GlslTokens.contains(apart.get(PatchShaderType.VERTEX), "m = mat2 ( 0.0 ) ;"));
        assertFalse(GlslTokens.contains(apart.get(PatchShaderType.VERTEX), "k = mat3 ( 0.0 ) ;"));

        final Map<PatchShaderType, String> paired = douira(vertex, alike);
        assertSameProgram(taumc(vertex, alike), paired);
        assertTrue(GlslTokens.contains(paired.get(PatchShaderType.VERTEX), "k = mat3 ( 0.0 ) ; m = mat2 ( 0.0 ) ;")
                || GlslTokens.contains(paired.get(PatchShaderType.VERTEX), "m = mat2 ( 0.0 ) ; k = mat3 ( 0.0 ) ;"),
            paired.get(PatchShaderType.VERTEX));
    }

    /** The header: {@code #version N core}, then the extension lines in source order, {@code require} included. */
    @Test
    void extensionLinesInTheHeader() {
        final String vertex = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
        final String fragment = "#version 120\n#extension GL_ARB_shader_texture_lod : enable\n#extension GL_EXT_gpu_shader4 : require\n"
            + "uniform sampler2D colortex0;\nvoid main() { gl_FragColor = texture2DLod(colortex0, gl_FragCoord.xy, 0.0); }\n";
        final Map<PatchShaderType, String> output = douira(vertex, fragment);
        assertSameProgram(taumc(vertex, fragment), output);
        assertTrue(output.get(PatchShaderType.FRAGMENT).startsWith("#version 330 core\n\n#extension GL_ARB_shader_texture_lod : enable\n"
            + "#extension GL_EXT_gpu_shader4 : require\n"), output.get(PatchShaderType.FRAGMENT));
        assertTrue(output.get(PatchShaderType.VERTEX).startsWith("#version 330 core\n\n"), output.get(PatchShaderType.VERTEX));
    }

    /**
     * Named difference: TauMC's header printed every directive of its pre-parser tree but {@code #version}, so a
     * {@code #define} or {@code #pragma} the source still had came back in the header; glsl-transformer drops them
     * (and logs them). Sources reach the transform preprocessed; no recorded input has one.
     */
    @Test
    void differenceOtherDirectivesAreNotReemitted() {
        final String vertex = "#version 330 core\nin vec3 pos;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n";
        final String fragment = "#version 330 core\n#extension GL_ARB_shader_texture_lod : enable\n#define UNUSED 1\n#pragma optimize(on)\n"
            + "out vec4 frag;\nvoid main() { frag = vec4(1.0); }\n";
        final String old = taumc(vertex, fragment).get(PatchShaderType.FRAGMENT);
        final String now = douira(vertex, fragment).get(PatchShaderType.FRAGMENT);
        assertTrue(GlslTokens.contains(old, "#define UNUSED 1"), old);
        assertTrue(GlslTokens.contains(old, "#pragma optimize ( on )"), old);
        assertFalse(now.contains("#define") || now.contains("#pragma"), now);
        assertTrue(GlslTokens.contains(now, "#extension GL_ARB_shader_texture_lod : enable"), now);
    }

    /** Named difference: a source that does not parse throws here; TauMC re-parsed it with error recovery. */
    @Test
    void differenceASyntaxErrorThrows() {
        final String vertex = "#version 330 core\nin vec3 pos;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n";
        final String broken = "#version 330 core\nout vec4 frag;\nvoid main() { frag = vec4(1.0) }\n";
        assertTrue(taumc(vertex, broken).containsKey(PatchShaderType.FRAGMENT));
        assertThrows(ShaderAst.SyntaxException.class, () -> douira(vertex, broken));
    }

    /** A kind that is not ported throws with the phrase the corpus replay classifies as unsupported. */
    @Test
    void unportedKindsThrow() {
        final AttributeParameters attributes = new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, true, true));
        final UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
            () -> AstShaderTransformer.transform("#version 330 core\nvoid main() {}\n", null, null, null, null, attributes));
        assertEquals("glsl-transformer engine: ATTRIBUTES not ported yet", thrown.getMessage());
        assertNull(attributes.type);
    }

    /** COMPUTE: the shorter pre-pass list, the same header and the parameter type reset after a failure. */
    @Test
    void compute() {
        final String compute = "#version 430\nlayout(local_size_x = 8) in;\nuniform sampler2D colortex0;\nlayout(rgba8) uniform image2D colorimg0;\n"
            + "void main() { imageStore(colorimg0, ivec2(gl_GlobalInvocationID.xy), texture2D(colortex0, vec2(0.5))); }\n";
        final Map<PatchShaderType, String> old = ShaderTransformer.transformCompute(compute,
            new ComputeParameters(Patch.COMPUTE, TextureStage.COMPOSITE_AND_FINAL, null));
        final ComputeParameters parameters = new ComputeParameters(Patch.COMPUTE, TextureStage.COMPOSITE_AND_FINAL, null);
        final Map<PatchShaderType, String> now = AstShaderTransformer.transformCompute(compute, parameters);
        assertSameProgram(old, now);
        assertTrue(now.get(PatchShaderType.COMPUTE).startsWith("#version 430 core\n"), now.get(PatchShaderType.COMPUTE));
        assertNull(parameters.type);

        final ComputeParameters failing = new ComputeParameters(Patch.COMPUTE, TextureStage.COMPOSITE_AND_FINAL, null);
        assertThrows(IllegalArgumentException.class, () -> AstShaderTransformer.transformCompute("void main() {}", failing));
        assertNull(failing.type);
    }
}
