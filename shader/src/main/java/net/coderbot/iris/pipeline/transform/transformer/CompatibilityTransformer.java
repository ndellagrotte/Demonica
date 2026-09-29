package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.EmptyDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.Template;
import io.github.douira.glsl_transformer.ast.transform.TransformationException;
import io.github.douira.glsl_transformer.parser.ParseShape;
import io.github.douira.glsl_transformer.util.Type;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Driver-compatibility fixes. Per stage ({@link #transformEach}): a Sildur's water patch, unused functions removed,
 * {@code const} dropped where a {@code const} parameter initializes a declaration (ported from the TauMC engine in
 * Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md, on {@link ShaderAst}'s verbs), and empty external
 * declarations removed (Iris 26.1, Step 12). Across stages ({@link #transformGrouped}): Iris 26.1's
 * {@code transformGrouped} (Step 12), which adds the {@code out} declarations a later stage's {@code in} needs,
 * initializes an {@code out} the previous stage never uses, and casts an {@code out} whose type differs from the
 * {@code in}, over the whole pipeline (vertex, tessellation control, tessellation evaluation, geometry, fragment).
 * The pre-parse text patches live in {@code net.coderbot.iris.pipeline.transform.CompatibilityPatches}.
 *
 * <p>Step 12 took the Iris 26.1 code without translation: {@link Upstream} holds it as Iris has it
 * ({@code net.irisshaders.iris.pipeline.transform.transformer.CompatibilityTransformer}), with three adaptations and
 * two fixes, each marked {@code Demonica:} in the code: it runs under {@link ShaderAst#BUILD_LOCK} (glsl-transformer 3.0.0-pre3
 * builds nodes on a static stack; the class is nested so that its matchers and templates are built when it is first
 * used, which is inside the lock), Demonica's {@link Parameters} has no program name (the warnings name the patch),
 * and only the empty-declaration block of Iris's {@code transformEach} is taken. The fixes: an unsigned {@code out} is
 * initialized with {@code 0u}, where Iris writes {@code 0}, which does not compile below GLSL 4.00; and the declarations
 * are visited in document order, where Iris's node-index order changes from run to run.
 * docs/glsl-transformer_adoption/PORTING_GUIDE.md describes the method.</p>
 */
public final class CompatibilityTransformer {

    private CompatibilityTransformer() {
    }

    public static void transformEach(ShaderAst transformer, Parameters parameters) {
        if (parameters.type == ShaderType.VERTEX) {
            // TODO: sildur's jankness
            // This is a hacky patch for sildur's shaders that changes the way it does it's waving water to make it work better?
            // Why is this the GLSL transformation code in Iris, tell sildur to fix it and remove this?
            // it's still there in current, modern Iris
            // See https://github.com/IrisShaders/Iris/issues/509
            transformer.replaceExpression("fract(worldpos.y + 0.001)", "fract(worldpos.y + 0.01)");
        }

        /*
         * Removes const storage qualifier from declarations in functions if they are
         * initialized with const parameters. Const parameters are immutable parameters
         * and can't be used to initialize const declarations because they expect
         * constant, not just immutable, expressions. This varies between drivers and
         * versions. Also removes the const qualifier from declarations that use the
         * identifiers from which the declaration was removed previously.
         * See https://wiki.shaderlabs.org/wiki/Compiler_Behavior_Notes
         */
        transformer.removeUnusedFunctions();
        transformer.removeConstAssignment();

        // Step 12: Iris 26.1's empty-declaration removal. The TauMC engine carried it commented out ("glsl-transformation-lib
        // doesn't have a way to identify empty declarations"); a ';' at file scope is a compile error before GLSL 4.60
        // (glslangValidator: "'extraneous semicolon' : not supported for this version").
        transformer.build(() -> {
            Upstream.transformEach(transformer.t, transformer.tree, transformer.root, parameters);
            return null;
        });
    }

    /**
     * Iris 26.1's {@code transformGrouped} on the stages' trees ({@link Upstream#transformGrouped}), under
     * {@link ShaderAst#BUILD_LOCK}. The stages are the ones {@code ShaderTransformer} parsed; the parameters are the
     * last stage's, as in Iris (they only name the program in the warnings).
     */
    public static void transformGrouped(Map<PatchShaderType, ShaderAst> stages, Parameters parameters) {
        if (stages.isEmpty()) {
            return;
        }
        final Map<PatchShaderType, TranslationUnit> trees = new EnumMap<>(PatchShaderType.class);
        stages.forEach((type, ast) -> trees.put(type, ast.tree));
        final ShaderAst any = stages.values().iterator().next();
        any.build(() -> {
            Upstream.transformGrouped(any.t, trees, parameters);
            return null;
        });
    }

    /**
     * Iris 26.1's {@code CompatibilityTransformer} code that Demonica runs, copied as Iris has it (tabs, names,
     * comments and warnings included). Nested so that the matchers and templates, which glsl-transformer builds on its
     * static build stack, are built at the first use, inside {@link ShaderAst#BUILD_LOCK}; every caller holds it.
     */
    static final class Upstream {
	private static final Logger LOGGER = LogManager.getLogger(CompatibilityTransformer.class);

	private static final ShaderType[] pipeline = {ShaderType.VERTEX, ShaderType.TESSELATION_CONTROL, ShaderType.TESSELATION_EVAL, ShaderType.GEOMETRY, ShaderType.FRAGMENT};
	private static final Matcher<ExternalDeclaration> outDeclarationMatcher = new DeclarationMatcher(
		StorageType.OUT);
	private static final Matcher<ExternalDeclaration> inDeclarationMatcher = new DeclarationMatcher(
		StorageType.IN);
	private static final String tagPrefix = "iris_template_";
	private static final Template<ExternalDeclaration> declarationTemplate = Template
		.withExternalDeclaration("out __type __name;");
	private static final Template<Statement> initTemplate = Template.withStatement("__decl = __value;");
	private static final Template<ExternalDeclaration> variableTemplate = Template
		.withExternalDeclaration("__type __internalDecl;");
	private static final Template<Statement> statementTemplate = Template
		.withStatement("__oldDecl = vec3(__internalDecl);");
	private static final Template<Statement> statementTemplateVector = Template
		.withStatement("__oldDecl = vec3(__internalDecl, vec4(0));");

	static {
		declarationTemplate
			.markLocalReplacement(declarationTemplate.getSourceRoot().nodeIndex.getUnique(TypeQualifier.class));
		declarationTemplate.markLocalReplacement("__type", TypeSpecifier.class);
		declarationTemplate.markIdentifierReplacement("__name");
		initTemplate.markIdentifierReplacement("__decl");
		initTemplate.markLocalReplacement("__value", ReferenceExpression.class);
		variableTemplate.markLocalReplacement("__type", TypeSpecifier.class);
		variableTemplate.markIdentifierReplacement("__internalDecl");
		statementTemplate.markIdentifierReplacement("__oldDecl");
		statementTemplate.markIdentifierReplacement("__internalDecl");
		statementTemplate.markLocalReplacement(
			statementTemplate.getSourceRoot().nodeIndex.getStream(BuiltinNumericTypeSpecifier.class)
				.filter(specifier -> specifier.type == Type.F32VEC3).findAny().get());
		statementTemplateVector.markIdentifierReplacement("__oldDecl");
		statementTemplateVector.markIdentifierReplacement("__internalDecl");
		statementTemplateVector.markLocalReplacement(
			statementTemplateVector.getSourceRoot().nodeIndex.getStream(BuiltinNumericTypeSpecifier.class)
				.filter(specifier -> specifier.type == Type.F32VEC3).findAny().get());
	}

	// Demonica: of Iris's transformEach only the empty-declaration block. Its other blocks: Sildur's water patch,
	// unused-function removal and const-parameter handling run on ShaderAst's verbs above (TauMC parity, Step 5); the
	// reserved-word renaming of texture and sample is done by GlslTransformUtils's regex passes before the parse
	// (Step 12 report, "the reserved-word question"); the move of unsized array specifiers on struct members is not
	// ported.
	public static void transformEach(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
		// remove empty external declarations
		boolean emptyDeclarationHit = root.process(
			root.nodeIndex.getStream(EmptyDeclaration.class),
			ASTNode::detachAndDelete);
		if (emptyDeclarationHit) {
			LOGGER.warn(
				"Removed empty external declarations (\";\").");
		}
	}

	private static Statement getInitializer(Root root, String name, Type type) {
		return initTemplate.getInstanceFor(root,
			new Identifier(name),
			type.isScalar()
				? getDefaultValue(type)
				: root.indexNodes(() -> new FunctionCallExpression(
				new Identifier(type.getMostCompactName()),
				Stream.of(getDefaultValue(type)))));
	}

	// Demonica: glsl-transformer 3.0.0-pre3's LiteralExpression.getDefaultValue gives an int 0 for unsigned types, and
	// 'id = 0;' for a uint does not compile before GLSL 4.00, which has no implicit int-to-uint conversion
	// (glslangValidator at 330: "cannot convert from ' const int' to ' flat out uint'"). The TauMC engine wrote 0u;
	// so does this. Every other type gets glsl-transformer's value, as in Iris.
	private static LiteralExpression getDefaultValue(Type type) {
		return type.getNumberType() == Type.NumberType.UNSIGNED_INTEGER
			? new LiteralExpression(Type.UINT32, 0)
			: LiteralExpression.getDefaultValue(type);
	}

	private static TypeQualifier makeQualifierOut(TypeQualifier typeQualifier) {
		for (TypeQualifierPart qualifierPart : typeQualifier.getParts()) {
			if (qualifierPart instanceof StorageQualifier storageQualifier) {
				if (storageQualifier.storageType == StorageType.IN) {
					storageQualifier.storageType = StorageType.OUT;
				}
			}
		}
		return typeQualifier;
	}

	// Demonica: Parameters has no program name (Iris: parameters.name); the warnings name the patch.
	private static String programName(Parameters parameters) {
		return parameters == null ? "(no parameters)" : String.valueOf(parameters.patch);
	}

	// Demonica: Iris iterates root.nodeIndex.get(DeclarationExternalDeclaration.class), a HashSet of nodes in identity-hash
	// order, which changes from run to run, and so would the order of the injected declarations and initializations. The
	// two loops below go over the file-scope declarations in document order instead: the output is the same every time.
	private static List<DeclarationExternalDeclaration> inDocumentOrder(TranslationUnit tree) {
		return tree.getChildren().stream()
			.filter(DeclarationExternalDeclaration.class::isInstance)
			.map(DeclarationExternalDeclaration.class::cast)
			.toList();
	}

	// does transformations that require cross-shader type data
	public static void transformGrouped(
		ASTParser t,
		Map<PatchShaderType, TranslationUnit> trees,
		Parameters parameters) {
		/*
		  find attributes that are declared as "in" in geometry or fragment but not
		  declared as "out" in the previous stage. The missing "out" declarations for
		  these attributes are added and initialized.

		  It doesn't bother with array specifiers because they are only legal in
		  geometry shaders, but then also only as an in declaration. The out
		  declaration in the vertex shader is still just a single value. Missing out
		  declarations in the geometry shader are also just normal.

		  TODO:
		  - fix issues where Iris' own declarations are detected and patched like
		  iris_FogFragCoord if there are geometry shaders present
		  - improved geometry shader support? They use funky declarations
		 */
		ShaderType prevType = null;
		for (ShaderType type : pipeline) {
			PatchShaderType[] patchTypes = PatchShaderType.fromGlShaderType(type);

			// check if the patch types have sources and continue if not
			boolean hasAny = false;
			for (PatchShaderType currentType : patchTypes) {
				if (trees.get(currentType) != null) {
					hasAny = true;
				}
			}
			if (!hasAny) {
				continue;
			}

			// if the current type has sources but the previous one doesn't, set the
			// previous one and continue
			if (prevType == null) {
				prevType = type;
				continue;
			}

			PatchShaderType prevPatchTypes = PatchShaderType.fromGlShaderType(prevType)[0];
			TranslationUnit prevTree = trees.get(prevPatchTypes);
			Root prevRoot = prevTree.getRoot();

			// test if the prefix tag is used for some reason
			if (prevRoot.getPrefixIdentifierIndex().prefixQueryFlat(tagPrefix).findAny().isPresent()) {
				LOGGER.warn("The prefix tag " + tagPrefix + " is used in the shader, bailing compatibility transformation.");
				return;
			}

			// find out declarations
			Map<String, BuiltinNumericTypeSpecifier> outDeclarations = new HashMap<>();
			for (DeclarationExternalDeclaration declaration : inDocumentOrder(prevTree)) {
				if (outDeclarationMatcher.matchesExtract(declaration)) {
					BuiltinNumericTypeSpecifier extractedType = outDeclarationMatcher.getNodeMatch("type",
						BuiltinNumericTypeSpecifier.class);
					for (DeclarationMember member : outDeclarationMatcher
						.getNodeMatch("name*", DeclarationMember.class)
						.getAncestor(TypeAndInitDeclaration.class)
						.getMembers()) {
						String name = member.getName().getName();
						if (!name.startsWith("gl_")) {
							outDeclarations.put(name, extractedType);
						}
					}
				}
			}

			// add out declarations that are missing for in declarations
			for (PatchShaderType currentType : patchTypes) {
				TranslationUnit currentTree = trees.get(currentType);
				if (currentTree == null) {
					continue;
				}
				Root currentRoot = currentTree.getRoot();

				for (ExternalDeclaration declaration : inDocumentOrder(currentTree)) {
					if (!inDeclarationMatcher.matchesExtract(declaration)) {
						continue;
					}

					BuiltinNumericTypeSpecifier inTypeSpecifier = inDeclarationMatcher.getNodeMatch("type",
						BuiltinNumericTypeSpecifier.class);
					for (DeclarationMember inDeclarationMember : inDeclarationMatcher
						.getNodeMatch("name*", DeclarationMember.class)
						.getAncestor(TypeAndInitDeclaration.class)
						.getMembers()) {
						String name = inDeclarationMember.getName().getName();
						if (name.startsWith("gl_")) {
							continue;
						}

						// patch missing declarations with an initialization
						if (!outDeclarations.containsKey(name)) {
							// make sure the declared in is actually used
							if (currentRoot.identifierIndex.getAncestors(name, ReferenceExpression.class).findAny().isEmpty()) {
								continue;
							}

							if (inTypeSpecifier == null) {
								LOGGER.warn(
									"The in declaration '" + name + "' in the " + programName(parameters) + " " + currentType.glShaderType.name()
										+ " shader that has a missing corresponding out declaration in the previous stage "
										+ prevType.name()
										+ " has a non-numeric type and could not be compatibility-patched. See debugging.md for more information.");
								continue;
							}
							Type inType = inTypeSpecifier.type;

							// insert the new out declaration but copy over the type qualifiers, except for
							// the in/out qualifier
							TypeQualifier outQualifier = (TypeQualifier) inDeclarationMatcher
								.getNodeMatch("qualifier").cloneInto(prevRoot);
							makeQualifierOut(outQualifier);
							prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, declarationTemplate.getInstanceFor(prevRoot,
								outQualifier,
								inTypeSpecifier.cloneInto(prevRoot),
								new Identifier(name)));

							// add the initializer to the main function
							prevTree.prependMainFunctionBody(getInitializer(prevRoot, name, inType));

							// update out declarations to prevent duplicates
							outDeclarations.put(name, null);

							LOGGER.warn(
								"The in declaration '" + name + "' in the " + programName(parameters) + " " + currentType.glShaderType.name()
									+ " shader is missing a corresponding out declaration in the previous stage "
									+ prevType.name()
									+ " and has been compatibility-patched. See debugging.md for more information.");
						}

						// patch mismatching declaration with a local variable and a cast
						else {
							// there is an out declaration for this in declaration, check if the types match
							BuiltinNumericTypeSpecifier outTypeSpecifier = outDeclarations.get(name);

							// skip newly inserted out declarations
							if (outTypeSpecifier == null) {
								continue;
							}

							Type inType = inTypeSpecifier.type;
							Type outType = outTypeSpecifier.type;

							// check if the out declaration is an array-type, if so, skip it.
							// this only checks the out declaration because it's the one that when it's an
							// array type means that both declarations are arrays and we're not just in the
							// case of a geometry shader where the in declaration is an array and the out
							// declaration is not
							if (outTypeSpecifier.getArraySpecifier() != null) {
								LOGGER.warn(
									"The out declaration '" + name + "' in the " + programName(parameters) + " " + prevPatchTypes.glShaderType.name()
										+ " shader that has a missing corresponding in declaration in the next stage "
										+ type.name()
										+ " has an array type and could not be compatibility-patched. See debugging.md for more information.");
								continue;
							}

							// skip if the type matches, nothing has to be done
							if (inType == outType) {
								// if the types match but it's never assigned a value,
								// an initialization is added
								if (prevRoot.identifierIndex.get(name).size() > 1) {
									continue;
								}

								// add an initialization statement for this declaration
								prevTree.prependMainFunctionBody(getInitializer(prevRoot, name, inType));
								outDeclarations.put(name, null);

								LOGGER.warn(
									"The in declaration '" + name + "' in the " + programName(parameters) + " " + currentType.glShaderType.name()
										+ " shader that is never assigned to in the previous stage "
										+ prevType.name()
										+ " has been compatibility-patched by adding an initialization for it. See debugging.md for more information.");
								continue;
							}

							// bail and warn on mismatching dimensionality
							if (outType.getDimension() != inType.getDimension()) {
								LOGGER.warn(
									"The in declaration '" + name + "' in the " + programName(parameters) + " " + currentType.glShaderType.name()
										+ " shader has a mismatching dimensionality (scalar/vector/matrix) with the out declaration in the previous stage "
										+ prevType.name()
										+ " and could not be compatibility-patched. See debugging.md for more information.");
								continue;
							}

							boolean isVector = outType.isVector();

							// rename all references of this out declaration to a new name (iris_)
							String newName = tagPrefix + name;
							prevRoot.identifierIndex.rename(name, newName);

							// rename the original out declaration back to the original name
							TypeAndInitDeclaration outDeclaration = outTypeSpecifier.getAncestor(TypeAndInitDeclaration.class);
							if (outDeclaration == null) {
								continue;
							}

							List<DeclarationMember> outMembers = outDeclaration.getMembers();
							DeclarationMember outMember = null;
							for (DeclarationMember member : outMembers) {
								if (member.getName().getName().equals(newName)) {
									outMember = member;
								}
							}
							if (outMember == null) {
								throw new TransformationException("The targeted out declaration member is missing!");
							}
							outMember.getName().replaceByAndDelete(new Identifier(name));

							// move the declaration member out of the declaration in case there is more than
							// one member to avoid changing the other member's type as well.
							if (outMembers.size() > 1) {
								outMember.detach();
								outTypeSpecifier = outTypeSpecifier.cloneInto(prevRoot);
								DeclarationExternalDeclaration singleOutDeclaration = (DeclarationExternalDeclaration) declarationTemplate
									.getInstanceFor(prevRoot,
										makeQualifierOut(outDeclaration.getType().getTypeQualifier().cloneInto(prevRoot)),
										outTypeSpecifier,
										new Identifier(name));
								((TypeAndInitDeclaration) singleOutDeclaration.getDeclaration()).getMembers().set(0, outMember);
								prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, singleOutDeclaration);
							}

							// add a global variable with the new name and the old type
							prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, variableTemplate.getInstanceFor(prevRoot,
								outTypeSpecifier.cloneInto(prevRoot),
								new Identifier(newName)));

							// insert a statement at the end of the main function that sets the value of the
							// out declaration to the value of the global variable and does a type cast
							prevTree.appendMainFunctionBody(
								(isVector && outType.getDimensions()[0] < inType.getDimensions()[0] ? statementTemplateVector
									: statementTemplate).getInstanceFor(prevRoot,
									new Identifier(name),
									new Identifier(newName),
									inTypeSpecifier.cloneInto(prevRoot)));

							// make the out declaration use the same type as the fragment shader
							outTypeSpecifier.replaceByAndDelete(inTypeSpecifier.cloneInto(prevRoot));

							// don't do the patch twice
							outDeclarations.put(name, null);

							LOGGER.warn(
								"The out declaration '" + name + "' in the " + programName(parameters) + " " + prevType.name()
									+ " shader has a different type " + outType.getMostCompactName()
									+ " than the corresponding in declaration of type " + inType.getMostCompactName()
									+ " in the following stage " + currentType.glShaderType.name()
									+ " and has been compatibility-patched. See debugging.md for more information.");
						}
					}
				}
			}

			prevType = type;
		}
	}

	private static class DeclarationMatcher extends Matcher<ExternalDeclaration> {
		private final StorageType storageType;

		{
			markClassWildcard("qualifier", pattern.getRoot().nodeIndex.getUnique(TypeQualifier.class));
			markClassWildcard("type", pattern.getRoot().nodeIndex.getUnique(BuiltinNumericTypeSpecifier.class));
			markClassWildcard("name*",
				pattern.getRoot().identifierIndex.getUnique("name").getAncestor(DeclarationMember.class));
		}

		public DeclarationMatcher(StorageType storageType) {
			super("out float name;", ParseShape.EXTERNAL_DECLARATION);
			this.storageType = storageType;
		}

		@Override
		public boolean matchesExtract(ExternalDeclaration tree) {
			boolean result = super.matchesExtract(tree);
			if (!result) {
				return false;
			}
			TypeQualifier qualifier = getNodeMatch("qualifier", TypeQualifier.class);
			for (TypeQualifierPart part : qualifier.getParts()) {
				if (part instanceof StorageQualifier storageQualifier) {
					if (storageQualifier.storageType == storageType) {
						return true;
					}
				}
			}
			return false;
		}
	}
    }
}
