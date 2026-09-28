#version 120
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
varying vec2 texcoord;
void main() {
    vec3 color = texture2D(colortex0, gl_TexCoord[0].st).rgb;
    color += texture2D(colortex4, texcoord).rgb * 0.5;
    color *= texture2D(colortex5, texcoord).a;
    gl_FragColor = vec4(color, 1.0);
}
