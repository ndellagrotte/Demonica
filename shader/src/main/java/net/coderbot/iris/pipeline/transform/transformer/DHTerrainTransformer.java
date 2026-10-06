package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

/**
 * DH_TERRAIN, Distant Horizons' LOD terrain and water programs, on {@link ShaderAst}: the legacy matrices and vertex
 * inputs become Iris's uniforms and the values {@code _vert_init()} unpacks from DH's vertex format (a
 * {@code uvec4 vPosition} with the micro-offset and light bits in {@code .a}, the normal index and material in
 * {@code irisExtra}), offset by {@code modelOffset}. Ported verb for verb from the TauMC engine's
 * {@code net.coderbot.iris.pipeline.transform.DHTerrainTransformer} (Step 7 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), with the same injected source strings in the same order.
 *
 * <p>Plan item 3.3 (docs/IRIS_PORTING_PLAN.md) brought it up to Iris 26.1's {@code DHTerrainTransformer}: the vertex
 * stage passes the block position and DH's texture tile id and face index on ({@code iris_vBlockPos},
 * {@code iris_TexId}), the fragment stage gets {@code dhBlockAtlas} and the {@code dh_hasTexture()},
 * {@code dh_blockFaceUv()} and {@code dh_sampleTexture()} helpers (the atlas is bound by {@code IrisLodRenderProgram}),
 * and the micro-offset is applied in x and z only, as DH's own shader does.</p>
 */
public final class DHTerrainTransformer {
    private DHTerrainTransformer() {
    }

    public static void transform(ShaderAst transformer, Parameters parameters, int glslVersion) {
        // Demonica: Iris passes core = false here. The flag means different things on the two sides: Iris's only turns
        // off its fixed-function alpha test (and DHParameters' AlphaTest.ALWAYS turns it off for DH anyway), Demonica's
        // selects the core-profile output (gl_FragData[i] flattened to iris_FragDatai with the GLSM alpha discard), which
        // every Demonica transformer asks for.
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

            CommonTransformer.replaceGlMultiTexCoordBounded(transformer, 4, 7);
        }

        transformer.rename("gl_Color", "_vert_color");

        if (parameters.type == ShaderType.VERTEX) {
            transformer.replaceExpression("gl_Normal", "_vert_normal");
        }

        transformer.replaceExpression("gl_NormalMatrix", "iris_NormalMatrix");
        ShaderTransformer.addIfNotExists(transformer, "iris_NormalMatrix", "uniform mat3 iris_NormalMatrix;");
        ShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrixInverse", "uniform mat4 iris_ModelViewMatrixInverse;");
        ShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrixInverse", "uniform mat4 iris_ProjectionMatrixInverse;");

        transformer.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        transformer.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        transformer.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");

