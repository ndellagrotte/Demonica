# DH 3.3.0 shader check (plan 3.3 open check 1)

## Jar identity
Jar: ~/.gradle/caches/modules-2/files-2.1/maven.modrinth/uCdwusMi/Sa0ttGJr/a91ff357520e0533f5375b9259201e2186fa28ba/uCdwusMi-Sa0ttGJr.jar
- `mcmod.info`: `"modid": "distanthorizons"`, `"version": "3.3.0"`, `"mcversion": "1.12.2"`, `"requiredMods": [ "cleanroom" ]`. Also `META-INF/1_12_2.distanthorizons_at.cfg`. So it is the 1.12.2 Cleanroom build.
- `build_info.json`: info_git_commit "main", info_git_branch "04e1f7096f5b511ab27e1a0e944a81c8cc430c92" (fields swapped in the file), build_source "User".

## 1. Micro-offset: DH zeroes Y (matches upstream Iris 26.1, not Demonica)
Entry `assets/distanthorizons/shaders/terrain/gl/vert.vert` (`#version 330 core`, header "version: 2025-12-22"), lines 72-88:
```
uint meta = vPosition.a;
uint mirco = (meta & 0xFF00u) >> 8u; // mirco offset which is a xyz 2bit value
float mx = (mirco & 1u)!=0u ? uMircoOffset : 0.0;
mx = (mirco & 2u)!=0u ? -mx : mx;
//float my = (mirco & 4u)!=0u ? uMircoOffset : 0.0;
//my = (mirco & 8u)!=0u ? -my : my;
float mz = (mirco & 16u)!=0u ? uMircoOffset : 0.0;
mz = (mirco & 32u)!=0u ? -mz : mz;
vertexWorldPos.x += mx;
//vertexWorldPos.y += my;
vertexWorldPos.z += mz;
```
The y component is computed in the source only as commented-out code and is NOT applied (my and the y add are both commented out). Position itself: `vertexWorldPos = vPosition.xyz + uModelOffset;` (line 68), then `gl_Position = uCombinedMatrix * vec4(vertexWorldPos, 1.0);` (line 110). Light bits: `uint lights = meta & 0xFFu;`, skyLight = `(float(lights/16u)+0.5)/16.0`, blockLight = `(mod(float(lights),16.0)+0.5)/16.0` (same as Demonica/upstream).
The `blaze/vert.vsh` variant is the modern-MC path, not used on 1.12.2 (GlDhTerrainShaderProgram loads `assets/distanthorizons/shaders/terrain/gl/vert.vert` and `.../frag.frag`).

Conclusion: DH applies (mx, 0, mz). Upstream Iris `vec3(mx, 0, mz)` (upstream :162) matches DH; Demonica's `vec3(mx, my, mz)` (DHTerrainTransformer.java:97) does not. Keep upstream's form, i.e. change to `vec3(mx, 0.0, mz)` (the my lines may stay, unused, or be dropped).

## 2. Vertex inputs and names
`terrain/gl/vert.vert` declarations (no explicit `layout(location=)`; locations come from the Java side):
```
in uvec4 vPosition;
in vec4 color;
// x: iris material id, y: face normal index, zw: block texture tile id (little endian)
in uvec4 irisData;
out vec4 vPos;  out vec4 vertexColor;  out vec3 vertexWorldPos;  out float vertexYPos;
out vec3 vBlockPos;
flat out uint vNormalIndex;
flat out uint vTextureTileId;
uniform bool uIsWhiteWorld;  uniform mat4 uCombinedMatrix;  uniform vec3 uModelOffset;
uniform float uWorldYOffset; uniform sampler2D uLightMap;  uniform float uMircoOffset;
uniform float uEarthRadius;  uniform float uFrameMod8; uniform float uViewWidth; uniform float uViewHeight;
```
Body: `vBlockPos = vec3(vPosition.xyz);` (l.64), `vNormalIndex = irisData.y;` (l.65), `vTextureTileId = irisData.z | (irisData.w << 8u);` (l.66).
Attribute binding in `com/seibel/distanthorizons/common/render/openGl/terrain/GlDhTerrainShaderProgram.class` constructor: attribute name array is {"vPosition", "color", "irisData"} (indices 0,1,2 -> locations 0,1,2); vertex pointers added: one unsigned-shorts pointer (vPosition), then two unsigned-bytes pointers (color, irisData), via GlVertexPointer.addUnsignedShortsPointer / addUnsignedBytesPointer.
Carriers: material id = irisData.x; normal/face index = irisData.y; texture tile id = irisData.z | irisData.w<<8; block position = vPosition.xyz (-> vBlockPos); light+micro-offset = vPosition.a. Demonica's compat program (`IrisLodRenderProgram.java:70-72`) binds locations 0/1/2 to "vPosition"/"iris_color"/"irisExtra", i.e. the same slots as DH's vPosition/color/irisData, so the semantic mapping is: irisExtra == DH's irisData (x material, y normal, zw tile id). Consistent with upstream's `iris_TexId = uvec2(irisExtra.z | (irisExtra.w << 8u), irisExtra.y)`.

Fragment (`terrain/gl/frag.frag`): `in vec3 vBlockPos; flat in uint vNormalIndex; flat in uint vTextureTileId; uniform sampler2D uBlockAtlas;` plus `blockFaceUv()` using the same switch as upstream's dh_blockFaceUv, and
```
if (vTextureTileId != 0u) {
    ivec2 atlasSize = textureSize(uBlockAtlas, 0);
    vec2 tileOrigin = vec2(float(vTextureTileId % 256u), float(vTextureTileId / 256u)) * 16.0;
    vec2 uv = (tileOrigin + blockFaceUv() * 16.0) / vec2(atlasSize);
    vec4 tile = texture(uBlockAtlas, uv);
    vec3 clampedColor = clamp(fragColor.rgb * (tile.rgb * 2.0), 0.0, 1.0);
    fragColor.rgb = mix(fragColor.rgb, clampedColor, tile.a);
}
```
This is exactly the math upstream's dh_sampleTexture() copies (tile id % 256 / / 256, *16, textureSize).

