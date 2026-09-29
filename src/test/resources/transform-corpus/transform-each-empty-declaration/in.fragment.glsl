#version 330 core
uniform sampler2D colortex0;
in vec2 uv;
;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = texture(colortex0, uv);
}
