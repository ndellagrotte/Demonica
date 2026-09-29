#version 330 core
#extension GL_ARB_shader_texture_lod : enable
uniform sampler2D colortex0;
#extension GL_EXT_gpu_shader4 : require
uniform sampler2D depthtex0;
in vec2 uv;
/* RENDERTARGETS: 0 */
#extension GL_ARB_gpu_shader5 : enable
layout(location = 0) out vec4 outColor;
void main() {
    float depth = texture(depthtex0, uv).r;
    outColor = vec4(texture(colortex0, uv).rgb * (1.0 - depth), 1.0);
}
