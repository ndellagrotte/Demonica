#version 330 core
in vec3 vaPosition;
out vec2 uv;
out vec3[2] pair;
out vec3 w[2];
out float lit[2];
void main() {
    uv = vaPosition.xy;
    pair[0] = vaPosition;
    pair[1] = -vaPosition;
    w[0] = vec3(1.0);
    w[1] = vec3(0.5);
    gl_Position = vec4(vaPosition, 1.0);
}
