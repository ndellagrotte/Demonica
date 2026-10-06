# Iris porting research

Verified 2026-09-29 against both trees; corrections and the resulting plan are in
[IRIS_PORTING_PLAN.md](IRIS_PORTING_PLAN.md) §1. Read that section before acting on §3-§4 here.

Date: 2026-09-29. Tree: `dev` at `9bac5226` (0.7.0-SNAPSHOT; the glsl-transformer
adoption shipped in 0.6.0). Upstream reference: `.reference/Iris-26.1`, Iris
**1.11.4+mc26.1.2** (Minecraft 26.1.2, Sodium 0.9.x, `MOD_VERSION 1.11.4` in its
`build.gradle.kts`). Both trees build their GLSL transforms on
`io.github.douira:glsl-transformer:3.0.0-pre3`, which is what makes most of the
gap below cheap to close.

This page surveys what upstream Iris has that Demonica's `shader/` tree (the
Iris pipeline, package `net.coderbot.iris`, from Angelica's backport via
Actinium) does not, and ranks it as porting targets. It is static analysis:
file lists, line counts, string and structural diffs, and targeted reading of
both trees. Nothing here was compiled or run. Where a claim depends on runtime
behaviour or on code not read line by line, it says so. Appendix A has the
commands.

## 1. Summary

