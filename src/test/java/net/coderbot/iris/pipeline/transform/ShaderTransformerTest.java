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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orchestrator, {@link ShaderTransformer} (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md, as
 * {@code AstShaderTransformerTest} until Step 11), against the TauMC engine's outputs, frozen in
 * {@code src/test/resources/transform-engine-taumc/} when Step 11 removed that engine ({@link TauMcSnapshots}; each
 * output is keyed by the test method, the patch kind and a hash of the inputs, so a changed input needs a new
 * recording, which the removed library can no longer make), on the shapes the corpus replay does not cover: the cases TauMC could not transform, the header's
 * extension lines and the ones after the leading directives, the matrix spellings {@code transformGrouped} compares, and the named behaviour differences. Outputs
 * are compared as {@link GlslTokens}. Step 6 adds ATTRIBUTES and CELERITAS_TERRAIN: every declared type of
 * {@code mc_Entity} and {@code mc_midTexCoord} (the corpora have only {@code vec3}/{@code vec4} and
 * {@code vec2}/{@code vec4}), the geometry stage, and every input-availability combination. Step 7 adds DH_TERRAIN and
 * DH_GENERIC. Step 7b adds the {@code #extension} lines glsl-transformer's grammar rejects, {@code patch} as an
 * identifier, the {@code gl_MultiTexCoord3} shapes and the legacy texture calls neither engine renames.
 */
class ShaderTransformerTest {

    @BeforeAll
    static void fullCapability() {
        // As the mini-corpus was recorded: GLSL 460 with SSBO and image load/store, full version hoisting.
        RenderSystem.initializeGlslCapabilityForTesting(460, true, true);
        VersionNegotiation.resetForTesting();
        VersionNegotiation.init();
    }

    @AfterAll
    static void restoreGlobalState() {
        VersionNegotiation.resetForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    // The TauMC engine's outputs, frozen in src/test/resources/transform-engine-taumc/ (Step 11; TauMcSnapshots).
    private static final TauMcSnapshots SNAPSHOTS = new TauMcSnapshots("transform-engine-taumc");
    private static String method;

    @BeforeEach
    void rememberMethod(TestInfo info) {
        method = info.getTestMethod().orElseThrow().getName();
    }

    /** TauMC's answer {@code key} of the running test method. */
    private static String snapshot(String key) {
        return SNAPSHOTS.get(method, key);
    }

    /**
     * The TauMC engine's output for {@code label} (the patch kind and parameters) and these inputs, keyed by the label and
     * a hash of the inputs; where it threw, the same exception type with the same message is thrown.
     */
    private static Map<PatchShaderType, String> taumcEngine(String label, List<String> inputs) {
        final String value = snapshot(label + " " + inputHash(label, inputs));
        if (TauMcSnapshots.threw(value)) {
            throw TauMcSnapshots.exception(value);
        }
        return fromSnapshot(value);
    }

    static String inputHash(String label, List<String> inputs) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(label.getBytes(StandardCharsets.UTF_8));
            for (String input : inputs) {
                digest.update((byte) 0);
                digest.update(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Each stage as {@code ---- <STAGE> <length>}, then exactly that many characters and a line break. */
    static String toSnapshot(Map<PatchShaderType, String> output) {
        final StringBuilder text = new StringBuilder();
        new EnumMap<>(output).forEach((stage, program) -> text.append("---- ").append(stage.name()).append(' ')
            .append(program == null ? "null" : String.valueOf(program.length())).append('\n')
            .append(program == null ? "" : program + "\n"));
        return text.toString();
    }

    static Map<PatchShaderType, String> fromSnapshot(String text) {
        final Map<PatchShaderType, String> output = new EnumMap<>(PatchShaderType.class);
        int at = 0;
        while (at < text.length()) {
            final int end = text.indexOf('\n', at);
            final String[] header = text.substring(at, end).split(" ");
            assertEquals("----", header[0], text);
            if (header[2].equals("null")) {
                output.put(PatchShaderType.valueOf(header[1]), null);
                at = end + 1;
            } else {
                final int length = Integer.parseInt(header[2]);
                output.put(PatchShaderType.valueOf(header[1]), text.substring(end + 1, end + 1 + length));
                at = end + 1 + length + 1;
            }
        }
        return output;
    }

    private static Parameters composite() {
        return new TextureStageParameters(Patch.COMPOSITE, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    private static Map<PatchShaderType, String> taumc(String vertex, String fragment) {
        return taumcEngine("COMPOSITE", Arrays.asList(vertex, fragment));
    }

    private static Map<PatchShaderType, String> douira(String vertex, String fragment) {
        return ShaderTransformer.transform(vertex, null, null, null, fragment, composite());
    }

    private static void assertSameProgram(Map<PatchShaderType, String> expected, Map<PatchShaderType, String> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((stage, text) -> {
            final String diff = GlslTokens.diff(text, actual.get(stage));
            assertTrue(diff.isEmpty(), () -> stage + ": TauMC (-) and glsl-transformer (+) engines differ:\n" + diff);
        });
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ShaderTransformerTest.class.getResourceAsStream(path)) {
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
        return douira ? ShaderTransformer.transform(vertex, geometry, null, null, fragment, parameters)
            : taumcEngine("CELERITAS_TERRAIN", Arrays.asList(vertex, geometry, fragment));
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
     * The new engine replaces them and transforms the case. Since Step 7b its {@code patchMultiTexCoord3} injects no
     * second declaration when the shader declared {@code gl_MultiTexCoord3} (Iris 26.1's handling, fixed; report S7b),
     * so the output declares {@code mc_midTexCoord} once, as Celeritas's {@code in vec2}; until then it declared it
     * twice (report S06, Open questions).
     */
    @Test
    void theMultiTexCoord3Case() throws IOException {
        final String vertex = resource("/transform-corpus/celeritas-terrain-multitexcoord3/in.vertex.glsl");
        final String fragment = resource("/transform-corpus/celeritas-terrain-multitexcoord3/in.fragment.glsl");

        final IndexOutOfBoundsException thrown = assertThrows(IndexOutOfBoundsException.class,
            () -> terrain(false, vertex, null, fragment));
        assertEquals("Index: -1, Size: 30", thrown.getMessage());

        // TauMC's verbs, in the engine's order, up to the throw: the renamed references are missed. Recorded from the
        // engine's pre-passes for a vertex shader at the effective version 330 (fixupQualifiers, renameReservedWords,
        // replaceTexture), then CeleritasTransformer, patchMultiTexCoord3, removeVariable("mc_midTexCoord") and
        // replaceExpression("mc_midTexCoord", "iris_MidTex") on TauMC's Transformer, printed.
        final String taumcTree = snapshot("TauMC's verbs up to the throw");
        assertTrue(GlslTokens.contains(taumcTree, "midcoord = ( iris_TextureMatrix * mc_midTexCoord ) . xy ;"), taumcTree);
        assertTrue(GlslTokens.contains(taumcTree, "position . xz += ( mc_midTexCoord . xy - texcoord ) * 0.05 ;"), taumcTree);

        final String outVertex = terrain(true, vertex, null, fragment).get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.contains(outVertex, "midcoord = ( iris_TextureMatrix * iris_MidTex ) . xy ;"), outVertex);
        assertTrue(GlslTokens.contains(outVertex, "position . xz += ( iris_MidTex . xy - texcoord ) * 0.05 ;"), outVertex);
        assertTrue(GlslTokens.contains(outVertex, "vec4 iris_MidTex = vec4 ( mc_midTexCoord . xy * 3.0517578E-5 , 0.0 , 1.0 ) ;"), outVertex);
        assertFalse(GlslTokens.contains(outVertex, "gl_MultiTexCoord3"), outVertex);
        // One declaration: replaceMidTexCoord removed the renamed one and declared Celeritas's attribute (Step 7b).
        assertEquals(1, lines(outVertex, "in vec2 mc_midTexCoord ;"), outVertex);
        assertEquals(0, lines(outVertex, "in vec4 mc_midTexCoord ;"), outVertex);
        assertEquals(0, lines(outVertex, "attribute vec4 mc_midTexCoord ;"), outVertex);
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

        final AttributeParameters newAttributes = new AttributeParameters(Patch.ATTRIBUTES, true, new InputAvailability(true, true, true));
        assertSameProgram(taumcEngine("ATTRIBUTES geometry=true inputs=111", Arrays.asList(vertex, geometry, fragment)),
            ShaderTransformer.transform(vertex, geometry, null, null, fragment, newAttributes));
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
            final String label = "ATTRIBUTES geometry=false inputs=" + (flags & 1) + ((flags & 2) >> 1) + ((flags & 4) >> 2);
            assertSameProgram(taumcEngine(label, Arrays.asList(vertex, fragment)),
                ShaderTransformer.transform(vertex, null, null, null, fragment, new AttributeParameters(Patch.ATTRIBUTES, false, inputs)));
        }
    }

    /**
     * Named deviation (Step 12): {@code transformGrouped} is Iris 26.1's, which compares glsl-transformer's
     * {@code Type}, so an {@code out mat2x2} pairs with an {@code in mat2} (they are one type in GLSL) and the unassigned
     * outputs are initialized; TauMC compared the types as spelled (S4 verification) and left them alone. Spelled
     * alike, both engines initialize both; Iris's order is its own (it prepends each initialization in the order the
     * fragment stage declares its inputs), so the programs have the same lines. The method keeps its Step 8 name, under
     * which TauMC's outputs are frozen ({@code transform-engine-taumc/groupedTypesCompareAsSpelled.txt}).
     */
    @Test
    void groupedTypesCompareAsSpelled() {
        final String vertex = "#version 330 core\nin vec3 pos;\nout mat2x2 m;\nout mat3 k;\nvoid main() { gl_Position = vec4(pos, 1.0); }\n";
        final String differently = "#version 330 core\nin mat2 m;\nin mat3x3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n";
        final String alike = "#version 330 core\nin mat2x2 m;\nin mat3 k;\nout vec4 frag;\nvoid main() { frag = vec4(m[0], k[0].xy); }\n";

        final Map<PatchShaderType, String> apart = douira(vertex, differently);
        final Map<PatchShaderType, String> taumcApart = taumc(vertex, differently);
        assertFalse(GlslTokens.contains(taumcApart.get(PatchShaderType.VERTEX), "m = mat2 ( 0.0 ) ;"));
        assertFalse(GlslTokens.contains(taumcApart.get(PatchShaderType.VERTEX), "k = mat3 ( 0.0 ) ;"));
        assertEquals(GlslTokens.of(taumcApart.get(PatchShaderType.FRAGMENT)), GlslTokens.of(apart.get(PatchShaderType.FRAGMENT)));
        assertTrue(GlslTokens.contains(apart.get(PatchShaderType.VERTEX), "void main ( ) { k = mat3 ( 0.0 ) ; m = mat2 ( 0.0 ) ;"),
            apart.get(PatchShaderType.VERTEX));
        final String withInitializations = GlslTokens.of(taumcApart.get(PatchShaderType.VERTEX)).text()
            .replace("void main ( ) {", "void main ( ) {\nk = mat3 ( 0.0 ) ;\nm = mat2 ( 0.0 ) ;");
        assertEquals(sortedLines(withInitializations), sortedLines(apart.get(PatchShaderType.VERTEX)));

        final Map<PatchShaderType, String> paired = douira(vertex, alike);
        final Map<PatchShaderType, String> taumcPaired = taumc(vertex, alike);
        taumcPaired.forEach((stage, text) -> assertEquals(sortedLines(text), sortedLines(paired.get(stage)), stage.name()));
        assertTrue(GlslTokens.contains(paired.get(PatchShaderType.VERTEX), "void main ( ) { k = mat3 ( 0.0 ) ; m = mat2 ( 0.0 ) ;"),
            paired.get(PatchShaderType.VERTEX));
    }

    /** The lines of {@link GlslTokens#text()}, stripped and sorted: a program compared without its order. */
    static List<String> sortedLines(String glsl) {
        return GlslTokens.of(glsl).text().lines().map(String::strip).filter(line -> !line.isEmpty()).sorted().toList();
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

    private static Map<PatchShaderType, String> attributes(boolean douira, String vertex, String fragment) {
        final AttributeParameters parameters = new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, true, true));
        return douira ? ShaderTransformer.transform(vertex, null, null, null, fragment, parameters)
            : taumcEngine("ATTRIBUTES geometry=false inputs=111", Arrays.asList(vertex, fragment));
    }

    /**
     * Step 7b, the {@code #extension} lines glsl-transformer 3.0.0-pre3's grammar rejects but TauMC's engine
     * transformed: {@code #extension all : warn} (and {@code all : disable} after code), an {@code #extension} inside
     * a function body, and one before {@code #version}. The new engine takes {@code #extension} lines out of the text
     * before the parse ({@code ShaderAst.ExtensionLines}) and gives TauMC's program: the same header lines, the same
     * body. The mini-corpus cases {@code composite-extension-all}, {@code -in-function} and {@code -before-version}.
     */
    @Test
    void extensionLinesTheGrammarRejects() throws IOException {
        for (String name : List.of("composite-extension-all", "composite-extension-in-function", "composite-extension-before-version")) {
            final String vertex = resource("/transform-corpus/" + name + "/in.vertex.glsl");
            final String fragment = resource("/transform-corpus/" + name + "/in.fragment.glsl");
            // glsl-transformer's own parse, without ShaderAst's text pre-pass, rejects the fragment shader.
            assertThrows(RuntimeException.class, () -> rawGlslTransformerParse(fragment), name);
            assertSameProgram(taumc(vertex, fragment), douira(vertex, fragment));
        }
        final String allWarn = douira(resource("/transform-corpus/composite-extension-all/in.vertex.glsl"),
            resource("/transform-corpus/composite-extension-all/in.fragment.glsl")).get(PatchShaderType.FRAGMENT);
        assertTrue(GlslTokens.contains(allWarn, "#extension all : warn"), allWarn);
        final String beforeVersion = douira(resource("/transform-corpus/composite-extension-before-version/in.vertex.glsl"),
            resource("/transform-corpus/composite-extension-before-version/in.fragment.glsl")).get(PatchShaderType.FRAGMENT);
        assertTrue(beforeVersion.startsWith("#version 330 core\n"), beforeVersion);
        assertTrue(GlslTokens.contains(beforeVersion, "#extension GL_ARB_gpu_shader5 : enable"), beforeVersion);
    }

    /**
     * Step 7b verification follow-up: a line comment holding {@code /*} on an {@code #extension} line. The pre-pass
     * ({@code ShaderAst.ExtensionLines}) read the {@code /*} as the start of a block comment and blanked the text after
     * it with the line, up to the next block comment's end or to the end of the source: in the leading directive block
     * the program lost its declarations and {@code main} without an error, in a function body the parse failed at
     * {@code <EOF>}. TauMC's engine, and glsl-transformer's own parse, read the line comment to its line break. The
     * mini-corpus case {@code composite-extension-line-comment} (the leading block in the fragment shader, a function
     * body in the vertex shader), and the leading block with a later block comment.
     */
    @Test
    void extensionLineWithALineCommentHoldingABlockCommentStart() throws IOException {
        final String vertex = resource("/transform-corpus/composite-extension-line-comment/in.vertex.glsl");
        final String fragment = resource("/transform-corpus/composite-extension-line-comment/in.fragment.glsl");
        final Map<PatchShaderType, String> output = douira(vertex, fragment);
        assertSameProgram(taumc(vertex, fragment), output);
        final String fragmentOutput = output.get(PatchShaderType.FRAGMENT);
        assertTrue(GlslTokens.contains(fragmentOutput, "#extension GL_ARB_gpu_shader5 : enable"), fragmentOutput);
        assertTrue(GlslTokens.contains(fragmentOutput, "uniform sampler2D colortex0 ;"), fragmentOutput);
        assertTrue(GlslTokens.contains(fragmentOutput, "outColor = texture ( colortex0 , texcoord ) ;"), fragmentOutput);
        assertTrue(GlslTokens.contains(output.get(PatchShaderType.VERTEX), "texcoord = iris_MultiTexCoord0 . xy ;"),
            output.get(PatchShaderType.VERTEX));

        final String laterBlockComment = fragment.replace("in vec2 texcoord;\n", "in vec2 texcoord;\n/* a comment */\n");
        assertFalse(laterBlockComment.equals(fragment));
        assertSameProgram(taumc(vertex, laterBlockComment), douira(vertex, laterBlockComment));
    }

    /** glsl-transformer's own parse of a program, without {@code ShaderAst}'s text pre-pass; throws on a syntax error. */
    private static Object rawGlslTransformerParse(String source) {
        final io.github.douira.glsl_transformer.ast.transform.ASTParser parser = new io.github.douira.glsl_transformer.ast.transform.ASTParser();
        parser.setParsingCacheStrategy(io.github.douira.glsl_transformer.ast.transform.ASTParser.ParsingCacheStrategy.NONE);
        ShaderAst.BUILD_LOCK.lock();
        try {
            return parser.parseTranslationUnit(ShaderAst.ROOT_SUPPLIER.get(), source);
        } finally {
            ShaderAst.BUILD_LOCK.unlock();
        }
    }

    /**
     * Step 7b, {@code patch} as an identifier. TauMC's lexer reads it as a keyword at every version, so its output is
     * broken GLSL (its error recovery writes {@code <missing ';'>} into it). glsl-transformer's lexer is version-aware:
     * raised to 330, {@code patch} is an identifier and the new engine's program is the pack's; hoisted to 420 (by
     * {@code imageLoad}), it is a keyword, and the new engine throws where TauMC's output would not have compiled
     * either. The mini-corpus cases {@code composite-patch-identifier} and {@code composite-patch-hoisted}.
     */
    @Test
    void patchAsAnIdentifier() throws IOException {
        final String vertex = resource("/transform-corpus/composite-patch-identifier/in.vertex.glsl");
        final String at330 = resource("/transform-corpus/composite-patch-identifier/in.fragment.glsl");
        assertTrue(taumc(vertex, at330).get(PatchShaderType.FRAGMENT).contains("<missing"));
        final String output = douira(vertex, at330).get(PatchShaderType.FRAGMENT);
        assertTrue(output.startsWith("#version 330 core\n"), output);
        assertTrue(GlslTokens.contains(output, "float patch = 0.5 ;"), output);
        assertTrue(GlslTokens.contains(output, "iris_FragData0 = texture ( colortex0 , texcoord ) * patch ;"), output);

        final String hoisted = resource("/transform-corpus/composite-patch-hoisted/in.fragment.glsl");
        final String taumcHoisted = taumc(vertex, hoisted).get(PatchShaderType.FRAGMENT);
        assertTrue(taumcHoisted.startsWith("#version 420 core") && taumcHoisted.contains("<missing"), taumcHoisted);
        final ShaderAst.SyntaxException thrown = assertThrows(ShaderAst.SyntaxException.class, () -> douira(vertex, hoisted));
        assertTrue(thrown.getMessage().contains("'float patch'"), thrown.getMessage());
    }

    /**
     * Step 7b, {@code gl_MultiTexCoord3} (OptiFine's alias of {@code mc_midTexCoord}) in a vertex shader, declared
     * (which GLSL itself forbids: {@code gl_} names are reserved) or read as the built-in, in ATTRIBUTES and
     * CELERITAS_TERRAIN programs. TauMC's engine patched only a declared one and then declared {@code mc_midTexCoord}
     * twice (ATTRIBUTES; CELERITAS_TERRAIN threw), and left the built-in in a core-profile program. The new engine
     * handles both as Iris 26.1 does, without the second declaration ({@code CommonTransformer.patchMultiTexCoord3}): one
     * declaration of {@code mc_midTexCoord}, no {@code gl_MultiTexCoord3}. The mini-corpus cases
     * {@code attributes-multitexcoord3-declared}, {@code attributes-multitexcoord3-builtin},
     * {@code celeritas-terrain-multitexcoord3} and {@code celeritas-terrain-multitexcoord3-builtin}.
     */
    @Test
    void multiTexCoord3Shapes() throws IOException {
        final String fragment = resource("/transform-corpus/attributes-multitexcoord3-declared/in.fragment.glsl");
        final String declared = resource("/transform-corpus/attributes-multitexcoord3-declared/in.vertex.glsl");
        final String builtin = resource("/transform-corpus/attributes-multitexcoord3-builtin/in.vertex.glsl");

        final String taumcDeclared = attributes(false, declared, fragment).get(PatchShaderType.VERTEX);
        assertEquals(2, lines(taumcDeclared, "in vec4 mc_midTexCoord ;"), taumcDeclared);
        final String taumcBuiltin = attributes(false, builtin, fragment).get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.contains(taumcBuiltin, "midcoord = gl_MultiTexCoord3 . xy ;"), taumcBuiltin);

        for (String vertex : List.of(declared, builtin)) {
            final String output = attributes(true, vertex, fragment).get(PatchShaderType.VERTEX);
            assertEquals(1, lines(output, "in vec4 mc_midTexCoord ;"), output);
            assertTrue(GlslTokens.contains(output, "midcoord = mc_midTexCoord . xy ;"), output);
            assertFalse(GlslTokens.contains(output, "gl_MultiTexCoord3"), output);
        }

        final String terrainFragment = resource("/transform-corpus/celeritas-terrain-multitexcoord3-builtin/in.fragment.glsl");
        final String terrainBuiltin = resource("/transform-corpus/celeritas-terrain-multitexcoord3-builtin/in.vertex.glsl");
        final String taumcTerrain = terrain(false, terrainBuiltin, null, terrainFragment).get(PatchShaderType.VERTEX);
        assertTrue(GlslTokens.contains(taumcTerrain, "gl_MultiTexCoord3"), taumcTerrain);
        final String output = terrain(true, terrainBuiltin, null, terrainFragment).get(PatchShaderType.VERTEX);
        assertEquals(1, lines(output, "in vec2 mc_midTexCoord ;"), output);
        assertEquals(0, lines(output, "attribute vec4 mc_midTexCoord ;"), output);
        assertEquals(0, lines(output, "in vec4 mc_midTexCoord ;"), output);
        assertTrue(GlslTokens.contains(output, "vec4 iris_MidTex = vec4 ( mc_midTexCoord . xy * 3.0517578E-5 , 0.0 , 1.0 ) ;"), output);
        assertTrue(GlslTokens.contains(output, "midcoord = ( iris_TextureMatrix * iris_MidTex ) . xy ;"), output);
        assertFalse(GlslTokens.contains(output, "gl_MultiTexCoord3"), output);

        // A shader that reads the built-in and declares mc_midTexCoord is left alone, by TauMC, Iris 26.1 and the new
        // engine alike (the mini-corpus case 'attributes'; report S7b, Open questions).
        final String both = resource("/transform-corpus/attributes/in.vertex.glsl");
        assertTrue(GlslTokens.contains(attributes(true, both, resource("/transform-corpus/attributes/in.fragment.glsl"))
            .get(PatchShaderType.VERTEX), "vec4 tangentData = gl_MultiTexCoord3 ;"));
    }

    /**
     * Step 7b pins, for the maintainer's decision (report S7b, Open questions): under {@code #version 330 core},
     * {@code texture2DRect}, {@code textureCube}, {@code texture1D} and {@code texture2DArray} calls keep their names in
     * the new engine's output ({@code GlslTransformUtils.TEXTURE_RENAMES} lacks them), and a core profile does not have
     * them; TauMC's grammar lexes them as keywords, and its output is broken GLSL. The mini-corpus case
     * {@code composite-legacy-textures}. A fix changes this test.
     */
    @Test
    void legacyTextureCallsKeepTheirNames() throws IOException {
        final String vertex = resource("/transform-corpus/composite-legacy-textures/in.vertex.glsl");
        final String fragment = resource("/transform-corpus/composite-legacy-textures/in.fragment.glsl");
        assertTrue(taumc(vertex, fragment).get(PatchShaderType.FRAGMENT).contains("<missing"));
        final String output = douira(vertex, fragment).get(PatchShaderType.FRAGMENT);
        assertTrue(output.startsWith("#version 330 core\n"), output);
        assertTrue(GlslTokens.contains(output, "iris_FragData0 = texture2DRect ( colortex4 , texcoord * 16.0 ) "
            + "+ textureCube ( skybox , vec3 ( texcoord , 1.0 ) ) + texture1D ( noise , texcoord . x ) "
            + "+ texture2DArray ( layers , vec3 ( texcoord , 0.0 ) ) ;"), output);
    }

    /** Every patch kind is ported (Step 7): nothing throws "not ported yet", and the parameter type is reset. */
    @Test
    void everyKindIsPorted() {
        final DHParameters dh = new DHParameters(Patch.DH_TERRAIN, null);
        final Map<PatchShaderType, String> output = ShaderTransformer.transform(
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
                final Map<PatchShaderType, String> old = taumcEngine(patch.name(), Arrays.asList(vertex, withGeometry, fragment));
                final DHParameters parameters = new DHParameters(patch, null);
                final Map<PatchShaderType, String> now = withoutDhTextureDeltas(patch,
                    ShaderTransformer.transform(vertex, withGeometry, null, null, fragment, parameters));
                if (withGeometry == null) {
                    assertSameProgram(old, now);
                } else {
                    // Named deviation (Step 12): Iris 26.1's transformGrouped declares and initializes the geometry
                    // stage's missing outputs in the order the fragment stage declares its inputs; TauMC's in HashMap
                    // order. The same lines otherwise.
                    // Named deviation (plan item 3.2): the geometry stage's out vec4 gcolor keeps its name, where TauMC
                    // renamed it gtexture; Iris 26.1's getGtextureRenameTargets renames only a sampler uniform.
                    assertEquals(old.keySet(), now.keySet());
                    old.forEach((stage, text) -> assertEquals(stage == PatchShaderType.GEOMETRY
                            ? sortedLines(text.replace("gtexture", "gcolor")) : GlslTokens.of(text).text(),
                        stage == PatchShaderType.GEOMETRY ? sortedLines(now.get(stage)) : GlslTokens.of(now.get(stage)).text(), stage.name()));
                }
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
            final Map<PatchShaderType, String> old = taumcEngine(patch.name(), Arrays.asList(vertex, fragment));
            final Map<PatchShaderType, String> now = withoutDhTextureDeltas(patch,
                ShaderTransformer.transform(vertex, null, null, null, fragment, new DHParameters(patch, null)));
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

    /**
     * Named deviation (plan item 3.3): Iris 26.1's DH transformer deltas, which TauMC's engine did not have. A DH_TERRAIN
     * program's vertex stage declares and writes {@code iris_vBlockPos} and {@code iris_TexId} and applies the
     * micro-offset as {@code vec3(mx, 0.0, mz)} (TauMC: {@code vec3(mx, my, mz)}), and its fragment stage declares
     * {@code iris_vBlockPos}, {@code iris_TexId} and {@code dhBlockAtlas}; the {@code dh_*} helpers (DH_TERRAIN) and
     * stand-ins (DH_GENERIC) are removed again when the stage does not call them, as in these programs. This takes those
     * lines out of the new output and puts {@code my} back, so the rest is still compared with TauMC's.
     * {@code transformer/DHTransformerTest} checks the deltas themselves.
     */
    private static Map<PatchShaderType, String> withoutDhTextureDeltas(Patch patch, Map<PatchShaderType, String> output) {
        if (patch != Patch.DH_TERRAIN) {
            return output;
        }
        final Map<PatchShaderType, String> result = new java.util.EnumMap<>(PatchShaderType.class);
        output.forEach((stage, text) -> {
            if (stage == PatchShaderType.VERTEX || stage == PatchShaderType.FRAGMENT) {
                assertTrue(text.contains("iris_TexId"), stage + ": " + text);
                text = text.lines()
                    .filter(line -> !line.contains("iris_vBlockPos") && !line.contains("iris_TexId") && !line.contains("dhBlockAtlas"))
                    .collect(java.util.stream.Collectors.joining("\n", "", "\n"))
                    .replace("vec3(mx, 0.0f, mz)", "vec3(mx, my, mz)");
            }
            result.put(stage, text);
        });
        return result;
    }

    /** COMPUTE: the shorter pre-pass list, the same header and the parameter type reset after a failure. */
    @Test
    void compute() {
        final String compute = "#version 430\nlayout(local_size_x = 8) in;\nuniform sampler2D colortex0;\nlayout(rgba8) uniform image2D colorimg0;\n"
            + "void main() { imageStore(colorimg0, ivec2(gl_GlobalInvocationID.xy), texture2D(colortex0, vec2(0.5))); }\n";
        final Map<PatchShaderType, String> old = taumcEngine("COMPUTE", List.of(compute));
        final ComputeParameters parameters = new ComputeParameters(Patch.COMPUTE, TextureStage.COMPOSITE_AND_FINAL, null);
        final Map<PatchShaderType, String> now = ShaderTransformer.transformCompute(compute, parameters);
        assertSameProgram(old, now);
        assertTrue(now.get(PatchShaderType.COMPUTE).startsWith("#version 430 core\n"), now.get(PatchShaderType.COMPUTE));
        assertNull(parameters.type);

        final ComputeParameters failing = new ComputeParameters(Patch.COMPUTE, TextureStage.COMPOSITE_AND_FINAL, null);
        assertThrows(IllegalArgumentException.class, () -> ShaderTransformer.transformCompute("void main() {}", failing));
        assertNull(failing.type);
    }
}
