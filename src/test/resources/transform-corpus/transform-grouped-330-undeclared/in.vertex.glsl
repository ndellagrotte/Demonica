#version 330 core
in vec3 vaPosition;
uniform mat4 projectionMatrix;
out vec2 uv;
out float fade;
void main() {
    uv = vaPosition.xy * 0.5 + 0.5;
    gl_Position = projectionMatrix * vec4(vaPosition, 1.0);
}
