#version 330 core
uniform sampler2D colortex0;
in vec2 uv;
in vec4 color;
in float fog;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = mix(texture(colortex0, uv) * color, vec4(1.0), fog);
}
