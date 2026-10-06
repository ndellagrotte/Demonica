#version 120
uniform sampler2D gtexture;
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 extra;
varying vec4 glcolor;
void main() {
    gl_FragData[0] = texture2D(gtexture, texcoord) * texture2D(lightmap, lmcoord) * glcolor + extra * 0.0;
}
