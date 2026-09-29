#version 400 core
in vec3 vaPosition;
out vec2 vUv;
void main() {
    vUv = vaPosition.xy;
    gl_Position = vec4(vaPosition, 1.0);
}
