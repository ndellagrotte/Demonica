package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.ArrayAccessExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.MemberAccessExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExtensionDirective;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.node.external_declaration.LayoutDefaults;
import io.github.douira.glsl_transformer.ast.node.external_declaration.PragmaDirective;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Life cycle: {@link #parse(String, int)}, then the verbs, then {@link #print(String)}. An instance is not
 * thread-safe; every call to {@code parse} builds its own parser (see {@link #newParser}). Different instances may be
 * used on different threads: every method that builds AST nodes holds {@link #BUILD_LOCK}.</p>
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
     * with separate parsers, can give nodes the other thread's root or corrupt the stack. Every method of this class
     * that builds nodes (the parse, and each verb that parses a snippet) holds this lock while it does; code that uses
     * {@link #t}, {@link #tree} or {@link #root} to build nodes itself (a {@code parseAndInjectNode}, a
     * {@code new Identifier(...)}) must hold it too. Reentrant, so a caller may hold it around a whole transform.
     */
    public static final ReentrantLock BUILD_LOCK = new ReentrantLock();

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("#version\\s+(\\d+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The parser; also the argument every glsl-transformer call that parses a snippet needs. */
    public final ASTParser t;
    /** The program. {@link #print(String)} removes its version statement and extension directives. */
    public final TranslationUnit tree;
    /** The indexes of {@link #tree}: identifiers by name, nodes by class, external declarations by name. */
    public final Root root;

    private final List<String> droppedDirectives;

    // TauMC's Transformer.variable and Transformer.function: where the next injectVariable and injectFunction insert.
    private ExternalDeclaration variableAnchor;
    private ExternalDeclaration functionAnchor;

    private ShaderAst(ASTParser t, TranslationUnit tree, Root root, List<String> droppedDirectives) {
        this.t = t;
        this.tree = tree;
        this.root = root;
        this.droppedDirectives = droppedDirectives;
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
     * {@code #pragma}, which glsl-transformer parses, is removed from the tree. {@link #droppedDirectives()} lists
     * them. {@code #version} and {@code #extension} stay in the tree until {@link #print(String)}.</p>
     *
     * @throws SyntaxException          if the source does not parse
     * @throws IllegalArgumentException if glsl-transformer has no {@link Version} for {@code version}
     */
    public static ShaderAst parse(String source, int version) {
        return parse(source, Version.fromNumber(version));
    }

    private static ShaderAst parse(String source, Version lexerVersion) {
        final DirectiveFilter filter = new DirectiveFilter();
        final ASTParser parser = newParser(filter);
        if (lexerVersion != null) {
            parser.getLexer().version = lexerVersion;
        }
        final Root root = ROOT_SUPPLIER.get();
        final TranslationUnit tree;
        try {
            tree = locked(() -> parser.parseTranslationUnit(root, source));
        } catch (ParsingException | ParseCancellationException e) {
            throw SyntaxException.of(e);
        }
        final List<String> dropped = new ArrayList<>(filter.dropped.values());
        for (ExternalDeclaration declaration : new ArrayList<>(tree.getChildren())) {
            if (declaration instanceof PragmaDirective pragma) {
                dropped.add(ASTPrinter.print(PrintType.COMPACT, pragma).trim());
                pragma.detachAndDelete();
            }
        }
        if (!dropped.isEmpty()) {
            LOGGER.warn("[ShaderAst] Dropped {} preprocessor directive(s) that the transform does not evaluate: {}",
                dropped.size(), dropped);
        }
        return new ShaderAst(parser, tree, root, List.copyOf(dropped));
    }

    /**
     * Builds the parser for one {@link #parse}. This is the only place a parser is constructed: Step 5 decides
     * whether it stays one per call or becomes one shared instance under a lock.
     *
     * <p>Parsing cache: {@link ASTParser.ParsingCacheStrategy#NONE}. The two-tier cache that Iris uses returns a
     * cached parse tree for a translation unit it has seen, and then the channel filter sees no tokens, so a
     * dropped directive would go unlogged. {@code ALL_EXCLUDING_TRANSLATION_UNIT}, which would fit, recurses without
     * end in 3.0.0-pre3 ({@code TranslationUnitFilterCachingParser.parse} calls itself). Snippet ASTs (the verbs'
     * code strings) are still cached by the parser's own AST cache, which this setting does not affect; that cache
     * lives as long as the parser, so with one parser per call it serves repeats within one transform only.</p>
     */
    static ASTParser newParser(DirectiveFilter filter) {
        final ASTParser parser = new ASTParser();
        // Replaces the parser, so it comes before setTokenFilter.
        parser.setParsingCacheStrategy(ASTParser.ParsingCacheStrategy.NONE);
        parser.setTokenFilter(filter);
        return parser;
    }

    private static <N> N locked(Supplier<N> build) {
        BUILD_LOCK.lock();
        try {
            return build.get();
        } finally {
            BUILD_LOCK.unlock();
        }
    }

    /** The directives {@link #parse} dropped, as {@code line N: #define} (filtered) or the {@code #pragma} text. */
    public List<String> droppedDirectives() {
        return droppedDirectives;
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
     * variable anchor. The anchor starts as the first external declaration that is a function definition or holds a
     * storage qualifier ({@code const}, {@code in}, {@code out}, {@code uniform}, {@code attribute}, {@code varying},
     * {@code buffer}, ...; layout, precision, interpolation and invariant qualifiers do not count). An injected
     * declaration that holds a storage qualifier becomes the new anchor, so qualified injections appear in reverse
     * order, and an injected function definition becomes both anchors. With no anchor (no qualified declaration and
     * no function) nothing is inserted, as in TauMC.
     *
     * <p>Deviation: if the anchor has been removed from the tree, it is recomputed; TauMC throws
     * {@code IndexOutOfBoundsException}.</p>
     */
    public void injectVariable(String code) {
        final ExternalDeclaration anchor = variableAnchor();
        if (anchor == null) {
            LOGGER.debug("[ShaderAst] injectVariable: no qualified declaration and no function to anchor on; dropped {}",
                code);
            return;
        }
        final ExternalDeclaration insert = locked(() -> t.parseExternalDeclaration(root, code));
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
     * <p>Deviation: with no function definition in the program the declaration is appended at the end, where TauMC
     * throws {@code IndexOutOfBoundsException}; a removed anchor is recomputed.</p>
     */
    public void injectFunction(String code) {
        final ExternalDeclaration anchor = functionAnchor();
        final ExternalDeclaration insert = locked(() -> t.parseExternalDeclaration(root, code));
        final int index = anchor == null ? tree.getChildren().size() : tree.getChildren().indexOf(anchor);
        tree.getChildren().add(index, insert);
        updateAnchors(insert, true);
    }

    private ExternalDeclaration variableAnchor() {
        if (variableAnchor != null && variableAnchor.getParent() == tree) {
            return variableAnchor;
        }
        variableAnchor = null;
        for (ExternalDeclaration declaration : tree.getChildren()) {
            if (declaration instanceof FunctionDefinition || declaration instanceof LayoutDefaults
                || holdsStorageQualifier(declaration)) {
                variableAnchor = declaration;
                break;
            }
        }
        return variableAnchor;
    }

    private ExternalDeclaration functionAnchor() {
        if (functionAnchor != null && functionAnchor.getParent() == tree) {
            return functionAnchor;
        }
        functionAnchor = null;
        for (ExternalDeclaration declaration : tree.getChildren()) {
            if (declaration instanceof FunctionDefinition) {
                functionAnchor = declaration;
                break;
            }
        }
        return functionAnchor;
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
     * lexer; glsl-transformer lexes them as identifiers, so they are ordinary call names here.
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
     * throws, where TauMC wrote the identifier {@code newName + "-1"}.</p>
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
            access.replaceByAndDelete(locked(() -> t.parseExpression(root, newName + value)));
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
     * <p>Any other {@code oldCode} matches every expression node that is structurally equal to its parse (same node
     * classes, same identifiers, same operators, literals equal by value); TauMC compared the source text of binary
     * and postfix expressions.</p>
     *
     * <p>Deviations, all where TauMC produced wrong code or depended on spelling: literals match by value
     * ({@code 0.0}, {@code 0.} and {@code 0.0f} are one literal; TauMC matched the spelling); a replacement is
     * parenthesized by the printer where the context binds tighter (TauMC printed {@code a + b * c} for
     * {@code x * c} with {@code x} replaced by {@code a + b}); a replacement in postfix position (an assignment
     * target, {@code old.xy}, {@code old[0]}) is kept whole, where TauMC reparsed it as a postfix expression and
     * dropped everything after the first operator; a pattern that is itself a binary expression
     * ({@code colorSample * mult}) replaces only its matches, where TauMC's postfix pass also cut the pattern to its
     * first operand and replaced every remaining occurrence of that operand ({@code colorSample}); a call of
     * {@code oldCode} is left alone when {@code newCode} is not an identifier (TauMC wrote {@code newCode(args)}).
     * None of Demonica's patterns (names, array and member accesses, calls) reaches the last two.</p>
     */
    public void replaceExpression(String oldCode, String newCode) {
        final Expression pattern = locked(() -> t.parseExpression(patternRoot(), oldCode));
        if (pattern instanceof ReferenceExpression reference) {
            final String name = reference.getIdentifier().getName();
            final String trimmed = newCode.trim();
            final boolean newIsIdentifier = IDENTIFIER.matcher(trimmed).matches();
            for (Identifier identifier : new ArrayList<>(root.identifierIndex.get(name))) {
                if (identifier.getParent() instanceof ReferenceExpression target) {
                    target.replaceByAndDelete(locked(() -> t.parseExpression(root, newCode)));
                } else if (newIsIdentifier && identifier.getParent() instanceof FunctionCallExpression) {
                    identifier.setName(trimmed);
                }
            }
            return;
        }

        final Matcher<Expression> matcher = new Matcher<>(pattern);
        final Class<? extends Expression> patternClass = pattern.getClass();
        final Set<Expression> matches = Collections.newSetFromMap(new IdentityHashMap<>());
        final String hint = longestIdentifier(pattern);
        if (hint != null) {
            for (Identifier identifier : root.identifierIndex.get(hint)) {
                // Every ancestor of the pattern's class, not only the nearest: the hint can sit in a nested node of
                // that class (the pattern f(g(x)) with the hint x), or below a non-expression node of the pattern (the
                // array size N of mat4[N](...)).
                for (ASTNode node = identifier.getParent(); node != null && node != tree; node = node.getParent()) {
                    if (node.getClass() == patternClass && matcher.matches((Expression) node)) {
                        matches.add((Expression) node);
                    }
                }
            }
        } else {
            for (Expression node : root.nodeIndex.get(patternClass)) {
                if (matcher.matches(node)) {
                    matches.add(node);
                }
            }
        }
        for (Expression match : matches) {
            // A match inside an earlier replaced match is already gone with it.
            if (isAttached(match)) {
                match.replaceByAndDelete(locked(() -> t.parseExpression(root, newCode)));
            }
        }
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
            main.getBody().getStatements().add(0, locked(() -> t.parseStatement(root, code)));
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
            main.getBody().getStatements().add(locked(() -> t.parseStatement(root, code)));
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
     * local, not parameters or struct members) are scanned in document order: the first one that shares its
     * declaration with other declarators ({@code float a, name;}) is removed alone and the scan stops; a declarator
     * that is alone in its declaration is remembered and the scan goes on, so the last such one is removed, together
     * with its whole declaration (global or local statement). Nothing happens if no declarator has the name.
     *
     * <p>Deviation: removing the first declarator of {@code float a = 1.0, b;} gives {@code float b;}; TauMC wrote the
     * next declarator's text into the first one's name and kept the first one's array size and initializer
     * ({@code float b = 1.0;}).</p>
     */
    public void removeVariable(String name) {
        DeclarationMember target = null;
        boolean shared = false;
        for (DeclarationMember member : declaratorsInDocumentOrder(name)) {
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
        } else {
            // TypeAndInitDeclaration, then its DeclarationExternalDeclaration or DeclarationStatement.
            target.getParent().getParent().detachAndDelete();
        }
    }

    /**
     * The type named by the first declaration (in document order) that declares a variable {@code name}: a global
     * or local declarator, not a parameter or a struct member. TauMC {@code findType} returned the type keyword's
     * lexer token, 0 when nothing matched; this returns a {@link DeclaredType}, or null when nothing matched. Like
     * TauMC it skips a declaration whose type is a struct and looks further. An array declaration reports its
     * element type.
     */
    public DeclaredType findType(String name) {
        for (DeclarationMember member : declaratorsInDocumentOrder(name)) {
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
     */
    public boolean containsCall(String name) {
        for (Identifier identifier : root.identifierIndex.get(name)) {
            if (isExpressionIdentifier(identifier)) {
                return true;
            }
        }
        return false;
    }

    private List<DeclarationMember> declaratorsInDocumentOrder(String name) {
        final List<DeclarationMember> members = new ArrayList<>();
        for (Identifier identifier : root.identifierIndex.get(name)) {
            if (identifier.getParent() instanceof DeclarationMember member && member.getParent() instanceof TypeAndInitDeclaration) {
                members.add(member);
            }
        }
        if (members.size() > 1) {
            sortInDocumentOrder(members);
        }
        return members;
    }

    private <N extends ASTNode> void sortInDocumentOrder(List<N> nodes) {
        final Map<ASTNode, Integer> order = new IdentityHashMap<>();
        for (N node : nodes) {
            order.put(node, -1);
        }
        final int[] position = {0};
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (order.containsKey(node)) {
                    order.put(node, position[0]);
                }
                position[0]++;
            }
        }.visit(tree);
        nodes.sort((a, b) -> Integer.compare(order.get(a), order.get(b)));
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

        DirectiveFilter() {
            super(TokenChannel.PREPROCESSOR);
        }

        @Override
        public boolean isTokenAllowed(Token token) {
            if (super.isTokenAllowed(token)) {
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
        }
    }
}
