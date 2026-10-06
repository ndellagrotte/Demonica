#version 120
attribute vec4 mc_Entity;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 extra;
varying vec4 glcolor;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    extra = gl_MultiTexCoord4 + gl_MultiTexCoord5 * 0.5 + gl_MultiTexCoord6.xyzw - vec4(gl_MultiTexCoord7.xy, 0.0, 0.0);
    glcolor = gl_Color;
    vec4 position = gl_Vertex;
    if (mc_Entity.x == 10001.0) {
        position.y += gl_MultiTexCoord4.w * 0.05;
    }
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * position;
}
