package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.FunctionDeclaration;
import io.github.douira.glsl_transformer.ast.node.declaration.FunctionParameter;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.ArrayAccessExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.BinaryExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.MemberAccessExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExtensionDirective;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.node.external_declaration.LayoutDefaults;
import io.github.douira.glsl_transformer.ast.node.external_declaration.PragmaDirective;
import io.github.douira.glsl_transformer.ast.node.statement.CompoundStatement;
import io.github.douira.glsl_transformer.ast.node.statement.loop.ForLoopStatement;
import io.github.douira.glsl_transformer.ast.node.statement.terminal.DeclarationStatement;
import io.github.douira.glsl_transformer.ast.node.type.FullySpecifiedType;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeReference;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor;
import io.github.douira.glsl_transformer.parser.ParsingException;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import io.github.douira.glsl_transformer.util.Type;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * One parsed GLSL program on glsl-transformer, with the verbs Demonica's transformers call on TauMC's
 * {@code org.taumc.glsl.Transformer}, under the same names and with the same semantics
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, 3.4 and Appendix B). A transformer class ports by changing its
 * parameter type; code that is being brought closer to upstream Iris can use {@link #t}, {@link #tree} and
 * {@link #root} directly, as Iris does.
 *
 * <p>The semantics are TauMC's at the pinned build {@code 0.2.0-32.g7dd88a4-GTNH} (sources at commit {@code 7dd88a4}),
 * where {@code Transformer} implements each verb over parse-tree rule contexts. Where TauMC's grammar and
 * glsl-transformer's AST name the same construct differently, each verb's javadoc says which AST nodes stand for
 * which rule. Deliberate deviations (TauMC throwing, or emitting broken GLSL) are named in the verb's javadoc.</p>
 *
 * <p>The nineteen verbs: {@link #injectVariable}, {@link #injectFunction}, {@link #rename(String, String)},
 * {@link #rename(Map)}, {@link #replaceExpression}, {@link #prependMain}, {@link #appendMain},
 * {@link #removeVariable}, {@link #findType}, {@link #containsCall}, {@link #hasVariable},
 * {@link #renameFunctionCall(String, String)}, {@link #renameFunctionCall(Map)}, {@link #renameArray} (Step 3), and
 * {@link #renameAndWrapShadow}, {@link #removeUnusedFunctions}, {@link #removeConstAssignment},
 * {@link #findQualifiers}, {@link #hasAssignment}, {@link #initialize} and {@link #replaceFunctionDefinition(String,
 * String)} (Step 4; TauMC's three-argument {@code replaceExpression} over function definitions). The queries
 * {@link #functions()}, {@link #source(FunctionDefinition)}, {@link #text(ASTNode)} and {@link #isDeclaredGlobal}
 * have no TauMC counterpart; they replace the parse-tree walks {@code AdaptiveShadowBoundsTransformer} and
 * {@code CompatibilityTransformer} do through {@code mutateTree}.</p>
 *
 * <p>Order. Where a TauMC verb takes "the first" or "the last" of something, it took it from its rule-context cache:
 * the parsed program in document order, followed by every subtree a verb added since (an injection, a prepended or
 * appended statement, a replacement), in the order they were added. This class records what its verbs add and
 * rebuilds that order where a verb depends on it (the injection anchors, {@link #findType}, {@link #removeVariable},
 * {@link #findQualifiers}, {@link #removeConstAssignment}).</p>
 *
 * <p>Life cycle: {@link #parse(String, int)}, then the verbs, then {@link #print(String)}. An instance is not
 * thread-safe. Different instances may be used on different threads: they share one parser, and every method that
 * parses or builds AST nodes holds {@link #BUILD_LOCK} while it does (see {@link #newParser} and {@link #build}).</p>
 *
 * <p>This class lives in the {@code glsm} project, not next to the Iris transformers in {@code shader}, because
 * GLSM's {@code CompatShaderTransformer} must use it too and {@code glsm} cannot see {@code shader}.</p>
 */
public final class ShaderAst {
    private static final Logger LOGGER = LogManager.getLogger("ShaderAst");

    /**
     * Iris 26.1's root: a prefix identifier index (for {@code prefixQueryFlat}) and an exact external-declaration
     * index. The node index is unordered, so every verb that depends on document order walks the tree.
     */
    public static final RootSupplier ROOT_SUPPLIER = RootSupplier.PREFIX_UNORDERED_ED_EXACT;

    /**
     * glsl-transformer 3.0.0-pre3 gives every AST node it constructs the root on top of a static, unsynchronized
     * stack ({@code Root.activeBuildRoots}), which each build pushes and pops. Two threads building nodes at once, even
     * with separate parsers, can give nodes the other thread's root or corrupt the stack. The lock also guards the one
     * parser every program shares ({@link #t}; see {@link #newParser}). Every method of this class that builds nodes
     * (the parse, and each verb that parses a snippet) holds this lock while it does; code that uses {@link #t},
     * {@link #tree} or {@link #root} to build nodes itself (a {@code parseAndInjectNode}, a
     * {@code new Identifier(...)}) must run it through {@link #build}, which holds the lock and sets the lexer version.
     * Reentrant. Step 5 measured holding it around a whole transform instead: slower under concurrency, no faster
     * alone (docs/glsl-transformer_adoption/reports/S05-orchestrator-composite.md).
     */
    public static final ReentrantLock BUILD_LOCK = new ReentrantLock();

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("#version\\s+(\\d+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * The parser, shared by every program: the argument every glsl-transformer call that parses a snippet needs. Use it
     * only inside {@link #build}.
     */
    public final ASTParser t;
    /** The program. {@link #print(String)} removes its version statement and extension directives. */
    public final TranslationUnit tree;
    /** The indexes of {@link #tree}: identifiers by name, nodes by class, external declarations by name. */
    public final Root root;

    private final List<String> droppedDirectives;
    // How many of the program's #extension directives are in its leading directive block (see leadingExtensionCount).
    private final int leadingExtensions;
    // The lexer version of the parse; every snippet a verb parses is lexed at it too (see snippet).
    private final Version lexerVersion;
    // The spelling of each numeric type specifier of the parsed program whose spelling is not the type's compact name
    // (mat2x2 for mat2): glsl-transformer keeps the Type only, TauMC compared the spelled text (typeName).
    private final Map<BuiltinNumericTypeSpecifier, String> spelledTypes;

    // TauMC's Transformer.variable and Transformer.function: where the next injectVariable and injectFunction insert.
    private ExternalDeclaration variableAnchor;
    private ExternalDeclaration functionAnchor;

    // Every subtree a verb added, with the sequence number of its addition (see inTauMCOrder).
    private final Map<ASTNode, Integer> additions = new IdentityHashMap<>();
    private int additionCount;

    private static final Set<Expression.ExpressionType> ASSIGNMENTS = EnumSet.of(Expression.ExpressionType.ASSIGNMENT,
        Expression.ExpressionType.MULTIPLICATION_ASSIGNMENT, Expression.ExpressionType.DIVISION_ASSIGNMENT,
        Expression.ExpressionType.MODULO_ASSIGNMENT, Expression.ExpressionType.ADDITION_ASSIGNMENT,
        Expression.ExpressionType.SUBTRACTION_ASSIGNMENT, Expression.ExpressionType.LEFT_SHIFT_ASSIGNMENT,
        Expression.ExpressionType.RIGHT_SHIFT_ASSIGNMENT, Expression.ExpressionType.BITWISE_AND_ASSIGNMENT,
        Expression.ExpressionType.BITWISE_XOR_ASSIGNMENT, Expression.ExpressionType.BITWISE_OR_ASSIGNMENT);

    private ShaderAst(ASTParser t, TranslationUnit tree, Root root, List<String> droppedDirectives, int leadingExtensions,
                      Version lexerVersion, Map<BuiltinNumericTypeSpecifier, String> spelledTypes) {
        this.t = t;
        this.tree = tree;
        this.root = root;
        this.droppedDirectives = droppedDirectives;
        this.leadingExtensions = leadingExtensions;
        this.lexerVersion = lexerVersion;
        this.spelledTypes = spelledTypes;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Life cycle

    /**
     * Parses a program with the lexer at the version its {@code #version} directive names, or at the library's
     * default (the newest GLSL version) if it has none.
     *
     * @throws SyntaxException          if the source does not parse
     * @throws IllegalArgumentException if glsl-transformer has no {@link Version} for the directive's number
     */
    public static ShaderAst parse(String source) {
        final java.util.regex.Matcher version = VERSION_DIRECTIVE.matcher(source);
        return parse(source, version.find() ? Version.fromNumber(Integer.parseInt(version.group(1))) : null);
    }

    /**
     * Parses a program with the lexer set to GLSL {@code version}, which decides which words are keywords
     * ({@code sample} is one from 400 on). Pass the version of the {@code #version} line the source carries.
     *
     * <p>Preprocessor directives other than {@code #version} and {@code #extension} are dropped and logged, as the
     * TauMC engine ignored them: the channel filter drops {@code #define}, {@code #if} and the like, and
     * {@code #pragma}, which glsl-transformer parses, is removed from the tree. An {@code #extension} after the leading
     * directive block ({@link #extensionDirectives()}) is dropped too, as the TauMC engine dropped it.
     * {@link #droppedDirectives()} lists them. {@code #version} and {@code #extension} stay in the tree until
     * {@link #print(String)}.</p>
     *
     * @throws SyntaxException          if the source does not parse
     * @throws IllegalArgumentException if glsl-transformer has no {@link Version} for {@code version}
     */
    public static ShaderAst parse(String source, int version) {
        return parse(source, Version.fromNumber(version));
    }

    private static ShaderAst parse(String source, Version lexerVersion) {
        final DirectiveFilter filter = new DirectiveFilter();
        final Root root = ROOT_SUPPLIER.get();
        final Version version = lexerVersion != null ? lexerVersion : DEFAULT_LEXER_VERSION;
        final TranslationUnit tree;
        final List<String> dropped;
        final List<TypeToken> typeTokens;
        BUILD_LOCK.lock();
        try {
            PARSER.setTokenFilter(filter);
            PARSER.getLexer().version = version;
            tree = PARSER.parseTranslationUnit(root, source);
            // Read under the lock: the filter stays on the shared parser, and the next parse (a snippet of another
            // program on another thread) resets it.
            dropped = new ArrayList<>(filter.dropped.values());
            typeTokens = new ArrayList<>(filter.numericTypes.values());
        } catch (ParsingException | ParseCancellationException e) {
            throw SyntaxException.of(e);
        } finally {
            filter.recording = false;
            BUILD_LOCK.unlock();
        }
        final int leadingExtensions = leadingExtensionCount(source);
        int extensions = 0;
        for (ExternalDeclaration declaration : new ArrayList<>(tree.getChildren())) {
            if (declaration instanceof PragmaDirective pragma) {
                dropped.add(ASTPrinter.print(PrintType.COMPACT, pragma).trim());
                pragma.detachAndDelete();
            } else if (declaration instanceof ExtensionDirective extension && ++extensions > leadingExtensions) {
                // Left in the tree: print() removes every #extension, and extensionDirectives() skips this one.
                dropped.add(extensionLine(extension) + " (after the leading directives)");
            }
        }
        if (!dropped.isEmpty()) {
            LOGGER.warn("[ShaderAst] Dropped {} preprocessor directive(s): {}", dropped.size(), dropped);
        }
        return new ShaderAst(PARSER, tree, root, List.copyOf(dropped), Math.min(leadingExtensions, extensions), version,
            spelledTypes(tree, typeTokens));
    }

    private static final Pattern EXTENSION_LINE = Pattern.compile("#[ \\t]*extension\\b");

    /**
     * How many {@code #extension} directives the leading directive block of {@code source} holds: the ones the TauMC
     * engine's header kept. TauMC took its header from a pre-parser that read the lexer's tokens on every channel (a
     * {@code BufferedTokenStream}, so whitespace and comments included) and stopped at the first token outside a
     * directive. Its block is therefore the lines from the start of the source that begin with {@code #} in column 0,
     * up to the first line that does not: a blank line, an indented line, a comment or code. A backslash before the
     * line break continues a line, as in a {@code #define}. Probed against the pinned TauMC jar
     * ({@code ShaderAstParityTest.extensionHeaderLines}); the recorded corpora have every {@code #extension} (111) in
     * this block.
     *
     * <p>Not modelled, because preprocessed sources have neither: a comment on a directive line, which TauMC's
     * directive lexer modes have no token for (its error recovery then sometimes loses the next directive, and this
     * rule keeps it); and conditional directives, after which TauMC's lexer reads program text into the block.</p>
     */
    static int leadingExtensionCount(String source) {
        final int length = source.length();
        int count = 0;
        int start = 0;
        while (start < length && source.charAt(start) == '#') {
            int end = start;
            while (true) {
                final int newline = source.indexOf('\n', end);
                if (newline < 0) {
                    end = length;
                    break;
                }
                int last = newline - 1;
                if (last > end && source.charAt(last) == '\r') {
                    last--;
                }
                end = newline + 1;
                if (last <= start || source.charAt(last) != '\\') {
                    break;
                }
            }
            if (EXTENSION_LINE.matcher(source).region(start, end).lookingAt()) {
                count++;
            }
            start = end;
        }
        return count;
    }

    /**
     * Pairs the numeric type keywords the lexer read with the {@link BuiltinNumericTypeSpecifier}s the parse built:
     * the grammar builds exactly one specifier from each such token ({@code builtinTypeSpecifierParseable} occurs only
     * in {@code typeSpecifier}), in document order. Keeps the spellings that are not the type's compact name. If the
     * two sequences do not pair up, no spelling is kept and {@link QualifiedDeclaration#typeName()} falls back to
     * compact names.
     */
    private static Map<BuiltinNumericTypeSpecifier, String> spelledTypes(TranslationUnit tree, Collection<TypeToken> tokens) {
        final List<BuiltinNumericTypeSpecifier> specifiers = new ArrayList<>(tokens.size());
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof BuiltinNumericTypeSpecifier specifier) {
                    specifiers.add(specifier);
                }
            }
        }.visit(tree);
        final Map<BuiltinNumericTypeSpecifier, String> spelled = new IdentityHashMap<>();
        if (specifiers.size() != tokens.size()) {
            LOGGER.debug("[ShaderAst] {} numeric type tokens, {} type specifiers; type spellings not kept", tokens.size(),
                specifiers.size());
            return spelled;
        }
        final Iterator<TypeToken> token = tokens.iterator();
        for (BuiltinNumericTypeSpecifier specifier : specifiers) {
            final TypeToken next = token.next();
            if (next.type() != specifier.type) {
                LOGGER.debug("[ShaderAst] type token {} paired with a {} specifier; type spellings not kept", next.text(),
                    specifier.type);
                return new IdentityHashMap<>();
            }
            if (!next.text().equals(specifier.type.getMostCompactName())) {
                spelled.put(specifier, next.text());
            }
        }
        return spelled;
    }

    /** A numeric type keyword as the lexer read it: its type and its spelling. */
    record TypeToken(Type type, String text) {
    }

    // Every numeric type keyword's token type (mat2 and mat2x2 are one token type, F32MAT2X2).
    private static final Map<Integer, Type> NUMERIC_TYPE_TOKENS = numericTypeTokens();

    private static Map<Integer, Type> numericTypeTokens() {
        final Map<Integer, Type> tokens = new HashMap<>();
        for (Type type : Type.values()) {
            if (type.getTokenType() != Token.INVALID_TYPE) {
                tokens.put(type.getTokenType(), type);
            }
        }
        return Map.copyOf(tokens);
    }

    /**
     * The one parser of every program and every snippet, used only under {@link #BUILD_LOCK} (see {@link #newParser}).
     * Each {@link #parse} sets its own filter and lexer version on it; {@link #build} sets the program's version again
     * before a verb's snippet.
     */
    private static final ASTParser PARSER = newParser(new DirectiveFilter());

    // The lexer's own default version, for a source without #version.
    private static final Version DEFAULT_LEXER_VERSION = PARSER.getLexer().version;

    /**
     * Builds a parser; {@link #PARSER}, the one every program uses, is built here. Step 5 measured one parser per
     * {@link #parse} against one shared parser under {@link #BUILD_LOCK} on the recorded COMPOSITE cases (the replay's
     * concurrent pass and {@code TransformPatcherCacheTest}): the shared parser was faster alone and on eight threads,
     * mostly because its AST cache (below) keeps the verbs' snippets across programs (report
     * docs/glsl-transformer_adoption/reports/S05-orchestrator-composite.md).
     *
     * <p>Parsing cache: {@link ASTParser.ParsingCacheStrategy#NONE}. The two-tier cache that Iris uses returns a
     * cached parse tree for a translation unit it has seen, and then the channel filter sees no tokens, so a
     * dropped directive would go unlogged. {@code ALL_EXCLUDING_TRANSLATION_UNIT}, which would fit, recurses without
     * end in 3.0.0-pre3 ({@code TranslationUnitFilterCachingParser.parse} calls itself). Snippet ASTs (the verbs'
     * code strings) are still cached by the parser's own AST cache, which this setting does not affect; that cache
     * lives as long as the parser, so with the shared parser a snippet is parsed once and cloned into every later
     * program that uses it. That cache (glsl-transformer's {@code TypedTreeCache}, an LRU of 400 entries) is keyed on
     * the snippet's text and grammar rule, not on the lexer version: a snippet first parsed for a program at one
     * version is reused for programs at other versions; the version {@link #build} sets matters only on a cache miss.
     * A snippet whose words are keywords at some versions and identifiers at others would therefore be read as the
     * first program's version reads it; the risk is small, because the verbs' snippets are Iris's own code and
     * {@code renameReservedWords} renames such words in pack code before the parse (S5 verification).</p>
     */
    static ASTParser newParser(DirectiveFilter filter) {
        final ASTParser parser = new ASTParser();
        // Replaces the parser, so it comes before setTokenFilter.
        parser.setParsingCacheStrategy(ASTParser.ParsingCacheStrategy.NONE);
        parser.setTokenFilter(filter);
        return parser;
    }

    /**
     * Runs {@code build}, code that parses a snippet with {@link #t} or builds nodes into {@link #root} (an Iris idiom
     * such as {@code tree.parseAndInjectNode(t, ...)} or {@code new Identifier(...)}), under {@link #BUILD_LOCK} and with
     * the lexer at this program's version: {@link #t} is shared by every program, so another program may have been
     * parsed at another version since. Every verb of this class that parses goes through here.
     */
    public <N> N build(Supplier<N> build) {
        BUILD_LOCK.lock();
        try {
            t.getLexer().version = lexerVersion;
            return build.get();
        } finally {
            BUILD_LOCK.unlock();
        }
    }

    /**
     * The directives {@link #parse} dropped, as {@code line N: #define} (filtered), the {@code #pragma} text, or an
     * {@code #extension} line after the leading directives with the suffix {@code (after the leading directives)}.
     */
    public List<String> droppedDirectives() {
        return droppedDirectives;
    }

    /**
     * The {@code #extension} directives of the program's leading directive block, in document order, one line each, as
     * TauMC's token-spaced printer wrote them: {@code #extension GL_ARB_shader_texture_lod : enable}. These are the
     * extension lines the TauMC engine put in its header. The leading block is the run of directive lines at the very
     * start of the source, up to the first blank line, comment, indented line or code ({@link #leadingExtensionCount}).
     *
     * <p>An {@code #extension} after that block is not returned: TauMC's pre-parser never read it and its parser
     * ignores directives, so the TauMC engine dropped it, and so does this class ({@link #parse} lists it in
     * {@link #droppedDirectives()} and logs it; {@link #print(String)} removes it from the body). glsl-transformer
     * parses {@code #extension} anywhere at the top level, so without this rule such a line would move into the header,
     * where a {@code require} of an extension the driver lacks fails a program TauMC's output compiled.</p>
     *
     * <p>{@link #print(String)} removes the directives from the tree, so the orchestrator reads them first and writes
     * them into its header.</p>
     */
    public List<String> extensionDirectives() {
        final List<String> lines = new ArrayList<>();
        for (ExternalDeclaration declaration : tree.getChildren()) {
            if (declaration instanceof ExtensionDirective extension && lines.size() < leadingExtensions) {
                lines.add(extensionLine(extension));
            }
        }
        return lines;
    }

    private static String extensionLine(ExtensionDirective extension) {
        String line = "#extension " + extension.getName();
        if (extension.behavior != null) {
            // By token: the enum constant for 'require' is named DEBUG in 3.0.0-pre3.
            final String literal = GLSLLexer.VOCABULARY.getLiteralName(extension.behavior.tokenType);
            line += " : " + (literal != null ? literal.substring(1, literal.length() - 1)
                : GLSLLexer.VOCABULARY.getSymbolicName(extension.behavior.tokenType));
        }
        return line;
    }

    /**
     * Prints the program under {@code header}, as {@code GlslTransformUtils.getFormattedShader(tree, header)} did:
     * the header, a newline, then the body. The body is printed with {@link PrintType#INDENTED} after the version
     * statement and every {@code #extension} directive are removed from {@link #tree} (the orchestrator writes its
     * own {@code #version} and extension lines into the header). Read them before printing if they are needed.
     */
    public String print(String header) {
        final VersionStatement version = tree.getVersionStatement();
        if (version != null) {
            version.detachAndDelete();
        }
        for (ExternalDeclaration declaration : new ArrayList<>(tree.getChildren())) {
            if (declaration instanceof ExtensionDirective) {
                declaration.detachAndDelete();
            }
        }
        return header + "\n" + ASTPrinter.print(PrintType.INDENTED, tree);
    }

    /** {@link #print(String)} with an empty header, for tests. */
    public String printBody() {
        return print("");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Injection

    /**
     * TauMC {@code injectVariable}: parses {@code code} as one external declaration and inserts it before the
     * variable anchor. An injected declaration that holds a storage qualifier ({@code const}, {@code in},
     * {@code out}, {@code uniform}, {@code attribute}, {@code varying}, {@code buffer}, ...; layout, precision,
     * interpolation and invariant qualifiers do not count) becomes the new anchor, so qualified injections appear in
     * reverse order, and an injected function definition (here or through {@link #injectFunction}) becomes both
     * anchors.
     *
     * <p>The first {@code injectVariable} fixes the anchor as TauMC did: take the first storage qualifier anywhere in
     * the program (a global, a parameter, a local {@code const}, the {@code in} of {@code layout(...) in;}) in TauMC's
     * cache order (the class javadoc: the parsed program first, then what verbs added, so a declaration that
     * {@link #injectFunction} inserted counts only after all of the program's own); if the external declaration it
     * sits in comes before the first function definition (same order), that declaration is the anchor, otherwise the
     * first function is. In a program whose qualified declarations all come first, that is the first qualified
     * declaration; in one whose first qualifier sits inside or after the first function, it is that function. With no
     * qualifier and no function nothing is inserted, as in TauMC.</p>
     *
     * <p>Deviation: if the anchor has been removed from the tree, it is fixed again the same way; TauMC throws
     * {@code IndexOutOfBoundsException}.</p>
     */
    public void injectVariable(String code) {
        final ExternalDeclaration anchor = variableAnchor();
        if (anchor == null) {
            LOGGER.debug("[ShaderAst] injectVariable: no qualified declaration and no function to anchor on; dropped {}",
                code);
            return;
        }
        final ExternalDeclaration insert = added(build(() -> t.parseExternalDeclaration(root, code)));
        tree.getChildren().add(tree.getChildren().indexOf(anchor), insert);
        updateAnchors(insert, false);
    }

    /**
     * TauMC {@code injectFunction}: parses {@code code} as one external declaration (a function definition, or any
     * declaration that must follow the variables, such as a struct or a global initialized from uniforms) and
     * inserts it before the function anchor. The anchor starts as the first function definition; an injected
     * function definition becomes both anchors, so injected functions appear in reverse order and later
     * {@link #injectVariable} calls insert before the last injected function.
     *
     * <p>The first function definition is taken in TauMC's cache order (the class javadoc), which differs from the
     * document's only after {@link #replaceFunctionDefinition} replaced the first function before any injection.</p>
     *
     * <p>Deviation: with no function definition in the program the declaration is appended at the end, where TauMC
     * throws {@code IndexOutOfBoundsException}; a removed anchor is recomputed.</p>
     */
    public void injectFunction(String code) {
        final ExternalDeclaration anchor = functionAnchor();
        final ExternalDeclaration insert = added(build(() -> t.parseExternalDeclaration(root, code)));
        final int index = anchor == null ? tree.getChildren().size() : tree.getChildren().indexOf(anchor);
        tree.getChildren().add(index, insert);
        updateAnchors(insert, true);
    }

    // TauMC's Transformer.injectVariable when its anchor is unset (see injectVariable's javadoc).
    private ExternalDeclaration variableAnchor() {
        if (variableAnchor != null && variableAnchor.getParent() == tree) {
            return variableAnchor;
        }
        final ExternalDeclaration function = firstFunction();
        variableAnchor = function;
        // A LayoutDefaults (layout(local_size_x = 8) in;) holds TauMC's storage qualifier implicitly: glsl-transformer
        // keeps its in/out/uniform as a LayoutMode, not as a StorageQualifier node.
        final List<Found<ASTNode>> qualifiers = inTauMCOrder(ASTNode.class,
            node -> node instanceof StorageQualifier || node instanceof LayoutDefaults);
        if (!qualifiers.isEmpty()) {
            final ExternalDeclaration owner = qualifiers.get(0).top();
            if (owner != null && (function == null
                || tree.getChildren().indexOf(owner) < tree.getChildren().indexOf(function))) {
                variableAnchor = owner;
            }
        }
        return variableAnchor;
    }

    private ExternalDeclaration functionAnchor() {
        if (functionAnchor != null && functionAnchor.getParent() == tree) {
            return functionAnchor;
        }
        functionAnchor = firstFunction();
        return functionAnchor;
    }

    // The first function definition in TauMC's cache order.
    private FunctionDefinition firstFunction() {
        final List<Found<FunctionDefinition>> functions = inTauMCOrder(FunctionDefinition.class, definition -> true);
        return functions.isEmpty() ? null : functions.get(0).node();
    }

    // TauMC's InjectorPoint, walked over the inserted node.
    private void updateAnchors(ExternalDeclaration insert, boolean functionMode) {
        if (insert instanceof FunctionDefinition) {
            functionAnchor = insert;
            variableAnchor = insert;
        } else if (!functionMode && (insert instanceof LayoutDefaults || holdsStorageQualifier(insert))) {
            variableAnchor = insert;
        }
    }

    /**
     * Whether a storage qualifier occurs anywhere in {@code node}. This walks down the subtree rather than up from the
     * indexed qualifiers: in 3.0.0-pre3 a {@code VariableDeclaration} ({@code layout(local_size_x = 8) in;},
     * {@code invariant gl_Position;}) does not set itself as its {@code TypeQualifier}'s parent, so a walk up from its
     * qualifier stops at the qualifier.
     */
    private static boolean holdsStorageQualifier(ASTNode node) {
        final boolean[] found = {false};
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode visited) {
                if (visited instanceof StorageQualifier) {
                    found[0] = true;
                }
            }
        }.visit(node);
        return found[0];
    }

    /**
     * Whether {@code node} is still in {@link #tree}: its parents lead to the tree, or to the parentless
     * {@code TypeQualifier} of a {@code VariableDeclaration} (see {@link #holdsStorageQualifier}). A node inside a
     * replaced or removed subtree leads to that subtree's detached top instead.
     */
    private boolean isAttached(ASTNode node) {
        ASTNode current = node;
        while (true) {
            final ASTNode parent = current.getParent();
            if (parent == tree) {
                return true;
            }
            if (parent == null) {
                return current instanceof TypeQualifier;
            }
            current = parent;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // TauMC's cache order

    /** Records {@code node} as a subtree a verb added: TauMC's scanNode appended it to its rule-context cache. */
    private <N extends ASTNode> N added(N node) {
        additions.put(node, ++additionCount);
        return node;
    }

    /** A node found by {@link #inTauMCOrder}, with the external declaration it sits in. */
    private record Found<N extends ASTNode>(N node, ExternalDeclaration top) {
    }

    /**
     * The nodes of {@code type} that pass {@code filter}, in TauMC's cache order: the program as parsed in document
     * order, then each subtree a verb added, in the order added (a subtree added inside an earlier addition, such as a
     * replacement inside a prepended statement, counts as the later addition). The walk goes down from {@link #tree},
     * so it also reaches the parentless {@code TypeQualifier} of a {@code VariableDeclaration}.
     */
    private <N extends ASTNode> List<Found<N>> inTauMCOrder(Class<N> type, Predicate<? super N> filter) {
        final List<Found<N>> found = new ArrayList<>();
        final List<Integer> epochs = new ArrayList<>();
        new ASTVoidVisitor() {
            private int epoch;
            private ExternalDeclaration top;

            @Override
            public Void visit(ASTNode node) {
                final int outer = epoch;
                final Integer addition = additions.get(node);
                if (addition != null) {
                    epoch = addition;
                }
                if (node instanceof ExternalDeclaration declaration && node.getParent() == tree) {
                    top = declaration;
                }
                if (type.isInstance(node) && filter.test(type.cast(node))) {
                    found.add(new Found<>(type.cast(node), top));
                    epochs.add(epoch);
                }
                node.accept(this);
                epoch = outer;
                return null;
            }
        }.visit(tree);
        if (additionCount == 0 || found.size() < 2) {
            return found;
        }
        // Document order is already the order within each addition; a stable sort by addition gives TauMC's order.
        final List<Integer> indices = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            indices.add(i);
        }
        indices.sort(Comparator.comparingInt(epochs::get));
        final List<Found<N>> ordered = new ArrayList<>(found.size());
        for (int index : indices) {
            ordered.add(found.get(index));
        }
        return ordered;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Renaming

    /**
     * TauMC {@code rename(String, String)}: {@link #rename(Map)} with one entry.
     */
    public void rename(String oldName, String newName) {
        rename(Collections.singletonMap(oldName, newName));
    }

    /**
     * TauMC {@code rename(Map)}: renames, all at once (no chaining through the map), every identifier whose name is a
     * key and that is a variable declarator's name ({@link DeclarationMember}, global or local, including a
     * {@code for} initializer), a reference in an expression ({@link ReferenceExpression}), a called function's
     * name ({@link FunctionCallExpression}), a member selection after a dot ({@link MemberAccessExpression}: struct
     * fields and swizzles), or a function prototype's name (definitions and declarations). Like TauMC, it leaves
     * alone function parameter names, struct and interface block member declarations, struct and block names, type
     * names, layout qualifier names and the name declared in a loop condition.
     *
     * <p>Deviations, none reachable from Demonica's arguments: a struct name used as the element type of an array
     * constructor ({@code S[2](a, b)}) is a type name here and is left alone, where TauMC renamed it (a struct
     * constructor call {@code S(x)} is renamed by both, the struct's declaration by neither); the {@code length} of
     * {@code arr.length()} is not an identifier here, where TauMC renamed it; {@code texture2D} and
     * {@code texture3D} are renamed here, where TauMC's lexer made them keywords that only
     * {@link #renameFunctionCall} touched.</p>
     */
    public void rename(Map<String, String> names) {
        renameWhere(names, ShaderAst::isRenameTarget);
    }

    /**
     * TauMC {@code renameFunctionCall(String, String)}: {@link #renameFunctionCall(Map)} with one entry.
     */
    public void renameFunctionCall(String oldName, String newName) {
        renameFunctionCall(Collections.singletonMap(oldName, newName));
    }

    /**
     * TauMC {@code renameFunctionCall(Map)}: {@link #rename(Map)} without the variable declarators. Despite the name
     * it renames every expression identifier (references, calls and member selections) and function prototype
     * names, not only calls. TauMC also renamed the {@code texture2D} and {@code texture3D} keywords of its own
     * lexer; glsl-transformer lexes them as identifiers, so they are ordinary call names here. The struct-name and
     * {@code length()} deviations of {@link #rename(Map)} apply.
     */
    public void renameFunctionCall(Map<String, String> names) {
        renameWhere(names, id -> isRenameTarget(id) && !(id.getParent() instanceof DeclarationMember));
    }

    private void renameWhere(Map<String, String> names, Predicate<Identifier> filter) {
        final List<Identifier> targets = new ArrayList<>();
        for (String oldName : names.keySet()) {
            for (Identifier identifier : root.identifierIndex.get(oldName)) {
                if (filter.test(identifier)) {
                    targets.add(identifier);
                }
            }
        }
        for (Identifier identifier : targets) {
            identifier.setName(names.get(identifier.getName()));
        }
    }

    // The identifiers TauMC's rename touches: typeless_declaration, variable_identifier and function_prototype.
    private static boolean isRenameTarget(Identifier identifier) {
        final ASTNode parent = identifier.getParent();
        return parent instanceof DeclarationMember || parent instanceof FunctionPrototype || isExpressionIdentifier(identifier);
    }

    // TauMC's variable_identifier: an identifier used in an expression, as a reference, a call name or a member.
    private static boolean isExpressionIdentifier(Identifier identifier) {
        final ASTNode parent = identifier.getParent();
        return parent instanceof ReferenceExpression || parent instanceof FunctionCallExpression
            || parent instanceof MemberAccessExpression;
    }

    /**
     * TauMC {@code renameArray(String, String, Set)}: rewrites every array access {@code oldName[i]} whose base is the
     * bare name and whose index is an integer literal into the identifier {@code newName + i}, and adds each
     * {@code i} to {@code found}. Accesses of anything else are left alone.
     *
     * <p>TauMC reads the index with {@code Integer.parseInt} of its text, so an index that is not a decimal
     * {@code int} literal (a variable, an expression, {@code 0u}, {@code 0x1}) makes it throw
     * {@link NumberFormatException}; this does the same. Deviations: an octal literal such as {@code 07} gives
     * {@code newName + "7"}, where TauMC wrote {@code newName + "07"} but recorded 7; a negative index ({@code [-1]})
     * throws, where TauMC wrote the identifier {@code newName + "-1"}; an index with a unary plus ({@code [+1]})
     * throws, where TauMC recorded 1 and wrote the token {@code newName + "+1"}, which reads as an addition.</p>
     */
    public void renameArray(String oldName, String newName, Set<Integer> found) {
        for (Identifier identifier : new ArrayList<>(root.identifierIndex.get(oldName))) {
            if (!(identifier.getParent() instanceof ReferenceExpression reference)
                || !(reference.getParent() instanceof ArrayAccessExpression access)
                || access.getLeft() != reference) {
                continue;
            }
            final Expression index = access.getRight();
            if (!(index instanceof LiteralExpression literal) || !literal.isInteger() || literal.getType() != Type.INT32
                || literal.getIntegerFormat() == LiteralExpression.IntegerFormat.HEXADECIMAL
                || literal.getInteger() > Integer.MAX_VALUE) {
                throw new NumberFormatException("For input string: \"" + ASTPrinter.print(PrintType.COMPACT, index).trim()
                    + "\" (renameArray " + oldName + ": the index is not a decimal int literal)");
            }
            final int value = (int) literal.getInteger();
            found.add(value);
            final Expression replacement = build(() -> t.parseExpression(root, newName + value));
            // TauMC renamed the token in place, so the name keeps the cache position of the access it replaces.
            final Integer addition = additions.remove(access);
            if (addition != null) {
                additions.put(replacement, addition);
            }
            access.replaceByAndDelete(replacement);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Expressions and main

    /**
     * TauMC {@code replaceExpression(String, String)}: replaces every occurrence of the expression {@code oldCode}
     * with a new parse of {@code newCode}.
     *
     * <p>An identifier {@code oldCode} matches every {@link ReferenceExpression} of that name (in TauMC: every binary
     * or postfix expression whose text is the name), including assignment targets, not declarations or member
     * selections. When {@code newCode} is an identifier too, calls of a function named {@code oldCode} are renamed
     * as well, as TauMC's postfix pass did.</p>
     *
     * <p>Any other {@code oldCode} matches every expression node that is structurally equal to its parse: the same
     * tree of node classes, the same identifiers and operators, the same number of arguments at every level, literals
     * equal by value (see the private {@code structure(ASTNode)}; glsl-transformer's own {@code Matcher} is not used because it accepts
     * {@code f(a)} for the pattern {@code f(a, b)}). TauMC compared the source text of binary and postfix
     * expressions. Occurrences are collected before anything is replaced, so a replacement is never searched.</p>
     *
     * <p>Deviations, all where TauMC produced wrong code or depended on spelling: literals match by value
     * ({@code 0.0}, {@code 0.} and {@code 0.0f} are one literal; TauMC matched the spelling); a replacement is
     * parenthesized by the printer where the context binds tighter (TauMC printed {@code a + b * c} for
     * {@code x * c} with {@code x} replaced by {@code a + b}); a replacement in postfix or unary position (an
     * assignment target, {@code old.xy}, {@code old[0]}, {@code -old}) is kept whole, where TauMC reparsed it as a
     * postfix expression and dropped everything after the first operator ({@code -x} with {@code x} replaced by
     * {@code a + b} gave {@code -a}); a conditional replacement is kept whole, where TauMC's binary pass reparsed it
     * as a binary expression and dropped the {@code ? :} part ({@code c} replaced by {@code u > 0.0 ? 1.0 : 2.0}
     * in {@code x > c} gave {@code x > u > 0.0}); a replacement that contains the pattern is not searched again,
     * where TauMC's postfix pass replaced what its binary pass had inserted ({@code f(v)} replaced by
     * {@code f(f(v))} in {@code f(f(v))} gave {@code f(f(f(f(v))))}); identifiers renamed by an earlier
     * {@link #rename}, {@link #renameFunctionCall} or {@link #renameArray} match under their new name, where TauMC
     * looked nodes up in a by-text cache that those verbs do not update, built at its first {@code replaceExpression},
     * so it missed them under their new name and still found them under the old one; a pattern that is itself a
     * binary expression ({@code colorSample * mult}) replaces only its matches, where TauMC's postfix pass also cut
     * the pattern to its first operand and replaced every remaining occurrence of that operand
     * ({@code colorSample}); a call of {@code oldCode} is left alone when {@code newCode} is not an identifier
     * (TauMC wrote {@code newCode(args)}). Demonica's patterns are names, array and member accesses and calls, so
     * none reaches the last two; the rename case needs a {@code CELERITAS_TERRAIN} vertex shader that declares
     * {@code gl_MultiTexCoord3} ({@code patchMultiTexCoord3} renames it to {@code mc_midTexCoord}, which
     * {@code replaceMidTexCoord} then replaces).</p>
     */
    public void replaceExpression(String oldCode, String newCode) {
        final Expression pattern = build(() -> t.parseExpression(patternRoot(), oldCode));
        if (pattern instanceof ReferenceExpression reference) {
            final String name = reference.getIdentifier().getName();
            final String trimmed = newCode.trim();
            final boolean newIsIdentifier = IDENTIFIER.matcher(trimmed).matches();
            for (Identifier identifier : new ArrayList<>(root.identifierIndex.get(name))) {
                if (identifier.getParent() instanceof ReferenceExpression target) {
                    target.replaceByAndDelete(added(build(() -> t.parseExpression(root, newCode))));
                } else if (newIsIdentifier && identifier.getParent() instanceof FunctionCallExpression) {
                    identifier.setName(trimmed);
                }
            }
            return;
        }

        final List<Object> shape = structure(pattern);
        final Class<? extends Expression> patternClass = pattern.getClass();
        final Set<Expression> matches = Collections.newSetFromMap(new IdentityHashMap<>());
        final String hint = longestIdentifier(pattern);
        if (hint != null) {
            for (Identifier identifier : root.identifierIndex.get(hint)) {
                // Every ancestor of the pattern's class, not only the nearest: the hint can sit in a nested node of
                // that class (the pattern f(g(x)) with the hint x), or below a non-expression node of the pattern (the
                // array size N of mat4[N](...)).
                for (ASTNode node = identifier.getParent(); node != null && node != tree; node = node.getParent()) {
                    if (node.getClass() == patternClass && structure(node).equals(shape)) {
                        matches.add((Expression) node);
                    }
                }
            }
        } else {
            for (Expression node : root.nodeIndex.get(patternClass)) {
                if (structure(node).equals(shape)) {
                    matches.add(node);
                }
            }
        }
        for (Expression match : matches) {
            // A match inside an earlier replaced match is already gone with it.
            if (isAttached(match)) {
                match.replaceByAndDelete(added(build(() -> t.parseExpression(root, newCode))));
            }
        }
    }

    private static final Object STRUCTURE_END = new Object();

    /**
     * The node's structure: its pre-order sequence of node classes (an operator is its node's class) and data
     * (identifier names; literal types, values and integer formats; type and qualifier enums), with an end marker
     * after each node's children. Two expressions are structurally equal when their structures are equal.
     *
     * <p>glsl-transformer 3.0.0-pre3's {@code Matcher} compares the same sequence without the end markers and does
     * not check that the whole pattern was consumed, so it accepts {@code f(a)} for the pattern {@code f(a, b)} (a
     * prefix) and {@code f(g(a), b)} for {@code f(g(a, b))} (the same items, another nesting), in both directions.</p>
     */
    private static List<Object> structure(ASTNode node) {
        final List<Object> items = new ArrayList<>();
        new ASTVoidVisitor() {
            @Override
            public Void visit(ASTNode visited) {
                items.add(visited.getClass());
                visited.accept(this);
                items.add(STRUCTURE_END);
                return null;
            }

            @Override
            public void visitVoidData(Object data) {
                items.add(data);
            }
        }.startVisit(node);
        return items;
    }

    private static Root patternRoot() {
        // Never attached: a pattern parsed into this program's root would register its identifiers in the index.
        return RootSupplier.EXACT_UNORDERED.get();
    }

    private static String longestIdentifier(ASTNode node) {
        final String[] longest = {null};
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode visited) {
                if (visited instanceof Identifier identifier
                    && (longest[0] == null || identifier.getName().length() > longest[0].length())) {
                    longest[0] = identifier.getName();
                }
            }
        }.visit(node);
        return longest[0];
    }

    /**
     * TauMC {@code prependMain}: parses {@code code} as one statement and inserts it as the first statement of every
     * {@code main} definition. Without a {@code main} definition nothing happens.
     *
     * <p>Deviation: an empty {@code main} body gets the statement; TauMC threw {@code NullPointerException}.</p>
     */
    public void prependMain(String code) {
        for (FunctionDefinition main : mainDefinitions()) {
            main.getBody().getStatements().add(0, added(build(() -> t.parseStatement(root, code))));
        }
    }

    /**
     * TauMC {@code appendMain}: parses {@code code} as one statement and appends it as the last statement of every
     * {@code main} definition (after a trailing {@code return}, if any). Without a {@code main} definition nothing
     * happens.
     *
     * <p>Deviation: an empty {@code main} body gets the statement; TauMC threw {@code NullPointerException}.</p>
     */
    public void appendMain(String code) {
        for (FunctionDefinition main : mainDefinitions()) {
            main.getBody().getStatements().add(added(build(() -> t.parseStatement(root, code))));
        }
    }

    private List<FunctionDefinition> mainDefinitions() {
        final List<FunctionDefinition> mains = new ArrayList<>();
        for (Identifier identifier : root.identifierIndex.get("main")) {
            if (identifier.getParent() instanceof FunctionPrototype prototype
                && prototype.getParent() instanceof FunctionDefinition definition) {
                mains.add(definition);
            }
        }
        return mains;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Declarations

    /**
     * TauMC {@code removeVariable}: removes a variable declarator named {@code name}. The declarators (global and
     * local, not parameters or struct members) are scanned in TauMC's cache order (the class javadoc: the parsed
     * program in document order, then what verbs added, in the order added): the first one that shares its
     * declaration with other declarators ({@code float a, name;}) is removed alone and the scan stops; a declarator
     * that is alone in its declaration is remembered and the scan goes on, so the last such one is removed, together
     * with its whole declaration (global or local statement). Nothing happens if no declarator has the name. So after
     * {@code injectVariable("uniform float w;")} in a program with a local {@code vec2 w}, the injected uniform is
     * the last scanned and goes, as in TauMC, although the local comes later in the document.
     *
     * <p>Deviations: removing the first declarator of {@code float a = 1.0, b;} gives {@code float b;}; TauMC wrote the
     * next declarator's text into the first one's name and kept the first one's array size and initializer
     * ({@code float b = 1.0;}). A variable declared alone in a {@code for} initializer
     * ({@code for (int i = 0; i < n; i++)}) leaves the loop with an empty initializer ({@code for (; i < n; i++)});
     * TauMC dropped the declaration with its semicolon ({@code for (i < n; i++)}, not GLSL). Either way {@code i} is
     * no longer declared. A declaration that is the unbraced body of an {@code if}, {@code else} or loop
     * ({@code if (c) float x = 1.0;}) is replaced by an empty statement ({@code if (c) ;}); TauMC removed it and
     * left the {@code if} without a body, so the next statement became the body and the program's meaning
     * changed.</p>
     */
    public void removeVariable(String name) {
        DeclarationMember target = null;
        boolean shared = false;
        for (DeclarationMember member : declaratorsInTauMCOrder(name)) {
            target = member;
            if (((TypeAndInitDeclaration) member.getParent()).getMembers().size() > 1) {
                shared = true;
                break;
            }
        }
        if (target == null) {
            return;
        }
        if (shared) {
            target.detachAndDelete();
            return;
        }
        final ASTNode declaration = target.getParent();
        final ASTNode holder = declaration.getParent();
        if (holder instanceof ForLoopStatement) {
            // A for initializer: the loop keeps an empty initializer.
            declaration.detachAndDelete();
        } else if (holder instanceof DeclarationStatement statement && !(statement.getParent() instanceof CompoundStatement)) {
            // The unbraced body of an if, else or loop: a field of its parent, which must not become null.
            statement.replaceByAndDelete(build(() -> t.parseStatement(root, ";")));
        } else {
            // The TypeAndInitDeclaration's DeclarationExternalDeclaration or DeclarationStatement.
            holder.detachAndDelete();
        }
    }

    /**
     * TauMC {@code findType}: the type named by the first declaration, in TauMC's cache order (the class javadoc: the
     * parsed program in document order, then what verbs added), that declares a variable {@code name}, a global or
     * local declarator, not a parameter or a struct member. TauMC returned the type keyword's lexer token, 0 when
     * nothing matched; this returns a {@link DeclaredType}, or null when nothing matched. Like TauMC it skips a
     * declaration whose type is a struct and looks further. An array declaration reports its element type. After
     * {@code injectVariable("uniform float w;")} in a program with a local {@code vec2 w}, this reports {@code vec2},
     * as TauMC did: the injected uniform comes first in the document but last in the cache.
     */
    public DeclaredType findType(String name) {
        for (DeclarationMember member : declaratorsInTauMCOrder(name)) {
            final TypeSpecifier specifier = ((TypeAndInitDeclaration) member.getParent()).getType().getTypeSpecifier();
            if (specifier instanceof BuiltinNumericTypeSpecifier numeric) {
                return new DeclaredType.Numeric(numeric.type);
            }
            if (specifier instanceof BuiltinFixedTypeSpecifier fixed) {
                return new DeclaredType.Fixed(fixed.type);
            }
        }
        return null;
    }

    /**
     * TauMC {@code hasVariable}: whether a variable declarator (global or local) or a function prototype (definition
     * or declaration) has the name. Parameters, struct and block members and interface blocks do not count.
     */
    public boolean hasVariable(String name) {
        for (Identifier identifier : root.identifierIndex.get(name)) {
            final ASTNode parent = identifier.getParent();
            if (parent instanceof DeclarationMember || parent instanceof FunctionPrototype) {
                return true;
            }
        }
        return false;
    }

    /**
     * TauMC {@code containsCall}: whether the name is used in an expression, as a reference, a call name or a member
     * selection. Despite the name, any use counts ({@code containsCall("gl_FragColor")} asks whether the shader
     * writes or reads {@code gl_FragColor}); declarations do not.
     *
     * <p>Deviations: the {@code length} of {@code arr.length()} is not an identifier in glsl-transformer (a
     * {@code LengthAccessExpression}), so {@code containsCall("length")} is false for a program whose only
     * {@code length} is that method, where TauMC, whose grammar made it a {@code variable_identifier}, said true (the
     * same gap as in {@link #rename(Map)}); {@code texture2D} and {@code texture3D} are identifiers here and keywords
     * in TauMC, so this finds their calls and TauMC never did. No Demonica caller asks about either name.</p>
     */
    public boolean containsCall(String name) {
        for (Identifier identifier : root.identifierIndex.get(name)) {
            if (isExpressionIdentifier(identifier)) {
                return true;
            }
        }
        return false;
    }

    private List<DeclarationMember> declaratorsInTauMCOrder(String name) {
        final List<DeclarationMember> members = new ArrayList<>();
        for (Identifier identifier : root.identifierIndex.get(name)) {
            if (identifier.getParent() instanceof DeclarationMember member && member.getParent() instanceof TypeAndInitDeclaration) {
                members.add(member);
            }
        }
        if (members.size() < 2) {
            return members;
        }
        final List<DeclarationMember> ordered = new ArrayList<>(members.size());
        for (Found<DeclarationMember> found : inTauMCOrder(DeclarationMember.class,
            member -> member.getParent() instanceof TypeAndInitDeclaration && member.getName().getName().equals(name))) {
            ordered.add(found.node());
        }
        return ordered;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Shadow sampling and functions

    /**
     * TauMC {@code renameAndWrapShadow}: wraps every call of {@code oldName} that has arguments in {@code vec4(...)},
     * then {@link #renameFunctionCall(String, String) renames} {@code oldName} to {@code newName} wherever that verb
     * does (calls, references, member selections, function prototypes). This turns the legacy {@code shadow2D}
     * family, which returned a {@code vec4}, into {@code texture} and its relatives, which return a {@code float} for
     * a shadow sampler, so that {@code shadow2D(s, p).r} becomes {@code vec4(texture(s, p)).r} and still compiles.
     * A call in any position is wrapped: a whole initializer, an operand, the argument of another function or of a
     * constructor. A call without arguments is left alone.
     *
     * <p>Only the outermost call of a nest of {@code oldName} calls is wrapped: in
     * {@code shadow2D(s, vec3(shadow2D(t, p).r))} the inner call is renamed but not wrapped, as in TauMC, which
     * replaced the outer call by a fresh parse of its text before it reached the inner one. The wrapper and the call
     * inside it are a new parse here too (the call's printed text in {@code vec4(...)}), which counts as an addition
     * for the verbs that follow (the class javadoc). The calls are wrapped, and so recorded as additions, in TauMC's
     * cache order: the program's own calls in document order, then the calls inside earlier additions in the order
     * those were added. {@link #removeConstAssignment} walks identifiers in that order, so it sees the difference when
     * a second {@code renameAndWrapShadow} wraps a call that sits inside a first one's wrapper.</p>
     *
     * <p>Deviation: TauMC built the wrapper from the call's parse-tree text ({@code getText()}, the tokens joined
     * without whitespace), so an argument {@code p.z - -0.001} became {@code p.z--0.001}, which lexes as a decrement
     * and is not valid GLSL; here the call is printed from the AST and keeps {@code p.z - -0.001} (test
     * {@code deviationWrappedShadowCallKeepsANegatedLiteral}).</p>
     */
    public void renameAndWrapShadow(String oldName, String newName) {
        final List<FunctionCallExpression> calls = new ArrayList<>();
        for (Identifier identifier : root.identifierIndex.get(oldName)) {
            if (identifier.getParent() instanceof FunctionCallExpression call && call.getFunctionName() == identifier
                && !call.getParameters().isEmpty()) {
                calls.add(call);
            }
        }
        if (calls.size() > 1) {
            // TauMC's cache order: an outer call before the calls in its arguments, and the program's own calls
            // before the calls inside what verbs added (an earlier renameAndWrapShadow's wrappers among them).
            final Set<FunctionCallExpression> selected = Collections.newSetFromMap(new IdentityHashMap<>());
            selected.addAll(calls);
            calls.clear();
            for (Found<FunctionCallExpression> found : inTauMCOrder(FunctionCallExpression.class, selected::contains)) {
                calls.add(found.node());
            }
        }
        for (FunctionCallExpression call : calls) {
            // A call inside an outer call that was wrapped went with it.
            if (isAttached(call)) {
                final String wrapped = "vec4(" + text(call) + ")";
                call.replaceByAndDelete(added(build(() -> t.parseExpression(root, wrapped))));
            }
        }
        renameFunctionCall(oldName, newName);
    }

    /**
     * TauMC {@code removeUnusedFunctions}: removes every function definition and function declaration (prototype) at
     * file scope whose name is not used in any expression of the program, as {@link #containsCall} counts uses
     * (calls, references, member selections), except {@code main}; then again, until nothing more goes, so a helper
     * that only removed helpers called goes too. A name counts as used wherever it occurs, also as another function's
     * local variable or inside the function's own body. All overloads of a name go or stay together.
     *
     * <p>Deviations: TauMC collected the prototype names inside function bodies too (local prototypes, legal in GLSL
     * 1.10) but could not remove them, and looped forever when such a name was unused; here only file-scope
     * declarations count and the loop ends when a pass removes nothing. {@code arr.length()} does not count as a use
     * of a function named {@code length} (see {@link #containsCall}). Like TauMC this leaves an injection anchor that
     * it removed behind; the next injection fixes a new one (see {@link #injectVariable}).</p>
     */
    public void removeUnusedFunctions() {
        boolean removed = true;
        while (removed) {
            removed = false;
            for (ExternalDeclaration declaration : new ArrayList<>(tree.getChildren())) {
                final FunctionPrototype prototype = prototypeOf(declaration);
                if (prototype == null) {
                    continue;
                }
                final String name = prototype.getName().getName();
                if (!name.equals("main") && !containsCall(name)) {
                    declaration.detachAndDelete();
                    removed = true;
                }
            }
        }
    }

    private static FunctionPrototype prototypeOf(ExternalDeclaration declaration) {
        if (declaration instanceof FunctionDefinition definition) {
            return definition.getFunctionPrototype();
        }
        if (declaration instanceof DeclarationExternalDeclaration external
            && external.getDeclaration() instanceof FunctionDeclaration functionDeclaration) {
            return functionDeclaration.getFunctionPrototype();
        }
        return null;
    }

    /**
     * TauMC {@code removeConstAssignment}: a {@code const} parameter is not a constant expression, so a {@code const}
     * local initialized from one does not compile on strict drivers. For every function with parameters whose
     * <em>first</em> qualifier is {@code const} ({@code const in float x}, not {@code in const float x}), this walks
     * the program's expression identifiers once, in TauMC's cache order (the class javadoc): an identifier that names
     * one of the function's const parameters, or a variable already found, inside a function of that name (every
     * overload), and inside the type or the first declarator of a declaration ({@code float y = x * 2.0;}, not the
     * {@code b} of {@code float a = 1.0, b = x;}) adds that declaration's first declarator to the found names, and if
     * the declaration's first qualifier is {@code const}, the declaration loses its whole type qualifier
     * ({@code const highp float y} becomes {@code float y}). So the removal follows chains forward through the
     * function ({@code float w = x; const float z = w;} loses the {@code const} of {@code z}), whether the
     * declarations in between were {@code const} or not.
     *
     * <p>Deviations, all where TauMC threw {@code NullPointerException}: an unnamed {@code const} parameter
     * (a prototype's {@code const float}) and a declaration without declarators are skipped.</p>
     */
    public void removeConstAssignment() {
        final Map<String, List<String>> functions = new LinkedHashMap<>();
        for (Found<FunctionParameter> found : inTauMCOrder(FunctionParameter.class,
            parameter -> startsWithConst(parameter.getType().getTypeQualifier()) && parameter.getName() != null
                && parameter.getParent() instanceof FunctionPrototype)) {
            final FunctionParameter parameter = found.node();
            final String function = ((FunctionPrototype) parameter.getParent()).getName().getName();
            functions.computeIfAbsent(function, f -> new ArrayList<>()).add(parameter.getName().getName());
        }
        if (functions.isEmpty()) {
            return;
        }
        final List<Found<Identifier>> identifiers = inTauMCOrder(Identifier.class, ShaderAst::isExpressionIdentifier);
        for (Map.Entry<String, List<String>> entry : functions.entrySet()) {
            final List<String> names = entry.getValue();
            for (Found<Identifier> found : identifiers) {
                final Identifier identifier = found.node();
                if (!names.contains(identifier.getName())) {
                    continue;
                }
                final FunctionDefinition definition = identifier.getAncestor(FunctionDefinition.class);
                if (definition == null || !definition.getFunctionPrototype().getName().getName().equals(entry.getKey())) {
                    continue;
                }
                final TypeAndInitDeclaration declaration = singleDeclarationOf(identifier);
                if (declaration == null) {
                    continue;
                }
                names.add(declaration.getMembers().get(0).getName().getName());
                final TypeQualifier qualifier = declaration.getType().getTypeQualifier();
                if (startsWithConst(qualifier)) {
                    qualifier.detachAndDelete();
                }
            }
        }
    }

    private static boolean startsWithConst(TypeQualifier qualifier) {
        return qualifier != null && !qualifier.getParts().isEmpty()
            && qualifier.getParts().get(0) instanceof StorageQualifier storage
            && storage.storageType == StorageQualifier.StorageType.CONST;
    }

    /**
     * TauMC's {@code single_declaration} around {@code node}: the declaration whose type or first declarator holds it.
     * TauMC's grammar puts the second and later declarators outside that rule, so a node in one of them has none.
     */
    private static TypeAndInitDeclaration singleDeclarationOf(ASTNode node) {
        ASTNode child = node;
        for (ASTNode parent = node.getParent(); parent != null; child = parent, parent = parent.getParent()) {
            if (parent instanceof TypeAndInitDeclaration declaration) {
                return !declaration.getMembers().isEmpty()
                    && (child == declaration.getType() || child == declaration.getMembers().get(0)) ? declaration : null;
            }
        }
        return null;
    }

    /**
     * TauMC {@code replaceExpression(source, newSource, GLSLParser::function_definition)}, as
     * {@code AdaptiveShadowBoundsTransformer} uses it: parses {@code newSource} as a function definition and puts it in
     * place of every definition named {@code name} with the same parameter types (the overload that
     * {@code newSource} redefines; TauMC matched the old definition's text instead, which names the same one). The
     * replacement counts as an addition (the class javadoc) and keeps any injection anchor the old definition was.
     *
     * @return how many definitions were replaced (0 or 1 in a valid program)
     * @throws IllegalArgumentException if {@code newSource} is not a function definition
     */
    public int replaceFunctionDefinition(String name, String newSource) {
        final ExternalDeclaration probe = build(() -> t.parseExternalDeclaration(patternRoot(), newSource));
        if (!(probe instanceof FunctionDefinition probeDefinition)) {
            throw new IllegalArgumentException("Not a function definition: " + newSource);
        }
        final List<String> signature = signature(probeDefinition.getFunctionPrototype());
        final List<FunctionDefinition> targets = new ArrayList<>();
        for (ExternalDeclaration declaration : tree.getChildren()) {
            if (declaration instanceof FunctionDefinition definition
                && definition.getFunctionPrototype().getName().getName().equals(name)
                && signature(definition.getFunctionPrototype()).equals(signature)) {
                targets.add(definition);
            }
        }
        for (FunctionDefinition target : targets) {
            replaceFunctionDefinition(target, newSource);
        }
        return targets.size();
    }

    /**
     * Puts a parse of {@code newSource} in place of {@code definition}, as {@link #replaceFunctionDefinition(String,
     * String)} does for each definition it selects.
     *
     * @throws IllegalArgumentException if {@code newSource} is not a function definition
     */
    public void replaceFunctionDefinition(FunctionDefinition definition, String newSource) {
        final ExternalDeclaration parsed = build(() -> t.parseExternalDeclaration(root, newSource));
        if (!(parsed instanceof FunctionDefinition replacement)) {
            parsed.unregisterSubtree();
            throw new IllegalArgumentException("Not a function definition: " + newSource);
        }
        definition.replaceByAndDelete(added(replacement));
        // TauMC's anchors were the external declaration around the definition, which the replacement kept.
        if (variableAnchor == definition) {
            variableAnchor = replacement;
        }
        if (functionAnchor == definition) {
            functionAnchor = replacement;
        }
    }

    // The parameter types that tell overloads apart: each type specifier with its array, then the declarator's array.
    private static List<String> signature(FunctionPrototype prototype) {
        final List<String> types = new ArrayList<>();
        for (FunctionParameter parameter : prototype.getParameters()) {
            types.add(text(parameter.getType().getTypeSpecifier())
                + (parameter.getArraySpecifier() == null ? "" : text(parameter.getArraySpecifier())));
        }
        return types;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Qualified declarations and assignments

    /**
     * TauMC {@code findQualifiers(int token)}: every variable declared with the storage qualifier {@code type}
     * ({@code IN}, {@code OUT}, {@code UNIFORM}, {@code CONST}, ...) in a declaration with a type and declarators,
     * global or local, by declarator name; for {@code out float mat, recolor;} both names. Parameters, interface
     * blocks and qualifier-only declarations ({@code layout(...) in;}, {@code invariant gl_Position;}) are not
     * included, as in TauMC. A name declared twice keeps the declaration TauMC's cache order (the class javadoc) sees
     * last.
     *
     * <p>Order: TauMC returned a {@code HashMap}, and {@code CompatibilityTransformer.transformGrouped} injects the
     * missing {@code out} declarations in its iteration order, so the injected order depends on it. The returned map
     * iterates in exactly that order: it is filled like TauMC's map (same keys, inserted in TauMC's cache order) and
     * then frozen. It is not modifiable.</p>
     *
     * <p>Deviation: a qualified declaration without declarators ({@code uniform struct S { float a; };}) is skipped;
     * TauMC threw {@code NullPointerException}.</p>
     */
    public Map<String, QualifiedDeclaration> findQualifiers(StorageQualifier.StorageType type) {
        final Map<String, QualifiedDeclaration> hashOrder = new HashMap<>();
        for (Found<StorageQualifier> found : inTauMCOrder(StorageQualifier.class, qualifier -> qualifier.storageType == type)) {
            final StorageQualifier qualifier = found.node();
            if (qualifier.getParent() instanceof TypeQualifier typeQualifier
                && typeQualifier.getParent() instanceof FullySpecifiedType fullType
                && fullType.getParent() instanceof TypeAndInitDeclaration declaration) {
                for (DeclarationMember member : declaration.getMembers()) {
                    hashOrder.put(member.getName().getName(), QualifiedDeclaration.of(declaration, member,
                        nameOfType(declaration.getType().getTypeSpecifier())));
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(hashOrder));
    }

    /**
     * A variable declared with a storage qualifier, as {@link #findQualifiers} reports it. For
     * {@code flat out float isMoon;}: name {@code isMoon}, typeText {@code flat out float}, typeName {@code float},
     * arraySpecifierText null.
     *
     * @param name               the declarator's name
     * @param typeText           the declaration's type with all its qualifiers, printed on one line
     *                           ({@code layout(location = 0) out vec4}). TauMC's {@code ShaderPrinter} printed its
     *                           {@code fully_specified_type} with the same tokens; its {@code getText()} is this without
     *                           spaces ({@code flatoutfloat})
     * @param typeName           the type without qualifiers or array as the source spells it: a keyword
     *                           ({@code vec3}; {@code mat2x2} and {@code mat2} stay apart, as in TauMC), a struct name,
     *                           or an inline struct's text without whitespace ({@code structLight{vec3p;}}). TauMC's
     *                           {@code type_specifier_nonarray} first child's {@code getText()}, which
     *                           {@code transformGrouped} compares between stages. A declaration a verb added has the
     *                           compact name ({@code mat2}): the spelling is kept for the parsed program only
     * @param arraySpecifierText the array specifier on the type ({@code [2]} for {@code out vec3[2] v;}), or null. TauMC's
     *                           {@code type_specifier().array_specifier()}, which {@code transformGrouped} checks; an
     *                           array on the declarator ({@code out vec3 v[2];}) is the {@code member}'s
     * @param declaration        the declaration; every declarator of it has the same type fields
     * @param member             this declarator
     */
    public record QualifiedDeclaration(String name, String typeText, String typeName, String arraySpecifierText,
                                       TypeAndInitDeclaration declaration, DeclarationMember member) {
        static QualifiedDeclaration of(TypeAndInitDeclaration declaration, DeclarationMember member, String typeName) {
            final TypeSpecifier specifier = declaration.getType().getTypeSpecifier();
            return new QualifiedDeclaration(member.getName().getName(), text(declaration.getType()), typeName,
                specifier.getArraySpecifier() == null ? null : text(specifier.getArraySpecifier()), declaration, member);
        }
    }

    private String nameOfType(TypeSpecifier specifier) {
        if (specifier instanceof BuiltinNumericTypeSpecifier numeric) {
            final String spelled = spelledTypes.get(numeric);
            return spelled != null ? spelled : numeric.type.getMostCompactName();
        }
        if (specifier instanceof BuiltinFixedTypeSpecifier fixed) {
            return new DeclaredType.Fixed(fixed.type).keyword();
        }
        if (specifier instanceof TypeReference reference) {
            return reference.getReference().getName();
        }
        // An inline struct: its text without whitespace, as TauMC's getText() gave it.
        return compactText(specifier);
    }

    /**
     * TauMC {@code hasAssigment} (spelled correctly here): whether the left side of any assignment ({@code =},
     * {@code +=}, {@code *=}, ...) in the program starts with the text {@code name}. It is a text prefix, as in TauMC:
     * {@code color.rgb = ...} and {@code color[0] = ...} count for {@code color}, and so does {@code colorOut = ...}.
     * Increments ({@code color++}), initializers and {@code out} arguments do not count.
     */
    public boolean hasAssignment(String name) {
        final boolean[] found = {false};
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (!found[0] && node instanceof BinaryExpression binary && ASSIGNMENTS.contains(binary.getExpressionType())
                    && compactText(binary.getLeft()).startsWith(name)) {
                    found[0] = true;
                }
            }
        }.visit(tree);
        return found[0];
    }

    /**
     * TauMC {@code initialize(declaration, name)}: prepends {@code name = <zero>;} to {@code main}, with the zero value
     * of the declaration's type as TauMC wrote it: {@code false}, {@code 0}, {@code 0u}, {@code 0.0f}, or the vector or
     * matrix constructor of one ({@code vec3(0.0f)}, {@code ivec2(0)}, {@code bvec4(false)}, {@code mat3(0.0f)}). A
     * struct type initializes nothing, as in TauMC.
     *
     * <p>Deviations: {@code double} types get {@code 0.0lf} ({@code dvec2(0.0lf)}); TauMC wrote {@code 0.0d}, which
     * its parser read as {@code 0.0} followed by an error, and for vectors a statement without a value
     * ({@code v = ;}). Samplers and other opaque types initialize nothing; TauMC threw {@code NullPointerException}.
     * Neither can be an {@code in} or {@code out} of a shader stage that {@code transformGrouped} pairs.</p>
     */
    public void initialize(QualifiedDeclaration declaration, String name) {
        if (declaration.declaration().getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric) {
            final String zero = zeroValue(numeric.type);
            if (zero != null) {
                prependMain(name + " = " + zero + ";");
            }
        }
    }

    // TauMC's BuiltinFunction initializers, for the types its lexer knew (no 8-, 16- or explicit 64-bit integer types).
    private static String zeroValue(Type type) {
        if (type.getCompactName() == null) {
            return null;
        }
        final String scalar = switch (type.getNumberType()) {
            case BOOLEAN -> "false";
            case SIGNED_INTEGER -> type.getBitDepth() == 32 ? "0" : null;
            case UNSIGNED_INTEGER -> type.getBitDepth() == 32 ? "0u" : null;
            case FLOATING_POINT -> type.getBitDepth() == 32 ? "0.0f" : type.getBitDepth() == 64 ? "0.0lf" : null;
            default -> null;
        };
        if (scalar == null) {
            return null;
        }
        return type.isScalar() ? scalar : type.getCompactName() + "(" + scalar + ")";
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries

    /**
     * Every function definition, in document order, with its name, return type and parameters: what
     * {@code AdaptiveShadowBoundsTransformer} read from TauMC's parse tree. Declarations without a body are not
     * included.
     */
    public List<FunctionInfo> functions() {
        final List<FunctionInfo> functions = new ArrayList<>();
        for (ExternalDeclaration declaration : tree.getChildren()) {
            if (declaration instanceof FunctionDefinition definition) {
                functions.add(FunctionInfo.of(definition));
            }
        }
        return functions;
    }

    /**
     * A function definition as {@link #functions()} reports it.
     *
     * @param name       the function's name
     * @param returnType the return type with its qualifiers, printed on one line ({@code float}, {@code highp vec3});
     *                   TauMC's {@code fully_specified_type().getText()} is this without spaces
     * @param parameters the parameters in order
     * @param node       the definition; {@link #source(FunctionDefinition)} prints it
     */
    public record FunctionInfo(String name, String returnType, List<Parameter> parameters, FunctionDefinition node) {
        static FunctionInfo of(FunctionDefinition definition) {
            final FunctionPrototype prototype = definition.getFunctionPrototype();
            final List<Parameter> parameters = new ArrayList<>();
            for (FunctionParameter parameter : prototype.getParameters()) {
                parameters.add(new Parameter(text(parameter.getType().getTypeSpecifier()),
                    parameter.getName() == null ? null : parameter.getName().getName(), parameter));
            }
            return new FunctionInfo(prototype.getName().getName(), text(prototype.getReturnType()), List.copyOf(parameters),
                definition);
        }

        /**
         * The body's tokens joined without whitespace, braces included, as TauMC's {@code getText()} of the body gave
         * them for text searches ({@code shadowPos.x>}); literals are in glsl-transformer's form ({@code 1.0f}).
         */
        public String bodyText() {
            return compactText(node.getBody());
        }
    }

    /**
     * A function parameter as {@link FunctionInfo} reports it.
     *
     * @param type the type without qualifiers, with an array on the type ({@code vec3}, {@code sampler2D},
     *             {@code vec3[2]}); an array on the name ({@code float w[2]}) is not included, as in TauMC's
     *             {@code parameter_declarator.type_specifier()}
     * @param name the parameter's name, or null for an unnamed parameter
     * @param node the parameter
     */
    public record Parameter(String type, String name, FunctionParameter node) {
    }

    /** The definition printed as {@link #print(String)} prints it, for building a replacement's source. */
    public static String source(FunctionDefinition definition) {
        return ASTPrinter.print(PrintType.INDENTED, definition);
    }

    /** Any node printed on one line, tokens separated by the printer's spacing ({@code layout(location = 0) out vec4}). */
    public static String text(ASTNode node) {
        return ASTPrinter.print(PrintType.COMPACT, node).trim();
    }

    // The text without whitespace, as TauMC's getText() joined a rule's tokens.
    private static String compactText(ASTNode node) {
        return WHITESPACE.matcher(text(node)).replaceAll("");
    }

    /**
     * Whether a variable is declared at file scope under {@code name}: a declarator of a global declaration
     * ({@code uniform float name;}, {@code const int name = 1;}, {@code float a, name;}). {@link #hasVariable} also
     * counts locals and functions; interface blocks and their members count for neither.
     */
    public boolean isDeclaredGlobal(String name) {
        for (Identifier identifier : root.identifierIndex.get(name)) {
            if (identifier.getParent() instanceof DeclarationMember member
                && member.getParent() instanceof TypeAndInitDeclaration declaration
                && declaration.getParent() instanceof DeclarationExternalDeclaration) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Types

    /**
     * The type keyword of a declaration, as {@link #findType(String)} reports it: a numeric type ({@code float},
     * {@code vec3}, {@code uint}, {@code ivec2}, {@code bool}, {@code mat4}, ...) or a fixed type (samplers, images,
     * {@code void}, {@code atomic_uint}).
     */
    public sealed interface DeclaredType permits DeclaredType.Numeric, DeclaredType.Fixed {
        /** The GLSL keyword: {@code vec2}, {@code sampler2D}. A square matrix is {@code mat2}, never {@code mat2x2}. */
        String keyword();

        /** Whether this is the numeric type {@code type}. */
        default boolean is(Type type) {
            return this instanceof Numeric numeric && numeric.type() == type;
        }

        /** Whether this is the fixed type {@code type}. */
        default boolean is(BuiltinFixedTypeSpecifier.BuiltinType type) {
            return this instanceof Fixed fixed && fixed.type() == type;
        }

        record Numeric(Type type) implements DeclaredType {
            @Override
            public String keyword() {
                return type.getMostCompactName();
            }
        }

        record Fixed(BuiltinFixedTypeSpecifier.BuiltinType type) implements DeclaredType {
            @Override
            public String keyword() {
                final String literal = GLSLLexer.VOCABULARY.getLiteralName(type.getTokenType());
                return literal == null ? type.name().toLowerCase(java.util.Locale.ROOT) : literal.substring(1, literal.length() - 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Parsing support

    /**
     * A program that does not parse. The two ways glsl-transformer reports one, {@link ParsingException} (an input
     * mismatch, with a message such as "Missing semicolon or comma!") and ANTLR's
     * {@link ParseCancellationException} (any other recognition error), both become this; the original is the
     * cause. The message starts with ANTLR's position, {@code line L:C}, when there is one.
     */
    public static final class SyntaxException extends RuntimeException {
        private static final Pattern POSITION = Pattern.compile("^line (\\d+):(\\d+)");

        private final int line;
        private final int column;

        private SyntaxException(String message, RuntimeException cause, int line, int column) {
            super(message, cause);
            this.line = line;
            this.column = column;
        }

        static SyntaxException of(RuntimeException e) {
            final ParseCancellationException cancellation = ParsingException.extractParseCancellationException(e);
            final String antlr = cancellation == null ? null : cancellation.getMessage();
            String message = antlr != null ? antlr : String.valueOf(e.getMessage());
            if (e instanceof ParsingException && e.getMessage() != null && !e.getMessage().equals(antlr)) {
                message = message + " (" + e.getMessage() + ")";
            }
            int line = -1;
            int column = -1;
            if (antlr != null) {
                final java.util.regex.Matcher position = POSITION.matcher(antlr);
                if (position.find()) {
                    line = Integer.parseInt(position.group(1));
                    column = Integer.parseInt(position.group(2));
                }
            }
            return new SyntaxException(message, e, line, column);
        }

        /** The 1-based line of the error, or -1 if the parser gave none. */
        public int line() {
            return line;
        }

        /** The 0-based column of the error, or -1 if the parser gave none. */
        public int column() {
            return column;
        }
    }

    /**
     * Drops every token on the preprocessor channel and records each directive it drops. A directive such as
     * {@code #define A 1} arrives as {@code PP_ENTER_MODE} ("#define"), {@code PP_CONTENT} and {@code PP_EOL}; an empty
     * {@code #} line is {@code PP_EMPTY}; {@code #line} is {@code NR_LINE}. Records are keyed by the token's offset,
     * because the parser lexes the input a second time when its fast prediction mode fails.
     */
    static final class DirectiveFilter extends ChannelFilter<JobParameters> {
        final Map<Integer, String> dropped = new LinkedHashMap<>();
        // The program's numeric type keywords by offset, while the program itself is parsed (not the verbs' snippets).
        final TreeMap<Integer, TypeToken> numericTypes = new TreeMap<>();
        boolean recording = true;

        DirectiveFilter() {
            super(TokenChannel.PREPROCESSOR);
        }

        @Override
        public boolean isTokenAllowed(Token token) {
            if (super.isTokenAllowed(token)) {
                if (recording) {
                    final Type type = NUMERIC_TYPE_TOKENS.get(token.getType());
                    if (type != null) {
                        numericTypes.put(token.getStartIndex(), new TypeToken(type, token.getText()));
                    }
                }
                return true;
            }
            final int type = token.getType();
            if (type == GLSLLexer.PP_ENTER_MODE || type == GLSLLexer.PP_EMPTY || type == GLSLLexer.NR_LINE) {
                dropped.put(token.getStartIndex(), "line " + token.getLine() + ": " + token.getText().trim());
            }
            return false;
        }

        @Override
        public void resetState() {
            super.resetState();
            dropped.clear();
            numericTypes.clear();
        }
    }
}
