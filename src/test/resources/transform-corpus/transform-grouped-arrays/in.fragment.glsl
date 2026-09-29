#version 330 core
uniform sampler2D colortex0;
in vec2 uv;
in vec4[2] pair;
in vec4 w[2];
in float lit[2];
layout(location = 0) out vec4 outColor;
void main() {
    outColor = texture(colortex0, uv) * (pair[0] + pair[1] + w[0] + w[1]) * (lit[0] + lit[1]);
}
