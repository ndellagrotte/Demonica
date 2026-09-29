#version 120
uniform sampler2DShadow outer;
uniform sampler2DShadow inner;
varying vec3 p;
void main() {
    gl_FragColor = shadow2D(outer, vec3(shadow2D(inner, p).r));
}
