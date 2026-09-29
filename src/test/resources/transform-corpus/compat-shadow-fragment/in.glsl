#version 120
#extension GL_ARB_texture_rectangle : enable
uniform sampler2D texture;
uniform sampler2DShadow shadowMap;
uniform sampler1DShadow shadowLine;
uniform sampler3D volume;
uniform samplerCube sky;
uniform sampler2DRect rect;
varying vec4 shadowPos;
float sample(vec2 uv) {
    float new = texture2DLod(texture, uv, 0.0).r;
    return new + texture2DProj(texture, vec3(uv, 1.0)).g;
}
void main() {
    float a = shadow2D(shadowMap, shadowPos.xyz).r;
    float b = shadow2DProj(shadowMap, shadowPos).x;
    float c = shadow2DLod(shadowMap, shadowPos.xyz, 0.0).r;
    float d = shadow1D(shadowLine, shadowPos.xyz).r + shadow1DProj(shadowLine, shadowPos).r
        + shadow1DLod(shadowLine, shadowPos.xyz, 1.0).r;
    vec4 e = textureCube(sky, shadowPos.xyz) + texture2DRect(rect, shadowPos.xy) + texture3D(volume, shadowPos.xyz);
    gl_FragColor = texture2D(texture, shadowPos.xy) * (a + b + c + d + sample(shadowPos.zw)) + e;
}
