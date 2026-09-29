#version 330 core

#extension GL_ARB_texture_rectangle : enable

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
uniform sampler2D gtexture ; 
uniform sampler2DShadow shadowMap ; 
uniform sampler1DShadow shadowLine ; 
uniform sampler3D volume ; 
uniform samplerCube sky ; 
uniform sampler2DRect rect ; 
in vec4 shadowPos ; 
struct actinium_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
actinium_FogParameters actinium_Fog = actinium_FogParameters ( actinium_FogColor , actinium_FogDensity , actinium_FogStart , actinium_FogEnd , 1.0 / ( actinium_FogEnd - actinium_FogStart ) ) ; 
float sample ( vec2 uv ) { 
	 float new = textureLod ( gtexture , uv , 0.0 ) . r ; 
	return new + textureProj ( gtexture , vec3 ( uv , 1.0 ) ) . g ; 
} 
void main ( ) { 
	 float a = vec4 ( texture ( shadowMap , shadowPos . xyz ) ) . r ; 
	float b = vec4 ( textureProj ( shadowMap , shadowPos ) ) . x ; 
	float c = vec4 ( textureLod ( shadowMap , shadowPos . xyz , 0.0 ) ) . r ; 
	float d = vec4 ( texture ( shadowLine , shadowPos . xyz ) ) . r + vec4 ( textureProj ( shadowLine , shadowPos ) ) . r + vec4 ( textureLod ( shadowLine , shadowPos . xyz , 1.0 ) ) . r ; 
	vec4 e = texture ( sky , shadowPos . xyz ) + texture ( rect , shadowPos . xy ) + texture ( volume , shadowPos . xyz ) ; 
	actinium_FragData0 = texture ( gtexture , shadowPos . xy ) * ( a + b + c + d + sample ( shadowPos . zw ) ) + e ; 
	if ( actinium_FragData0 . a <= actinium_currentAlphaTest ) discard ; 
} 
