package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * The CELERITAS_TERRAIN patch kind (terrain and shadow programs drawn by Celeritas), on {@link ShaderAst}: the common
 * transformation, the matrix uniforms and, for vertex shaders, Celeritas's vertex inputs ({@code _vert_*},
 * {@code _celeritas_getVertexPosition()}, {@code u_RegionOffset}) in place of the fixed-function ones. Ported verb for
 * verb from the TauMC engine's {@code net.coderbot.iris.pipeline.transform.CeleritasTransformer} (Step 6 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md), with the same injected source strings. The Celeritas header
 * ({@code chunk_vertex.glsl}) that declares those inputs is text the orchestrator puts between the extension lines and
 * the body; it is not parsed.
 */
public final class CeleritasTransformer {
    private CeleritasTransformer() {
    }

    public static void transform(ShaderAst transformer, Parameters parameters, int glslVersion) {
        // Always core profile — minimum GLSL version is 330 (see VersionNegotiation.getStageMinimumVersion)
        CommonTransformer.transform(transformer, parameters, true, glslVersion);

        switch (parameters.type) {
            case FRAGMENT:
                transformFragment(transformer, parameters);
                break;
            case GEOMETRY:
                transformGeometry(transformer, parameters);
                break;
            case VERTEX:
                transformVertex(transformer, parameters);
                break;
            default:
                throw new IllegalStateException("Unexpected Celeritas terrain patching shader type: " + parameters.type);
        }
    }

    public static void transformVertex(ShaderAst transformer, Parameters parameters) {
        transformer.injectVariable("uniform vec3 u_RegionOffset;");
        transformer.injectVariable("vec4 iris_LightTexCoord;");
        transformer.injectFunction("vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * _celeritas_getVertexPosition(); }");
        transformer.injectFunction(
            "vec4 _celeritas_getVertexPosition() { " +
            "return vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0); }");
        transformer.injectFunction(
            "void _celeritas_init() { " +
            "_vert_init(); " +
            "iris_LightTexCoord = vec4(vec2(_vert_tex_light_coord), 0.0, 1.0); }");
        transformer.prependMain("_celeritas_init();");
        transformShared(transformer, parameters);

        // Eclipse-style terrain packs displace worldpos in the vertex stage and emit it as
        // gl_Position. Celeritas still needs clip space, so project the displaced world
        // position here and let the geometry stage pass the clip position through.
        transformer.replaceExpression(
            "vec4(worldpos, 0.0)",
            "iris_ProjectionMatrix * gbufferModelView * vec4(worldpos, 1.0)"
        );

        // HashMaps, as in the TauMC engine: the replacements and renames run in their iteration order.
        final Map<String, String> vertexReplacements = new HashMap<>();
        vertexReplacements.put("gl_Vertex", "_celeritas_getVertexPosition()");
        vertexReplacements.put("gl_MultiTexCoord0", "vec4(_vert_tex_diffuse_coord, 0.0, 1.0)");
        vertexReplacements.put("gl_MultiTexCoord1", "iris_LightTexCoord");
        vertexReplacements.put("gl_MultiTexCoord2", "iris_LightTexCoord");
        vertexReplacements.forEach(transformer::replaceExpression);

        // gl_MultiTexCoord0 and gl_MultiTexCoord1 are the only valid inputs (with
        // gl_MultiTexCoord2 and gl_MultiTexCoord3 as aliases), other texture
        // coordinates are not valid inputs.
        // Demonica: here, after the 0-2 replacements, where Iris calls it after patchMultiTexCoord3; Demonica patches
        // gl_MultiTexCoord3 later, in ShaderTransformer.doTransform. The names do not overlap, so the order does not
        // change the output.
        CommonTransformer.replaceGlMultiTexCoordBounded(transformer, 4, 7);

        if (transformer.hasVariable("chunkOffset")) {
            transformer.removeVariable("chunkOffset");
        }

        final Map<String, String> vertexRenames = new HashMap<>();
        vertexRenames.put("gl_Color", "_vert_color");
        vertexRenames.put("ftransform", "iris_ftransform");
        vertexRenames.put("gl_Normal", "iris_Normal");
        vertexRenames.put("chunkOffset", "u_RegionOffset");
        transformer.rename(vertexRenames);

        transformer.injectVariable("in vec3 iris_Normal;");

        // Iris 26.1 SodiumTransformer.injectVertInit:
        //   String chunkFadeDeclaration = parameters.shadow ? "const float mc_chunkFade = -1.0;" : "float mc_chunkFade;";
        // with mc_chunkFade = (chunkFade < 0) ? 1.0 : fade; from the section's fade-in time.
        // Demonica: 1.12.2 chunks have no fade-in time, so every chunk is the upstream "no time" case, 1.0 (fully
        // faded in); -1.0 here would read as a fading chunk to packs that test chunkFade < 1.0 (Complementary
        // Reimagined r5.9.3's gbuffers_terrain and gbuffers_water). The parameters do not tell the shadow pass apart,
        // so it gets 1.0 too, where upstream declares -1.0. Declared only where the program reads it and does not
        // declare it itself, so programs that never name it print as before.
        if (transformer.containsCall("mc_chunkFade")) {
            ShaderTransformer.addIfNotExists(transformer, "mc_chunkFade", "const float mc_chunkFade = 1.0;");
        }
    }

    public static void transformFragment(ShaderAst transformer, Parameters parameters) {
        transformShared(transformer, parameters);
    }

    private static void transformGeometry(ShaderAst transformer, Parameters parameters) {
        transformShared(transformer, parameters);

        // The geometry stage above is written for the pack's world-space vertex output.
        // Celeritas now receives clip-space positions, so it must not reproject them.
        transformer.replaceExpression(
            "toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz)",
            "vertex"
        );
    }

    private static void transformShared(ShaderAst transformer, Parameters parameters) {
        CoreTransformHelper.injectMatrixUniforms(transformer);
    }
}
