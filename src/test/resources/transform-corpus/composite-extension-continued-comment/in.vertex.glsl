#version 120
#extension GL_ARB_gpu_shader5 : enable // c \
uniform float u;
varying vec2 texcoord;
void main() {
    texcoord = vec2(0.0);
#extension GL_ARB_gpu_shader5 : enable // c \
    texcoord = gl_MultiTexCoord0.xy * u;
    gl_Position = ftransform();
}
