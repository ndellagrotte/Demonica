package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.debug.TransformCorpus;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.FunctionParameter;
import io.github.douira.glsl_transformer.ast.node.declaration.InterfaceBlockDeclaration;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.AdditionExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.ArrayAccessExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.MultiplicationExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.MemberAccessExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.statement.terminal.DeclarationStatement;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.NamedLayoutQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeReference;
import io.github.douira.glsl_transformer.ast.node.type.struct.StructDeclarator;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.util.Type;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import org.taumc.glsl.Transformer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The corpus mode of {@link ShaderAstParityTest}: runs every {@link ShaderAst} verb and its TauMC counterpart on every
 * recorded input of a transform corpus, with arguments drawn from the input itself, and compares the results
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, Step 3). This finds the semantic gaps that hand-written fixtures
 * miss.
 *
 * <p>Inputs: every Iris stage input, prepared as {@code ShaderTransformer} hands it to TauMC's parser
 * ({@link GlslCorpusParseSurveyTest#prepare}), and every compat input prepared as {@code CompatShaderTransformer} hands
 * it over ({@link #prepareCompat}); identical texts once. Per input:
 * the printed program without a verb (the baseline); the queries {@code findType}, {@code hasVariable} and
 * {@code containsCall} for names of every kind that occur; and each mutating verb on a fresh parse on both sides:
 * {@code rename} (one name per identifier kind, as a map, and the most frequent name), {@code renameFunctionCall}
 * (called and defined functions and referenced variables, as a map, and the most called function),
 * {@code replaceExpression} (a referenced variable, an array access with a literal index, a member access, a small
 * call, a product or sum of two names), {@code prependMain} and {@code appendMain}, a sequence of
 * {@code injectVariable} and {@code injectFunction}, {@code removeVariable} (a sole global, a shared declarator, a sole
 * local) and {@code renameArray} (arrays indexed only by literals, and one indexed otherwise, which both must reject).</p>
 *
 * <p>Outputs are compared as {@link GlslTokens}; a verb whose baseline already differs is counted as such, not
 * judged. Diffs go to {@code build/reports/shader-ast-parity/}. Known, deliberate deviations are classified by
 * {@link #explain} and do not fail the test.</p>
 */
final class ShaderAstCorpusDifferential {
    private static final int THREADS = 4;
    private static final int DIFFS_KEPT_PER_VERB = 25;
    private static final String ABSENT = "iris_parityAbsent";
    // TauMC's lexer makes these keywords: rename, containsCall and replaceExpression never see them (see
    // ShaderAstParityTest.deviationTexture2DIsAnIdentifier). They are left out of those verbs' arguments.
    private static final Set<String> TAUMC_KEYWORDS = Set.of("texture2D", "texture3D");

    private ShaderAstCorpusDifferential() {
    }

    enum Kind { GLOBAL, LOCAL, PARAMETER, FUNCTION, CALLED, REFERENCE, MEMBER, STRUCT_MEMBER, TYPE, BLOCK, LAYOUT }

    enum Outcome { IDENTICAL, DIFFERENT, BOTH_THREW, ONLY_TAUMC_THREW, ONLY_ADAPTER_THREW, BASELINE_DIFFERS }

    record Input(String label, String text) {
    }

    /**
     * One verb with its arguments on both libraries. A known TauMC quirk that ShaderAst deliberately does not reproduce
     * comes with an emulation: a difference is explained only if the adapter followed by the emulation prints exactly
     * what TauMC printed.
     */
    record Application(String verb, String arguments, Consumer<Transformer> taumc, Consumer<ShaderAst> adapter,
                       Set<Integer> foundTauMC, Set<Integer> foundAdapter, Consumer<ShaderAst> quirk, String quirkReason) {
        static Application of(String verb, String arguments, Consumer<Transformer> taumc, Consumer<ShaderAst> adapter) {
            return new Application(verb, arguments, taumc, adapter, null, null, null, null);
        }
    }

    static final class Summary {
        final Map<String, EnumMap<Outcome, Integer>> byVerb = new TreeMap<>();
        final Map<String, Integer> explained = new TreeMap<>();
        final Map<String, int[]> queries = new TreeMap<>();
        final Map<String, Integer> effective = new TreeMap<>();
        final List<String> parseFailures = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        int inputs;
        int distinctInputs;
        int unexplained;
        long millis;

        List<String> lines() {
            return lines;
        }

        int unexplained() {
            return unexplained;
        }

        synchronized void count(String verb, Outcome outcome) {
            byVerb.computeIfAbsent(verb, v -> new EnumMap<>(Outcome.class)).merge(outcome, 1, Integer::sum);
        }

        synchronized void effective(String verb) {
            effective.merge(verb, 1, Integer::sum);
        }

        synchronized void query(String verb, boolean same) {
            final int[] tally = queries.computeIfAbsent(verb, v -> new int[2]);
            tally[same ? 0 : 1]++;
        }
    }

    static Summary run(Path corpus, Path reports) throws Exception {
        final long start = System.nanoTime();
        final Summary summary = new Summary();
        final List<Input> inputs = collectInputs(corpus, summary);
        clear(reports);
        Files.createDirectories(reports);
        final Map<String, AtomicInteger> kept = new ConcurrentHashMap<>();

        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (Input input : inputs) {
                futures.add(pool.submit(() -> {
                    runInput(input, summary, reports, kept);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        summary.millis = (System.nanoTime() - start) / 1_000_000;
        describe(corpus, summary);
        Files.writeString(reports.resolve("summary.txt"), String.join("\n", summary.lines) + "\n", StandardCharsets.UTF_8);
        return summary;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Inputs

    private static List<Input> collectInputs(Path corpus, Summary summary) throws Exception {
        final List<Path> cases;
        try (Stream<Path> files = Files.walk(corpus)) {
            cases = files.filter(p -> p.getFileName().toString().equals(TransformCorpus.CASE_FILE))
                .map(Path::getParent).sorted().collect(Collectors.toList());
        }
        final Method requiredVersion = ShaderTransformer.class.getDeclaredMethod("getRequiredVersion", String.class, int.class);
        requiredVersion.setAccessible(true);
        final Map<String, String> byText = new LinkedHashMap<>();
        for (Path caseDir : cases) {
            final String name = corpus.relativize(caseDir).toString().replace('\\', '/');
            final Map<String, String> p = TransformCorpus.readCaseProperties(caseDir.resolve(TransformCorpus.CASE_FILE));
            if ("compat".equals(p.get("domain"))) {
                summary.inputs++;
                final String raw = Files.readString(caseDir.resolve("in.glsl"), StandardCharsets.UTF_8);
                try {
                    byText.putIfAbsent(prepareCompat(raw, p), name + " compat");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    final Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
                    summary.skipped.add(name + " compat: GLSM's pre-parse steps refuse it (" + cause + ")");
                }
                continue;
            }
            GlslCorpusParseSurveyTest.restoreHoisting(p);
            final Patch patch = Patch.valueOf(p.get("patch"));
            for (PatchShaderType stage : PatchShaderType.VALUES) {
                final String stageName = stage.name().toLowerCase(Locale.ROOT);
                final Path input = caseDir.resolve("in." + stageName + ".glsl");
                if (!Files.isRegularFile(input)) {
                    continue;
                }
                summary.inputs++;
                final String prepared = GlslCorpusParseSurveyTest.prepare(Files.readString(input, StandardCharsets.UTF_8),
                    stage, patch, p, requiredVersion);
                byText.putIfAbsent(prepared, name + " " + stageName);
            }
        }
        summary.distinctInputs = byText.size();
        final List<Input> inputs = new ArrayList<>();
        byText.forEach((text, label) -> inputs.add(new Input(label, text)));
        return inputs;
    }

    /**
     * The text {@code CompatShaderTransformer.transformInternal} hands TauMC's parser: GLES precision guards stripped, the
     * preprocessor preamble separated (conditionals in the body evaluated), {@code main(void)} normalized, then the
     * texture and reserved-word renames at the target version. Its private steps are called by reflection. The
     * {@code #version} line goes to the separated preamble, so the target version is put back in front for
     * {@link ShaderAst}'s lexer (TauMC ignores directives).
     */
    static String prepareCompat(String source, Map<String, String> p) throws ReflectiveOperationException {
        final Class<?> compat = com.gtnewhorizons.angelica.glsm.CompatShaderTransformer.class;
        final java.util.regex.Matcher version = java.util.regex.Pattern.compile("#version\\s+(\\d+)").matcher(source);
        final int declared = version.find() ? Integer.parseInt(version.group(1)) : 110;
        final int target = Math.max(declared, Integer.parseInt(p.getOrDefault("minGlslVersion", "330")));
        final Method strip = compat.getDeclaredMethod("stripGlesPrecisionGuards", String.class);
        final Method separate = compat.getDeclaredMethod("separatePreprocessorPreamble", String.class, int.class);
        strip.setAccessible(true);
        separate.setAccessible(true);
        final Object separated = separate.invoke(null, strip.invoke(null, source), target);
        final Method shader = separated.getClass().getDeclaredMethod("shader");
        shader.setAccessible(true);
        final java.lang.reflect.Field mainVoid = compat.getDeclaredField("MAIN_VOID_PARAMETERS_PATTERN");
        mainVoid.setAccessible(true);
        String text = ((java.util.regex.Pattern) mainVoid.get(null)).matcher((String) shader.invoke(separated)).replaceAll("main()");
        text = com.gtnewhorizons.angelica.glsm.GlslTransformUtils.replaceTexture(text);
        text = com.gtnewhorizons.angelica.glsm.GlslTransformUtils.renameParseBreakingTextureFunctions(text);
        text = com.gtnewhorizons.angelica.glsm.GlslTransformUtils.renameReservedWords(text, target);
        return text.contains("#version") ? text : "#version " + target + (target >= 150 ? " core" : "") + "\n" + text;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // One input

    private static void runInput(Input input, Summary summary, Path reports, Map<String, AtomicInteger> kept) {
        final ShaderAst probe;
        try {
            probe = ShaderAst.parse(input.text());
        } catch (RuntimeException e) {
            synchronized (summary) {
                summary.parseFailures.add(input.label() + ": " + e);
            }
            return;
        }
        final Map<Kind, TreeSet<String>> names = classify(probe);
        final List<Application> applications = applications(probe, names);
        runQueries(input, probe, names, summary, reports, kept);

        // The baseline: both libraries print the same program for the unmodified input.
        final Result baseline = compare(input.text(), Application.of("baseline", "", t -> { }, a -> { }));
        final boolean baselineSame = baseline.outcome == Outcome.IDENTICAL;
        record(input, "baseline", "", baseline, summary, reports, kept);
        final GlslTokens unchanged = baseline.taumc() == null ? null : GlslTokens.of(baseline.taumc());
        for (Application application : applications) {
            if (!baselineSame) {
                summary.count(application.verb(), Outcome.BASELINE_DIFFERS);
                continue;
            }
            final Result result = compare(input.text(), application);
            if (result.taumc() != null && unchanged != null && !GlslTokens.of(result.taumc()).equals(unchanged)) {
                summary.effective(application.verb());
            }
            record(input, application.verb(), application.arguments(), result, summary, reports, kept);
        }
    }

    /** The outcome; {@code taumc} is TauMC's printed program when it did not throw. */
    record Result(Outcome outcome, String detail, String explanation, String taumc) {
        Result(Outcome outcome, String detail) {
            this(outcome, detail, null, null);
        }
    }

    private static Result compare(String text, Application application) {
        String taumc = null;
        String adapter = null;
        Throwable taumcError = null;
        Throwable adapterError = null;
        try {
            taumc = ShaderAstParityTest.viaTauMC(text, application.taumc());
        } catch (Throwable e) {
            taumcError = e;
        }
        try {
            adapter = ShaderAstParityTest.viaShaderAst(text, application.adapter());
        } catch (Throwable e) {
            adapterError = e;
        }
        if (taumcError != null && adapterError != null) {
            final String detail = "TauMC: " + taumcError + "\nShaderAst: " + adapterError;
            // Both refuse the input the same way, or the refusals differ.
            return new Result(taumcError.getClass() == adapterError.getClass() ? Outcome.BOTH_THREW : Outcome.DIFFERENT, detail);
        }
        if (taumcError != null) {
            return new Result(Outcome.ONLY_TAUMC_THREW, "TauMC: " + taumcError);
        }
        if (adapterError != null) {
            return new Result(Outcome.ONLY_ADAPTER_THREW, "ShaderAst: " + stackHead(adapterError), null, taumc);
        }
        final String diff = GlslTokens.diff(taumc, adapter);
        if (!diff.isEmpty()) {
            if (application.quirk() != null) {
                try {
                    final String emulated = ShaderAstParityTest.viaShaderAst(text, application.adapter().andThen(application.quirk()));
                    if (GlslTokens.diff(taumc, emulated).isEmpty()) {
                        return new Result(Outcome.DIFFERENT, diff, application.quirkReason(), taumc);
                    }
                } catch (RuntimeException e) {
                    // Not explained.
                }
            }
            return new Result(Outcome.DIFFERENT, diff, null, taumc);
        }
        if (application.foundTauMC() != null && !application.foundTauMC().equals(application.foundAdapter())) {
            return new Result(Outcome.DIFFERENT, "found TauMC " + application.foundTauMC() + ", ShaderAst " + application.foundAdapter(),
                null, taumc);
        }
        return new Result(Outcome.IDENTICAL, "", null, taumc);
    }

    private static String stackHead(Throwable e) {
        final StringBuilder text = new StringBuilder(e.toString());
        final StackTraceElement[] stack = e.getStackTrace();
        for (int i = 0; i < Math.min(6, stack.length); i++) {
            text.append("\n    at ").append(stack[i]);
        }
        return text.toString();
    }

    private static void record(Input input, String verb, String arguments, Result result, Summary summary, Path reports,
                               Map<String, AtomicInteger> kept) {
        summary.count(verb, result.outcome());
        if (result.outcome() == Outcome.IDENTICAL || result.outcome() == Outcome.BOTH_THREW) {
            return;
        }
        final String explanation = explain(verb, arguments, result);
        synchronized (summary) {
            if (explanation != null) {
                summary.explained.merge(verb + ": " + explanation, 1, Integer::sum);
            } else {
                summary.unexplained++;
            }
        }
        final int n = kept.computeIfAbsent(verb, v -> new AtomicInteger()).incrementAndGet();
        if (n <= DIFFS_KEPT_PER_VERB) {
            final String file = verb.replaceAll("[^A-Za-z0-9]+", "_") + "-" + n + ".diff";
            final String text = "input: " + input.label() + "\nverb: " + verb + " " + arguments + "\noutcome: " + result.outcome()
                + (explanation == null ? "" : " (explained: " + explanation + ")") + "\n\n" + result.detail() + "\n";
            try {
                Files.writeString(reports.resolve(file), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** The reason for a known, deliberate difference, or null if the difference is not explained. */
    static String explain(String verb, String arguments, Result result) {
        return result.explanation();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Queries

    private static void runQueries(Input input, ShaderAst probe, Map<Kind, TreeSet<String>> names, Summary summary,
                                   Path reports, Map<String, AtomicInteger> kept) {
        final Transformer transformer;
        try {
            transformer = new Transformer(org.taumc.glsl.ShaderParser.parseShader(input.text()).full());
        } catch (RuntimeException e) {
            synchronized (summary) {
                summary.parseFailures.add(input.label() + " (TauMC): " + e);
            }
            return;
        }
        final Set<String> queried = new TreeSet<>();
        for (Kind kind : Kind.values()) {
            names.get(kind).stream().limit(6).forEach(queried::add);
        }
        queried.add(ABSENT);
        final Set<String> typed = new TreeSet<>();
        names.get(Kind.GLOBAL).stream().limit(12).forEach(typed::add);
        names.get(Kind.LOCAL).stream().limit(12).forEach(typed::add);
        names.get(Kind.PARAMETER).stream().limit(2).forEach(typed::add);
        typed.add(ABSENT);

        final List<String> differences = new ArrayList<>();
        for (String name : queried) {
            if (!TAUMC_KEYWORDS.contains(name)) {
                query(summary, "containsCall", name, transformer.containsCall(name), probe.containsCall(name), differences);
            }
            query(summary, "hasVariable", name, transformer.hasVariable(name), probe.hasVariable(name), differences);
        }
        for (String name : typed) {
            query(summary, "findType", name, ShaderAstParityTest.taumcTypeKeyword(transformer.findType(name)),
                ShaderAstParityTest.adapterTypeKeyword(probe.findType(name)), differences);
        }
        for (String difference : differences) {
            final String verb = difference.substring(0, difference.indexOf(' '));
            record(input, verb, difference, new Result(Outcome.DIFFERENT, difference), summary, reports, kept);
        }
    }

    private static void query(Summary summary, String verb, String name, Object taumc, Object adapter, List<String> differences) {
        final boolean same = taumc.equals(adapter);
        summary.query(verb, same);
        if (!same) {
            differences.add(verb + " " + name + ": TauMC " + taumc + ", ShaderAst " + adapter);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Arguments

    /** Every name in the program, by the kinds of places it occurs in (a name can be of several kinds). */
    static Map<Kind, TreeSet<String>> classify(ShaderAst ast) {
        final Map<Kind, TreeSet<String>> names = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            names.put(kind, new TreeSet<>());
        }
        for (Identifier identifier : identifiers(ast)) {
            final Kind kind = kindOf(identifier);
            if (kind != null) {
                names.get(kind).add(identifier.getName());
            }
        }
        return names;
    }

    private static List<Identifier> identifiers(ShaderAst ast) {
        final List<Identifier> identifiers = new ArrayList<>();
        new io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof Identifier identifier) {
                    identifiers.add(identifier);
                }
            }
        }.visit(ast.tree);
        return identifiers;
    }

    private static Kind kindOf(Identifier identifier) {
        final ASTNode parent = identifier.getParent();
        if (parent instanceof DeclarationMember member) {
            final ASTNode holder = member.getParent() == null ? null : member.getParent().getParent();
            return holder instanceof DeclarationExternalDeclaration ? Kind.GLOBAL
                : holder instanceof DeclarationStatement ? Kind.LOCAL : null;
        }
        if (parent instanceof FunctionParameter) {
            return Kind.PARAMETER;
        }
        if (parent instanceof FunctionPrototype) {
            return Kind.FUNCTION;
        }
        if (parent instanceof FunctionCallExpression) {
            return Kind.CALLED;
        }
        if (parent instanceof ReferenceExpression) {
            return Kind.REFERENCE;
        }
        if (parent instanceof MemberAccessExpression) {
            return Kind.MEMBER;
        }
        if (parent instanceof StructDeclarator) {
            return Kind.STRUCT_MEMBER;
        }
        if (parent instanceof TypeReference) {
            return Kind.TYPE;
        }
        if (parent instanceof InterfaceBlockDeclaration) {
            return Kind.BLOCK;
        }
        if (parent instanceof NamedLayoutQualifierPart) {
            return Kind.LAYOUT;
        }
        return null;
    }

    private static String first(Map<Kind, TreeSet<String>> names, Kind kind, Set<String> exclude) {
        for (String name : names.get(kind)) {
            if (!exclude.contains(name) && !TAUMC_KEYWORDS.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private static List<Application> applications(ShaderAst probe, Map<Kind, TreeSet<String>> names) {
        final List<Application> applications = new ArrayList<>();

        // rename(Map): one name of every kind.
        final Map<String, String> renames = new TreeMap<>();
        for (Kind kind : Kind.values()) {
            final String name = first(names, kind, renames.keySet());
            if (name != null) {
                renames.put(name, name + "_rn");
            }
        }
        if (!renames.isEmpty()) {
            applications.add(Application.of("rename(Map)", renames.toString(), t -> t.rename(renames), a -> a.rename(renames)));
        }
        final String frequent = mostFrequent(probe, id -> !TAUMC_KEYWORDS.contains(id.getName()));
        if (frequent != null) {
            applications.add(Application.of("rename", frequent, t -> t.rename(frequent, frequent + "_r1"),
                a -> a.rename(frequent, frequent + "_r1")));
        }

        // renameFunctionCall(Map): called names (TauMC's keyword calls included), a defined function, a reference and a
        // global.
        final Map<String, String> callRenames = new TreeMap<>();
        names.get(Kind.CALLED).stream().limit(3).forEach(n -> callRenames.put(n, n + "_rf"));
        for (Kind kind : List.of(Kind.FUNCTION, Kind.REFERENCE, Kind.GLOBAL)) {
            final String name = first(names, kind, callRenames.keySet());
            if (name != null) {
                callRenames.put(name, name + "_rf");
            }
        }
        if (!callRenames.isEmpty()) {
            applications.add(Application.of("renameFunctionCall(Map)", callRenames.toString(),
                t -> t.renameFunctionCall(callRenames), a -> a.renameFunctionCall(callRenames)));
        }
        final String called = mostFrequent(probe, id -> id.getParent() instanceof FunctionCallExpression);
        if (called != null) {
            applications.add(Application.of("renameFunctionCall", called, t -> t.renameFunctionCall(called, called + "_c1"),
                a -> a.renameFunctionCall(called, called + "_c1")));
        }

        // replaceExpression: a referenced global, then patterns printed from the program.
        for (String name : names.get(Kind.GLOBAL)) {
            if (names.get(Kind.REFERENCE).contains(name) && !TAUMC_KEYWORDS.contains(name)) {
                applications.add(replace("identifier", name, "iris_parityFn(iris_parityArg).x"));
                break;
            }
        }
        patterns(probe).forEach((shape, pattern) -> applications.add(replace(shape, pattern, "iris_parity" + shape)));

        // prependMain and appendMain together.
        applications.add(Application.of("prependMain+appendMain", "",
            t -> { t.prependMain("iris_parityP = 1.0;"); t.appendMain("iris_parityQ = 2.0;"); },
            a -> { a.prependMain("iris_parityP = 1.0;"); a.appendMain("iris_parityQ = 2.0;"); }));

        // A sequence of injections that moves both anchors.
        final List<String[]> injections = List.of(
            new String[]{"v", "uniform float iris_parityU0;"},
            new String[]{"v", "vec4 iris_parityG0;"},
            new String[]{"f", "float iris_parityF0(float x) { return x * 2.0; }"},
            new String[]{"f", "struct iris_parityS { float a; };"},
            new String[]{"v", "uniform float iris_parityU1;"},
            new String[]{"f", "float iris_parityV = 1.0;"});
        applications.add(Application.of("injectVariable+injectFunction", "6 injections",
            t -> injections.forEach(i -> { if (i[0].equals("v")) t.injectVariable(i[1]); else t.injectFunction(i[1]); }),
            a -> injections.forEach(i -> { if (i[0].equals("v")) a.injectVariable(i[1]); else a.injectFunction(i[1]); })));

        // removeVariable: a sole global, a declarator that shares its declaration, a sole local.
        final Map<String, String> removals = removalCandidates(probe);
        removals.forEach((shape, name) -> applications.add(Application.of("removeVariable", shape + " " + name,
            t -> t.removeVariable(name), a -> a.removeVariable(name))));

        // renameArray: arrays indexed only by int literals, and one that is not.
        final Map<String, Boolean> arrays = arrays(probe);
        int literal = 0;
        boolean other = false;
        for (Map.Entry<String, Boolean> array : arrays.entrySet()) {
            final String name = array.getKey();
            if (array.getValue() && literal < 2) {
                literal++;
                final Set<Integer> foundTauMC = new TreeSet<>();
                final Set<Integer> foundAdapter = new TreeSet<>();
                applications.add(new Application("renameArray", name, t -> t.renameArray(name, name + "_ra", foundTauMC),
                    a -> a.renameArray(name, name + "_ra", foundAdapter), foundTauMC, foundAdapter, null, null));
            } else if (!array.getValue() && !other) {
                other = true;
                applications.add(Application.of("renameArray(non-literal)", name, t -> t.renameArray(name, name + "_ra", new TreeSet<>()),
                    a -> a.renameArray(name, name + "_ra", new TreeSet<>())));
            }
        }
        return applications;
    }

    private static Application replace(String shape, Pattern pattern, String replacement) {
        final String text = pattern.text();
        final String leading = pattern.leadingOperand();
        // TauMC's postfix pass parses a binary pattern as a postfix expression, which keeps only its first operand, and
        // replaces every remaining occurrence of that operand (ShaderAstParityTest.deviationBinaryPatternAlsoReplacesItsFirstOperandInTauMC).
        final Consumer<ShaderAst> quirk = leading == null ? null : a -> a.replaceExpression(leading, replacement);
        return new Application("replaceExpression(" + shape + ")", text + " -> " + replacement,
            t -> t.replaceExpression(text, replacement), a -> a.replaceExpression(text, replacement), null, null, quirk,
            "TauMC's postfix pass cut the binary pattern to its first operand and replaced that operand everywhere");
    }

    private static Application replace(String shape, String text, String replacement) {
        return replace(shape, new Pattern(text, null), replacement);
    }

    /** A pattern's text, and for a binary pattern the text of its left operand. */
    record Pattern(String text, String leadingOperand) {
    }

    private static String mostFrequent(ShaderAst ast, java.util.function.Predicate<Identifier> filter) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (Identifier identifier : identifiers(ast)) {
            if (filter.test(identifier)) {
                counts.merge(identifier.getName(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .map(Map.Entry::getKey).orElse(null);
    }

    /** Small expressions printed from the program, one per shape, without float literals (TauMC matches spelling). */
    private static Map<String, Pattern> patterns(ShaderAst ast) {
        final Map<String, TreeMap<String, String>> candidates = new TreeMap<>();
        new io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof ArrayAccessExpression access && access.getLeft() instanceof ReferenceExpression
                    && access.getRight() instanceof LiteralExpression index && index.isInteger() && index.getType() == Type.INT32) {
                    add("ArrayAccess", access);
                } else if (node instanceof MemberAccessExpression member && member.getOperand() instanceof ReferenceExpression) {
                    add("MemberAccess", member);
                } else if (node instanceof FunctionCallExpression call && call.getFunctionName() != null
                    && !TAUMC_KEYWORDS.contains(call.getFunctionName().getName())
                    && !call.getParameters().isEmpty() && call.getParameters().size() <= 3
                    && call.getParameters().stream().allMatch(ShaderAstCorpusDifferential::simpleOperand)) {
                    add("Call", call);
                } else if ((node instanceof MultiplicationExpression || node instanceof AdditionExpression)
                    && simpleOperand(((io.github.douira.glsl_transformer.ast.node.expression.binary.BinaryExpression) node).getLeft())
                    && simpleOperand(((io.github.douira.glsl_transformer.ast.node.expression.binary.BinaryExpression) node).getRight())) {
                    add(node instanceof MultiplicationExpression ? "Product" : "Sum", node);
                }
            }

            private void add(String shape, ASTNode node) {
                final String leading = node instanceof io.github.douira.glsl_transformer.ast.node.expression.binary.BinaryExpression binary
                    ? ASTPrinter.print(PrintType.COMPACT, binary.getLeft()).trim() : null;
                candidates.computeIfAbsent(shape, s -> new TreeMap<>()).putIfAbsent(ASTPrinter.print(PrintType.COMPACT, node).trim(), leading);
            }
        }.visit(ast.tree);
        final Map<String, Pattern> patterns = new TreeMap<>();
        candidates.forEach((shape, texts) -> patterns.put(shape, new Pattern(texts.firstKey(), texts.firstEntry().getValue())));
        return patterns;
    }

    private static boolean simpleOperand(Expression expression) {
        return expression instanceof ReferenceExpression reference && !TAUMC_KEYWORDS.contains(reference.getIdentifier().getName())
            || expression instanceof MemberAccessExpression member && member.getOperand() instanceof ReferenceExpression;
    }

    private static Map<String, String> removalCandidates(ShaderAst ast) {
        final Map<String, String> removals = new TreeMap<>();
        final List<DeclarationMember> members = new ArrayList<>();
        new io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof DeclarationMember member && member.getParent() instanceof TypeAndInitDeclaration) {
                    members.add(member);
                }
            }
        }.visit(ast.tree);
        for (DeclarationMember member : members) {
            final TypeAndInitDeclaration declaration = (TypeAndInitDeclaration) member.getParent();
            final boolean global = declaration.getParent() instanceof DeclarationExternalDeclaration;
            final boolean shared = declaration.getMembers().size() > 1;
            final String name = member.getName().getName();
            if (shared) {
                // Not an initialized or sized first declarator: see ShaderAstParityTest.deviationRemovingAnInitializedFirstDeclarator.
                final boolean first = declaration.getMembers().get(0) == member;
                if (!first || member.getInitializer() == null && member.getArraySpecifier() == null) {
                    removals.putIfAbsent("shared", name);
                }
            } else {
                removals.putIfAbsent(global ? "sole-global" : "sole-local", name);
            }
        }
        return removals;
    }

    /** Array names used with a bare base, mapped to whether every such access has a decimal int literal index. */
    private static Map<String, Boolean> arrays(ShaderAst ast) {
        final Map<String, Boolean> arrays = new TreeMap<>();
        new io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof ArrayAccessExpression access && access.getLeft() instanceof ReferenceExpression reference) {
                    final boolean literal = access.getRight() instanceof LiteralExpression index && index.isInteger()
                        && index.getType() == Type.INT32 && index.getIntegerFormat() != LiteralExpression.IntegerFormat.HEXADECIMAL;
                    arrays.merge(reference.getIdentifier().getName(), literal, Boolean::logicalAnd);
                }
            }
        }.visit(ast.tree);
        return arrays;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Report

    private static void describe(Path corpus, Summary summary) {
        final List<String> lines = summary.lines;
        lines.add("shader-ast-parity: corpus=" + corpus + " inputs=" + summary.inputs + " distinct=" + summary.distinctInputs
            + " parseFailures=" + summary.parseFailures.size() + " skipped=" + summary.skipped.size()
            + " unexplained=" + summary.unexplained
            + " seconds=" + summary.millis / 1000);
        summary.byVerb.forEach((verb, outcomes) -> lines.add("shader-ast-parity:   " + verb + " " + outcomes
            + (verb.equals("baseline") ? "" : " changedTheProgram=" + summary.effective.getOrDefault(verb, 0))));
        summary.queries.forEach((verb, tally) -> lines.add("shader-ast-parity:   query " + verb + " same=" + tally[0]
            + " different=" + tally[1]));
        summary.explained.forEach((reason, count) -> lines.add("shader-ast-parity:   explained " + count + "x " + reason));
        summary.parseFailures.forEach(f -> lines.add("shader-ast-parity:   PARSE FAILED " + f));
        summary.skipped.forEach(f -> lines.add("shader-ast-parity:   SKIPPED " + f));
    }

    private static void clear(Path reports) throws IOException {
        if (!Files.isDirectory(reports)) {
            return;
        }
        try (Stream<Path> files = Files.list(reports)) {
            for (Path file : files.collect(Collectors.toList())) {
                Files.deleteIfExists(file);
            }
        }
    }
}
