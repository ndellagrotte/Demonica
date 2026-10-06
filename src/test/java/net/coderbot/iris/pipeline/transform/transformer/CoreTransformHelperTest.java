package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.GlslTokens;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CoreTransformHelper#injectMatrixUniforms}'s texture matrices (item 3.4 of docs/IRIS_PORTING_PLAN.md):
 * {@code gl_TextureMatrix[2]} is the lightmap matrix, as Iris's {@code glTextureMatrix2}, not the identity of the
 * catch-all array, which still serves {@code [3]} to {@code [7]} and a non-literal index. The output is printed and
 * parsed again (it must parse).
 */
class CoreTransformHelperTest {
    private static final String CATCH_ALL = "mat4 [ 8 ] ( iris_TextureMatrix , iris_LightmapTextureMatrix , mat4 ( 1.0 ) ,"
        + " mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) )";

    @Test
    void textureMatrixTwoIsTheLightmapMatrix() {
        final GlslTokens tokens = transform("""
            #version 120
            uniform int index;
            void main() {
                vec4 a = gl_TextureMatrix[0] * vec4(1.0);
                vec4 b = gl_TextureMatrix[1] * vec4(1.0);
                vec4 c = gl_TextureMatrix[2] * vec4(1.0);
                vec4 d = gl_TextureMatrix[3] * vec4(1.0);
                vec4 e = gl_TextureMatrix[index] * vec4(1.0);
                gl_Position = a + b + c + d + e;
            }
            """);

        assertEquals(0, tokens.count("gl_TextureMatrix"), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 a = iris_TextureMatrix * vec4 ( 1.0 ) ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 b = iris_LightmapTextureMatrix * vec4 ( 1.0 ) ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 c = iris_LightmapTextureMatrix * vec4 ( 1.0 ) ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 d = " + CATCH_ALL + " [ 3 ] * vec4 ( 1.0 ) ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 e = " + CATCH_ALL + " [ index ] * vec4 ( 1.0 ) ;"), tokens.text());
    }

    @Test
    void textureMatrixTwoAloneNeedsNoCatchAll() {
        final GlslTokens tokens = transform("""
            #version 120
            void main() {
                gl_Position = gl_TextureMatrix[2] * gl_TextureMatrix [ 2 ] * vec4(1.0);
            }
            """);

        assertEquals(0, tokens.count("gl_TextureMatrix"), tokens.text());
        assertEquals(0, occurrences(tokens, CATCH_ALL), tokens.text());
        assertEquals(1, occurrences(tokens,
            "gl_Position = iris_LightmapTextureMatrix * iris_LightmapTextureMatrix * vec4 ( 1.0 ) ;"), tokens.text());
    }

    private static GlslTokens transform(String source) {
        final ShaderAst ast = ShaderAst.parse(source);
        CoreTransformHelper.injectMatrixUniforms(ast);
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
