#version 120
uniform sampler2DRect colortex4;
uniform samplerCube skybox;
uniform sampler1D noise;
uniform sampler2DArray layers;
varying vec2 texcoord;
void main() {
    gl_FragColor = texture2DRect(colortex4, texcoord * 16.0) + textureCube(skybox, vec3(texcoord, 1.0))
        + texture1D(noise, texcoord.x) + texture2DArray(layers, vec3(texcoord, 0.0));
}
