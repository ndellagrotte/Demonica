#version 330 core
uniform sampler2D colortex0;
in vec2 uv;
in vec4 tint;
in vec3 glow;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = texture(colortex0, uv) * tint + vec4(glow, 0.0);
}
