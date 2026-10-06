package net.coderbot.iris.pipeline.transform.transformer;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CommonTransformer#renameGtexture}, Iris 26.1's {@code gtexture} renaming and merge (item 3.2 of
 * docs/IRIS_PORTING_PLAN.md), in the sequence {@code ShaderTransformer} runs it: the {@code replaceTexture} pre-pass, the
 * parse, the patch transformer, {@code renameGtexture}, {@link TextureTransformer}, the print and
 * {@code restoreReservedWords}. Each output is parsed again (it must parse). The mini-corpus cases
 * {@code composite-gtexture-two-decls}, {@code attributes-gtexture-two-decls} and {@code composite-gtexture-gcolor-only}
 * hold the whole programs.
 */
class GtextureMergeTest {
    private static final String SAMPLER = "uniform sampler2D gtexture ;";

    @Test
    void twoDeclarationsKeepGcolors() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D texture;
            uniform sampler2D colortex1;
            uniform sampler2D gcolor;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(texture, texcoord) + texture2D(gcolor, texcoord) + texture2D(colortex1, texcoord);
            }
            """, null);

        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        // gcolor's declaration is the one kept, after colortex1, as in Iris.
        assertEquals(1, occurrences(tokens, "uniform sampler2D colortex1 ; uniform sampler2D gtexture ;"), tokens.text());
        assertEquals(0, tokens.count("gcolor"), tokens.text());
        assertEquals(1, occurrences(tokens, "texture ( gtexture , texcoord ) + texture ( gtexture , texcoord )"), tokens.text());
    }

    @Test
    void oneDeclarationLosesTheTextureMember() {
        final ShaderAst ast = ShaderAst.parse(GlslTransformUtils.replaceTexture("""
            #version 120
            uniform sampler2D gcolor, texture, normals;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(texture, texcoord) * texture2D(gcolor, texcoord) * texture2D(normals, texcoord);
            }
            """));
        final AttributeParameters parameters = new AttributeParameters(Patch.ATTRIBUTES, false,
            new InputAvailability(true, true, true));
        parameters.type = ShaderType.FRAGMENT;
        AttributeTransformer.transform(ast, parameters, 330);
        final GlslTokens tokens = finish(ast, parameters);

        assertEquals(1, occurrences(tokens, "uniform sampler2D gtexture , normals ;"), tokens.text());
        assertEquals(3, tokens.count("gtexture"), tokens.text());
        assertEquals(0, tokens.count("gcolor"), tokens.text());
    }

    @Test
    void textureAloneIsRenamedAsBefore() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D texture;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(texture, texcoord);
            }
            """, null);

        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        assertEquals(1, occurrences(tokens, "texture ( gtexture , texcoord )"), tokens.text());
    }

    @Test
    void gcolorAloneIsRenamedAsBefore() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D gcolor;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(gcolor, texcoord);
            }
            """, null);

        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        assertEquals(1, occurrences(tokens, "texture ( gtexture , texcoord )"), tokens.text());
        assertEquals(0, tokens.count("gcolor"), tokens.text());
    }

    /** Iris renames an unused sampler declaration too; the TauMC engine's verb wanted a use of gcolor. */
    @Test
    void unusedGcolorSamplerIsRenamed() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D gcolor;
            void main() {
                gl_FragData[0] = vec4(1.0);
            }
            """, null);

        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        assertEquals(0, tokens.count("gcolor"), tokens.text());
    }

    /** A gcolor that is not a sampler uniform keeps its name (Iris); the TauMC engine renamed it. */
    @Test
    void gcolorThatIsNotASamplerKeepsItsName() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform vec4 gcolor;
            void main() {
                gl_FragData[0] = gcolor;
            }
            """, null);

        assertEquals(0, tokens.count("gtexture"), tokens.text());
        assertEquals(1, occurrences(tokens, "uniform vec4 gcolor ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "= gcolor ;"), tokens.text());
    }

    /**
     * The branch kept from the TauMC engine: a texture that is declared but not as a sampler uniform (here a local) is
     * still renamed gtexture, so the restored name does not hide the texture() function in its scope.
     */
    @Test
    void textureThatIsNotASamplerIsStillRenamed() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D colortex0;
            varying vec2 texcoord;
            void main() {
                vec4 texture = texture2D(colortex0, texcoord);
                gl_FragData[0] = texture * texture2D(colortex0, texcoord);
            }
            """, null);

        assertEquals(1, occurrences(tokens, "vec4 gtexture = texture ( colortex0 , texcoord ) ;"), tokens.text());
        assertEquals(1, occurrences(tokens, "= gtexture * texture ( colortex0 , texcoord ) ;"), tokens.text());
    }

    /** Iris renames a parameter named texture with its uses; the TauMC verb left the parameter and renamed the uses. */
    @Test
    void parameterNamedTextureIsRenamedWithItsUses() {
        final GlslTokens tokens = compositeFragment("""
            #version 120
            uniform sampler2D texture;
            varying vec2 texcoord;
            vec4 fetch(sampler2D texture, vec2 uv) {
                return texture2D(texture, uv);
            }
            void main() {
                gl_FragData[0] = fetch(texture, texcoord);
            }
            """, null);

        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        assertEquals(1, occurrences(tokens, "vec4 fetch ( sampler2D gtexture , vec2 uv ) { return texture ( gtexture , uv ) ; }"),
            tokens.text());
        assertEquals(1, occurrences(tokens, "fetch ( gtexture , texcoord )"), tokens.text());
    }

    /**
     * Iris's order: the merge runs before {@link TextureTransformer}, so a pack's custom texture for {@code gtexture}
     * renames the merged sampler, and one for {@code gcolor} finds nothing to rename.
     */
    @Test
    void textureTransformerSeesTheMergedName() {
        final String source = """
            #version 120
            uniform sampler2D texture;
            uniform sampler2D gcolor;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(texture, texcoord) + texture2D(gcolor, texcoord);
            }
            """;
        final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> gtexture = new Object2ObjectOpenHashMap<>();
        gtexture.put(new Tri<>("gtexture", TextureType.TEXTURE_2D, TextureStage.COMPOSITE_AND_FINAL), "customAlbedo");
        GlslTokens tokens = compositeFragment(source, gtexture);
        assertEquals(1, occurrences(tokens, "uniform sampler2D customAlbedo ;"), tokens.text());
        assertEquals(0, tokens.count("gtexture"), tokens.text());

        final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> gcolor = new Object2ObjectOpenHashMap<>();
        gcolor.put(new Tri<>("gcolor", TextureType.TEXTURE_2D, TextureStage.COMPOSITE_AND_FINAL), "customAlbedo");
        tokens = compositeFragment(source, gcolor);
        assertEquals(1, occurrences(tokens, SAMPLER), tokens.text());
        assertEquals(0, tokens.count("customAlbedo"), tokens.text());
    }

    /**
     * Iris walks the identifier index, a set in no fixed order, and the first file-scope declaration it meets decides;
     * this takes them in document order (PORTING_GUIDE rule 2). Redeclaring a uniform with another type is not valid
     * GLSL; the programs only check that the answer follows the document and is the same every time.
     */
    @Test
    void theFirstDeclarationInTheDocumentDecides() {
        final String samplerFirst = """
            #version 120
            uniform sampler2D gcolor;
            uniform vec4 gcolor;
            void main() {
                gl_FragData[0] = vec4(1.0);
            }
            """;
        final String samplerSecond = """
            #version 120
            uniform vec4 gcolor;
            uniform sampler2D gcolor;
            void main() {
                gl_FragData[0] = vec4(1.0);
            }
            """;
        final String first = compositeFragment(samplerFirst, null).text();
        final String second = compositeFragment(samplerSecond, null).text();
        assertEquals(0, GlslTokens.of(first).count("gcolor"), first);
        assertEquals(2, GlslTokens.of(second).count("gcolor"), second);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, compositeFragment(samplerFirst, null).text());
            assertEquals(second, compositeFragment(samplerSecond, null).text());
        }
    }

    private static GlslTokens compositeFragment(String source,
                                                Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        final ShaderAst ast = ShaderAst.parse(GlslTransformUtils.replaceTexture(source));
        final TextureStageParameters parameters = new TextureStageParameters(Patch.COMPOSITE,
            TextureStage.COMPOSITE_AND_FINAL, textureMap);
        parameters.type = ShaderType.FRAGMENT;
        CompositeDepthTransformer.transform(ast, parameters, 330);
        return finish(ast, parameters);
    }

    // ShaderTransformer.doTransform's order after the patch transformer, then the print and restoreReservedWords.
    private static GlslTokens finish(ShaderAst ast, Parameters parameters) {
        CommonTransformer.renameGtexture(ast);
        TextureTransformer.transform(ast, parameters);
        final String printed = GlslTransformUtils.restoreReservedWords(ast.printBody());
        return GlslTokens.of(ShaderAst.parse("#version 330 core\n" + printed).printBody());
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
