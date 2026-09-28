#version 430 core

uniform mat4 iris_TextureMatrix ; 
uniform mat4 iris_LightmapTextureMatrix ; 
uniform mat3 iris_NormalMatrix ; 
uniform mat4 iris_ProjectionMatrixInverse ; 
uniform mat4 iris_ProjectionMatrix ; 
uniform mat4 iris_ModelViewMatrixInverse ; 
uniform mat4 iris_ModelViewMatrix ; 
layout ( std430 , binding = 7 ) buffer ActiniumShadowBoundsStats { 
	 uint textureCalls ; 
	uint textureRejected ; 
	uint filteredCalls ; 
	uint filteredRejected ; 
	uint textureSamplesSaved ; 
	uint filteredSamplesSaved ; 
} 
actiniumShadowBoundsStats ; 
uniform vec4 iris_FogColor ; 
uniform float iris_FogEnd ; 
uniform float iris_FogStart ; 
uniform float iris_FogDensity ; 
uniform float iris_currentAlphaTest ; 
layout ( location = 0 ) out vec4 iris_FragData0 ; 
in vec4 iris_FrontColor ; 
in float iris_FogFragCoord ; 
uniform sampler2D shadowtex0 ; 
in vec3 shadowCoord ; 
const float shadowMapResolution = 2048.0 ; 
struct iris_FogParameters { 
	 vec4 color ; 
	float density ; 
	float start ; 
	float end ; 
	float scale ; 
} 
; 
iris_FogParameters iris_Fog = iris_FogParameters ( iris_FogColor , iris_FogDensity , iris_FogStart , iris_FogEnd , 1.0f / ( iris_FogEnd - iris_FogStart ) ) ; 
float texture2DShadow2x2 ( sampler2D shadowtex , vec3 shadowPos ) { 
	 atomicAdd ( actiniumShadowBoundsStats . textureCalls , 1u ) ; 
	if ( ! ( shadowPos . x > 1.5 / shadowMapResolution && shadowPos . x < 1.0 - 1.5 / shadowMapResolution && shadowPos . y > 1.5 / shadowMapResolution && shadowPos . y < 1.0 - 1.5 / shadowMapResolution && shadowPos . z > 0.0 && shadowPos . z < 1.0 ) ) { 
	 atomicAdd ( actiniumShadowBoundsStats . textureRejected , 1u ) ; 
	atomicAdd ( actiniumShadowBoundsStats . textureSamplesSaved , 4u ) ; 
	return 1.0 ; 
} 
vec2 offset = vec2 ( 0.5 / shadowMapResolution ) ; 
float shadow = step ( shadowPos . z , texture ( shadowtex , shadowPos . xy + offset ) . r ) ; 
shadow += step ( shadowPos . z , texture ( shadowtex , shadowPos . xy - offset ) . r ) ; 
return shadow * 0.5 ; } 
void main ( ) { 
	 iris_FragData0 = vec4 ( vec3 ( texture2DShadow2x2 ( shadowtex0 , shadowCoord ) ) , 1.0 ) ; 
	if ( iris_FragData0 . a <= iris_currentAlphaTest ) discard ; 
} 
