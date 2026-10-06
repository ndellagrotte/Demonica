# Iris porting plan

Date: 2026-09-29. Tree: `dev` at `9bac5226` (0.7.0-SNAPSHOT). Upstream: `.reference/Iris-26.1`, Iris
**1.11.4+mc26.1.2** (Sodium 0.9.2, glsl-transformer 3.0.0-pre3 on both sides). Companion pages:
[IRIS_PORTING_RESEARCH.md](IRIS_PORTING_RESEARCH.md) (the survey this plan corrects and orders) and
[glsl-transformer_adoption/PORTING_GUIDE.md](glsl-transformer_adoption/PORTING_GUIDE.md) (the workflow for
transform-layer ports).

Path shorthands: **D** = `shader/src/main/java/net/coderbot/iris`, **I** =
`.reference/Iris-26.1/common/src/main/java/net/irisshaders/iris`, **H** =
`src/main/java/com/demonica/mixin/features/iris`, **G** = `glsm/src/main/java/com/gtnewhorizons/angelica/glsm`.
Line numbers are as of the tree above.

## 1. Verification of the research

The research page was checked against both trees on 2026-09-29 (file lists, string and structure diffs, and
reading of every cited site). Its shape holds: the file counts in its Appendix A, the version facts, the
transformer renames, the two missing feature flags, the six missing program ids, the missing directives, the
absent DH transformer outputs, the absent API v0 shadow callback, the robustness items, the mechanics, and
the "what not to port" list are all as stated. The following are not, and this plan is built on the corrected
picture.

