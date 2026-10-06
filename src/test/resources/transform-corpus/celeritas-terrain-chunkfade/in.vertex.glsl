#version 120
attribute vec4 mc_Entity;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying float chunkFade;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
    chunkFade = mc_chunkFade;
}
