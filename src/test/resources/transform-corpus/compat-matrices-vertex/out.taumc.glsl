#version 330 core

out vec4 actinium_TexCoord1 ; 
out vec4 actinium_TexCoord0 ; 
layout ( location = 4 ) in vec3 actinium_Normal ; 
layout ( location = 3 ) in vec4 actinium_MultiTexCoord1 ; 
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
in vec4 extra ; 
uniform int unit ; 
out vec3 normal ; 
out vec4 eye ; 
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
	gl_Position = ( actinium_ProjectionMatrix * actinium_ModelViewMatrix ) * actinium_Vertex ; 
	actinium_TexCoord0 = mat4 ( 1.0 ) * actinium_MultiTexCoord0 ; 
	actinium_TexCoord1 = actinium_LightmapTextureMatrix * actinium_MultiTexCoord1 + mat4 [ 8 ] ( mat4 ( 1.0 ) , actinium_LightmapTextureMatrix , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) , mat4 ( 1.0 ) ) [ unit ] * extra ; 
	normal = actinium_NormalMatrix * actinium_Normal ; 
	eye = actinium_ModelViewMatrixInverse * ( actinium_ProjectionMatrixInverse * gl_Position ) ; 
	actinium_FogFragCoord = length ( ( actinium_ModelViewMatrix * actinium_Vertex ) . xyz ) ; 
	actinium_FrontColor = actinium_Color * 0.75 ; 
} 