| # | Research said | Verified |
|---|---|---|
| 1 | Intel HD 4000 workaround absent (§3.1) | Present: `D/pipeline/transform/ShaderTransformer.java:317-320`, called for CELERITAS_TERRAIN (`:293`), COMPOSITE (`CoreTransformHelper.java:62`) and both DH transformers |
| 2 | Legacy texture-function renames and `shadow2D` wrapping are a regex pre-pass covering more forms | AST in D too: `D/pipeline/transform/transformer/CommonTransformer.java:121-123` uses `GlslTransformUtils.TEXTURE_RENAMES` (18 entries, a superset of upstream's 12) and `ShaderAst.renameAndWrapShadow`. The `texture2DRect`/`texture2DArray`/`textureCube` regex runs only on GLSM's compat path (`CompatShaderTransformer.java:180`), never for shader packs |
| 3 | `gl_MultiTexCoord4-7` zeroing absent; `AttributeTransformer` renames 0-1 only | `AttributeTransformer.java:56-77` handles 0-3. D's DH transformers zero 4-7 already (private copies, `DHTerrainTransformer.java:107-111`, `DHGenericTransformer.java:115-119`). The gap is ATTRIBUTES and CELERITAS_TERRAIN (4-7) and COMPOSITE (`CoreTransformHelper.java:57` renames only 0; upstream replaces 1-7) |
| 4 | DH transformer lacks `dhMaterialId`, `iris_TexId`, `iris_vBlockPos` | `dhMaterialId` is ported (`DHTerrainTransformer.java:76,99`). Missing: `iris_TexId`, `iris_vBlockPos`, the fragment `dhBlockAtlas` sampler and `dh_hasTexture`/`dh_blockFaceUv`/`dh_sampleTexture` (I `DHTerrainTransformer.java:45-71,145-146,166-167`), DHGeneric's fragment stubs (I `DHGenericTransformer.java:45-54`). Also D applies `vec3(mx, my, mz)` where upstream applies `vec3(mx, 0, mz)` (D `:97`, I `:162`) |
| 5 | `iris_TexId` goes "through the geometry stage" | No geometry passthrough upstream; it is a vertex out and a fragment in |
| 6 | Custom-texture flip snapshot needs an audit (§3.7) | Present: `D/gl/program/ProgramSamplers.java:84-85,231`, threaded through `postprocess/CompositeRenderer`, `postprocess/FinalPassRenderer`, `shadows/ShadowCompositeRenderer` |
| 7 | `ImageClearPass` needs an audit | Equivalent present: `DeferredWorldRenderingPipeline.java:351-355` collects images to clear and `:1821-1823` calls `GlImage.clear()` (`glClearTexImage`) in `beginLevelRendering` |
| 8 | End flash is the End gateway/portal flash, with API `supportsEndFlash`; Actinium's end-portal renderer is the capture point | It is MC 26.x's End *sky* flash (`I/mixin/MixinEndFlash.java` targets `EndFlashState`); `supportsEndFlash()` is a pipeline method; 1.12.2 has no such effect and the end-portal renderer captures nothing. Complementary's custom uniforms already fail with `Unknown variable: endFlashIntensity` (`D/uniforms/custom/CustomUniforms.java:296`), so zero stubs have value now |
| 9 | `maxPlayerArmor` cap "20 or 50, check"; shallow-water uniforms; `iris_currentAlphaFunc` missing | 50 (I `IrisExclusiveUniforms.java:71,241`). `isInShallowWater`/`vehicleInShallowWater` are helper methods, not uniforms. `iris_currentAlphaFunc` is present (`D/uniforms/IrisInternalUniforms.java:55`). Missing and unlisted: `currentColorSpace`, `logicalHeightLimit` |
| 10 | `heavyFog` easy to add | Declared and dead: `D/uniforms/ExternallyManagedUniforms.java:20` marks it externally managed and nothing sets it |
| 11 | `isRightHanded` at parity | Hardcoded `() -> true` (`D/uniforms/IrisExclusiveUniforms.java:39`) |
| 12 | Shadow/default inverse matrices: "audit whether GLSM already fills them" | Real gap. `CoreTransformHelper.java:32,34` renames `gl_ModelViewMatrixInverse`/`gl_ProjectionMatrixInverse` to `iris_*Inverse` in ATTRIBUTES, CELERITAS_TERRAIN and COMPOSITE programs. The Celeritas seam (`D/celeritas/IrisCeleritasChunkShaderInterface.java:85-87,235-238`) and the DH programs upload them per draw; `G/CompatUniformManager.java:87-96` has slots for `iris_ModelViewMatrix`, `iris_ProjectionMatrix`, `iris_NormalMatrix`, `iris_LightmapTextureMatrix`, `iris_TextureMatrix` and only the `actinium_*Inverse` inverses (`:243-248`, `:275-280`). Gbuffers and composite programs read an unset uniform. Upstream's Shadow/Default split is a Sodium-UBO detail |
| 13 | Six program ids missing; the rest "just gain the names" | Also: D's enum already has `ShadowSolid`, `ShadowCutout`, `TerrainSolid`, `TerrainCutout`, `TerrainCutoutMip`, `Item` (`D/shaderpack/loading/ProgramId.java:14-35`) but `D/shaderpack/ProgramSet.java:82-148` never reads those files and `get()` (`:421-452`) returns empty for them. `D/celeritas/IrisTerrainPass.java:12` names `gbuffers_terrain_cutout`, but `CeleritasTerrainPipeline.java:102-108` feeds SOLID and CUTOUT both from `gbuffers_terrain` and SHADOW and SHADOW_CUTOUT both from `shadow`. Upstream reads every id and selects the solid/cutout split (`I/pipeline/programs/ShaderKey.java:33-34,77`, `SodiumPrograms.java:208-212`) |
| 14 | `separateEntityDraws` toggle exists on both sides | Parsed (`D/shaderpack/ShaderProperties.java:224`) with no caller of `shouldUseSeparateEntityDraws()` |
| 15 | Upstream matches option names by suffix/prefix; D enumerates spellings | Upstream also matches exactly; it builds the set in a loop over `MAX_SHADOW_COLOR_BUFFERS_IRIS` (I `OptionAnnotatedSource.java:86-94`). D (`D/shaderpack/option/OptionAnnotatedSource.java:91-128`) hardcodes buffers 0-1, lacks `shadowcolor0/1MinMagNearest` lowercase, `shadowHardwareFiltering2-7`, and `voxelDistance` |
| 16 | `patchIntegerAttribute` is a port target | Not applicable: ATTRIBUTES programs receive no integer entity attribute in 1.12.2 (`mc_Entity` is bound at location 11, `DeferredWorldRenderingPipeline.java:1107`, and entity ids travel as uniforms through `EntityPatcher`); CELERITAS_TERRAIN has `ShaderTransformer.replaceMCEntity` (`:373-405`) |
| 17 | Feature-flag handling differs only by the two flags | D defines `IRIS_FEATURE_<flag>` for every usable flag (`D/shaderpack/ShaderPack.java:218-227`); upstream only for the pack's valid optional flags (I `ShaderPack.java:246-250`). Upstream throws when SSBO or custom images are used without the flag (I `:216-222`); D does not. D accepts the `TESSELATION` spelling (`FeatureFlags.java:66-75`); upstream's `isInvalid` does not |
| 18 | DH LODs may be culled only by the box culler; check the callbacks' hooks | D has no DH mixins; `AdvancedShadowCullingFrustum`, `BoxCullingFrustum`, `NonCullingFrustum`, `CullEverythingFrustum` implement `IDhApiShadowCullingFrustum` directly and `D/compat/dh/LodRendererEvents.java:263` binds `ShadowRenderer.FRUSTUM`. The shadow callback is an API-surface item for other mods, not a DH-correctness item |
| 19 | Smaller facts | Upstream `BiomeUniforms` is 129 lines, not 298. `entity_render_context` is under `I/mixin/`. Host mixins: 28 top-level, 31 with `startup/`. `ClearPass*` is in D `pipeline/`, `ShadowCompositeRenderer` in D `shadows/`, `CompositePass` has no D file. The IncludeGraph check is gated on the debug-options config (I `IncludeGraph.java:84-91`), not dev mode. `gl_TextureMatrix[2]` is identity in D (`CoreTransformHelper.java:46`), the lightmap matrix upstream. `PORTING_GUIDE.md:6-7` cites a stale reference path outside this repo and "mod 1.11.2" (the build file says 1.11.4); fix when next edited |

## 2. Conventions

- **Provenance.** Commits that port Iris code carry `Ported-From: iris@1.11.4+mc26.1.2 <path under I>`,
  the Actinium convention of [FORK.md](FORK.md) adapted (the reference has no git history, so version and
  path). `git log --grep=Ported-From` keeps working.
- **Text.** Copy the upstream method with its comments; every Demonica change gets a `// Demonica:` comment
  saying why (PORTING_GUIDE rule 4). Ported files keep their LGPL-3.0 headers; `THIRD_PARTY_NOTICES.md`
  already lists Iris.
- **API translation.** The `cleanroom` MCP tool's `find_equivalent` (`from: "modern-minecraft"`) first,
  then `search_mappings`. Two things it cannot know: Iris's `RenderSystem`/`GlStateManager` map to GLSM
  (`G/GLStateManager`, `G/RenderSystem`), and `Identifier` is `ResourceLocation`. Others met in this plan:
  `Minecraft.getInstance()` is `getMinecraft()`, `level` is `world`, `getCameraEntity()` is
  `getRenderViewEntity()`.
- **Verification tiers.** Items name them by letter.
  - **V-build**: `./gradlew build` (unit tests plus the committed mini-corpus replay).
  - **V-transform**: the PORTING_GUIDE "Verifying a port" list in full: mini-corpus case first with
    `out.douira.*` recorded before the port, `accepted.txt` entry per changed case and stage, replay of
    `run/transform-corpus`, `run/transform-corpus-dh` and `run/transform-corpus-s7-taumc` with
    `-PglslReplayThreads=8`, `glslangValidator` per stage and linked, a unit test next to the class.
  - **V-game**: BSL, Complementary Reimagined and I Like Vanilla in the dev client, and the screenshot sweep
    (`scripts/glsl-corpus/sweep.txt`) against the last pre-release sweep.
  - **V-dh**: a Distant Horizons run per `docs/compat/`, with the `run/transform-corpus-dh` replay.
  - **V-prod**: the Prism `prod-smoke-test` instance, as for releases.
- **Probe pack.** Several items need a uniform shown in isolation. Use a throwaway
  `run/client/shaderpacks/_demonica_probe/` (not committed) whose `gbuffers_textured` writes the probed value
  to colortex0 and whose `final` shows it.

## 3. Phase table

Effort: S an afternoon, M a few days. Lanes are independent branches; within a lane, items land in order.

| # | Item | Effort | Depends on | Lane |
|---|---|---|---|---|
| 0.1 | `FADE_VARIABLE` and `TEXTURE_FILTERING` flags, `mc_chunkFade` const, filtering stubs, `breaksAnisotropy` | S | none | A |
| 0.2 | End-flash stubs: `endFlashShadows`, three uniforms, `supportsEndFlash()` | S | none | A |
| 0.3 | `OptionAnnotatedSource` const-option names | S | none | A |
| 0.4 | Cheap uniforms: `seaLevel`, armor, `isRightHanded`, `heavyFog`, `isRiding`, `isElytraFlying`, `logicalHeightLimit`, `constantMood`, `currentColorSpace` | S | none | A |
| 0.5 | `IrisTimeUniforms` | S | none | A |
| 1.1 | GLSM `iris_*Inverse` upload slots (bug fix) | S | none | B |
| 1.2 | `iris_Default*`, `iris_Shadow*` internal matrices | S-M | 1.1 | B |
| 1.3 | Player, vehicle and look uniforms | S | none | A |
| 1.4 | `currentSelectedBlockId`, `currentSelectedBlockPos` | S | none | A |
| 1.5 | `cloudTime`, `textureReloadCount` | S | none | A |
| 1.6 | `FeatureMissingErrorScreen` | S | 0.1 | A |
| 2.1 | `ProgramSet` reads every `ProgramId` | S | none | C |
| 2.2 | Celeritas terrain pass split (solid/cutout, shadow solid/cutout) | M | 2.1 | C |
| 2.3 | `Particles`, `ParticlesTrans` | S-M | 2.1 | C |
| 2.4 | `Lightning`, `ShadowEntities`, `ShadowLightning`, `ShadowBlock` | M | 2.1 | C |
| 2.5 | `skipAllRendering` | S | none | C |
| 3.1 | `replaceGlMultiTexCoordBounded` in every patch kind | S | none | D |
| 3.2 | `gtexture` two-declaration merge | S-M | none | D |
| 3.3 | DH transformer deltas | M | none | D |
| 3.4 | `gl_TextureMatrix[2]` | S | 3.1 | D |
| 4.1 | `IrisShadowRenderCallback` API | S | 2.4 (same file) | E |
| 4.2 | `ShaderCompileException` | S | none | E |
| 4.3 | IncludeGraph "did you mean" | S | none | E |
| 4.4 | `BuiltShaderStorageInfo` | S | none | E, optional |

## 4. Phase 0: quick wins and warning silencers (lane A)

### 0.1 Feature flags `FADE_VARIABLE`, `TEXTURE_FILTERING`

- **Goal.** A pack that requires either flag loads instead of turning shaders off
  (`D/shaderpack/ShaderPack.java:248-255`); a pack gated on `IRIS_FEATURE_FADE_VARIABLE` compiles.
- **Files.** `D/features/FeatureFlags.java` (two entries from I `features/FeatureFlags.java:23-24`, both
  software-true). `D/pipeline/transform/transformer/AttributeTransformer.java` and `CeleritasTransformer.java`:
  vertex stage injects `const float mc_chunkFade = -1.0;` unless the pack declares it (upstream
  `VanillaTransformer.java:30`, `SodiumTransformer.java:124`; use `ShaderTransformer.addIfNotExists`). This
  lands in the same commit as the flag, because D defines `IRIS_FEATURE_FADE_VARIABLE` for every usable flag
  (§1 row 17) and a pack would then reference `mc_chunkFade`. `D/uniforms/IrisExclusiveUniforms.java`:
  `textureFilteringMode` = 0, `chunkFadeTimeInv` = 0 (I `:46-51`). `D/uniforms/CommonUniforms.java`:
  `anisotropicFiltering` = 0 (I `:166-172`). `D/shaderpack/ShaderProperties.java` and `PackDirectives.java`:
  parse `breaksAnisotropy` (I `ShaderProperties.java:212`, `PackDirectives.java:42,85,207`); no consumer, D
  has no sampler anisotropy path.
- **1.12.2.** No anisotropic or texture-filtering setting exists, so 0 is the truthful constant.
- **Decide during the item.** Whether to add upstream's throw for SSBO/custom images used without the flag
  (I `ShaderPack.java:216-222`): only if BSL, Complementary and I Like Vanilla all declare what they use;
  otherwise log and continue, with a `// Demonica:` note.
- **Verify.** V-build. V-transform for the two transformer changes (new mini-corpus cases
  `attributes-chunkfade`, `celeritas-terrain-chunkfade`). Probe pack with
  `iris.features.required = FADE_VARIABLE TEXTURE_FILTERING` and an `#ifdef IRIS_FEATURE_FADE_VARIABLE`
  branch reading `mc_chunkFade`. V-game.

### 0.2 End-flash stubs

- **Goal.** `endFlashShadows` is accepted; `endFlashIntensity` and `previousEndFlashIntensity` read 0,
  `endFlashPosition` reads `vec3(0)`; Complementary's custom uniforms stop failing.
- **Files.** `D/shaderpack/ShaderProperties.java` (`handleBooleanDirective` for `endFlashShadows`, I `:219`),
  `D/shaderpack/PackDirectives.java` (`supportsEndFlash`, I `:47,90,112,352`), `D/pipeline/WorldRenderingPipeline.java`
  and its implementations (`supportsEndFlash()` returning the directive, `false` in the fixed-function
  pipeline; I `IrisRenderingPipeline.java:1320`), `D/uniforms/IrisExclusiveUniforms.java` (two `uniform1f`
  PER_TICK returning 0, I `:68-69`), `D/uniforms/CelestialUniforms.java` (`uniformTruncated3f` PER_FRAME
  returning zero, I `:84`). Not ported: `EndFlashStorage`, the `ShadowMatrices` sun-angle swap, the
  `EndFlashState` mixins.
- **Verify.** V-build. Complementary log free of `Unknown variable: endFlashIntensity`. V-game.

### 0.3 `OptionAnnotatedSource` const-option names

- **Goal.** `voxelDistance` and every `shadowcolor0-7{Mipmap,Nearest,MinMagNearest}` in both spellings, plus
  `shadowHardwareFiltering0-7`, are editable const options.
- **Files.** `D/shaderpack/option/OptionAnnotatedSource.java:91-128`: replace the literal list with upstream's
  static block (I `OptionAnnotatedSource.java:55-98`), looping over `PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS_IRIS`
  (`D/shaderpack/PackShadowDirectives.java:12`).
- **Verify.** V-build. Unit test asserting `shadowcolor5MinMagNearest` and `voxelDistance` are in the set.
  A pack declaring `const float voxelDistance` shows it in the option screen.

### 0.4 Cheap uniforms

- **Goal.** Nine uniforms with one-line 1.12.2 suppliers.
- **Files.** `D/uniforms/IrisExclusiveUniforms.java` (registrations from I `:54-78`, suppliers I `:104-181,236-242`).
  `D/uniforms/CommonUniforms.java` gets `isRightHanded` as upstream has it (I `:146`); remove the hardcoded
  one at `D/uniforms/IrisExclusiveUniforms.java:39`. `D/uniforms/ExternallyManagedUniforms.java:20`: delete
  the dead `heavyFog` line; it becomes a real `uniform1b`.
- **1.12.2.** `seaLevel`: `world.getSeaLevel()`. `currentPlayerArmor`: `player.getTotalArmorValue() / 50f`;
  `maxPlayerArmor`: 50. `isRightHanded`: `gameSettings.mainHand == EnumHandSide.RIGHT`. `heavyFog`:
  `mc.ingameGUI.getBossOverlay().shouldCreateFog()` (SRG `func_184056_f`; only the dragon fight sets it, see §11).
  `isRiding`: `player.isRiding()`. `isElytraFlying`: `player.isElytraFlying()`. `logicalHeightLimit`:
  `world.provider.getActualHeight()` (Forge; 128 in the Nether, else 256), registered in `WorldInfoUniforms` beside
  `heightLimit` as upstream (I `:305`); `getHeight()` is always 256. `constantMood`: 0 (no mood mechanic). `currentColorSpace`: 0 (no colour-space
  pathway). Keep the survival-only gate pattern of `IrisExclusiveUniforms.java:51` where upstream gates.
- **Verify.** V-build. Probe pack: `heavyFog` true during the End dragon fight; `isRightHanded` follows the Main Hand
  option. V-game.

### 0.5 `IrisTimeUniforms`

- **Goal.** `currentDate` (`ivec3` y/m/d), `currentTime` (`ivec3` h/m/s), `currentYearTime` (`ivec2` seconds
  elapsed, seconds remaining), all PER_TICK.
- **Files.** New `D/uniforms/IrisTimeUniforms.java`, copied whole from I `uniforms/IrisTimeUniforms.java`
  (`D/gl/uniform/UniformHolder.java:31,35` already has `uniform2i`/`uniform3i` over JOML suppliers).
  `D/uniforms/CommonUniforms.java`: call `IrisTimeUniforms.addTimeUniforms` after the exclusive uniforms
  (I `:132`). `D/pipeline/DeferredWorldRenderingPipeline.java`: `IrisTimeUniforms.updateTime()` at the top of
  `beginLevelRendering` (upstream updates at `MixinLevelRenderer.java:125`, the head of `renderLevel`; D's
  host hook `H/EntityRendererIrisMixin.java:138` already calls `beginLevelRendering` once per frame).
- **Verify.** V-build. Probe pack shows `currentTime` as a bar. V-game.

## 5. Phase 1: uniforms and matrices (lanes A and B)

### 1.1 GLSM upload slots for `iris_ModelViewMatrixInverse` and `iris_ProjectionMatrixInverse` (lane B, bug fix)

- **Goal.** ATTRIBUTES and COMPOSITE programs that use `gl_ModelViewMatrixInverse` or
  `gl_ProjectionMatrixInverse` read real values (§1 row 12).
- **Files.** `G/CompatUniformManager.java`: add `LOC_IRIS_MODELVIEW_INVERSE` and `LOC_IRIS_PROJECTION_INVERSE`
  (renumber `LOC_LIGHT_BASE`; `LOC_COUNT` derives), names in the static block (`:87-96`), uploads next to
  the `actinium_*Inverse` branches (`:243-248`, `:275-280`, sharing `scratchMatrix`). No Iris-side change:
  `D/gl/program/Program` goes through `GLStateManager.glUseProgram`, which calls `onUseProgram`, and
  `D/uniforms/ExternallyManagedUniforms.java:13,15` already declares both names. Extend
  `src/test/java/com/gtnewhorizons/angelica/glsm/CompatUniformManagerTest.java` for the new locations.
- **Adaptation.** Upstream's Shadow/Default split exists because Sodium bakes terrain uniforms; GLSM uploads
  the current stack at draw time, so one name serves both passes.
- **Open check.** Confirm GLSM's stack holds the shadow matrices while `ShadowRenderer` draws entities; if not,
  upload the inverses from `ShadowRenderer` the way the Celeritas seam does.
- **Verify.** V-build. Probe pack: `gbuffers_textured` writes `(gl_ModelViewMatrixInverse * vec4(0,0,0,1)).xyz`;
  it must be non-zero and follow the camera; sample it from `shadow.vsh` too (expect the shadow camera).
  V-game.

### 1.2 `iris_Default*Inverse`, `iris_DefaultNormalMat`, `iris_Shadow*Inverse` (lane B)

- **Goal.** The five internal matrices packs read directly (I `uniforms/IrisInternalUniforms.java:57-82`).
- **Files.** New `D/gl/uniform/Matrix3Uniform.java` (from I `gl/uniform/Matrix3Uniform.java`, GLSM upload
  call); `D/gl/uniform/UniformHolder.java` and its implementations gain `uniformMatrix3` (I `UniformHolder.java:46`).
  `D/uniforms/IrisInternalUniforms.java`: port `addOtherUniforms`; `D/uniforms/CommonUniforms.java` calls it
  (I `:136`).
- **Adaptation.** D has no `CapturedRenderingState.getGbufferModelView()`; use `RenderingState.INSTANCE`
  as `D/uniforms/MatrixUniforms.java` does. Shadow matrices: `ShadowRenderer.createShadowModelView` and
  `ShadowMatrices.createOrthoMatrix` (`D/shadow/ShadowMatrices.java`), near/far from the shadow directives
  with upstream's `-1` rule (I `IrisInternalUniforms.java:72-73,79-80`).
- **Verify.** V-build. Probe pack: `iris_DefaultModelViewMatrixInverse` minus `gbufferModelViewInverse` is
  near zero. V-game.

### 1.3 Player, vehicle and look uniforms (lane A)

- **Goal.** `playerLookVector`, `playerBodyVector`, `vehicleId`, `vehicleLookVector`,
  `relativeVehiclePosition`, `vehicleInWater`, `inSwimmingAnimation`, `feetInWater`.
- **Files.** `D/uniforms/IrisExclusiveUniforms.java` (I `:54-61,82-89,104-156`), in D's allocation-free
  cache style (`:21-24`).
- **1.12.2.** Look: `getRenderViewEntity().getLook(tickDelta)`. Body: `Entity.getForward()` (SRG
  `func_189651_aD`), the same pitch-aware vector upstream uses. Vehicle: `player.getRidingEntity()`; id
  through `EntityIdHelper.getEntityId(vehicle)` (`D/uniforms/EntityIdHelper.java:52`, already handles
  registry and legacy names). `feetInWater`/`vehicleInWater`: upstream's `isInShallowWater()` is
  `isInWater() && !isUnderWater()` (eyes in water), so use `e.isInWater() && !e.isInsideOfMaterial(Material.WATER)`
  on the player and the vehicle; false when fully submerged. No swimming animation before 1.13:
  `inSwimmingAnimation` = false, marked `// Demonica:`.
  Positions relative to `CameraUniforms.getUnshiftedCameraPosition()`.
- **Verify.** V-build. Probe pack while riding a boat: id non-zero, `relativeVehiclePosition` small. V-game.

### 1.4 `currentSelectedBlockId`, `currentSelectedBlockPos` (lane A)

- **Files.** `D/uniforms/IrisExclusiveUniforms.java` (I `:76,78,183-204`).
  `src/main/resources/META-INF/demonica_at.cfg`: `public net.minecraft.client.renderer.EntityRenderer isDrawBlockOutline()Z # isDrawBlockOutline` (MCP name like the file's other entries; remapJar writes `func_175070_n()Z`).
- **1.12.2.** `mc.objectMouseOver` (`typeOfHit == BLOCK`, `getBlockPos()`); air test on the block state;
  `world.getWorldBorder().contains(pos)`; id through `BlockRenderingSettings.INSTANCE.getBlockStateId`;
  position is the block centre minus `CameraUniforms.getUnshiftedCameraPosition()`, `-256` when the gate fails.
- **Verify.** V-build. Probe pack highlights the looked-at block by id. V-game.

### 1.5 `cloudTime`, `textureReloadCount` (lane A)

- **Files.** `D/uniforms/CapturedRenderingState.java`: fields and setters (I `CapturedRenderingState.java:25,128-141`).
  `D/uniforms/IrisExclusiveUniforms.java`: `cloudTime` (I `:80`). `D/uniforms/CommonUniforms.java`:
  `textureReloadCount` on the texture notifier (I `:96`). `H/RenderGlobalIrisMixin.java`: inject at
  `renderClouds` HEAD, `setCloudTime((cloudTickCounter + partialTicks) * 0.03f)` (`cloudTickCounter` is SRG
  `field_72773_u`; upstream `MixinLevelRenderer.java:131`). New `H/TextureManagerIrisMixin.java`:
  `onResourceManagerReload` TAIL increments the count (upstream `mixin/texture/MixinTextureManager.java:27-35`);
  register it in `mixins.demonica.iris.json`.
- **Verify.** V-build. F3+T increments the count. BSL clouds unchanged in the sweep.
- **As landed (2026-10-05).** `renderClouds` is still the path that draws clouds (Celeritas 9b661b70's forge122
  module does not replace it; without a pack `renderWorldPass` calls `renderCloudsCheck`, with a pack
  `EntityRendererIrisMixin.demonica$renderLateClouds` does), but only while clouds are drawn: never with the clouds
  option off, nor under a pack with `clouds=off`, which is how packs that draw their own clouds run. A HEAD inject
  there would freeze `cloudTime` in exactly the case it serves. So it is set where upstream sets it (level render
  start): `EntityRendererIrisMixin.demonica$beginIrisWorld`, next to `setTickDelta`, reading
  `RenderGlobal.cloudTickCounter` through a new AT line, with upstream's wrap at one texture period
  (`% (256 * 400)`: 1.12.2 fancy clouds map 256 cells to one repeat). `Iris.reload()` resets the count, as upstream.

### 1.6 `FeatureMissingErrorScreen` (lane A)

- **Goal.** Replace the commented-out screen at `D/shaderpack/ShaderPack.java:249-252` with a visible one.
- **Files.** New `D/gui/screen/FeatureMissingErrorScreen.java`, modeled on
  `D/gui/screen/ShadersUnavailableScreen.java` (parent, title, wrapped message, Back); I
  `gui/FeatureMissingErrorScreen.java` is the behaviour reference only. `ShaderPack.java:248-255`: when the
  current screen is `ShaderPackScreen`, show it, then disable shaders as today. Lang keys exist at
  `src/main/resources/assets/iris/lang/en_us.lang:9-13`.
- **Verify.** V-build. Probe pack with `iris.features.required = BOGUS_FLAG` selected from the pack screen
  shows the screen and shaders turn off.

## 6. Phase 2: programs and directives (lane C)

### 2.1 `ProgramSet` reads every `ProgramId` (foundation)

- **Goal.** `shadow_solid`, `shadow_cutout`, `gbuffers_terrain_solid`, `gbuffers_terrain_cutout`,
  `gbuffers_terrain_cutout_mip`, `gbuffers_item` and the six new ids are read and resolvable through
  `D/shaderpack/ProgramFallbackResolver.java`.
- **Files.** `D/shaderpack/loading/ProgramId.java`: add `ShadowEntities`, `ShadowLightning`, `ShadowBlock`,
  `Lightning`, `Particles`, `ParticlesTrans` with upstream's fallbacks (I `shaderpack/loading/ProgramId.java:17-19,42-44`);
  keep `TerrainCutoutMip`. `D/shaderpack/ProgramSet.java:82-148`: `readProgramSource` for every unread id
  (shadow ones with `BlendModeOverride.OFF`); `:421-452`: `get()` covers every constant, drop the `default`
  so the compiler flags omissions. Deriving fields from `ProgramId.values()` as upstream's `ProgramSet` does
  is optional, only if it stays readable.
- **Verify.** V-build. Unit test over `ProgramFallbackResolver.resolve(id)` for every id against a fixture
  pack in `src/test/resources`: the file when present, the fallback chain otherwise.

### 2.2 Celeritas terrain pass split

- **Goal.** `GBUFFER_SOLID` uses `TerrainSolid`, `GBUFFER_CUTOUT` uses `TerrainCutout`, `SHADOW` uses
  `ShadowSolid`, `SHADOW_CUTOUT` uses `ShadowCutout`, each through the fallback chain (upstream
  `ShaderKey.java:33-34,77`, `SodiumPrograms.java:208-212`).
- **Files.** `D/pipeline/DeferredWorldRenderingPipeline.java:240-249,629-638`: resolve the four sources
  through the fallback resolver instead of `programs.getGbuffersTerrain()`/`getShadow()`; the terrain
  transform futures grow from four to six. `D/celeritas/CeleritasTerrainPipeline.java:80-121`: the
  constructor takes per-pass sources; the `programId` switch maps each `IrisTerrainPass` to its own id for
  blend and alpha defaults. `TerrainCutoutMip` stays folded into CUTOUT (`D/celeritas/IrisTerrainPass.java:34`).
- **Effort.** M. Program count doubles for packs that ship the files; watch `TransformPatcher` cache size and
  BSL load time.
- **Verify.** V-build. V-game on all three corpus packs, which ship none of the split files, so the sweep must
  be pixel-identical. Probe pack with a `gbuffers_terrain_cutout.fsh` that tints leaves.

### 2.3 `Particles`, `ParticlesTrans`

- **Goal.** The particle pass uses `gbuffers_particles` (fallback `TexturedLit`, today's program).
- **Files.** `D/gbuffer_overrides/matching/RenderCondition.java`: add `PARTICLES` (the shadow conditions stay
  last). `D/pipeline/DeferredWorldRenderingPipeline.java:405-431`: add the row (`null, Particles, Particles`);
  `:781`: phase `PARTICLES` maps to the new condition. `H/ParticleManagerIrisMixin.java` already sets the phase.
- **Adaptation.** 1.12.2's `ParticleManager.renderParticles` blends every layer, so upstream's opaque and
  translucent particle split has no counterpart; everything maps to `Particles`, `ParticlesTrans` is reachable
  only through fallback. `// Demonica:` note; revisit if a pack ships `gbuffers_particles_translucent` with
  different blending. (Corrected in 2.3: no id falls back to `ParticlesTrans`, so it is never drawn; §11 item 4.)
- **Verify.** V-build. Probe pack with `gbuffers_particles.fsh` tinting particles. Sweep unchanged.

### 2.4 `Lightning`, `ShadowEntities`, `ShadowLightning`, `ShadowBlock`

- **Goal.** Lightning bolts and the dragon death ray use `gbuffers_lightning`; in the shadow pass, entities
  use `shadow_entities`, block entities `shadow_block`, lightning `shadow_lightning` (upstream
  `pipeline/programs/ShaderKey.java` and its pipeline mapping).
- **Files.** `D/gbuffer_overrides/matching/SpecialCondition.java`: `LIGHTNING`. `RenderCondition.java`:
  `LIGHTNING`, `SHADOW_ENTITIES`, `SHADOW_LIGHTNING`, `SHADOW_BLOCK`. `D/pipeline/DeferredWorldRenderingPipeline.java:405-431`
  (since 2.3 the static `GBUFFER_PROGRAM_IDS`, checked by `DeferredWorldRenderingPipelineProgramTableTest`, whose
  shadow-row checks name `SHADOW`/`SHADOW_TRANSLUCENT` and need the new shadow conditions):
  four rows; `:762-767`: the shadow branch picks by phase and special condition; `:770-778`: the special
  condition maps to `LIGHTNING`; the `SHADOW || SHADOW_TRANSLUCENT` checks (`:471,489,515`) become an
  "is a shadow condition" helper. `H/RenderManagerIrisMixin.java:57-62`: when `EntityIdHelper.isLightningBolt`
  holds, wrap the draw in `GbufferPrograms.setupSpecialRenderCondition`/teardown
  (`D/layer/GbufferPrograms.java`); `H/RenderDragonIrisMixin.java` likewise around the death ray.
  `ShadowRenderer`'s entity and block-entity loops already pass through the entity mixins, so the phase is set
  during shadows; confirm with the shader regression debug log.
- **Effort.** M.
- **Verify.** V-build. `/summon lightning_bolt` with a probe `gbuffers_lightning`. A probe `shadow_entities.fsh`
  that discards removes entity shadows and leaves terrain shadows. Sweep on the corpus packs (check whether
  any ships `shadow_entities`).
- **As landed (2026-10-05).** Three corrections to the text above, from upstream's `IrisPipelines.assignToShadow`
  (§11 item 10): block entities cast with `shadow_entities`, not `shadow_block` (only the end portal and gateway take
  `shadow_block`, through a new `SpecialCondition.END_PORTAL` set in `H/TileEntityEndPortalRendererIrisMixin`); the
  death ray is `LayerEnderDragonDeath`, not `RenderDragon`, so the wrap is a new `H/LayerEnderDragonDeathIrisMixin`;
  and lightning bolts were never in the shadow pass (1.12.2 keeps them in `World.weatherEffects`), so
  `ShadowRenderer.renderEntities` now walks that list too.

### 2.5 `skipAllRendering`

- **Goal.** A pack can skip terrain and entity rendering (upstream `mixin/MixinLevelRenderer_SkipRendering.java`).
- **Files.** `D/shaderpack/ShaderProperties.java` (I `:222,860`), `D/shaderpack/PackDirectives.java`
  (I `:45,88,243`), `D/pipeline/WorldRenderingPipeline.java` and `DeferredWorldRenderingPipeline.java`
  (I `IrisRenderingPipeline.java:183,233,1375`). `H/EntityRendererIrisMixin.java`: condition the terrain and
  entity calls inside its existing `renderWorldPass` wrap and redirect sites (`:148,257`) on
  `!pipeline.skipAllRendering()` rather than adding injections.
- **Verify.** V-build. Probe pack with `skipAllRendering = true` shows sky and composites only.
- **As landed (2026-10-05).** Upstream conditions three things: `update`'s `cullTerrain` call (terrain setup),
  `renderGroup` (every chunk layer group, translucent included) and the entity list of `extractVisibleEntities`
  (lightning included); block entities are a TODO there (still drawn), and the shadow pass, particles, weather, world
  border, outline, block damage, clouds and hand are untouched. The `cullTerrain` condition has no effect while a pack
  is in use: `MixinLevelRenderer.iris$setShadows` (I `:192-197`) already drops that call whenever
  `isPackInUseQuick()`, and `iris$renderTerrainShadows` (`:199-203`) calls `cullTerrain` directly after the shadow
  pass, so upstream still sets up terrain and builds chunks (the shadow pass keeps its terrain). D: the `setupTerrain`
  wrap is unchanged and always sets up terrain (fix commit after 0ee95b23, which had skipped it and so left the shadow
  map without terrain: no chunks were ever built); the `renderBlockLayer` redirect lost its `ordinal = 2` and now covers all four layers (renamed
  `demonica$renderTerrainLayer`); a skipped translucent call still runs S8's prelude (`ShaderTerrain.beginLayer` +
  `endLayer`: solid hand, then `beginTranslucents`, i.e. the deferred passes), which upstream runs from a separate
  inject. Two departures from the text above: entities needed one new injection (`@WrapWithCondition` on both
  `renderEntities` calls, Forge passes 0 and 1), since no existing site covers them; and because 1.12.2 draws entities,
  `weatherEffects` and block entities in that one call, block entities are skipped too. `skipAllRendering()` is on the
  `WorldRenderingPipeline` interface (upstream: `IrisRenderingPipeline` only, behind `instanceof`); the host condition
  is false in a nested `renderWorldPass`. Side effects of skipping `renderEntities` that upstream does not have:
  `TileEntityRendererDispatcher.prepare` no longer runs (the shadow pass's block entities draw through the 5-argument
  `render`, which skips the distance test that reads its camera fields; a renderer that reads them itself sees the last
  frame that ran it, not surveyed), and a stale entity-outline framebuffer would still be composited by
  `renderEntityOutlineFramebuffer` if a glowing entity was outlined before the pack loaded (neither surveyed in game).
  Probe (after the fix): with the directive on, terrain, water, entity, block-entity and lightning pixels are 0, sky,
  the deferred and composite stripes draw, stats read `terrain C: 155/2896; shadow sections 163` (as without it) and
  the shadowtex0 inset shows terrain (std 26.6, same as without the directive).

## 7. Phase 3: transforms (lane D, each item a PORTING_GUIDE cycle)

### 3.1 `replaceGlMultiTexCoordBounded` as a shared helper

- **Goal.** ATTRIBUTES and CELERITAS_TERRAIN vertex shaders zero `gl_MultiTexCoord4-7`; COMPOSITE zeros 1-7
  (upstream `VanillaTransformer.java:115`, `SodiumTransformer.java:52`, `CompositeTransformer.java:51`).
- **Files.** `D/pipeline/transform/transformer/CommonTransformer.java`: a public
  `replaceGlMultiTexCoordBounded(ShaderAst, from, to)`, either lifting the private loop from
  `DHTerrainTransformer.java:107-111` or porting I `CommonTransformer.java:461-473` as idiom code inside
  `ast.build`. `AttributeTransformer.java:77` (4-7 after `patchMultiTexCoord3`), `CeleritasTransformer.java:68`
  (4-7), `CoreTransformHelper.java:57` (1-7 before the 0 rename), and the two DH private copies removed.
- **Verify.** V-transform: mini-corpus cases `attributes-multitexcoord-4-7`, `celeritas-terrain-multitexcoord-4-7`,
  `composite-multitexcoord-1-7`; pack corpora identical unless a shader referenced them (list every changed
  case in the report).
- **As landed (2026-10-05).** Branch `feat/iris-3.1-multitexcoord-bounded` (from `dev` `bc4e1c00`), commit `0b13bbde`.
  `CommonTransformer.replaceGlMultiTexCoordBounded(ShaderAst, minimum, maximum)` is the DH transformers' verb loop lifted
  (exact names `gl_MultiTexCoord<i>`, `replaceExpression` -> `vec4(0.0, 0.0, 0.0, 1.0)`), not Iris's
  `replaceReferenceExpressions` over a prefix query as idiom code: every caller runs it among other `ShaderAst` verbs
  (PORTING_GUIDE rule 3) and the verb keeps the DH output byte-identical (`dh-terrain-legacy`, `dh-generic-legacy` read
  4-7 and record identical bytes before and after); both replace only reference expressions, so a pack's own declaration
  stays, and the exact names never reach Iris's `Integer.parseInt` of a non-numeric suffix (`// Demonica:` comment).
  Call sites: `AttributeTransformer` 4-7 after `patchMultiTexCoord3`; `CeleritasTransformer` 4-7 after the 0-2
  `vertexReplacements` (upstream runs it after `patchMultiTexCoord3`, which D runs later in `ShaderTransformer.doTransform`);
  `CoreTransformHelper.injectCompositeVertexAttributes` 1-7 before the `gl_MultiTexCoord0` rename (upstream replaces 0
  with `vec4(UV0, 0.0, 1.0)` first, then 1-7; D keeps its `iris_MultiTexCoord0` input, so composite output for 0 is
  unchanged); the two DH private copies are gone. The names do not overlap, so neither order change alters output.
  Mini-corpus cases `attributes-multitexcoord-4-7`, `celeritas-terrain-multitexcoord-4-7`, `composite-multitexcoord-1-7`
  (`out.douira.*` recorded before the port; three `accepted.txt` entries, vertex only). Before: each vertex stage failed
  glslangValidator (`'gl_MultiTexCoord4'`/`'gl_MultiTexCoord1' : undeclared identifier`); after: every stage and the
  linked pair compile. A post-port record of all 48 non-error mini-corpus cases (78 stage files) differs from the
  pre-port record in exactly those three vertex files. Pack corpora (8 threads) identical: `run/transform-corpus` 424/424
  `differing=0`, `-dh` 140/140 `differing=0`, `-s7-taumc` 175/175 `differing=0`. No pack input reads
  `gl_MultiTexCoord4-7`; 18 Complementary COMPOSITE vertex inputs (9 in each of the first two corpora) read
  `gl_MultiTexCoord1`, all in `GetLightMapCoordinates()`, which they never call and the transform removes, so their
  output is unchanged. Unit test `transformer/CommonTransformerTest` (5 tests). `./gradlew build` after 3.4: 1068 tests,
  0 failures, 2 skipped; mini-corpus `cases=49 identical=27 accepted=22 failing=0`, `used=23 stale=0`. Not run: V-game.

### 3.2 `gtexture` two-declaration merge

- **Goal.** A pack declaring both `uniform sampler2D texture;` and `uniform sampler2D gcolor;` ends with one
  `gtexture` (I `CommonTransformer.java:319-349,389-436`); D's `CommonTransformer.java:101-111` renames both
  and leaves two declarations.
- **Files.** `D/.../CommonTransformer.java:101-111`: port `getGtextureRenameTargets` and the merge as
  glsl-transformer idiom code in a nested `Upstream` holder (PORTING_GUIDE rules 1-3: build lock, document
  order, after the verbs), keeping the `actinium_renamed_texture` branch that `GlslTransformUtils.replaceTexture`
  feeds.
- **Verify.** V-transform: mini-corpus case `composite-gtexture-two-decls`; a unit test beside
  `CompatibilityTransformerTest`; `glslangValidator -l` on the merged output; corpora replay with any
  `gcolor`+`texture` pack change audited.
- **As landed (2026-10-05).** Branch `feat/iris-3.2-gtexture-merge` (from `feat/iris-3.1-multitexcoord-bounded` `917e994a`),
  commit `6c51b0ec`; checked again on 2026-10-06 against the committed tree, with no fix needed. `CommonTransformer.renameGtexture(ShaderAst)`
  runs Iris's merge block from `CommonTransformer.transform`, plus `getGtextureRenameTargets`, `RenameTargetResult` and the
  `sampler` matcher. They are copied as glsl-transformer idiom code into a nested `Upstream` holder under `ast.build`
  (rules 1-3). A name counts only when a file-scope declaration of it is a `uniform` sampler, and a non-sampler file-scope
  declaration of the name turns the rename off. Every non-call use becomes `gtexture`. When both names are declared,
  `gcolor`'s declaration is the one kept, renamed `gtexture`, and `texture`'s declaration is deleted (or only its member, in
  `uniform sampler2D gcolor, texture;`). The three TauMC verb renames in `CommonTransformer.transform` are gone.
  Departures from the item's text:
  - The merge is called from `ShaderTransformer.doTransform` after the patch switch and before `TextureTransformer`, not
    from inside `CommonTransformer.transform`. Idiom code runs after the stage's verbs (rule 3), and `TextureTransformer`
    renames samplers by name, which is also Iris's order. All six patch kinds called `CommonTransformer.transform` before,
    so the set of stages it reaches is the same.
  - The unit test is a new `transformer/GtextureMergeTest` (10 tests) in the same package as `CompatibilityTransformerTest`.
  - There are three mini-corpus cases where the item named one: `composite-gtexture-two-decls`;
    `attributes-gtexture-two-decls`, which has one declaration with two members; and `composite-gtexture-gcolor-only`, which
    pins the single-declaration path and replays unchanged.

  Departures from upstream, each with a `// Demonica:` comment:
  - The texture name used is `actinium_renamed_texture`, because `GlslTransformUtils.replaceTexture` gives that name to every
    `texture` that is not a call before the parse.
  - After the merge, the TauMC `ast.rename(actinium_renamed_texture -> gtexture)` branch is kept. A `texture` that the merge
    does not take (a local variable, or a non-sampler declaration) therefore still becomes `gtexture` and does not hide the
    `texture()` builtin once the name is restored. Iris leaves such a name alone.
  - Identifiers are visited in the document order of their external declarations, not in `identifierIndex`'s HashSet
    order. In that order, the first declaration met decides (rule 2).

  Behaviour changes against the TauMC verbs, all of them upstream's:
  - A non-sampler `gcolor` keeps its name. This shows in `dh-terrain-legacy`/`dh-generic-legacy` geometry (`out vec4
    gcolor`, written and never read by the fragment stage). Their `accepted.txt` reasons were amended rather than given a
    second entry, because of the first-match rule. `ShaderTransformerTest.dhPrograms` gained a named deviation that maps
    `gtexture` back to `gcolor` in the TauMC geometry text before the compare.
  - An unused `gcolor` sampler is now renamed.
  - A function parameter named `texture` is renamed together with its uses. This affects one pack-corpus case,
    `run/transform-corpus` `vanilla/00014-COMPOSITE-8068a20f` (I Like Vanilla composite, fragment). There,
    `addReflection(..., sampler2D texture, ...)` sampled the file-scope `gtexture` instead of its argument. It is called
    with `tex`. In a composite pass, `tex` and `gtexture` are both unbound names, so they read unit 0, which is colortex0,
    the default sampler. The image is therefore expected to stay the same. This was not checked in game.

  No pack-corpus input contains the word `gcolor` (0 files across the three corpora). 313 inputs in `run/transform-corpus`
  and 117 in `-s7-taumc` declare `uniform sampler2D texture;` alone; their output is unchanged. Before/after comparison:
  `out.douira.*` was recorded before the port (`run/iris-plan/pre32`, 52 cases/95 stage files, identical to the
  post-3.4 record on the 89 files they share). A post-port record differs in exactly 4 stage files: the two two-decls
  fragments and the two DH legacy geometries.

  Verification results:
  - glslangValidator, before the port: both two-decls fragments fail (`'gtexture' : redefinition`). After the port, every
    stage and the linked pair compile for all three new cases. The DH legacy geometry stages fail before and after on a
    pre-existing error (`'glcolor' : redeclaring non-array as array`). Both versions of the changed pack case (TauMC and
    douira) compile and link.
  - `accepted.txt` has two new entries (the two merge fragments) and `vanilla/00014-COMPOSITE-8068a20f | fragment`.
  - Mini-corpus: `cases=52 identical=28 (byte-identical 1) accepted=24 failing=0`, `accepted entries in scope=25 used=25 stale=0`.
  - Pack corpora (8 threads):
    - `run/transform-corpus`: `cases=424 identical=423 accepted=1 failing=0`, `used=1 stale=0`, concurrent 387 `differing=0`.
    - `-dh`: 140/140, `differing=0`.
    - `-s7-taumc`: 175/175, `differing=0`.
  - `./gradlew build`: `BUILD SUCCESSFUL`. Then `:test --rerun`: 137 classes, 1078 tests, 0 failures, 2 skipped.

  Not run: V-game.

  Report: `run/iris-plan/3.2-gtexture-merge-report.md`.

### 3.3 DH transformer deltas

- **Goal.** DH terrain vertex emits `out vec3 iris_vBlockPos` and `flat out uvec2 iris_TexId`; fragment gets
  `dhBlockAtlas`, `dh_hasTexture()`, `dh_blockFaceUv()`, `dh_sampleTexture()` (I
  `DHTerrainTransformer.java:45-71,145-146,166-167`); DH generic fragment gets the `false`/`vec4(1.0)` stubs
  (I `DHGenericTransformer.java:45-54`).
- **Files.** `D/.../DHTerrainTransformer.java:73-105`, `D/.../DHGenericTransformer.java`,
  `D/compat/dh/IrisLodRenderProgram.java` (bind `dhBlockAtlas` to the block atlas unit, upstream
  `IrisLodRenderProgram.java:50,135,233`). PORTING_GUIDE rule 5: `uint` literals need the `u` suffix.
- **Open check.** D applies the `my` micro-offset (`:97`), upstream applies 0 (`:162`) while still computing
  it. The arbiter is DH 3.3.0's own vertex shader in its jar (`assets/lod/shaders/`, read from the Prism
  instance). Keep whichever DH does, with a `// Demonica:` note if it is D's.
- **Verify.** V-transform with `run/transform-corpus-dh` (the `dh-terrain`/`dh-generic` mini-corpus cases get
  new `out.douira.*` and `accepted.txt` entries). V-dh: Complementary with DH, LODs textured when DH supplies
  `irisExtra.z/w`, flat colour otherwise, never black.
- **As landed (2026-10-05).** Commit `f817d00d` on `feat/iris-3.3-dh-transformer-deltas` (stacked on 3.2's
  `6c51b0ec`). What landed:
  - DH_TERRAIN vertex stages declare and write `out vec3 iris_vBlockPos` and `flat out uvec2 iris_TexId`. The two
    declarations go through `addIfNotExists`, after `modelOffset`; the two assignments sit in `_vert_init` before
    `_vert_color`, as upstream has them.
  - The micro-offset is `vec3(mx, 0.0, mz)` (§11 item 1). `my` is still computed, as upstream does.
  - DH_TERRAIN fragment stages get upstream's block, text unchanged: the `iris_vBlockPos`/`iris_TexId` inputs,
    `uniform sampler2D dhBlockAtlas`, and `dh_hasTexture`, `dh_blockFaceUv` and `dh_sampleTexture`.
  - DH_GENERIC fragment stages get upstream's stand-ins: `dh_hasTexture()` returns `false`, `dh_sampleTexture()` returns
    `vec4(1.0)`.
  - `IrisLodRenderProgram` binds DH's atlas. The id comes from
    `DhApi.Delayed.renderProxy.getDhBlockRatioAtlasTextureGlId()`, which the 1.12.2 jar has, as upstream uses. It goes
    to unit 0 through GLSM's `RenderSystem.bindTextureToUnit`, after `images.update()`.
  - `StandardMacros` defines `DISTANT_HORIZONS_TEXTURES` next to `DISTANT_HORIZONS`, as upstream does (`:65-66`).

  Departures:
  - **Two fragment blocks are idiom code.** They run in `ast.build` at the end of each transformer, after its verbs
    (PORTING_GUIDE rule 3). Upstream runs them between the `gl_MultiTexCoord` and `gl_Color` steps. Both place the
    block at `BEFORE_DECLARATIONS`.
  - **Uniform set with `glUniform1i`.** Upstream's `setUniform(dhBlockAtlas, 0)` resolves to its float overload,
    which is `GL_INVALID_OPERATION` on a sampler.
  - **No log line when the atlas is missing.** Upstream prints "WHY" every pass. DH 3.3.0 sets the id only while
    `enableTexturedLods` is on (default `true`), in `GlDhMetaRenderer.runRenderPassSetup`; it binds the atlas itself
    to unit 1 with raw GL33. With the option off, `ColumnRenderSource` keeps no texture palette, so every
    texture-set id is 0 and `dh_hasTexture()` is false.
  - **`StandardMacros` was not in the item's Files.** Without the macro, BSL (`program/dh_terrain.glsl:118-124,169-171`)
    never calls the helpers. Complementary r5.9.3 and I Like Vanilla 1.4.4 call none of them.
  - **`CommonTransformer.transform(..., true)` is kept; upstream passes `false`.** The flag means different things:
    - Upstream's flag only turns off its fixed-function alpha test (`!core`). For DH that test is off anyway:
      `DHParameters` inherits `AlphaTest.ALWAYS`.
    - D's flag selects core-profile output: `gl_FragData[i]` becomes `iris_FragDatai`, plus the GLSM
      `iris_currentAlphaTest` discard. All six D callers pass `true`; the flag came with the S5 orchestrator
      (`8b7b3489`).

    So the difference is a convention, not a DH choice. A `// Demonica:` comment in both transformers says so.
  - **Unused helpers are dropped, as in Iris.** `removeUnusedFunctions` (CompatibilityTransformer.transformEach;
    upstream `CompatibilityTransformer:176-188`) removes every helper a stage does not call. A pack that calls none
    gets only the three fragment declarations (DH_TERRAIN), or nothing at all (DH_GENERIC). So, unlike this item's
    Verify text, `dh-generic`, `dh-generic-legacy` and Complementary's DH_GENERIC program replay identically and have
    no `accepted.txt` entries.

  V-transform:
  - **New cases, recorded before the port.** `dh-terrain-textured`, `dh-generic-textured` and
    `dh-terrain-textured-geometry` (`run/iris-plan/pre33`, `pre33geo`). Their fragment stages failed glslang before
    (`'dh_hasTexture' : no matching overloaded function found`). After the port, every stage and the linked program
    compile for all three.
  - **Pre/post records of the mini-corpus.** 55 cases, 102 stage files; 12 files differ.
  - **`accepted.txt`: 15 new entries plus one amended.**
    - The amended one is the `dh-terrain-multitexcoord2 | vertex` line (the matcher is first-match).
    - The DH legacy geometry stages do not change. They still fail glslang on the old `'glcolor' : redeclaring
      non-array as array`.
    - 11 of the new entries are mini-corpus stages: the vertex and fragment stages of `dh-terrain`,
      `dh-terrain-legacy` and `dh-terrain-textured`; the fragment stages of `dh-terrain-multitexcoord2` and
      `dh-generic-textured`; and the three stages of `dh-terrain-textured-geometry`. In the geometry case,
      `transformGrouped` declares `flat out uvec2 iris_TexId` and writes `uvec2(0u)`.
    - The other 4 are pack-corpus stages (next bullet).
  - **`run/transform-corpus-dh`: 2 of 3 DH cases change.** `complementary/00138-DH_TERRAIN-df6768a5` and
    `00140-DH_TERRAIN-7be0f1b1` change in vertex (outputs and offset) and fragment (three declarations).
    `00139-DH_GENERIC-ccfa5fb3` is identical. Both changed cases compile and link before and after.
  - **Mini-corpus result.** `cases=55 identical=27 (byte-identical 1) accepted=28 failing=0`,
    `accepted entries in scope=36 used=36 stale=0`.
  - **Pack corpora, 8 threads.**

    | Corpus | Result | Entries | Concurrent pass |
    |---|---|---|---|
    | `run/transform-corpus` | `cases=424 identical=423 accepted=1` (3.2's case) | `used=1 stale=0` | 387 cases, `differing=0` |
    | `-dh` | `cases=140 identical=138 accepted=2 failing=0` | `used=4 stale=0` | 118 cases, `differing=0` |
    | `-s7-taumc` | `cases=175 identical=175` | none | 170 cases, `differing=0` |
  - **Tests.**
    - Unit test `transformer/DHTransformerTest` (6 tests).
    - `ShaderTransformerTest.dhPrograms`/`dhMultiTexCoord2Alias` gained a named deviation,
      `withoutDhTextureDeltas`. It strips the 3.3 lines and puts `my` back before comparing with TauMC.
    - `./gradlew build`: `BUILD SUCCESSFUL`. `:test --rerun`: 138 classes, 1084 tests, 0 failures, 2 skipped.

  V-dh: `-PwithCompatMods`, script `run/client/scripts/dh33.txt`, with `renderDistance:4` set for the runs and restored
  afterwards. Four runs:
  - **Complementary and BSL, textured LODs on, then off** (`run/iris-plan/33-dh-{on,off}.out`). LODs draw beyond the
    vanilla distance in every frame. 0.000% of the shader frames are near-black. No compile or link error for any
    program.
  - **The probe pack, on and off.** `run/client/shaderpacks/_demonica_probe` has only `dh_terrain`: it shows
    `dh_sampleTexture()` where `dh_hasTexture()`, and magenta elsewhere. Script `dh33probe.txt`.
    - With the option on, every LOD shows DH's (untinted, grey) block textures: 0.000% magenta, at most 0.026%
      near-black.
    - With it off, every LOD is magenta (46.9%, 21.5% and 28.4% of the three frames). No black.
    - Non-LOD pixels are the same in both, within 0.4%.

  So both paths work: LODs are textured when DH supplies `irisExtra.z/w`, flat otherwise, and never black. Not
  resolvable by eye at 1200x720: the textured look in BSL. Its on/off frames differ mostly where the near foliage
  moves. Not run: V-game, V-prod.

  Report: `run/iris-plan/3.3-dh-transformer-deltas-report.md`.

### 3.4 `gl_TextureMatrix[2]`

- **Goal.** `gl_TextureMatrix[2]` becomes `iris_LightmapTextureMatrix` (upstream `glTextureMatrix2`, I
  `CommonTransformer.java:45-46`), not the identity from the catch-all (`CoreTransformHelper.java:46`).
- **Files.** `CoreTransformHelper.java:39-46`: add the `[2]` replacement before the catch-all. Fold into 3.1.
- **Verify.** Mini-corpus case; corpora replay.
- **As landed (2026-10-05).** Commit `917e994a` on `feat/iris-3.1-multitexcoord-bounded` (after 3.1's `0b13bbde`).
  `CoreTransformHelper.injectMatrixUniforms` calls `replaceExpression("gl_TextureMatrix[2]", "iris_LightmapTextureMatrix")`
  on its own line after the `[0]`/`[1]` HashMap and before the catch-all, so the HashMap order is untouched; `[3]`-`[7]`
  and a non-literal index keep the `mat4[8](...)` catch-all (whose slot 2 is still `mat4(1.0)`, reachable only through a
  non-literal index). Departures: upstream uses `glTextureMatrix2` only in `VanillaCoreTransformer` (core-profile packs),
  where `[1]` and `[2]` both become the lightmap matrix written out as a constant; D maps `[2]` to the uniform `[1]` already
  uses, whose `BuiltinReplacementUniforms` value is that constant. Upstream's compat-profile `VanillaTransformer` and
  `SodiumTransformer` leave `[2]` as the built-in, and its `CompositeTransformer` makes `[0]`-`[7]` `mat4(1.0)`; D's helper is
  shared, so ATTRIBUTES, CELERITAS_TERRAIN and COMPOSITE all get `[2]` -> `iris_LightmapTextureMatrix` (COMPOSITE already
  diverged for `[0]`/`[1]`). Mini-corpus cases `attributes-texturematrix-2` and `composite-texturematrix-2` (two, where the
  orchestrator named one: the ATTRIBUTES case is the path upstream's matcher serves, the COMPOSITE one pins the shared
  helper's composite output), recorded before the change; three `accepted.txt` entries (attributes vertex, composite
  vertex and fragment). Both compile and link before and after. A post-3.4 record of the mini-corpus differs from the
  post-3.1 record in exactly those three stage files. Pack corpora identical (no input reads `gl_TextureMatrix[2-7]`; only
  `[0]` and `[1]` occur): 424/424, 140/140, 175/175, `differing=0` each. Unit test `transformer/CoreTransformHelperTest`
  (2 tests).

## 8. Phase 4: API and robustness (lane E)

### 4.1 `IrisShadowRenderCallback`

- **Goal.** Other mods can draw into the shadow pass (API v0). Not a DH item (§1 row 18).
- **Files.** New `shader/src/main/java/net/irisshaders/iris/api/v0/IrisShadowRenderCallback.java` from
  `.reference/Iris-26.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisShadowRenderCallback.java`
  (drop the `RenderPipeline` paragraph); `IrisApi.java` gains `registerShadowRenderCallback` and bumps the
  minor API revision; `D/apiimpl/IrisApiV0Impl.java` delegates; new `D/shadows/ShadowRenderCallbacks.java`
  from I `shadows/ShadowRenderCallbacks.java`; `D/pipeline/ShadowRenderer.java` invokes it after the opaque
  terrain draw and before entities, with the shadow modelview and projection, render origin and tick delta
  (I `shadows/ShadowRenderer.java:514-518`).
- **Verify.** V-build. A dev-only callback drawing a quad into the shadow map casts a visible shadow.
- **As landed (2026-10-05).** Branch `feat/iris-4.1-shadow-render-callback` from lane C's head `b1d7d112` (2.4 + 2.5):
  `238c104a` (API, registry, call site, tests) and `5bdf178b` (probe, diagnostics companion only). `IrisShadowRenderCallback`
  is upstream's with JOML `Matrix4f` (D's `ShadowRenderer.MODELVIEW`/`PROJECTION` already are; the static instances are
  passed, as upstream); `ShadowRenderCallbacks` is upstream's verbatim (registration order, a `Throwable` from one callback
  logged with `Iris.logger` and the rest still run) plus a package-private `clear()` for its test; `IrisApiV0Impl`
  delegates. Departures: (1) GL state. Upstream only sets `TERRAIN_CUTOUT` around the callbacks, which draw through a
  `RenderPipeline` assigned to a shadow program. D's callbacks draw like vanilla code, so `ShadowRenderer.renderShadowCallbacks`
  pushes the shadow model-view onto GLSM's model-view stack and `RenderingState` (the code `setupEntityShadowState` used,
  now shared as `loadShadowModelView`/`unloadShadowModelView`, without the entity polygon offset and `RenderManager`
  position) and runs them in `TerrainPhaseScope.runCutout` (`TERRAIN_CUTOUT` -> `getShadowCondition` default ->
  `shadow`). The projection stack already holds the shadow projection from `setupGlState`. (2) Placement: after the opaque
  terrain draw and after D's viewport reset (upstream resets the viewport after the callbacks), before entities, profiler
  section `iris_shadow_callbacks`, skipped when none is registered. (3) Arguments: render origin = `Camera.getEntityPos()`
  (eye position, the point the shadow model-view and the entity pass are centred on), where upstream passes the camera
  position (they differ in third person); tick delta = `CapturedRenderingState.getTickDelta()`. (4) Javadoc: the
  `RenderPipeline` paragraph and upstream's "any render target may be passed when creating a render pass" sentence are
  gone; a Demonica paragraph documents the state (shadow framebuffer bound and full-map viewport, `shadow` program in use,
  both GLSM matrix stacks loaded, positions relative to the render origin, texture unit 0 left as terrain left it).
  (5) Revision: `getMinorApiRevision()` 1 -> 2 (upstream 4, since D lacks upstream's revisions 1-3: text vertex sink, sun
  path rotation, pipeline assignment); the javadoc says revision 2 adds `registerShadowRenderCallback`, and both new
  javadocs say `@since Demonica API v0.2 (upstream Iris: API v0.4)`. A mod that gates on `getMinorApiRevision() >= 4`
  will not find it on Demonica. Verification: unit tests `shadows/ShadowRenderCallbacksTest` (4: empty/registered, order
  and arguments, an `IllegalStateException` and a `NoSuchMethodError` do not stop later callbacks, a callback registered
  twice runs twice) and `ShadowRendererPhaseTest.runsShadowCallbacksWithCutoutPhaseAndRestoresPreviousPhase` (5/5 in the
  class); `./gradlew build` (8G after `--stop`) `BUILD SUCCESSFUL`; `:test --rerun` 138 suites, 1089 tests, 0 failures,
  0 errors, 2 skipped; mini-corpus replay unchanged (`cases=44 identical=27 accepted=17 failing=0`). Not a transform item,
  so no corpus cases or replays. Probe: harness step `shadowquad on|off` (`diagnostics/.../probe/ShadowCallbackProbe`,
  registered through `IrisApi.getInstance()`), script `run/client/scripts/41shadowquad.txt`, BSL, noon, camera straight
  down 25 blocks up, an 8x8 stone quad 8 blocks above the ground. The first call logs `draw framebuffer 149, program 125,
  viewport 0 0 2048 2048, cull false` before and after its draw (125 = the pipeline's active pass program; 149 is the shadow
  pass's framebuffer, shadowtex0 + shadowcolor0/1, not the depth-source framebuffer 13) and `GLSM model-view == modelView
  true, GLSM projection == projection true`; 238 calls in the ~2 s it was on (log 02:30:51-02:30:53); no callback error logged. Frames
  (`run/iris-plan/41-shots/`, numbers in `run/iris-plan/41-framediff.txt`): a square shadow appears on the grass under the
  quad; the box it covers (x 520-680, y 390-535 of 1200x720) has mean luminance 134.6 without a callback, 77.7 with the
  quad (78.6% of its pixels darker by >32), 134.6 with the callback registered but drawing nothing, 77.7 with the quad
  again; between the two callback-free frames 0.3% of the box (0.68% outside it, waving grass) is darker by >32. V-game
  sweeps, V-dh and V-prod not run (not named by the item).

### 4.2 `ShaderCompileException`

- **Files.** New `D/gl/shader/ShaderCompileException.java` from I `gl/shader/ShaderCompileException.java`;
  throw it at `D/gl/shader/GlShader.java:50`, `D/gl/shader/ProgramCreator.java:54`,
  `D/gl/program/ProgramBuilder.java:91` with the program name and info log; `D/Iris.java` catches it first
  and logs filename and driver message on one line. Skip `MixinChainedJsonException` (modern GUI plumbing).
- **Verify.** V-build. A deliberately broken `composite.fsh` in the probe pack yields file name and driver
  message in the log.
- **As landed (2026-10-05).** Commit `49e5a2a5` on `feat/iris-4.2-shader-compile-exception` (from 4.1's `5bdf178b`).
  `D/gl/shader/ShaderCompileException.java` is upstream's class; `GlShader.java:50` and `ProgramCreator.java:54` throw it with
  the file or program name and the driver's info log, and the wrappers upstream lets it through do so too: `ProgramBuilder.buildShader`,
  `CompositeRenderer` and `FinalPassRenderer` (program and compute), `DeferredWorldRenderingPipeline`'s shadow/gbuffer computes
  (upstream `IrisRenderingPipeline.java:558`), `ShadowCompositeRenderer` (upstream merged it into `CompositeRenderer`) and
  `DHCompat` (upstream `:37` tests the cause, as it builds `DHCompatInternal` by reflection; Demonica calls it directly).
  `Iris.createPipeline` catches it first and logs `Shader compilation failed for <file>: <driver log, lines joined with " | ">`,
  then disables shaders with the existing entry and chat line (moved into `disablePipeline`); its generic catch also looks for
  one in the cause chain, because Demonica's pass creation wraps gbuffer compile errors in "Failed to create pass for ...".
  Departures from upstream: the `(String, String)` constructor passes only the error to `super` (upstream passes
  `filename + ": " + error` and `getMessage()` prefixes the file again, `composite.fsh: composite.fsh: ...`); the class gains
  `toLogLine()` and `findIn(Throwable)` (upstream shows the log in its debug screen, not ported, nor `MixinChainedJsonException`).
  Left as they were: setup computes (`DeferredWorldRenderingPipeline`, wrapped as upstream's `:623` does; Iris finds it in the
  chain), the DH programs' link checks (`IrisGenericRenderProgram`, `IrisLodRenderProgram`: upstream's own RuntimeException),
  Celeritas's chunk-shader `GlShader`/`GlProgram` (Celeritas code), GLSM's FFP and passthrough shaders (`glsm` module, not
  pack shaders) and `TransformPatcher` (a transform failure, which upstream `:226` also wraps in this exception; not done here).
  Log text that changed: a compile failure no longer logs "Shader compilation failed, see log for details", "Failed to compile
  FRAGMENT shader for program ..." or "Shader compilation failed!" in its stack trace; the new one-liner starts with
  "Shader compilation failed", so the harness greps still match, but a grep for `Failed to compile` no longer does. Link
  failures read "Shader compilation failed for <program>" (upstream uses the same exception for both). The chat reason is
  now `composite.fsh: <driver log>` instead of "Shader compilation failed, see log for details" (it keeps the driver's trailing
  newline). V-build: `./gradlew build` BUILD SUCCESSFUL; `:test --rerun` 139 suites, 1094 tests, 0 failures, 0 errors,
  2 skipped (replay `cases=44 identical=27 accepted=17 failing=0`); `ShaderCompileExceptionTest` 5/5. Probe
  (`scripts/42compilefail.txt`, `_demonica_probe` with a `composite.fsh` using an undeclared identifier):
  `[Demonica]: Shader compilation failed for composite.fsh: 0(28) : error C1503: undefined variable "demonicaProbeUndeclared"`,
  then `Failed to create shader rendering pipeline, disabling shaders!` and chat `Failed to create the shader rendering pipeline,
  shaders disabled! Reason: composite.fsh: 0(28) : error C1503: ...`; the client rendered vanilla, ran `/say` and `/tp`, and
  exited normally. A true syntax error (missing `;`) never reaches the driver: the transformer rejects it
  (`ShaderAst$SyntaxException: line 7:0 missing ';' at '}'` inside "Shader transformation failed for 'composite'"), so no
  one-liner; that path is unchanged. The probe's composite files were deleted afterwards.

### 4.3 IncludeGraph "did you mean"

- **Files.** `D/shaderpack/include/IncludeGraph.java`: port I `shaderpack/include/IncludeGraph.java:82-91`,
  gated on `Iris.getIrisConfig().areDebugOptionsEnabled()` (`D/config/IrisConfig.java:124`) and not a zip.
- **Verify.** V-build. A case-mismatched `#include` with debug options on names the canonical path.
- **As landed (2026-10-05).** `9bb825a9` on `feat/iris-4.3-include-did-you-mean` (stacked on 4.2). With debug options on,
  a case-mismatched `#include` fails with upstream's text, `'/lib/Common.glsl' doesn't exist, did you mean 'lib/common.glsl'?`,
  through the ported I `FileIncludeException` (new `D/shaderpack/include/FileIncludeException.java`) and upstream's extra
  catch branch (the hint becomes the RusticError's second line, detail "file not found"). Departures: (1) upstream does not
  walk the tree; it compares `toAbsolutePath()` with `toFile().getCanonicalPath()` (cut after the last `shaders/`), which folds
  case only on Windows/macOS: on Linux the mismatched file just does not exist and upstream prints a bare "file not found",
  and the canonical path resolves symlinks, so a symlinked include would be flagged. Demonica resolves the include one
  segment at a time against the real directory listings (exact name first, else the first sorted name equal ignoring case;
  listings cached per graph), so the hint works on Linux and symlinks pass. (2) Upstream already has the zip gate, as an
  `isZip` flag threaded from `Iris.loadExternalShaderpack` through `ShaderPack` (`toFile()` throws on a zip FileSystem); D's
  `ShaderPack` has no such flag, so `IncludeGraph` derives it as `root.getFileSystem() != FileSystems.getDefault()` and the
  public `(root, startingPaths)` constructor is unchanged; the gate's comment says why it stays (zip entry names are
  case-sensitive on every OS). (3) `Iris.getIrisConfig()` is read null-safely (null in unit tests); a package-private
  `(root, startingPaths, boolean debugOptionsEnabled)` constructor takes the gate for tests. Verify: `./gradlew build` BUILD
  SUCCESSFUL, 1102 tests, 2 skipped (pre-existing `GlslCorpusParseSurveyTest`, `PackResourceProbeTest`), 0 failures;
  new `src/test/java/.../shaderpack/include/IncludeGraphCaseHintTest` 8/8 (file and directory mismatch, exact include,
  absent file, gate off, symlink, zip FileSystem, public constructor without Iris config). Dev client (script
  `run/client/scripts/43include.txt`, throwaway folder pack `_demonica_probe_include` with `#include "/lib/Common.glsl"` and
  `shaders/lib/common.glsl`): with `enableDebugOptions=true` the log has `error: failed to resolve #include directive` /
  `'/lib/Common.glsl' doesn't exist, did you mean 'lib/common.glsl'?`, then "Failed to load the shaderpack", the chat line
  `Failed to load shader pack "_demonica_probe_include", falling back to vanilla rendering. Check the log for details.`,
  the harness's later `/say` and a vanilla screenshot; with it false the same failure without the hint (0 "did you mean"
  lines). The probe pack and save were deleted and `config/shaders.properties` restored afterwards. No §11 open check
  concerned this item. Follow-up `dd4b8b2c` fixed the symlink test's temp-dir leak (it wrote `/tmp/shared-lib` outside the JUnit temp dir) and the `UncheckedIOException` gap in `IncludeGraph.listNames`.

