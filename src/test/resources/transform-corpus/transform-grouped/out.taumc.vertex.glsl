#version 330 core

layout ( location = 2 ) in vec4 iris_MultiTexCoord0 ; 
layout ( location = 0 ) in vec4 iris_Vertex ; 
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
out vec4 iris_FrontColor ; 
out float iris_FogFragCoord ; 
out vec2 uv ; 
out float fade ; 
struct iris_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
iris_FogParameters iris_Fog = iris_FogParameters ( iris_FogColor , iris_FogDensity , iris_FogStart , iris_FogEnd , 1.0f / ( iris_FogEnd - iris_FogStart ) ) ; 
out vec3 viewDir ; 
vec4 iris_ftransform ( ) { 
	 return ( iris_ProjectionMatrix * iris_ModelViewMatrix ) * iris_Vertex ; 
} 
void main ( ) { 
	 viewDir = vec3 ( 0.0f ) ; 
	fade = 0.0f ; 
	iris_FrontColor = vec4 ( 1.0 ) ; 
	iris_FogFragCoord = 0.0f ; 
	uv = iris_MultiTexCoord0 . xy ; 
	gl_Position = iris_ftransform ( ) ; 
} 
