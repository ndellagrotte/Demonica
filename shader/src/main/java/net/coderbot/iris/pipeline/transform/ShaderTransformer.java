package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebug;
import io.github.douira.glsl_transformer.util.Type;
import net.coderbot.iris.Iris;
import net.coderbot.iris.celeritas.vertices.ExtendedChunkVertexType;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.transformer.AdaptiveShadowBoundsTransformer;
import net.coderbot.iris.pipeline.transform.transformer.AttributeTransformer;
import net.coderbot.iris.pipeline.transform.transformer.CeleritasTransformer;
import net.coderbot.iris.pipeline.transform.transformer.CommonTransformer;
import net.coderbot.iris.pipeline.transform.transformer.CompatibilityTransformer;
import net.coderbot.iris.pipeline.transform.transformer.CompositeDepthTransformer;
import net.coderbot.iris.pipeline.transform.transformer.ComputeTransformer;
import net.coderbot.iris.pipeline.transform.transformer.DHGenericTransformer;
import net.coderbot.iris.pipeline.transform.transformer.DHTerrainTransformer;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import net.coderbot.iris.pipeline.transform.transformer.TextureTransformer;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The transform orchestrator, on douira's glsl-transformer (docs/glsl-transformer_adoption/ADOPTION_PLAN.md). Written as
 * {@code AstShaderTransformer} next to the old orchestrator on TauMC's glsl-transformation-lib, it took over one patch
 * kind at a time: COMPOSITE and COMPUTE (Step 5), ATTRIBUTES and CELERITAS_TERRAIN (Step 6), DH_TERRAIN and DH_GENERIC
 * (Step 7); it became the default in Step 8, and in Step 11 the old engine was deleted and this class took its name.
 * Every {@link Patch} is transformed here, adaptive shadow bounds included ({@code transformer/CommonTransformer}).
 *
 * <p>The sequence is the TauMC engine's, stage by stage: find {@code #version}; hoist the version for the features the
 * source uses ({@link VersionNegotiation}; the scan includes the Celeritas header for CELERITAS_TERRAIN vertex shaders
 * and the adaptive-shadow-bounds marker for instrumented fragment shaders with a PCF helper); raise it to the stage
 * minimum; negotiate it against the hardware; the text pre-passes in the same order ({@code replaceTexture},
 * {@code renameReservedWords}, {@code fixupQualifiers}, the COMPOSITE fragment cloud patches, the fragment cloud-time
 * patch); parse with {@link ShaderAst} with the lexer at the effective version; read the {@code #extension} lines;
 * transform ({@link #doTransform}). Then across stages {@link CompatibilityTransformer#transformGrouped}, and each stage
 * printed under {@code #version N core}, the extension lines and (CELERITAS_TERRAIN vertex) the Celeritas header, a
 * text block that is not parsed, and {@code restoreReservedWords}, which runs over the header too. The compute path runs only {@code replaceTexture} and
 * {@code renameReservedWords} before the parse, and has no grouped step.</p>
 *
 * <p>Threads: {@code TransformPatcher} calls this from several {@code Shader-Transform-*} threads at once. No lock is
 * held around a transform; {@link ShaderAst} holds {@link ShaderAst#BUILD_LOCK} around every AST build (glsl-transformer
 * 3.0.0-pre3 is not thread-safe there, and its snippet parser is shared), but not around a program's ANTLR parse
 * (Step 7b). Step 5 measured the alternative, the lock around the whole transform: as fast alone, and serialized on
 * eight threads. With {@code -Ddemonica.glsmPerfDebug=true} each transform logs where its time went
 * ({@link ShaderAst.Timing}).</p>
 *
 * <p>The header's {@code #extension} lines are the ones the TauMC engine wrote: those of the source's leading
 * directive block, the directive lines at its very start up to the first blank line, comment, indented line or code
 * ({@link ShaderAst#extensionDirectives()}). TauMC's pre-parser read only that block, and its parser ignores
 * directives, so an {@code #extension} after the block is dropped, here as there. {@link ShaderAst} finds the lines in
 * the text and takes them out before the parse (Step 7b), so {@code #extension all : warn}, an {@code #extension} in a
 * function body and one before {@code #version}, which glsl-transformer's grammar rejects, transform as they did with
 * TauMC. Two things differ from the TauMC
 * engine by construction. TauMC's header held every directive of the leading block except {@code #version}, so a
 * {@code #define} or {@code #pragma} there came back in the header; glsl-transformer drops and logs them (sources
 * arrive preprocessed; no recorded input has one). A source that does not parse throws
 * {@link ShaderAst.SyntaxException}; TauMC re-parsed it with error recovery and returned what it recovered.</p>
 */
public class ShaderTransformer {
    private static final Pattern versionPattern = VersionNegotiation.VERSION_PATTERN;

    static void clearSessionState() {
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
        final ShaderAst.Timing timing = ShaderAst.Timing.start();

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
        logTiming(patchType, watch, timing);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        final EnumMap<PatchShaderType, ShaderAst> types = new EnumMap<>(PatchShaderType.class);
        final EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);

        final Stopwatch watch = Stopwatch.createStarted();
        final ShaderAst.Timing timing = ShaderAst.Timing.start();

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
            // and the body, never parsed (its #ifdef blocks stay as they are); restoreReservedWords runs over it.
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                header += computeCeleritasHeader();
            }

            final String formattedShader = GlslTransformUtils.restoreReservedWords(entry.getValue().print(header));

            result.put(shaderType, formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        logTiming(patchType, watch, timing);
        return result;
    }

    /**
     * With {@code -Ddemonica.glsmPerfDebug=true}, where the transform's time went (Step 7b):
     * {@code [ShaderTransformer] ATTRIBUTES timing totalMs=.. parseMs=.. buildMs=.. lockWaitMs=.. lockHeldMs=..
     * locks=.. contended=..} ({@link ShaderAst.Timing}).
     */
    private static void logTiming(Patch patchType, Stopwatch watch, ShaderAst.Timing timing) {
        if (GLSMPerfDebug.isEnabled()) {
            Iris.logger.info("[ShaderTransformer] {} timing totalMs={} {}", patchType.name(),
                watch.elapsed(TimeUnit.NANOSECONDS) / 1_000_000.0, timing);
        }
    }

    private static void doTransform(ShaderAst ast, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case CELERITAS_TERRAIN:
                CeleritasTransformer.transform(ast, parameters, versionInt);
                // Handle mc_midTexCoord for Celeritas
                patchMultiTexCoord3(ast, parameters);
                replaceMidTexCoord(ast, ExtendedChunkVertexType.MID_TEX_SCALE);
                replaceMCEntity(ast, parameters);
                applyIntelHd4000Workaround(ast);
                break;
            case COMPOSITE:
                CompositeDepthTransformer.transform(ast, parameters, versionInt);
                break;
            case ATTRIBUTES:
                AttributeTransformer.transform(ast, (AttributeParameters) parameters, versionInt);
                break;
            case COMPUTE:
                ComputeTransformer.transform(ast, parameters, versionInt);
                break;
            case DH_TERRAIN:
                DHTerrainTransformer.transform(ast, parameters, versionInt);
                break;
            case DH_GENERIC:
                DHGenericTransformer.transform(ast, parameters, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        // Iris 26.1 renames texture and gcolor to gtexture in CommonTransformer (plan item 3.2). Here it is idiom code,
        // so it runs after the patch transformer's verbs (PORTING_GUIDE rule 3), and before TextureTransformer, which
        // renames samplers by name, as in Iris.
        CommonTransformer.renameGtexture(ast);
        TextureTransformer.transform(ast, parameters);
        CompatibilityTransformer.transformEach(ast, parameters);
    }

    /** The TauMC engine's {@code applyIntelHd4000Workaround}: {@code ftransform()} calls go through Iris's own function. */
    public static void applyIntelHd4000Workaround(ShaderAst ast) {
        ast.renameFunctionCall("ftransform", "iris_ftransform");
    }

    /**
     * CELERITAS_TERRAIN's {@code gl_MultiTexCoord3}: a vertex shader that uses {@code gl_MultiTexCoord3} (declared or
     * as the built-in) and does not declare {@code mc_midTexCoord} reads the mid-texture coordinate through
     * {@code mc_midTexCoord}, as Iris 26.1 does ({@link CommonTransformer#patchMultiTexCoord3}, Step 7b; the TauMC
     * engine patched only a declared {@code gl_MultiTexCoord3}). A declaration is injected only if the shader had none;
     * it says {@code attribute}, as the TauMC engine's did. {@link #replaceMidTexCoord} right after removes the one
     * declaration there is, so the program declares {@code mc_midTexCoord} once, as Celeritas's {@code in vec2}.
     */
    public static void patchMultiTexCoord3(ShaderAst ast, Parameters parameters) {
        CommonTransformer.patchMultiTexCoord3(ast, parameters, "attribute vec4 mc_midTexCoord;");
    }

    /**
     * The TauMC engine's {@code replaceMidTexCoord}: the pack's {@code mc_midTexCoord}, whatever its declared type, is
     * read from Celeritas's {@code vec2} attribute through {@code iris_MidTex}, scaled by {@code textureScale}. The
     * type switch is TauMC's with glsl-transformer's {@link Type} for its lexer tokens: {@code float}
     * {@link Type#FLOAT32}, {@code vec2}..{@code vec4} {@link Type#F32VEC2}..{@link Type#F32VEC4}; no declaration
     * (TauMC's 0) and {@code bool} {@link Type#BOOL} return after the replacement; any other type falls through to
     * the {@code in vec2} declaration without an {@code iris_MidTex}, as in TauMC.
     */
    public static void replaceMidTexCoord(ShaderAst ast, float textureScale) {
        final ShaderAst.DeclaredType type = ast.findType("mc_midTexCoord");
        if (type != null) {
            ast.removeVariable("mc_midTexCoord");
        }
        ast.replaceExpression("mc_midTexCoord", "iris_MidTex");
        if (type == null || type.is(Type.BOOL)) {
            return;
        }
        if (type.is(Type.FLOAT32)) {
            ast.injectFunction("float iris_MidTex = (mc_midTexCoord.x * " + textureScale + ").x;"); //TODO go back to variable if order is fixed
        } else if (type.is(Type.F32VEC2)) {
            ast.injectFunction("vec2 iris_MidTex = (mc_midTexCoord.xy * " + textureScale + ").xy;");
        } else if (type.is(Type.F32VEC3)) {
            ast.injectFunction("vec3 iris_MidTex = vec3(mc_midTexCoord.xy * " + textureScale + ", 0.0);");
        } else if (type.is(Type.F32VEC4)) {
            ast.injectFunction("vec4 iris_MidTex = vec4(mc_midTexCoord.xy * " + textureScale + ", 0.0, 1.0);");
        }

        ast.injectVariable("in vec2 mc_midTexCoord;"); //TODO why is this inserted oddly?
    }

    /**
     * The TauMC engine's {@code replaceMCEntity}: replaces a shader-declared {@code mc_Entity} (vec2/ivec2/float/int
     * and so on) with upstream-compatible unpacking from a single uint attribute, packed as
     * {@code ((blockId + 1) << 1) | (renderType & 1)}. The type switch is TauMC's with glsl-transformer's
     * {@link Type}: {@link Type#FLOAT32}, {@link Type#F32VEC2}..{@link Type#F32VEC4}, {@link Type#UINT32},
     * {@link Type#INT32}, {@link Type#I32VEC2}..{@link Type#I32VEC4}; no declaration and {@link Type#BOOL} return
     * after the replacement; any other type throws. The exception names the type's keyword, where TauMC's named its
     * lexer token number.
     */
    public static void replaceMCEntity(ShaderAst ast, Parameters parameters) {
        if (parameters.type != ShaderType.VERTEX) return;

        final ShaderAst.DeclaredType type = ast.findType("mc_Entity");
        if (type != null) {
            ast.removeVariable("mc_Entity");
        }
        ast.replaceExpression("mc_Entity", "iris_Entity");
        if (type == null || type.is(Type.BOOL)) {
            return;
        } else if (type.is(Type.FLOAT32)) {
            ast.injectFunction("float iris_Entity = float(int(mc_Entity >> 1u) - 1);");
        } else if (type.is(Type.F32VEC2)) {
            ast.injectFunction("vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
        } else if (type.is(Type.F32VEC3)) {
            ast.injectFunction("vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
        } else if (type.is(Type.F32VEC4)) {
            ast.injectFunction("vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
        } else if (type.is(Type.UINT32)) {
            ast.injectFunction("uint iris_Entity = uint(int(mc_Entity >> 1u) - 1);");
        } else if (type.is(Type.INT32)) {
            ast.injectFunction("int iris_Entity = int(mc_Entity >> 1u) - 1;");
        } else if (type.is(Type.I32VEC2)) {
            ast.injectFunction("ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
        } else if (type.is(Type.I32VEC3)) {
            ast.injectFunction("ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
        } else if (type.is(Type.I32VEC4)) {
            ast.injectFunction("ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
        } else {
            throw new IllegalStateException("Got an invalid format mc_Entity (type " + type.keyword() + ").");
        }
        ast.injectVariable("in uint mc_Entity;");
    }

    /** The TauMC engine's {@code addIfNotExists}: declares {@code code} unless a variable {@code name} exists. */
    public static void addIfNotExists(ShaderAst ast, String name, String code) {
        if (!ast.hasVariable(name)) {
            ast.injectVariable(code);
        }
    }

    /** The TauMC engine's {@code addIfNotExistsType}: declares {@code type name;} unless a variable {@code name} exists. */
    public static void addIfNotExistsType(ShaderAst ast, String name, String type) {
        if (!ast.hasVariable(name)) {
            ast.injectVariable(type + " " + name + ";");
        }
    }

    /**
     * Celeritas's {@code chunk_vertex.glsl} with Iris's constants, the text header of CELERITAS_TERRAIN vertex shaders
     * (emitted between the extension lines and the body, and scanned for version hoisting). Moved here from the TauMC
     * engine's orchestrator in Step 7.
     */
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
