#version 330 core
uniform sampler2D colortex0;
in vec2 uv;
in float fade;
in vec3 viewDir;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = vec4(texture(colortex0, uv).rgb * fade + normalize(viewDir) * 0.0, 1.0);
}
