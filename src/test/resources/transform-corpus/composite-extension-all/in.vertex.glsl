#version 120
varying vec2 texcoord;
#extension all : disable
void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.xy;
}
