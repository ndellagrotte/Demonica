#version 120
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec2 midcoord;
varying vec4 glcolor;
void main() {
    gl_FragData[0] = texture2D(gtexture, texcoord) * glcolor + vec4(midcoord, 0.0, 0.0) * 0.0;
}
