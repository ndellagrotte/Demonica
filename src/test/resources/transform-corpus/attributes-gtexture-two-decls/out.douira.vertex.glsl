#version 330 core

layout(location = 1) in vec4 iris_Color;
layout(location = 4) in vec3 iris_Normal;
layout(location = 3) in vec4 iris_MultiTexCoord1;
layout(location = 2) in vec4 iris_MultiTexCoord0;
layout(location = 0) in vec4 iris_Vertex;
uniform mat4 iris_TextureMatrix;
uniform mat4 iris_LightmapTextureMatrix;
uniform mat3 iris_NormalMatrix;
uniform mat4 iris_ProjectionMatrixInverse;
uniform mat4 iris_ProjectionMatrix;
uniform mat4 iris_ModelViewMatrixInverse;
uniform mat4 iris_ModelViewMatrix;
uniform vec4 actinium_ClipPlane[8];
uniform bool actinium_ClipPlanesEnabled;
uniform vec4 iris_FogColor;
uniform float iris_FogEnd;
uniform float iris_FogStart;
uniform float iris_FogDensity;
out vec4 iris_FrontColor;
out float iris_FogFragCoord;
out vec2 texcoord;
out vec4 glcolor;
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
	texcoord = (iris_TextureMatrix * iris_MultiTexCoord0).xy;
	glcolor = iris_Color;
	gl_Position = iris_ftransform();
	{
		if (actinium_ClipPlanesEnabled) {
			vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex;
			gl_ClipDistance[0] = dot(actinium_ClipPlane[0], _cp_ep);
			gl_ClipDistance[1] = dot(actinium_ClipPlane[1], _cp_ep);
			gl_ClipDistance[2] = dot(actinium_ClipPlane[2], _cp_ep);
			gl_ClipDistance[3] = dot(actinium_ClipPlane[3], _cp_ep);
			gl_ClipDistance[4] = dot(actinium_ClipPlane[4], _cp_ep);
			gl_ClipDistance[5] = dot(actinium_ClipPlane[5], _cp_ep);
			gl_ClipDistance[6] = dot(actinium_ClipPlane[6], _cp_ep);
			gl_ClipDistance[7] = dot(actinium_ClipPlane[7], _cp_ep);
		}
	}
}
