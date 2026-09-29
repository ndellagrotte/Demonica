#version 330 core

uniform float actinium_currentAlphaTest ; 
layout ( location = 1 ) out vec4 actinium_FragData1 ; 
layout ( location = 0 ) out vec4 actinium_FragData0 ; 
in vec4 actinium_TexCoord2 ; 
in vec4 actinium_TexCoord0 ; 
in vec4 actinium_FrontColor ; 
uniform vec4 actinium_SceneColor ; 
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
uniform sampler2D tex ; 
in vec3 normal ; 
struct actinium_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
actinium_FogParameters actinium_Fog = actinium_FogParameters ( actinium_FogColor , actinium_FogDensity , actinium_FogStart , actinium_FogEnd , 1.0 / ( actinium_FogEnd - actinium_FogStart ) ) ; 
struct actinium_LightSourceParameters { 
	 vec4 ambient ; 
	vec4 diffuse ; 
	vec4 specular ; 
	vec4 position ; 
	vec4 halfVector ; 
	vec3 spotDirection ; 
	float spotExponent ; 
	float spotCutoff ; 
	float spotCosCutoff ; 
	float constantAttenuation ; 
	float linearAttenuation ; 
	float quadraticAttenuation ; 
} 
; 
uniform actinium_LightSourceParameters actinium_LightSource [ 2 ] ; 
struct actinium_MaterialParameters { 
	 vec4 emission ; 
	vec4 ambient ; 
	vec4 diffuse ; 
	vec4 specular ; 
	float shininess ; 
} 
; 
uniform actinium_MaterialParameters actinium_FrontMaterial ; 
void main ( ) { 
	 vec3 l = normalize ( actinium_LightSource [ 0 ] . position . xyz ) ; 
	float diffuse = max ( dot ( normalize ( normal ) , l ) , 0.0 ) ; 
	vec4 lit = actinium_SceneColor + actinium_FrontMaterial . emission + actinium_LightSource [ 1 ] . diffuse * actinium_FrontMaterial . diffuse * diffuse ; 
	vec4 color = texture ( tex , actinium_TexCoord0 . st ) * actinium_FrontColor * lit + actinium_TexCoord2 ; 
	float fog = clamp ( ( actinium_Fog . end - actinium_FogFragCoord ) * actinium_Fog . scale , 0.0 , 1.0 ) ; 
	actinium_FragData0 = mix ( actinium_Fog . color , color , fog ) ; 
	actinium_FragData1 = vec4 ( normal * 0.5 + 0.5 , 1.0 ) ; 
	if ( actinium_FragData0 . a <= actinium_currentAlphaTest ) discard ; 
} 
