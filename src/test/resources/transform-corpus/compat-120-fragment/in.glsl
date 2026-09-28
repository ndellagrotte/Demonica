#version 120
uniform sampler2D tex;
uniform sampler2DShadow shadowMap;
varying vec2 texcoord;
varying vec4 viewPos;
void main() {
    float lit = shadow2D(shadowMap, vec3(texcoord, viewPos.z)).r;
    gl_FragColor = texture2D(tex, texcoord) * gl_Color * mix(0.5, 1.0, lit);
}
