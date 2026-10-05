#version 120
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec4 glcolor;
varying float chunkFade;
void main() {
    vec4 color = texture2D(gtexture, texcoord) * glcolor;
    if (mc_chunkFade >= 0.0 && chunkFade < 1.0) {
        color.rgb = mix(vec3(0.5), color.rgb, clamp(chunkFade, 0.0, 1.0));
    }
    gl_FragData[0] = color;
}
