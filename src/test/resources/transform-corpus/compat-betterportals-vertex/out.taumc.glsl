#version 330 core

#define BP_GL_ES 0
#define BP_MOBILEGLUES 0
#define BP_NATIVE_ES 0
#define BP_SKIP_FIXED_CLIP 1
// Native ESSL has no fixed-function vertex interface.  MobileGlues and SFPEW
// translate the legacy interface before submitting it to GLES; a direct ESSL
// context uses the explicit in/matrix path below.

















// The compatibility wrappers do not expose gl_ClipVertex in translated ESSL.
// SFPEW's generated source is modern as well, so omit the assignment there.

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
	vec4 viewPos = actinium_ModelViewMatrix * actinium_Vertex ; 
	gl_Position = actinium_ProjectionMatrix * viewPos ; 
	actinium_FogFragCoord = length ( viewPos . xyz ) ; 
} 
