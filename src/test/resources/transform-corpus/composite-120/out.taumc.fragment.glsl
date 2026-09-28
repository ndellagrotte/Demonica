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
layout ( location = 0 ) out vec4 iris_FragData0 ; 
in vec4 iris_FrontColor ; 
in float iris_FogFragCoord ; 
uniform sampler2D colortex0 ; 
uniform sampler2D iris_customTex4 ; 
uniform sampler2D colortex5 ; 
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
	 vec3 color = texture ( colortex0 , gl_TexCoord [ 0 ] . st ) . rgb ; 
	color += texture ( iris_customTex4 , texcoord ) . rgb * 0.5 ; 
	color *= texture ( colortex5 , texcoord ) . a ; 
	iris_FragData0 = vec4 ( color , 1.0 ) ; 
} 