Demonica's pipeline is Angelica's Iris backport, whose Iris-side history stops
in early 2026 (`bb52476d` "Custom images, textures, SSBO, and
ShaderPack/ShaderProperties backports", `969d2c37` "Backports tessellation
shader support, safe zone shadow culling, and shader pack rendering
directives", `bee95417` "Shadow Compute Shaders"). The reference is roughly a
year of upstream work later. Of 616 Java files on Iris's side, 232 share a
relative path with one of Demonica's 387; 384 exist only upstream, and about
185 of those are modern-platform glue (`mixin/`, `mixinterface/`,
`compat/sodium/`, the `GpuDevice`/`RenderPass` layer) that has no 1.12.2
counterpart by design. The porting surface that remains is the pack-facing
logic: transforms, uniforms, directives, feature flags, programs, shadow and
composite behaviour.

The findings, by priority:

1. **The transform layer is now the cheapest place to take upstream fixes, and
   it is where the next pack-compatibility bugs will come from.** Same
   library, same class names (`PORTING_GUIDE.md` maps them), a proven port
   workflow with a replay corpus, and S12 already demonstrated taking Iris
   26.1's `CompatibilityTransformer` whole. The remaining known deltas are
   small and listed in §3.1.
2. **Uniforms are the widest gap packs can see.** Upstream added about 30
   uniforms Demonica does not expose (§3.2), among them the real-world clock
   (`currentDate`/`currentTime`/`currentYearTime`, a self-contained port),
   player state (`currentPlayerArmor`, `maxPlayerArmor`, air/hunger already
   partly there), vehicle and look vectors, selected block, `seaLevel`,
   `cloudTime`, `textureReloadCount`, and the shadow/default inverse matrix
   uniforms.
3. **Two unknown feature flags now silently turn shaders off.** A pack that
   declares `iris.features.required = FADE_VARIABLE` or `TEXTURE_FILTERING`
   fails `FeatureFlags.isInvalid` on Demonica (the flag resolves to `UNKNOWN`)
   and `ShaderPack` responds by disabling shaders outright
   (`ShaderPack.java:245-257`, with a `// TODO: GUI` where upstream shows
   `FeatureMissingErrorScreen`). Both flags are software-true upstream.
4. **Six program ids are missing**: `gbuffers_particles`,
   `gbuffers_particles_translucent`, `gbuffers_lightning`, `shadow_entities`,
   `shadow_lightning`, `shadow_block` (§3.4). Packs shipping them fall back to
   parent programs today, silently.
5. **The end-flash feature set** (the End gateway/portal flash): directive
   `endFlashShadows`, uniforms `endFlashIntensity`,
   `previousEndFlashIntensity`, `endFlashPosition`, API
   `supportsEndFlash` (§3.5).
6. **DH terrain transforms drifted**: upstream packs `dhMaterialId`,
   `iris_TexId` (a `uvec2` through the geometry stage) and `iris_vBlockPos`
   into `DHTerrainTransformer`; Demonica's copy predates all three (§3.6).
7. **Correctness fixes worth auditing for**: the custom-texture
   buffer-flip snapshot (`flippedAtLeastOnceSnapshot` threaded through
   program creation), `ImageClearPass` for custom images, and the
   `FeatureMissingErrorScreen` UX (§3.7).
8. **Most of the 384 upstream-only files are not targets**: the
   `pipeline/programs` shader-key architecture, the UBO-based vanilla/sodium
   transformers, the color-space pathway, octahedral normals and the entity
   render-state overhaul are all modern-platform code whose job here is done
   by GLSM, the Celeritas seam and 1.12.2's own renderers (§5).

## 2. Where the trees differ in shape

Before porting anything, classify the upstream file. Most upstream-only files
fall into one of these buckets:

| Upstream (Iris 26.1) | Demonica counterpart | Porting note |
|---|---|---|
| `mixin/**` (≈150 files), `mixinterface/**` | `com.demonica.mixin.features.iris` (28 files) + access transformers | Per-target; only the *behaviour* is portable, re-implemented against 1.12.2 classes |
| `compat/sodium/**` | the Celeritas seam (`net.coderbot.iris.celeritas`, quarantine mixins) | Celeritas plays Sodium's role; upstream fixes here inform the seam, not a copy |
| `pipeline/programs/**` (`ShaderKey`, `ShaderMap`, `ExtendedShader`, …) | `gbuffer_overrides/` (`ProgramTable`, `InputAvailability`, `RenderCondition`) + `IrisCeleritasChunkProgramOverrides` | Concept parity exists; a port means adapting the idea, not the files (§5) |
| `GpuTexture`/`RenderPass`/`GlStateManager` (`com.mojang.blaze3d.*`) | GLSM (`com.gtnewhorizons.angelica.glsm`) | Upstream code touching these ports onto GLSM calls; the `cleanroom` MCP tool's equivalents rarely know GLSM (PORTING_GUIDE, "Minecraft code") |
| `targets/`, `pathways/`, `pbr/`, `shaderpack/{properties,programs,parsing,texture}`, `shadows/` | `rendertarget/`, `postprocess/` (+ `pipeline/`), `texture/pbr/`, `shaderpack/` flat, `shadow/`+`shadows/` | Package moves; content is the closest to upstream and the main porting surface |

Shared files: Demonica's tree is *bigger* on the platform-coupled side
(`BiomeUniforms` 427 vs 298, `ShaderPack` 782 vs 631, `Iris.java` 1000 vs 814)
because 1.12.2 needs more adaptation, and *smaller* on the features added
upstream since the backport. §3 is about the second kind of difference.

## 3. Tier 1 — the shader-pack surface

### 3.1 Transform-layer ports (the glsl-transformer payoff)

Demonica's `pipeline/transform/` now mirrors Iris's layout
(`PORTING_GUIDE.md`), with intentional renames: `VanillaTransformer` is
`AttributeTransformer` (patch `ATTRIBUTES`), `SodiumTransformer` is
`CeleritasTransformer` (patch `CELERITAS_TERRAIN`). The verified remaining
behaviour gaps, each a candidate for the S12-style port (copy the method,
mark adaptations with `// Demonica:`, add a mini-corpus case, replay):

| Upstream behaviour | Where upstream | Demonica today |
|---|---|---|
| Intel HD 4000 sampler workaround | `CommonTransformer.applyIntelHd4000Workaround`, called from `SodiumTransformer` | absent (grep: no match) |
| `gl_MultiTexCoord4`–`gl_MultiTexCoord7` zeroed in terrain vertex shaders | `CommonTransformer.replaceGlMultiTexCoordBounded(t, root, 4, 7)` | absent from `CeleritasTransformer`; `AttributeTransformer` renames 0–1 only |
| Integer vertex attributes with component swizzle | `CommonTransformer.patchIntegerAttribute` | absent; packs declaring `attribute ivec2/vec3`-style inputs in ATTRIBUTES programs get no conversion |
| Sampler-alias deduplication of *two* declarations (`gcolor` and `texture` both declared) | `CommonTransformer.getGtextureRenameTargets` — removes the second declaration member | `CommonTransformer.java:102-110` renames `texture`/`gcolor` to `gtexture` but does not merge two declarations; audit whether any pack hits it |
| Legacy texture-function renames as AST (`texture2D`, `texture3DLod`, `texture2DGradARB`, `texelFetch2D`, …) | `CommonTransformer.transform` tail | done as a regex pre-pass (`GlslTransformUtils.replaceTexture`), which covers *more* forms (`texture2DRect`, `texture2DArray`); converge only if a corpus case shows a behavioural difference |
| `shadow2D`/`shadow2DLod` wrapped (`renameAndWrapShadow`) | `CommonTransformer.transform` tail | handled by the regex pass (`replaceTexture` includes the shadow2D family); same note |
| `upgradeStorageQualifiers` (`attribute`→`in`, `varying`→`out`/`in`) as AST | `CommonTransformer` | regex (`CompatShaderTransformer.fixupQualifiers`); same note |
| DH terrain: `dhMaterialId = int(irisExtra.x)`, `iris_TexId` (`uvec2`), `iris_vBlockPos` | `DHTerrainTransformer` | `DHTerrainTransformer` predates them (§3.6) |
| `gl_TextureMatrix[2]` matcher | `CommonTransformer.glTextureMatrix2` | covered by the catch-all array replacement in `CoreTransformHelper` |

Two upstream bugs already found during S12 are worth reporting upstream rather
than porting: the unsigned zero for `uint` outputs (glsl-transformer's
`LiteralExpression.getDefaultValue` returns `int` 0) and declaration visiting
in identity-hash order (`transformGrouped` injection order). The S12 report
(`docs/glsl-transformer_adoption/reports/S12-payoff.md`, "Open questions") has
the details.

### 3.2 Uniforms

The largest pack-visible gap. Verified missing uniforms, with the upstream
file and 1.12.2 feasibility:

| Uniform(s) | Upstream | 1.12.2 feasibility |
|---|---|---|
| `currentDate`, `currentTime`, `currentYearTime` (`vec3i`/`vec2i`, real-world clock) | `uniforms/IrisTimeUniforms` (new file; updated from `MixinLevelRenderer`) | trivial: `LocalDateTime.now()`, no MC API |
| `currentPlayerArmor`, `maxPlayerArmor` | `IrisExclusiveUniforms` | trivial: `player.getTotalArmorValue()`, cap 20 upstream says 50 — check |
| `currentSelectedBlockId` (`int`), `currentSelectedBlockPos` (`vec3f`) | `IrisExclusiveUniforms` (via `mc.hitResult`, outline-on check) | easy: `mc.objectMouseOver`, `gameSettings.showDebugInfo`-independent outline flag is `renderGlobal.drawBlockOutline` |
| `seaLevel` | `IrisExclusiveUniforms` | trivial: `world.getSeaLevel()` |
| `playerLookVector`, `playerBodyVector` (`vec3d`) | `IrisExclusiveUniforms` | easy: `player.getLookVec()`, `player.getForward()` equivalents (`rotationYawHead` math) |
| `vehicleId`, `vehicleLookVector`, `relativeVehiclePosition`, `vehicleInWater` | `IrisExclusiveUniforms` | easy: `player.getRidingEntity()`; id through `WorldRenderingSettings.getEntityIds()` — Demonica's IdMap already builds that map |
| `isRiding`, `isElytraFlying`, `inSwimmingAnimation`, `feetInWater`, `isInShallowWater`, `vehicleInShallowWater` | `IrisExclusiveUniforms` | mostly easy (`isRiding`, `isElytraFlying`); "shallow water" is a 1.13+ concept — approximate or return `false` |
| `heavyFog` (wither boss fog) | `IrisExclusiveUniforms` (`BossHealthOverlay.shouldCreateWorldFog`) | easy: 1.12.2 `GuiBossOverlay` has the same dragon/wither fog flags |
| `cloudTime`, `chunkFadeTimeInv` | `CapturedRenderingState`/`IrisExclusiveUniforms` | `cloudTime` easy (frame counter while clouds render); `chunkFadeTimeInv` ties to `mc_chunkFade` (§5, don't port the runtime) |
| `textureReloadCount` | `CommonUniforms` (via `CapturedRenderingState`) | easy: count `RenderEngine`/texture-map reloads; needs a hook |
| `constantMood` | `CommonUniforms` | no 1.12.2 mood mechanic; return 0 |
| `textureFilteringMode`, `anisotropicFiltering` | `IrisExclusiveUniforms`/`CommonUniforms` | map to 1.12.2's Mipmap Levels setting and a constant/driver query; needs a decision |
| `endFlashIntensity`, `previousEndFlashIntensity`, `endFlashPosition` | `IrisExclusiveUniforms`, `CelestialUniforms`, `EndFlashStorage` | part of the end-flash set (§3.5) |
| `iris_currentAlphaFunc` | `IrisInternalUniforms` | easy: Demonica already injects `iris_currentAlphaTest` in transforms; the `AlphaTests`/`BlendModeFunction` enums are small cleanups |
| `iris_ShadowModelViewMatrixInverse`, `iris_ShadowProjectionMatrixInverse` | `IrisInternalUniforms` + injected by `SodiumTransformer`/`DHTerrainTransformer` (`parameters.shadow ? "Shadow" : "Default"`) | **audit**: upstream splits the inverse-matrix uniforms per pass because its terrain uniforms are baked; Demonica's `CoreTransformHelper` renames `gl_ModelViewMatrixInverse` to `iris_ModelViewMatrixInverse` in shadow programs too, which GLSM's state tracking may already fill with the shadow matrices. Verify a shadow pass before porting; if wrong, port with the matching uniforms |
| `iris_DefaultModelViewMatrixInverse`, `iris_DefaultProjectionMatrixInverse`, `iris_DefaultNormalMat` | `IrisInternalUniforms` | used by vanilla-path shaders to undo vanilla transforms; GLSM tracks the same matrices (`MatrixStack`, `RenderingState`) |
| `isRightHanded` moved Common↔IrisExclusive | `CommonUniforms` upstream | Demonica has it in `IrisExclusiveUniforms`; 1.12.2 has `gameSettings.mainHand` — parity already, no work |

`WorldTimeUniforms`, `CameraUniforms`, `ViewportUniforms`, `MatrixUniforms`,
`HardcodedCustomUniforms`, `BuiltinReplacementUniforms` are at parity or
differ only by platform API (GLSM vs modern `GlStateManager`); no port needed
beyond what Angelica already adapted. `IdMapUniforms` already covers
`entityId`/`blockEntityId`/`currentRenderedItemId`/`heldItemId`; upstream's
`CommonUniforms` copies are relocations plus a lightning fallback path.

### 3.3 Feature flags and directives

- **Flags**: add `FADE_VARIABLE` and `TEXTURE_FILTERING` to
  `features/FeatureFlags.java` as software-supported. Without them a pack
  requiring either disables shaders silently (§1 finding 3). While there, port
  upstream's `FeatureMissingErrorScreen` (upstream `gui/FeatureMissingErrorScreen.java`;
  Demonica has the screen swap commented out with `// TODO: GUI` in
  `ShaderPack.java`) so the failure is visible.
- **`skipAllRendering`** (`ShaderProperties` + `PackDirectives.skipAllRendering`
  + `IrisRenderingPipeline.skipAllRendering()` + `MixinLevelRenderer_SkipRendering`):
  a pack switch to skip the whole world render (used by "performance" modes).
  On 1.12.2 this lands in `RenderGlobalIrisMixin`/the pipeline's
  `beginLevelRendering` path. Small, self-contained.
- **`voxelDistance` as an option**: the directive and its shadow-culling use
  are already ported (`PackShadowDirectives`, `ShadowRenderer:147,350`), but
  upstream also teaches `OptionAnnotatedSource` to offer it as a const option
  (`shaderpack/option/OptionAnnotatedSource.java`, `"voxelDistance"`). Trivial.
- **`OptionAnnotatedSource` mipmap pattern generalization**: upstream matches
  any `*MinMagNearest`/`*Mipmap`/`*Nearest` suffix and `shadowcolor`/`shadowColor`
  prefixes generically; Demonica enumerates exact spellings. Ports as a small
  diff; more packs' options become editable.
- **`endFlashShadows`**: part of §3.5.
- `ShaderProperties` is otherwise at parity: upstream's copy adds only
  `breaksAnisotropy` (a directive that makes Iris drop anisotropic filtering
  on the samplers a pack mismatches, read through `WorldRenderingSettings`;
  part of the `TEXTURE_FILTERING` work, so fold it into that port) beyond the
  two directives above.

### 3.4 Programs

`ProgramId` (upstream `shaderpack/loading/ProgramId.java`) added six ids
Demonica lacks: `Particles` (`gbuffers_particles`, fallback `TexturedLit`),
`ParticlesTrans` (`gbuffers_particles_translucent`, fallback `Particles`),
`Lightning` (`gbuffers_lightning`, fallback `Entities`), and the shadow split
`ShadowEntities`/`ShadowLightning` (fallback `Shadow`) and `ShadowBlock`
(fallback `Shadow`). Demonica also carries a 1.12.2-only `TerrainCutoutMip`
(`terrain_cutout_mip`), which stays.

Porting these means: the enum entries, `ProgramSet`'s name list (upstream
rearchitected `ProgramSet` to derive from `ProgramId`; Demonica's explicit
list at `shaderpack/ProgramSet.java` just gains the names), and the host-side
program selection — particles are drawn through `ParticleManagerIrisMixin`
today (maps to `TexturedLit`), lightning through `RenderManagerIrisMixin`
(maps to `Entities`), and shadow entities/block entities/lightning through
`ShadowRenderer`'s render loops, which would each pick the new ids. The
`separateEntityDraws` and `shadowEntities`/`shadowBlockEntities` toggles
already exist on both sides.

