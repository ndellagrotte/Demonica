#version 120
layout(rgba8) uniform image2D colorimg0;
varying vec2 texcoord;
void main() {
    float patch = 0.5;
    gl_FragColor = imageLoad(colorimg0, ivec2(texcoord * 16.0)) * patch;
}
