package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.parameter.CeleritasTerrainParameters;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The glsl-transformer engine's {@link CeleritasTransformer} (Step 8 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md),
 * on the fixtures of the TauMC engine's {@code net.coderbot.iris.pipeline.transform.CeleritasTransformerTest}, which
 * stays until Step 11 and keeps testing the TauMC copy. The TauMC test walked the parse tree with a listener; here each
 * output is printed ({@link ShaderAst#printBody()}), parsed again (it must parse) and read through
 * {@link ShaderAst#findQualifiers}, {@link ShaderAst#functions()} and {@link GlslTokens}.
 */
class CeleritasTransformerTest {
    @Test
    void legacyChunkOffsetDeclarationIsReplacedByCanonicalUniform() {
        final ShaderAst output = transformVertex("""
            #version 430 compatibility
            uniform vec3 chunkOffset;

            vec3 readRegionOffset() {
                return chunkOffset;
            }

            void main() {
                gl_Position = vec4(readRegionOffset(), 1.0);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(1, occurrences(tokens, "uniform vec3 u_RegionOffset ;"), tokens.text());
        assertEquals("uniform vec3", output.findQualifiers(StorageQualifier.StorageType.UNIFORM).get("u_RegionOffset").typeText());
        assertEquals(0, tokens.count("chunkOffset"), tokens.text());
        assertEquals(1, GlslTokens.of(function(output, "readRegionOffset")).count("u_RegionOffset"), tokens.text());
    }

    @Test
    void canonicalRegionOffsetUniformIsInjectedWhenLegacyDeclarationIsAbsent() {
        final ShaderAst output = transformVertex("""
            #version 430 compatibility
            void main() {
                gl_Position = vec4(0.0);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(1, occurrences(tokens, "uniform vec3 u_RegionOffset ;"), tokens.text());
        assertEquals(0, tokens.count("chunkOffset"), tokens.text());
    }

    @Test
    void geometryStageDoesNotReprojectCeleritasClipSpacePositions() {
        final ShaderAst output = transformGeometry("""
            #version 430 core
            layout(triangles) in;
            layout(triangle_strip, max_vertices = 3) out;

            uniform mat4 gbufferModelView;

            vec4 toClipSpace3(vec3 viewSpacePosition) {
                return vec4(viewSpacePosition, -viewSpacePosition.z);
            }

            void main() {
                vec4 vertex = gl_in[0].gl_Position;
                vertex = toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz);
                gl_Position = vertex;
                EmitVertex();
                EndPrimitive();
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(1, occurrences(tokens, "vertex = vertex ;"), tokens.text());
        assertFalse(tokens.contains("toClipSpace3 ( mat3 ( gbufferModelView )"), tokens.text());
    }

    /**
     * docs/IRIS_PORTING_PLAN.md, item 0.1: a vertex stage that reads {@code mc_chunkFade} (under
     * {@code IRIS_FEATURE_FADE_VARIABLE}) gets Iris 1.11.4's value for a chunk with no fade-in time, 1.0, once.
     */
    @Test
    void chunkFadeIsDeclaredFullyFadedInWhereItIsRead() {
        final ShaderAst output = transformVertex("""
            #version 430 compatibility
            out float chunkFade;
            void main() {
                chunkFade = mc_chunkFade;
                gl_Position = vec4(0.0);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(1, occurrences(tokens, "const float mc_chunkFade = 1.0 ;"), tokens.text());
        assertEquals(2, tokens.count("mc_chunkFade"), tokens.text());
    }

    @Test
    void chunkFadeIsNotDeclaredWhereItIsNotRead() {
        final ShaderAst output = transformVertex("""
            #version 430 compatibility
            void main() {
                gl_Position = vec4(0.0);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(0, tokens.count("mc_chunkFade"), tokens.text());
    }

    @Test
    void aDeclaredChunkFadeIsKept() {
        final ShaderAst output = transformVertex("""
            #version 430 compatibility
            uniform float mc_chunkFade;
            out float chunkFade;
            void main() {
                chunkFade = mc_chunkFade;
                gl_Position = vec4(0.0);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.printBody());

        assertEquals(0, occurrences(tokens, "const float mc_chunkFade"), tokens.text());
        assertEquals(1, occurrences(tokens, "uniform float mc_chunkFade ;"), tokens.text());
    }

    /** Transforms, prints and parses again: the output must be a program glsl-transformer reads. */
    private static ShaderAst transformVertex(String source) {
        final ShaderAst ast = ShaderAst.parse(source);
        final CeleritasTerrainParameters parameters = new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN);
        parameters.type = ShaderType.VERTEX;
        CeleritasTransformer.transformVertex(ast, parameters);
        return ShaderAst.parse("#version 430 compatibility\n" + ast.printBody());
    }

    private static ShaderAst transformGeometry(String source) {
        final ShaderAst ast = ShaderAst.parse(source);
        final CeleritasTerrainParameters parameters = new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN);
        parameters.type = ShaderType.GEOMETRY;
        CeleritasTransformer.transform(ast, parameters, 460);
        return ShaderAst.parse("#version 430 core\n" + ast.printBody());
    }

    private static String function(ShaderAst ast, String name) {
        return ast.functions().stream().filter(function -> function.name().equals(name)).findFirst()
            .map(function -> ShaderAst.source(function.node())).orElseThrow();
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
