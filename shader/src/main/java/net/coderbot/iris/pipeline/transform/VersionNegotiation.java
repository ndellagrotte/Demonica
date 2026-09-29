package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.Iris;

import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The transform pipeline's version handling, used by {@link ShaderTransformer}: the {@code #version} regex, version hoisting (a shader that uses a
 * feature of a later GLSL version is raised to it, if the hardware has the feature), the stage minimum, and the
 * negotiation against {@link RenderSystem#getMaxGlslVersion()}, which never lowers a version.
 *
 * <p>Extracted unchanged from the TauMC engine's orchestrator in Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md, when two engines shared it. {@code Iris}, the corpus recorder and
 * the tests call it directly.</p>
 */
public final class VersionNegotiation {
    /** The {@code #version} directive: group 1 the number, group 2 the profile, if any. */
    static final Pattern VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");

    private VersionNegotiation() {
    }

    private record VersionRequirement(String keyword, int minVersion, BooleanSupplier supported) {}

    // Sorted descending by minVersion for early exit in getRequiredVersion

    private static final VersionRequirement[] VERSION_REQUIREMENTS = {
        new VersionRequirement("std430", 430, RenderSystem::supportsSSBO),
        new VersionRequirement("iimage", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("uimage", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageLoad", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageStore", 420, RenderSystem::supportsImageLoadStore),

        new VersionRequirement("uint", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec2", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec3", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec4", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("flat", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
    };


    record NegotiationResult(int targetVersion, String profile, String error) {
        static NegotiationResult error(String message) {
            return new NegotiationResult(-1, "", message);
        }

        static NegotiationResult noop(int version, String profile) {
            return new NegotiationResult(version, profile, null);
        }

        boolean isError() { return error != null; }
    }

    static int getStageMinimumVersion(PatchShaderType stage) {
        return switch (stage) {
            case COMPUTE -> 330;
            case TESS_CONTROL, TESS_EVAL -> 400;
            case GEOMETRY -> 330;
            default -> 330;
        };
    }

    static NegotiationResult negotiateVersion(int effectiveVersion, PatchShaderType stage) {
        final int maxGlsl = RenderSystem.getMaxGlslVersion();

        if (effectiveVersion <= maxGlsl) {
            return NegotiationResult.noop(effectiveVersion, effectiveVersion >= 150 ? "core" : "");
        }

        final int stageMin = getStageMinimumVersion(stage);
        if (maxGlsl < stageMin) {
            return NegotiationResult.error("Hardware GLSL " + maxGlsl + " below stage minimum " + stageMin + " for " + stage.name());
        }

        return NegotiationResult.error("Shader requires GLSL " + effectiveVersion + " but hardware max is " + maxGlsl);
    }

    private static Pattern hoistPattern;
    private static Object2IntMap<String> keywordToVersion;
    private static int maxSupportedHoistVersion;

    /** Enables version hoisting for the features the hardware supports; {@code Iris} calls it at runtime GL initialization. */
    public static void init() {
        final StringBuilder patternBuilder = new StringBuilder();
        final Object2IntOpenHashMap<String> versionMap = new Object2IntOpenHashMap<>();
        int maxVersion = 0;

        for (VersionRequirement req : VERSION_REQUIREMENTS) {
            if (req.supported.getAsBoolean()) {
                if (!patternBuilder.isEmpty()) patternBuilder.append('|');

                patternBuilder.append("\\b").append(Pattern.quote(req.keyword)).append("\\b");
                versionMap.put(req.keyword, req.minVersion);
                maxVersion = Math.max(maxVersion, req.minVersion);
            }
        }

        if (!patternBuilder.isEmpty()) {
            hoistPattern = Pattern.compile(patternBuilder.toString());
            keywordToVersion = versionMap;
        }
        maxSupportedHoistVersion = maxVersion;

        Iris.logger.info("Shader version hoisting: {} feature(s) GLSL {}", versionMap.size(), maxVersion > 0 ? maxVersion : "N/A");
    }

    /**
     * The keywords that currently hoist a shader's version, in declaration order, or {@code none} before
     * {@link #init()} (Iris's transform warm-up runs first) or when the hardware supports none of them. Recorded with
     * each transform corpus case, because it changes the output.
     */
    public static String versionHoistingState() {
        final Object2IntMap<String> keywords = keywordToVersion;
        if (hoistPattern == null || keywords == null) {
            return "none";
        }
        final StringBuilder state = new StringBuilder();
        for (VersionRequirement req : VERSION_REQUIREMENTS) {
            if (keywords.containsKey(req.keyword)) {
                if (!state.isEmpty()) state.append(',');
                state.append(req.keyword);
            }
        }
        return state.toString();
    }

    /** Returns version hoisting to its state before {@link #init()}, for replaying a case recorded then. */
    static void resetForTesting() {
        hoistPattern = null;
        keywordToVersion = null;
        maxSupportedHoistVersion = 0;
    }

    static int getRequiredVersion(String shaderSource, int declaredVersion) {
        if (hoistPattern == null || declaredVersion >= maxSupportedHoistVersion) {
            return declaredVersion;
        }

        final Matcher m = hoistPattern.matcher(shaderSource);
        int required = declaredVersion;
        while (m.find()) {
            final int ver = keywordToVersion.getInt(m.group());
            if (ver > required) {
                required = ver;
                if (required >= maxSupportedHoistVersion) break;
            }
        }
        return required;
    }
}
