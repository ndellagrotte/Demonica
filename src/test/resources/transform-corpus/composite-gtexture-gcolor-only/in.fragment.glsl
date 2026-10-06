#version 120
uniform sampler2D gcolor;
varying vec2 texcoord;
void main() {
    gl_FragData[0] = vec4(texture2D(gcolor, texcoord).rgb, 1.0);
}