Value: packs that ship these programs (modern OptiFine/Iris packs) get
correct blending and alpha behaviour for particles and lightning instead of
the parent program's; shadow-entity split matters for packs that fade entities
out of shadows. Effort: M (touches the seam's program table and three host
mixins).

### 3.5 The end-flash set

Upstream: `ShaderProperties` key `endFlashShadows`, `uniforms/EndFlashStorage`
(new), `endFlashIntensity`/`previousEndFlashIntensity` (PER_TICK),
`endFlashPosition` (celestial `vec3`), `CelestialUniforms` growth,
`MixinEndFlash`/`EndFlashAccess` mixins on the End gateway renderer, pipeline
method `supportsEndFlash()`, and `endFlashShadows` gating it during the shadow
pass. Demonica already owns an end-portal replacement renderer (Actinium's),
so the capture point exists. Effort: M; value depends on how many packs read
these uniforms (newer packs do, for End-portal light flashes).

### 3.6 Distant Horizons

- `DHTerrainTransformer`/`DHGenericTransformer` deltas (§3.1 table): `dhMaterialId`,
  `iris_TexId`, `iris_vBlockPos`. Packs reading `dhMaterialId` today get the
  old shape. These port directly on the shared library.
- `registerShadowRenderCallback` / `IrisShadowRenderCallback` (API v0) and
  `shadows/ShadowRenderCallbacks` exist so DH can drive shadow passes;
  Demonica's DH compat (`compat/dh`, ~1500 lines) predates them. Worth
  auditing whether Demonica's shadow-from-DH path misses the callbacks' hooks.
- Upstream's `compat/dh/mixin/Mixin*Frustum` classes shadow-cull DH LODs
  through Iris's frustums. Demonica has its own DH mixins; the behaviour to
  check is that DH chunks are culled by `AdvancedShadowCullingFrustum`, not
  only the box culler.

### 3.7 Correctness and robustness fixes

| Fix | Upstream | Note |
|---|---|---|
| Custom textures sampled with a buffer-flip snapshot | `ProgramSamplers.customTextureSamplerInterceptor(..., flippedAtLeastOnceSnapshot)`, threaded through `FinalPassRenderer`/`CompositeRenderer` program creation | A pack's `customTexture.<name>.path=colortex4` must see the texture that has ever flipped, not the current flip state; audit Demonica's `ProgramSamplers`/`CustomTextureManager` |
| `ImageClearPass` for custom images | `gl/image/ImageClearPass` | Custom `image.` declarations cleared between frames; verify Demonica's `gl/image` path clears |
| Shader compile failure diagnostics | `gl/shader/ShaderCompileException`, `MixinChainedJsonException` | Better error surfacing on pack load; small, self-contained |
| `gl/buffer/BuiltShaderStorageInfo` | `gl/buffer/` | SSBO bookkeeping tidy-up; low value unless packs use `bufferObject.` heavily |
| Include-graph "did you mean" | `shaderpack/include/IncludeGraph` (dev-mode canonical-path check) | Dev QoL; cheap |
| `UpdateChecker` | `UpdateChecker.java` | Installer-centric; skip |

## 4. Suggested order

Effort: S an afternoon, M a few days, L a project. Dependencies matter: items
in §3.2/§3.3 unlock packs; §3.1 hardens everything.

| # | Item | Value | Effort | Depends on |
|---|---|---|---|---|
| 1 | `FADE_VARIABLE`/`TEXTURE_FILTERING` flags + `FeatureMissingErrorScreen` | unblocks future packs that require them | S | none |
| 2 | `IrisTimeUniforms` clock uniforms | packs use them now | S | none |
| 3 | Shadow/default inverse matrix uniforms + transformer renames (§3.2) | shadow-terrain correctness, if the audit finds wrong values | S–M | 7 (same files) |
| 4 | DH transformer deltas (`dhMaterialId`, `iris_TexId`, `iris_vBlockPos`) | DH packs | S | 7 |
| 5 | `IntelHd4000Workaround`, `replaceGlMultiTexCoordBounded`, `patchIntegerAttribute` | old-GPU and exotic-pack fixes | S each | 7 |
| 6 | Remaining §3.2 uniforms (player/vehicle/selected block/seaLevel/heavyFog…) | wide pack surface | M | none |
| 7 | Transform-layer convergence pass: diff each transformer against upstream, port behaviour deltas per `PORTING_GUIDE` | keeps 1–5 and future ports cheap | M (recurring) | none |
| 8 | New program ids (§3.4) | particle/lightning/shadow-entity fidelity | M | seam knowledge |
| 9 | `skipAllRendering`, `voxelDistance` option, option-pattern generalization (§3.3) | pack options | S each | none |
| 10 | End-flash set (§3.5) | newer packs' End effects | M | none |
| 11 | Custom-texture flip snapshot + `ImageClearPass` audits (§3.7) | correctness | S–M | none |
| 12 | API v0 `registerShadowRenderCallback` + DH shadow audit (§3.6) | DH users | M | 8 helps |

## 5. What not to port (and why)

- **`pipeline/programs/**` (`ShaderKey`, `ShaderMap`, `ExtendedShader`,
  `PartialShader`, `SodiumPrograms`…)** — Iris's answer to modern MC's
  `RenderPipeline`/core-profile vanilla shaders. Demonica's equivalents are
  `gbuffer_overrides` (program matching by render condition) and the Celeritas
  seam. Port individual *behaviours* (alpha-test defaults, blend overrides)
  when a bug shows up, not the architecture.
- **UBO-shaped transformers** — `VanillaTransformer`'s `iris_Fog`,
  `iris_DynamicTransforms`, `iris_Globals` blocks and `SodiumTransformer`'s
  `u_Globals` mirror Sodium 0.9's uniform blocks. Celeritas supplies uniforms
  its own way; Demonica's `CoreTransformHelper` + `VersionNegotiation` is the
  equivalent. Same for `CompositeTransformer`/`*CoreTransformer`/`LayoutTransformer`
  (already listed as "not ported" in `PORTING_GUIDE.md`).
- **Color-space pathway** (`pathways/colorspace/**`, `IrisConfig.colorSpace`,
  `finalizeGameRendering`, `setIsMainBound`/`onSetAlbedoTex`) — HDR/wide-gamut
  monitor support for MC 26.x's GpuDevice. No 1.12.2 counterpart.
- **Octahedral normals** (`NormalHelper.encodeNormal*`, `packDiamondByte`) and
  `IrisVertexFormats`, `vertices/sodium/**` serializers — new vertex formats
  for Sodium 0.9 terrain/entities. Would only matter if Celeritas adopted them.
- **Entity render-state overhaul** (`layer/*RenderStateShard`,
  `entity_render_context/*`, `WrappingMultiBufferSource`, `IrisModelPart`) —
  built on 1.21.2+ entity rendering. Demonica's per-layer mixins
  (`LayerArmorBaseIrisMixin`, `RenderLivingBaseIrisMixin`, …) are the 1.12.2
  answer and exist.
- **`mc_chunkFade` runtime** (`u_SectionTimeInfo`, `FADE_VARIABLE` execution) —
  chunk fade-in animation needs per-section time data from the terrain
  renderer. Add the flag (item 1) so packs load, emit the `const float
  mc_chunkFade = -1.0;` fallback as upstream's non-sodium paths do, and only
  pursue the real thing with Celeritas support.
- **`iris_overlay`/`iris_UV1` overlay-texture path** (`EntityPatcher`'s modern
  half) — modern entities carry overlay color as a texture. 1.12.2's
  `entityColor` uniform path is already ported (`EntityPatcher.patchOverlayColor`).
- **Wholesale `mixin/**`** — every mixin targets modern classes; only the
  behaviour transfers, and then into Demonica's own mixins (e.g.
  `MixinLevelRenderer_SkipRendering` → the `RenderGlobal` hooks).
