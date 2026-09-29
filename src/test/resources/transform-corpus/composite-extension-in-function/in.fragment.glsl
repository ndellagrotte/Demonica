#version 330 core
uniform sampler2D colortex0;
in vec2 texcoord;
layout(location = 0) out vec4 outColor;
void main() {
#extension GL_ARB_gpu_shader5 : enable
    outColor = texture(colortex0, texcoord);
}
