#version 120
uniform sampler2D texture;
uniform sampler2D colortex1;
uniform sampler2D gcolor;
varying vec2 texcoord;
void main() {
    vec3 a = texture2D(texture, texcoord).rgb;
    vec3 b = texture2D(gcolor, texcoord).rgb;
    gl_FragData[0] = vec4(mix(a, b, 0.5) + texture2D(colortex1, texcoord).rgb * 0.0, 1.0);
}
