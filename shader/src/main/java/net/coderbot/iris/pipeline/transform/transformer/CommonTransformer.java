package net.coderbot.iris.pipeline.transform.transformer;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.HashSet;
import java.util.Set;

/**
 * The transformation every patch kind starts with, on {@link ShaderAst}: legacy built-ins ({@code gl_FogFragCoord},
 * {@code gl_FrontColor}, {@code gl_Color}, {@code gl_FragColor}, {@code gl_FragData}, {@code gl_Fog}) become
 * declared {@code iris_*} variables, a sampler named {@code texture} or {@code gcolor} becomes {@code gtexture}, the
 * legacy texture functions get their core names, {@code shadow2D}/{@code shadow2DLod} become wrapped
 * {@code texture}/{@code textureLod} calls, and recognized PCF shadow helpers get a bounds guard
 * ({@link AdaptiveShadowBoundsTransformer}, Step 7). Ported verb for verb from the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.CommonTransformer} (Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md).
 */
public final class CommonTransformer {
	private CommonTransformer() {
	}

	/**
	 * {@code gl_MultiTexCoord3}, OptiFine's old alias of {@code mc_midTexCoord}, in a vertex shader, as Iris 26.1's
	 * {@code CommonTransformer.patchMultiTexCoord3} handles it, with one fix (Step 7b of
	 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md, report S7b-hardening.md): if the shader uses
	 * {@code gl_MultiTexCoord3} at all (declared or as the built-in, which a core profile does not have) and does not
	 * declare {@code mc_midTexCoord}, every {@code gl_MultiTexCoord3} becomes {@code mc_midTexCoord}, and
	 * {@code declaration} declares it, unless the shader declared {@code gl_MultiTexCoord3} itself: its renamed
	 * declaration is then the one (Iris declares it again, and so did the TauMC engine, giving two declarations).
	 *
	 * <p>The TauMC engine tested {@code hasVariable} for both names (declared), so a shader that read the built-in was
	 * not patched and kept {@code gl_MultiTexCoord3} in a core-profile program; Iris 26.1 tests any use for both. This
	 * tests any use of {@code gl_MultiTexCoord3}, as Iris, and a declaration of {@code mc_midTexCoord}, as TauMC: a
	 * shader that reads {@code mc_midTexCoord} without declaring it gets the declaration it lacks. A shader that reads
	 * the built-in {@code gl_MultiTexCoord3} and declares {@code mc_midTexCoord} is left alone, by all three (report
	 * S7b, Open questions).</p>
	 */
	public static void patchMultiTexCoord3(ShaderAst ast, Parameters parameters, String declaration) {
		if (parameters.type == ShaderType.VERTEX && ast.root.identifierIndex.has("gl_MultiTexCoord3")
			&& !ast.hasVariable("mc_midTexCoord")) {
			final boolean declared = ast.hasVariable("gl_MultiTexCoord3");
			ast.rename("gl_MultiTexCoord3", "mc_midTexCoord");
			if (!declared) {
				ast.injectVariable(declaration);
			}
		}
	}

