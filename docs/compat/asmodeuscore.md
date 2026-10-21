# AsmodeusCore: the nebula by day, a white sky at night

## Symptom

With Galacticraft 4.0.7 and AsmodeusCore 1.0.5 (Cleanroom 0.7.10, no shader pack), the overworld sky was wrong in
two ways: AsmodeusCore's Milky Way band and nebula images showed at full strength while the sky was still bright
(the reporter saw them by day; the harness catches them at dusk, when the star brightness is below 0.2), and at
midnight the whole sky dome drew opaque white. AsmodeusCore intends the images to fade in with the stars, over a
dark dome.

## Cause

AsmodeusCore replaces the overworld's sky renderer every client tick (`AsmodeusClientEvent.onTick`, config
`enableSkyOverworld`, on by default). Its `SkyProviderBase.render` sets the fixed-function current color with raw
`GL11.glColor*` calls, and three of them leave [0,1]:

| Draw | Color | GL | GLSM before the fix |
|---|---|---|---|
| sky dome: `glColor3f(skyColor - posY / 400)` then `glCallList(glSkyList)` | negative RGB at night (a near-black sky minus 0.16 at y=64) | clamps to black | opaque white |
| galaxy band: `glColor4f(1, 1, 1, starBrightness - 0.2F)` on six textured quads | negative from noon until the star brightness passes 0.2 after dusk | clamps to 0, invisible under additive blending | opaque white: the images at full strength |
| planet body: alpha `posY / 255 - 0.3F` | negative below y≈76 | invisible | opaque white |

Fixed-function GL stores the current color unclamped and clamps every component to [0,1] when it uses it. GLSM's
`changeColor` caches the raw values and never calls the driver; the FFP emulation shader reads them as the
`u_CurrentColor` uniform, and `Uniforms.sanitizeUniformColor` prepared that upload. It existed to catch the
(-1,-1,-1,-1) sentinel `GLStateManager.clearCurrentColor` (vanilla `GlStateManager.resetColor`) stores so that the
next `glColor*` bypasses the cache: that sentinel must become GL's default opaque white, or every draw after
`resetColor` (JourneyMap's map tiles were the case) goes transparent. But it took **any** negative component for the
sentinel and uploaded white for all three colors above.

Display lists are not involved: AsmodeusCore compiles its star and dome lists with `GL_COMPILE`, which GLSM records
without touching the cache and replays through `GLStateManager`. The Iris sky hooks are no-ops on the fixed-function
pipeline.

## Fix

`Uniforms.sanitizeUniformColor` maps only the full (-1,-1,-1,-1) sentinel to white and clamps every other component
to [0,1] (`Uniforms.clamp01`). The same normalization now covers the other two readers of the cached color:
`CompatUniformManager.uploadColorModulator` (a pack's `iris_ColorModulator`) and `ImmediateModeRecorder`, which packs
the color into captured vertices and wrapped a negative or over-range component into a garbage byte.
`UniformColorSanitizeTest` covers the sentinel, the two AsmodeusCore colors and the clamps.

## Repro

```
./gradlew runClient -PwithAsmodeusCore -PdevScript=@scripts/asmodeus-sky.txt -Dorg.gradle.jvmargs=-Xmx2G
```

`-PwithAsmodeusCore` loads Galacticraft 4.0.7, AsmodeusCore 1.0.5 and Fugue 0.25.5, the files of the reporter's Prism
instance. `scripts/asmodeus-sky.txt` (copy it into `run/client/scripts`) makes a creative world, freezes the daylight
cycle and the weather, and shoots the sky straight up and towards the horizon at noon, at dusk (12300) and at
midnight. Before the fix the dusk shots showed the Milky Way band at full strength in a bright blue sky and the
night shots a white dome; the noon shots were right either way, the band being additive on a sky too bright to show
it.
