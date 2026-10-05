package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code mc_chunkFade} declaration of {@link AttributeTransformer} (docs/IRIS_PORTING_PLAN.md, item 0.1): Iris
 * 1.11.4's {@code VanillaTransformer} declares {@code const float mc_chunkFade = -1.0;} in every stage; Demonica
 * declares it only in a stage that reads it and does not declare it. Each output is parsed again (it must parse).
 */
class AttributeTransformerTest {
    private static final String CHUNK_FADE = "const float mc_chunkFade = -1.0 ;";

    @Test
    void chunkFadeIsDeclaredInEveryStageThatReadsIt() {
        final Map<PatchShaderType, String> output = transform("""
            #version 120
            varying float chunkFade;
            void main() {
                chunkFade = mc_chunkFade;
                gl_Position = ftransform();
            }
            """, """
            #version 120
            varying float chunkFade;
            void main() {
                gl_FragData[0] = vec4(chunkFade, mc_chunkFade, 0.0, 1.0);
            }
            """);

        for (PatchShaderType stage : new PatchShaderType[] { PatchShaderType.VERTEX, PatchShaderType.FRAGMENT }) {
            final GlslTokens tokens = GlslTokens.of(output.get(stage));
            assertEquals(1, occurrences(tokens, CHUNK_FADE), stage + ": " + tokens.text());
        }
    }

    @Test
    void chunkFadeIsNotDeclaredWhereItIsNotRead() {
        final Map<PatchShaderType, String> output = transform("""
            #version 120
            void main() {
                gl_Position = ftransform();
            }
            """, """
            #version 120
            void main() {
                gl_FragData[0] = vec4(1.0);
            }
            """);

        for (String stage : new String[] { output.get(PatchShaderType.VERTEX), output.get(PatchShaderType.FRAGMENT) }) {
            assertEquals(0, GlslTokens.of(stage).count("mc_chunkFade"), stage);
        }
    }

    @Test
    void aDeclaredChunkFadeIsKept() {
        final Map<PatchShaderType, String> output = transform("""
            #version 120
            const float mc_chunkFade = 0.5;
            varying float chunkFade;
            void main() {
                chunkFade = mc_chunkFade;
                gl_Position = ftransform();
            }
            """, """
            #version 120
            varying float chunkFade;
            void main() {
                gl_FragData[0] = vec4(chunkFade);
            }
            """);
        final GlslTokens tokens = GlslTokens.of(output.get(PatchShaderType.VERTEX));

        assertEquals(0, occurrences(tokens, CHUNK_FADE), tokens.text());
        assertEquals(1, occurrences(tokens, "const float mc_chunkFade = 0.5 ;"), tokens.text());
    }

    private static Map<PatchShaderType, String> transform(String vertex, String fragment) {
        final AttributeParameters parameters = new AttributeParameters(Patch.ATTRIBUTES, false, new InputAvailability(true, true, true));
        final Map<PatchShaderType, String> output = ShaderTransformer.transform(vertex, null, null, null, fragment, parameters);
        output.values().forEach(ShaderAst::parse);
        return output;
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
