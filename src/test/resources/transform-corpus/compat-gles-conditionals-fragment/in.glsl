#ifdef GL_ES
precision mediump float;
#endif
#define USE_TINT 1
#define SCALE(c) ((c) * 0.5)
uniform sampler2D tex;
#if USE_TINT
uniform vec4 tint;
#else
const vec4 tint = vec4(1.0);
#endif
void main(void) {
    gl_FragColor = SCALE(texture2D(tex, gl_TexCoord[0].xy) * tint);
}
