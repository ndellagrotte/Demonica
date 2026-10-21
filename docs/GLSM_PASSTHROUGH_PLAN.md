# GLSM passthrough plan

Date: 2026-10-21. Tree: `dev` at `ef1b8c73` (v0.8.2-12, the 0.9 line). Companion pages:
[SCOPE_RESEARCH.md §3.2](SCOPE_RESEARCH.md) (GLSM, the redirector and the core profile; the compat-profile status of
2026-09-28), [celeritas/SPIKE.md](celeritas/SPIKE.md) (GLSM on the compatibility context), [celeritas/LEDGER.md](celeritas/LEDGER.md)
(the quarantine patches, S15 in particular), and the compat write-ups [compat/opencomputers.md](compat/opencomputers.md) and
[compat/asmodeuscore.md](compat/asmodeuscore.md) (two of the bugs this plan removes the cause of).

**Status: proposed.** Nothing below is implemented. The item reports go in `glsm_passthrough/reports/` when the work
starts, as the Iris port did in `iris_porting/reports/`.

**The goal in one paragraph.** GLSM is Angelica's state manager, inherited through Actinium: a bytecode redirector sends
every GL call in every class to `GLStateManager`, which keeps its own copy of the fixed-function state, skips calls it
believes redundant, answers queries from its copy, and draws everything through generated shaders, display-list and
client-array emulation, on both profiles. That design exists so that the game can run on a core-profile context. On the
compatibility profile, the default on Windows and Linux since 0.5.0 (SCOPE_RESEARCH §3.2, status 2026-09-28), the driver
has the real fixed-function pipeline, and the copy is only a second implementation of OpenGL that must agree with the
first and does not always. This plan makes GLSM, on the compatibility profile, a **passthrough-and-observe layer**: every
GL call reaches the driver unchanged and in order, GLSM observes what passes and tells Iris and Celeritas, and nothing
GLSM remembers is ever authoritative over the driver. Emulation stays, unchanged, for the core profile (macOS, and the
`Core` option), behind a mode switch chosen once at context creation.

Path shorthands: **G** = `glsm/src/main/java/com/gtnewhorizons/angelica/glsm`, **GSM** = `G/GLStateManager.java`
(8,275 lines), **D** = `shader/src/main/java/net/coderbot/iris`, **H** = `src/main/java/com/demonica`, **B** =
`src/main/java/com/gtnewhorizons/angelica/iris/IrisGLSMBridge.java`, **R** = `G/redirect/GLSMRedirector.java`, **T** =
`src/test/java/com/gtnewhorizons/angelica/glsm`. Line numbers are as of the tree above.

## 1. The verified picture

Everything this plan rests on was read in the tree on 2026-10-21. The facts that decide the design:

| # | Fact | Where |
|---|---|---|
| 1 | **Emulation is unconditional.** `RenderSystem` enables the FFP shader emulation on a core context and then, if it is still off, on every other context ("Enabling FFP shader emulation for streaming tessellator rendering"). The profile string is kept only for reporting; nothing else in `glsm/` branches on it | `G/RenderSystem.java:143-156` |
| 2 | **Fixed-function caps never reach the driver on either profile.** `GL_FOG`, `GL_ALPHA_TEST`, `GL_LIGHTING`, `GL_LIGHT0-7`, `GL_COLOR_MATERIAL`, `GL_NORMALIZE`, `GL_RESCALE_NORMAL`, `GL_COLOR_SUM` and every per-unit `GL_TEXTURE_2D`/`GL_TEXTURE_GEN_*` are `ffpStateOnly` stacks; the enable is stored and the GL call skipped | `G/GLContextState.java:122-136,186,210`, `G/stacks/TextureUnitBooleanStateStack.java:44`, `G/states/BooleanState.java:52-58` |
| 3 | **Every redundant-looking call is skipped from the copy.** Booleans in `BooleanState.setEnabled`; blend func, color mask, clear color, depth func and mask, active texture, 2D texture bind, framebuffer bind and program bind compare against `ctx()` before calling the backend. The framebuffer and texture-bind skips do not even honour `BYPASS_CACHE` | GSM `:1570-1577` (blend), `:1735,1755` (depth), `:1875` (color), `:2070` (active texture), `:2146-2148` (bind texture, `isCachingEnabled` only), `:6484-6501` (framebuffer, unconditional), `:5330` (program) |
| 4 | **Queries are answered from the copy.** `glIsEnabled`, `glGetBoolean`, `glGetInteger` and `glGetFloat` return tracked values for every enable cap and ~60 pnames (bindings, blend, depth, stencil, fog, matrices, viewport, current program, framebuffers); `glGetFloat(int)`, `glGetDouble`, `glGetMaterial`, `glGetLight`, `glGetTexEnv*`, `glGetTexGen*` never consult the driver at all. `glGetTexParameter*`/`glGetTexLevelParameter*` on `GL_TEXTURE_2D` read `TextureInfoCache` | GSM `:846-919`, `:953-1027`, `:1061-1139`, `:1219-1373`, `:1179-1214`, `:4796-4876`, `:6923-7115` |
| 5 | **The copy is the only authority for matrices, color, normal, texcoords and lights.** `glLoadMatrix`, `glMultMatrix`, `glTranslate`, `glColor*`, `glNormal*`, `glTexCoord*`, `glLight*`, `glMaterial*`, `glFog*`, `glTexEnv*`, `glTexGen*`, `glClipPlane` update `ctx()` and bump generation counters; the FFP program's uniforms are uploaded from them before each draw | GSM `:4255-4526`, `:1765-1935`, `:3744-3809`, `:5017-5279`, `:6817-7060`; `G/ffp/Uniforms.java:147-549` |
| 6 | **The vertex path is emulated on both profiles.** `glVertexPointer`/`glColorPointer`/`glNormalPointer`/`glTexCoordPointer` become generic attributes at fixed locations; `glEnableClientState` becomes `glEnableVertexAttribArray`; client-side arrays are streamed into `clientArraysVBO` before every draw; `glBegin`/`glEnd` go through `ImmediateModeRecorder` and the streaming drawer; `GL_QUADS` is converted to triangles with an index buffer; vanilla's `Tessellator`/`WorldVertexBufferUploader` draws are cancelled by mixins and sent to `VanillaBufferBuilderRenderer` | GSM `:3119-3285`, `:3009-3088`, `:2556-2589`, `:3083-3115`; `H/mixin/core/MixinTessellator.java`, `MixinWorldVertexBufferUploader.java`; `H/render/VanillaBufferBuilderRenderer.java:71-116` |
| 7 | **Native display lists are never used.** `glGenLists`/`glNewList`/`glEndList`/`glCallList` go to `DisplayListIDAllocator` and `DisplayListManager`, which records commands and compiles them to VBOs; replay runs every command back through `GLStateManager`. Nested `glNewList` is supported "spec-violating but needed for mod compatibility"; `glBlendEquation` in a list throws; `glClipPlane` is not executed under `GL_COMPILE_AND_EXECUTE` | GSM `:3932-3998`, `:1585`, `:5884-5887`; `G/DisplayListManager.java:114,797-1012`; `G/recording/CommandBufferExecutor.java:52-551` |
| 8 | **Mods' own GLSL is rewritten.** `glShaderSource` runs `CompatShaderTransformer` when emulation is enabled (always): `gl_ModelViewMatrix`, `gl_Fog`, `gl_LightSource`, `gl_FrontMaterial`, `gl_Vertex`, `gl_Color`, `gl_TexCoord[n]`, `gl_FragData`… become `actinium_*` names fed by `CompatUniformManager` from the copy, and the version is forced to `#version N core` | GSM `:5469-5471`, `:7161-7166`; `G/CompatShaderTransformer.java:70-82,996-1088,1198-1215`; `G/CompatUniformManager.java:78-114,236-476` |
| 9 | **Legacy functions the copy cannot model throw.** `glPixelTransfer*`, `glRasterPos*`, `glPolygonStipple`, `glAccum` and `glTexEnv(FloatBuffer, GL_TEXTURE_FILTER_CONTROL)` throw `UnsupportedOperationException` by default ("not available in GL 3.3 core profile"), on the compatibility profile too. `glDrawPixels`, `glBitmap`, `glMap*`, `glEval*`, `glRect*`, `glCopyPixels`, `glPixelZoom`, `glWindowPos*` have no mapping and reach the driver while the raster position they depend on was refused | GSM `:2523-2541`, `:6733-6741`, `:7123-7129`, `:6820-6826`; `R:1123-1135` |
| 10 | **Calls *to* vanilla `GlStateManager` and `OpenGlHelper` are redirected, so their Java never runs.** About 115 MCP+SRG pairs map `GlStateManager.x` to `GLStateManager.y`; vanilla's own field cache stays frozen at its initial values and every other mod's mixin on those classes is dead | `R:522-772`; `H/compat/ccl/GlStateTrackerSnapshot.java:10-23` (the comment that names the consequence) |
| 11 | **What Iris reads from GLSM** is small and enumerable: hook events (`TEXTURE_BIND`, `TEXTURE_UNIT_STATE`, `PROGRAM_CHANGE`, `ALPHA_STATE_CHANGE`, `BLEND_FUNC_CHANGE`, `VANILLA_BLEND_CHANGE`, `FOG_STATE_CHANGE`, `SHADER_COLOR_CHANGE`, `FOREIGN_DRAW_END`, `TEXTURE_DELETE`, `LIGHTMAP_COORDS`); the fog state, the alpha and blend state, the shader color, the bound unit-0 texture, the current program, the matrix mode; the model-view and projection matrices in three places; and `TextureInfoCache`. Its overrides are three lock-and-defer handlers plus six "vanilla layers" | `B:99-398`; `D/uniforms/CommonUniforms.java:87-111,179`, `FogUniforms.java:21-29`, `IrisInternalUniforms.java:31-59`; `D/pipeline/DeferredWorldRenderingPipeline.java:817-820,969,1578,1874`; `D/postprocess/FullScreenQuadRenderer.java:91-110`; `D/pipeline/ShadowRenderer.java:610,647,653,729`; `D/gl/blending/*Storage.java` |
| 12 | **What Celeritas needs from GLSM** is nothing of its own. Its calls (`LWJGL3Service`, 99 `GL*C` sites; `RenderGlobalMixin.renderBlockLayer`; `VintageDrawContext`) are redirected like any class and link (`GlsmRedirectLinkageTest`). The one quarantine patch caused by GLSM is **S15**, which exists because fact 10 freezes vanilla's `GlStateManager.fogState` that Celeritas's `GLStateManagerFogService` reads. Celeritas's matrices come from `ActiveRenderInfo`'s buffers, which vanilla fills with `GlStateManager.getFloat` (redirected: fact 4) | `src/test/java/com/demonica/celeritas/GlsmRedirectLinkageTest.java:48-84`; `H/mixin/celeritas/seam/GLStateManagerFogServiceMixin.java:30-73`; `celeritas/patches/S15.md`; `H/mixin/features/iris/ActiveRenderInfoIrisMixin.java:28-31` |
| 13 | **Iris's gbuffers programs are compiled core-style on both profiles.** The ATTRIBUTES transform injects `iris_ModelViewMatrix`… uniforms and `layout(location = n) in` attributes, renames `gl_Vertex`/`gl_Color`/`gl_Normal`/`gl_MultiTexCoordN`/`gl_ModelViewMatrix`…, replaces `ftransform`, and the stage minimum is GLSL 330 ("Always core profile"). GLSM then feeds `iris_ModelViewMatrix`, `iris_ProjectionMatrix`, their inverses, `iris_NormalMatrix`, `iris_TextureMatrix`, `iris_LightmapTextureMatrix` and `iris_ColorModulator` from the copy | `D/pipeline/transform/transformer/AttributeTransformer.java:22-91`, `CoreTransformHelper.java:21-55`, `D/pipeline/transform/VersionNegotiation.java:44-60`; `G/CompatUniformManager.java:87-96,236-318,356-369` |
| 14 | **A bypass flag exists and is not a passthrough.** `-Dangelica.disableGlCache` sets `BYPASS_CACHE`: the copy stops being updated and most skips are disabled, but facts 1, 2, 5, 6, 7 and 8 hold regardless, and several paths ignore the flag (fact 3). Nothing outside `glsm/` references it | GSM `:218`, `:668-671`; the Iris-consumer and Celeritas surveys found no external reference |
| 15 | **The compat code that exists because of GLSM** is 13 entries in `H/mixin/mod` and `H/compat` plus two class transformers, and falls in four causes: the frozen vanilla cache (fact 10: CodeChickenLib, Extra Utilities 2, HBM `RenderUtil`, RenderLib, StellarCore and Gnetum HUD caching), copy-versus-driver divergence (Botania ×2, VoxelMap scissor), the emulation's shader key (Lumenized/GTCEu bloom), and the core profile itself (VoxelMap alpha bits and min filter) | `H/mixin/mod/{ccl,extrautils2,hbm,renderlib,botania,voxelmap,lumenized}/*`, `H/loading/fml/transformers/{StellarCore,Gnetum}HudCachingCompatTransformer.java`; SCOPE_RESEARCH §3.2 |

