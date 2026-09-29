#version 330 core
in vec3 vaPosition;
out vec2 uv;;
vec2 flip(vec2 p) {
    return vec2(p.x, 1.0 - p.y);
};
void main() {
    uv = flip(vaPosition.xy);
    gl_Position = vec4(vaPosition, 1.0);
}
