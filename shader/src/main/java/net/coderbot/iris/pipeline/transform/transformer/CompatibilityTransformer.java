package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.Map;

/**
 * Driver-compatibility fixes on {@link ShaderAst}: per stage ({@link #transformEach}), a Sildur's water patch, unused
 * functions removed and {@code const} dropped where a {@code const} parameter initializes a declaration; across stages
 * ({@link #transformGrouped}), the {@code out} declarations a later stage's {@code in} needs. Ported from the TauMC
 * engine's {@code net.coderbot.iris.pipeline.transform.CompatibilityTransformer} (Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md); its pre-parse text patches are engine-neutral and live in
 * {@code net.coderbot.iris.pipeline.transform.CompatibilityPatches}.
 */
public final class CompatibilityTransformer {

    private static final ShaderType[] pipeline = {ShaderType.VERTEX, ShaderType.GEOMETRY, ShaderType.FRAGMENT};

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
    }

    /**
     * Finds the variables a geometry or fragment stage declares {@code in} that the previous stage does not declare
     * {@code out}, and adds and initializes those {@code out} declarations; an {@code out} of the same type that the
     * previous stage never assigns is initialized. TauMC's method line for line (it matched it on every fixture and
     * recorded stage group in Step 4's {@code ShaderAstParityTest.transformGrouped}, which this replaces):
     * {@link ShaderAst#findQualifiers} iterates in TauMC's {@code HashMap} order, which decides the injection order,
     * so its maps are iterated as returned; the types are compared as the source spells them
     * ({@link ShaderAst.QualifiedDeclaration#typeName()}: {@code mat2x2} and {@code mat2} differ, as in TauMC).
     *
     * <p>It doesn't bother with array specifiers because they are only legal in geometry shaders, but then also only
     * as an in declaration. The out declaration in the vertex shader is still just a single value. Missing out
     * declarations in the geometry shader are also just normal.</p>
     *
     * <p>TODO (from the TauMC engine): Iris' own declarations such as {@code iris_FogFragCoord} are detected and
     * patched too if there are geometry shaders present; geometry shaders' funky declarations.</p>
     *
     * @param parameters unused, as in TauMC
     */
    public static void transformGrouped(Map<PatchShaderType, ShaderAst> trees, Parameters parameters) {
        ShaderType prevType = null;
        for (ShaderType type : pipeline) {
            final PatchShaderType[] patchTypes = PatchShaderType.fromGlShaderType(type);

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

            final ShaderAst prev = trees.get(PatchShaderType.fromGlShaderType(prevType)[0]);

            // find out declarations
            final Map<String, ShaderAst.QualifiedDeclaration> outDec = prev.findQualifiers(StorageQualifier.StorageType.OUT);
            for (PatchShaderType currentType : patchTypes) {
                final ShaderAst current = trees.get(currentType);
                if (current == null) {
                    continue;
                }

                final Map<String, ShaderAst.QualifiedDeclaration> inDec = current.findQualifiers(StorageQualifier.StorageType.IN);
                for (String in : inDec.keySet()) {
                    if (in.startsWith("gl_")) {
                        continue;
                    }

                    if (!outDec.containsKey(in)) {
                        if (!current.containsCall(in)) {
                            continue;
                        }

                        final String outDeclaration = inDec.get(in).typeText() + " " + in + ";";
                        prev.injectVariable(outDeclaration.replaceFirst("\\bin\\b", "out"));

                        if (!prev.hasAssignment(in)) {
                            prev.initialize(inDec.get(in), in);
                        }
                    } else {
                        if (outDec.get(in).arraySpecifierText() != null) {
                            continue;
                        }

                        if (inDec.get(in).typeName().equals(outDec.get(in).typeName())) {
                            if (!prev.hasAssignment(in)) {
                                prev.initialize(inDec.get(in), in);
                            }
                        }
                    }
                }
            }
            prevType = type;
        }
    }
}
