#version 120
uniform sampler2D gcolor, texture;
varying vec2 texcoord;
varying vec4 glcolor;
void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    albedo.rgb = mix(albedo.rgb, texture2D(gcolor, texcoord).rgb, 0.25);
    gl_FragData[0] = albedo;
}
