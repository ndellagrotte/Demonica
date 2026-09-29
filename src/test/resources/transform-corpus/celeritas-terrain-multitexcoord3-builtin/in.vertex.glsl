#version 120
attribute vec4 mc_Entity;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec2 midcoord;
varying vec4 glcolor;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    midcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord3).xy;
    glcolor = gl_Color;
    vec4 position = gl_Vertex;
    if (mc_Entity.x == 10001.0) {
        position.xz += (gl_MultiTexCoord3.xy - texcoord) * 0.05;
    }
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * position;
}
