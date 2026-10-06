#version 120
varying vec4 glcolor;
varying float material;
void main() {
    vec4 albedo = glcolor;
    if (dh_hasTexture()) {
        vec4 dhTexture = dh_sampleTexture();
        vec3 clampedColor = clamp(albedo.rgb * (dhTexture.rgb * 2.0), 0.0, 1.0);
        albedo.rgb = mix(albedo.rgb, clampedColor, dhTexture.a);
    }
    gl_FragData[0] = vec4(albedo.rgb * (material > 1.0 ? 0.8 : 1.0), albedo.a);
}
