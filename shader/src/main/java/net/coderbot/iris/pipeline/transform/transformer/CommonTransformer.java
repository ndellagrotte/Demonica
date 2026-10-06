package net.coderbot.iris.pipeline.transform.transformer;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier.BuiltinType.TypeKind;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.parser.ParseShape;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The transformation every patch kind starts with, on {@link ShaderAst}: legacy built-ins ({@code gl_FogFragCoord},
 * {@code gl_FrontColor}, {@code gl_Color}, {@code gl_FragColor}, {@code gl_FragData}, {@code gl_Fog}) become
 * declared {@code iris_*} variables, the legacy texture functions get their core names,
 * {@code shadow2D}/{@code shadow2DLod} become wrapped {@code texture}/{@code textureLod} calls, and recognized PCF shadow helpers get a bounds guard
 * ({@link AdaptiveShadowBoundsTransformer}, Step 7). Ported verb for verb from the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.CommonTransformer} (Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md). {@link #renameGtexture}, which {@code ShaderTransformer} runs after
 * the patch transformer, makes a sampler named {@code texture} or {@code gcolor} {@code gtexture}.
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

		// Demonica: the renaming of the texture and gcolor samplers to gtexture is renameGtexture, which
		// ShaderTransformer.doTransform runs after the patch transformer's verbs (PORTING_GUIDE rule 3) and before
		// TextureTransformer, as Iris runs it before its TextureTransformer.

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

	/**
	 * The name {@code GlslTransformUtils.replaceTexture} gives every {@code texture} that is not a call before the parse
	 * ({@code restoreReservedWords} gives it back at print), so the sampler a pack declares as {@code texture} is this
	 * name in the AST.
	 */
	static final String RENAMED_TEXTURE = "actinium_renamed_texture";

	/**
	 * Iris 26.1's {@code gtexture} renaming ({@code CommonTransformer.transform} and {@code getGtextureRenameTargets},
	 * {@link Upstream#renameGtexture}): a {@code uniform} sampler named {@code texture} or {@code gcolor} becomes
	 * {@code gtexture} with every use of the name that is not a call, and a shader that declares both keeps one
	 * declaration, {@code gcolor}'s (plan item 3.2; before it, both were renamed and the two {@code gtexture}
	 * declarations did not compile). A name that some file-scope declaration declares as anything but a sampler
	 * uniform is left alone, as is a name with no such declaration. Runs on the tree, under {@link ShaderAst#build},
	 * after the patch transformer's verbs (PORTING_GUIDE rule 3).
	 *
	 * <p>Then, as before, a {@code texture} the merge left alone that is still declared somewhere (a local variable, a
	 * file-scope declaration that is not a sampler uniform) becomes {@code gtexture} through the {@link ShaderAst#rename}
	 * verb: restored to {@code texture} at print, such a variable would hide the {@code texture()} function that the
	 * legacy calls are renamed to.</p>
	 */
	public static void renameGtexture(ShaderAst ast) {
		ast.build(() -> {
			Upstream.renameGtexture(ast.tree, ast.root);
			return null;
		});
		// Demonica: the TauMC engine's branch for the pre-parse name, kept for a texture the merge does not take
		// (Iris leaves it, and the restored name would hide the texture() builtin in its scope). After the merge took the
		// name no identifier has it any more (the pre-pass leaves calls named texture), so this does nothing.
		if (ast.hasVariable(RENAMED_TEXTURE)) {
			ast.rename(RENAMED_TEXTURE, "gtexture");
		}
	}

	/**
	 * Iris 26.1's {@code gtexture} code, copied as Iris has it ({@code CommonTransformer.transform}'s "addition" block,
	 * {@code getGtextureRenameTargets}, {@code RenameTargetResult} and the {@code sampler} matcher). Nested so that the
	 * matcher, which glsl-transformer builds on its static build stack and which keeps the last match's nodes, is built
	 * at the first use, inside {@link ShaderAst#BUILD_LOCK}; every caller holds it (PORTING_GUIDE rule 1).
	 */
	static final class Upstream {
	public static final Matcher<ExternalDeclaration> sampler = new Matcher<>(
		"uniform Type name;", ParseShape.EXTERNAL_DECLARATION) {
		{
			markClassedPredicateWildcard("type",
				pattern.getRoot().identifierIndex.getUnique("Type").getAncestor(TypeSpecifier.class),
				BuiltinFixedTypeSpecifier.class,
				specifier -> specifier.type.kind == TypeKind.SAMPLER);
			markClassWildcard("name*",
				pattern.getRoot().identifierIndex.getUnique("name").getAncestor(DeclarationMember.class));
		}
	};

	// Demonica: the block of Iris's CommonTransformer.transform, as a method of its own (Iris has the tree and root in
	// transform's parameters).
	static void renameGtexture(TranslationUnit tree, Root root) {
		// addition: rename all uses of texture and gcolor to gtexture if it's *not*
		// used as a function call.
		// it only does this if they are declared as samplers and makes sure that there
		// is only one sampler declaration.
		RenameTargetResult gcolorResult = getGtextureRenameTargets("gcolor", tree, root);
		// Demonica: the pre-parse name of texture (RENAMED_TEXTURE), the name the AST has; Iris parses texture itself.
		RenameTargetResult textureResult = getGtextureRenameTargets(RENAMED_TEXTURE, tree, root);
		DeclarationMember samplerDeclarationMember = null;
		Stream<Identifier> targets = Stream.empty();
		if (gcolorResult != null) {
			samplerDeclarationMember = gcolorResult.samplerDeclarationMember;
			targets = Stream.concat(targets, gcolorResult.targets);
		}
		if (textureResult != null) {
			// if two exist, remove the member from the second one
			if (samplerDeclarationMember == null) {
				samplerDeclarationMember = textureResult.samplerDeclarationMember;
			} else {
				DeclarationMember secondDeclarationMember = textureResult.samplerDeclarationMember;
				if (((TypeAndInitDeclaration) secondDeclarationMember.getParent()).getMembers().size() == 1) {
					textureResult.samplerDeclaration.detachAndDelete();
				} else {
					secondDeclarationMember.detachAndDelete();
				}
			}
			targets = Stream.concat(targets, textureResult.targets);
		}
		if (samplerDeclarationMember != null) {
			samplerDeclarationMember.getName().setName("gtexture");
		}
		root.process(targets.filter(id -> !(id.getParent() instanceof FunctionCallExpression)),
			id -> id.setName("gtexture"));
	}

	// Demonica: takes the tree for inDocumentOrder.
	private static RenameTargetResult getGtextureRenameTargets(String name, TranslationUnit tree, Root root) {
		List<Identifier> gtextureTargets = new ArrayList<>();
		DeclarationExternalDeclaration samplerDeclaration = null;
		DeclarationMember samplerDeclarationMember = null;

		// collect targets until we find out if the name is a sampler or not
		// Demonica: in document order (inDocumentOrder), not the index's.
		for (Identifier id : inDocumentOrder(tree, root, name)) {
			gtextureTargets.add(id);
			if (samplerDeclaration != null) {
				continue;
			}
			DeclarationExternalDeclaration externalDeclaration = (DeclarationExternalDeclaration) id.getAncestor(
				3, 0, DeclarationExternalDeclaration.class::isInstance);
			if (externalDeclaration == null) {
				continue;
			}
			if (sampler.matchesExtract(externalDeclaration)) {
				// check that any of the members match the name
				boolean foundNameMatch = false;
				for (DeclarationMember member : sampler
					.getNodeMatch("name*", DeclarationMember.class)
					.getAncestor(TypeAndInitDeclaration.class).getMembers()) {
					if (member.getName().getName().equals(name)) {
						foundNameMatch = true;
					}
				}
				if (!foundNameMatch) {
					return null;
				}

				// no need to check any more declarations
				samplerDeclaration = externalDeclaration;
				samplerDeclarationMember = id.getAncestor(DeclarationMember.class);

				// remove since we are treating the declaration specially
				gtextureTargets.removeLast();
				continue;
			}
			// we found a declaration using this name, but it's not a sampler,
			// renaming this name is disabled
			return null;
		}
		if (samplerDeclaration == null) {
			// no sampler declaration found, renaming this name is disabled
			return null;
		}
		return new RenameTargetResult(samplerDeclaration, samplerDeclarationMember, gtextureTargets.stream());
	}

	// Demonica: Iris iterates root.identifierIndex.get(name), a HashSet in no fixed order (ShaderAst's root, as Iris's), and
	// the first file-scope declaration of the name it meets decides: a name declared both as a sampler uniform and as
	// something else would be renamed in one run and not in the next (PORTING_GUIDE rule 2). The identifiers are taken
	// in the document order of the external declarations they sit in. Two identifiers in the same external declaration
	// meet the same answer, so their order does not matter.
	private static List<Identifier> inDocumentOrder(TranslationUnit tree, Root root, String name) {
		List<Identifier> identifiers = new ArrayList<>(root.identifierIndex.get(name));
		if (identifiers.size() < 2) {
			return identifiers;
		}
		Map<ASTNode, Integer> positions = new IdentityHashMap<>();
		List<ExternalDeclaration> children = tree.getChildren();
		for (int i = 0; i < children.size(); i++) {
			positions.put(children.get(i), i);
		}
		Map<Identifier, Integer> keys = new IdentityHashMap<>();
		for (Identifier identifier : identifiers) {
			ASTNode node = identifier;
			while (node != null && !positions.containsKey(node)) {
				node = node.getParent();
			}
			keys.put(identifier, node == null ? Integer.MAX_VALUE : positions.get(node));
		}
		identifiers.sort((a, b) -> Integer.compare(keys.get(a), keys.get(b)));
		return identifiers;
	}

	private record RenameTargetResult(DeclarationExternalDeclaration samplerDeclaration,
									  DeclarationMember samplerDeclarationMember, Stream<Identifier> targets) {
	}
	}
}
