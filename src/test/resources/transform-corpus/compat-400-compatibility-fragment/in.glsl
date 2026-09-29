#version 400 compatibility
uniform sampler2D sampler;
vec4 shade(vec2 uv) {
    return texture2D(sampler, uv) * gl_Color;
}
void main() {
    gl_FragColor = shade(gl_TexCoord[0].st);
}
