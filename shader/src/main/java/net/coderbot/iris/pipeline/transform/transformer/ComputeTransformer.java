package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;

/**
 * COMPUTE on {@link ShaderAst}: the common transformation in core profile. Ported from the TauMC engine's class of the
 * same name (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md).
 */
public final class ComputeTransformer {
	private ComputeTransformer() {
	}

	public static void transform(ShaderAst transformer, Parameters parameters, int glslVersion) {
		CommonTransformer.transform(transformer, parameters, true, glslVersion);
	}
}
