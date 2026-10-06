#version 330 core

uniform mat4 iris_ProjectionMatrix;
uniform mat4 iris_ModelViewMatrix;
uniform mat4 iris_ProjectionMatrixInverse;
uniform mat4 iris_ModelViewMatrixInverse;
uniform mat3 iris_NormalMatrix;
uniform vec4 iris_FogColor;
uniform float iris_FogEnd;
uniform float iris_FogStart;
uniform float iris_FogDensity;
uniform float iris_currentAlphaTest;
layout(location = 0) out vec4 iris_FragData0;
in vec4 iris_FrontColor;
in float iris_FogFragCoord;
in vec4 glcolor;
in float material;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	vec4 albedo = glcolor;
	if (dh_hasTexture()) {
		vec4 dhTexture = dh_sampleTexture();
		vec3 clampedColor = clamp(albedo.rgb * (dhTexture.rgb * 2.0f), 0.0f, 1.0f);
		albedo.rgb = mix(albedo.rgb, clampedColor, dhTexture.a);
	}
	iris_FragData0 = vec4(albedo.rgb * (material > 1.0f ? 0.8f : 1.0f), albedo.a);
	if (iris_FragData0.a <= iris_currentAlphaTest) discard;
}
