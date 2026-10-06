#version 120
varying vec2 texcoord;
varying vec4 extra;
void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.xy;
    vec2 lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    extra = vec4(lmcoord, 0.0, 0.0) + gl_MultiTexCoord2 + gl_MultiTexCoord3 + gl_MultiTexCoord4
        + gl_MultiTexCoord5 + gl_MultiTexCoord6 + gl_MultiTexCoord7;
}