### 4.4 `BuiltShaderStorageInfo` (optional)

- I `gl/buffer/BuiltShaderStorageInfo.java`, used by I `ShaderPack.java:201`. Only if a corpus pack uses
  `bufferObject.<n>` preloads; otherwise skip and record that here.
- **As landed (2026-10-06).** Skipped: no corpus pack uses the preload form. Upstream parses `bufferObject.<index> = <size> [<path>]`
  (`ShaderProperties.java:386-417`); a second token on a two-part value is a file path that `ShaderPack.java:180-205` reads
  from the pack root and wraps in `BuiltShaderStorageInfo` as initial buffer data (an error if it exceeds the size). Demonica's
  `parseBufferObject` (`ShaderProperties.java:732`) reads only `<size>` or `<size> true <scaleX> <scaleY>`, so it never sees a path.
  Complementary declares `bufferObject.0 = 50855936`, `114229248`, `202899456`, `456130560` and `810549248` (per `COLORED_LIGHTING`)
  and `bufferObject.3 = 5376`, all size-only. BSL and I Like Vanilla declare no `bufferObject` line. The `run/transform-corpus*`
  directories are domain buckets (`bsl`, `compat`, `complementary`, `vanilla`) whose `case.properties` name no pack, so no other
  `shaders.properties` exists to check. Revisit if a pack with a preload path is added to the corpus. Side fact from the check: Demonica's parser rejects
  an index above 8 (`ShaderProperties.java:736`) where upstream allows up to 12; the corpus packs use indices 0 and 3 only.

