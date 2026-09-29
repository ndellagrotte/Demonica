#version 330 core

uniform float actinium_currentAlphaTest ; 
layout ( location = 0 ) out vec4 actinium_FragData0 ; 
in vec4 actinium_FrontColor ; 
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
uniform sampler2D tex ; 
uniform sampler2DShadow shadowMap ; 
in vec2 texcoord ; 
in vec4 viewPos ; 
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
	 float lit = vec4 ( texture ( shadowMap , vec3 ( texcoord , viewPos . z ) ) ) . r ; 
	actinium_FragData0 = texture ( tex , texcoord ) * actinium_FrontColor * mix ( 0.5 , 1.0 , lit ) ; 
	if ( actinium_FragData0 . a <= actinium_currentAlphaTest ) discard ; 
} 
