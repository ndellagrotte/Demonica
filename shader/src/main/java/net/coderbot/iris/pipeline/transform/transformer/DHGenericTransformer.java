package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.AstShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

/**
 * DH_GENERIC, Distant Horizons' generic-object program (the instanced boxes of DH's generic rendering), on
 * {@link ShaderAst}: the legacy matrices and vertex inputs become Iris's uniforms and the values {@code _vert_init()}
 * computes from DH's instanced box format (a {@code vec3 vPosition} scaled by {@code aScale} and translated by the
 * chunk and sub-chunk offsets against the camera; the normal from {@code gl_VertexID}). Ported verb for verb from the
 * TauMC engine's {@code net.coderbot.iris.pipeline.transform.DHGenericTransformer} (Step 7 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), with the same injected source strings in the same order.
 */
public final class DHGenericTransformer {
    private DHGenericTransformer() {
    }

    public static void transform(ShaderAst transformer, Parameters parameters, int glslVersion) {
        CommonTransformer.transform(transformer, parameters, true, glslVersion);

        transformer.replaceExpression("gl_TextureMatrix[0]", "mat4(1.0)");
        transformer.replaceExpression("gl_TextureMatrix[1]", "mat4(1.0)");
        transformer.rename("gl_ProjectionMatrix", "iris_ProjectionMatrix");

        if (parameters.type == ShaderType.VERTEX) {
            // Alias of gl_MultiTexCoord1 on 1.15+ for OptiFine
            // See https://github.com/IrisShaders/Iris/issues/1149
            transformer.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");

            transformer.replaceExpression("gl_MultiTexCoord0", "vec4(0.0, 0.0, 0.0, 1.0)");
            transformer.replaceExpression("gl_MultiTexCoord1", "vec4(_vert_tex_light_coord, 0.0, 1.0)");

            replaceGlMultiTexCoordBounded(transformer, 4, 7);
        }

        transformer.rename("gl_Color", "_vert_color");

        if (parameters.type == ShaderType.VERTEX) {
            transformer.replaceExpression("gl_Normal", "_vert_normal");
        }

        transformer.replaceExpression("gl_NormalMatrix", "iris_NormalMatrix");
        AstShaderTransformer.addIfNotExists(transformer, "iris_NormalMatrix", "uniform mat3 iris_NormalMatrix;");
        AstShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrixInverse", "uniform mat4 iris_ModelViewMatrixInverse;");
        AstShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrixInverse", "uniform mat4 iris_ProjectionMatrixInverse;");

        transformer.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        transformer.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        transformer.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");

        if (parameters.type == ShaderType.VERTEX) {
            if (transformer.containsCall("ftransform")) {
                transformer.injectFunction("vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
            }

            AstShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
            AstShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            transformer.injectFunction("vec4 getVertexPosition() { return vec4(_vert_position, 1.0); }");
            transformer.replaceExpression("gl_Vertex", "getVertexPosition()");

            injectVertInit(transformer);
        } else {
            AstShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            AstShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
        }

        transformer.replaceExpression("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        AstShaderTransformer.applyIntelHd4000Workaround(transformer);
    }

    /** Declares DH's generic vertex inputs and the Iris values, and calls {@code _vert_init()} first in {@code main}. */
    public static void injectVertInit(ShaderAst transformer) {
        AstShaderTransformer.addIfNotExists(transformer, "_vert_position", "vec3 _vert_position;");
        AstShaderTransformer.addIfNotExists(transformer, "_vert_tex_light_coord", "vec2 _vert_tex_light_coord;");
        AstShaderTransformer.addIfNotExists(transformer, "dhMaterialId", "int dhMaterialId;");
        AstShaderTransformer.addIfNotExists(transformer, "_vert_color", "vec4 _vert_color;");
        AstShaderTransformer.addIfNotExists(transformer, "_vert_normal", "vec3 _vert_normal;");
        AstShaderTransformer.addIfNotExists(transformer, "uOffsetChunk", "uniform ivec3 uOffsetChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "uOffsetSubChunk", "uniform vec3 uOffsetSubChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "uCameraPosChunk", "uniform ivec3 uCameraPosChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "uCameraPosSubChunk", "uniform vec3 uCameraPosSubChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "uSkyLight", "uniform int uSkyLight;");
        AstShaderTransformer.addIfNotExists(transformer, "uBlockLight", "uniform int uBlockLight;");
        AstShaderTransformer.addIfNotExists(transformer, "iris_color", "in vec4 iris_color;");
        AstShaderTransformer.addIfNotExists(transformer, "aScale", "in vec3 aScale;");
        AstShaderTransformer.addIfNotExists(transformer, "aTranslateChunk", "in ivec3 aTranslateChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "aTranslateSubChunk", "in vec3 aTranslateSubChunk;");
        AstShaderTransformer.addIfNotExists(transformer, "aMaterial", "in int aMaterial;");
        AstShaderTransformer.addIfNotExists(transformer, "vPosition", "in vec3 vPosition;");

        transformer.injectFunction("const vec3 irisNormals[6] = vec3[](vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0), vec3(0,-1,0), vec3(0,1,0));");
        transformer.injectFunction(
            "void _vert_init() {"
                + " vec3 trans = vec3(aTranslateChunk + uOffsetChunk - uCameraPosChunk) * 16.0;"
                + " trans += (aTranslateSubChunk + uOffsetSubChunk - uCameraPosSubChunk);"
                + " mat4 transform = mat4("
                + "  aScale.x, 0.0, 0.0, 0.0,"
                + "  0.0, aScale.y, 0.0, 0.0,"
                + "  0.0, 0.0, aScale.z, 0.0,"
                + "  trans.x, trans.y, trans.z, 1.0"
                + " );"
                + " _vert_position = (transform * vec4(vPosition, 1.0)).xyz;"
                + " _vert_normal = irisNormals[int(floor(float(gl_VertexID) / 4.0))];"
                + " float blockLight = (float(uBlockLight) + 0.5) / 16.0;"
                + " float skyLight = (float(uSkyLight) + 0.5) / 16.0;"
                + " _vert_tex_light_coord = vec2(blockLight, skyLight);"
                + " dhMaterialId = aMaterial;"
                + " _vert_color = iris_color;"
                + " }"
        );
        transformer.prependMain("_vert_init();");
    }

    private static void replaceGlMultiTexCoordBounded(ShaderAst transformer, int from, int to) {
        for (int i = from; i <= to; i++) {
            transformer.replaceExpression("gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
        }
    }
}
