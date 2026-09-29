#version 120
uniform vec3 modelOffset;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying float material;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    vec4 unused = gl_MultiTexCoord4 + gl_MultiTexCoord5 + gl_MultiTexCoord6 + gl_MultiTexCoord7;
    glcolor = gl_Color * unused.w;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    material = float(dhMaterialId);
    vec4 view = gl_ModelViewMatrixInverse * gl_ProjectionMatrixInverse * vec4(modelOffset, 1.0);
    gl_Position = ftransform() + gl_ModelViewProjectionMatrix * gl_Vertex + gl_ProjectionMatrix * gl_ModelViewMatrix * view;
}
