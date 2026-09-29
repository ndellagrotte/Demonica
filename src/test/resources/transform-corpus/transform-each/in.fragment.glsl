#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
float unusedHelper(float x) {
    return x * x;
}
vec3 tonemap(const in vec3 color, const float exposure) {
    const float scale = exposure;
    float shoulder = scale * 0.5;
    return color * scale / (color + vec3(shoulder));
}
void main() {
    gl_FragColor = vec4(tonemap(texture2D(colortex0, texcoord).rgb, 1.5), 1.0);
}
