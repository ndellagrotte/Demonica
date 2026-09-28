package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

/**
 * COMPOSITE (composite, deferred, final, prepare, shadowcomp and begin passes) on {@link ShaderAst}: the common
 * transformation in core profile, the matrix uniforms, the full-screen quad's vertex attributes, and
 * {@code centerDepthSmooth} read from Iris's texture. Ported from the TauMC engine's class of the same name (Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md).
 */
public final class CompositeDepthTransformer {
	private CompositeDepthTransformer() {
	}

	public static void transform(ShaderAst transformer, Parameters parameters, int glslVersion) {
		CommonTransformer.transform(transformer, parameters, true, glslVersion);

		CoreTransformHelper.injectMatrixUniforms(transformer);

		if (parameters.type == ShaderType.VERTEX) {
			CoreTransformHelper.injectCompositeVertexAttributes(transformer);
		}

		final ShaderAst.DeclaredType type = transformer.findType("centerDepthSmooth");
		if (type != null) {
			transformer.injectVariable("uniform sampler2D iris_centerDepthSmooth;");
			transformer.replaceExpression("centerDepthSmooth", "texture2D(iris_centerDepthSmooth, vec2(0.5)).r");
		}
	}
}
