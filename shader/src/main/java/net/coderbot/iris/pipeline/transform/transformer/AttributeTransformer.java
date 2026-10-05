package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;

import java.util.HashMap;
import java.util.Map;

/**
 * The ATTRIBUTES patch kind (every gbuffers and shadow program outside Celeritas terrain), on {@link ShaderAst}: the
 * common transformation, the entity uniforms, the matrix uniforms and, for vertex shaders, the fixed-function vertex
 * attributes as {@code layout}-located {@code iris_*} inputs. Ported verb for verb from the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.AttributeTransformer} (Step 6 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), with the same injected source strings; {@code gl_MultiTexCoord3} is
 * handled as Iris 26.1 does ({@link CommonTransformer#patchMultiTexCoord3}, Step 7b).
 */
public final class AttributeTransformer {
	private AttributeTransformer() {
	}

	public static void transform(ShaderAst transformer, AttributeParameters parameters, int version) {
		// Always core profile — minimum GLSL version is 330 (see VersionNegotiation.getStageMinimumVersion)
		CommonTransformer.transform(transformer, parameters, true, version);

		// Entity ID and overlay color patching (uniform path)
		EntityPatcher.patchEntityId(transformer, parameters);
		EntityPatcher.patchOverlayColor(transformer, parameters);

		transformCore(transformer, parameters);
	}

	private static void transformCore(ShaderAst transformer, AttributeParameters parameters) {
		CoreTransformHelper.injectMatrixUniforms(transformer);

		// Iris 26.1 VanillaTransformer: "const float mc_chunkFade = -1.0;" in every stage, for packs that read it under
		// IRIS_FEATURE_FADE_VARIABLE (-1.0: not a fading chunk). Demonica: declared only where the program reads it and
		// does not declare it itself, so programs that never name it print as before (an unused const changes nothing).
		if (transformer.containsCall("mc_chunkFade")) {
			ShaderTransformer.addIfNotExists(transformer, "mc_chunkFade", "const float mc_chunkFade = -1.0;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
			transformer.injectVariable("layout(location = 2) in vec4 iris_MultiTexCoord0;");
			transformer.injectVariable("layout(location = 3) in vec4 iris_MultiTexCoord1;");
			transformer.injectVariable("layout(location = 4) in vec3 iris_Normal;");
			if (parameters.inputs.color) {
				transformer.injectVariable("layout(location = 1) in vec4 iris_Color;");
				transformer.rename("gl_Color", "iris_Color");
			} else {
				transformer.injectVariable("uniform vec4 iris_ColorModulator;");
				transformer.rename("gl_Color", "iris_ColorModulator");
			}

			transformer.rename("gl_Vertex", "iris_Vertex");
			transformer.rename("gl_Normal", "iris_Normal");

			// ftransform() = gl_ModelViewProjectionMatrix * gl_Vertex
			transformer.renameFunctionCall("ftransform", "iris_ftransform");
			transformer.injectFunction("vec4 iris_ftransform() { return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex; }");

			// gl_MultiTexCoord1 and gl_MultiTexCoord2 are both lightmap
			if (parameters.inputs.lightmap) {
				transformer.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
			}

			// A HashMap, as in the TauMC engine: the replacements run in its iteration order, which is the same here.
			Map<String, String> texCoordReplacements = new HashMap<>();
			if (!parameters.inputs.lightmap) {
				texCoordReplacements.put("gl_MultiTexCoord1", "vec4(240.0, 240.0, 0.0, 1.0)");
				texCoordReplacements.put("gl_MultiTexCoord2", "vec4(240.0, 240.0, 0.0, 1.0)");
			}
			if (!parameters.inputs.texture) {
				texCoordReplacements.put("gl_MultiTexCoord0", "vec4(240.0, 240.0, 0.0, 1.0)");
			}
			texCoordReplacements.forEach(transformer::replaceExpression);

			// Rename remaining gl_MultiTexCoord references to iris_ versions
			transformer.rename("gl_MultiTexCoord0", "iris_MultiTexCoord0");
			transformer.rename("gl_MultiTexCoord1", "iris_MultiTexCoord1");

			// gl_MultiTexCoord3 as Iris 26.1 handles it (Step 7b); the TauMC engine patched only a declared one, and
			// declared mc_midTexCoord twice then.
			CommonTransformer.patchMultiTexCoord3(transformer, parameters, "in vec4 mc_midTexCoord;");
		}
	}
}
