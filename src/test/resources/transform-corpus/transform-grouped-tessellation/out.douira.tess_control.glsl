#version 400 core

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
layout(vertices = 3) out;
in vec2 vUv[];
in vec3 vNormal[];
out vec2 tcUv[];
out vec3 tcNormal[];
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	tcUv[gl_InvocationID] = vUv[gl_InvocationID];
	tcNormal[gl_InvocationID] = vNormal[gl_InvocationID];
	gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
	gl_TessLevelInner[0] = 1.0f;
	gl_TessLevelOuter[0] = 1.0f;
	gl_TessLevelOuter[1] = 1.0f;
	gl_TessLevelOuter[2] = 1.0f;
}
