#version 120
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec2 lmcoord2;
varying vec2 othercoord;
void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * vec4(240.0, 240.0, 0.0, 1.0)).xy;
    lmcoord2 = (gl_TextureMatrix[2] * vec4(240.0, 240.0, 0.0, 1.0)).xy;
    othercoord = (gl_TextureMatrix[3] * vec4(texcoord, 0.0, 1.0)).xy;
}
