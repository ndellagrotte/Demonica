#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
varying vec4 extra;
void main() {
    gl_FragData[0] = vec4(texture2D(colortex0, texcoord).rgb + extra.rgb * 0.0, 1.0);
}
