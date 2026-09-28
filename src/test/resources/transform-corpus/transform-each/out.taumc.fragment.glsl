#version 330 core

uniform mat4 iris_TextureMatrix ; 
uniform mat4 iris_LightmapTextureMatrix ; 
uniform mat3 iris_NormalMatrix ; 
uniform mat4 iris_ProjectionMatrixInverse ; 
uniform mat4 iris_ProjectionMatrix ; 
uniform mat4 iris_ModelViewMatrixInverse ; 
uniform mat4 iris_ModelViewMatrix ; 
uniform vec4 iris_FogColor ; 
uniform float iris_FogEnd ; 
uniform float iris_FogStart ; 
uniform float iris_FogDensity ; 
layout ( location = 0 ) out vec4 iris_FragData0 ; 
in vec4 iris_FrontColor ; 
in float iris_FogFragCoord ; 
uniform sampler2D colortex0 ; 
in vec2 texcoord ; 
struct iris_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
iris_FogParameters iris_Fog = iris_FogParameters ( iris_FogColor , iris_FogDensity , iris_FogStart , iris_FogEnd , 1.0f / ( iris_FogEnd - iris_FogStart ) ) ; 
vec3 tonemap ( const in vec3 color , const float exposure ) { 
	 float scale = exposure ; 
	float shoulder = scale * 0.5 ; 
	return color * scale / ( color + vec3 ( shoulder ) ) ; 
} 
void main ( ) { 
	 iris_FragData0 = vec4 ( tonemap ( texture ( colortex0 , texcoord ) . rgb , 1.5 ) , 1.0 ) ; 
} 
