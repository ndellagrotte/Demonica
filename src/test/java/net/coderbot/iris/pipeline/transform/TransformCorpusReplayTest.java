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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Replays a recorded transform corpus through one engine and compares every stage with the recorded TauMC output
 * ({@code out.taumc.<stage>.glsl}) as {@link GlslTokens} (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, 3.5).
 *
 * <p>Skipped unless a corpus is configured. The root {@code test {}} block forwards the Gradle properties:
 * {@code -PglslCorpusDir=<abs>} (searched recursively for {@code case.properties}), {@code -PglslReplayEngine=taumc|douira}
 * (default: the engine {@code demonica.glsl.engine} selects), {@code -PglslReplayPatches=COMPOSITE,COMPUTE,...}
 * (patch kinds to replay, {@code COMPAT} for GLSM's mod-shader cases; default all) and {@code -PglslReplayRecord=true}
 * (write {@code out.<engine>.<stage>.glsl} instead of comparing).</p>
 *
 * <p>Each case restores what the output depends on besides the sources: the GLSL capability
 * ({@link RenderSystem#initializeGlslCapabilityForTesting(int, boolean, boolean)}), version hoisting
 * ({@link ShaderTransformer#init()} or its reset), the adaptive-shadow-bounds instrumentation
 * ({@link AdaptiveShadowBoundsStats#activateForTesting(int)}) and the {@link Parameters}; then it calls the engine
 * directly, not through the cache. A stage that differs is written to {@code build/reports/transform-replay/} and
 * fails the test unless {@code src/test/resources/transform-replay/accepted.txt} tolerates it. A case the engine cannot
 * run (a patch kind not ported yet, a state the replayer cannot restore) is counted as unsupported.</p>
 */
class TransformCorpusReplayTest {
    static final String CORPUS_DIR_PROPERTY = "demonica.glsl.corpus.dir";
    static final String ENGINE_PROPERTY = "demonica.glsl.replay.engine";
    static final String PATCHES_PROPERTY = "demonica.glsl.replay.patches";
    static final String RECORD_PROPERTY = "demonica.glsl.replay.record";
    static final String REFERENCE_ENGINE = "taumc";
    private static final String ACCEPTED_RESOURCE = "/transform-replay/accepted.txt";

    @AfterAll
    static void restoreGlobalState() {
        AdaptiveShadowBoundsStats.activateForTesting(-1);
        ShaderTransformer.resetVersionHoistingForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    @Test
    void replayCorpus() throws IOException {
        final String dirValue = System.getProperty(CORPUS_DIR_PROPERTY, "").trim();
        assumeFalse(dirValue.isEmpty(), "no transform corpus configured (-PglslCorpusDir)");
        final Path corpus = Paths.get(dirValue).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(corpus), "corpus directory does not exist: " + corpus);

        final String engineValue = System.getProperty(ENGINE_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
        final String engine = engineValue.isEmpty() ? TransformPatcher.engine().id : engineValue;
        assertTrue(engine.equals("taumc") || engine.equals("douira"), "unknown replay engine: " + engine);
        final Set<String> patches = parsePatches(System.getProperty(PATCHES_PROPERTY, ""));
        final boolean record = Boolean.parseBoolean(System.getProperty(RECORD_PROPERTY, "false"));
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
        writeSummary(reports, line, summary);

        if (!record) {
            assertEquals(0, summary.failing, "replay failures (diffs under " + reports + "): " + summary.failures);
        }
    }

    enum Outcome { IDENTICAL, ACCEPTED, FAILING, UNSUPPORTED, RECORDED }

    record Result(Outcome outcome, boolean byteIdentical, String detail) {
        static Result unsupported(String reason) {
            return new Result(Outcome.UNSUPPORTED, false, reason);
        }
    }

    /** Replays one case at a time, restoring the global state each case needs. */
    static final class Replayer {
        private final String engine;
        private final boolean record;
        private final List<AcceptedDiff> accepted;
        private final Path reports;
        private String capabilityState;

        Replayer(String engine, boolean record, List<AcceptedDiff> accepted, Path reports) {
            this.engine = engine;
            this.record = record;
            this.accepted = accepted;
            this.reports = reports;
        }

        Result replay(String name, Path caseDir, Map<String, String> properties) throws IOException {
            final String domain = properties.getOrDefault("domain", "iris");
            return switch (domain) {
                case "iris" -> replayIris(name, caseDir, properties);
                case "compat" -> replayCompat(name, caseDir, properties);
                default -> Result.unsupported("unknown domain '" + domain + "'");
            };
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
            try {
                output = runEngine(patch, inputs, parameters);
            } catch (UnsupportedOperationException e) {
                if (String.valueOf(e.getMessage()).contains("not ported yet")) {
                    return Result.unsupported(e.getMessage());
                }
                return engineFailed(name, p.get("error"), e);
            } catch (RuntimeException e) {
                return engineFailed(name, p.get("error"), e);
            } finally {
                AdaptiveShadowBoundsStats.activateForTesting(-1);
            }
            if (recordedError) {
                return new Result(Outcome.FAILING, false, name + ": recorded an error (" + p.get("error")
                    + ") but the replay succeeded");
            }

            final Map<String, String> actual = new LinkedHashMap<>();
            if (output != null) {
                output.forEach((stage, text) -> actual.put(TransformCorpusRecorder.stageName(stage), text));
            }
            return compare(name, caseDir, actual);
        }

        private Result replayCompat(String name, Path caseDir, Map<String, String> p) throws IOException {
            if (!engine.equals(REFERENCE_ENGINE)) {
                // CompatShaderTransformer gets its engine switch in Step 10.
                return Result.unsupported("compat on " + engine + ": CompatShaderTransformer has no engine switch yet");
            }
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
        private Result engineFailed(String name, String recordedError, RuntimeException e) {
            final String error = e.getClass().getName() + ": " + e.getMessage();
            if (error.equals(recordedError)) {
                return new Result(Outcome.IDENTICAL, true, name + ": failed as recorded");
            }
            if (recordedError != null) {
                return new Result(Outcome.FAILING, false, name + ": recorded the error '" + recordedError
                    + "', the replay threw '" + error + "'");
            }
            final String frames = Arrays.stream(e.getStackTrace()).limit(4).map(String::valueOf)
                .collect(Collectors.joining(" < "));
            return new Result(Outcome.FAILING, false, name + ": the engine threw " + e + " at " + frames);
        }

        /**
         * Compares or records each stage. {@code actual} maps a stage name ({@code ""} for the compat domain's single
         * output) to the engine's output.
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
                return engine.equals(REFERENCE_ENGINE)
                    ? ShaderTransformer.transformCompute(compute, parameters)
                    : AstShaderTransformer.transformCompute(compute, parameters);
            }
            final String vertex = in.get(PatchShaderType.VERTEX);
            final String geometry = in.get(PatchShaderType.GEOMETRY);
            final String tessControl = in.get(PatchShaderType.TESS_CONTROL);
            final String tessEval = in.get(PatchShaderType.TESS_EVAL);
            final String fragment = in.get(PatchShaderType.FRAGMENT);
            return engine.equals(REFERENCE_ENGINE)
                ? ShaderTransformer.transform(vertex, geometry, tessControl, tessEval, fragment, parameters)
                : AstShaderTransformer.transform(vertex, geometry, tessControl, tessEval, fragment, parameters);
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
                ShaderTransformer.resetVersionHoistingForTesting();
                if (!hoisting.equals("none")) {
                    ShaderTransformer.init();
                }
                capabilityState = state;
            }
            final String replayHoisting = ShaderTransformer.versionHoistingState();
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

    /** One line of accepted.txt: {@code <case glob> | <stage> | <reason>}. */
    record AcceptedDiff(Pattern caseGlob, String stage, String reason) {
        boolean matches(String caseName, String stageName) {
            final String leaf = caseName.substring(caseName.lastIndexOf('/') + 1);
            return (stage.equals("*") || stage.equals(stageName))
                && (caseGlob.matcher(caseName).matches() || caseGlob.matcher(leaf).matches());
        }
    }

    static List<AcceptedDiff> readAccepted() throws IOException {
        final List<AcceptedDiff> entries = new ArrayList<>();
        try (InputStream in = TransformCorpusReplayTest.class.getResourceAsStream(ACCEPTED_RESOURCE)) {
            if (in == null) {
                fail("missing test resource " + ACCEPTED_RESOURCE);
            }
            final String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                final String line = lines[i].strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split("\\|", 3);
                if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                    fail(ACCEPTED_RESOURCE + ":" + (i + 1) + ": expected '<case glob> | <stage> | <reason>', got: " + line);
                }
                entries.add(new AcceptedDiff(glob(parts[0].strip()), parts[1].strip(), parts[2].strip()));
            }
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
        Files.writeString(reports.resolve("summary.txt"), out.toString(), StandardCharsets.UTF_8);
    }

    static final class Summary {
        int cases;
        int identical;
        int byteIdentical;
        int accepted;
        int failing;
        int unsupported;
        int recorded;
        int filtered;
        final Map<String, Map<Outcome, Integer>> perPatch = new TreeMap<>();
        final Map<String, Integer> unsupportedReasons = new TreeMap<>();
        final List<String> failures = new ArrayList<>();

        void add(String patch, Result result) {
            cases++;
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
