#version 420 core

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
layout ( rgba8 ) uniform image2D colorimg0 ; 
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
void main ( ) { 
	 float patch layout ( rgba8 ) uniform image2D colorimg0 ; 
	in vec2 texcoord ; 
	void main ( ) <missing ';'> { 
	 float patch = 0.5 ; 
	iris_FragData0 = <missing ';'> imageLoad <missing ';'> ( <missing ';'> colorimg0 , ivec2 ( texcoord * 16.0 ) ) * patch ; 
} 
<missing '}'> 