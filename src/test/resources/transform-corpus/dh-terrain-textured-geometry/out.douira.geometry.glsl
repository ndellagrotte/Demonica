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
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec4 glcolorV[];
in float materialV[];
out vec4 glcolor;
out float material;
struct iris_FogParameters {
	vec4 color;
	float density;
	float start;
	float end;
	float scale;
};
iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));
void main() {
	for (int i = 0; i < 3; i++) {
		glcolor = glcolorV[i];
		material = materialV[i];
		gl_Position = gl_in[i].gl_Position;
		EmitVertex();
	}
	EndPrimitive();
}
