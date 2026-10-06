#version 330 core

uniform mat4 iris_ModelViewMatrix;
uniform mat4 iris_ProjectionMatrix;
uniform mat4 iris_ProjectionMatrixInverse;
uniform mat4 iris_ModelViewMatrixInverse;
uniform mat3 iris_NormalMatrix;
uniform vec4 iris_FogColor;
uniform float iris_FogEnd;
uniform float iris_FogStart;
uniform float iris_FogDensity;
out vec4 iris_FrontColor;
out float iris_FogFragCoord;
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
vec3 _vert_position;
vec2 _vert_tex_light_coord;
int dhMaterialId;
vec4 _vert_color;
vec3 _vert_normal;
in uvec4 irisExtra;
in uvec4 vPosition;
in vec4 iris_color;
uniform vec3 modelOffset;
uniform float mircoOffset;
const vec3 irisNormals[6] = vec3[](vec3(0, -1, 0), vec3(0, 1, 0), vec3(0, 0, -1), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(1, 0, 0));
void _vert_init() {
	uint meta = vPosition.a;
	uint mirco = (meta & 0xff00u) >> 8u;
	float mx = (mirco & 1u) != 0u ? mircoOffset : 0.0f;
	mx = (mirco & 2u) != 0u ? -mx : mx;
	float my = (mirco & 4u) != 0u ? mircoOffset : 0.0f;
	my = (mirco & 8u) != 0u ? -my : my;
	float mz = (mirco & 16u) != 0u ? mircoOffset : 0.0f;
	mz = (mirco & 32u) != 0u ? -mz : mz;
	uint lights = meta & 0xffu;
	_vert_position = (vPosition.xyz + vec3(mx, my, mz));
	_vert_normal = irisNormals[int(irisExtra.y)];
	dhMaterialId = int(irisExtra.x);
	_vert_tex_light_coord = vec2((float(lights / 16u) + 0.5f) / 16.0f, (mod(float(lights), 16.0f) + 0.5f) / 16.0f);
	_vert_color = iris_color;
}
vec4 getVertexPosition() {
	return vec4(modelOffset + _vert_position, 1.0f);
}
void main() {
	_vert_init();
	iris_FrontColor = vec4(1.0f);
	iris_FogFragCoord = 0.0f;
	glcolor = _vert_color;
	material = float(dhMaterialId);
	gl_Position = iris_ProjectionMatrix * iris_ModelViewMatrix * getVertexPosition();
}
