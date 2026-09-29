#version 330 core

#define BP_GL_ES 0
#define BP_MOBILEGLUES 0
#define BP_NATIVE_ES 0
// Native ESSL has no fixed-function fog interface.  MobileGlues and SFPEW
// translate the legacy gl_Fog/gl_FogFragCoord symbols, while a direct ESSL
// context receives equivalent values through explicit uniforms.

uniform float actinium_currentAlphaTest ; 
layout ( location = 0 ) out vec4 actinium_FragData0 ; 
uniform vec4 actinium_FogColor ; 
uniform float actinium_FogEnd ; 
uniform float actinium_FogStart ; 
uniform float actinium_FogDensity ; 
in float actinium_FogFragCoord ; 
uniform mat4 actinium_LightmapTextureMatrix ; 
uniform mat3 actinium_NormalMatrix ; 
uniform mat4 actinium_ProjectionMatrixInverse ; 
uniform mat4 actinium_ProjectionMatrix ; 
uniform mat4 actinium_ModelViewMatrixInverse ; 
uniform mat4 actinium_ModelViewMatrix ; 
uniform sampler2D sampler ; 
uniform vec2 screenSize ; 
uniform float fogDensity ; 
uniform vec3 fogColor ; 
uniform float opacity ; 
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
	 vec2 uv = gl_FragCoord . xy / screenSize ; 
	vec3 color = texture ( sampler , uv ) . rgb ; 
	color = mix ( color , actinium_Fog . color . rgb , clamp ( ( actinium_FogFragCoord - actinium_Fog . start ) * actinium_Fog . scale , 0.0 , 1.0 ) ) ; 
	color = mix ( color , fogColor , fogDensity ) ; 
	actinium_FragData0 = vec4 ( color , opacity ) ; 
	if ( actinium_FragData0 . a <= actinium_currentAlphaTest ) discard ; 
} 