- **`UpdateChecker`, modern GUI widgets, `FileDialogUtil`** — installer- and
  widget-specific; Demonica's GUI is 1.12.2-native.

## 6. Porting mechanics

- **License**: Iris is LGPL-3.0-only. Ported files keep their headers;
  `THIRD_PARTY_NOTICES.md` already covers Iris as LGPL-3.0. Demonica's own
  changes in a ported file get `// Demonica:` comments naming why
  (PORTING_GUIDE rule 4).
- **Provenance**: FORK.md asks Actinium ports to carry
  `Ported-From: actinium@4a19c959`. Iris ports deserve the same trail; suggest
  `Ported-From: iris@1.11.4+mc26.1.2` (or the specific upstream commit when
  the reference gains git history), so `git log --grep=Ported-From` keeps
  working. The reference tree is plain files (no Iris git history), so cite
  version + file path in the commit message.
- **Transform code**: follow `docs/glsl-transformer_adoption/PORTING_GUIDE.md`
  exactly — mini-corpus case first, `out.douira.*` recorded before the port,
  `accepted.txt` entries per changed stage, pack corpora replay
  (`run/transform-corpus*`), `glslangValidator` compile of every changed
  stage, unit tests next to the class, and the screenshot sweep for behaviour
  changes.
- **Non-transform code**: upstream files import modern MC heavily; the
  `cleanroom` MCP tool's `find_equivalent(from: "modern-minecraft")` is the
  front door, with the two Demonica-specific caveats the guide records
  (Iris's `RenderSystem`/`GlStateManager` map to GLSM's, `Identifier` is
  `ResourceLocation`). Uniform work mostly needs neither: the suppliers are
  small and read player/world state that 1.12.2 exposes directly.
