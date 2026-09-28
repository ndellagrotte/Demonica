package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.transformer.CompatibilityTransformer;
import net.coderbot.iris.pipeline.transform.transformer.CompositeDepthTransformer;
import net.coderbot.iris.pipeline.transform.transformer.ComputeTransformer;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import net.coderbot.iris.pipeline.transform.transformer.TextureTransformer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The transform engine on douira's glsl-transformer, selected with {@code -Ddemonica.glsl.engine=douira}
 * ({@link TransformPatcher#engine()}). It takes over from {@link ShaderTransformer} one patch kind at a time
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md); a kind that is not ported yet throws
 * {@code UnsupportedOperationException("glsl-transformer engine: <kind> not ported yet")}, a message the corpus replay
 * relies on. Ported: COMPOSITE and COMPUTE (Step 5).
 *
 * <p>The sequence is the TauMC engine's, stage by stage: find {@code #version}; hoist the version for the features the
 * source uses ({@link VersionNegotiation}; the scan includes the Celeritas header for CELERITAS_TERRAIN vertex shaders
 * and the adaptive-shadow-bounds marker for instrumented fragment shaders with a PCF helper); raise it to the stage
 * minimum; negotiate it against the hardware; the text pre-passes in the same order ({@code replaceTexture},
 * {@code renameReservedWords}, {@code fixupQualifiers}, the COMPOSITE fragment cloud patches, the fragment cloud-time
 * patch); parse with {@link ShaderAst} with the lexer at the effective version; read the {@code #extension} lines;
 * transform ({@link #doTransform}). Then across stages {@link CompatibilityTransformer#transformGrouped}, and each stage
 * printed under {@code #version N core}, the extension lines and (CELERITAS_TERRAIN vertex, Step 6) the Celeritas
 * header, and {@code restoreReservedWords}. The compute path runs only {@code replaceTexture} and
 * {@code renameReservedWords} before the parse, and has no grouped step.</p>
 *
 * <p>Threads: {@code TransformPatcher} calls this from several {@code Shader-Transform-*} threads at once. No lock is
 * held around a transform; {@link ShaderAst} holds {@link ShaderAst#BUILD_LOCK} around every parse and node build
 * (glsl-transformer 3.0.0-pre3 is not thread-safe there, and its parser is shared). Step 5 measured the alternative,
 * the lock around the whole transform: as fast alone, and serialized on eight threads.</p>
 *
 * <p>Two things differ from the TauMC engine by construction. The {@code #extension} lines come from the parsed
 * program ({@link ShaderAst#extensionDirectives()}); TauMC printed every directive of its pre-parser tree except
 * {@code #version}, so it also re-emitted a {@code #define} or {@code #pragma} in the header, which glsl-transformer
 * drops and logs (sources arrive preprocessed; no recorded input has one). A source that does not parse throws
 * {@link ShaderAst.SyntaxException}; TauMC re-parsed it with error recovery and returned what it recovered.</p>
 */
public class AstShaderTransformer {
    private static final Pattern versionPattern = VersionNegotiation.VERSION_PATTERN;

    /** The patch kinds this engine transforms; the others throw {@link #notPorted}. */
    private static final Set<Patch> PORTED = EnumSet.of(Patch.COMPOSITE, Patch.COMPUTE);

    static void clearSessionState() {
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }
        if (!PORTED.contains(parameters.patch)) {
            throw notPorted(parameters.patch);
        }

        final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(PatchShaderType.VERTEX, vertex);
        inputs.put(PatchShaderType.GEOMETRY, geometry);
        inputs.put(PatchShaderType.TESS_CONTROL, tessControl);
        inputs.put(PatchShaderType.TESS_EVAL, tessEval);
        inputs.put(PatchShaderType.FRAGMENT, fragment);
        try {
            return transformInternal(inputs, parameters.patch, parameters);
        } finally {
            parameters.type = null;
        }
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transformCompute(String compute, P parameters) {
        if (compute == null) {
            return null;
        }
        if (!PORTED.contains(parameters.patch)) {
            throw notPorted(parameters.patch);
        }

        try {
            return transformComputeInternal(compute, parameters.patch, parameters);
        } finally {
            parameters.type = null;
        }
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformComputeInternal(String compute, Patch patchType, P parameters) {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);

        final Stopwatch watch = Stopwatch.createStarted();

        parameters.type = ShaderType.COMPUTE;

        final Matcher matcher = versionPattern.matcher(compute);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in compute shader source code!");
        }

        String versionString = matcher.group(1);
        int versionInt = Integer.parseInt(versionString);

        // Check if shader uses features requiring a higher GLSL version
        final int requiredVersion = VersionNegotiation.getRequiredVersion(compute, versionInt);
        if (requiredVersion > versionInt) {
            Iris.logger.debug("Compute shader requires GLSL {} for detected features, hoisting from {}", requiredVersion, versionInt);
            versionInt = requiredVersion;
            versionString = String.valueOf(versionInt);
        }

        // Compute shaders always use core profile, minimum 330
        if (versionInt < 330) {
            versionString = "330";
            versionInt = 330;
        }

        // Negotiate version downgrade if needed
        final VersionNegotiation.NegotiationResult negotiation = VersionNegotiation.negotiateVersion(versionInt, PatchShaderType.COMPUTE);
        if (negotiation.isError()) {
            throw new RuntimeException("Compute shader version negotiation failed: " + negotiation.error());
        }
        if (negotiation.targetVersion() != versionInt) {
            Iris.logger.debug("Negotiated compute shader from GLSL {} to {}", versionInt, negotiation.targetVersion());
            versionInt = negotiation.targetVersion();
            versionString = String.valueOf(versionInt);
        }

        final String profileString = "#version " + versionString + " core\n";

        // Pre-parse reserved word renaming
        String input = GlslTransformUtils.replaceTexture(compute);
        input = GlslTransformUtils.renameReservedWords(input, versionInt);

        final ShaderAst ast = ShaderAst.parse(input, versionInt);
        final String extensions = String.join("\n", ast.extensionDirectives());

        doTransform(ast, patchType, parameters, versionInt);

        final String finalHeader = profileString + (extensions.isEmpty() ? "" : "\n" + extensions);
        final String formattedShader = GlslTransformUtils.restoreReservedWords(ast.print(finalHeader));

        result.put(PatchShaderType.COMPUTE, formattedShader);

        watch.stop();
        Iris.logger.info("[Load #{}] Transformed compute shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        final EnumMap<PatchShaderType, ShaderAst> types = new EnumMap<>(PatchShaderType.class);
        final EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);

        final Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.VALUES) {
            parameters.type = type.glShaderType;
            if (inputs.get(type) == null) {
                continue;
            }

            String input = inputs.get(type);

            final Matcher matcher = versionPattern.matcher(input);
            if (!matcher.find()) {
                throw new IllegalArgumentException("No #version directive found in source code!");
            }

            String versionString = matcher.group(1);
            if (versionString == null) {
                continue;
            }

            int versionInt = Integer.parseInt(versionString);

            // Include celeritas header in scan — it's injected post-negotiation but contains uint/uvec3
            String scanSource = (patchType == Patch.CELERITAS_TERRAIN && type == PatchShaderType.VERTEX) ? input + ShaderTransformer.computeCeleritasHeader() : input;
            if (type == PatchShaderType.FRAGMENT
                && AdaptiveShadowBoundsStats.isInstrumentationEnabled()
                && AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats(input)) {
                scanSource += "\n" + AdaptiveShadowBoundsStats.shaderVersionMarker();
            }
            final int requiredVersion = VersionNegotiation.getRequiredVersion(scanSource, versionInt);
            if (requiredVersion > versionInt) {
                Iris.logger.debug("Shader requires GLSL {} for detected features, hoisting from {}", requiredVersion, versionInt);
                versionInt = requiredVersion;
                versionString = String.valueOf(versionInt);
            }

            // Ensure minimum version for this stage (330 for most, 400 for tessellation)
            final int stageMin = VersionNegotiation.getStageMinimumVersion(type);
            if (versionInt < stageMin) {
                versionInt = stageMin;
                versionString = String.valueOf(versionInt);
            }

            // Negotiate version if needed (error if hardware can't support)
            final VersionNegotiation.NegotiationResult negotiation = VersionNegotiation.negotiateVersion(versionInt, type);
            if (negotiation.isError()) {
                throw new RuntimeException("Shader version negotiation failed for " + type.name() + ": " + negotiation.error());
            }

            // All stages >= 330 use core profile
            final String profile = "core";
            final String profileString = "#version " + versionString + " " + profile + "\n";

            // Pre-parse reserved word renaming — prevents parse failures on words that are keywords at the version
            input = GlslTransformUtils.replaceTexture(input);
            input = GlslTransformUtils.renameReservedWords(input, versionInt);
            input = CompatShaderTransformer.fixupQualifiers(input, parameters.type == ShaderType.FRAGMENT);
            if (patchType == Patch.COMPOSITE && parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityPatches.patchCaveSkyholeClouds(input);
                input = CompatibilityPatches.patchVolumetricCloudReferenceDistance(input);
            }
            if (parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityPatches.patchCloudMovementTime(input);
            }

            // The lexer reads the program at the effective version, which decides the keywords; the source's own
            // #version line is dropped at print.
            final ShaderAst ast = ShaderAst.parse(input, versionInt);
            // Read before the transform: print() removes them from the tree.
            final String extensions = String.join("\n", ast.extensionDirectives());

            doTransform(ast, patchType, parameters, versionInt);

            types.put(type, ast);
            prepatched.put(type, profileString + (extensions.isEmpty() ? "" : "\n" + extensions));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            final PatchShaderType shaderType = entry.getKey();
            String header = prepatched.get(shaderType);

            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header: text between the extension lines
            // and the body. CELERITAS_TERRAIN is ported in Step 6; the hook is here so the header stays text.
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                header += ShaderTransformer.computeCeleritasHeader();
            }

            final String formattedShader = GlslTransformUtils.restoreReservedWords(entry.getValue().print(header));

            result.put(shaderType, formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void doTransform(ShaderAst ast, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case COMPOSITE:
                CompositeDepthTransformer.transform(ast, parameters, versionInt);
                break;
            case COMPUTE:
                ComputeTransformer.transform(ast, parameters, versionInt);
                break;
            default:
                throw notPorted(patchType);
        }
        TextureTransformer.transform(ast, parameters);
        CompatibilityTransformer.transformEach(ast, parameters);
    }

    /** The TauMC engine's {@code applyIntelHd4000Workaround}: {@code ftransform()} calls go through Iris's own function. */
    public static void applyIntelHd4000Workaround(ShaderAst ast) {
        ast.renameFunctionCall("ftransform", "iris_ftransform");
    }

    private static UnsupportedOperationException notPorted(Patch patch) {
        return new UnsupportedOperationException("glsl-transformer engine: " + patch + " not ported yet");
    }
}
