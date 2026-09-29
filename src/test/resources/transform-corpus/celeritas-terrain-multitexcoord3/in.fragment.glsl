#version 120
uniform sampler2D texture;
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec2 midcoord;
varying vec4 glcolor;
void main() {
    vec4 color = texture2D(texture, texcoord + (midcoord - texcoord) * 0.0) * glcolor * texture2D(lightmap, lmcoord);
    gl_FragData[0] = color;
}
