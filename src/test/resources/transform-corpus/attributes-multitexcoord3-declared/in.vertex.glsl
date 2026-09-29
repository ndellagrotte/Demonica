#version 120
attribute vec4 gl_MultiTexCoord3;
varying vec2 texcoord;
varying vec2 midcoord;
varying vec4 glcolor;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    midcoord = gl_MultiTexCoord3.xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
}
