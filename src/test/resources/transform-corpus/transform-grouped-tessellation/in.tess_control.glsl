#version 400 core
layout(vertices = 3) out;
in vec2 vUv[];
in vec3 vNormal[];
out vec2 tcUv[];
out vec3 tcNormal[];
void main() {
    tcUv[gl_InvocationID] = vUv[gl_InvocationID];
    tcNormal[gl_InvocationID] = vNormal[gl_InvocationID];
    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
    gl_TessLevelInner[0] = 1.0;
    gl_TessLevelOuter[0] = 1.0;
    gl_TessLevelOuter[1] = 1.0;
    gl_TessLevelOuter[2] = 1.0;
}
