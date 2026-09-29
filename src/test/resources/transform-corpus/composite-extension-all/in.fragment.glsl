#version 330 core
#extension GL_ARB_gpu_shader5 : enable
#extension all : warn
uniform sampler2D colortex0;
in vec2 texcoord;
layout(location = 0) out vec4 outColor;
void main() {
    outColor = texture(colortex0, texcoord);
}
