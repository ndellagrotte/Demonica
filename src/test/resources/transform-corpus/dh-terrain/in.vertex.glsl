#version 120
varying vec4 glcolor;
varying vec3 normal;
varying float material;
void main() {
    glcolor = gl_Color;
    normal = gl_NormalMatrix * gl_Normal;
    material = float(dhMaterialId);
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
