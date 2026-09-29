package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.CeleritasTerrainParameters;
import net.coderbot.iris.pipeline.transform.parameter.ComputeParameters;
import net.coderbot.iris.pipeline.transform.parameter.DHParameters;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glsl-transformer engine's orchestrator (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md) against the
 * TauMC engine, on the shapes the corpus replay does not cover: the cases TauMC could not transform, the header's
 * extension lines and the ones after the leading directives, the matrix spellings {@code transformGrouped} compares, and the named behaviour differences. Outputs
 * are compared as {@link GlslTokens}. Step 6 adds ATTRIBUTES and CELERITAS_TERRAIN: every declared type of
 * {@code mc_Entity} and {@code mc_midTexCoord} (the corpora have only {@code vec3}/{@code vec4} and
 * {@code vec2}/{@code vec4}), the geometry stage, and every input-availability combination. Step 7 adds DH_TERRAIN and
 * DH_GENERIC.
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

    private static Map<PatchShaderType, String> terrain(boolean douira, String vertex, String geometry, String fragment) {
        final CeleritasTerrainParameters parameters = new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN);
        return douira ? AstShaderTransformer.transform(vertex, geometry, null, null, fragment, parameters)
            : ShaderTransformer.transform(vertex, geometry, null, null, fragment, parameters);
    }

    /** How many lines of {@code glsl}'s {@link GlslTokens#text()} are the statement {@code line}. */
    private static long lines(String glsl, String line) {
        final String wanted = GlslTokens.of(line).text().strip();
        return GlslTokens.of(glsl).text().lines().filter(wanted::equals).count();
    }

    private static final String TERRAIN_FRAGMENT = "#version 330 core\nin vec2 texcoord;\nuniform sampler2D gtexture;\n"
        + "void main() { gl_FragData[0] = texture(gtexture, texcoord); }\n";

    /**
     * The mini-corpus case {@code celeritas-terrain-multitexcoord3}, accepted in the replay as "old engine threw": a
     * Celeritas terrain vertex shader that declares {@code gl_MultiTexCoord3} and not {@code mc_midTexCoord}, the one
     * production shape of TauMC's stale by-text {@code replaceExpression} cache (S3 remark 3). {@code patchMultiTexCoord3}
     * renames the declaration and injects {@code attribute vec4 mc_midTexCoord;}, which TauMC makes its variable anchor;
     * {@code replaceMidTexCoord}'s {@code removeVariable} removes that injected declaration, and the next
     * {@code injectVariable} throws. Before that, TauMC's {@code replaceExpression} had missed the renamed references.
     * The new engine replaces them and transforms the case; its output still declares {@code mc_midTexCoord} twice, from
     * the transformer logic both engines share (report S06, Open questions).
     */
    @Test
    void theMultiTexCoord3Case() throws IOException {
        final String vertex = resource("/transform-corpus/celeritas-terrain-multitexcoord3/in.vertex.glsl");
        final String fragment = resource("/transform-corpus/celeritas-terrain-multitexcoord3/in.fragment.glsl");

        final IndexOutOfBoundsException thrown = assertThrows(IndexOutOfBoundsException.class,
            () -> terrain(false, vertex, null, fragment));
        assertEquals("Index: -1, Size: 30", thrown.getMessage());

        // TauMC's verbs, in the engine's order, up to the throw: the renamed references are missed.
        // The engine's pre-passes for a vertex shader at the effective version 330.
        final String prepared = com.gtnewhorizons.angelica.glsm.CompatShaderTransformer.fixupQualifiers(
            com.gtnewhorizons.angelica.glsm.GlslTransformUtils.renameReservedWords(
                com.gtnewhorizons.angelica.glsm.GlslTransformUtils.replaceTexture(vertex), 330), false);
        final org.taumc.glsl.Transformer t = new org.taumc.glsl.Transformer(org.taumc.glsl.ShaderParser.parseShader(prepared).full());
        final CeleritasTerrainParameters parameters = new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN);
        parameters.type = ShaderType.VERTEX;
        CeleritasTransformer.transform(t, parameters, 330);
        ShaderTransformer.patchMultiTexCoord3(t, parameters);
        t.removeVariable("mc_midTexCoord");
        t.replaceExpression("mc_midTexCoord", "iris_MidTex");
        final StringBuilder taumcTree = new StringBuilder();
        t.mutateTree(tree -> taumcTree.append(com.gtnewhorizons.angelica.glsm.GlslTransformUtils.getFormattedShader(tree, "")));
        assertTrue(GlslTokens.contains(taumcTree.toString(), "midcoord = ( iris_TextureMatrix * mc_midTexCoord ) . xy ;"), taumcTree::toString);
        assertTrue(GlslTokens.contains(taumcTree.toString(), "position . xz += ( mc_midTexCoord . xy - texcoord ) * 0.05 ;"), taumcTree::toString);

        final String outVertex = terrain(true, vertex, null, fragment).get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.contains(outVertex, "midcoord = ( iris_TextureMatrix * iris_MidTex ) . xy ;"), outVertex);
        assertTrue(GlslTokens.contains(outVertex, "position . xz += ( iris_MidTex . xy - texcoord ) * 0.05 ;"), outVertex);
        assertTrue(GlslTokens.contains(outVertex, "vec4 iris_MidTex = vec4 ( mc_midTexCoord . xy * 3.0517578E-5 , 0.0 , 1.0 ) ;"), outVertex);
        assertFalse(GlslTokens.contains(outVertex, "gl_MultiTexCoord3"), outVertex);
        // The shared logic's defect: the renamed declaration stays next to the injected one.
        assertEquals(1, lines(outVertex, "in vec2 mc_midTexCoord ;"), outVertex);
        assertEquals(1, lines(outVertex, "in vec4 mc_midTexCoord ;"), outVertex);
    }

    /**
     * {@code replaceMCEntity}: every declared type of {@code mc_Entity}, and none; the new engine's {@code Type} switch
     * against TauMC's lexer-token switch. A type outside the switch throws in both.
     */
    @Test
    void mcEntityTypes() {
        for (String type : List.of("float", "vec2", "vec3", "vec4", "int", "ivec2", "ivec3", "ivec4", "uint", "bool")) {
            final String vertex = "#version 330 core\nin " + type + " mc_Entity;\nout vec2 texcoord;\n"
                + "void main() { " + type + " e = mc_Entity; texcoord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }\n";
            assertSameProgram(terrain(false, vertex, null, TERRAIN_FRAGMENT), terrain(true, vertex, null, TERRAIN_FRAGMENT));
            if (!type.equals("bool")) {
                assertTrue(GlslTokens.contains(terrain(true, vertex, null, TERRAIN_FRAGMENT).get(PatchShaderType.VERTEX),
                    type + " iris_Entity ="), type);
            }
        }
        final String undeclared = "#version 330 core\nout vec2 texcoord;\n"
            + "void main() { texcoord = vec2(mc_Entity.x); gl_Position = ftransform(); }\n";
        assertSameProgram(terrain(false, undeclared, null, TERRAIN_FRAGMENT), terrain(true, undeclared, null, TERRAIN_FRAGMENT));
        final String matrix = "#version 330 core\nin mat2 mc_Entity;\nvoid main() { gl_Position = ftransform(); }\n";
        assertThrows(IllegalStateException.class, () -> terrain(false, matrix, null, TERRAIN_FRAGMENT));
        final IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> terrain(true, matrix, null, TERRAIN_FRAGMENT));
        assertEquals("Got an invalid format mc_Entity (type mat2).", thrown.getMessage());
    }

    /**
     * {@code replaceMidTexCoord}: every declared type of {@code mc_midTexCoord}, and none. {@code int} falls through
     * TauMC's switch to the {@code in vec2} declaration without an {@code iris_MidTex}, here as there.
     */
    @Test
    void mcMidTexCoordTypes() {
        for (String type : List.of("float", "vec2", "vec3", "vec4", "bool", "int")) {
            final String vertex = "#version 330 core\nin " + type + " mc_midTexCoord;\nout vec2 texcoord;\n"
                + "void main() { " + type + " m = mc_midTexCoord; texcoord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }\n";
            assertSameProgram(terrain(false, vertex, null, TERRAIN_FRAGMENT), terrain(true, vertex, null, TERRAIN_FRAGMENT));
        }
        final String undeclared = "#version 330 core\nout vec2 texcoord;\n"
            + "void main() { texcoord = mc_midTexCoord.xy; gl_Position = ftransform(); }\n";
        assertSameProgram(terrain(false, undeclared, null, TERRAIN_FRAGMENT), terrain(true, undeclared, null, TERRAIN_FRAGMENT));
    }

    /**
     * CELERITAS_TERRAIN and ATTRIBUTES with a geometry stage, which no recorded corpus has: Celeritas's geometry branch
     * replaces the pack's reprojection ({@code toClipSpace3(...)}) with the clip-space position, and the vertex stage
     * projects a displaced {@code worldpos}.
     */
    @Test
    void geometryStages() {
        final String vertex = "#version 330 core\nuniform mat4 gbufferModelView;\nout vec4 vertexPos;\n"
            + "void main() { vec3 worldpos = gl_Vertex.xyz; vertexPos = vec4(worldpos, 0.0); gl_Position = vec4(worldpos, 0.0); }\n";
        final String geometry = "#version 330 core\nlayout(triangles) in;\nlayout(triangle_strip, max_vertices = 3) out;\n"
            + "uniform mat4 gbufferModelView;\nin vec4 vertexPos[];\nvec4 toClipSpace3(vec3 p) { return vec4(p, 1.0); }\n"
            + "void main() { for (int i = 0; i < 3; i++) { vec4 vertex = gl_in[i].gl_Position; "
            + "gl_Position = toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz); EmitVertex(); } EndPrimitive(); }\n";
        final String fragment = "#version 330 core\nvoid main() { gl_FragData[0] = vec4(1.0); }\n";
        final Map<PatchShaderType, String> old = terrain(false, vertex, geometry, fragment);
        final Map<PatchShaderType, String> now = terrain(true, vertex, geometry, fragment);
        assertSameProgram(old, now);
        assertTrue(GlslTokens.contains(now.get(PatchShaderType.GEOMETRY), "gl_Position = vertex ;"), now.get(PatchShaderType.GEOMETRY));
        assertTrue(GlslTokens.contains(now.get(PatchShaderType.VERTEX),
            "gl_Position = iris_ProjectionMatrix * gbufferModelView * vec4 ( worldpos , 1.0 ) ;"), now.get(PatchShaderType.VERTEX));

        final AttributeParameters oldAttributes = new AttributeParameters(Patch.ATTRIBUTES, true, new InputAvailability(true, true, true));
        final AttributeParameters newAttributes = new AttributeParameters(Patch.ATTRIBUTES, true, new InputAvailability(true, true, true));
        assertSameProgram(ShaderTransformer.transform(vertex, geometry, null, null, fragment, oldAttributes),
            AstShaderTransformer.transform(vertex, geometry, null, null, fragment, newAttributes));
    }

    /** ATTRIBUTES under every input-availability combination (the corpora have five of the eight). */
    @Test
    void attributeInputAvailability() {
        final String vertex = "#version 120\nattribute vec4 mc_Entity;\nvarying vec2 texcoord;\nvarying vec2 lmcoord;\nvarying vec4 glcolor;\n"
            + "void main() { texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;"
            + " vec2 lm2 = gl_MultiTexCoord2.xy; glcolor = gl_Color; gl_Position = ftransform() + vec4(lm2, gl_Normal.xy) * 0.0;"
            + " if (entityId == 1) glcolor = entityColor; }\n";
        final String fragment = "#version 120\nvarying vec2 texcoord;\nvarying vec4 glcolor;\nuniform sampler2D texture;\n"
            + "void main() { gl_FragColor = texture2D(texture, texcoord) * glcolor; }\n";
        for (int flags = 0; flags < 8; flags++) {
            final InputAvailability inputs = new InputAvailability((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            assertSameProgram(ShaderTransformer.transform(vertex, null, null, null, fragment, new AttributeParameters(Patch.ATTRIBUTES, false, inputs)),
                AstShaderTransformer.transform(vertex, null, null, null, fragment, new AttributeParameters(Patch.ATTRIBUTES, false, inputs)));
        }
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
     * An {@code #extension} after the leading directive block (after code, a blank line, a comment) is dropped, as
     * TauMC dropped it: its pre-parser read only the leading block and its parser ignores directives. glsl-transformer
     * parses the directive anywhere at the top level, so the header would otherwise gain a {@code require} TauMC's
     * output never had. The first program is the S5 verifier's repro ({@code p9-midfile-extension}).
     */
    @Test
    void extensionsAfterTheLeadingDirectivesAreDropped() {
        final String vertex = "#version 330 core\nin vec3 vaPosition;\nvoid main() { gl_Position = vec4(vaPosition, 1.0); }\n";
        final String afterCode = "#version 330 core\nuniform sampler2D colortex0;\n#extension GL_EXT_gpu_shader4 : require\n"
            + "layout(location = 0) out vec4 outColor;\nvoid main() { outColor = texture(colortex0, vec2(0.5)); }";
        final Map<PatchShaderType, String> output = douira(vertex, afterCode);
        assertSameProgram(taumc(vertex, afterCode), output);
        assertFalse(output.get(PatchShaderType.FRAGMENT).contains("#extension"), output.get(PatchShaderType.FRAGMENT));

        final String mixed = "#version 330 core\n#extension GL_ARB_shader_texture_lod : enable\n\n#extension GL_EXT_gpu_shader4 : require\n"
            + "uniform sampler2D colortex0;\n// a comment\n#extension GL_ARB_gpu_shader5 : enable\n"
            + "layout(location = 0) out vec4 outColor;\nvoid main() { outColor = texture(colortex0, vec2(0.5)); }\n";
        final Map<PatchShaderType, String> mixedOutput = douira(vertex, mixed);
        assertSameProgram(taumc(vertex, mixed), mixedOutput);
        final String fragment = mixedOutput.get(PatchShaderType.FRAGMENT);
        assertTrue(fragment.startsWith("#version 330 core\n\n#extension GL_ARB_shader_texture_lod : enable\n"), fragment);
        assertEquals(1, fragment.lines().filter(line -> line.startsWith("#extension")).count(), fragment);
    }

    /**
     * Named difference: TauMC's header held every directive of the source's leading directive block but
     * {@code #version} (its pre-parser read only that block), so a {@code #define} or {@code #pragma} there came back in
     * the header; glsl-transformer drops them (and logs them). Sources reach the transform preprocessed; no recorded
     * input has one.
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

    /** Every patch kind is ported (Step 7): nothing throws "not ported yet", and the parameter type is reset. */
    @Test
    void everyKindIsPorted() {
        final DHParameters dh = new DHParameters(Patch.DH_TERRAIN, null);
        final Map<PatchShaderType, String> output = AstShaderTransformer.transform(
            "#version 330 core\nvoid main() { gl_Position = gl_Vertex; }\n", null, null, null, null, dh);
        assertTrue(GlslTokens.contains(output.get(PatchShaderType.VERTEX), "_vert_init ( ) ;"), output.get(PatchShaderType.VERTEX));
        assertNull(dh.type);
    }

    /**
     * DH_TERRAIN and DH_GENERIC (Step 7) against the TauMC engine on what the mini-corpus's two cases and
     * Complementary's three recorded DH programs do not use: {@code ftransform()}, the texture matrices,
     * {@code gl_MultiTexCoord0} to {@code 7} except 2 ({@link #dhMultiTexCoord2Alias}), the inverse matrices, the
     * combined matrix, the legacy matrices in the fragment stage, a declaration the transformer would otherwise add,
     * and a geometry stage.
     */
    @Test
    void dhPrograms() {
        final String vertex = "#version 120\nuniform vec3 modelOffset;\nvarying vec2 texcoord;\nvarying vec2 lmcoord;\n"
            + "varying vec4 glcolor;\nvarying vec3 normal;\nvarying float material;\n"
            + "void main() {\n"
            + "    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;\n"
            + "    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;\n"
            + "    vec4 unused = gl_MultiTexCoord4 + gl_MultiTexCoord5 + gl_MultiTexCoord6 + gl_MultiTexCoord7;\n"
            + "    glcolor = gl_Color * unused.w;\n"
            + "    normal = normalize(gl_NormalMatrix * gl_Normal);\n"
            + "    material = float(dhMaterialId);\n"
            + "    vec4 view = gl_ModelViewMatrixInverse * gl_ProjectionMatrixInverse * vec4(modelOffset, 1.0);\n"
            + "    gl_Position = ftransform() + gl_ModelViewProjectionMatrix * gl_Vertex + gl_ProjectionMatrix * gl_ModelViewMatrix * view;\n"
            + "}\n";
        final String geometry = "#version 330 core\nlayout(triangles) in;\nlayout(triangle_strip, max_vertices = 3) out;\n"
            + "in vec4 glcolor[];\nout vec4 gcolor;\n"
            + "void main() { for (int i = 0; i < 3; i++) { gcolor = glcolor[i]; "
            + "gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_in[i].gl_Position; EmitVertex(); } EndPrimitive(); }\n";
        final String fragment = "#version 120\nuniform sampler2D texture;\nvarying vec2 texcoord;\nvarying vec4 glcolor;\n"
            + "varying float material;\n"
            + "void main() { vec4 p = gl_ProjectionMatrixInverse * gl_ModelViewMatrixInverse * gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(1.0);"
            + " gl_FragData[0] = texture2D(texture, texcoord) * glcolor * p.w * (gl_TextureMatrix[0] * vec4(material)).x; }\n";
        for (Patch patch : List.of(Patch.DH_TERRAIN, Patch.DH_GENERIC)) {
            for (String withGeometry : new String[] {null, geometry}) {
                final Map<PatchShaderType, String> old = ShaderTransformer.transform(vertex, withGeometry, null, null, fragment,
                    new DHParameters(patch, null));
                final DHParameters parameters = new DHParameters(patch, null);
                final Map<PatchShaderType, String> now = AstShaderTransformer.transform(vertex, withGeometry, null, null, fragment,
                    parameters);
                assertSameProgram(old, now);
                assertNull(parameters.type);

                final String outVertex = now.get(PatchShaderType.VERTEX);
                assertTrue(GlslTokens.contains(outVertex, "void main ( ) { _vert_init ( ) ;"), outVertex);
                assertTrue(GlslTokens.contains(outVertex, patch == Patch.DH_TERRAIN
                    ? "vec4 getVertexPosition ( ) { return vec4 ( modelOffset + _vert_position , 1.0 ) ; }"
                    : "vec4 getVertexPosition ( ) { return vec4 ( _vert_position , 1.0 ) ; }"), outVertex);
                assertTrue(GlslTokens.contains(outVertex, patch == Patch.DH_TERRAIN ? "in uvec4 vPosition ;" : "in vec3 aScale ;"),
                    outVertex);
                // The pack's own declaration is kept and, for DH_TERRAIN, not injected a second time.
                final String text = GlslTokens.of(outVertex).text();
                assertEquals(1, text.split("uniform vec3 modelOffset ;", -1).length - 1, outVertex);
            }
        }
    }

    /**
     * Named difference (Step 7): {@code gl_MultiTexCoord2}, OptiFine's alias of the lightmap coordinate. Both DH
     * transformers rename it to {@code gl_MultiTexCoord1} and then replace {@code gl_MultiTexCoord1} with the DH light
     * coordinate. TauMC's {@code replaceExpression} misses the renamed references (its by-text cache still knows them
     * as {@code gl_MultiTexCoord2}, S3 remark 3), so its output keeps {@code gl_MultiTexCoord1}, which a core-profile
     * program does not have; the new engine replaces them, as the transformer means. Mini-corpus case
     * {@code dh-terrain-multitexcoord2}.
     */
    @Test
    void dhMultiTexCoord2Alias() {
        final String vertex = "#version 120\nvarying vec2 lmcoord;\n"
            + "void main() { lmcoord = gl_MultiTexCoord2.xy + gl_MultiTexCoord1.xy; gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";
        final String fragment = "#version 120\nvarying vec2 lmcoord;\nvoid main() { gl_FragData[0] = vec4(lmcoord, 0.0, 1.0); }\n";
        for (Patch patch : List.of(Patch.DH_TERRAIN, Patch.DH_GENERIC)) {
            final Map<PatchShaderType, String> old = ShaderTransformer.transform(vertex, null, null, null, fragment,
                new DHParameters(patch, null));
            final Map<PatchShaderType, String> now = AstShaderTransformer.transform(vertex, null, null, null, fragment,
                new DHParameters(patch, null));
            final String light = "vec4 ( _vert_tex_light_coord , 0.0 , 1.0 ) . xy";
            assertTrue(GlslTokens.contains(old.get(PatchShaderType.VERTEX), "lmcoord = gl_MultiTexCoord1 . xy + " + light + " ;"),
                old.get(PatchShaderType.VERTEX));
            assertTrue(GlslTokens.contains(now.get(PatchShaderType.VERTEX), "lmcoord = " + light + " + " + light + " ;"),
                now.get(PatchShaderType.VERTEX));
            assertFalse(now.get(PatchShaderType.VERTEX).contains("gl_MultiTexCoord"), now.get(PatchShaderType.VERTEX));
            // Nothing else differs.
            final String withoutTheLine = GlslTokens.diff(old.get(PatchShaderType.VERTEX), now.get(PatchShaderType.VERTEX));
            assertEquals(2, withoutTheLine.lines().filter(line -> line.startsWith("-") || line.startsWith("+")).count(),
                withoutTheLine);
            assertSameProgram(Map.of(PatchShaderType.FRAGMENT, old.get(PatchShaderType.FRAGMENT)),
                Map.of(PatchShaderType.FRAGMENT, now.get(PatchShaderType.FRAGMENT)));
        }
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
