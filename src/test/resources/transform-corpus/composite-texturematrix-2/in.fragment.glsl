#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec2 lmcoord2;
varying vec2 othercoord;
void main() {
    vec2 lm = lmcoord2 * (gl_TextureMatrix[2] * vec4(1.0)).xy;
    gl_FragData[0] = vec4(texture2D(colortex0, texcoord).rgb + vec3(lmcoord + lm + othercoord, 0.0) * 0.0, 1.0);
}
