package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.CeleritasTerrainParameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CommonTransformer#replaceGlMultiTexCoordBounded}, Iris 26.1's helper (item 3.1 of docs/IRIS_PORTING_PLAN.md),
 * alone and at the three call sites it gained: ATTRIBUTES and CELERITAS_TERRAIN vertex shaders zero
 * {@code gl_MultiTexCoord4} to 7, COMPOSITE vertex shaders zero 1 to 7. Each output is printed and parsed again (it must
 * parse). The DH call sites are the mini-corpus cases {@code dh-terrain-legacy} and {@code dh-generic-legacy}, which
 * replay byte-identical.
 */
class CommonTransformerTest {
    private static final String ZERO = "vec4 ( 0.0 , 0.0 , 0.0 , 1.0 )";

    @Test
    void boundedReplacementTouchesOnlyTheRange() {
        final ShaderAst ast = ShaderAst.parse("""
            #version 120
            attribute vec4 gl_MultiTexCoord5;
            void main() {
                gl_Position = gl_MultiTexCoord3 + gl_MultiTexCoord4 + gl_MultiTexCoord5 + gl_MultiTexCoord7
                    + gl_MultiTexCoord8 + gl_MultiTexCoord4.xyzw;
            }
            """);
        CommonTransformer.replaceGlMultiTexCoordBounded(ast, 4, 7);
        final GlslTokens tokens = GlslTokens.of(ShaderAst.parse("#version 120\n" + ast.printBody()).printBody());

        assertEquals(4, occurrences(tokens, ZERO), tokens.text());
        assertEquals(1, tokens.count("gl_MultiTexCoord3"), tokens.text());
        assertEquals(1, tokens.count("gl_MultiTexCoord8"), tokens.text());
        assertEquals(0, tokens.count("gl_MultiTexCoord4"), tokens.text());
        assertEquals(0, tokens.count("gl_MultiTexCoord7"), tokens.text());
        // A declaration is not a reference expression: it stays, as in Iris's replaceReferenceExpressions.
        assertEquals(1, occurrences(tokens, "attribute vec4 gl_MultiTexCoord5 ;"), tokens.text());
        assertEquals(1, tokens.count("gl_MultiTexCoord5"), tokens.text());
    }

    @Test
    void attributesVertexZerosFourToSeven() {
        final ShaderAst ast = ShaderAst.parse(VERTEX);
        final AttributeParameters parameters = new AttributeParameters(Patch.ATTRIBUTES, false,
            new InputAvailability(true, true, true));
        parameters.type = ShaderType.VERTEX;
        AttributeTransformer.transform(ast, parameters, 330);
        final GlslTokens tokens = reparse(ast);

        assertNoBuiltinsFrom(tokens, 4);
        assertEquals(4, occurrences(tokens, ZERO), tokens.text());
        assertEquals(1, occurrences(tokens, "texcoord = ( iris_TextureMatrix * iris_MultiTexCoord0 ) . xy ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "lmcoord = ( iris_LightmapTextureMatrix * iris_MultiTexCoord1 ) . xy ;"), tokens.text());
    }

    @Test
    void celeritasTerrainVertexZerosFourToSeven() {
        final ShaderAst ast = ShaderAst.parse(VERTEX);
        final CeleritasTerrainParameters parameters = new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN);
        parameters.type = ShaderType.VERTEX;
        CeleritasTransformer.transform(ast, parameters, 330);
        final GlslTokens tokens = reparse(ast);

        assertNoBuiltinsFrom(tokens, 4);
        assertEquals(4, occurrences(tokens, ZERO), tokens.text());
        assertEquals(1, occurrences(tokens, "lmcoord = ( iris_LightmapTextureMatrix * iris_LightTexCoord ) . xy ;"), tokens.text());
    }

    @Test
    void compositeVertexZerosOneToSeven() {
        final ShaderAst ast = ShaderAst.parse(VERTEX);
        final TextureStageParameters parameters = new TextureStageParameters(Patch.COMPOSITE,
            TextureStage.COMPOSITE_AND_FINAL, null);
        parameters.type = ShaderType.VERTEX;
        CompositeDepthTransformer.transform(ast, parameters, 330);
        final GlslTokens tokens = reparse(ast);

        assertNoBuiltinsFrom(tokens, 1);
        // gl_MultiTexCoord1 and 4-7: five zero vectors; gl_MultiTexCoord0 stays the quad's texture coordinate.
        assertEquals(5, occurrences(tokens, ZERO), tokens.text());
        assertEquals(1, occurrences(tokens, "texcoord = ( iris_TextureMatrix * iris_MultiTexCoord0 ) . xy ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "layout ( location = 2 ) in vec4 iris_MultiTexCoord0 ;"), tokens.text());
    }

    @Test
    void compositeFragmentIsLeftAlone() {
        final ShaderAst ast = ShaderAst.parse("""
            #version 120
            varying vec4 extra;
            void main() {
                gl_FragData[0] = extra;
            }
            """);
        final TextureStageParameters parameters = new TextureStageParameters(Patch.COMPOSITE,
            TextureStage.COMPOSITE_AND_FINAL, null);
        parameters.type = ShaderType.FRAGMENT;
        CompositeDepthTransformer.transform(ast, parameters, 330);

        assertEquals(0, occurrences(reparse(ast), ZERO));
    }

    private static final String VERTEX = """
        #version 120
        varying vec2 texcoord;
        varying vec2 lmcoord;
        varying vec4 extra;
        void main() {
            texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            extra = gl_MultiTexCoord4 + gl_MultiTexCoord5 + gl_MultiTexCoord6 + gl_MultiTexCoord7;
            gl_Position = ftransform();
        }
        """;

    private static void assertNoBuiltinsFrom(GlslTokens tokens, int minimum) {
        for (int i = minimum; i <= 7; i++) {
            assertEquals(0, tokens.count("gl_MultiTexCoord" + i), "gl_MultiTexCoord" + i + " in " + tokens.text());
        }
    }

    private static GlslTokens reparse(ShaderAst ast) {
        return GlslTokens.of(ShaderAst.parse("#version 330 core\n" + ast.printBody()).printBody());
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
