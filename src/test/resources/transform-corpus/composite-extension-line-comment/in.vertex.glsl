#version 120
varying vec2 texcoord;
void main() {
#extension GL_ARB_gpu_shader5 : enable // old /* code
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.xy;
}