Two things the research pages had wrong, for the record: SCOPE_RESEARCH §3.2 counts "GLSM: state tracking, redirector,
FFP shader generation, display-list recording, streaming, LWJGL abstraction, Iris bridge" at ~39,000 lines; today's
`glsm/` main tree is 31,917 lines (14,868 in the root package, of which GSM is 8,275; `recording/` 4,252; `ffp/` 3,333;
`states/`+`stacks/` 3,239; `redirect/` 1,223; `streaming/` 962). And the 2026-09-28 status says "0 lines became cold"
on the compatibility default; true, and the reason is fact 1, which this plan reverses.

## 2. Why the tracked model goes

The copy is not a cache in front of OpenGL; it is a second OpenGL whose state the real one has to be made to agree
with. Every bug class below is a place where the two disagreed, and every entry has already cost a fix or a compat
mixin. None of them can happen in a layer that forwards every call and answers every query from the driver.

**(a) Divergence, then a wrong skip.** The copy misses a call (a class the redirector did not rewrite, an entry point
with an LWJGL 3 name, a DSA call that bound without `glBindTexture`, a foreign renderer), then skips the next real call
because the copy already "has" that value. Evidence: `24aee44c` (Forge's `glMultMatrixf` reached the driver while draws
used GLSM's matrix stack: flat GUI block items); `e0ec3f26` (Kirino's DSA `GL_TEXTURE_2D_ARRAY` was never seen bound, so
a deferred delete rebound and overwrote the live font texture); Botania's two mixins ("under the GLSM state cache its
`glPushAttrib`/`disableTexture` pairing can leave the active texture unit disabled", `H/mixin/mod/botania/*:11-19`);
VoxelMap's scissor reroute (`H/mixin/mod/voxelmap/MixinVoxelMapGLShim.java:14-23`); the `beginForeignDraw`/`endForeignDraw`
scopes and the `FOREIGN_DRAW_END` deferral that exist only to survive renderers the copy cannot follow (GSM `:1502-1522`,
`B:296-312`); and `QuadConverter`'s unconditional EBO rebind "because external native GL code may detach it"
(`G/QuadConverter.java:111-113`).

**(b) Queries answered from the wrong place.** Fact 4. A mod that saves and restores state with `glGetInteger` gets the
copy's belief, not the driver's state; the two differ exactly when (a) has happened. Extra Utilities 2's compat
(`H/mixin/mod/extrautils2/MixinGLStateAttributes.java:118-161`) re-reads 30 pnames through GLSM for this reason. Angelica
itself keeps extending the table (upstream `3978be16` "Answer legacy GL queries from tracked state", `6dfb0312` "Answer
legacy stack and client state queries from GLSM") because each unanswered pname is a bug report on core, where the driver
cannot answer. On compat the driver can, and should.

**(c) Semantics re-implemented, and wrong.** The current color stored unclamped with a `(-1,-1,-1,-1)` dirty sentinel,
then any negative component read as the sentinel: `90e09843`, AsmodeusCore's white sky. Hook listeners' GL calls recorded
into a `GL_COMPILE_AND_EXECUTE` list: `5a22c6f2`, OpenComputers' black screens. The HUD baseline reasserting `GL_LEQUAL`
because both GLSM and the frozen vanilla cache start at `GL_LESS` while `Minecraft.init` sets `GL_LEQUAL` once:
`27dad57a`. `glBlendEquation` in a display list throws; `glClipPlane` under `GL_COMPILE_AND_EXECUTE` does not execute;
`glPolygonMode` always issues `GL_FRONT_AND_BACK` (GSM `:5707-5715`); `glTexEnv` buffer forms other than
`GL_TEXTURE_ENV_COLOR` are silently dropped (GSM `:6826`); `glLineStipple` and wide lines are a per-draw shader variant
(GSM `:5423-5439`); `FFP_LIGHT_COUNT` lights only (`G/ffp/VertexKey.java:30,152-153`); `glInterleavedArrays` is a no-op
unless the emulation is active (GSM `:3412-3415`). Each is a divergence from the specification that the driver implements for free on the
compatibility profile.

**(d) Legacy entry points refused where the driver supports them.** Fact 9: a mod that calls `glRasterPos` or
`glPixelTransfer` crashes with "not available in GL 3.3 core profile" on a 4.6 compatibility context.

**(e) The frozen vanilla `GlStateManager`.** Fact 10 is the single largest source of compat code: seven entries in fact
15, two of them class transformers that run before any mixin, plus `GLSMConfig.hudCacheOverride`/`hudCacheBlendEnabled`
and five read sites of it inside GSM that re-implement StellarCore's blend-alpha rewrite (GSM `:1416,1440,1563,1628,1871`).
Every mod on 1.12.2 is written against a live `GlStateManager`; freezing it breaks the contract the whole ecosystem
assumes.

**(f) Cost on the hot path.** Every redirected call pays a `DisplayListManager.getRecordMode()` check, a lock check
against three Iris handlers, a copy compare, a generation bump and, for ~23 of them, a hook post. Every draw packs a
vertex key and a fragment key from ~40 reads of the copy (`G/ffp/VertexKey.java:144-263`, `FragmentKey.java:109-216`),
flushes four deferred attributes and uploads dirty uniforms. `GLSMPerfDebug` exists to measure this. On the compatibility
profile the driver does all of it in its own state machine, and `glGet*` of client-side state is not a pipeline sync.

What the model is **right** about, and the plan keeps: a shader mod on 1.12.2 needs to see state changes as they happen
(texture binds, texture-unit enables, program binds, alpha and blend changes, fog) to select programs and feed uniforms,
and it needs to override alpha test, blend and masks while a pack's program is bound and give the application's value
back afterwards. Those are observation and virtualisation of a small, named subset, and §3 keeps both.

## 3. Target design

### 3.1 Principles

- **P1 Passthrough.** On the compatibility profile every GL call GLSM receives is issued to the driver, with the same
  arguments, in program order. The only exceptions are the Iris overrides of §3.5, and they are declared.
- **P2 Observe, never own.** GLSM posts events as calls pass. An event carries the call's arguments; a listener keeps what
  it needs. No value GLSM remembers is ever used to skip a call or to answer a query.
- **P3 Queries go to the driver.** `glGet*`, `glIsEnabled`, `glGetTex*` return what the driver returns. Where a
  consumer needs a value GL cannot give back (there is none in the inventory of fact 11, except texture metadata by id
  without DSA, §3.6), GLSM reads it from the driver at the point of use.
- **P4 A mirror only where measured, and verified.** A few consumers want a value without a query per draw (the bound
  unit-0 texture for `gtextureId`, the current program for the pass logic). Those live in a small `GlMirror` that is
  written from the observed calls and from resync points, and an oracle mode compares it with the driver after every
  write (§4, V-oracle). A mirror value is never used for P1 or P3.
- **P5 Emulation only on core.** Today's GLSM, whole, becomes the core-profile implementation. It is not refactored by
  this plan beyond what the split requires, so the core profile cannot regress from it.
- **P6 The driver feeds Iris's vanilla-style programs.** On compat, gbuffers and shadow programs keep the pack's
  `gl_Vertex`, `gl_Color`, `gl_Normal`, `gl_MultiTexCoordN`, `gl_ModelViewMatrix`, `gl_NormalMatrix`, `gl_Fog`, `ftransform()`
  under `#version N compatibility`; the compatibility profile supplies them from the real fixed-function state. GLSM
  uploads no matrices on compat.
- **P7 Vanilla's `GlStateManager` runs.** The redirector rewrites the GL calls *inside* vanilla's `GlStateManager` and
  `OpenGlHelper`, not the calls *to* them. Vanilla's own cache, elision and other mods' mixins on it behave as on
  1.12.2 without Demonica.

### 3.2 Shape

`GLStateManager` stays the redirector's target (the descriptors are pinned by `GLStateManagerRedirectContractTest`,
`RedirectedCallLinkageTest`, `GlsmRedirectLinkageTest`) and becomes a dispatcher over two implementations, chosen once
in `RenderSystem.initRenderer` from `GL_CONTEXT_PROFILE_MASK` (`G/RenderSystem.java:143-146`):