## 9. Order

- **Lane A** (0.1-0.5, 1.3-1.6): `uniforms/` and `shaderpack/` Java only. Start here; one PR for Phase 0,
  then one per item.
- **Lane B** (1.1, 1.2): GLSM plus `IrisInternalUniforms`. Independent of A; 1.2 reuses 1.1's probe.
- **Lane C** (2.1-2.5): 2.1 first; 2.2-2.4 are mutually independent but all edit the program table
  (`DeferredWorldRenderingPipeline.java:405-431`) and `RenderCondition`, so land them serially.
- **Lane D** (3.1-3.4): transform layer only; any time a transform-savvy slot opens; 3.3 needs a DH run.
- **Lane E** (4.x): last; 4.1 rebases after 2.4 (both edit `ShadowRenderer`).
- Merge order A, B, C, D, E. Release checkpoints (V-game sweep and V-prod) after C and after D.

### Merge record (2026-10-06)

Everything is on `dev`. The stacked item PRs #4-#7 (lane A), #9 (lane B) and #11-#14 (lane C) were merged into their
parent branches, not `dev`, so only #3 (Phase 0), #8 (1.1) and #10 (2.1) had landed; one PR per lane tip then carried
the rest, in the order of §9, each merged with a merge commit (no squash, so `Ported-From:` survives):

