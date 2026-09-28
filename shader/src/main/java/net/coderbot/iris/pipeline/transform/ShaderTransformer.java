package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.celeritas.vertices.ExtendedChunkVertexType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderTransformer {
    private static final Pattern versionPattern = VersionNegotiation.VERSION_PATTERN;

    // Track logged negotiations to avoid spam - cleared on shader pack reload
    private static final Set<String> loggedNegotiations = new HashSet<>();

    static void clearSessionState() {
        loggedNegotiations.clear();
    }


    /** {@link VersionNegotiation#init()}; kept here so that {@code Iris} and the tests that call it are unchanged. */
    public static void init() {
        VersionNegotiation.init();
    }

    /** {@link VersionNegotiation#versionHoistingState()}; the corpus recorder and replayer call it here. */
    public static String versionHoistingState() {
        return VersionNegotiation.versionHoistingState();
    }

    /** Returns version hoisting to its state before {@link #init()}, for replaying a case recorded then. */
    static void resetVersionHoistingForTesting() {
        VersionNegotiation.resetForTesting();
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
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

        final var parsedShader = ShaderParser.parseShader(input);
        final var transformer = new Transformer(parsedShader.full());

        doTransform(transformer, patchType, parameters, versionInt);

        // Extract extensions
        final var extensions = versionPattern.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

        final String finalHeader = profileString + (extensions.isEmpty() ? "" : "\n" + extensions);
        final StringBuilder formattedShaderBuilder = new StringBuilder();

        transformer.mutateTree(tree -> formattedShaderBuilder.append(GlslTransformUtils.getFormattedShader(tree, finalHeader)));

        String formattedShader = GlslTransformUtils.restoreReservedWords(formattedShaderBuilder.toString());

        result.put(PatchShaderType.COMPUTE, formattedShader);

        watch.stop();
        Iris.logger.info("[Load #{}] Transformed compute shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
         final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
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
            String scanSource = (patchType == Patch.CELERITAS_TERRAIN && type == PatchShaderType.VERTEX) ? input + computeCeleritasHeader() : input;
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

            // Pre-parse reserved word renaming — prevents ANTLR parse failures
            input = GlslTransformUtils.replaceTexture(input);
            input = GlslTransformUtils.renameReservedWords(input, versionInt);
            input = CompatShaderTransformer.fixupQualifiers(input, parameters.type == ShaderType.FRAGMENT);
            if (patchType == Patch.COMPOSITE && parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityTransformer.patchCaveSkyholeClouds(input);
                input = CompatibilityTransformer.patchVolumetricCloudReferenceDistance(input);
            }
            if (parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityTransformer.patchCloudMovementTime(input);
            }

            final var parsedShader = ShaderParser.parseShader(input);
            final var transformer = new Transformer(parsedShader.full());

            doTransform(transformer, patchType, parameters, versionInt);

            // Extract extensions from the pre-parsed content (version + extensions before main code)
            // This preserves #extension directives that the shader pack declares
            final var extensions = versionPattern.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

            types.put(type, transformer);
            prepatched.put(type, profileString + (extensions.isEmpty() ? "" : "\n" + extensions));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            final PatchShaderType shaderType = entry.getKey();
            final Transformer transformer = entry.getValue();
            String header = prepatched.get(shaderType);

            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                header += computeCeleritasHeader();
            }

            final String finalHeader = header;
            final StringBuilder formattedShaderBuilder = new StringBuilder();

            transformer.mutateTree(tree -> formattedShaderBuilder.append(GlslTransformUtils.getFormattedShader(tree, finalHeader)));

            String formattedShader = GlslTransformUtils.restoreReservedWords(formattedShaderBuilder.toString());

            result.put(shaderType, formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case CELERITAS_TERRAIN:
                CeleritasTransformer.transform(transformer, parameters, versionInt);
                // Handle mc_midTexCoord for Celeritas
                patchMultiTexCoord3(transformer, parameters);
                replaceMidTexCoord(transformer, ExtendedChunkVertexType.MID_TEX_SCALE);
                replaceMCEntity(transformer, parameters);
                applyIntelHd4000Workaround(transformer);
                break;
            case COMPOSITE:
                CompositeDepthTransformer.transform(transformer, parameters, versionInt);
                break;
            case ATTRIBUTES:
                AttributeTransformer.transform(transformer, (AttributeParameters) parameters, versionInt);
                break;
            case COMPUTE:
                ComputeTransformer.transform(transformer, parameters, versionInt);
                break;
            case DH_TERRAIN:
                DHTerrainTransformer.transform(transformer, parameters, versionInt);
                break;
            case DH_GENERIC:
                DHGenericTransformer.transform(transformer, parameters, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        TextureTransformer.transform(transformer, parameters);
        CompatibilityTransformer.transformEach(transformer, parameters);
    }

    public static void applyIntelHd4000Workaround(Transformer transformer) {
        transformer.renameFunctionCall("ftransform", "iris_ftransform");
    }

    public static void patchMultiTexCoord3(Transformer transformer, Parameters parameters) {
        if (parameters.type == ShaderType.VERTEX && transformer.hasVariable("gl_MultiTexCoord3") && !transformer.hasVariable("mc_midTexCoord")) {
            transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
            transformer.injectVariable("attribute vec4 mc_midTexCoord;");
        }
    }

    public static void replaceMidTexCoord(Transformer transformer, float textureScale) {
        final int type = transformer.findType("mc_midTexCoord");
        if (type != 0) {
            transformer.removeVariable("mc_midTexCoord");
        }
        transformer.replaceExpression("mc_midTexCoord", "iris_MidTex");
        switch (type) {
            case 0:
                return;
            case GLSLLexer.BOOL:
                return;
            case GLSLLexer.FLOAT:
                transformer.injectFunction("float iris_MidTex = (mc_midTexCoord.x * " + textureScale + ").x;"); //TODO go back to variable if order is fixed
                break;
            case GLSLLexer.VEC2:
                transformer.injectFunction("vec2 iris_MidTex = (mc_midTexCoord.xy * " + textureScale + ").xy;");
                break;
            case GLSLLexer.VEC3:
                transformer.injectFunction("vec3 iris_MidTex = vec3(mc_midTexCoord.xy * " + textureScale + ", 0.0);");
                break;
            case GLSLLexer.VEC4:
                transformer.injectFunction("vec4 iris_MidTex = vec4(mc_midTexCoord.xy * " + textureScale + ", 0.0, 1.0);");
                break;
            default:

        }

        transformer.injectVariable("in vec2 mc_midTexCoord;"); //TODO why is this inserted oddly?

    }

    /**
     * Replaces shader-declared mc_Entity (vec2/ivec2/float/int/etc.) with upstream-compatible unpacking
     * from a single uint attribute. The uint is packed as ((blockId + 1) << 1) | (renderType & 1).
     */
    public static void replaceMCEntity(Transformer transformer, Parameters parameters) {
        if (parameters.type != ShaderType.VERTEX) return;

        final int type = transformer.findType("mc_Entity");
        if (type != 0) {
            transformer.removeVariable("mc_Entity");
        }
        transformer.replaceExpression("mc_Entity", "iris_Entity");
        switch (type) {
            case 0:
            case GLSLLexer.BOOL:
                return;
            case GLSLLexer.FLOAT:
                transformer.injectFunction("float iris_Entity = float(int(mc_Entity >> 1u) - 1);");
                break;
            case GLSLLexer.VEC2:
                transformer.injectFunction("vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
                break;
            case GLSLLexer.VEC3:
                transformer.injectFunction("vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
                break;
            case GLSLLexer.VEC4:
                transformer.injectFunction("vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
                break;
            case GLSLLexer.UINT:
                transformer.injectFunction("uint iris_Entity = uint(int(mc_Entity >> 1u) - 1);");
                break;
            case GLSLLexer.INT:
                transformer.injectFunction("int iris_Entity = int(mc_Entity >> 1u) - 1;");
                break;
            case GLSLLexer.IVEC2:
                transformer.injectFunction("ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
                break;
            case GLSLLexer.IVEC3:
                transformer.injectFunction("ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
                break;
            case GLSLLexer.IVEC4:
                transformer.injectFunction("ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
                break;
            default:
                throw new IllegalStateException("Got an invalid format mc_Entity (type token " + type + ").");
        }
        transformer.injectVariable("in uint mc_Entity;");
    }

    public static void addIfNotExists(Transformer transformer, String name, String code) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(code);
        }
    }

    public static void addIfNotExistsType(Transformer transformer, String name, String type) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(type + " " + name + ";");
        }
    }

    /** Celeritas's {@code chunk_vertex.glsl}, the text header of CELERITAS_TERRAIN vertex shaders; both engines use it. */
    static String computeCeleritasHeader() {
        final ShaderConstants constants = ShaderConstants.builder()
            .add("VERT_POS_SCALE", "1.0")
            .add("VERT_POS_OFFSET", "0.0")
            .add("VERT_TEX_SCALE", "1.0")
            .build();

        final String chunkVertexHeader = org.embeddedt.embeddium.impl.gl.shader.ShaderParser.parseShader(
            ShaderLoader.getShaderSource("actinium:include/chunk_vertex.glsl"), ShaderLoader::getShaderSource, constants)
            .replace("_get_relative_chunk_coord(pos) * vec3(16.0)", "vec3(_get_relative_chunk_coord(pos)) * 16.0");


        return "\n\n" + chunkVertexHeader + "\n\n";
    }


}
