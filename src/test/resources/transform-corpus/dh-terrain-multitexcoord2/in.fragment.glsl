#version 120
varying vec2 lmcoord;
void main() {
    gl_FragData[0] = vec4(lmcoord, 0.0, 1.0);
}
