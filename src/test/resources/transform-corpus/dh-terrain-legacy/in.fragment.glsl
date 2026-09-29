#version 120
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;
varying float material;
void main() {
    vec4 p = gl_ProjectionMatrixInverse * gl_ModelViewMatrixInverse * gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(1.0);
    gl_FragData[0] = texture2D(texture, texcoord) * glcolor * p.w * (gl_TextureMatrix[0] * vec4(material)).x;
}
