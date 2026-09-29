#version 110
attribute vec4 extra;
uniform int unit;
varying vec3 normal;
varying vec4 eye;
void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
    gl_TexCoord[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1 + gl_TextureMatrix[unit] * extra;
    normal = gl_NormalMatrix * gl_Normal;
    eye = gl_ModelViewMatrixInverse * (gl_ProjectionMatrixInverse * gl_Position);
    gl_FogFragCoord = length((gl_ModelViewMatrix * gl_Vertex).xyz);
    gl_FrontColor = gl_Color * 0.75;
}
