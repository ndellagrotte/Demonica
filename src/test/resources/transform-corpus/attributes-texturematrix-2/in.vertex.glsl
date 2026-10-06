#version 120
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec2 othercoord;
varying vec4 glcolor;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[2] * gl_MultiTexCoord2).xy;
    othercoord = (gl_TextureMatrix[3] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
}