        if (parameters.type == ShaderType.VERTEX) {
            if (transformer.containsCall("ftransform")) {
                transformer.injectFunction("vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
            }

            ShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
            ShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            transformer.injectFunction("vec4 getVertexPosition() { return vec4(modelOffset + _vert_position, 1.0); }");
            transformer.replaceExpression("gl_Vertex", "getVertexPosition()");

            injectVertInit(transformer);
        } else {
            ShaderTransformer.addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            ShaderTransformer.addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
        }

        transformer.replaceExpression("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        ShaderTransformer.applyIntelHd4000Workaround(transformer);

        if (parameters.type == ShaderType.FRAGMENT) {
            injectFragmentTextureHelpers(transformer);
        }
    }

    /**
     * Iris 26.1's DH terrain fragment block ({@code DHTerrainTransformer.transform}, the {@code FRAGMENT} branch): the
     * vertex stage's block position and tile id, DH's block atlas, and the functions a pack calls to texture a LOD.
     * {@code iris_TexId.x} is DH's tile id (0: no texture, the LOD keeps its flat colour), {@code .y} the face index.
     * DH's own fragment shader ({@code assets/distanthorizons/shaders/terrain/gl/frag.frag}, {@code blockFaceUv()}
     * and the {@code vTextureTileId} block) does the same arithmetic.
     *
     * <p>Demonica: idiom code, Iris's text, run after this stage's verbs (PORTING_GUIDE rule 3) instead of between
     * the {@code gl_MultiTexCoord} and {@code gl_Color} steps where Iris has it; the block goes at the top of the
     * declarations either way.</p>
     */
    static void injectFragmentTextureHelpers(ShaderAst ast) {
        ast.build(() -> {
            ast.tree.parseAndInjectNodes(ast.t, ASTInjectionPoint.BEFORE_DECLARATIONS,
                "in vec3 iris_vBlockPos;",
                "flat in uvec2 iris_TexId;",
                "uniform sampler2D dhBlockAtlas;",
                """
					bool dh_hasTexture() { return iris_TexId.x != 0u; }""", """
					vec2 dh_blockFaceUv() {
						vec3 pos = fract(iris_vBlockPos);
					      switch (iris_TexId.y)
					      {
					          case 0u: return vec2(pos.x, 1.0 - pos.z); // down
					          case 1u: return vec2(pos.x, pos.z); // up
					          case 2u: return vec2(1.0 - pos.x, 1.0 - pos.y); // north
					          case 3u: return vec2(pos.x, 1.0 - pos.y); // south
					          case 4u: return vec2(pos.z, 1.0 - pos.y); // west
					          default: return vec2(1.0 - pos.z, 1.0 - pos.y); // east
					      }
					}""", """
					vec4 dh_sampleTexture() {
						ivec2 atlasSize = textureSize(dhBlockAtlas, 0);
					          vec2 tileOrigin = vec2(float(iris_TexId.x % 256u), float(iris_TexId.x / 256u)) * 16.0;
					          vec2 uv = (tileOrigin + dh_blockFaceUv() * 16.0) / vec2(atlasSize);
					          return texture(dhBlockAtlas, uv);
					 }
					 """);
            return null;
        });
    }

    /** Declares DH's terrain vertex inputs and the Iris values, and calls {@code _vert_init()} first in {@code main}. */
    public static void injectVertInit(ShaderAst transformer) {
        ShaderTransformer.addIfNotExists(transformer, "_vert_position", "vec3 _vert_position;");
        ShaderTransformer.addIfNotExists(transformer, "_vert_tex_light_coord", "vec2 _vert_tex_light_coord;");
        ShaderTransformer.addIfNotExists(transformer, "dhMaterialId", "int dhMaterialId;");
        ShaderTransformer.addIfNotExists(transformer, "_vert_color", "vec4 _vert_color;");
        ShaderTransformer.addIfNotExists(transformer, "_vert_normal", "vec3 _vert_normal;");
        ShaderTransformer.addIfNotExists(transformer, "mircoOffset", "uniform float mircoOffset;");
        ShaderTransformer.addIfNotExists(transformer, "modelOffset", "uniform vec3 modelOffset;");
        // Iris 26.1: the block position and the tile id and face index, for the fragment stage's dh_* helpers.
        ShaderTransformer.addIfNotExists(transformer, "iris_vBlockPos", "out vec3 iris_vBlockPos;");
        ShaderTransformer.addIfNotExists(transformer, "iris_TexId", "flat out uvec2 iris_TexId;");
        ShaderTransformer.addIfNotExists(transformer, "iris_color", "in vec4 iris_color;");
        ShaderTransformer.addIfNotExists(transformer, "vPosition", "in uvec4 vPosition;");
        ShaderTransformer.addIfNotExists(transformer, "irisExtra", "in uvec4 irisExtra;");

        transformer.injectFunction("const vec3 irisNormals[6] = vec3[](vec3(0,-1,0), vec3(0,1,0), vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0));");
        transformer.injectFunction(
            "void _vert_init() {"
                + " uint meta = vPosition.a;"
                + " uint mirco = (meta & 0xFF00u) >> 8u;"
                + " float mx = (mirco & 1u) != 0u ? mircoOffset : 0.0;"
                + " mx = (mirco & 2u) != 0u ? -mx : mx;"
                + " float my = (mirco & 4u) != 0u ? mircoOffset : 0.0;"
                + " my = (mirco & 8u) != 0u ? -my : my;"
                + " float mz = (mirco & 16u) != 0u ? mircoOffset : 0.0;"
                + " mz = (mirco & 32u) != 0u ? -mz : mz;"
                + " uint lights = meta & 0xFFu;"
                // Demonica: 0.0 for y, as Iris 26.1 has it (vec3(mx, 0, mz)); DH 3.3.0's own vertex shader
                // (assets/distanthorizons/shaders/terrain/gl/vert.vert) computes mx and mz only: its my lines and
                // vertexWorldPos.y += my are commented out. my stays computed, unused, as in Iris.
                + " _vert_position = (vPosition.xyz + vec3(mx, 0.0, mz));"
                + " _vert_normal = irisNormals[int(irisExtra.y)];"
                + " dhMaterialId = int(irisExtra.x);"
                + " _vert_tex_light_coord = vec2((float(lights / 16u) + 0.5) / 16.0, (mod(float(lights), 16.0) + 0.5) / 16.0);"
                + " iris_vBlockPos = vec3(vPosition.xyz);"
                + " iris_TexId = uvec2(irisExtra.z | (irisExtra.w << 8u), irisExtra.y);"
                + " _vert_color = iris_color;"
                + " }"
        );
        transformer.prependMain("_vert_init();");
    }
}
