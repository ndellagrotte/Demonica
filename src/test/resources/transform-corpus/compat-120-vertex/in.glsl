#version 120
varying vec2 texcoord;
varying vec4 viewPos;
void main() {
    viewPos = gl_ModelViewMatrix * gl_Vertex;
    texcoord = gl_MultiTexCoord0.st;
    gl_FrontColor = gl_Color;
    gl_Position = ftransform();
}
