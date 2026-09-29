#version 120
varying vec2 lmcoord;
void main() {
    lmcoord = gl_MultiTexCoord2.xy + gl_MultiTexCoord1.xy;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
