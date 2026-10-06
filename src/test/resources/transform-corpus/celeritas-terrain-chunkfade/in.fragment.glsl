#version 120
uniform sampler2D gtexture;
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying float chunkFade;
void main() {
    vec4 color = texture2D(gtexture, texcoord) * glcolor * texture2D(lightmap, lmcoord);
    float skyLightFactor = 0.5;
    if (chunkFade < 1.0) skyLightFactor = 1.0 - chunkFade * 0.5;
    gl_FragData[0] = color;
    gl_FragData[1] = vec4(skyLightFactor);
}