- **Verification beyond the corpus**: `./gradlew build` (the replay runs in
  `:test`), then in-game on the three corpus packs (BSL, Complementary, I
  Like Vanilla) plus a DH run for §3.6 items.

## Appendix A — method

```
# file-level presence
D=shader/src/main/java/net/coderbot/iris
I=.reference/Iris-26.1/common/src/main/java/net/irisshaders/iris
comm -3 <(cd $D && find . -name '*.java' | sed 's|^\./||' | sort) \
          <(cd $I && find . -name '*.java' | sed 's|^\./||' | sort)

# shared files by line-count delta (basename match for moved packages), then
# diff of directive/uniform/sampler string literals per file, e.g.
grep -oE '"[a-zA-Z_0-9]+"' $D/uniforms/IrisExclusiveUniforms.java | sort -u
grep -oE '"[a-zA-Z_0-9]+"' $I/uniforms/IrisExclusiveUniforms.java | sort -u

# structural diffs (methods present on either side)
diff <(grep -oE '(public|private|protected)[a-zA-Z <>,\[\]]* [a-zA-Z]+\(' FILE_D \
      | grep -oE ' [a-zA-Z]+\($' | sort -u) \
     <(grep -oE '(public|private|protected)[a-zA-Z <>,\[\]]* [a-zA-Z]+\(' FILE_I \
      | grep -oE ' [a-zA-Z]+\($' | sort -u)
```

