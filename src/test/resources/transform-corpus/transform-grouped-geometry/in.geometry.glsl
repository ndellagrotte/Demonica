#version 330 core
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec2 vUv[];
in vec4 vColor[];
out vec2 uv;
out vec4 color;
void main() {
    for (int i = 0; i < 3; i++) {
        uv = vUv[i];
        color = vColor[i];
        gl_Position = gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
