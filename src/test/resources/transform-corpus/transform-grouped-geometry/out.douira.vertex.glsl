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
out vec4 vColor;
in vec3 vaPosition;
out vec2 vUv;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	vColor = vec4(0.0f);
	iris_FrontColor = vec4(1.0f);
	iris_FogFragCoord = 0.0f;
	vUv = vaPosition.xy;
	gl_Position = vec4(vaPosition, 1.0f);
}