Line counts: Demonica `shader/` 387 files under `net/coderbot/iris`; Iris 616
under `net/irisshaders/iris`; 232 paths shared; 155 Demonica-only; 384
Iris-only, of which 185 match `^mixin/|^mixinterface/|^compat/sodium|^compat/dh/mixin`.
Counts taken 2026-09-29 on `dev` at `9bac5226`.

## Appendix B — package map (files moved upstream)

| Iris 26.1 | Demonica | Note |
|---|---|---|
| `shaderpack/properties/*`, `shaderpack/parsing/*`, `shaderpack/programs/*`, `shaderpack/option/OrderBackedProperties` | `shaderpack/*` (flat) | `PackDirectives`, `ShaderProperties`, `ProgramSet`, … same simple names |
| `shaderpack/materialmap/WorldRenderingSettings` | `block_rendering/BlockRenderingSettings` | renamed |
| `targets/*` | `rendertarget/*` | `RenderTarget`, `RenderTargets`, `ClearPass*`, `DepthTexture`, `NativeImageBacked*` |
| `pathways/{CenterDepthSampler,FullScreenQuadRenderer,HandRenderer,HorizonRenderer}` | `postprocess/*`, `pipeline/{HandRenderer,HorizonRenderer}` | |
| `pbr/{format,loader,mipmap,texture,util}` | `texture/{format,pbr,mipmap,util}` | LabPBR et al. |
| `shadows/{ShadowMatrices,ShadowRenderer,ShadowCompositeRenderer}` | `shadow/ShadowMatrices`, `pipeline/{ShadowRenderer,ShadowCompositeRenderer}` | |
| `pipeline/{CompositePass,CompositeRenderer,FinalPassRenderer}` | `postprocess/{CompositeRenderer,FinalPassRenderer}` | |
| `gl/blending/{AlphaTests,BlendMode,BlendModeFunction}` | `gl/blending/AlphaTest*`, GLSM `BlendState` | partial |
| `uniforms/{IrisTimeUniforms,VanillaUniforms,EndFlashStorage}` | – (missing) | §3.2, §3.5 |
| `api/v0/*` (separate `common/src/api` source set) | `net.irisshaders.iris.api.v0` (in `shader/`) | §3.6 for new methods |