String search (grep -aF over every extracted entry of the jar):
- `irisExtra`: NOT present anywhere in the jar.
- `iris_TexId`, `iris_vBlockPos`, `dhBlockAtlas`, `dhMaterialId`, `dh_hasTexture`: NOT present anywhere (those are Iris-side names).
- `irisData`: present in `GlDhTerrainShaderProgram.class` and `assets/.../terrain/gl/vert.vert`.
- `uBlockAtlas`: `GlDhTerrainShaderProgram.class`, `terrain/gl/frag.frag`, `terrain/blaze/frag.fsh`.
So DH's attribute is named `irisData`, Iris's transformed shader names it `irisExtra`; the program binds by location, so the name mismatch is fine as long as Demonica's IrisLodRenderProgram uses slot 2 (it does). Sampler for the atlas in DH is `uBlockAtlas`; Iris-facing name `dhBlockAtlas` is Iris's, so Demonica must bind it itself (plan already says so).

## 3. Block atlas / textured-LOD path: yes
Class entries:
- `com/seibel/distanthorizons/core/render/AbstractBlockTextureAtlas.class` (comment in frag refers to `AbstractBlockTextureAtlas.TILES_PER_ROW`)
- `com/seibel/distanthorizons/common/render/openGl/terrain/GlBlockTextureAtlas.class` (+ `GlBlockTextureAtlas$1`, `$PixelUploadGlState`)
- `com/seibel/distanthorizons/common/render/blaze/wrappers/texture/BlazeBlockTextureAtlas.class`
- `com/seibel/distanthorizons/core/dataObjects/render/textures/BlockTextureRegistry.class`
- `com/seibel/distanthorizons/common/wrappers/block/TextureAtlasSpriteWrapper.class`
GlDhTerrainShaderProgram strings include `uBlockAtlas`, `enableTexturedLods`, `texturedLodsEnabled`, `IRIS_ACCESSOR`, `IIrisAccessor`. Also references to atlas/texture id in `ColumnBox.class`, `FullDataToRenderDataTransformer.class`, `IIrisAccessor.class`, `IAngelicaAccessor.class`, `MinecraftRenderWrapper.class`, `DhApiRenderProxy.class`. Textured LODs are a config toggle (`Config...Graphics.Texture`); I did not decompile when/how the atlas texture unit is bound for Iris programs or whether irisData.zw is non-zero when the toggle is off (not determined). Per the shader, tile id 0 means untextured.

## 4. Upstream transforms missing from Demonica's DHTerrainTransformer
(Upstream = .reference/Iris-26.1/.../DHTerrainTransformer.java.) Everything else matches (texture matrices, gl_ProjectionMatrix, MultiTexCoord handling, gl_Color/gl_Normal, normal matrix, ftransform, getVertexPosition, matrix uniforms, Intel workaround), modulo API shape and Demonica's addIfNotExists on matrix uniforms.
- :45-71 Fragment-only injection BEFORE_DECLARATIONS: `in vec3 iris_vBlockPos;`, `flat in uvec2 iris_TexId;`, `uniform sampler2D dhBlockAtlas;`, functions `dh_hasTexture()` (:51), `dh_blockFaceUv()` (:52-63), `dh_sampleTexture()` (:64-69). Demonica has no fragment-specific DH block at all.
- :145 vertex `out vec3 iris_vBlockPos;` declaration in injectVertInit (Demonica lacks it).
- :146 vertex `flat out uvec2 iris_TexId;` declaration (lacks).
- :166 in `_vert_init`: `iris_vBlockPos = vec3(vPosition.xyz);` (lacks).
- :167 in `_vert_init`: `iris_TexId =  uvec2(irisExtra.z | (irisExtra.w << 8u), irisExtra.y);` (lacks).
- :162 `vec3(mx, 0, mz)` instead of Demonica's `vec3(mx, my, mz)` (Demonica :97); DH confirms 0 for y.
- :163 upstream `irisNormals[irisExtra.y]` (uint index) vs Demonica `irisNormals[int(irisExtra.y)]`; Demonica's int cast is a harmless (arguably safer) difference.
- :147 upstream declares `const vec3 irisNormals[6]` as a declaration; Demonica injects the same via injectFunction (:85); equivalent.
- :93 upstream `Iris.logger.warn("Type is " ...)` debug log: not needed.
- Nuance: upstream calls `CommonTransformer.transform(..., false)` (:20) vs Demonica's `true` (:20 of Demonica); not checked here whether that is a deliberate TauMC/glsl-transformer-port difference.
Also note PORTING_GUIDE rule 5 (u suffix): upstream's `vec2(float(iris_TexId.x % 256u), ...)` already uses u literals; `vec3(mx, 0, mz)` is fine as a vec3 constructor but write `0.0` in Demonica's string.

## Recommendation
Keep upstream: y micro-offset = 0 (DH 3.3.0's own vertex shader has `my`/`vertexWorldPos.y += my` commented out). Add the iris_vBlockPos / iris_TexId outputs and the fragment helper block. The attribute slot-2 `irisExtra` data layout (x material, y normal, z/w tile id) is confirmed by DH's `irisData` comment.
