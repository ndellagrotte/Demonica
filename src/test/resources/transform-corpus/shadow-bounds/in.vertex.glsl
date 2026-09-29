#version 120
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;
varying vec3 shadowCoord;
void main() {
    vec4 shadowPos = shadowProjection * shadowModelView * gl_Vertex;
    shadowCoord = shadowPos.xyz * 0.5 + 0.5;
    gl_Position = ftransform();
}
