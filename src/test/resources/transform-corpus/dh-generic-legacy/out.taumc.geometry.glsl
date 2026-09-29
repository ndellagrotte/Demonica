#version 330 core

out vec4 glcolor ; 
out float material ; 
out vec2 texcoord ; 
uniform mat4 iris_ProjectionMatrix ; 
uniform mat4 iris_ModelViewMatrix ; 
uniform mat4 iris_ProjectionMatrixInverse ; 
uniform mat4 iris_ModelViewMatrixInverse ; 
uniform mat3 iris_NormalMatrix ; 
uniform vec4 iris_FogColor ; 
uniform float iris_FogEnd ; 
uniform float iris_FogStart ; 
uniform float iris_FogDensity ; 
layout ( triangles ) in ; 
layout ( triangle_strip , max_vertices = 3 ) out ; 
in vec4 glcolor [ ] ; 
out vec4 gtexture ; 
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
	 glcolor = vec4 ( 0.0f ) ; 
	material = 0.0f ; 
	texcoord = vec2 ( 0.0f ) ; 
	for ( int i = 0 ; 
	i < 3 ; 
	i ++ ) { 
	 gtexture = glcolor [ i ] ; 
	gl_Position = iris_ProjectionMatrix * iris_ModelViewMatrix * gl_in [ i ] . gl_Position ; 
	EmitVertex ( ) ; 
} 
EndPrimitive ( ) ; } 