| Lane | PR | merge commit | conflicts with earlier lanes |
|---|---|---|---|
| A (1.3-1.6) | #15 | `0c13704e` | none |
| B (1.2) | #16 | `6c2f16f3` | none |
| C (2.2-2.5) | #17 | `cb7128ae` | 0.2 `supportsEndFlash` / 2.5 `skipAllRendering` in five files, both kept (`f7a44f2b`) |
| D (3.1, 3.4, 3.2, 3.3) | #18 | `f491ee3e` | `accepted.txt`: 0.1's block and lane D's blocks, both kept (`77480832`) |
| E (4.1-4.3; 4.4 skipped) | #19 | `f3c33fa4` | none |

Verification on the merged trees: `./gradlew build` and `:test --rerun` after each conflicted merge (C: 1096 tests;
D: 1119; E: 1137; 0 failures, 2 opt-in skips each); mini-corpus replay `failing=0 stale=0` throughout; after lane D the
three pack-corpus replays (`run/transform-corpus` 424/423+1, `-dh` 140/138+2, `-s7-taumc` 175/175, `differing=0`),
the same counts as before the merge. CI green on every PR. Sweep on the merged `dev` (`scripts/glsl-corpus/sweep.txt`, log `run/sweep-merged-dev.out`) against the 2026-10-05 lane C frames: every frame within the noise floor (>16/255: 0.015-0.69 % of pixels, the largest on I Like Vanilla, whose same-code floor on that frame is 0.80 %; mean luminance equal to 0.1), all three packs load, `Unknown variable: endFlashIntensity` is gone (0.2), no compile, link or GL error; the remaining warnings (`BIOME_SULFUR_CAVES`, `pi` shadowing) predate the port.
Not run: V-prod (next release) and a DH run on the merged tree.

