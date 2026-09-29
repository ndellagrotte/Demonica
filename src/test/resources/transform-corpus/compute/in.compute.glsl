#version 430
layout(local_size_x = 8, local_size_y = 8) in;
layout(rgba16f) uniform image2D colorimg0;
uniform sampler2D colortex1;
void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    vec4 color = texelFetch(colortex1, texel, 0);
    imageStore(colorimg0, texel, color * 0.5);
}
