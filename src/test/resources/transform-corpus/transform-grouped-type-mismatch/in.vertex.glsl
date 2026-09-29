#version 330 core
in vec3 vaPosition;
out vec2 uv;
out vec3 tint, glow;
void main() {
    uv = vaPosition.xy;
    tint = vec3(1.0, 0.5, 0.25);
    glow = vec3(0.1);
    gl_Position = vec4(vaPosition, 1.0);
}