## 10. Not planned

From the research page's §5, unchanged: the `pipeline/programs` architecture, the UBO-shaped transformers
and `*CoreTransformer`s, the colour-space pathway, octahedral normals and `IrisVertexFormats`, the entity
render-state overhaul, the `mc_chunkFade` runtime (the const fallback in 0.1 is the whole port), the
`iris_overlay` path, wholesale mixins, `UpdateChecker` and modern GUI widgets.

Removed by verification: the custom-texture flip snapshot and image clearing (present), the Intel HD 4000
workaround (present), the texture-function renames and `shadow2D` wrapping (AST already), `dhMaterialId`
(present), `patchIntegerAttribute` (not applicable, §1 row 16), a `separateEntityDraws` runtime (no pack
report yet), `voxelDistance` as a directive (present; only the option name is in 0.3).

## 11. Open checks

Resolve during the item and record the answer here.

1. DH `my` micro-offset (3.3): read DH 3.3.0's vertex shader.
   **Answer (2026-10-05, item 3.3): DH applies no y offset, so upstream's `vec3(mx, 0, mz)` was ported (written
   `0.0`).**
   - The jar is DH 3.3.0 for 1.12.2: `mcmod.info` says `version` 3.3.0, `mcversion` 1.12.2. The path is
     `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/uCdwusMi/Sa0ttGJr/`.
   - The shaders are not under `assets/lod/shaders/` as the item says. `GlDhTerrainShaderProgram` loads
     `assets/distanthorizons/shaders/terrain/gl/vert.vert` and `.../frag.frag`.
   - `vert.vert` (`#version 330 core`, header "version: 2025-12-22"), lines 72-88, computes x and z only:
     `float mx = (mirco & 1u)!=0u ? uMircoOffset : 0.0;`, `mx = (mirco & 2u)!=0u ? -mx : mx;`, the same two lines
     for `mz` (bits 16u/32u), then `vertexWorldPos.x += mx;` and `vertexWorldPos.z += mz;`.
   - The y lines are commented out: `//float my = (mirco & 4u)!=0u ? uMircoOffset : 0.0;`,
     `//my = (mirco & 8u)!=0u ? -my : my;` and `//vertexWorldPos.y += my;`.
   - D had applied `my` (`DHTerrainTransformer.java:97`). It now matches DH and upstream, with a `// Demonica:` note
     citing the DH file.
   - Same file: DH's `irisData` attribute (slot 2, bound by D as `irisExtra`) carries x the material, y the normal
     index, and zw the tile id, little-endian. `frag.frag`'s `blockFaceUv()` and tile sampling match upstream's
     `dh_blockFaceUv`/`dh_sampleTexture`.
   - The read is in `run/iris-plan/dh-shader-check.md`.
