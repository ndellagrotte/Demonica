package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.ShaderTransformer;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared core-profile transformation logic for matrix uniforms, texture matrices, and vertex attribute replacement, on
 * {@link ShaderAst}. Ported from the TauMC engine's class of the same name (Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md).
 */
public final class CoreTransformHelper {
    private CoreTransformHelper() {
    }

    /**
     * Inject matrix uniforms and rename FFP matrix builtins to iris_* equivalents.
     * Handles: ModelView, ModelViewInverse, Projection, ProjectionInverse, NormalMatrix, ModelViewProjectionMatrix, TextureMatrix[0..2], and LightmapTextureMatrix.
     */
    public static void injectMatrixUniforms(ShaderAst transformer) {
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrix;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrixInverse;");
        transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
        transformer.injectVariable("uniform mat4 iris_LightmapTextureMatrix;");
        transformer.injectVariable("uniform mat4 iris_TextureMatrix;");

        final Map<String, String> renames = new HashMap<>();
        renames.put("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        renames.put("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        renames.put("gl_ProjectionMatrix", "iris_ProjectionMatrix");
        renames.put("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");
        renames.put("gl_NormalMatrix", "iris_NormalMatrix");
        transformer.rename(renames);

        // A HashMap, as in the TauMC engine: the replacements run in its iteration order, which is the same here.
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        replacements.put("gl_TextureMatrix[0]", "iris_TextureMatrix");
        replacements.put("gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
        replacements.forEach(transformer::replaceExpression);

        // gl_TextureMatrix[2] is the lightmap matrix too, as gl_MultiTexCoord2 aliases gl_MultiTexCoord1 (Iris's
        // glTextureMatrix2, which VanillaCoreTransformer replaces with the same lightmap matrix as [1]).
        // Demonica: iris_LightmapTextureMatrix, as [1] gets here, where Iris writes the matrix out as a constant
        // (BuiltinReplacementUniforms holds the same values). Its own call after the HashMap, so the order of the
        // [0]/[1] replacements stays as it was. COMPOSITE gets it too, as it gets [0] and [1]; Iris's
        // CompositeTransformer makes [0]-[7] mat4(1.0) there.
        transformer.replaceExpression("gl_TextureMatrix[2]", "iris_LightmapTextureMatrix");

        // Catch any remaining gl_TextureMatrix references (e.g. [3]-[7], or a non-literal index)
        transformer.replaceExpression("gl_TextureMatrix", "mat4[8](iris_TextureMatrix, iris_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))");
    }

    /**
     * Inject vertex attributes and ftransform() replacement for composite/depth shaders. Uses locations matching FullScreenQuadRenderer's POSITION_TEXTURE VAO layout.
     */
    public static void injectCompositeVertexAttributes(ShaderAst transformer) {
        transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
        transformer.injectVariable("layout(location = 2) in vec4 iris_MultiTexCoord0;");

        transformer.rename("gl_Vertex", "iris_Vertex");
        // Iris's CompositeTransformer: the full-screen quad has texture coordinates on unit 0 only, so 1-7 are not
        // valid inputs and read the fixed-function initial value.
        // Demonica: before the gl_MultiTexCoord0 rename, where Iris calls it after replacing gl_MultiTexCoord0 with
        // vec4(UV0, 0.0, 1.0); Demonica keeps its iris_MultiTexCoord0 input. The names do not overlap, so the order
        // does not change the output.
        CommonTransformer.replaceGlMultiTexCoordBounded(transformer, 1, 7);
        transformer.rename("gl_MultiTexCoord0", "iris_MultiTexCoord0");

        transformer.renameFunctionCall("ftransform", "iris_ftransform");
        transformer.injectFunction("vec4 iris_ftransform() { return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex; }");

        ShaderTransformer.applyIntelHd4000Workaround(transformer);
    }
}
