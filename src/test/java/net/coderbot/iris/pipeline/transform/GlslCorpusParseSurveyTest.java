package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.debug.TransformCorpus;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.parser.ParsingException;
import net.coderbot.iris.gl.shader.ShaderType;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Parses every recorded input of a transform corpus with glsl-transformer, in the reference configuration
 * ({@link GlslTransformerSpikeTest#newTransformer}), and reports what fails (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
 * Step 2, the GLSL 120 survey of the risk register). It asserts nothing about the inputs; it only reports.
 *
 * <p>Two passes per Iris input: <b>raw</b>, the input as recorded with the lexer at its own {@code #version}; and
 * <b>prepared</b>, the text the TauMC engine actually parses (version hoisting, the stage minimum, then
 * {@code replaceTexture}, {@code renameReservedWords}, {@code fixupQualifiers} and the cloud patches, in
 * {@code ShaderTransformer}'s order) with its {@code #version} line rewritten to the effective version, which is what
 * the new engine will parse. GLSM's compat inputs get the raw pass only: that path evaluates the preprocessor first.</p>
 *
 * <p>Skipped unless {@code -PglslCorpusDir} is set. Each input gets a fresh transformer: with the two-tier parsing
 * cache, the dropping filter sees tokens only on a cache miss.</p>
 */
class GlslCorpusParseSurveyTest {
    private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)(?:[ \\t]+(\\w+))?");
    private static final Pattern DIRECTIVE_NAME = Pattern.compile("#\\s*(\\w+)");

    @AfterAll
    static void restoreGlobalState() {
        ShaderTransformer.resetVersionHoistingForTesting();
        RenderSystem.initializeGlslCapabilityForTesting(460, false, false);
    }

    @Test
    void surveyCorpus() throws Exception {
        final String dirValue = System.getProperty(TransformCorpusReplayTest.CORPUS_DIR_PROPERTY, "").trim();
        assumeFalse(dirValue.isEmpty(), "no transform corpus configured (-PglslCorpusDir)");
        final Path corpus = Paths.get(dirValue).toAbsolutePath().normalize();

        final List<Path> cases;
        try (Stream<Path> files = Files.walk(corpus)) {
            cases = files.filter(p -> p.getFileName().toString().equals(TransformCorpus.CASE_FILE))
                .map(Path::getParent).sorted().collect(Collectors.toList());
        }
        assertTrue(!cases.isEmpty(), "no " + TransformCorpus.CASE_FILE + " under " + corpus);

        final Method requiredVersion = ShaderTransformer.class.getDeclaredMethod("getRequiredVersion", String.class, int.class);
        requiredVersion.setAccessible(true);

        final Tally raw = new Tally();
        final Tally prepared = new Tally();
        final List<String> report = new ArrayList<>();
        for (Path caseDir : cases) {
            final String name = corpus.relativize(caseDir).toString().replace('\\', '/');
            final Map<String, String> p = TransformCorpus.readCaseProperties(caseDir.resolve(TransformCorpus.CASE_FILE));
            final boolean compat = "compat".equals(p.get("domain"));
            if (compat) {
                final boolean fragment = Boolean.parseBoolean(p.get("isFragment"));
                final String source = Files.readString(caseDir.resolve("in.glsl"), StandardCharsets.UTF_8);
                raw.add(name + " compat", parse(fragment ? PatchShaderType.FRAGMENT : PatchShaderType.VERTEX, source));
                continue;
            }
            restoreHoisting(p);
            final Patch patch = Patch.valueOf(p.get("patch"));
            for (PatchShaderType stage : PatchShaderType.VALUES) {
                final Path input = caseDir.resolve("in." + stage.name().toLowerCase(Locale.ROOT) + ".glsl");
                if (!Files.isRegularFile(input)) {
                    continue;
                }
                final String source = Files.readString(input, StandardCharsets.UTF_8);
                final String label = name + " " + stage.name().toLowerCase(Locale.ROOT);
                raw.add(label, parse(stage, source));
                prepared.add(label, parse(stage, prepare(source, stage, patch, p, requiredVersion)));
            }
        }

        report.add("parse-survey: corpus=" + corpus + " cases=" + cases.size());
        raw.describe("raw", report);
        prepared.describe("prepared", report);
        report.forEach(System.out::println);
        final Path out = Paths.get(System.getProperty("demonica.projectRoot", "."), "build", "reports",
            "glsl-parse-survey.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n", report) + "\n", StandardCharsets.UTF_8);
    }

    /** The text ShaderTransformer hands TauMC's parser, with its {@code #version} line set to the effective version. */
    private static String prepare(String source, PatchShaderType stage, Patch patch, Map<String, String> p,
                                  Method requiredVersion) throws Exception {
        final Matcher version = VERSION.matcher(source);
        if (!version.find()) {
            return source;
        }
        int versionInt = Integer.parseInt(version.group(1));
        String scan = source;
        // The Celeritas header joins the scan for CELERITAS_TERRAIN vertex shaders; it only needs GLSL 130, below the
        // stage minimum, so it is left out here.
        if (stage == PatchShaderType.FRAGMENT && Boolean.parseBoolean(p.get("shadowBounds.instrumentation"))
            && AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats(source)) {
            scan += "\nstd430";
        }
        versionInt = Math.max(versionInt, (int) requiredVersion.invoke(null, scan, versionInt));
        final int stageMinimum = stage == PatchShaderType.TESS_CONTROL || stage == PatchShaderType.TESS_EVAL ? 400 : 330;
        versionInt = Math.max(versionInt, stageMinimum);

        String input = GlslTransformUtils.replaceTexture(source);
        input = GlslTransformUtils.renameReservedWords(input, versionInt);
        if (stage != PatchShaderType.COMPUTE) {
            final boolean fragment = stage.glShaderType == ShaderType.FRAGMENT;
            input = CompatShaderTransformer.fixupQualifiers(input, fragment);
            if (patch == Patch.COMPOSITE && fragment) {
                input = CompatibilityTransformer.patchCaveSkyholeClouds(input);
                input = CompatibilityTransformer.patchVolumetricCloudReferenceDistance(input);
            }
            if (fragment) {
                input = CompatibilityTransformer.patchCloudMovementTime(input);
            }
        }
        return VERSION.matcher(input).replaceFirst("#version " + versionInt + (versionInt >= 150 ? " core" : ""));
    }

    private static void restoreHoisting(Map<String, String> p) {
        RenderSystem.initializeGlslCapabilityForTesting(Integer.parseInt(p.getOrDefault("glsl.maxVersion", "460")),
            Boolean.parseBoolean(p.get("glsl.ssbo")), Boolean.parseBoolean(p.get("glsl.imageLoadStore")));
        ShaderTransformer.resetVersionHoistingForTesting();
        if (!"none".equals(p.getOrDefault("versionHoisting", "none"))) {
            ShaderTransformer.init();
        }
    }

    record Parse(String error, List<String> dropped) {
    }

    private static Parse parse(PatchShaderType stage, String source) {
        final GlslTransformerSpikeTest.DroppingPreprocessorFilter filter = new GlslTransformerSpikeTest.DroppingPreprocessorFilter();
        final EnumASTTransformer<JobParameters, PatchShaderType> transformer = GlslTransformerSpikeTest.newTransformer(filter);
        transformer.setTransformation(trees -> {
        });
        final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(stage, source);
        try {
            transformer.transform(inputs, JobParameters.EMPTY);
            return new Parse(null, filter.droppedDirectives);
        } catch (ParsingException | ParseCancellationException e) {
            final ParseCancellationException cause = ParsingException.extractParseCancellationException(e);
            final String detail = cause != null && cause != e ? e.getMessage() + " (" + cause.getMessage() + ")" : e.getMessage();
            return new Parse(e.getClass().getSimpleName() + ": " + detail, filter.droppedDirectives);
        } catch (RuntimeException e) {
            return new Parse(e.getClass().getName() + ": " + e.getMessage(), filter.droppedDirectives);
        }
    }

    static final class Tally {
        int inputs;
        int parsed;
        final List<String> failures = new ArrayList<>();
        final Map<String, Integer> droppedByDirective = new TreeMap<>();
        int inputsWithDropped;

        void add(String label, Parse parse) {
            inputs++;
            if (parse.error() == null) {
                parsed++;
            } else {
                failures.add(label + ": " + parse.error().replace('\n', ' '));
            }
            if (!parse.dropped().isEmpty()) {
                inputsWithDropped++;
            }
            for (String directive : parse.dropped()) {
                final Matcher m = DIRECTIVE_NAME.matcher(directive);
                droppedByDirective.merge(m.lookingAt() ? "#" + m.group(1) : directive, 1, Integer::sum);
            }
        }

        void describe(String pass, List<String> report) {
            report.add("parse-survey: " + pass + " inputs=" + inputs + " parsed=" + parsed + " failed=" + failures.size()
                + " inputsWithDroppedDirectives=" + inputsWithDropped + " dropped=" + droppedByDirective);
            failures.forEach(f -> report.add("parse-survey:   " + pass + " FAILED " + f));
        }
    }
}