2. Shadow-pass inverse (1.1): does GLSM's stack hold the shadow matrices during `ShadowRenderer`'s entity
   draws? If not, upload from `ShadowRenderer` as the seam does.
   **Answer (2026-10-05, item 1.1): yes, so no `ShadowRenderer` change.** `setupGlState` loads the shadow projection
   into GLSM's projection stack (`RenderSystem.setupProjectionMatrix`, push + `glLoadMatrix`) before terrain and keeps
   it until `restoreGlState` after translucent terrain. `setupEntityShadowState` (entities and the player) and
   `renderTileEntities` push GLSM's modelview, `glLoadMatrix` the shadow modelview, and pop it afterwards; the entity and
   block-entity renderers' own translate/rotate then act on that stack, so the inverse uploaded is of the modelview each
   vertex uses (the vanilla `gl_ModelViewMatrixInverse` meaning). Both paths also call
   `CompatUniformManager.refreshCurrentProgramMatrices()` on push and pop. Dirty tracking: the `iris_*Inverse` uploads sit
   in the same `mvChanged`/`projChanged` branches as `iris_ModelViewMatrix`/`iris_ProjectionMatrix`; every GLSM matrix
   op (load, mult, translate, rotate, scale, ortho, frustum, pop, `setModelViewMatrix`, `setProjectionMatrix`, attrib
   pop) bumps the generation, and `ShaderManager.preDraw` calls `onUseProgram` for the bound program on every draw (FFP
   emulation is always enabled), so a matrix change without a program switch re-uploads matrix and inverse before the
   next draw. Not checked in game yet (the 1.1 probe-pack step). Side note: CELERITAS_TERRAIN programs now get the
   inverses from GLSM too, at bind and at a draw after a GLSM matrix change, exactly as they already got
   `iris_ModelViewMatrix`; the seam's own per-pass upload follows the bind, so it still wins.
