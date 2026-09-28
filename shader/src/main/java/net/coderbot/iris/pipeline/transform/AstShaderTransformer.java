package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.Map;

/**
 * The transform engine on douira's glsl-transformer, selected with {@code -Ddemonica.glsl.engine=douira}
 * ({@link TransformPatcher#engine()}). It takes over from {@link ShaderTransformer} one patch kind at a time
 * (docs/glsl-transformer_adoption/ADOPTION_PLAN.md); a kind that is not ported yet throws.
 */
public class AstShaderTransformer {

    static void clearSessionState() {
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }
        throw notPorted(parameters.patch);
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transformCompute(String compute, P parameters) {
        if (compute == null) {
            return null;
        }
        throw notPorted(parameters.patch);
    }

    private static UnsupportedOperationException notPorted(Patch patch) {
        return new UnsupportedOperationException("glsl-transformer engine: " + patch + " not ported yet");
    }
}
