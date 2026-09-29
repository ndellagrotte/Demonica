#version 330 core

uniform float actinium_currentAlphaTest ; 
layout ( location = 0 ) out vec4 actinium_FragData0 ; 
uniform vec4 actinium_FogColor ; 
uniform float actinium_FogEnd ; 
uniform float actinium_FogStart ; 
uniform float actinium_FogDensity ; 
uniform mat4 actinium_LightmapTextureMatrix ; 
uniform mat3 actinium_NormalMatrix ; 
uniform mat4 actinium_ProjectionMatrixInverse ; 
uniform mat4 actinium_ProjectionMatrix ; 
uniform mat4 actinium_ModelViewMatrixInverse ; 
uniform mat4 actinium_ModelViewMatrix ; 
uniform sampler2DShadow outer ; 
uniform sampler2DShadow inner ; 
in vec3 p ; 
struct actinium_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
actinium_FogParameters actinium_Fog = actinium_FogParameters ( actinium_FogColor , actinium_FogDensity , actinium_FogStart , actinium_FogEnd , 1.0 / ( actinium_FogEnd - actinium_FogStart ) ) ; 
void main ( ) { 
	 actinium_FragData0 = vec4 ( texture ( outer , vec3 ( texture ( inner , p ) . r ) ) ) ; 
	if ( actinium_FragData0 . a <= actinium_currentAlphaTest ) discard ; 
} 
