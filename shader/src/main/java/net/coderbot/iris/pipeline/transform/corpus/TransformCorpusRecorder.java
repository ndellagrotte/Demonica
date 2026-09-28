package net.coderbot.iris.pipeline.transform.corpus;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.debug.TransformCorpus;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records {@code TransformPatcher}'s cache misses into the transform corpus ({@link TransformCorpus}; inert unless
 * {@value TransformCorpus#PROPERTY} is set). A case holds the sources, everything else the output depends on (the
 * {@link Parameters}, the GLSL capability, version hoisting, the adaptive-shadow-bounds instrumentation), the engine,
 * its output per stage and the elapsed time. The replayer, {@code TransformCorpusReplayTest}, rebuilds the call from
 * these keys, so a new {@code Parameters} field needs a key here and a line there.
 *
 * <p>{@link #begin} runs before the engine, because the engine mutates {@link Parameters#type}; {@link #finish} runs
 * after the result is cached, so the file writes stay out of the {@code transformMs} the cache logs. Neither throws.</p>
 */
public final class TransformCorpusRecorder {
    private TransformCorpusRecorder() {
    }

    /** Whether recording is on; a constant per JVM. */
    public static boolean isEnabled() {
        return TransformCorpus.isEnabled();
    }

    /** The file-name form of a stage: {@code vertex}, {@code tess_control}, ... */
    public static String stageName(PatchShaderType stage) {
        return stage.name().toLowerCase(Locale.ROOT);
    }

    /** Describes one call before it runs, or returns null if that fails (the failure is logged once). */
    public static TransformCorpus.Case begin(Parameters parameters, String vertex, String geometry, String tessControl,
                                             String tessEval, String fragment, String compute,
                                             boolean shadowBoundsInstrumentation, int shadowBoundsBinding) {
        try {
            final TransformCorpus.Case c = new TransformCorpus.Case("iris", parameters.patch.name());
            c.input("parameters", parameters.getClass().getSimpleName());
            c.input("textureStage", parameters.getTextureStage());
            if (parameters instanceof AttributeParameters attributes) {
                c.input("hasGeometry", attributes.hasGeometry);
                c.input("inputs.texture", attributes.inputs.texture);
                c.input("inputs.lightmap", attributes.inputs.lightmap);
                c.input("inputs.color", attributes.inputs.color);
            }
            describeTextureMap(c, parameters.getTextureMap());

            final List<String> stages = new ArrayList<>();
            addStage(c, stages, PatchShaderType.VERTEX, vertex);
            addStage(c, stages, PatchShaderType.GEOMETRY, geometry);
            addStage(c, stages, PatchShaderType.TESS_CONTROL, tessControl);
            addStage(c, stages, PatchShaderType.TESS_EVAL, tessEval);
            addStage(c, stages, PatchShaderType.FRAGMENT, fragment);
            addStage(c, stages, PatchShaderType.COMPUTE, compute);
            c.input("stages", String.join(",", stages));

            c.input("glsl.maxVersion", RenderSystem.getMaxGlslVersion());
            c.input("glsl.ssbo", RenderSystem.supportsSSBO());
            c.input("glsl.imageLoadStore", RenderSystem.supportsImageLoadStore());
            c.input("versionHoisting", ShaderTransformer.versionHoistingState());
            c.input("shadowBounds.instrumentation", shadowBoundsInstrumentation);
            c.input("shadowBounds.binding", shadowBoundsBinding);
            return c;
        } catch (Exception | LinkageError e) {
            TransformCorpus.reportFailure(e);
            return null;
        }
    }

    /**
     * Completes and writes {@code c}.
     *
     * @param output the engine's result, or null if it threw {@code error}
     */
    public static void finish(TransformCorpus.Case c, String engine, Map<PatchShaderType, String> output,
                              Throwable error, long elapsedNanos) {
        if (c == null) {
            return;
        }
        try {
            c.result("engine", engine);
            c.result("outcome", error == null ? "ok" : "error");
            if (error != null) {
                c.result("error", error.getClass().getName() + ": " + error.getMessage());
            }
            c.result("transformMs", elapsedNanos / 1_000_000.0);
            if (output != null) {
                for (Map.Entry<PatchShaderType, String> stage : output.entrySet()) {
                    c.outputFile("out." + engine + "." + stageName(stage.getKey()) + ".glsl", stage.getValue());
                }
            }
            TransformCorpus.record(c);
        } catch (Exception | LinkageError e) {
            TransformCorpus.reportFailure(e);
        }
    }

    private static void addStage(TransformCorpus.Case c, List<String> stages, PatchShaderType stage, String source) {
        if (source != null) {
            stages.add(stageName(stage));
            c.inputFile("in." + stageName(stage) + ".glsl", source);
        }
    }

    /** {@code textureMap=null}, or the entry count and one {@code textureMap.N=name|type|stage=replacement} each. */
    private static void describeTextureMap(TransformCorpus.Case c,
                                           Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        if (textureMap == null) {
            c.input("textureMap", "null");
            return;
        }
        final List<String> entries = new ArrayList<>(textureMap.size());
        textureMap.forEach((key, replacement) ->
            entries.add(key.first() + "|" + key.second() + "|" + key.third() + "=" + replacement));
        c.input("textureMap", entries.size());
        for (int i = 0; i < entries.size(); i++) {
            c.input("textureMap." + i, entries.get(i));
        }
    }
}
