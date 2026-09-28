#version 120
uniform sampler2D texture;
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor * texture2D(lightmap, lmcoord);
    gl_FragData[0] = color;
    gl_FragData[1] = vec4(normal * 0.5 + 0.5, 1.0);
}
