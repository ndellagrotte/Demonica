#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
void main() {
    float patch = 0.5;
    gl_FragColor = texture2D(colortex0, texcoord) * patch;
}
