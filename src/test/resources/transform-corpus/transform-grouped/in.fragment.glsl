#version 120
uniform sampler2D colortex0;
varying vec2 uv;
varying float fade;
varying vec3 viewDir;
void main() {
    gl_FragColor = vec4(texture2D(colortex0, uv).rgb * fade + normalize(viewDir) * 0.0, 1.0);
}
