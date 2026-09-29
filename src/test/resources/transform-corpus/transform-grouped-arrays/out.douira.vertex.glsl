#version 330 core

layout(location = 2) in vec4 iris_MultiTexCoord0;
layout(location = 0) in vec4 iris_Vertex;
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
out vec4 iris_FrontColor;
out float iris_FogFragCoord;
in vec3 vaPosition;
out vec2 uv;
out vec3[2] pair;
out vec3 w[2];
out float lit[2];
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	lit = 0.0f;
	iris_FrontColor = vec4(1.0f);
	iris_FogFragCoord = 0.0f;
	uv = vaPosition.xy;
	pair[0] = vaPosition;
	pair[1] = -vaPosition;
	w[0] = vec3(1.0f);
	w[1] = vec3(0.5f);
	gl_Position = vec4(vaPosition, 1.0f);
}
