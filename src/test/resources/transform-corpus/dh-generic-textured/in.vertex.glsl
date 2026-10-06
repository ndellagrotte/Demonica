#version 120
varying vec4 glcolor;
varying float material;
void main() {
    glcolor = gl_Color;
    material = float(dhMaterialId);
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