3. SSBO and custom-image flag checks (0.1): do BSL, Complementary and I Like Vanilla declare the flags they
   use? If not, log instead of throw.
   **Answer (2026-10-05, item 0.1): yes, so upstream's throws are ported.** Read from the three zips in
   `run/client/shaderpacks/`: BSL 10.1.8 `shaders.properties:228` declares `iris.features.optional=CUSTOM_IMAGES
   FADE_VARIABLE` inside `#ifdef MULTICOLORED_BLOCKLIGHT`, the same block as all its `image.*` lines (`:233-273`), and
   has no `bufferObject`; Complementary Reimagined r5.9.3 `:116` declares `CUSTOM_IMAGES SSBO BLOCK_EMISSION_ATTRIBUTE
   FADE_VARIABLE ENTITY_TRANSLUCENT` unconditionally, covering its `image.*` (`:164-225`) and `bufferObject.0`/`.3`
   (`:206-224`); I Like Vanilla 1.4.4 `:267` declares `CUSTOM_IMAGES` inside `#if COLORED_LIGHTING_ENABLED`, the block of
   all its `image.*` lines (`:271-305`), and has no `bufferObject`. `Iris.loadExternalShaderpack` catches the exception
   and leaves shaders off, as upstream.
   Also found in 0.1: BSL and Complementary already list `FADE_VARIABLE`, so the flag turns their
   `IRIS_FEATURE_FADE_VARIABLE` code on. Complementary's terrain and water treat `mc_chunkFade < 1.0` as a fading chunk
   (`gbuffers_terrain.glsl:368-371`, `lib/atmospherics/fog/mainFog.glsl:35-38`), so a Celeritas terrain program gets
   `const float mc_chunkFade = 1.0;` (upstream Sodium's value for a chunk with no fade-in time), not the `-1.0` this
   item's text names; ATTRIBUTES programs get upstream's `-1.0`. Both are declared only where a stage reads the name.
4. `ParticlesTrans` (2.3): fallback-only until a pack differs.
   **Answer (2026-10-05, item 2.3): no draw uses it, and none can through the fallback chain either.** `ParticlesTrans`
   falls back to `Particles`, but no id falls back to `ParticlesTrans` (D's and upstream's `ProgramId` alike), so "reachable
   only through fallback" in 2.3's Adaptation is wrong: `gbuffers_particles_translucent` is read and attribute-transformed
   at load (2.1) and never drawn. Upstream needs a translucent-particle render pipeline to reach it
   (`IrisPipelines.java:48`, `TRANSLUCENT_PARTICLE` -> `ShaderKey.PARTICLES_TRANS`). 1.12.2 has none:
   `ParticleManager.renderParticles` sets `enableBlend` + `SRC_ALPHA, ONE_MINUS_SRC_ALPHA` and alpha func 1/255 once and
   draws every layer (0-2, each depth-mask false then true) under it; `renderLitParticles` (layer 3) lets each particle
   draw itself. So every particle draw is "translucent" by upstream's measure and none is opaque; mapping all of them to
   `Particles` keeps today's look for packs without the file (`Particles` falls back to `TexturedLit`). Of the corpus
   packs only I Like Vanilla ships `gbuffers_particles` and `gbuffers_particles_translucent` (2.1's note); compare the
   two before deciding whether its translucent one should win. Phase coverage: `H/ParticleManagerIrisMixin` injects at
   HEAD and RETURN of both `renderParticles` and `renderLitParticles`, so every caller is covered: vanilla
   `EntityRenderer.renderWorldPass` and Cleanroom's Kirino (`KirinoClientCore:349,355`) call those two methods, and
   Forge adds no other particle path. Two vanilla particles render an entity inside the phase, both in layer 3
   (`renderLitParticles`): `ParticleItemPickup` (the picked-up item) and `ParticleMobAppearance` (the elder guardian
   curse's model). `H/ParticleItemPickupIrisMixin` and `H/ParticleMobAppearanceIrisMixin` draw them with the entity
   programs through `GbufferPrograms.drawNestedEntity`, which puts the particle phase back afterwards (2.3 fixes
   80cd0d21, cb1e5f15): the pickup mixin's `endEntities()` used to leave `NONE`, so every lit particle after a pickup in
   that frame drew with `DEFAULT` (invisible until 2.3 gave `PARTICLES` its own row), and the guardian model drew with
   `gbuffers_particles` (with `textured_lit` before 2.3; upstream draws it `entityTranslucent`, so
   `ENTITIES_TRANSLUCENT`). Probe (2026-10-05, run/fix23-probe{,-before}.out): with the old pickup mixin, explosions
   spawned after a pickup drew grey (`textured_lit`) in 2 of the 18 pickup frames; with the fix every frame that shows
   them draws them magenta (`gbuffers_particles`), and the guardian draws green (`gbuffers_entities`). Mods that draw particles outside
   `ParticleManager` (their own render-world-last effects) are not in the phase and keep whatever program the current
   phase gives; not surveyed. Two behaviour notes: a textured particle draw without the lightmap (none in vanilla:
   `EntityRenderer` enables it around both calls) now gets `Particles` -> `TexturedLit` where the `DEFAULT` row gave it
   `Textured`, as upstream's lit `PARTICLES` key does; and a pack without `gbuffers_particles` now links one more program
   (the table caches passes by id, so `Particles` and `TexturedLit` build separate programs from the same source, as
   `WORLD_BORDER` and `DEFAULT` share one only because both name `TexturedLit`). Upstream alpha-tests particles at 0.1
   (`ShaderKey.PARTICLES`, `ONE_TENTH_ALPHA`); D sets no default, so vanilla's 1/255 alpha func stays, as before.
   The program table moved to the static `DeferredWorldRenderingPipeline.GBUFFER_PROGRAM_IDS` so
   `DeferredWorldRenderingPipelineProgramTableTest` can check row alignment; 2.4 adds its rows there.
   I Like Vanilla 1.4.4 (2.3 verifier): its `gbuffers_particles` and `gbuffers_particles_translucent` (world0, world1,
   world-1) both include `/program/gbuffers_particles.glsl` and differ only in a `SHADER_GBUFFERS_PARTICLES[_TRANSLUCENT]`
   define the pack never reads; `shaders.properties:244-245` turns colortex2 blending off for both. So drawing every
   particle with `Particles` changes nothing for it. If a pack ever ships a different translucent program, 1.12.2's
   nearest split is the per-particle depth layer (`fxLayers[i][0]` depth mask off, `[i][1]` on, by
   `Particle.shouldDisableDepth`).
5. `PORTING_GUIDE.md:6-7`: stale reference path and version; fix on its next edit.
6. `heavyFog` and `logicalHeightLimit` sources (0.4). **Answer (2026-10-05, 0.4 verifier):** in 1.12.2 only
   `DragonFightManager.java:60` calls `BossInfo.setCreateFog(true)`; `EntityWither` only sets `setDarkenSky(true)`. So
   `heavyFog` is true during the dragon fight only (upstream's fog flag is also dragon-only), and its probe needs the End,
   which the dev harness cannot reach yet. `logicalHeightLimit` uses Forge's `WorldProvider.getActualHeight()`
   (`:571-574`, 128 in the Nether), not `getHeight()` (`:566-569`, constant 256).
7. Nested disable in `ShaderPack`'s constructor (found by the 1.6 verifier, 2026-10-05; predates 1.6 and matches
   upstream): the required-feature check calls `setShadersEnabledAndApply(false)` from inside the constructor, so
   `Iris.reload` runs while the outer load is still building the pack. For a zip pack, `destroyEverything` closes the
   zip `FileSystem` under it (`ClosedFileSystemException` at `IdMap.readProperties`, a second "Failed to load shader
   pack" chat line); for a folder pack the outer load completes, so `Iris.getCurrentPack()` is present with
   `enableShaders=false` and the frame is fixed-function only because the inner reload cached a FixedFunction pipeline.
   Candidate fix for a later item: leave the constructor (throw) after disabling, or defer the disable until after
   construction.
8. Shadow-matrix rules and custom-uniform plumbing (1.2, 2026-10-05). **The `-1` rule in 1.2's Adaptation is not
   what landed.** D's shadow pass (`ShadowRenderer` ortho projection, `MatrixUniforms` `shadowProjection`) substitutes the
   DH/render distance for any *negative* plane and `D/compat/dh/DHCompat.getRenderDistance()` already returns blocks,
   where upstream tests `Mth.equal(plane, -1.0f)` and multiplies by 16 in both places. An inverse has to invert the matrix
   the pass actually uses, so `iris_ShadowProjectionMatrixInverse` follows D's rule; `iris_ShadowModelViewMatrixInverse`
   takes no near/far because D's `createShadowModelView` has none (fixed `SHADOW_CAMERA_OFFSET`). Like upstream, the
   projection inverse is always the ortho one, so a pack using `shadowFov` (perspective shadow pass) gets an ortho
   inverse, as `shadowProjection` already does in D. Also needed beyond the item's file list: every common uniform is an
   input of `CustomUniforms`, whose `assignTo` calls `kroppeb.stareval.function.Type.convert` on each and would have thrown
   for `mat3`; upstream's `MAT3` line and `Float3MatrixCachedUniform` were ported with it.
9. Shadow blend defaults (2.1, 2026-10-05). `ProgramSet` now reads all seven shadow programs with `BlendModeOverride.OFF`
   as their default (upstream reads every id with `ProgramId.getBlendModeOverride()`, which is `OFF` for all seven). D's
   enum still gives `Shadow`, `ShadowSolid` and `ShadowCutout` no default override (upstream: `OFF`); 2.1 left them so,
   because `CeleritasTerrainPipeline` takes the shadow passes' blend from `programId.getBlendModeOverride()` and would
   change from "no override" to `OFF`. The six new ids carry upstream's overrides. 2.2, which moves the Celeritas shadow
   passes to `ShadowSolid`/`ShadowCutout`, should decide whether to align the enum. D's `BlockTrans` is the opposite case
   (`OFF` in the enum, read with no default; upstream has neither). Also from 2.1: every id's file is now
   attribute-transformed at pipeline load (`submitAttributeTransforms` walks `ProgramId.values()`) and its fragment
   const directives are scanned in `locateDirectives` (as upstream's `ProgramSet` does); Complementary ships `gbuffers_lightning` and I Like
   Vanilla `gbuffers_particles(_translucent)`, so their loads now transform those unused programs.
   **Answer (2026-10-05, item 2.2): not aligned.** The Celeritas shadow passes now take `ShadowSolid`/`ShadowCutout`
   (`IrisTerrainPass.getProgramId()`), whose D defaults are no override, as `Shadow`'s was, so shadow terrain blending is
   unchanged. `CeleritasTerrainPipeline` is the only reader of a shadow id's enum default (the program table,
   `DeferredWorldRenderingPipeline.java:1123`, takes the directive first, which `ProgramSet` fills with `OFF`), so aligning
   the three ids would change exactly the Celeritas shadow solid and cutout passes from "leave the blend state" to
   `OFF`. Upstream gets `OFF` there by another road: `SodiumShader` takes `source.getDirectives().getBlendModeOverride()
   .orElse(null)`, i.e. `OFF` unless `blend.shadow*` says otherwise, where D's shadow terrain passes ignore the directive.
   Either change (enum or directive) is a behaviour change for its own item, not for a split that must keep output
   identical.
   Also from 2.2: upstream's Sodium terrain path (`SodiumPrograms.getAlphaTest`) alpha-tests both cutout passes at
   `HALF_ALPHA` (0.5) and `SHADOW_TRANS` not at all (`ShaderKey`, the non-Sodium path, differs: `TERRAIN_CUTOUT` is
   `HALF_ALPHA` but `SHADOW_TERRAIN_CUTOUT` and `SHADOW_TRANSLUCENT` are `ONE_TENTH_ALPHA`); D keeps Angelica's 0.1 and 0.0001
   (`CeleritasTerrainPipeline`, `// Demonica:` comment). `gbuffers_terrain_cutout_mip` is read (2.1) but no pass uses it.
   Load cost (`scripts/glsl-corpus/timing.sh bsl`, two runs each, `run/timing-bsl-douira-plan22-*.out`): BSL overworld
   624.1/765.7 ms before, 722.3/695.5 ms after; nether 224.5/222.7 before, 292.8/212.4 after (noise). 170 cache misses and
   `cacheSize=170` both sides. The transform count fell: BSL ships no `shadow_water`, so the old four futures submitted
   `shadow` twice concurrently (172 transformer timing lines, 11161 locks); one future per distinct source gives 170/11011.
10. Shadow-pass program choice and phase (2.4, 2026-10-05). **Upstream's mapping, not the item's wording.** Upstream picks
    shadow programs by render pipeline (`I/pipeline/IrisPipelines.java:85-134`): every entity and block-entity pipeline
    (entity models, items, armour, glint, eyes, beacon beams via `SHADOW_BEACON_BEAM`, sign text via `SHADOW_TEXT*`) maps
    to a `ShadowEntities` key; only `END_PORTAL` and `END_GATEWAY` map to `SHADOW_BLOCK`; `LIGHTNING`, `DRAGON_RAYS` and
    `DRAGON_RAYS_DEPTH` to `SHADOW_LIGHTNING`; particles, weather, lines, leashes and crumbling to `Shadow`. So D's
    shadow branch (`DeferredWorldRenderingPipeline.getShadowCondition`) maps the `LIGHTNING` special condition to
    `SHADOW_LIGHTNING`, a new `END_PORTAL` special condition (set only around `EndPortalRenderer.render`, so the
    gateway's beam stays on `shadow_entities` as upstream's beacon-beam key) to `SHADOW_BLOCK`, phases `ENTITIES` and
    `BLOCK_ENTITIES` to `SHADOW_ENTITIES`, translucent terrain to `SHADOW_TRANSLUCENT`, everything else to `SHADOW`.
    A chest therefore casts with `shadow_entities` (upstream) where the item text would have given it `shadow_block`.
    Not mirrored: upstream's moving-piston/`*_BLOCK` pipelines drawn inside a block entity take the terrain shadow keys;
    in D any draw in the block-entity phase takes `shadow_entities`. Alpha: upstream's `LIGHTNING` and `SHADOW_LIGHTNING`
    are `AlphaTests.OFF`, so `ProgramId.Lightning` and `ShadowLightning` default to `AlphaTestOverride.OFF` (1.12.2's
    `RenderLightningBolt` leaves vanilla's GREATER 0.1 on; `LayerEnderDragonDeath` turns it off itself).
    `SHADOW_ENTITIES_CUTOUT` and `SHADOW_BLOCK` are `ONE_TENTH_ALPHA`, which is vanilla's default state, so those ids keep
    no default (as D's `Entities`). Blend: `Lightning` keeps vanilla's `SRC_ALPHA, ONE` (no override, as upstream's
    pipeline blend); the three shadow ids keep 2.1's `BlendModeOverride.OFF`.
    **Phase during the shadow pass (read, not run):** `EntityRendererIrisMixin` runs the shadow pass at the
    `setupTerrain` call, after `renderSky` (whose RETURN inject sets `NONE`) and after `beginLevelRendering` reset the
    phase to `NONE`; `ShadowRenderer.TerrainPhaseScope` puts `NONE` back after opaque terrain. `renderEntities` and
    `renderPlayerEntity` draw through `RenderManager.renderEntityStatic` -> `renderEntity`, whose
    `RenderManagerIrisMixin` wrap calls `GbufferPrograms.enterEntityPhase()`, which begins `ENTITIES` when the phase is
    `NONE`; `renderTileEntities` calls `GbufferPrograms.beginBlockEntities()` around its loops. So both phases are set.
    The shader regression debug log (`renderEntity:before ... previousPhase=NONE began=true`) was not captured in a run.
    **Lightning in the shadow pass:** 1.12.2 adds bolts with `World.addWeatherEffect` (client: `SPacketSpawnGlobalEntity`),
    so they live in `weatherEffects`, not `loadedEntityList`, and `ShadowRenderer.renderEntities` never drew them; upstream's
    `entitiesForRendering()` includes them. `renderEntities` now adds `weatherEffects` (same frustum test), so with shadow
    entities on, every pack now draws bolts into the shadow map (with `shadow_lightning` -> `shadow_entities` -> `shadow`);
    none of the three corpus packs ships `shadow_lightning`, `shadow_entities` or `shadow_block`, so their bolts now cast
    with `shadow`. Complementary ships `gbuffers_lightning` (all three dimensions); BSL and I Like Vanilla do not, so
    their bolts and the death ray keep `gbuffers_entities` (before 2.4 they matched `ENTITIES`: `SRC_ALPHA, ONE` is not
    the translucent blend).
