package net.coderbot.iris.pipeline.transform;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.node.Profile;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExtensionDirective;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.parser.ParsingException;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference parser configuration for the glsl-transformer engine (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
 * Step 1). The transformer is set up as Iris 26.1's {@code TransformPatcher} sets up its own: an
 * {@link EnumASTTransformer} over {@link PatchShaderType} with {@link RootSupplier#PREFIX_UNORDERED_ED_EXACT}, the
 * two-tier parsing cache, the lexer's version taken from the {@code #version} directive before each parse, and a
 * {@link ChannelFilter} on {@link TokenChannel#PREPROCESSOR}. Unlike Iris's filter, which throws on a directive, this
 * one drops it and records it, as the TauMC engine ignored directives. The output is printed with
 * {@link PrintType#INDENTED}. Assertions compare tokens, never raw strings.
 */
class GlslTransformerSpikeTest {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("#version\\s+(\\d+)", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]*"
            + "|(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?[fFlLuU]*"
            + "|<<=|>>=|\\+\\+|--|<<|>>|<=|>=|==|!=|&&|\\|\\||\\^\\^|[-+*/%&|^]="
            + "|\\S");

    /** Drops every preprocessor-channel token and records the text of each directive it drops. */
    static final class DroppingPreprocessorFilter extends ChannelFilter<JobParameters> {
        final List<String> droppedDirectives = new ArrayList<>();

        DroppingPreprocessorFilter() {
            super(TokenChannel.PREPROCESSOR);
        }

        @Override
        public boolean isTokenAllowed(Token token) {
            if (super.isTokenAllowed(token)) {
                return true;
            }
            // A directive such as '#define A 1' arrives as PP_ENTER_MODE ('#define'), PP_CONTENT and PP_EOL.
            final int type = token.getType();
            if (type == GLSLLexer.PP_ENTER_MODE || type == GLSLLexer.PP_EMPTY || type == GLSLLexer.NR_LINE) {
                droppedDirectives.add(token.getText().trim());
            }
            return false;
        }
    }

    private static EnumASTTransformer<JobParameters, PatchShaderType> newTransformer(DroppingPreprocessorFilter filter) {
        final EnumASTTransformer<JobParameters, PatchShaderType> transformer = new EnumASTTransformer<>(PatchShaderType.class) {
            {
                setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
                // Replaces the parser, so it comes before setTokenFilter.
                setParsingCacheStrategy(ParsingCacheStrategy.TWO_TIER);
            }

            @Override
            public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
                final Matcher matcher = VERSION_DIRECTIVE.matcher(input);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("No #version directive found");
                }
                getLexer().version = Version.fromNumber(Integer.parseInt(matcher.group(1)));
                return super.parseTranslationUnit(rootInstance, input);
            }
        };
        transformer.setTokenFilter(filter);
        transformer.setPrintType(PrintType.INDENTED);
        return transformer;
    }

    private static Map<PatchShaderType, String> transform(EnumASTTransformer<JobParameters, PatchShaderType> transformer,
                                                          PatchShaderType type, String source,
                                                          BiConsumer<TranslationUnit, Root> transformation) {
        transformer.setTransformation(trees -> {
            final TranslationUnit tree = trees.get(type);
            final Root root = tree.getRoot();
            root.indexBuildSession(() -> transformation.accept(tree, root));
        });
        final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(type, source);
        return transformer.transform(inputs, JobParameters.EMPTY);
    }

    /** A minimal token view: identifiers, numbers, multi-character operators and single characters, one space apart. */
    static String tokens(String glsl) {
        final String withoutComments = glsl.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        final List<String> tokens = new ArrayList<>();
        final Matcher matcher = TOKEN.matcher(withoutComments);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return String.join(" ", tokens);
    }

    @Test
    void legacyVertexShaderParsesRenamesInjectsAndPrints() {
        final String vertex = """
            #version 120
            #define SPIKE_UNUSED 1
            attribute vec4 mc_Entity;
            varying vec2 texcoord;
            const float SPIKE_EPSILON = 1e-3;
            void main() {
                texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_Position = ftransform();
            }
            """;
        final DroppingPreprocessorFilter filter = new DroppingPreprocessorFilter();
        final EnumASTTransformer<JobParameters, PatchShaderType> transformer = newTransformer(filter);
        final boolean[] renamed = new boolean[1];
        final VersionStatement[] version = new VersionStatement[1];

        final Map<PatchShaderType, String> result = transform(transformer, PatchShaderType.VERTEX, vertex, (tree, root) -> {
            version[0] = tree.getVersionStatement();
            renamed[0] = root.rename("gl_TexCoord", "iris_TexCoord");
            tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ModelViewMatrix;");
            tree.prependMainFunctionBody(transformer, "iris_FogFragCoord = 0.0;");
        });
        final String printed = result.get(PatchShaderType.VERTEX);
        System.out.println("[GlslTransformerSpikeTest] #version 120 vertex, INDENTED:\n" + printed);
        final String out = tokens(printed);

        assertEquals(List.of("#define"), filter.droppedDirectives, "the filter drops and records the directive");
        assertFalse(out.contains("SPIKE_UNUSED"), out);
        assertNotNull(version[0]);
        assertEquals(Version.GLSL12, version[0].version);
        assertTrue(out.startsWith("# version 120 "), out);
        assertTrue(renamed[0], "rename reports that gl_TexCoord was found");
        assertFalse(out.contains("gl_TexCoord "), out);
        assertTrue(out.contains("iris_TexCoord [ 0 ] = gl_MultiTexCoord0 ;"), out);
        assertTrue(out.contains("uniform mat4 iris_ModelViewMatrix ;"), out);
        assertTrue(out.indexOf("uniform mat4 iris_ModelViewMatrix ;") < out.indexOf("attribute vec4 mc_Entity ;"),
            "BEFORE_DECLARATIONS puts the uniform ahead of the first declaration: " + out);
        assertTrue(out.contains("varying vec2 texcoord ;"), out);
        // The printer reprints a float literal as Double.toString(value) + "f" (ASTPrinter): 0.0 becomes 0.0f and 1e-3
        // becomes 0.001f, where the TauMC engine keeps the source text. A token comparison of the two engines' output
        // has to normalize float literals.
        assertTrue(out.contains("const float SPIKE_EPSILON = 0.001f ;"), out);
        assertTrue(out.contains("void main ( ) { iris_FogFragCoord = 0.0f ; texcoord = ( gl_TextureMatrix [ 0 ] * gl_MultiTexCoord0 ) . xy ;"), out);
        assertTrue(out.contains("gl_Position = ftransform ( ) ;"), out);
    }

    @Test
    void extensionDirectiveIsANodeAndSurvivesPrinting() {
        final String fragment = """
            #version 330 core
            #extension GL_ARB_shader_image_load_store : enable
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0);
            }
            """;
        final DroppingPreprocessorFilter filter = new DroppingPreprocessorFilter();
        final EnumASTTransformer<JobParameters, PatchShaderType> transformer = newTransformer(filter);
        final List<ExtensionDirective> extensions = new ArrayList<>();
        final VersionStatement[] version = new VersionStatement[1];

        final Map<PatchShaderType, String> result = transform(transformer, PatchShaderType.FRAGMENT, fragment, (tree, root) -> {
            version[0] = tree.getVersionStatement();
            tree.getChildren().stream()
                .filter(ExtensionDirective.class::isInstance)
                .map(ExtensionDirective.class::cast)
                .forEach(extensions::add);
        });
        final String printed = result.get(PatchShaderType.FRAGMENT);
        System.out.println("[GlslTransformerSpikeTest] #version 330 core with #extension, INDENTED:\n" + printed);
        final String out = tokens(printed);

        assertTrue(filter.droppedDirectives.isEmpty(), "#version and #extension are grammar, not preprocessor tokens");
        assertEquals(Version.GLSL33, version[0].version);
        assertEquals(Profile.CORE, version[0].profile);
        assertEquals(1, extensions.size());
        assertEquals("GL_ARB_shader_image_load_store", extensions.get(0).getName());
        assertEquals(ExtensionDirective.ExtensionBehavior.ENABLE, extensions.get(0).behavior);
        assertTrue(out.startsWith("# version 330 core # extension GL_ARB_shader_image_load_store : enable "), out);
        assertTrue(out.contains("out vec4 fragColor ;"), out);
        assertTrue(out.contains("fragColor = vec4 ( 1.0f ) ;"), out);
    }

    @Test
    void syntaxErrorRaisesTheLibraryExceptionWithALineNumber() {
        final String broken = """
            #version 330 core
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0) vec4(0.0);
            }
            """;
        final EnumASTTransformer<JobParameters, PatchShaderType> transformer = newTransformer(new DroppingPreprocessorFilter());

        final RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> transform(transformer, PatchShaderType.FRAGMENT, broken, (tree, root) -> { }));
        System.out.println("[GlslTransformerSpikeTest] syntax error: " + thrown.getClass().getName() + ": " + thrown.getMessage()
            + (thrown.getCause() != null ? " / cause: " + thrown.getCause().getMessage() : ""));

        // An input mismatch arrives as ParsingException, whose cause carries ANTLR's "line L:C ..." message; other
        // recognition errors arrive as the ParseCancellationException itself.
        assertTrue(thrown instanceof ParsingException || thrown instanceof ParseCancellationException, thrown.toString());
        final ParseCancellationException cancellation = ParsingException.extractParseCancellationException(thrown);
        assertNotNull(cancellation);
        assertTrue(cancellation.getMessage().startsWith("line 4:"), cancellation.getMessage());
    }
}
