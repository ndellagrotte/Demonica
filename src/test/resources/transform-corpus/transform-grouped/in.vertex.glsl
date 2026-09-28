#version 120
varying vec2 uv;
varying float fade;
void main() {
    uv = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
