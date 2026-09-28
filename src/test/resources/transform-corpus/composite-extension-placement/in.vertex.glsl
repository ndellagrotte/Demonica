#version 330 core

#extension GL_ARB_explicit_attrib_location : enable
in vec3 vaPosition;
in vec2 vaUV0;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
out vec2 uv;
void main() {
    uv = vaUV0;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(vaPosition, 1.0);
}
