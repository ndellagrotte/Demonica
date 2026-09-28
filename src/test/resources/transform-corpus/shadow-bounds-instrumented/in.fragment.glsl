#version 120
uniform sampler2D shadowtex0;
varying vec3 shadowCoord;
const float shadowMapResolution = 2048.0;
float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
    vec2 offset = vec2(0.5 / shadowMapResolution);
    float shadow = step(shadowPos.z, texture2D(shadowtex, shadowPos.xy + offset).r);
    shadow += step(shadowPos.z, texture2D(shadowtex, shadowPos.xy - offset).r);
    return shadow * 0.5;
}
void main() {
    gl_FragData[0] = vec4(vec3(texture2DShadow2x2(shadowtex0, shadowCoord)), 1.0);
}
