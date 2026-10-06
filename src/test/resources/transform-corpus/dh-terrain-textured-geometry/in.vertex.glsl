#version 120
varying vec4 glcolorV;
varying float materialV;
void main() {
    glcolorV = gl_Color;
    materialV = float(dhMaterialId);
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
