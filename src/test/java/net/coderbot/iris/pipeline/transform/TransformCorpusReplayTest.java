package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.debug.TransformCorpus;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;
import net.coderbot.iris.pipeline.transform.corpus.TransformCorpusRecorder;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.CeleritasTerrainParameters;
import net.coderbot.iris.pipeline.transform.parameter.ComputeParameters;
import net.coderbot.iris.pipeline.transform.parameter.DHParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Replays a recorded transform corpus through the transform engine (glsl-transformer, recorded as {@code douira}) and
 * compares every stage with the recorded reference output of TauMC's engine ({@code out.taumc.<stage>.glsl}) as
 * {@link GlslTokens} (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, 3.5). The TauMC engine and its library were
 * removed in Step 11; its recorded outputs stay the reference.
 *
 * <p>Without a configured corpus it replays the committed mini-corpus, {@code src/test/resources/transform-corpus}, so
 * every {@code :test} and {@code check} guards the transform output against its recorded TauMC snapshot (Step 8;
 * record mode needs an explicit corpus). The root {@code test {}} block forwards the Gradle properties:
 * {@code -PglslCorpusDir=<abs>} (another corpus, searched recursively for {@code case.properties}),
 * {@code -PglslReplayPatches=COMPOSITE,COMPUTE,...}
 * (patch kinds to replay, {@code COMPAT} for GLSM's mod-shader cases; default all) and {@code -PglslReplayRecord=true}
 * (write {@code out.<engine>.<stage>.glsl} instead of comparing) and {@code -PglslReplayThreads=N} (after the replay,
 * transform every case the engine replayed successfully again, once more on this thread and then all at once on N
 * threads, and require the concurrent outputs to equal the replayed ones; prints the timings).</p>
 *
 * <p>Each case restores what the output depends on besides the sources: the GLSL capability
 * ({@link RenderSystem#initializeGlslCapabilityForTesting(int, boolean, boolean)}), version hoisting
 * ({@link VersionNegotiation#init()} or its reset), the adaptive-shadow-bounds instrumentation
 * ({@link AdaptiveShadowBoundsStats#activateForTesting(int)}) and the {@link Parameters}; then it calls the engine
 * directly, not through the cache. A stage that differs is written to {@code build/reports/transform-replay/} and
 * fails the test unless {@code src/test/resources/transform-replay/accepted.txt} tolerates it. A case the engine cannot
 * run (a state the replayer cannot restore) is counted as unsupported. GLSM's compat cases run through
 * {@code CompatShaderTransformer.transform(source, isFragment)} (Step 10). An engine exception is a failure (or an
 * accepted error).</p>
 *
 * <p>A case recorded with {@code outcome=error} (the TauMC engine threw) is identical when the replay throws the same
 * {@code class: message}. When the replay succeeds or throws something else, the case's outcome differs: the report
 * {@code <case>.error.diff} holds both, and {@code accepted.txt} can tolerate it with the stage {@code error-succeeded}
 * (the replay transformed the case) or {@code error-threw} (the replay threw something else), never with both at once:
 * an entry for the first does not hide a replay that starts to throw (Step 6). A case recorded with an output whose
 * replay throws is failing unless an entry with the stage {@code threw} accepts it (Step 7b); its report is
 * {@code <case>.error.diff} too.</p>
 *
 * <p>Dead entries fail too (Step 7): every {@code accepted.txt} entry whose case glob matches a case this run replayed (not filtered out, not unsupported) must
 * have tolerated a difference of it; an entry that matched none is listed as {@code STALE} and fails the test, so
 * entries cannot outlive the differences they were written for. Entries for cases the run did not replay (another
 * corpus, a filtered patch kind) are not judged. Record mode skips the check.</p>
 *
 * <p>The summary also gives the engine's time: the sum of the engine calls per patch kind ({@code replay: transformMs}),
 * measured around the direct call, as {@code TransformPatcher}'s {@code transformMs} is around its call.</p>
 */
class TransformCorpusReplayTest {
    static final String CORPUS_DIR_PROPERTY = "demonica.glsl.corpus.dir";
    static final String PATCHES_PROPERTY = "demonica.glsl.replay.patches";
    static final String RECORD_PROPERTY = "demonica.glsl.replay.record";
    static final String THREADS_PROPERTY = "demonica.glsl.replay.threads";
    /** The engine whose recorded outputs are the reference: TauMC's, removed in Step 11. */
    static final String REFERENCE_ENGINE = "taumc";
    private static final String ACCEPTED_RESOURCE = "/transform-replay/accepted.txt";

    @AfterAll
    static void restoreGlobalState() {
        AdaptiveShadowBoundsStats.activateForTesting(-1);
        VersionNegotiation.resetForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    /** accepted.txt: every entry needs a reason and a known stage (S2 verification: stages were not checked). */
    @Test
    void acceptedEntriesNeedAReasonAndAKnownStage() throws IOException {
        final List<AcceptedDiff> entries = parseAccepted("# comment\n\nbsl/000*-COMPOSITE-* | fragment | a reason\n"
            + "transform-grouped-330-undeclared | error-succeeded | old engine threw\n* | * | anything\n"
            + "x-* | error-threw | both threw, differently\n");
        assertEquals(4, entries.size());
        assertTrue(entries.get(0).matches("bsl/00012-COMPOSITE-1a2b3c4d", "fragment"));
        assertFalse(entries.get(0).matches("bsl/00012-COMPOSITE-1a2b3c4d", "vertex"));
        assertTrue(entries.get(1).matches("mini/transform-grouped-330-undeclared", "error-succeeded"));
        // An entry for a replay that succeeds does not accept one that throws, and '*' accepts neither.
        assertFalse(entries.get(1).matches("mini/transform-grouped-330-undeclared", "error-threw"));
        assertTrue(entries.get(2).matches("x/y", "compat"));
        assertFalse(entries.get(2).matches("x/y", "error-succeeded"));
        assertFalse(entries.get(2).matches("x/y", "error-threw"));
        assertTrue(entries.get(3).matches("x-1", "error-threw"));
        assertFalse(entries.get(3).matches("x-1", "error-succeeded"));
        // Step 7b: a recorded output whose replay throws is accepted only by 'threw', never by '*' or an error stage.
        final List<AcceptedDiff> threw = parseAccepted("y-* | threw | TauMC's output would not compile either\n");
        assertTrue(threw.get(0).matches("y-1", "threw"));
        assertFalse(threw.get(0).matches("y-1", "error-threw"));
        assertFalse(entries.get(2).matches("x/y", "threw"));
        assertFalse(entries.get(3).matches("x-1", "threw"));
        // The Step 5 stage 'error' named no outcome; it is gone.
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> parseAccepted("a | error | old engine threw\n"));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> parseAccepted("a | fragments | typo\n"));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> parseAccepted("a | Fragment | wrong case\n"));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> parseAccepted("a | fragment |  \n"));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> parseAccepted("a | fragment\n"));
        // The committed file parses.
        readAccepted();
    }

    /**
     * The stale check (Step 7) on two mini-corpus cases replayed by the glsl-transformer engine: an entry for a case
     * that replays identically is stale, the entry that tolerates a real difference is used, and an entry for a case
     * the run did not replay is not judged.
     */
    @Test
    void acceptedEntriesThatTolerateNothingAreStale(@TempDir Path reports) throws IOException {
        final Path mini = miniCorpus();
        final List<AcceptedDiff> entries = parseAccepted("composite-330 | fragment | dead: this case replays identically\n"
            + "transform-grouped-330-undeclared | error-succeeded | old engine threw; the new engine transforms it\n"
            + "shadow-bounds | fragment | not replayed in this run, so not judged\n");
        final Replayer replayer = new Replayer("douira", false, entries, reports);
        try {
            for (String name : List.of("composite-330", "transform-grouped-330-undeclared")) {
                final Path caseDir = mini.resolve(name);
                final Result result = replayer.replay(name, caseDir,
                    TransformCorpus.readCaseProperties(caseDir.resolve(TransformCorpus.CASE_FILE)));
                assertEquals(name.equals("composite-330") ? Outcome.IDENTICAL : Outcome.ACCEPTED, result.outcome(), result.detail());
            }
        } finally {
            restoreGlobalState();
        }
        assertEquals(List.of(entries.get(0)), replayer.staleEntries());
        assertEquals(2, replayer.inScope.size());
        assertEquals(1, replayer.used.size());
        assertTrue(replayer.used.contains(entries.get(1)));
        assertEquals("accepted.txt:1: composite-330 | fragment | dead: this case replays identically", entries.get(0).source());
    }

    @Test
    void replayCorpus() throws IOException {
        final String dirValue = System.getProperty(CORPUS_DIR_PROPERTY, "").trim();
        final Path corpus = (dirValue.isEmpty() ? miniCorpus() : Paths.get(dirValue)).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(corpus), "corpus directory does not exist: " + corpus);

        final String engine = TransformCorpus.ENGINE;
        final Set<String> patches = parsePatches(System.getProperty(PATCHES_PROPERTY, ""));
        final boolean record = Boolean.parseBoolean(System.getProperty(RECORD_PROPERTY, "false"));
        // The committed snapshot is written only on purpose, with the corpus named explicitly.
        assertFalse(record && dirValue.isEmpty(), "record mode needs -PglslCorpusDir (the default mini-corpus is not "
            + "recorded into)");
        final int threads = Integer.parseInt(System.getProperty(THREADS_PROPERTY, "0").trim().isEmpty() ? "0"
            : System.getProperty(THREADS_PROPERTY, "0").trim());
        final List<AcceptedDiff> accepted = readAccepted();

        final Path reports = Paths.get(System.getProperty("demonica.projectRoot", "."), "build", "reports",
            "transform-replay");
        clearReports(reports);

        final List<Path> cases;
        try (Stream<Path> files = Files.walk(corpus)) {
            cases = files.filter(p -> p.getFileName().toString().equals(TransformCorpus.CASE_FILE))
                .map(Path::getParent)
                .sorted()
                .collect(Collectors.toList());
        }
        assertTrue(!cases.isEmpty(), "no " + TransformCorpus.CASE_FILE + " under " + corpus);

        final Summary summary = new Summary();
        final Replayer replayer = new Replayer(engine, record, accepted, reports);
        for (Path caseDir : cases) {
            final String name = corpus.relativize(caseDir).toString().replace('\\', '/');
            final Map<String, String> properties = TransformCorpus.readCaseProperties(caseDir.resolve(TransformCorpus.CASE_FILE));
            final String patch = properties.getOrDefault("patch", "?");
            if (!patches.isEmpty() && !patches.contains(patch)) {
                summary.filtered++;
                continue;
            }
            summary.add(patch, replayer.replay(name, caseDir, properties));
        }

        final String line = "replay: engine=" + engine + (record ? " (record)" : "") + " corpus=" + corpus
            + " cases=" + summary.cases + " identical=" + summary.identical + " (byte-identical " + summary.byteIdentical
            + ") accepted=" + summary.accepted + " failing=" + summary.failing + " unsupported=" + summary.unsupported
            + " recorded=" + summary.recorded + " filtered=" + summary.filtered;
        System.out.println(line);
        summary.perPatch.forEach((patch, counts) -> System.out.println("replay:   " + patch + " " + counts));
        summary.unsupportedReasons.forEach((reason, count) -> System.out.println("replay:   unsupported " + count + "x: " + reason));
        summary.failures.forEach(failure -> System.out.println("replay:   FAILING " + failure));
        final List<AcceptedDiff> stale = record ? List.of() : replayer.staleEntries();
        final String staleLine = record
            ? "replay: accepted entries not checked for staleness (record mode)"
            : "replay: accepted entries in scope=" + replayer.inScope.size() + " used=" + replayer.used.size()
                + " stale=" + stale.size();
        System.out.println(staleLine);
        summary.timing.add(staleLine);
        stale.forEach(entry -> System.out.println("replay:   STALE " + entry.source()
            + " (its glob matches cases of this run, but it tolerated no difference)"));
        final String timing = "replay: transformMs engine=" + engine + " " + summary.timingText();
        System.out.println(timing);
        summary.timing.add(timing);
        try {
            if (threads > 0 && !record) {
                replayer.concurrently(threads, summary.timing);
            }
        } finally {
            writeSummary(reports, line, summary);
        }

        if (!record) {
            assertEquals(0, summary.failing, "replay failures (diffs under " + reports + "): " + summary.failures);
            assertTrue(stale.isEmpty(), "stale accepted.txt entries (they tolerated no difference): "
                + stale.stream().map(AcceptedDiff::source).toList());
        }
    }

    /** The committed mini-corpus, replayed when no corpus is configured (Step 8). */
    static Path miniCorpus() {
        return Paths.get(System.getProperty("demonica.projectRoot", "."), "src", "test", "resources", "transform-corpus");
    }

    enum Outcome { IDENTICAL, ACCEPTED, FAILING, UNSUPPORTED, RECORDED }

    record Result(Outcome outcome, boolean byteIdentical, String detail, long engineNanos) {
        Result(Outcome outcome, boolean byteIdentical, String detail) {
            this(outcome, byteIdentical, detail, -1);
        }

        static Result unsupported(String reason) {
            return new Result(Outcome.UNSUPPORTED, false, reason);
        }

        Result timed(long nanos) {
            return new Result(outcome, byteIdentical, detail, nanos);
        }
    }

    /** A case the engine transformed during the replay, for the concurrent pass: what it needs, and what it gave. */
    record Job(String name, Patch patch, Map<String, String> properties, EnumMap<PatchShaderType, String> inputs,
               Map<PatchShaderType, String> output) {
    }

    /** Replays one case at a time, restoring the global state each case needs. */
    static final class Replayer {
        private final String engine;
        private final boolean record;
        private final List<AcceptedDiff> accepted;
        private final Path reports;
        private String capabilityState;
        private final List<Job> jobs = new ArrayList<>();
        /** The entries whose case glob matched a case this run replayed (not unsupported), for the stale check. */
        final Set<AcceptedDiff> inScope = Collections.newSetFromMap(new IdentityHashMap<>());
        /** The entries that tolerated a difference in this run. */
        final Set<AcceptedDiff> used = Collections.newSetFromMap(new IdentityHashMap<>());

        Replayer(String engine, boolean record, List<AcceptedDiff> accepted, Path reports) {
            this.engine = engine;
            this.record = record;
            this.accepted = accepted;
            this.reports = reports;
        }

        Result replay(String name, Path caseDir, Map<String, String> properties) throws IOException {
            final String domain = properties.getOrDefault("domain", "iris");
            final Result result = switch (domain) {
                case "iris" -> replayIris(name, caseDir, properties);
                case "compat" -> replayCompat(name, caseDir, properties);
                default -> Result.unsupported("unknown domain '" + domain + "'");
            };
            if (result.outcome() != Outcome.UNSUPPORTED) {
                for (AcceptedDiff entry : accepted) {
                    if (entry.matchesCase(name)) {
                        inScope.add(entry);
                    }
                }
            }
            return result;
        }

        /**
         * The entries that are in scope (their glob matched a case this replayer replayed) and tolerated nothing, in
         * file order: dead entries (class javadoc).
         */
        List<AcceptedDiff> staleEntries() {
            return accepted.stream().filter(entry -> inScope.contains(entry) && !used.contains(entry)).toList();
        }

        private Result replayIris(String name, Path caseDir, Map<String, String> p) throws IOException {
            final Patch patch;
            try {
                patch = Patch.valueOf(p.get("patch"));
            } catch (IllegalArgumentException | NullPointerException e) {
                return Result.unsupported("unknown patch '" + p.get("patch") + "'");
            }
            final String restored = restoreCapability(p);
            if (restored != null) {
                return Result.unsupported(restored);
            }
            final boolean instrumentation = Boolean.parseBoolean(p.getOrDefault("shadowBounds.instrumentation", "false"));
            AdaptiveShadowBoundsStats.activateForTesting(instrumentation
                ? Integer.parseInt(p.getOrDefault("shadowBounds.binding", "-1")) : -1);

            final Parameters parameters = parameters(patch, p);
            if (parameters == null) {
                return Result.unsupported("unknown parameters '" + p.get("parameters") + "'");
            }
            final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
            for (PatchShaderType stage : PatchShaderType.VALUES) {
                final Path input = caseDir.resolve("in." + TransformCorpusRecorder.stageName(stage) + ".glsl");
                if (Files.isRegularFile(input)) {
                    inputs.put(stage, Files.readString(input, StandardCharsets.UTF_8));
                }
            }

            final boolean recordedError = "error".equals(p.get("outcome"));
            Map<PatchShaderType, String> output;
            final long start = System.nanoTime();
            try {
                output = runEngine(patch, inputs, parameters);
            } catch (RuntimeException e) {
                return engineFailed(name, p.get("error"), e);
            } finally {
                AdaptiveShadowBoundsStats.activateForTesting(-1);
            }
            final long nanos = System.nanoTime() - start;

            final Map<String, String> actual = new LinkedHashMap<>();
            if (output != null) {
                output.forEach((stage, text) -> actual.put(TransformCorpusRecorder.stageName(stage), text));
            }
            if (recordedError && !record) {
                return outcomeDiffers(name, ERROR_SUCCEEDED, "recorded the error '" + p.get("error") + "', the replay succeeded",
                    actual).timed(nanos);
            }
            final Result result = compare(name, caseDir, actual).timed(nanos);
            if (output != null && (result.outcome() == Outcome.IDENTICAL || result.outcome() == Outcome.ACCEPTED)) {
                jobs.add(new Job(name, patch, p, inputs, output));
            }
            return result;
        }

        private Result replayCompat(String name, Path caseDir, Map<String, String> p) throws IOException {
            final int minGlsl = Integer.parseInt(p.getOrDefault("minGlslVersion", "330"));
            final int replayMinGlsl = BackendManager.RENDER_BACKEND.getMinGLSLVersion();
            if (minGlsl != replayMinGlsl) {
                return Result.unsupported("compat minGlslVersion " + minGlsl + " (replay has " + replayMinGlsl + ")");
            }
            final String input = Files.readString(caseDir.resolve("in.glsl"), StandardCharsets.UTF_8);
            final boolean isFragment = Boolean.parseBoolean(p.get("isFragment"));
            CompatShaderTransformer.clearCache();
            final String output = CompatShaderTransformer.transform(input, isFragment);
            final Map<String, String> actual = new LinkedHashMap<>();
            actual.put("", output);
            return compare(name, caseDir, actual);
        }

        /** The engine threw {@code e}; the case recorded {@code recordedError} (null when it succeeded). */
        private Result engineFailed(String name, String recordedError, RuntimeException e) throws IOException {
            final String error = e.getClass().getName() + ": " + e.getMessage();
            if (error.equals(recordedError)) {
                return new Result(Outcome.IDENTICAL, true, name + ": failed as recorded");
            }
            if (recordedError != null) {
                return outcomeDiffers(name, ERROR_THREW, "recorded the error '" + recordedError + "', the replay threw '"
                    + error + "'", Map.of());
            }
            final String frames = Arrays.stream(e.getStackTrace()).limit(4).map(String::valueOf)
                .collect(Collectors.joining(" < "));
            // A recorded output whose replay throws: only an entry naming the stage 'threw' accepts it (Step 7b).
            return outcomeDiffers(name, THREW, "recorded an output, the replay threw " + e + " at " + frames, Map.of());
        }

        /**
         * The case's outcome differs from the recorded one (recorded {@code outcome=error} and the replay did not throw
         * the same, or recorded an output and the replay threw): writes {@code <case>.error.diff} (the difference and
         * the replay's output, if any) and looks for an {@code accepted.txt} entry with the stage {@code kind}:
         * {@link #ERROR_SUCCEEDED}, {@link #ERROR_THREW} or {@link #THREW}.
         */
        private Result outcomeDiffers(String name, String kind, String detail, Map<String, String> actual) throws IOException {
            final StringBuilder report = new StringBuilder("# " + name + " error: " + detail + "\n");
            actual.forEach((stage, text) -> report.append("# ").append(engine).append(' ').append(stage.isEmpty() ? "compat" : stage)
                .append(" output:\n").append(text).append(text.endsWith("\n") ? "" : "\n"));
            Files.createDirectories(reports);
            Files.writeString(reports.resolve(name.replace('/', '_') + ".error.diff"), report, StandardCharsets.UTF_8);
            final AcceptedDiff tolerated = accepted.stream().filter(a -> a.matches(name, kind)).findFirst().orElse(null);
            if (tolerated != null) {
                used.add(tolerated);
            }
            return new Result(tolerated != null ? Outcome.ACCEPTED : Outcome.FAILING, false, name + " [" + kind + "]: " + detail);
        }

        /**
         * Transforms every case the replay transformed successfully again: first once more on this thread (warm), then
         * all at once on {@code threads} threads, grouped by the global state they need (capability, hoisting, the
         * adaptive-shadow-bounds instrumentation), which is set once per group. Every concurrent output must equal the
         * replayed one; a difference fails the test. The summary lines are printed and added to {@code summaryLines}.
         */
        void concurrently(int threads, List<String> summaryLines) {
            final Map<String, List<Job>> groups = new LinkedHashMap<>();
            for (Job job : jobs) {
                final Map<String, String> p = job.properties();
                final String key = p.getOrDefault("glsl.maxVersion", "460") + "|" + p.getOrDefault("glsl.ssbo", "false") + "|"
                    + p.getOrDefault("glsl.imageLoadStore", "false") + "|" + p.getOrDefault("versionHoisting", "none") + "|"
                    + p.getOrDefault("shadowBounds.instrumentation", "false") + "|" + p.getOrDefault("shadowBounds.binding", "-1");
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(job);
            }
            long sequentialNanos = 0;
            long concurrentWallNanos = 0;
            final java.util.concurrent.atomic.AtomicLong concurrentCallNanos = new java.util.concurrent.atomic.AtomicLong();
            final List<String> mismatches = java.util.Collections.synchronizedList(new ArrayList<>());
            for (List<Job> group : groups.values()) {
                final Map<String, String> p = group.getFirst().properties();
                assertEquals(null, restoreCapability(p), "concurrent pass: capability");
                final boolean instrumentation = Boolean.parseBoolean(p.getOrDefault("shadowBounds.instrumentation", "false"));
                AdaptiveShadowBoundsStats.activateForTesting(instrumentation
                    ? Integer.parseInt(p.getOrDefault("shadowBounds.binding", "-1")) : -1);
                try {
                    for (Job job : group) {
                        final long start = System.nanoTime();
                        runEngine(job.patch(), job.inputs(), parameters(job.patch(), job.properties()));
                        sequentialNanos += System.nanoTime() - start;
                    }
                    final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
                    try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads)) {
                        final List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                        for (Job job : group) {
                            futures.add(pool.submit(() -> {
                                go.await();
                                final long start = System.nanoTime();
                                final Map<PatchShaderType, String> output = runEngine(job.patch(), job.inputs(),
                                    parameters(job.patch(), job.properties()));
                                concurrentCallNanos.addAndGet(System.nanoTime() - start);
                                if (!job.output().equals(output)) {
                                    mismatches.add(job.name());
                                }
                                return null;
                            }));
                        }
                        final long start = System.nanoTime();
                        go.countDown();
                        for (java.util.concurrent.Future<?> future : futures) {
                            try {
                                future.get(120, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (Exception e) {
                                mismatches.add("thrown: " + e);
                            }
                        }
                        concurrentWallNanos += System.nanoTime() - start;
                    }
                } finally {
                    AdaptiveShadowBoundsStats.activateForTesting(-1);
                }
            }
            final List<String> lines = new ArrayList<>();
            lines.add(String.format(Locale.ROOT, "replay: concurrent engine=%s threads=%d cases=%d groups=%d sequentialMs=%.1f"
                    + " concurrentWallMs=%.1f concurrentCallMs=%.1f differing=%d", engine, threads, jobs.size(), groups.size(),
                sequentialNanos / 1e6, concurrentWallNanos / 1e6, concurrentCallNanos.get() / 1e6, mismatches.size()));
            mismatches.forEach(m -> lines.add("replay:   CONCURRENT DIFFERS " + m));
            summaryLines.addAll(lines);
            lines.forEach(System.out::println);
            assertTrue(mismatches.isEmpty(), "concurrent replay differs from the sequential one: " + mismatches);
        }

        /**
         * Compares or records each stage. {@code actual} maps a stage name ({@code ""} for the compat domain's single
         * output) to the engine's output. A differing stage is accepted by the first {@code accepted.txt} entry that
         * matches it, which counts as used.
         */
        private Result compare(String name, Path caseDir, Map<String, String> actual) throws IOException {
            if (record) {
                for (Map.Entry<String, String> stage : actual.entrySet()) {
                    Files.writeString(caseDir.resolve(outputName(engine, stage.getKey())), stage.getValue(),
                        StandardCharsets.UTF_8);
                }
                return new Result(Outcome.RECORDED, false, name);
            }

            final Set<String> stages = new LinkedHashSet<>(actual.keySet());
            final Map<String, String> expected = new LinkedHashMap<>();
            try (Stream<Path> files = Files.list(caseDir)) {
                final Pattern reference = Pattern.compile("out\\." + REFERENCE_ENGINE + "(?:\\.(\\w+))?\\.glsl");
                for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                    final var matcher = reference.matcher(file.getFileName().toString());
                    if (matcher.matches()) {
                        final String stage = matcher.group(1) == null ? "" : matcher.group(1);
                        expected.put(stage, Files.readString(file, StandardCharsets.UTF_8));
                        stages.add(stage);
                    }
                }
            }
            if (expected.isEmpty()) {
                return new Result(Outcome.FAILING, false, name + ": no " + REFERENCE_ENGINE + " output recorded");
            }

            boolean byteIdentical = true;
            boolean anyAccepted = false;
            final List<String> failing = new ArrayList<>();
            for (String stage : stages) {
                final String want = expected.get(stage);
                final String got = actual.get(stage);
                if (want != null && want.equals(got)) {
                    continue;
                }
                byteIdentical = false;
                final String diff = want == null ? "+ (stage only in the replay)\n"
                    : got == null ? "- (stage missing from the replay)\n"
                    : GlslTokens.diff(GlslTokens.of(want), GlslTokens.of(got));
                if (diff.isEmpty()) {
                    continue;
                }
                final String stageLabel = stage.isEmpty() ? "compat" : stage;
                final String reportName = name.replace('/', '_') + "." + stageLabel + ".diff";
                Files.createDirectories(reports);
                Files.writeString(reports.resolve(reportName), "# " + name + " " + stageLabel + ": "
                    + REFERENCE_ENGINE + " (-) against " + engine + " (+)\n" + diff, StandardCharsets.UTF_8);
                final AcceptedDiff tolerated = accepted.stream().filter(a -> a.matches(name, stageLabel)).findFirst()
                    .orElse(null);
                if (tolerated != null) {
                    used.add(tolerated);
                    anyAccepted = true;
                } else {
                    failing.add(stageLabel);
                }
            }
            if (!failing.isEmpty()) {
                return new Result(Outcome.FAILING, false, name + " " + failing);
            }
            return new Result(anyAccepted ? Outcome.ACCEPTED : Outcome.IDENTICAL, byteIdentical, name);
        }

        private Map<PatchShaderType, String> runEngine(Patch patch, EnumMap<PatchShaderType, String> in,
                                                       Parameters parameters) {
            if (patch == Patch.COMPUTE) {
                final String compute = in.get(PatchShaderType.COMPUTE);
                return ShaderTransformer.transformCompute(compute, parameters);
            }
            final String vertex = in.get(PatchShaderType.VERTEX);
            final String geometry = in.get(PatchShaderType.GEOMETRY);
            final String tessControl = in.get(PatchShaderType.TESS_CONTROL);
            final String tessEval = in.get(PatchShaderType.TESS_EVAL);
            final String fragment = in.get(PatchShaderType.FRAGMENT);
            return ShaderTransformer.transform(vertex, geometry, tessControl, tessEval, fragment, parameters);
        }

        /** Restores the GLSL capability and version hoisting; returns why it cannot, or null. */
        private String restoreCapability(Map<String, String> p) {
            final int maxVersion = Integer.parseInt(p.getOrDefault("glsl.maxVersion", "460"));
            final boolean ssbo = Boolean.parseBoolean(p.getOrDefault("glsl.ssbo", "false"));
            final boolean imageLoadStore = Boolean.parseBoolean(p.getOrDefault("glsl.imageLoadStore", "false"));
            final String hoisting = p.getOrDefault("versionHoisting", "none");
            final String state = maxVersion + "|" + ssbo + "|" + imageLoadStore + "|" + hoisting;
            if (!state.equals(capabilityState)) {
                RenderSystem.initializeGlslCapabilityForTesting(maxVersion, ssbo, imageLoadStore);
                VersionNegotiation.resetForTesting();
                if (!hoisting.equals("none")) {
                    VersionNegotiation.init();
                }
                capabilityState = state;
            }
            final String replayHoisting = VersionNegotiation.versionHoistingState();
            if (!replayHoisting.equals(hoisting)) {
                capabilityState = null;
                return "version hoisting '" + hoisting + "' cannot be restored (the capability gives '" + replayHoisting + "')";
            }
            return null;
        }

        private static Parameters parameters(Patch patch, Map<String, String> p) {
            final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = textureMap(p);
            final String stageName = p.get("textureStage");
            final TextureStage stage = stageName == null ? null : TextureStage.valueOf(stageName);
            return switch (p.getOrDefault("parameters", "")) {
                case "AttributeParameters" -> new AttributeParameters(patch,
                    Boolean.parseBoolean(p.get("hasGeometry")),
                    new InputAvailability(Boolean.parseBoolean(p.get("inputs.texture")),
                        Boolean.parseBoolean(p.get("inputs.lightmap")), Boolean.parseBoolean(p.get("inputs.color"))));
                case "CeleritasTerrainParameters" -> new CeleritasTerrainParameters(patch);
                case "TextureStageParameters" -> new TextureStageParameters(patch, stage, textureMap);
                case "ComputeParameters" -> new ComputeParameters(patch, stage, textureMap);
                case "DHParameters" -> new DHParameters(patch, textureMap);
                default -> null;
            };
        }

        /** {@code textureMap=null} or a count, then {@code textureMap.N=name|type|stage=replacement}, in order. */
        private static Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap(Map<String, String> p) {
            final String count = p.getOrDefault("textureMap", "null");
            if (count.equals("null")) {
                return null;
            }
            final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> map = new Object2ObjectLinkedOpenHashMap<>();
            for (int i = 0; i < Integer.parseInt(count); i++) {
                final String entry = p.get("textureMap." + i);
                final int equals = entry.indexOf('=');
                final String[] key = entry.substring(0, equals).split("\\|", -1);
                map.put(new Tri<>(key[0], TextureType.valueOf(key[1]), TextureStage.valueOf(key[2])),
                    entry.substring(equals + 1));
            }
            return map;
        }
    }

    static String outputName(String engine, String stage) {
        return stage.isEmpty() ? "out." + engine + ".glsl" : "out." + engine + "." + stage + ".glsl";
    }

    /** The stage of a case recorded as an error whose replay transformed it. */
    static final String ERROR_SUCCEEDED = "error-succeeded";
    /** The stage of a case recorded as an error whose replay threw something else. */
    static final String ERROR_THREW = "error-threw";
    /**
     * The stage of a case recorded with an output whose replay throws (Step 7b: a TauMC output that no compiler would
     * accept, where the glsl-transformer engine throws a syntax error instead).
     */
    static final String THREW = "threw";

    /**
     * The stages an accepted.txt entry may name: the Iris stages, {@code compat} (GLSM's cases),
     * {@link #ERROR_SUCCEEDED} and {@link #ERROR_THREW} (a case recorded as an error whose replay succeeded, or threw
     * something else), {@link #THREW} (a case recorded with an output whose replay threw) and {@code *} (any output
     * stage; not the three outcomes, which an entry must name).
     */
    static final Set<String> ACCEPTED_STAGES = Set.of("vertex", "geometry", "tess_control", "tess_eval", "fragment",
        "compute", "compat", ERROR_SUCCEEDED, ERROR_THREW, THREW, "*");

    /**
     * One line of accepted.txt: {@code <case glob> | <stage> | <reason>}; {@code source} is the line with its number
     * ({@code accepted.txt:42: ...}), for the messages.
     */
    record AcceptedDiff(Pattern caseGlob, String stage, String reason, String source) {
        boolean matches(String caseName, String stageName) {
            final boolean errorOutcome = stageName.equals(ERROR_SUCCEEDED) || stageName.equals(ERROR_THREW)
                || stageName.equals(THREW);
            return ((stage.equals("*") && !errorOutcome) || stage.equals(stageName)) && matchesCase(caseName);
        }

        /** Whether the glob matches the case's path under the corpus, or its directory name alone. */
        boolean matchesCase(String caseName) {
            final String leaf = caseName.substring(caseName.lastIndexOf('/') + 1);
            return caseGlob.matcher(caseName).matches() || caseGlob.matcher(leaf).matches();
        }
    }

    static List<AcceptedDiff> readAccepted() throws IOException {
        try (InputStream in = TransformCorpusReplayTest.class.getResourceAsStream(ACCEPTED_RESOURCE)) {
            if (in == null) {
                fail("missing test resource " + ACCEPTED_RESOURCE);
            }
            return parseAccepted(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** The entries of an accepted.txt text; a malformed line (no reason, an unknown stage) fails the test. */
    static List<AcceptedDiff> parseAccepted(String text) {
        final List<AcceptedDiff> entries = new ArrayList<>();
        final String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final String[] parts = line.split("\\|", 3);
            if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                fail(ACCEPTED_RESOURCE + ":" + (i + 1) + ": expected '<case glob> | <stage> | <reason>', got: " + line);
            }
            final String stage = parts[1].strip();
            if (!ACCEPTED_STAGES.contains(stage)) {
                fail(ACCEPTED_RESOURCE + ":" + (i + 1) + ": unknown stage '" + stage + "' (one of " + ACCEPTED_STAGES
                    + "), in: " + line);
            }
            entries.add(new AcceptedDiff(glob(parts[0].strip()), stage, parts[2].strip(),
                "accepted.txt:" + (i + 1) + ": " + line));
        }
        return entries;
    }

    /** A glob over case names: {@code **} any characters, {@code *} any but {@code /}, {@code ?} one of those. */
    static Pattern glob(String glob) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            final char ch = glob.charAt(i);
            if (ch == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (ch == '*') {
                regex.append("[^/]*");
            } else if (ch == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static Set<String> parsePatches(String value) {
        return Arrays.stream(value.split(","))
            .map(s -> s.strip().toUpperCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void clearReports(Path reports) throws IOException {
        if (!Files.isDirectory(reports)) {
            return;
        }
        try (Stream<Path> files = Files.list(reports)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                final String fileName = file.getFileName().toString();
                if (fileName.endsWith(".diff") || fileName.equals("summary.txt")) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void writeSummary(Path reports, String line, Summary summary) throws IOException {
        Files.createDirectories(reports);
        final StringBuilder out = new StringBuilder(line).append('\n');
        summary.perPatch.forEach((patch, counts) -> out.append(patch).append(' ').append(counts).append('\n'));
        summary.unsupportedReasons.forEach((reason, count) -> out.append("unsupported ").append(count).append("x: ")
            .append(reason).append('\n'));
        summary.failures.forEach(failure -> out.append("FAILING ").append(failure).append('\n'));
        summary.timing.forEach(timing -> out.append(timing).append('\n'));
        Files.writeString(reports.resolve("summary.txt"), out.toString(), StandardCharsets.UTF_8);
    }

    static final class Summary {
        /** The engine time per patch kind: {@code total=<ms> COMPOSITE=<ms>/<calls> ...}. */
        String timingText() {
            long all = 0;
            final StringBuilder text = new StringBuilder();
            for (Map.Entry<String, long[]> entry : nanosPerPatch.entrySet()) {
                all += entry.getValue()[0];
                text.append(String.format(Locale.ROOT, " %s=%.1f/%d", entry.getKey(), entry.getValue()[0] / 1e6, entry.getValue()[1]));
            }
            return String.format(Locale.ROOT, "total=%.1f", all / 1e6) + text;
        }

        int cases;
        int identical;
        int byteIdentical;
        int accepted;
        int failing;
        int unsupported;
        int recorded;
        int filtered;
        final Map<String, Map<Outcome, Integer>> perPatch = new TreeMap<>();
        final Map<String, long[]> nanosPerPatch = new TreeMap<>();
        final List<String> timing = new ArrayList<>();
        final Map<String, Integer> unsupportedReasons = new TreeMap<>();
        final List<String> failures = new ArrayList<>();

        void add(String patch, Result result) {
            cases++;
            if (result.engineNanos() >= 0) {
                final long[] total = nanosPerPatch.computeIfAbsent(patch, k -> new long[2]);
                total[0] += result.engineNanos();
                total[1]++;
            }
            perPatch.computeIfAbsent(patch, k -> new EnumMap<>(Outcome.class)).merge(result.outcome(), 1, Integer::sum);
            switch (result.outcome()) {
                case IDENTICAL -> {
                    identical++;
                    if (result.byteIdentical()) byteIdentical++;
                }
                case ACCEPTED -> accepted++;
                case FAILING -> {
                    failing++;
                    failures.add(result.detail());
                }
                case UNSUPPORTED -> {
                    unsupported++;
                    unsupportedReasons.merge(result.detail(), 1, Integer::sum);
                }
                case RECORDED -> recorded++;
            }
        }
    }
}
