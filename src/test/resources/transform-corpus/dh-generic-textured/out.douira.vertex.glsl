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
in vec3 vPosition;
in int aMaterial;
in vec3 aTranslateSubChunk;
in ivec3 aTranslateChunk;
in vec3 aScale;
in vec4 iris_color;
uniform int uBlockLight;
uniform int uSkyLight;
uniform vec3 uCameraPosSubChunk;
uniform ivec3 uCameraPosChunk;
uniform vec3 uOffsetSubChunk;
uniform ivec3 uOffsetChunk;
const vec3 irisNormals[6] = vec3[](vec3(0, 0, -1), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(1, 0, 0), vec3(0, -1, 0), vec3(0, 1, 0));
void _vert_init() {
	vec3 trans = vec3(aTranslateChunk + uOffsetChunk - uCameraPosChunk) * 16.0f;
	trans += (aTranslateSubChunk + uOffsetSubChunk - uCameraPosSubChunk);
	mat4 transform = mat4(aScale.x, 0.0f, 0.0f, 0.0f, 0.0f, aScale.y, 0.0f, 0.0f, 0.0f, 0.0f, aScale.z, 0.0f, trans.x, trans.y, trans.z, 1.0f);
	_vert_position = (transform * vec4(vPosition, 1.0f)).xyz;
	_vert_normal = irisNormals[int(floor(float(gl_VertexID) / 4.0f))];
	float blockLight = (float(uBlockLight) + 0.5f) / 16.0f;
	float skyLight = (float(uSkyLight) + 0.5f) / 16.0f;
	_vert_tex_light_coord = vec2(blockLight, skyLight);
	dhMaterialId = aMaterial;
	_vert_color = iris_color;
}
vec4 getVertexPosition() {
	return vec4(_vert_position, 1.0f);
}
void main() {
	_vert_init();
	iris_FrontColor = vec4(1.0f);
	iris_FogFragCoord = 0.0f;
	glcolor = _vert_color;
	material = float(dhMaterialId);
	gl_Position = iris_ProjectionMatrix * iris_ModelViewMatrix * getVertexPosition();
}
