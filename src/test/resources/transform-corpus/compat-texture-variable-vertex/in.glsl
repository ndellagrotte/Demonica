#version 120
uniform sampler2D texture;
varying float height;
void main() {
    height = texture2DLod(texture, gl_MultiTexCoord0.st, 0.0).r;
    gl_Position = ftransform();
    gl_FrontColor = gl_Color;
}
