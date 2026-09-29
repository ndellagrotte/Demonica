#version 400 core
uniform sampler2D colortex0;
in vec2 uv;
in vec3 normal;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = texture(colortex0, uv) * vec4(normalize(normal), 1.0);
}
