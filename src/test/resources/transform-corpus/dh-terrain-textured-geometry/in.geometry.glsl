#version 330 core
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec4 glcolorV[];
in float materialV[];
out vec4 glcolor;
out float material;
void main() {
    for (int i = 0; i < 3; i++) {
        glcolor = glcolorV[i];
        material = materialV[i];
        gl_Position = gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
