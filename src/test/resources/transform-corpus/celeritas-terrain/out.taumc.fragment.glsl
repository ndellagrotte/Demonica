#version 330 core

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
uniform float iris_currentAlphaTest ; 
layout ( location = 1 ) out vec4 iris_FragData1 ; 
layout ( location = 0 ) out vec4 iris_FragData0 ; 
in vec4 iris_FrontColor ; 
in float iris_FogFragCoord ; 
uniform sampler2D gtexture ; 
uniform sampler2D lightmap ; 
in vec2 texcoord ; 
in vec2 lmcoord ; 
in vec4 glcolor ; 
in vec3 normal ; 
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
	 vec4 color = texture ( gtexture , texcoord ) * glcolor * texture ( lightmap , lmcoord ) ; 
	iris_FragData0 = color ; 
	iris_FragData1 = vec4 ( normal * 0.5 + 0.5 , 1.0 ) ; 
	if ( iris_FragData0 . a <= iris_currentAlphaTest ) discard ; 
} 
