#version 120
uniform sampler2D tex;
varying vec3 normal;
void main() {
    vec3 l = normalize(gl_LightSource[0].position.xyz);
    float diffuse = max(dot(normalize(normal), l), 0.0);
    vec4 lit = gl_FrontLightModelProduct.sceneColor + gl_FrontMaterial.emission
        + gl_LightSource[1].diffuse * gl_FrontMaterial.diffuse * diffuse;
    vec4 color = texture2D(tex, gl_TexCoord[0].st) * gl_Color * lit + gl_TexCoord[2];
    float fog = clamp((gl_Fog.end - gl_FogFragCoord) * gl_Fog.scale, 0.0, 1.0);
    gl_FragData[0] = mix(gl_Fog.color, color, fog);
    gl_FragData[1] = vec4(normal * 0.5 + 0.5, 1.0);
}