```
GLStateManager.glX(args)                    // the public surface, unchanged descriptors
  -> MODE == EMULATE  ? EmulatedGl.glX(args) // today's body, moved verbatim (core profile)
                      : PassthroughGl.glX(args)
PassthroughGl.glX(args):
  observe(args)        // mirror write and/or hook post, only for the ~20 entry points of §3.4
  RENDER_BACKEND.glX(args)
```

`MODE` is a `static final` set before the first redirected call (the splash thread's calls arrive after
`GLStateManager.initialize`, `H/mixin/core/startup/MixinOpenGlHelper.java:42`), so the JIT folds the branch. The
`-Ddemonica.glsm.mode=emulate|passthrough` property overrides it for A/B runs on one context; passthrough on a core
context is refused at startup with the reason.

What each side owns:

| | Core (`EmulatedGl`, today's code) | Compat (`PassthroughGl`, new) |
|---|---|---|
| State | `GLContextState`, `states/`, `stacks/`, `Feature` | `GlMirror` (§3.4), no stacks |
| Queries | from the copy (fact 4) | the driver |
| Fixed function | `ffp/` shader generation and uniforms | the driver |
| Vertex path | `clientArraysVBO`, `ImmediateModeRecorder`, `streaming/`, `QuadConverter`, `VanillaBufferBuilderRenderer` | native arrays, native `glBegin`/`glEnd`, native `GL_QUADS`; vanilla's `WorldVertexBufferUploader` runs (§7, C.1) |
| Display lists | `DisplayListManager`, `recording/` | native, segmented at hook points (§7, C.2) |
| Mods' GLSL | `CompatShaderTransformer` + `CompatUniformManager` | untouched source; builtins from the driver |
| Legacy functions | `guardUnsupportedFFP`, `FeedbackManager`, line emulation | the driver |
| Iris gbuffers programs | core-style transform, `iris_*` matrices from GLSM | compatibility-profile builtins (§8, D.1) |
| Hooks | posted from the copy's change detection | posted from the call (§3.3) |
| Iris overrides | lock-and-defer in the stacks' vanilla layers | lock-and-defer in `PassthroughGl` (§3.5) |
| Push/pop attrib | `pushState`/`popState` over the stacks | native; mirror resync after pop (§3.4) |

### 3.3 Observation: the hooks keep their names and change their trigger

Today a hook fires when the copy's value changes (`TEXTURE_BIND` on a cache miss, GSM `:2146-2167`;
`TEXTURE_UNIT_STATE` from `setTexture2DEnabled` when the value differs, `:2502-2518`; `PROGRAM_CHANGE` unless
`program == activeProgram`, `:5333`). On compat a hook fires on the **call**. The two differ only for a call that
repeats the current value, and under P7 vanilla's own `GlStateManager` already elides most of those; a raw repeat from a
mod reaches the listener, which must be idempotent. Item D.3 audits each listener for that and measures the post rate.

The event set is unchanged (`G/hooks/GLSMHooks.java:48-61`); `PROGRAM_DELETE` and `LOADING_CHECKPOINT` have no listener
and go. `LIGHTMAP_COORDS` goes with P7 (its one listener mirrors into `OpenGlHelper.lastBrightnessX/Y`,
`H/mixin/core/startup/MixinOpenGlHelper.java:57-60`, which `OpenGlHelper.setLightmapTextureCoords` now writes itself).
`DisplayListManager.postHook` (the fix of `5a22c6f2`) is replaced by the segmentation of C.2: with native lists, the only
way to keep a listener's GL calls out of a list being compiled is to end the list before posting.

### 3.4 The mirror

