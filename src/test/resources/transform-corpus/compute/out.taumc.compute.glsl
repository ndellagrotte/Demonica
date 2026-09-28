#version 430 core

uniform vec4 iris_FogColor ; 
uniform float iris_FogEnd ; 
uniform float iris_FogStart ; 
uniform float iris_FogDensity ; 
layout ( local_size_x = 8 , local_size_y = 8 ) in ; 
layout ( rgba16f ) uniform image2D colorimg0 ; 
uniform sampler2D colortex1 ; 
struct iris_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
iris_FogParameters iris_Fog = iris_FogParameters ( iris_FogColor , iris_FogDensity , iris_FogStart , iris_FogEnd , 1.0f / ( iris_FogEnd - iris_FogStart ) ) ; 
void main ( ) { 
	 ivec2 texel = ivec2 ( gl_GlobalInvocationID . xy ) ; 
	vec4 color = texelFetch ( colortex1 , texel , 0 ) ; 
	imageStore ( colorimg0 , texel , color * 0.5 ) ; 
} 
