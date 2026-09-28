#version 120
attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    glcolor = gl_Color;
    vec4 position = gl_Vertex;
    if (mc_Entity.x == 10001.0) {
        position.y += sin(position.x + mc_midTexCoord.x) * 0.05;
    }
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * position;
}
