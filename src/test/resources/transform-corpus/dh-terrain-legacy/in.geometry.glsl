#version 330 core
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec4 glcolor[];
out vec4 gcolor;
void main() {
    for (int i = 0; i < 3; i++) {
        gcolor = glcolor[i];
        gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
