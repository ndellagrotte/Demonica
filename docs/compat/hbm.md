# NTM-CE: instanced particles are black under a shader pack

## Symptom

With HBM's Nuclear Tech Mod: Community Edition (NTM-CE 2.6.1.0, mod id `hbm`) and a shader pack
(reported with Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5, Demonica 0.10.0-dev on
Cleanroom 0.7.7), "many modded particle effects are black": the smoke of explosions, rocket and jet
flames, missile contrails, radiation fog and the RBMK effects draw as solid black quads. Without a
pack they are fine. The mini-nuke flash and shockwave, the fire cloud of a tiny tot and the other
particles that go through vanilla's `ParticleManager` are lit correctly under the pack.

## Cause

NTM-CE's config option `1.25_instancedParticles` (default on; its own comment says it "will break
with shaders") routes those particles through `com.hbm.particle_instanced.InstancedParticleRenderer`:
instanced billboards drawn with HBM's own GLSL program, `hbm:shaders/lit_particles`, from Forge's
`RenderWorldLastEvent`. The program multiplies the particle texture by a lightmap sample taken from
texture unit 1.

Under a shader pack `RenderWorldLastEvent` fires after `EntityRendererIrisMixin.demonica$finalizeIrisWorld`
has finished the frame: no gbuffers program is bound, the pipeline's own textures sit on the units,
and the draw lands on the composited image with whatever unit 1 holds, which is not the lightmap.
The result is black. The non-instanced counterparts of the same particles (`ParticleExSmoke`,
`ParticleRocketFlame`, `ParticleContrail`, `ParticleRadiationFog`, `ParticleRBMK*`) are ordinary
`ParticleManager` particles, which the pipeline's particle program (`gbuffers_particles`, falling back to
`textured_lit`/`textured`) lights like any other.

NTM-CE knows about this for one renderer only: the mushroom cloud (`RenderTorex`) takes its
non-instanced path when `ShaderHelper.areShadersActive()`, a probe of OptiFine's `Shaders.shaderPackLoaded`
that is always false without OptiFine. Every other spawn site reads `GeneralConfig.instancedParticles`
alone.

Not a cause, checked on the way: the mushroom cloud itself is near-black with or without a pack. Its
aged cloudlets are a 0.15 grey multiplied by a lightmap sample of about 0.2 (`RenderTorex.writeCloudlet`:
colour floor 0.15, lightmap floor 48/255), in the instanced and the immediate path alike; that is the mod's
own look. GLSM runs HBM's instanced draw correctly: program, VAO, attribute locations, the cloudlet
texture on unit 0, the lightmap on unit 1 and the instance buffer contents were all verified at the draw.
The immediate-mode draws of NTM-CE's `NTMImmediate` (client arrays with the lightmap on unit 1's texcoord
array) render correctly under GLSM and under a pack.

## Fix

`HbmInstancedParticlesCompat` (`src/main/java/com/demonica/compat/hbm/`) drives HBM's flag:
`MixinGeneralConfig` captures the configured value when `GeneralConfig.loadFromConfig` returns, and
`MixinInstancedParticleRenderer` calls `sync()` at the head of HBM's client tick and render-last
handlers, which sets the live field (through `GeneralConfigAccessor`) to
`configured && !IrisApiV0Impl.isShaderPackInUse()`. The client tick precedes that tick's effect packets,
so the spawn sites in `HbmEffectNT` and `ClientProxy` see the synced value; the renderer reads it at
render-last. Turning the pack off restores the configured value (instanced particles queued while the
pack was on resume rendering); a config reload recaptures it. One log line per change:
`HBM instanced particles off (configured true, shader pack on)`.

With the flag off under a pack the mushroom cloud also takes HBM's non-instanced path, as it does
under OptiFine shaders, so `RenderTorex` needs no redirect of its own. `HbmShaderStateCompat` is the one
place both this and `HbmWeaponDepthCompat` read the pack state from.

## Scene

`scripts/hbm-nuke.txt` (copied into `run/client/scripts`), run with
`./gradlew runClient -PwithHbm=<NTM-CE jar> -PdevScript=@scripts/hbm-nuke.txt`. It spawns the effects
client-side with the harness's `hbmfx <HbmEffectNT> x y z [nbt]` step (`HbmEffectNT.summonParticle`,
the effect packet's own entry point): `Muke` (flash + wave), `TinyTot` (fire cloud), `Smoke_Cloud`
(the instanced smoke), and summons `hbm:entity_effect_torex` (the mushroom cloud); without a pack, under
Complementary Reimagined, under Complementary Unbound + Euphoria Patches, then without a pack again.
Before the fix `hbm-1-fx-20` and `hbm-2-fx-20` show the smoke black; after it, lit grey.

`-PwithHbm` takes a jar path because CurseForge's 2.6.1.0 crashes against the pinned Celeritas (its
`MixinRenderSectionManager` redirects camera reads in `RenderSectionManager.update`, which d94ec1e5 keeps
in `updateCameraPosition`); the GitHub CI build of the same version (`NTM-CE-1.12.2-2.6.1.0-java25.jar`,
master 635df451, the reporter's jar) targets `updateCameraPosition`. The dev client runs that jar
through `remapHbmForDev` (the Celeritas mixin-aware SRG-to-MCP remap, refmap dropped), because its
mixin bodies reach vanilla members by SRG name and Cleanroom's load-time remap leaves mixin bodies
alone. CTM, which NTM-CE requires, comes from curse.maven.

## Out of scope, seen on the way

- With HBM's structure and landmine generation on, the dev client died in the driver (`memcpy` inside
  `glDrawElements`) the first time a landmine's `WaveFrontObjectVAO` model drew, right after GLSM warned
  that the VAO was generated off the window context: HBM builds its OBJ VAOs during resource load, on the
  splash's shared context, like the Sky Block program of #42. The scene runs with
  `1.05_enableLandmineSpawn=false` and `15.00_enableStructures=false` in `run/client/config/hbm/hbm.cfg`.
  Not reported by the user (Windows); worth its own look.
- NTM-CE's own post effects (`1.23_enableShaders2`, off by default) were not tested with a pack.
