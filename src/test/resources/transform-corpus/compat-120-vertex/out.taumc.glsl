#version 330 core

layout ( location = 2 ) in vec4 actinium_MultiTexCoord0 ; 
layout ( location = 1 ) in vec4 actinium_Color ; 
layout ( location = 0 ) in vec4 actinium_Vertex ; 
out vec4 actinium_FrontColor ; 
uniform vec4 actinium_FogColor ; 
uniform float actinium_FogEnd ; 
uniform float actinium_FogStart ; 
uniform float actinium_FogDensity ; 
out float actinium_FogFragCoord ; 
uniform mat4 actinium_LightmapTextureMatrix ; 
uniform mat3 actinium_NormalMatrix ; 
uniform mat4 actinium_ProjectionMatrixInverse ; 
uniform mat4 actinium_ProjectionMatrix ; 
uniform mat4 actinium_ModelViewMatrixInverse ; 
uniform mat4 actinium_ModelViewMatrix ; 
out vec2 texcoord ; 
out vec4 viewPos ; 
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
	 actinium_FrontColor = vec4 ( 1.0 ) ; 
	actinium_FogFragCoord = 0.0 ; 
	viewPos = actinium_ModelViewMatrix * actinium_Vertex ; 
	texcoord = actinium_MultiTexCoord0 . st ; 
	actinium_FrontColor = actinium_Color ; 
	gl_Position = ( actinium_ProjectionMatrix * actinium_ModelViewMatrix * actinium_Vertex ) ; 
} 
