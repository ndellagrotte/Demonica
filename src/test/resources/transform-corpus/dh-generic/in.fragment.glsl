#version 120
varying vec4 glcolor;
varying vec3 normal;
varying float material;
void main() {
    gl_FragData[0] = vec4(glcolor.rgb * (material > 1.0 ? 0.8 : 1.0), glcolor.a);
    gl_FragData[1] = vec4(normal * 0.5 + 0.5, 1.0);
}
