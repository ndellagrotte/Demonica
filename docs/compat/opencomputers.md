# OpenComputers: screen text under a shader pack

## Symptom

With a shader pack on (BSL 10.1.8 shows it; Complementary Reimagined r5.9.3 happened not to), the text on an
OpenComputers 1.8.10 screen was missing or misdrawn in the world, and the screen's GUI (right-click a screen
with a keyboard) was black. Without a pack both drew correctly.

## Cause

OpenComputers caches each screen's text in a display list (`TextBufferRenderCache.compileOrDraw`): when the
buffer changes it compiles the list with `GL_COMPILE_AND_EXECUTE`, otherwise it calls the list. The world pass
compiles it from the screen's block-entity renderer. Inside the list, `TextureFontRenderer.drawBuffer` disables
and re-enables `GL_TEXTURE_2D` around the background quads.

GLSM posts `TEXTURE_UNIT_STATE` for those calls, and Iris reacts by switching its pass
(`DeferredWorldRenderingPipeline.setInputs` → `matchPass` → `beginPass`): it binds the pass's program and
framebuffer, sets the viewport and the blend override. Under `GL_COMPILE_AND_EXECUTE` GLSM recorded those calls
into OpenComputers' list (`USE_PROGRAM`, `VIEWPORT`, `DISABLE(BLEND)`, `ACTIVE_TEXTURE`; visible with
`-Dangelica.logDisplayListCompilation=true`). Every call of the list then rebound the world pass's program and
viewport: in the GUI the text drew with the pack's gbuffers program into the world framebuffer, and in the world
it drew with whatever pass was current at compile time.

Upstream Angelica posts its hooks the same way.

## Fix

`DisplayListManager.postHook` posts every GLSM hook with recording paused, as `glEnd` already does for its live
draw. The listeners run against the live state, and nothing they do lands in the list. Replay runs the recorded
`ENABLE`/`DISABLE(TEXTURE_2D)` through `GLStateManager`, which posts the hook again, so Iris picks the pass for
the context the list is called in. `DisplayListHookRecordingTest` covers it.

## Checking it

`./gradlew runClient -PwithOpenComputers -PdevScript=@scripts/ocscreen.txt` loads OpenComputers, Scalar, Scalar
Legacy and Fugue (the Scala libraries Scalar nests are unpacked by `extractScalarLibraries`, since FML extracts
ContainedDeps only from the mods folder). The script builds a tier 3 computer booting OpenOS (it needs
`power.ignorePower=true` in run/client/config/opencomputers/settings.conf, copied from a full settings.conf:
OpenComputers rejects a partial one), and shoots the screen from the front, at an angle and in its GUI, without a
pack, under BSL and under Complementary. The harness steps `scmd`, `insert` and `use` were added for it.
