#version 330 core
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
in vec2 uv;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
    float depth = texture(depthtex0, uv).r;
    outColor = vec4(texture(colortex0, uv).rgb * (1.0 - depth), 1.0);
}