`GlMirror` holds, per GL context: active texture unit; the bound `GL_TEXTURE_2D` name per unit (the pass logic and
`gtextureId`, `D/uniforms/CommonUniforms.java:87-102`); `GL_TEXTURE_2D` enabled per unit (`TEXTURE_UNIT_STATE`); the
current program (`D/pipeline/DeferredWorldRenderingPipeline.java:969`, `B:314-398`); blend enable and function, alpha test
enable and function, depth mask and color mask (the override layer, §3.5, needs the application's last request); fog
mode, density, start, end, color (`FogUniforms`, `IrisInternalUniforms`, S15 if it stays); the shader color
(`setShaderColor` is GLSM's own API, not GL). Nothing else: no matrices (P6 removes every reader except three, which D.2
moves to `glGetFloat`), no lights, no materials, no texenv, no stacks.

Resync points, where the driver changes mirrored state without a call GLSM sees: `glPopAttrib` (re-read texture
binding, active unit, enables, blend, alpha, masks, fog); `glPopClientAttrib` (nothing mirrored); `glCallList` (the
list's footprint, C.2); `endForeignDraw` (re-read all); context switch (`makeCurrent`, per-context mirrors as today's
`registeredContexts`). A resync that finds a difference posts the corresponding hook, so Iris sees a texture rebound by
`glPopAttrib` exactly as it sees one bound by a call.

The oracle (`-Ddemonica.glsm.oracle=true`) wraps every mirror write and every resync with the matching driver query and
throws on mismatch with the call site; `GlStateDiffProbe` in the diagnostics jar already does this comparison for one
draw (`diagnostics/.../probe/GlStateDiffProbe.java:25-27`) and is the model.

### 3.5 Iris overrides on passthrough

Iris's `BlendModeStorage`, `AlphaTestStorage` and `DepthColorStorage` lock a state and want the application's calls to it
deferred while locked and reapplied at unlock (`B:113-184`, `D/gl/blending/*Storage.java`). On passthrough this is the one
place a call does not reach the driver, and it is by design: while `isBlendLocked()`, `PassthroughGl.glBlendFunc`
records the request in the mirror's "requested" slot and returns; `restoreBlend` reapplies the requested value. Nothing
else changes in the Iris side: the three handlers and `flushDeferredBlend` keep their contract, the six vanilla layers
(`setVanillaLayer` on the stacks, `B:140-145`) are the part that goes, because there are no stacks; `isEffectiveBlendEnabled`
and `getEffectiveBlendState` (used by `TranslucentBlendMatcher`, `D/gbuffer_overrides/matching/TranslucentBlendMatcher.java:15-18`)
read the requested slot. Under P7 vanilla's `GlStateManager` may elide a call that Iris would have wanted to see as a
request (vanilla's cache says blend is on, Iris turned it off underneath); at unlock the restore therefore reads
vanilla's `GlStateManager.blendState` fields as the last word when they are newer than the raw request. B.4 pins this
with a test and the `TranslucentBlendMatcher` cases from the sweep.

### 3.6 Texture metadata

`TextureInfoCache` (`G/texture/TextureInfoCache.java`) is populated from `glTexImage2D` on unit-bound 2D textures and read
by Iris for sizes and formats by **id** (`D/gl/framebuffer/GlFramebuffer.java:35`, `DeferredWorldRenderingPipeline.java:279,1524`,
`AtlasPBRLoader.java:45`, `CommonUniforms.java:91,104`). GL can answer by id only with `ARB_direct_state_access`
(`glGetTextureLevelParameteriv`); without it a query needs a bind. On compat the cache stays as an observed cache with a
driver fallback on miss (DSA when present, else bind/query/rebind through the mirror's resync), so a texture created
through DSA or by a class the redirector missed is answered correctly instead of as unknown. `glGetTexParameter*` and
`glGetTexLevelParameter*` themselves go to the driver (P3).

### 3.7 What this does to the compat surface

Under P1-P7 on the compatibility profile:

| Entry (fact 15) | Becomes |
|---|---|
| CodeChickenLib `GlStateTracker`, RenderLib `GLUtil`, HBM `RenderUtil`, Extra Utilities 2 `GLStateAttributes` | unnecessary (P7, P3); kept for core until G.3 decides |
| StellarCore and Gnetum HUD-caching transformers, `GLSMConfig.hudCacheOverride`, the five GSM read sites | unnecessary (P7: the mods' own mixins run) |
| Botania ×2, VoxelMap scissor | unnecessary (P1) |
| Lumenized/GTCEu bloom guard | unnecessary (no shader key); the mod's leak now has vanilla's effect |
| HBM early lightmap hook | unnecessary (the current texcoord of unit 1 is driver state) |
| VoxelMap alpha bits, min filter | core only, as their comments say |
| HUD baseline `GL_LEQUAL` (`27dad57a`) | re-evaluate in G.3: vanilla's cache is live, so CCL's pushes read the right value |
| S15 fog service | keep only the shape override, or drop (E.1) |
| `EndPortalRenderPolicy`/`EndPortalRenderer` without a pack | the legacy vanilla path's eye-linear texgen is native (G.4, optional) |

## 4. Conventions

- **Provenance.** No upstream to cite. Commits that move today's code into `EmulatedGl` carry `Moved-From: <path>`
  so `git log --follow` and the FORK.md convention keep working; the moved bodies are not edited in the same commit.
- **Text.** Every `PassthroughGl` entry point has a one-line comment naming what it observes, or `// passthrough` when
  nothing. Every place passthrough deliberately does not forward (the §3.5 locks) says `// virtualised: <why>`.
- **Spec references.** Where an item relies on a fact of the OpenGL 4.6 Compatibility Profile specification (what a
  display list captures, what `glPopAttrib` restores, which generic attributes may alias), the item cites the section
  number, and the test or oracle check that pins it names the same section.
- **Baseline first.** Before A.1 lands, the frames and logs of V-compat, V-core and V-mods are captured on `dev` and
  kept under `run/glsm-passthrough/baseline/` (not committed; the report records the commit and the hashes). Every later
  verification compares against them with the pixel-difference method of the Iris plan (share of pixels differing by
  more than 16/255, against a same-code floor).
- **Verification tiers.** Items name them by letter.
  - **V-build**: `./gradlew build` (unit tests, the mini-corpus replay, `verifyDistributedJar`, `verifyModuleBoundaries`).
  - **V-oracle**: the dev client with `-PdevProps=demonica.glsm.oracle=true` through `run/client/scripts/profile.txt`
    and the item's own script; zero mismatches logged. Available from A.2 on.
  - **V-compat**: the dev client on the default profile through `profile.txt` and `scripts/glsl-corpus/sweep.txt` (BSL,
    Complementary Reimagined, I Like Vanilla, and no pack), frames against the baseline.
  - **V-core**: the same with `-PdevProps=demonica.openglProfile=core`. The core path is not edited by this plan, so the
    frames must sit inside the same-code floor against the baseline at every checkpoint.
  - **V-mods**: the compat scripts, each with its mod toggle: `ocscreen.txt` (`-PwithOpenComputers`),
    `scripts/asmodeus-sky.txt` (`-PwithAsmodeusCore`), `itemgui.txt`, `hudcache.txt` (`-PwithHudCache`), `fontpack.txt`
    (Kirino), `dh33.txt` (`-PwithCompatMods`), `scripts/chunkanimator.txt`, `scripts/renderlib.txt`, `shaderpacks-scroll.txt`
    (`-PwithUniversalTweaks`), `menu.txt` with `-PwithModernSplash`. The mods of fact 15 that have no script
    (CodeChickenLib/Draconic Evolution, Extra Utilities 2, HBM, Botania, VoxelMap, Lumenized/GTCEu) get one when their
    compat entry is removed (G.3); until then the item says which were not run.
  - **V-perf**: `scripts/glsl-corpus/timing.sh` frame times, no pack and BSL, compat before and after, core before and
    after; `GLSMPerfDebug` counters for the per-call cost on the compat path (should read zero for the emulation
    stages).
  - **V-prod**: the Prism `prod-smoke-test` recipe of the releases memory, on the release jars.
- **Effort.** S an afternoon, M a few days, L a week or more.

## 5. Phase table

Lanes are independent branches; within a lane, items land in order. Lane A is the foundation every other lane builds on.

| # | Item | Effort | Depends on | Lane |
|---|---|---|---|---|
| A.0 | Baseline capture (§4) | S | none | A |
| A.1 | Mode switch and the `EmulatedGl`/`PassthroughGl` split; passthrough forwards everything, observes nothing | M | A.0 | A |
| A.2 | `GlMirror` with the oracle; resync points | M | A.1 | A |
| A.3 | Redirector contract: tests and the unmapped-call audit on compat | S | A.1 | A |
| B.1 | Queries to the driver; the `HAS_MULTIPLE_SET` and enable-cap tables go on compat | S | A.1 | B |
| B.2 | Hooks posted from the call: texture bind, unit state, program, alpha, blend, fog, shader color, texture delete | M | A.2 | B |
| B.3 | Iris override layer on passthrough (§3.5) | M | B.2 | B |
| B.4 | Vanilla `GlStateManager` and `OpenGlHelper` run (P7): redirector table, `LIGHTMAP_COORDS`, HUD-cache code | M | B.3 | B |
| C.1 | Native vertex path: client arrays, immediate mode, `GL_QUADS`, vanilla's uploader; side attributes for Iris | M | A.1 | C |
| C.2 | Native display lists, segmented at hook points | L | B.2, C.1 | C |
| C.3 | Emulation subsystems gated to core: `ffp/`, `recording/`, `streaming/`, `CompatShaderTransformer`, `CompatUniformManager`, `FeedbackManager`, line emulation, `guardUnsupportedFFP` | M | C.1, C.2 | C |
| D.1 | ATTRIBUTES transform, compatibility variant (P6) | M | A.1 | D |
| D.2 | Iris's matrix reads off GLSM: `FullScreenQuadRenderer`, `ShadowRenderer`, `EndPortalRenderer`; `refreshCurrentProgramMatrices` | S | D.1 | D |
| D.3 | Listener idempotence and post-rate audit | S | B.2 | D |
| D.4 | `TextureInfoCache` driver fallback (§3.6) | S | A.2 | D |
| E.1 | S15 and Celeritas on passthrough | S | B.4 | E |
| E.2 | Celeritas linkage and the elision sites of fact 3 | S | A.1 | E |
| F.1 | Diagnostics: `GpuCommandRecorder`'s program/framebuffer operands, `GlsmDrawLog`'s cached columns | S | A.2 | F |
| G.1 | Compat entries removed (§3.7), one PR per mod, each with its script | M | B.4, C.3 | G |
| G.2 | Documentation: SCOPE_RESEARCH §3.2 status, LEDGER S15 row, `docs/compat/*`, dev-run notes | S | G.1 | G |
| G.3 | Decisions deferred to measurement: HUD baseline, end-portal legacy path, wide lines, `-Ddemonica.glsm.mode` | S-M | G.1 | G |

## 6. Lane A: foundation

### A.0 Baseline capture

- **Goal.** Reference frames, logs and timings from `dev` before any change, for every tier of §4.
- **Do.** Run V-compat, V-core, V-mods and V-perf on `ef1b8c73` (or the merge base of the lane-A branch). Record the
  commit, the driver string (`GL_RENDERER`, `GL_VERSION`), the script list and the hashes in
  `glsm_passthrough/reports/A.0-baseline.md`. The core run is the one that must not move; take it twice to have its
  own floor.
- **1.12.2.** The sandbox hides the GPU (dev-run notes); the baseline is taken on the NVIDIA machine with the sandbox
  off, as the Iris sweeps were.
- **Verify.** The report exists and the floor numbers are inside the Iris plan's experience (0.0-0.8 % on animated
  frames).

### A.1 Mode switch and the split

- **Goal.** Two implementations behind the one surface; passthrough compiles and runs the game on the compatibility
  profile with every call forwarded and nothing observed, which already renders correctly without a pack and with
  Celeritas, because the driver does the fixed function (SPIKE.md's Celeritas-only run is the proof that the context
  can).
- **Files.** `G/RenderSystem.java:143-156` (choose the mode; stop enabling `ShaderManager` on compat), GSM (every public
  entry point dispatches; the bodies move to `G/emu/EmulatedGl.java` with `Moved-From:`), new `G/PassthroughGl.java`
  (one method per entry point: `RENDER_BACKEND.x(args)`; for names that `RenderBackend` lacks, which `RenderBackend`
  has none of the fixed-function ones (`G/backend/RenderBackend.java`, "no fixed-function methods" per SCOPE_RESEARCH),
  add them as `GL11`/`GL13`/`GL14` calls in `Lwjgl3GLRenderBackend` or call LWJGL directly from `PassthroughGl`; the
  redirector excludes `com.gtnewhorizons.angelica.glsm.`, `R:115-120`, so those calls are not rewritten),
  `H/mixin/core/startup/MixinOpenGlHelper.java:42-55` (the init config's `directDrawer` and streaming strategy are
  core-only), `H/debug/DemonicaDiagnostics.java:66` (the diagnostics line gains `glsm=passthrough|emulate`).
- **Mechanics.** The dispatch is mechanical: 814 public static methods on GSM. Generate `PassthroughGl` from the
  descriptor list the redirector tables name (`R:133-962`); anything GSM exposes that is not a GL entry point (the
  getters of fact 11, `beginForeignDraw`, `setShaderColor`, `initialize`, the context plumbing) stays on GSM and reads
  the mode. Entry points that exist on GSM only as GLSM extensions (`tryBlendFuncSeparate`, `enableBlend()` and the other
  `GL_CAP_REDIRECTS` targets, `R:123-131`) forward to the obvious GL call.
- **Decide during the item.** Whether `PassthroughGl` is a class of static methods selected by a `static final` boolean
  on every GSM method, or GSM's methods are rewritten by the build to call one or the other. Start with the boolean; the
  JIT folds it; measure in V-perf.
- **Verify.** V-build (the redirect-contract tests still see every descriptor). V-compat without a pack: terrain, GUI,
  item frames, end portal (the legacy path) against the baseline. V-core against the baseline (must be the floor).
  Shader packs are expected to be broken until lanes B and D; the report says so.

### A.2 `GlMirror` and the oracle

- **Goal.** The §3.4 mirror, written from the passthrough entry points that touch it, resynced at `glPopAttrib`,
  `glCallList`, `endForeignDraw` and `makeCurrent`, verified against the driver in oracle mode.
- **Files.** New `G/GlMirror.java` (plain fields, one per §3.4 item, per context: reuse `registeredContexts`/
  `tlWorkerContext` from GSM `:168-213`); `G/PassthroughGl.java` (the ~20 observing entry points: `glActiveTexture`,
  `glBindTexture`, `glEnable`/`glDisable` and the named `enable*`/`disable*` for `GL_TEXTURE_2D`, `GL_BLEND`,
  `GL_ALPHA_TEST`, `GL_FOG`, `glBlendFunc`, `glBlendFuncSeparate`, `tryBlendFuncSeparate`, `glAlphaFunc`, `glDepthMask`,
  `glColorMask`, `glUseProgram`, `glFogi`/`glFogf`/`glFog`, `glPopAttrib`, `glCallList`, `glDeleteTextures`);
  new `G/GlOracle.java` (`-Ddemonica.glsm.oracle`: after each mirror write, `glGetInteger(GL_TEXTURE_BINDING_2D)` etc.
  on the driver; throw with the call site).
- **Spec.** `glPopAttrib` restores the groups the matching push saved (Compatibility Profile §21.6, table 21.1):
  `GL_TEXTURE_BIT` covers the binding and enables per unit, `GL_COLOR_BUFFER_BIT` blend and masks and alpha test,
  `GL_FOG_BIT` fog, `GL_ENABLE_BIT` all enables; the current program is not in any group. The resync reads all mirrored
  items regardless of the mask (cheap, correct, and the mask is not tracked).
- **Verify.** V-build with a `GlMirrorTest` over the `StubRenderBackend` of `T/streaming/StubRenderBackend.java`.
  V-oracle through `profile.txt` and `itemgui.txt` without a pack: zero mismatches.

### A.3 Redirector contract on compat

- **Goal.** The tests that pin the redirector keep passing, and the unmapped-call list is known.
- **Files.** `T/redirect/*`, `T/RedirectedCallLinkageTest.java`, `src/test/java/com/demonica/celeritas/GlsmRedirectLinkageTest.java`
  (unchanged assertions: every rewritten call still resolves on GSM); `R:1123-1135` (`reportUnmapped` gets a
  `-Ddemonica.glsm.logUnmapped` switch independent of `GLSMDebug`); a new `UnmappedGlCallAuditTest` that runs the
  redirector's prefilter over the Cleanroom and Celeritas jars and lists the `org/lwjgl/opengl` names it leaves alone
  (fact 9's list), as a committed expectation file.
- **Why now.** On passthrough an unmapped call is harmless (it reaches the driver like a mapped one). The audit exists so
  that the mirror's observing set (A.2) is complete: a texture bind through an unmapped name would miss the mirror. The
  names in fact 9 are not state-bearing except `glRasterPos*` and `glWindowPos*`, which nothing mirrors.
- **Verify.** V-build.

## 7. Lane B: state

### B.1 Queries to the driver

- **Goal.** `glIsEnabled`, `glGetBoolean`, `glGetInteger`, `glGetFloat`, `glGetDouble`, `glGetMaterial`, `glGetLight`,
  `glGetClipPlane`, `glGetTexEnv*`, `glGetTexGen*`, `glGetTexParameter*`, `glGetTexLevelParameter*`, the indexed forms
  (`glGetIntegeri_v`, `glGetBooleani_v`) forward on compat.
- **Files.** `G/PassthroughGl.java` (already forwarding after A.1; this item removes the compat half of fact 4's tables
  by making the GSM methods dispatch before the `shouldBypassCache()` check, GSM `:847,954,1032,1062,1142,1220`), the
  array overloads (`glGetInteger(int,int[])`, GSM `:1169`; `glGetIntegeri_v(int,int,int[])` from `6728fbdb`) which keep
  their scratch-buffer shape.
- **Behaviour change to record.** Extra Utilities 2's compat says plain GPU queries for `GL_TEXTURE_2D` are invalid on
  the core context (`H/compat/extrautils2/ExtraUtils2GLStateCompat.java:8-20`); on compat they are valid and now answered
  by the driver, so that compat becomes core-only in G.1.
- **Verify.** V-build. V-oracle: the oracle itself is a query consumer. A probe in the dev client: `GuiGlStateBoundary`'s
  `CompositorSurfaceState.capture()` (`H/render/GuiGlStateBoundary.java:153-165`) logged before and after on `menu.txt`
  with RevoUI (`-PwithCompatMods` if it carries it; else note).

### B.2 Hooks from the call

- **Goal.** Every bus of `GLSMHooks` posts on compat from `PassthroughGl`, after the driver call, with the call's
  arguments; the event objects stay the shared instances (`G/hooks/GLSMHooks.java:63-75`).
- **Files.** `G/PassthroughGl.java`: `glBindTexture` (unit 0, `GL_TEXTURE_2D`) → `TEXTURE_BIND` and
  `RenderSystem.bindTextureToUnit(0, …)` as today (GSM `:2163-2170`, minus `lockBindCallback`, which guarded recursion
  through the copy); `enableTexture`/`disableTexture` and `glEnable(GL_TEXTURE_2D)`/`glDisable` → `TEXTURE_UNIT_STATE`
  with the active unit (the listener resolves ALBEDO/LIGHTMAP, `B:245-283`); `glUseProgram` → `PROGRAM_CHANGE` pre and
  post (`B:314-398`); `glAlphaFunc`, `enableAlphaTest`, `disableAlphaTest` → `ALPHA_STATE_CHANGE`; `glBlendFunc*` →
  `BLEND_FUNC_CHANGE`; `enableBlend`/`disableBlend` → `VANILLA_BLEND_CHANGE` (the listener re-matches the pass,
  `B:285-294`); `glFog*` → `FOG_STATE_CHANGE`; `glDeleteTextures` → `TEXTURE_DELETE`; `setShaderColor` →
  `SHADER_COLOR_CHANGE` (not a GL call; stays on GSM). `GLSMHooks.consumersActive` keeps gating the posts
  (`D/pipeline/PipelineManager.java:48`).
- **Decide during the item.** Whether `glBindTexture` on a non-zero unit posts anything (today no: `:2163`). Keep no.
- **Verify.** V-build with a `PassthroughHooksTest` (stub backend; one assertion per bus that the post follows the
  driver call and carries the arguments). V-compat with BSL on `profile.txt`: the first frames with a pack on
  passthrough; expect program selection to work and the gbuffers content to be wrong until D.1 (the programs still read
  `iris_*` uniforms nobody uploads). The report shows the frames anyway.

### B.3 Iris override layer on passthrough

- **Goal.** §3.5. `BlendModeStorage`, `AlphaTestStorage`, `DepthColorStorage` work unchanged on compat.
- **Files.** `G/PassthroughGl.java` (the lock checks at the top of `enableBlend`/`disableBlend`/`glBlendFunc`/
  `tryBlendFuncSeparate`/`glAlphaFunc`/`enableAlphaTest`/`disableAlphaTest`/`glDepthMask`/`glColorMask`, mirroring GSM
  `:1409-1415,1555-1559,1622-1626,1749-1752,1946-1949,2002-2047`; the requested slots in `GlMirror`), GSM
  (`isEffectiveBlendEnabled`, `getEffectiveBlendState`, `getEffectiveAlphaState`, `:1478-1500`, read the requested slots
  on compat), `B:140-145` (`setVanillaLayer` calls become no-ops on compat; the `gated()` wrappers stay for core),
  `D/gl/blending/BlendModeStorage.java:189-194` and `AlphaTestStorage.java:18-21` (`saveCurrentBlend`/`overrideAlphaTest`
  read the current state: from the mirror on compat, which is the driver's value by the oracle).
- **P7 interaction.** At unlock, compare the raw-request slot with vanilla's `GlStateManager.blendState`/`alphaState`
  fields (accessor mixin, `H/mixin/core/`), and apply the newer; record which was used in the diagnostics `pipeline-match`
  log (`diagnostics/.../IrisGlDiagnostics.java:1667-1687` already prints the blend state there).
- **Verify.** V-build with a lock test (lock, raw calls, unlock, assert the driver receives the last request once).
  V-compat BSL and Complementary on the sweep's entity and translucent frames (the `TranslucentBlendMatcher` cases);
  `ocscreen.txt` under BSL (the OpenComputers GUI uses the override path).

### B.4 Vanilla `GlStateManager` and `OpenGlHelper` run (P7)

- **Goal.** The redirector no longer rewrites calls to `net.minecraft.client.renderer.GlStateManager` and
  `OpenGlHelper`; their bodies are rewritten like any class, so `GlStateManager.enableBlend()` runs vanilla's Java and
  its `GL11.glEnable` reaches `GLStateManager.glEnable` and the driver. Applies to both profiles (the copy on core sees
  the same GL calls it would have mapped; `enableBlend` versus `glEnable(GL_BLEND)` are one state object there).
- **Files.** `R:522-772` (the two owner tables go; `GL_CAP_REDIRECTS` `R:123-131` stays for raw `GL11.glEnable(const)`),
  `R:986-991` (the `OpenGlHelper.glBlendFunc` body skip goes), `T/redirect/GLSMRedirectorTest.java:29,58,88` (the three
  assertions on vanilla-method rewrites invert), `T/redirect/GLStateManagerRedirectContractTest.java` (unchanged: the
  descriptors stay for mods calling GSM directly), `H/mixin/core/startup/MixinOpenGlHelper.java:57-60` (`LIGHTMAP_COORDS`
  listener goes; the bus goes), `H/loading/fml/transformers/StellarCoreHudCachingCompatTransformer.java`,
  `GnetumHudCachingCompatTransformer.java`, `H/mixins/MixinEarly.java:104-109` (unregistered), `G/hooks/GLSMConfig.java`
  (`hudCacheOverride`, `hudCacheBlendEnabled`, `lastBrightnessX/Y` go on compat; the GSM read sites `:1416,1440,1563,1628,
  1871` become core-only or go), `H/mixin/mod/revoui/MixinScreenEffectsGradientRelocation.java:34`,
  `H/mixin/mod/voxelmap/MixinVoxelMapRenderMap.java:44`, `MixinVoxelMapGLShim.java:40` (read StellarCore's own flag through
  an accessor instead).
- **What vanilla's cache does that GLSM did not.** Vanilla elides repeats (`GlStateManager.enableBlend` checks
  `blendState.blend.currentState`); `da90bad4` found one case where vanilla drops `renderString`'s black and GLSM did not
  (the Modern Splash font). On passthrough the result is vanilla's, which is the 1.12.2 contract. The splash font mixin
  (`H/mixin/core/startup/MixinFontRendererSplashColor.java`) is re-checked in V-mods (`menu.txt`, `-PwithModernSplash`).
- **Core.** On core the copy now also sees `GlStateManager.resetColor`'s effect as vanilla implements it (no GL call;
  vanilla sets its own `colorState` to -1 and the next `color()` call issues `glColor4f`), so `clearCurrentColor`'s
  sentinel (GSM `:1926-1936`) loses its only caller through the redirector; keep it for direct callers (Celeritas's
  `CeleritasWorldRenderer.drawChunkLayer:77` calls vanilla `resetColor`, now vanilla's).
- **Verify.** V-build. V-compat and V-core full sweeps (this item changes what the copy sees on core too; the core frames
  must stay inside the floor). V-mods: `hudcache.txt` with `-PwithHudCache` (StellarCore's own mixins now run; the
  cached HUD must match the uncached one), `menu.txt` with Modern Splash, `renderlib.txt`.

## 8. Lane C: the vertex path and display lists

### C.1 Native vertex path

- **Goal.** On compat, client arrays, `glBegin`/`glEnd`, `GL_QUADS`, `GL_QUAD_STRIP`, `GL_POLYGON` and vanilla's
  `WorldVertexBufferUploader` are the driver's; Iris's per-quad side attributes (`mc_midTexCoord`, `at_tangent`) still
  reach entity and item draws under a pack.
- **Files.** `G/PassthroughGl.java` (`glVertexPointer`… forward as fixed-function pointers; `glEnableClientState`
  forwards; `glBegin`/`glEnd`/`glVertex*`/`glColor*`/`glNormal*`/`glTexCoord*`/`glMultiTexCoord*` forward; draws forward
  without `preDrawFFP`/`prepareClientArrays`/`QuadConverter`), `H/mixin/core/MixinTessellator.java` and
  `MixinWorldVertexBufferUploader.java` (on compat: do not cancel; instead, when a pack is active and the draw is
  `GL_QUADS` with position, UV and normal, bind the side buffer and point generic attributes 12 and 13 at it before
  vanilla's `glDrawArrays` and disable them after: `QuadSideAttributes.layout(format)` and `QuadSideAttributeWriter`
  already compute the data, `H/render/VanillaBufferBuilderRenderer.java:175-218`), `H/render/VanillaBufferBuilderRenderer.java`
  and `VanillaVertexBufferRenderer.java` (core-only), `src/main/resources/mixins.demonica.core.json:17`.
- **Spec.** Generic attribute arrays coexist with the conventional arrays in the compatibility profile, and
  `glDrawArrays` inside `glNewList` captures both as data (Compatibility Profile §10.3, §21.4 "Display Lists": vertex array
  commands are executed immediately and their data dereferenced at compile time). Attribute 0 may alias `gl_Vertex` on
  some implementations (§10.2.1), which is why the side attributes stay at 12 and 13. This is what makes `ModelRenderer`'s
  compiled lists carry `at_tangent` natively (C.2) without a recorder: `ModelRenderer.compileDisplayList` draws through the
  `Tessellator` inside `glNewList`.
- **1.12.2.** Vanilla's uploader issues `glVertexPointer`/`glColorPointer`/`glTexCoordPointer`/`glNormalPointer` plus
  `OpenGlHelper.setClientActiveTexture` for the lightmap, then `glDrawArrays`; nothing in it needs GLSM.
- **Old Research's** client-array tessellator compat (`H/compat/oldresearch/OldResearchTessellatorCompat.java:20` calls
  `TessellatorStreamingDrawer.draw`) becomes core-only.
- **Verify.** V-compat without a pack (`itemgui.txt`: GUI items and the flat-block case of `24aee44c`, `profile.txt`).
  Under BSL with `ADVANCED_MATERIALS` (the Insanity case of the Iris plan §10): entity parallax and tangent frames on the
  sweep's entity frame against the baseline. V-core (must be the floor).

### C.2 Native display lists, segmented

- **Goal.** `glGenLists`/`glNewList`/`glEndList`/`glCallList`/`glCallLists`/`glDeleteLists`/`glListBase`/`glIsList` are the
  driver's on compat, and Iris still sees the state changes a list makes between its draws, in order.
- **Design.** GLSM owns the id namespace (it already does: `DisplayListIDAllocator`, GSM `:3960-3976`) so that one
  application list can be several driver lists. During `glNewList(L, mode)` every call passes through `PassthroughGl` and
  is compiled by the driver; when a call that posts a hook (the B.2 set) arrives **after a draw** in the same list, GLSM
  ends the current driver list, records the event in L's segment table, and begins the next driver list in the same
  mode. `glEndList` closes the last segment. `glCallList(L)` runs the table: post the events of a boundary, `glCallList`
  the segment, and so on; nested `glCallList` inside a list is recorded as a boundary entry and recursed. Events before
  the first draw are posted before the first segment; events after the last draw after it. A list with no mid-list hook
  calls is one driver list and `glCallList(L)` is one driver call. `GL_COMPILE_AND_EXECUTE` executes each segment as it
  compiles, which is native behaviour per segment. The mirror resync at `glCallList` (A.2) uses the segment table's
  footprint (which mirrored items the list writes) instead of a full re-read.
- **Spec.** Ending and beginning a list between two commands does not change their effect: a display list is "a group of
  GL commands that have been stored for subsequent execution" and executing lists back to back is executing their
  commands in order (Compatibility Profile §21.4). The commands that cannot appear inside `glBegin`/`glEnd` (§10.7.5,
  `glBindTexture`, `glEnable`, `glUseProgram` are all errors there) are exactly the hook-posting ones, so a boundary never
  falls inside a primitive. `glPushAttrib`/`glPopAttrib` inside a list are driver-side and cross segments freely.
  Nested `glNewList` is an error (§21.4) and stays unsupported on compat; the one mod that needs it (the comment at
  `G/DisplayListManager.java:114` does not name it) is an open check.
- **Files.** New `G/NativeDisplayLists.java` (segment tables keyed by application id, the allocator, the footprint),
  `G/PassthroughGl.java` (the list entry points; the hook-posting entry points consult `NativeDisplayLists.isCompiling()`
  and ask for a boundary instead of posting), `G/DisplayListIDAllocator.java` (shared), `H/mixin/core/MixinRenderGlobal*`
  and friends only if any host code assumed GLSM lists (grep `VBOManager` and negative ids: `G/DisplayListManager.java:769-780`
  checks GTNHLib's `VBOManager` for negative ids; find the issuer).
- **What this removes.** `postHook`'s pause-and-resume (`G/DisplayListManager.java:156-174`) and the `5a22c6f2` class of
  bug: listener GL calls can no longer be recorded because no application list is open when a hook posts. The
  OpenComputers case (`compat/opencomputers.md`) becomes: list L = [bg quads, disable TEXTURE_2D, draw, enable, draw]
  compiles as three segments with two `TEXTURE_UNIT_STATE` boundaries; each call of L switches passes as the live draw
  did.
- **Decide during the item.** Whether boundaries are inserted for *every* hook-posting call after a draw or only for the
  ones whose listener changes GL state (`TEXTURE_UNIT_STATE`, `TEXTURE_BIND`, `PROGRAM_CHANGE`, `VANILLA_BLEND_CHANGE`;
  the uniform notifiers do not). Fewer segments is better; start with the four.
- **Verify.** V-build with a `NativeDisplayListsTest` over the stub backend (segment table for the OC shape; nested call;
  compile-and-execute; delete). V-mods: `ocscreen.txt` under BSL and Complementary against `compat/opencomputers.md`'s
  frames; `asmodeus-sky.txt` (lists compiled with `GL_COMPILE`, called with a negative color: the driver clamps). V-compat
  entity frames (ModelRenderer lists) with and without BSL. V-perf: display-list-heavy frames (a crowd of mobs) before and
  after; the recorder merged draws and collapsed transforms, and the driver's lists must not be slower than that on the
  NVIDIA baseline machine (open check for Mesa).

### C.3 Emulation gated to core

- **Goal.** Nothing of the emulation runs on compat: no `ShaderManager`, no `CompatShaderTransformer` on
  `glShaderSource`, no `CompatUniformManager` on link/use, no `FeedbackManager`, no line emulation, no
  `guardUnsupportedFFP`, no `remapTexClamp` (GL_CLAMP is legal on compat), no `GL_ALPHA`-format promotion
  (`GLTypes.promoteAlphaFormat`, needed on core where `GL_ALPHA8` is gone), no `defaultVAO` substitution for VAO 0.
- **Files.** GSM `:5469-5471`, `:7161-7166`, `:7218` (dispatch before the compat-shader hooks), `G/PassthroughGl.java`
  (`glShaderSource`, `glLinkProgram`, `glUseProgram`, `glRenderMode`, `glFeedbackBuffer`, `glSelectBuffer`, `glLineStipple`,
  `glLineWidth`, `glPixelTransfer*`, `glRasterPos*`, `glPolygonStipple`, `glAccum`, `glTexEnv`, `glTexParameter*`,
  `glTexImage2D`, `glBindVertexArray` forward), GSM `:594-620` (wide-line probing core-only). Move
  `ffp/`, `recording/`, `streaming/`, `DisplayListManager`, `ImmediateModeRecorder`, `QuadConverter`, `FeedbackManager`,
  `CompatShaderTransformer`, `CompatUniformManager`, `CompatProgramUniformState(s)`, `GlslTransformUtils` under `G/emu/`
  with `Moved-From:` (one commit, no edits), so that `verifyModuleBoundaries` can later assert that nothing outside
  `G/emu` and `EmulatedGl` imports them.
- **What a mod's GLSL gets on compat.** Its own source: `gl_ModelViewMatrix`, `gl_LightSource`, `gl_Fog`, `gl_TexCoord[]`
  are the driver's. Mods that ship `#version 120` shaders expecting builtins (the reason `CompatShaderTransformer`
  exists on core) now work as they did on 1.12.2 without Demonica. The transformer's ES-style guards and preprocessor
  fixes (`T/CompatShaderTransformerTest.java`) are core-only.
- **Verify.** V-build (`verifyModuleBoundaries` rule added). V-compat with a mod that ships shaders: Lumenized/GTCEu
  bloom, or the `glsl-corpus` compat scripts (`scripts/glsl-corpus/compat.txt`, `compat-bsl.txt`), against the baseline.
  V-core (must be the floor: the moves are pure).

## 9. Lane D: Iris on passthrough

### D.1 ATTRIBUTES transform, compatibility variant (P6)

- **Goal.** On compat, a gbuffers or shadow program for vanilla-style draws (`Patch.ATTRIBUTES`) is compiled with the
  pack's builtins and `#version N compatibility`; the driver supplies `gl_Vertex`, `gl_Color`, `gl_Normal`,
  `gl_MultiTexCoord0`/`1`/`2`, `gl_ModelViewMatrix`, `gl_ProjectionMatrix`, `gl_NormalMatrix`, `gl_TextureMatrix[n]`,
  `gl_Fog`, `gl_FogFragCoord`, `gl_FrontColor`, `ftransform()`. `CELERITAS_TERRAIN`, `COMPOSITE`, `COMPUTE`, `DH_*` are
  unchanged on both profiles (they are Iris-owned draws with Iris-owned vertex formats, not fixed-function state).
- **Files.** `D/pipeline/transform/transformer/AttributeTransformer.java:22-91` (`transform` takes the profile; on
  compat skip `transformCore` and call a new `transformCompat`: `EntityPatcher` as today, `mc_chunkFade` as today,
  `gl_MultiTexCoord2 → gl_MultiTexCoord1` when the lightmap input exists and the constant `vec4(240,240,0,1)` replacements
  when it does not, as `:65-76` do today, `patchMultiTexCoord3` for `mc_midTexCoord` as an `attribute`, bounded 4-7 as
  today; no `iris_*` injection, no renames), `CommonTransformer.java:88-170` (`transform(…, core=false, …)` branch: keep
  `gl_FragData`/`gl_FragColor` as written when the version is below 130, flatten to layout outputs above it as today;
  no `iris_Fog` struct; no `iris_FogFragCoord`/`iris_FrontColor` varyings; keep the texture-function renames and
  `shadow2D` wrapping, which are version facts not profile facts; keep the clip-plane injection? no: `gl_ClipVertex`/
  `gl_ClipDistance` work natively and `actinium_ClipPlane*` has no uploader on compat, so drop it there),
  `CoreTransformHelper.java` (untouched; not called on compat), `VersionNegotiation.java:44-60` (the profile keyword:
  `compatibility` for ATTRIBUTES on compat for every version ≥ 150, empty below), `ShaderTransformer.java:287-310`
  (passes the profile), `D/pipeline/transform/corpus/*` and `run/transform-corpus*` (new cases `attributes-compat-*`;
  the replay records the profile with the case, as `VersionNegotiation.versionHoistingState()` is recorded today),
  `G/CompatUniformManager.java:87-96` (the `iris_*` matrix slots stay for core).
- **Spec.** `#version 330 compatibility` keeps every deprecated builtin (GLSL 3.30 §1.3, §7.1-7.5) and is accepted by a
  compatibility-profile context; the fixed-function attributes and matrices are the ones the driver's state holds at the
  draw (Compatibility Profile §10.2, §12.1). This is how OptiFine's and ShadersMod's packs ran on 1.12.2, so BSL,
  Complementary and I Like Vanilla were written for exactly these inputs.
- **Alpha test.** `CommonTransformer.java:129-137` injects `iris_currentAlphaTest` discard on core because core has no
  alpha test. On compat the alpha test is real, but Iris's `AlphaTestOverride` sets it to the pack's `alphaTest`
  directive through `glAlphaFunc`, which B.3 forwards, so the driver does it; keep the discard injection too (it is
  upstream behaviour and harmless) or drop it on compat; decide by the sweep's cutout frames.
- **Verify.** V-build and V-transform as the Iris plan defines it (mini-corpus cases, `accepted.txt`, the three
  pack-corpus replays with `-PglslReplayThreads=8`, `glslangValidator` per stage with `--target-env opengl` and the
  compatibility profile). V-compat: the full sweep under the three packs against the baseline; this is the item where
  passthrough with a pack must first match emulation with a pack. Probe pack (Iris plan §2) with a `gbuffers_textured`
  that writes `gl_ModelViewMatrix[3]` and `gl_Normal` to colortex0, compared between modes with `-Ddemonica.glsm.mode`.

### D.2 Iris's matrix reads off GLSM

- **Goal.** No reader of `GLStateManager.getModelViewMatrix()`/`getProjectionMatrix()` is left on compat;
  `CompatUniformManager.refreshCurrentProgramMatrices()` and `onUseProgram` have no compat caller.
- **Files.** `D/postprocess/FullScreenQuadRenderer.java:91-110` (uploads `iris_ModelViewMatrix`/`iris_ProjectionMatrix` to
  the current program for composite passes: on compat the COMPOSITE transform still renames to `iris_*`
  (`CoreTransformHelper.injectCompositeVertexAttributes`), so keep the upload but take the matrices from
  `RenderingState` or constants: composite draws a unit quad with identity matrices, which is what the pipeline sets
  before calling it), `D/pipeline/ShadowRenderer.java:610,642-653,729` (`glGetInteger(GL_MATRIX_MODE)` is a driver query
  now; `refreshCurrentProgramMatrices` calls go on compat: the shadow programs read `gl_ModelViewMatrix` from the driver
  after `RenderSystem.setupProjectionMatrix`, which must be a real `glMatrixMode`/`glLoadMatrix` on compat; check
  `G/RenderSystem.java:443`), `H/render/EndPortalRenderer.java:75-76` (`glGetFloat(GL_MODELVIEW_MATRIX)` into a scratch
  buffer), `H/mixin/features/iris/RenderGlobalIrisMixin.java:254` (`onUseProgram` call core-only),
  `diagnostics/.../probe/ShadowCallbackProbe.java:123-124` (compare against `glGetFloat`).
- **Iris plan item 1.1 and open check 2** described how GLSM's stack holds the shadow matrices during entity draws; on
  compat the same holds in the driver's stack because the same calls now reach it.
- **Verify.** V-build. V-compat: shadows frames of the sweep (BSL with shadows, Complementary) and the probe pack of 1.1
  (`iris_*Inverse`, now `gl_ModelViewMatrixInverse`, shown in colortex0).

### D.3 Listener idempotence and post rate

- **Goal.** Every listener in `B` tolerates a repeated event with equal arguments, and the per-frame post count on
  passthrough is known.
- **Files.** `B:186-398`; `D/pipeline/DeferredWorldRenderingPipeline.java:854-917` (`onModProgramOverride`,
  `restorePassAfterModProgram`, `onVanillaBlendChanged`, `setInputs`: the ones with side effects on repeat),
  `shader/src/main/java/com/gtnewhorizons/angelica/client/rendering/TextureTracker.java:32`.
- **Measure.** A `GLSMHooks` counter per bus, printed by `-Ddemonica.glsm.hookStats` at pipeline end, on `profile.txt`
  under BSL, both modes. If a bus posts an order of magnitude more on passthrough, add the equality check in the
  *listener* (its own last-seen value), never in GLSM.
- **Verify.** V-build with one repeat-event test per listener that has state. V-compat sweep unchanged.

### D.4 `TextureInfoCache` driver fallback

- **Goal.** §3.6. A miss resolves through `glGetTextureLevelParameteriv` when `RenderSystem` reports DSA, else through a
  bind on the active unit followed by the mirror's resync.
- **Files.** `G/texture/TextureInfoCache.java` (`getInfo(id)` fallback), `G/RenderSystem.java` (the DSA capability is
  already probed for `dsaState`), the `onTexImage2D` population stays as an observation (`G/RenderSystem.java:165-167`,
  GSM texture upload sites).
- **Verify.** V-build with a stub-backend test (miss, DSA path, non-DSA path leaves the binding as found). V-mods:
  `fontpack.txt` (Kirino's DSA textures, the `e0ec3f26` case).

## 10. Lane E: Celeritas

### E.1 S15 and Celeritas on passthrough

- **Goal.** Celeritas renders on passthrough with no quarantine patch that exists because of GLSM.
- **Files.** `H/mixin/celeritas/seam/GLStateManagerFogServiceMixin.java` (under B.4 vanilla's `GlStateManager.fogState`
  is live again, so upstream's `GLStateManagerFogService` reads correct values; what remains of S15 is the shape:
  upstream reports spherical, vanilla 1.12's fog is eye-plane, SPIKE.md:69-74 measured the edge difference. Keep S15 as a
  shape-only patch, or drop it and accept upstream's shape; the ledger row and `patches/S15.md` say which),
  `docs/celeritas/LEDGER.md:53,127` and `patches/S15.md`, `H/mixin/celeritas/internal/GlProgramMixin.java` (S17 stays: the
  `PROGRAM_CHANGE` listener still needs to know Iris's programs; B.2 posts on every `glUseProgram`, so the "candidate for
  removal" note in the ledger is re-measured in D.3).
- **Facts to lean on.** Celeritas's `glUseProgram(0)` after a layer reaches the driver on passthrough (today it activates
  the FFP shader instead, GSM `:5297-5323`); its `GlStateTracker` elision is its own; its matrices come from
  `ActiveRenderInfo`, which vanilla fills from `GlStateManager.getFloat`, now a driver query.
- **Verify.** V-compat without a pack and under BSL (terrain fog at the screen edges against SPIKE.md's description and
  the baseline); `guard-*.txt` scripts (the jar-pin guard, unchanged). V-core (floor).

### E.2 Celeritas linkage and fact 3's elision sites

- **Goal.** The linkage test still passes against GSM's surface after A.1 and B.4, and none of Celeritas's calls is
  skipped on passthrough.
- **Files.** `src/test/java/com/demonica/celeritas/GlsmRedirectLinkageTest.java` (unchanged assertions), a dev-client
  check with the oracle on `profile.txt` (the oracle compares `GL_CURRENT_PROGRAM`, `GL_FRAMEBUFFER_BINDING`,
  `GL_TEXTURE_BINDING_2D` after Celeritas's binds).
- **Verify.** V-build, V-oracle.

## 11. Lane F: diagnostics

### F.1 Command recorder operands and draw-log columns

- **Goal.** The diagnostics jar reports the driver's program and framebuffer, not the copy's.
- **Files.** GSM `:488-541` (`recordGpuCommand` takes `activeProgram`/`drawFramebuffer` from `ctx()`; on compat read the
  mirror's current program and `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)` when a recorder is installed, which it is only
  with the diagnostics jar), `getBoundTextureForGpuDiagnostics` (`:527-532`, the mirror), `diagnostics/.../glsm/GlsmDrawLog.java:95-203`
  ("cached" columns read the mirror on compat and already read the live values beside them),
  `diagnostics/.../iris/IrisGlDiagnostics.java` (its ~150 raw `glGet*` are driver queries after B.1, which is what it
  wanted; the `world-pass-state` and `pipeline-match` logs read GSM getters: route to the mirror/requested slots).
- **Verify.** V-build (`GpuCommandDiagnosticsTest`, `GpuCheckpointTrackerTest`). A diagnostics run of `profile.txt` under
  BSL with the flight recorder on; the log's program ids match the driver's in the oracle run.

## 12. Lane G: compat removal and closing decisions

### G.1 Compat entries removed, one PR per mod

For each row of §3.7 marked unnecessary: remove the mixin or transformer on compat (gate on the mode where the core
profile still needs it), add or run the mod's script, and record frames. Order by evidence available: OpenComputers
(script exists, C.2), StellarCore/Gnetum (script exists, B.4), RenderLib (script exists), then CodeChickenLib with
Draconic Evolution, Extra Utilities 2, HBM, Botania, VoxelMap, Lumenized/GTCEu as their jars are obtained (the Prism
instances of the dev-run notes hold some). Each PR's report says which mod and which case (the issue number in the
class's comment: #21/#22 RenderLib, #40 HammerLib, #41 OreLib, #48 Extra Utilities 2, #64 NTM-CE, #134, #136 HBM, #137,
#150 splash) it re-verified.

### G.2 Documentation

`SCOPE_RESEARCH.md §3.2` gets a dated status paragraph pointing here (the "0 lines became cold" paragraph is the one this
plan answers); `celeritas/LEDGER.md` S15 row and `patches/S15.md` per E.1; `docs/compat/opencomputers.md` and
`asmodeuscore.md` gain a "since passthrough" line; the dev-run notes gain `-Ddemonica.glsm.mode`, `oracle`, `hookStats`,
`logUnmapped`; the Iris porting plan's §11 open check 2 gets a cross-reference (D.2).

### G.3 Decisions deferred to measurement

- **HUD baseline** (`H/render/GuiGlStateBoundary.restoreHudBaseline`, `27dad57a`): with vanilla's cache live, CCL's
  subset pushes read `GL_LEQUAL`; remove the baseline's depth-func reassert if the chat-text case stays fixed on
  `hudcache.txt` and a Draconic Evolution run.
- **End portal without a pack** (`H/render/EndPortalRenderPolicy.java`): the legacy vanilla path's eye-linear texgen is
  native on compat, so `shouldUseReplacementRenderer` could return false without a pack. Measure the portal frames of
  `profile.txt` both ways; the replacement renderer stays for packs on both profiles and for core.
- **Wide lines and stipple**: on compat the driver's `GL_ALIASED_LINE_WIDTH_RANGE` decides; a driver that reports a
  maximum of 1 draws thin lines, as vanilla does on it. If a user report wants the emulation back on compat, it is a
  core-path feature reused, not a passthrough feature.
- **`-Ddemonica.glsm.mode=emulate` on compat**: keep as a diagnostic for one release, then remove; the emulated path on
  a compatibility context is exactly what this plan retires.
- **The core profile's future**: out of scope. After this plan, `G/emu` is the whole of Angelica's design, used only by
  macOS and the `Core` option; whether it stays, shrinks, or is replaced by a smaller core-only emulator is a separate
  decision with its own research page.

## 13. Order

- **Lane A** first and alone: A.0, A.1, A.2, A.3 as one PR each. After A.1 the compat profile without a pack is already on
  the driver; that is the first checkpoint (V-compat no-pack, V-core floor, V-perf no-pack).
- **Lanes B, C, D in parallel** after A.2: B is GLSM-internal plus the bridge; C is GLSM plus two host mixins; D is the
  transform layer and three Iris readers. They touch disjoint files except `G/PassthroughGl.java`, where B owns the state
  entry points and C the vertex and list entry points. Land B.1-B.3 before C.2 (segment boundaries post the B.2 hooks
  and respect the B.3 locks). D.1 is the item with the longest verification; start it early.
- **Second checkpoint** after B.3, C.2, D.1 and D.2: V-compat with the three packs, V-mods, V-perf, V-oracle. This is the
  point at which passthrough with a pack must match the baseline; everything after it is removal.
- **B.4** after the second checkpoint (it changes the core path's inputs too, so it wants the full sweep behind it), then
  C.3, E, F, G.
- **Release checkpoints**: after the second checkpoint (a pre-release on the compat default, emulate still selectable),
  and after G.1 (V-prod).

## 14. Not planned

- Refactoring, reducing or testing the emulated path beyond the mechanical move (P5). Its tests (`T/ffp/*`,
  `T/recording/*`, `T/streaming/*`, `T/states/*`) keep running against the moved classes.
- A GLSM-owned display-list recorder on compat as a fallback for performance; if C.2's measurement fails on a driver,
  that is an open check with its own decision, not a dual implementation.
- Translating packs' `#version N core` ATTRIBUTES programs to builtins: under D.1 a pack that declares `core` on a
  gbuffers program gets `compatibility` instead, which is a superset; no pack in the sweep declares `core` on those
  stages (Iris plan §11 item 3 read their `shaders.properties`; D.1 records the versions it finds).
- Removing the redirector. It stays as the mechanism that gives GLSM the calls to observe and the id namespace of C.2;
  its tables shrink by the two vanilla owners (B.4) and nothing else.
- Changing Celeritas's quarantine beyond S15 (E.1) and the S17 note (D.3).
- Any change to the LWJGL abstraction (`backend/`, `com.mitchej123.lwjgl`), to `GLDebug`, to the texture-target
  bookkeeping of `e0ec3f26` (it stays for deferred deletes on both profiles), or to the GLU ports (`compat/lwjgl`, reached
  through the type redirects, profile-independent).

## 15. Open checks

Resolve during the item and record the answer here.

1. **Which mod needs nested `glNewList`** (`G/DisplayListManager.java:114`, "spec-violating but needed for mod
   compatibility")? Angelica's history should name it; C.2 decides whether a nested `glNewList` on compat ends the outer
   list's current segment (legal) or is refused with a warning.
2. **Mesa display-list and immediate-mode performance** (C.2, C.1): the sandbox and CI run llvmpipe; the baseline
   machine is NVIDIA. V-perf on an AMD or Intel Linux machine before the second checkpoint if one is reachable.
3. **Does any class define a `GL_COMPILE_AND_EXECUTE` list whose live half must see a hook before its first draw?** The
   segment design posts pre-draw events before the first segment at call time and, under compile-and-execute, at
   compile time as the live calls pass; confirm with `ocscreen.txt` that the live half draws right on the compiling call.
4. **`GuiIngameForgeIrisMixin.java:17`** calls `GL11.glEnable(GL_ALPHA_TEST)` from a mixin merged into Forge's class.
   The late redirector registers with priority `Integer.MAX_VALUE` after Mixin's prepare
   (`src/main/java/com/gtnewhorizons/angelica/loading/fml/transformers/AngelicaRedirectorTransformer.java:22-25`,
   `.../tweakers/AngelicaLateTweaker.java:36-52`), so merged handler code should be rewritten; verify once with
   `-Ddemonica.glsm.logUnmapped`.
5. **VoxelMap `GLShim`'s premise** (`MixinVoxelMapGLShim.java:14-23`): the redirector maps `glEnable`/`glScissor`, so the
   mixin's "bypassing the cache" may already be stale. Irrelevant on passthrough; record the answer for the core profile.
6. **Packs' ATTRIBUTES versions** (D.1): list the `#version` lines of BSL 10.1.8, Complementary Reimagined r5.9.3 and
   I Like Vanilla 1.4.4's gbuffers and shadow stages; any `core` keyword or any use of a 330-only feature inside a
   vertex stage that also uses `gl_Vertex` is the case `compatibility` exists for.
7. **`TextureTracker` and `bindTextureNotifier` under repeated binds** (D.3): `CommonUniforms`' `atlasSize`,
   `gtextureId`, `gtextureSize` recompute per notification; on passthrough a bind of the same texture notifies again.
   Measure; if it matters, the tracker keeps the last id.
8. **Worker contexts** (`GLStateManager.enterWorkerContext`, GSM `:187-213`): who enters one on 1.12.2 (parallel chunk
   building is Celeritas's own, off GL). If nothing does, the per-context mirror is one instance plus the splash thread.
9. **GSM's copy-only matrix setters.** `RenderSystem.setupProjectionMatrix`/`restoreProjectionMatrix`
   (`G/RenderSystem.java:443-455`) issue `glMatrixMode`/`glPushMatrix`/`glLoadMatrix` through GSM, so passthrough forwards
   them. `GLStateManager.setModelViewMatrix`/`setProjectionMatrix` (GSM `:4281-4296`) write the copy without a GL call;
   find their callers and, on compat, make them `glLoadMatrix` on the right stack or remove them.
10. **The `Drawable.makeCurrent` redirect** (`R:1033-1041`) and the splash context migration
    (`displayContextMigrated`, `MixinSplashProgress.java:52-53`): on passthrough there is no default VAO to recreate and no
    state to replay (`displayContextMigrated` and the re-seed, GSM `:3891-3916`; `replayStateToBackend` around `:7748`);
    confirm the splash-to-main handover needs nothing but a mirror resync.

## 16. Risks

- **Driver variance on the compatibility profile.** The plan moves work from Java to the driver's fixed-function and
  display-list implementations. NVIDIA and AMD Windows drivers are the ones every 1.12.2 player already uses for this;
  Mesa is good on both; Intel's Windows driver is the historical weak spot for display lists. Mitigation: open check 2,
  and the `Core` option remains for a user whose driver misbehaves (it is the Angelica design, unchanged).
- **The second implementation of hooks.** B.2 re-creates 23 post sites. The stub-backend tests per bus and the
  hook-stats counter (D.3) are the guard; the sweep is the judge.
- **Idempotence assumptions in Iris.** Listeners written against change-detected events may re-enter pass matching on
  repeats (D.3). Fixing them in the listener is the correct place and also fixes the latent bug on core.
- **P7 reopens vanilla's cache semantics.** Every 1.12.2 mod lives with them; Demonica's own code must too (the splash
  font case `da90bad4` is the known one). B.4's full sweep on both profiles is the gate.
- **Segmented lists change a list's id behaviour.** `glIsList` and `glDeleteLists` ranges must map application ids to
  all segments; `glCallLists` with offsets and `glListBase` too. The allocator already virtualises ids; the test covers
  ranges.
- **Scope creep into the core path.** Every item says "core unchanged" and V-core proves it; the temptation to tidy
  `EmulatedGl` while moving it is declined by the `Moved-From:` rule (no edits in a move commit).

## 17. Release notes draft

For the release that carries the second checkpoint:

- **GLSM is a passthrough on the compatibility profile.** On Windows and Linux (the default OpenGL profile), Demonica
  no longer keeps its own copy of the OpenGL state or draws vanilla and mods through generated shaders. Every GL call
  reaches the driver as the game and the mods issued it; the driver's fixed-function pipeline, display lists and client
  arrays do their own work; queries return what the driver holds. Shader packs run their gbuffers programs with the
  pack's own `gl_*` builtins, as they did under OptiFine, and Iris observes texture, program, alpha, blend and fog
  changes as they pass. Vanilla's `GlStateManager` runs its own code again, so mods that patch it (StellarCore's and
  Gnetum's HUD caching, CodeChickenLib, RenderLib, Extra Utilities 2, HBM) work without Demonica's workarounds, which
  are removed. Known fixes this replaces: AsmodeusCore's white sky, OpenComputers' screen text under packs, the flat GUI
  block items of 0.8.0, and the class of "state leak" bugs behind the Botania and VoxelMap patches.
- **The core profile is unchanged.** macOS and the `Core` option keep the emulated path exactly as 0.8.x shipped it;
  `-Ddemonica.glsm.mode=emulate` selects it on a compatibility context for comparison, for this release only.
- **Diagnostics.** `demonica.glsm.oracle=true` checks GLSM's observed state against the driver after every call and
  reports the first mismatch with its call site; `demonica.glsm.hookStats=true` prints per-frame hook counts.
