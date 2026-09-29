#version 400 core
layout(triangles, equal_spacing, ccw) in;
in vec2 tcUv[];
in vec3 tcNormal[];
out vec2 uv;
out vec3 normal;
void main() {
    uv = gl_TessCoord.x * tcUv[0] + gl_TessCoord.y * tcUv[1] + gl_TessCoord.z * tcUv[2];
    normal = tcNormal[0];
    gl_Position = gl_TessCoord.x * gl_in[0].gl_Position + gl_TessCoord.y * gl_in[1].gl_Position + gl_TessCoord.z * gl_in[2].gl_Position;
}
