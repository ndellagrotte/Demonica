#version 120
attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying float blockId;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    vec4 tangentData = gl_MultiTexCoord3;
    blockId = mc_Entity.x + tangentData.w * 0.0 + mc_midTexCoord.x * 0.0;
    glcolor = gl_Color;
    gl_Position = ftransform();
}
