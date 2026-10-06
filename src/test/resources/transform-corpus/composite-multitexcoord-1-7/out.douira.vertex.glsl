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
out vec2 texcoord;
out vec4 extra;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
vec4 iris_ftransform() {
	return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex;
}
void main() {
	iris_FrontColor = vec4(1.0f);
	iris_FogFragCoord = 0.0f;
	gl_Position = iris_ftransform();
	texcoord = iris_MultiTexCoord0.xy;
	vec2 lmcoord = (iris_LightmapTextureMatrix * gl_MultiTexCoord1).xy;
	extra = vec4(lmcoord, 0.0f, 0.0f) + gl_MultiTexCoord2 + gl_MultiTexCoord3 + gl_MultiTexCoord4 + gl_MultiTexCoord5 + gl_MultiTexCoord6 + gl_MultiTexCoord7;
}