	/**
	 * Iris 26.1's {@code CommonTransformer.replaceGlMultiTexCoordBounded}: every {@code gl_MultiTexCoord<i>} with
	 * {@code minimum <= i <= maximum} read as an expression becomes {@code vec4(0.0, 0.0, 0.0, 1.0)}, the initial value
	 * of a texture coordinate the fixed-function pipeline never set. Iris calls it for 4-7 in its VANILLA and SODIUM
	 * vertex shaders (Demonica's ATTRIBUTES and CELERITAS_TERRAIN) and both DH patches, and for 1-7 in COMPOSITE.
	 */
	public static void replaceGlMultiTexCoordBounded(ShaderAst ast, int minimum, int maximum) {
		// Demonica: written with the ShaderAst verb (the loop DHTerrainTransformer and DHGenericTransformer had as
		// private copies), not as Iris's root.replaceReferenceExpressions over a prefix query: every caller runs it
		// between other verbs, which PORTING_GUIDE rule 3 keeps idiom code out of, and the verb leaves the DH output
		// as it was (dh-terrain-legacy, dh-generic-legacy). Both replace only reference expressions, so a pack's own
		// declaration of the name stays, as in Iris; the exact names also skip Iris's Integer.parseInt of the suffix,
		// which throws on a name such as gl_MultiTexCoordX.
		for (int i = minimum; i <= maximum; i++) {
			ast.replaceExpression("gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
		}
	}

	public static void transform(ShaderAst root, Parameters parameters, boolean core, int glslVersion) {
		root.rename("gl_FogFragCoord", "iris_FogFragCoord");
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out float iris_FogFragCoord;");
			root.prependMain("iris_FogFragCoord = 0.0f;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in float iris_FogFragCoord;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
			// Legacy packs read gl_Color in the composite vertex stage (e.g. Sildur's
			// Enhanced Default). The full-screen quad has no color attribute, so map it
			// to the same white-initialized out variable that gl_FrontColor uses.
			// Only COMPOSITE: other patches map gl_Color themselves (ATTRIBUTES to the
			// vertex color / modulator, Celeritas and DH terrain to _vert_color).
			if (parameters.patch == Patch.COMPOSITE) {
				root.rename("gl_Color", "iris_FrontColor");
			}
			root.prependMain("iris_FrontColor = vec4(1.0);");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in vec4 iris_FrontColor;");
			root.rename("gl_Color", "iris_FrontColor");
		}

		if (parameters.type == ShaderType.FRAGMENT) {
			if (root.containsCall("gl_FragColor")) {
				root.replaceExpression("gl_FragColor", "gl_FragData[0]");
			}

			if (core) {
				// Core profile: gl_FragData doesn't exist. Flatten gl_FragData[N] → iris_FragDataN with layout-qualified out declarations.
				Set<Integer> found = new HashSet<>();
				root.renameArray("gl_FragData", "iris_FragData", found);

				for (Integer i : found) {
					root.injectVariable("layout (location = " + i + ") out vec4 iris_FragData" + i + ";");
				}

				// Core profile: GL_ALPHA_TEST is removed. 1.7.10 engine relies on alpha test to discard transparent fragments. Inject
				// runtime discard using the GLSM-tracked alpha reference value.
				if (found.contains(0) && parameters.patch != Patch.COMPOSITE && parameters.patch != Patch.COMPUTE) {
					root.injectVariable("uniform float iris_currentAlphaTest;");
					root.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;");
				}
			}
		}

		if (root.containsCall("texture") && root.hasVariable("texture")) {
			root.rename("texture", "gtexture");
		}

		if (root.hasVariable("actinium_renamed_texture")) {
			root.rename("actinium_renamed_texture", "gtexture");
		}

		if (root.containsCall("gcolor") && root.hasVariable("gcolor")) {
			root.rename("gcolor", "gtexture");
		}

		root.rename("gl_Fog", "iris_Fog");
		root.injectVariable("uniform float iris_FogDensity;");
		root.injectVariable("uniform float iris_FogStart;");
		root.injectVariable("uniform float iris_FogEnd;");
		root.injectVariable("uniform vec4 iris_FogColor;");
		root.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
		root.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");

		root.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES);
		root.renameAndWrapShadow("shadow2D", "texture");
		root.renameAndWrapShadow("shadow2DLod", "textureLod");
		AdaptiveShadowBoundsTransformer.transform(root, parameters.type);

		if (parameters.patch == Patch.ATTRIBUTES && parameters.type == ShaderType.VERTEX) {
			root.injectVariable("uniform bool actinium_ClipPlanesEnabled;");
			root.injectVariable("uniform vec4 actinium_ClipPlane[8];");
			root.appendMain(
				"{ if (actinium_ClipPlanesEnabled) { vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; "
				+ "gl_ClipDistance[0] = dot(actinium_ClipPlane[0], _cp_ep); "
				+ "gl_ClipDistance[1] = dot(actinium_ClipPlane[1], _cp_ep); "
				+ "gl_ClipDistance[2] = dot(actinium_ClipPlane[2], _cp_ep); "
				+ "gl_ClipDistance[3] = dot(actinium_ClipPlane[3], _cp_ep); "
				+ "gl_ClipDistance[4] = dot(actinium_ClipPlane[4], _cp_ep); "
				+ "gl_ClipDistance[5] = dot(actinium_ClipPlane[5], _cp_ep); "
				+ "gl_ClipDistance[6] = dot(actinium_ClipPlane[6], _cp_ep); "
				+ "gl_ClipDistance[7] = dot(actinium_ClipPlane[7], _cp_ep); } }"
			);
		}
	}
}
