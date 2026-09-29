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
in vec4 iris_FrontColor;
in float iris_FogFragCoord;
uniform sampler2D colortex0;
in vec2 uv;
in vec4[2] pair;
in vec4 w[2];
in float lit[2];
layout(location = 0) out vec4 outColor;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	outColor = texture(colortex0, uv) * (pair[0] + pair[1] + w[0] + w[1]) * (lit[0] + lit[1]);
}
