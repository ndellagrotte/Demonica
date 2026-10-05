#version 330 core

uniform mat4 iris_TextureMatrix;
uniform mat4 iris_LightmapTextureMatrix;
uniform mat3 iris_NormalMatrix;
uniform mat4 iris_ProjectionMatrixInverse;
uniform mat4 iris_ProjectionMatrix;
uniform mat4 iris_ModelViewMatrixInverse;
uniform mat4 iris_ModelViewMatrix;
uniform vec4 iris_FogColor;
uniform float iris_FogEnd;
uniform float iris_FogStart;
uniform float iris_FogDensity;
uniform float iris_currentAlphaTest;
layout(location = 0) out vec4 iris_FragData0;
in vec4 iris_FrontColor;
in float iris_FogFragCoord;
uniform sampler2D gtexture;
in vec2 texcoord;
in vec4 glcolor;
in float chunkFade;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	vec4 color = texture(gtexture, texcoord) * glcolor;
	if (mc_chunkFade >= 0.0f && chunkFade < 1.0f) {
		color.rgb = mix(vec3(0.5f), color.rgb, clamp(chunkFade, 0.0f, 1.0f));
	}
	iris_FragData0 = color;
	if (iris_FragData0.a <= iris_currentAlphaTest) discard;
}
