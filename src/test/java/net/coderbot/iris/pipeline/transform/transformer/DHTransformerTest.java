package net.coderbot.iris.pipeline.transform.transformer;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.DHParameters;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 3.3 of docs/IRIS_PORTING_PLAN.md, Iris 26.1's DH transformer deltas, through the whole per-stage sequence
 * ({@link ShaderTransformer#transform}): DH_TERRAIN vertex stages pass the block position and DH's tile id and face index
 * on ({@code iris_vBlockPos}, {@code iris_TexId}) and offset in x and z only; DH_TERRAIN fragment stages get
 * {@code dhBlockAtlas} and the {@code dh_*} texture helpers ({@link DHTerrainTransformer#injectFragmentTextureHelpers});
 * DH_GENERIC fragment stages get the stand-ins ({@link DHGenericTransformer#injectFragmentTextureStubs}). Every output
 * is parsed again (it must parse). Mini-corpus cases {@code dh-terrain-textured}, {@code dh-terrain-textured-geometry}
 * and {@code dh-generic-textured} hold the same programs; glslangValidator compiled and linked their outputs.
 */
class DHTransformerTest {
    private static final String VERTEX = """
        #version 120
        varying vec4 glcolor;
        void main() {
            glcolor = gl_Color;
            gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
        }
        """;
    private static final String TEXTURED_FRAGMENT = """
        #version 120
        varying vec4 glcolor;
        void main() {
            vec4 albedo = glcolor;
            if (dh_hasTexture()) {
                albedo *= dh_sampleTexture();
            }
            gl_FragData[0] = albedo;
        }
        """;
    private static final String PLAIN_FRAGMENT = """
        #version 120
        varying vec4 glcolor;
        void main() {
            gl_FragData[0] = glcolor;
        }
        """;
    private static final String GEOMETRY = """
        #version 330 core
        layout(triangles) in;
        layout(triangle_strip, max_vertices = 3) out;
        in vec4 glcolorV[];
        out vec4 glcolor;
        void main() {
            for (int i = 0; i < 3; i++) {
                glcolor = glcolorV[i];
                gl_Position = gl_in[i].gl_Position;
                EmitVertex();
            }
            EndPrimitive();
        }
        """;

    @BeforeAll
    static void fullCapability() {
        // As the mini-corpus was recorded: GLSL 460 with SSBO and image load/store. Version hoisting is left as it is:
        // these programs use none of its keywords, and every DH stage is raised to 330 anyway.
        RenderSystem.initializeGlslCapabilityForTesting(460, true, true);
    }

    @AfterAll
    static void restoreGlobalState() {
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    @Test
    void terrainVertexPassesBlockPositionAndTileId() {
        final GlslTokens vertex = stage(transform(Patch.DH_TERRAIN, VERTEX, null, TEXTURED_FRAGMENT), PatchShaderType.VERTEX);

        assertEquals(1, occurrences(vertex, "out vec3 iris_vBlockPos ;"), vertex.text());
        assertEquals(1, occurrences(vertex, "flat out uvec2 iris_TexId ;"), vertex.text());
        assertEquals(1, occurrences(vertex, "iris_vBlockPos = vec3 ( vPosition . xyz ) ;"), vertex.text());
        assertEquals(1, occurrences(vertex,
            "iris_TexId = uvec2 ( irisExtra . z | ( irisExtra . w << 8u ) , irisExtra . y ) ;"), vertex.text());
        // DH 3.3.0's own vertex shader applies the micro-offset in x and z only, as Iris 26.1 does.
        assertEquals(1, occurrences(vertex, "_vert_position = ( vPosition . xyz + vec3 ( mx , 0.0 , mz ) ) ;"), vertex.text());
        assertEquals(0, occurrences(vertex, "vec3 ( mx , my , mz )"), vertex.text());
    }

    @Test
    void terrainFragmentDefinesTheTextureHelpers() {
        final GlslTokens fragment = stage(transform(Patch.DH_TERRAIN, VERTEX, null, TEXTURED_FRAGMENT), PatchShaderType.FRAGMENT);

        assertEquals(1, occurrences(fragment, "in vec3 iris_vBlockPos ;"), fragment.text());
        assertEquals(1, occurrences(fragment, "flat in uvec2 iris_TexId ;"), fragment.text());
        assertEquals(1, occurrences(fragment, "uniform sampler2D dhBlockAtlas ;"), fragment.text());
        assertEquals(1, occurrences(fragment, "bool dh_hasTexture ( ) { return iris_TexId . x != 0u ; }"), fragment.text());
        assertEquals(1, occurrences(fragment, "vec2 dh_blockFaceUv ( ) { vec3 pos = fract ( iris_vBlockPos ) ; switch ( iris_TexId . y ) {"),
            fragment.text());
        assertEquals(0, occurrences(fragment, "case 5u"), fragment.text());
        assertEquals(1, occurrences(fragment, "default : return vec2 ( 1.0 - pos . z , 1.0 - pos . y ) ;"), fragment.text());
        assertEquals(1, occurrences(fragment,
            "vec2 tileOrigin = vec2 ( float ( iris_TexId . x % 256u ) , float ( iris_TexId . x / 256u ) ) * 16.0 ;"), fragment.text());
        assertEquals(1, occurrences(fragment, "return texture ( dhBlockAtlas , uv ) ;"), fragment.text());
        // The helpers come before the pack's main, which calls them.
        final List<String> tokens = fragment.tokens();
        assertTrue(Collections.indexOfSubList(tokens, List.of("dh_sampleTexture", "(", ")", "{"))
            < Collections.indexOfSubList(tokens, List.of("main", "(", ")", "{")), fragment.text());
    }

    @Test
    void terrainFragmentWithoutCallsKeepsOnlyTheDeclarations() {
        // Iris's removeUnusedFunctions (CompatibilityTransformer.transformEach) drops helpers no one calls.
        final GlslTokens fragment = stage(transform(Patch.DH_TERRAIN, VERTEX, null, PLAIN_FRAGMENT), PatchShaderType.FRAGMENT);

        assertEquals(1, occurrences(fragment, "flat in uvec2 iris_TexId ;"), fragment.text());
        assertEquals(1, occurrences(fragment, "uniform sampler2D dhBlockAtlas ;"), fragment.text());
        assertEquals(0, fragment.count("dh_hasTexture"), fragment.text());
        assertEquals(0, fragment.count("dh_sampleTexture"), fragment.text());
        assertEquals(0, fragment.count("dh_blockFaceUv"), fragment.text());
    }

    @Test
    void genericFragmentGetsTheStandIns() {
        final Map<PatchShaderType, String> output = transform(Patch.DH_GENERIC, VERTEX, null, TEXTURED_FRAGMENT);
        final GlslTokens fragment = stage(output, PatchShaderType.FRAGMENT);
        final GlslTokens vertex = stage(output, PatchShaderType.VERTEX);

        assertEquals(1, occurrences(fragment, "bool dh_hasTexture ( ) { return false ; }"), fragment.text());
        assertEquals(1, occurrences(fragment, "vec4 dh_sampleTexture ( ) { return vec4 ( 1.0 ) ; }"), fragment.text());
        assertEquals(0, fragment.count("dhBlockAtlas"), fragment.text());
        assertEquals(0, fragment.count("iris_TexId"), fragment.text());
        assertEquals(0, vertex.count("iris_TexId"), vertex.text());
        assertEquals(0, vertex.count("iris_vBlockPos"), vertex.text());

        final GlslTokens plain = stage(transform(Patch.DH_GENERIC, VERTEX, null, PLAIN_FRAGMENT), PatchShaderType.FRAGMENT);
        assertEquals(0, plain.count("dh_hasTexture"), plain.text());
        assertEquals(0, plain.count("dh_sampleTexture"), plain.text());
    }

    @Test
    void aGeometryStageThatDoesNotForwardThemWritesZeros() {
        // Iris's transformGrouped declares the fragment stage's inputs the geometry stage lacks and zeroes them, so the
        // LOD reads tile id 0 and keeps its flat colour. The tile id stays flat and unsigned (PORTING_GUIDE rule 5).
        final String vertex = VERTEX.replace("varying vec4 glcolor;", "varying vec4 glcolorV;")
            .replace("glcolor = gl_Color;", "glcolorV = gl_Color;");
        final Map<PatchShaderType, String> output = transform(Patch.DH_TERRAIN, vertex, GEOMETRY, TEXTURED_FRAGMENT);
        final GlslTokens geometry = stage(output, PatchShaderType.GEOMETRY);

        assertEquals(1, occurrences(geometry, "flat out uvec2 iris_TexId ;"), geometry.text());
        assertEquals(1, occurrences(geometry, "out vec3 iris_vBlockPos ;"), geometry.text());
        assertEquals(1, occurrences(geometry, "iris_TexId = uvec2 ( 0u ) ;"), geometry.text());
        assertEquals(1, occurrences(geometry, "iris_vBlockPos = vec3 ( 0.0 ) ;"), geometry.text());
        assertEquals(1, occurrences(stage(output, PatchShaderType.FRAGMENT), "bool dh_hasTexture ( ) { return iris_TexId . x != 0u ; }"));
    }

    @Test
    void theSameInputGivesTheSameOutput() {
        // PORTING_GUIDE rule 2: the idiom code injects in a fixed order.
        final Map<PatchShaderType, String> first = transform(Patch.DH_TERRAIN, VERTEX, null, TEXTURED_FRAGMENT);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, transform(Patch.DH_TERRAIN, VERTEX, null, TEXTURED_FRAGMENT));
        }
    }

    private static Map<PatchShaderType, String> transform(Patch patch, String vertex, String geometry, String fragment) {
        return ShaderTransformer.transform(vertex, geometry, null, null, fragment, new DHParameters(patch, null));
    }

    private static GlslTokens stage(Map<PatchShaderType, String> output, PatchShaderType stage) {
        final String text = output.get(stage);
        assertFalse(text == null, stage + " missing");
        // It must parse again.
        final String body = text.substring(text.indexOf('\n') + 1);
        return GlslTokens.of(ShaderAst.parse("#version 330 core\n" + body).printBody());
    }

    private static int occurrences(GlslTokens tokens, String snippet) {
        final List<String> wanted = GlslTokens.of(snippet).tokens();
        final List<String> all = tokens.tokens();
        int count = 0;
        for (int from = 0; from + wanted.size() <= all.size(); ) {
            final int index = Collections.indexOfSubList(all.subList(from, all.size()), wanted);
            if (index < 0) {
                break;
            }
            count++;
            from += index + wanted.size();
        }
        return count;
    }
}
