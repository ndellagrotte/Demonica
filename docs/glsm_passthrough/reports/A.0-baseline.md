# Plan item A.0: baseline capture

## Status

Captured, with exceptions. The frames, logs and timings of V-compat, V-core, V-mods and V-perf were taken on 2026-10-21,
a second capture of every V-mods scene (the `mods-*-B` runs) and four retakes of three of them (`-C`, `-D`) on
2026-10-22, and their hashes are recorded here. Two parts of the verify criterion do not hold as written: five frames
have a same-code floor above 0.8 % (four animated compat frames and one F3 text frame) and two chunkanimator frames shot
mid-animation have none that can be used, and the V-core baseline records a core path that is already broken on `dev`
(finding 1). All eleven V-mods scenes have a same-code floor: for ocscreen, itemgui and hudcache-stellarcore, whose A
and B runs started at different spawn points, a retake that started at the spawn of one of them gives the pair (section
5.5). See "Verify verdict" below.

Deviations from the plan:

- Commit `6c3333f8`, not `ef1b8c73`: lane A branches from today's `dev`, so this is its merge base (section 1).
- `profile.txt` was amended to teleport to an absolute position; from A.0 on, the tiers' `profile.txt` means the
  amended script (section 2, finding 2).
- Every core run that produced frames ran with the FML splash off, because the core profile crashes in the splash
  (section 3.2, finding 1).
- `menu.txt` with Modern Splash crashed in the dev client and has no baseline (finding 3).
- `dh33.txt` ran without Distant Horizons, which `-PwithCompatMods` does not load (finding 4).
- V-mods: each scene was captured twice, not once, to give it a floor (A on 2026-10-21, B on 2026-10-22). The scripts
  were not amended, so three pairs started at different spawn points; those three scenes were retaken at the same commit
  (ocscreen and itemgui once, hudcache-stellarcore twice) until a run matched the spawn of A or B, and that matched pair
  is their floor (sections 3.3 and 5.5, finding 2).
- Five frames have a floor above 0.8 % (the two end-portal frames, the two I Like Vanilla frames and the hudcache F3
  frame); per-frame pass lines are proposed for them and for the fontpack menu frame (section 5.5).
- V-perf: one core run per pack, so core has no same-code perf pair (section 3.4), and no frame times, because fps
  sits at the 120 cap (section 6).

## 1. Header

| | |
|---|---|
| Date | 2026-10-21, captures 22:48-23:49 local (UTC-4); second V-mods capture (`mods-*-B`) 2026-10-22 00:16-00:27; retakes (`mods-*-C`, `-D`) 2026-10-22 00:39-00:49 |
| Commit | `6c3333f8` (`6c3333f82594020580c124f731793f98550f86e1`, branch `dev`; every A run's meta.txt records a clean tree, every `-B`, `-C` and `-D` run's one entry, the untracked `docs/glsm_passthrough/` that holds this report). The plan names `ef1b8c73` "or the merge base of the lane-A branch"; lane A branches from today's `dev`, so its merge base is this commit |
| Machine | Linux desktop, kernel 7.2.11-arch1-1, 30 GiB RAM, NVIDIA GeForce RTX 3080, driver 615.78.08 |
| GL_RENDERER | `NVIDIA GeForce RTX 3080/PCIe/SSE2` |
| GL_VERSION | `4.6.0 NVIDIA 615.78.08` on both profiles |
| Context lines | compat: `Created OpenGL compatibility profile context: configured=auto, requested=compatibility (debug=false), actual=4.6 (4.6.0 NVIDIA 615.78.08)`; core: `Created OpenGL core profile context: configured=core, requested=core (debug=false), actual=4.6 (4.6.0 NVIDIA 615.78.08)` followed by GLSM's `GL 3.3 core profile detected, enabling FFP shader emulation.` |
| Java | toolchain JavaLanguageVersion 25 (build.gradle); `java` on PATH openjdk 25.0.5 (2026-10-20); JAVA_HOME unset |
| Stack | Cleanroom 0.7.4-alpha (CleanroomGradle 0.22.0, lwjgly/SDL 3), Celeritas 2.5.0 (`celeritas@2.5.0-dev` in the mod list) |
| Sandbox | off for every client run (the sandbox hides the GPU) |
| FML splash | on (`run/client/config/splash.properties` `enabled=true`) for every compat run; off (`enabled=false`) for every core run that produced frames, because the core profile crashes in the splash with it on (finding 1). Restored to `enabled=true` afterwards. The `-B`, `-C` and `-D` runs record it in meta.txt (`splash: true`) |
| Game options | `run/client/options.txt` as kept in `baseline/options.txt.orig`: `renderDistance:8`, `maxFps:120`, `enableVsync:false`, `fboEnable:true`, `guiScale:2`; frames are 1280x720. dh33 and fontpack edited it for their run, in both captures (backups `options.txt.before-dh33`, `options.txt.before-fontpack`, and `-B` copies of both); it was restored each time and matches `options.txt.orig` |
| Packs | BSL v10.1.8, Complementary Reimagined r5.9.3, I Like Vanilla v1.4.4 (zips in `run/client/shaderpacks`) |

## 2. Method

**Helper.** Every non-perf run went through `<repo>/run/glsm-passthrough/capture-run.sh <run-name> <script> [gradle
args]` (sha256 `30a1881e...d456` for every run on 2026-10-21, `d4dd1ead...a9d3` for the `-B`, `-C` and `-D` runs,
after the splash line below was added; full hashes in the appendix). It refuses to start if a dev client is alive
(exit 3) or if the run directory is not empty (exit 4, `FORCE=1` wipes it), then runs
`./gradlew runClient -Dorg.gradle.jvmargs=-Xmx2G -PdevScript=@<absolute script> [args]` from `<repo>/` and writes to
`<repo>/run/glsm-passthrough/baseline/<run-name>/`:

- `client.out` (Gradle and client stdout), `latest.log` (copy of `run/client/logs/latest.log`, only if written during
  the run), `frames/*.png` (every screenshot newer than the run's start marker), `crash-*.txt` when the client crashed;
- `meta.txt`: script, Gradle arguments, start, end, duration, Gradle exit, HEAD, `git status` entry count, frame count,
  the context lines, the Celeritas-gate count (lines matching `N of N injectors found their targets`, and mismatches),
  the count of shader compile/link errors, every `Dev stats:` line, the last three GLSMPerfDebug lines and the frame
  list. From the second V-mods capture on (mods-ocscreen-B, 2026-10-22, its first run) it also records `splash:`, the
  `enabled=` value of `run/client/config/splash.properties` read before the launch (`absent` without the file); every
  `-B`, `-C` and `-D` run reads `splash: true`. The earlier runs' meta.txt has no such line: which of them had it off
  rests on the procedure of section 9 and on the run names (`*-nosplash-*`, and the perf core retakes of section 3.4);
- `SHA256SUMS` over `frames/*.png`, `latest.log` and `client.out`; on 2026-10-22 the crash reports, the perf runs'
  `perf-summary.txt`, `transform-times.txt` and `timing-stdout.txt`, and the StellarCore config copies were appended
  to the SHA256SUMS of their run directories, so every file the report cites is covered (appendix A.3 lists those
  additions, and `sha256sum -c` passes in every directory after the append).

V-perf runs went through `<repo>/run/glsm-passthrough/perf-run.sh <name> <tag> [gradle args]`, which wraps
`scripts/glsl-corpus/timing.sh` (that script adds `-PdevProps=demonica.glsmPerfDebug=true` itself), and writes
`perf-<name>-<tag>/` with the timing log, `latest.log`, `transform-times.txt`, `timing-stdout.txt`, the `corpus-*`
frames, `meta.txt`, `perf-summary.txt` (from `perf-summary.py`) and `SHA256SUMS`. The helper's SHA256SUMS covered the
frames, `latest.log` and the timing log; `perf-summary.txt`, `transform-times.txt` and `timing-stdout.txt`, which
section 6 reads, were appended to it on 2026-10-22 (appendix A.3).

Runs that failed and were retaken are kept under `baseline/_failed/<run>-attempt1/`. core-profile-A and
mods-menu-modernsplash crashed the same way on both attempts; perf-vanilla-a0-core-A attempt 1 crashed in the splash
with it on, and its retake with the splash off ran (section 3.4).

**Pixel difference.** `<repo>/run/glsm-passthrough/imgdiff.py <dirA> <dirB> [glob]` decodes each PNG pair with ffmpeg
to rgb24 and prints, per frame, the share of pixels where any channel differs by more than 16/255, and both mean
luminances. This is the Iris plan's method. A *same-code floor* is that share between two runs of the same commit and
script; for the sweep, which has three runs, it is the per-frame maximum over the three pairs.

**Layout.** `<repo>/run/glsm-passthrough/` (gitignored with the rest of `run/`):

```
capture-run.sh  perf-run.sh  perf-summary.py  imgdiff.py
baseline/
  environment.txt  floors.md  triage-core-profile.md  triage-modern-splash.md
  profile.txt (amended script of record)  profile.txt.orig
  options.txt.orig  options.txt.before-{dh33,fontpack}[-B]  splash.properties.orig  demonica-options.json.orig
  <run-name>/{client.out,latest.log,frames/,meta.txt,SHA256SUMS[,crash-*.txt]}   (mods: mods-<scene>[-B|-C|-D])
  perf-<name>-<tag>/{latest.log,timing-*.out,transform-times.txt,timing-stdout.txt,perf-summary.txt,frames/,meta.txt,SHA256SUMS}
  _failed/<run>-attempt1/
```

**How a later item compares.** Capture the item's run with the same helper and script under a new name, then, from
`<repo>/run/glsm-passthrough`:

```
python3 imgdiff.py baseline/compat-profile-A2/frames baseline/<item>-compat-profile/frames
python3 imgdiff.py baseline/compat-sweep-A/frames     baseline/<item>-compat-sweep/frames
python3 imgdiff.py baseline/core-profile-nosplash-A2/frames baseline/<item>-core-profile/frames
python3 imgdiff.py baseline/core-sweep-nosplash-A/frames    baseline/<item>-core-sweep/frames
python3 imgdiff.py baseline/mods-<scene>/frames   baseline/<item>-mods-<scene>/frames
python3 imgdiff.py baseline/mods-<scene>-B/frames baseline/<item>-mods-<scene>/frames
```

and repeat the first four against the B run (B2 for profile). A frame's result is the larger of its two shares, the
per-frame maximum over A and B, and it must sit at or under the frame's pass line (section 5.5). The runs of record are
compat-profile-A2/B2, compat-sweep-A/B/C, core-profile-nosplash-A2/B2, core-sweep-nosplash-A/B, the mods-* runs with
their mods-*-B partners (for ocscreen, itemgui and hudcache-stellarcore the matched pair of section 5.5 instead), and
perf-*-a0-compat-A/B and perf-*-a0-core-A. Before any comparison, `sha256sum -c SHA256SUMS` in the baseline run
directory confirms the reference is the one recorded here. Before comparing profile-0, check that the run's first `Dev
stats` line reads `terrain C: 170/3600`, as every run of record does; a lower count means the frame was shot before
terrain finished building (section 5.4). Before comparing a mods scene, check that the item run's first `Teleported
Developer to` line (for shaderpacks-scroll, the `logged in ... at` position) matches the baseline runs': the scripts
position relative to the spawn point, and a run that landed elsewhere is void and is retaken. For ocscreen, itemgui and
hudcache-stellarcore, whose A and B runs started at different spawns, compare with the matched pair instead
(mods-ocscreen and mods-ocscreen-C, mods-itemgui-B and mods-itemgui-C, mods-hudcache-stellarcore and
mods-hudcache-stellarcore-D, all at -164.5, 76.0, 218.5; section 5.5).

For V-perf, run `<repo>/run/glsm-passthrough/perf-run.sh <vanilla|bsl> <item-tag> [args]` and compare its
`perf-summary.txt` with the baseline's stage by stage: the count per line of each emulation stage, which the
passthrough must drive to zero on compat, and the order of magnitude of the µs columns (section 6). The per-scene
setup a run needs is in section 9: the splash off for core, StellarCore's first-launch config and then
`B:HudCaching=true`, `resourcePacks` for fontpack, and `renderDistance:4` for dh33.

**The profile.txt amendment (finding 2).** In the first pass, two identical compat runs of `profile.txt` differed on
27.8-62.6 % of pixels: vanilla's random spawn offset put run B at -160.5, 99.8, 221.5 instead of -164.5, 100.8, 218.5,
and the script teleported relative to the player. The teleport now goes to the absolute position the sweep and corpus
scripts already use:

```
--- glsm-passthrough/baseline/profile.txt.orig	2026-09-28 12:46:42.822484924 -0400
+++ client/scripts/profile.txt	2026-10-21 23:35:52.678507715 -0400
@@ -10,7 +10,7 @@
 cmd /weather clear
 cmd /gamemode spectator
 wait 5
-cmd /tp @p ~ ~25 ~ 225 15
+cmd /tp @p -164.5 100.82940159667969 218.5 225 15
 chunks 1200
 wait 60
 stats
```

No other line changed. Every run of the amended script logs `Teleported Developer to -164.5, 100.82940159667969,
218.5`, then `-164.5, 103.829..., 218.5` (portal) and back, and the compat floor fell to 0.001-1.788 %.
`run/client/scripts` is gitignored, so the amended file is kept byte-identical (`cmp` clean) as `baseline/profile.txt`,
the script of record (sha256 `be0f1d20...4127`), with the original as `baseline/profile.txt.orig` (`a6458726...beaa`).
From A.0 on, the plan's references to `profile.txt` (V-oracle, V-compat, V-core) mean the amended script. The V-mods
scripts that position the camera relative to the spawn point were not amended (section 5.5).

**Dev-classpath noise** present in every run, compat and core, and not counted as an error: six CleanMix `Unable to
locate resource for mixin config source` warnings (`mixins.demonica.core`, `.iris`, `.celeritas`, `.kirino`,
`.hbm.early` and `.diagnostics`), each followed by a `java.io.IOException: Mixin config does not reside in a jar file`
(thirteen of each in the `-PwithCompatMods` runs, which add the scannable, ichunutil, littletiles, renderlib,
obscuretooltips, extrautils2 and botania configs); FML's `Apache Maven library folder was not in the format expected`
ERROR with its `Full:` and `Trimmed:` lines; CleanMix's `Invalid REFMAP JSON` ERROR; and the narrator's `Couldn't load
Narrator library : libflite.so.1` warning. The `-PwithCompatMods` runs also log FML's texture-error report for one
missing Extra Utilities 2 texture (`textures/potions/purging.png`). Of the packs, Complementary logs `Failed to resolve
uniform inSulfurCaves ... Unknown variable: BIOME_SULFUR_CAVES` once per load, and I Like Vanilla logs `Failed to
resolve uniform pi, reason: Variable shadows build in uniform: pi` (with a stack trace) once per load: two of each per
sweep, the same in every sweep, compat and core (pack-side).

## 3. Runs

Every run's command is `./gradlew runClient -Dorg.gradle.jvmargs=-Xmx2G -PdevScript=@<script>` plus the arguments in
the "Gradle args" column. Scripts are written relative to `<repo>/`. "fps" lists the `Dev stats:` lines in order;
every value is at or just under the `maxFps:120` cap (section 6). "Gate" is the Celeritas-gate count from meta.txt
(`n of n` lines / mismatched). "Captured" means Gradle exit 0 with the scripted frames written; scene content was
looked at only where the notes say so.

### 3.1 V-compat

| Run | Script | Gradle args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| compat-profile-A2 | `run/client/scripts/profile.txt` (amended) | none | compat, 4.6 | 4: profile-0-nopack, profile-1-portal-nopack, profile-2-portal-bsl, profile-3-bsl | 120, 120 | pass, run of record | 36 s; gate 23/0; BSL Load #1 620.4 ms; 0 compile/link errors; 0 GL ERROR lines |
| compat-profile-B2 | same | none | compat, 4.6 | 4, same names | 120, 120 | pass, floor partner | 33 s; same three teleports as A2; gate 23/0 |
| compat-sweep-A | `scripts/glsl-corpus/sweep.txt` | `-PdevProps=demonica.glsmPerfDebug=true` | compat, 4.6 | 8: sweep-0-nopack, sweep-1-bsl, sweep-2-complementary, sweep-3-ilikevanilla, sweep-4-off, sweep-5-bsl-cached, sweep-6-complementary-cached, sweep-7-ilikevanilla-cached | 120 x6 | pass, run of record | 81 s; gate 23/0; three packs load, 0 compile/link errors |
| compat-sweep-B | same | same | compat, 4.6 | 8, same names | 120 x6 | pass | 71 s; gate 23/0 |
| compat-sweep-C | same | same | compat, 4.6 | 8, same names | 120, 120, 120, 119, 120, 120 | pass | 71 s; taken in the second pass to put a third sample on I Like Vanilla; BSL 596.7 ms, Complementary 1684.7 ms, I Like Vanilla 747.6 ms |
| compat-profile-A | `profile.txt.orig` | none | compat, 4.6 | 4, same names | 120, 120 | superseded (spawn drift) | first teleport -164.5, 100.83, 218.5 |
| compat-profile-B | `profile.txt.orig` | none | compat, 4.6 | 4, same names | 120, 120 | superseded (spawn drift) | first teleport -160.5, 99.83, 221.5; first stats C: 184/3600 against A's 170 |

### 3.2 V-core

All core runs pass `-PdevProps=demonica.openglProfile=core` and get a real core context (profile mask checked, see
the header). "splash off" means `splash.properties` `enabled=false` for that run.

| Run | Script | Gradle args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| core-profile-nosplash-A2 | `profile.txt` (amended) | core; splash off | core, 4.6 | 4: profile-0-nopack, profile-1-portal-nopack, profile-2-portal-bsl, profile-3-bsl | 120, 120 | captured, run of record; **broken output** | 33 s; gate 23/0; BSL 677.2 ms, 0 compile/link errors; `GL ERROR 1282` at Pre startup and Post startup; terrain untextured, no clouds without a pack (finding 1) |
| core-profile-nosplash-B2 | same | same | core, 4.6 | 4, same names | 120, 120 | captured, floor partner; broken output | 32 s; same teleports; same two 1282s; mean luminance equal to A2 on every frame |
| core-sweep-nosplash-A | `scripts/glsl-corpus/sweep.txt` | `-PdevProps=demonica.openglProfile=core,demonica.glsmPerfDebug=true`; splash off | core, 4.6 | 8, sweep names as in 3.1 | 120, 120, 120, 119, 120, 120 | captured, run of record; broken output | 75 s; gate 23/0; two 1282s |
| core-sweep-nosplash-B | same | same | core, 4.6 | 8 | 120, 120, 120, 120, 119, 120 | captured, floor partner; broken output | 70 s; gate 23/0; two 1282s |
| core-profile-nosplash-A | `profile.txt.orig` | core; splash off | core, 4.6 | 4 | 119, 120 | superseded | first stats C: 80/3600 (B: 170): frame 0 was shot before terrain finished building; both runs teleported to the same place |
| core-profile-nosplash-B | `profile.txt.orig` | core; splash off | core, 4.6 | 4 | 120, 120 | superseded | |
| core-profile-A | `profile.txt.orig` | core; splash on | core, 4.6 | 0 | none | failed (crash) | 7 s, Gradle exit 1; `IllegalStateException: Splash thread` caused by `Texture creation: Invalid operation` at `SplashProgress$Texture.<init>`; `crash-2026-10-21_22.55.29-client.txt`; gate 2/0. Attempt 1 (`_failed/`) identical |
| core-profile-B | `profile.txt.orig` | core; splash on | core, 4.6 | 0 | none | failed (crash) | same crash, `crash-2026-10-21_22.56.09-client.txt` |
| core-sweep-A | `sweep.txt` | core + glsmPerfDebug; splash on | core, 4.6 | 0 | none | failed (crash) | same crash, `crash-2026-10-21_22.55.55-client.txt` |
| core-sweep-B | `sweep.txt` | same | core, 4.6 | 0 | none | failed (crash) | same crash, `crash-2026-10-21_22.56.16-client.txt` |

The plan asks for the core run twice to give it its own floor: core-profile-nosplash-A2/B2 and core-sweep-nosplash-A/B
are those pairs.

### 3.3 V-mods

All on the compatibility profile, FML splash on. The plan asks for one capture each; each scene was captured a second
time on 2026-10-22 (the `-B` runs below: same commit, scripts, Gradle arguments and per-scene setup, one attempt each)
so that it has a same-code floor. Seven of the scripts position the camera relative to the spawn point, and for three
scenes (ocscreen, itemgui, hudcache-stellarcore) the spawn moved between A and B; those three were retaken (the `-C` and
`-D` runs below) until a run started at the spawn of A or B, and the matched pair is their floor (section 5.5). The
`-PwithCompatMods` runs also load Extra Utilities 2 and Botania, which no frame exercises (finding 6).

First capture (A), 2026-10-21:

| Run | Script | Gradle args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| mods-ocscreen | `run/client/scripts/ocscreen.txt` | `-PwithOpenComputers` | compat | 9: ocscreen-0-world, -1-angle, -2-gui, -3-bsl-world, -4-bsl-angle, -5-bsl-gui, -6-compl-world, -7-compl-angle, -8-compl-gui | none logged | captured, run of record (pair with mods-ocscreen-C) | 60 s; gate 23/0; frame 3: screen text visible under BSL |
| mods-asmodeus-sky | `scripts/asmodeus-sky.txt` | `-PwithAsmodeusCore` | compat | 6: asmodeus-sky-{day,dusk,night}-{horizon,up} | none logged | captured | 28 s; dusk horizon sky is blue, not white |
| mods-itemgui | `run/client/scripts/itemgui.txt` | none | compat | 2: itemgui-0-world, itemgui-1-inventory | none logged | captured; **not a floor** | 19 s; block items in the creative inventory are 3D; spawn -162.5, 76.0, 219.5, matched by neither B nor C, so B and C are the scene's pair (section 5.5) |
| mods-hudcache-stellarcore-prime | `run/client/scripts/hudcache.txt` | `-PwithHudCache=stellarcore` | compat | 7: hudcache-00-vanilla-a, -01-vanilla-b, -02-vanilla-c, -03-f3-vanilla, -04-bsl-a, -05-bsl-b, -06-bsl-c | none logged | captured, informational | first launch with StellarCore; it writes `stellar_core.cfg` with HUD caching at its default (off) |
| mods-hudcache-stellarcore | same | `-PwithHudCache=stellarcore` | compat | 7, same names | none logged | captured, run of record (pair with mods-hudcache-stellarcore-D) | `B:HudCaching=true`, `I:HudCachingFPSLimit=20` (copy of the config in the run directory) |
| mods-hudcache-gnetum | same | `-PwithHudCache=gnetum` | compat | 7, same names | none logged | captured | 32 s; vanilla-a/b/c byte-identical |
| mods-fontpack | `run/client/scripts/fontpack.txt` | none; options.txt `resourcePacks` set to `demonica-custom-font` for the run | compat | 2: fontpack-0-menu, fontpack-1-reload | none logged | captured | 38 s; gate 11/0 (menu scene, no world, so fewer Celeritas classes load); `kirino-font-cache.before` kept |
| mods-dh33 | `run/client/scripts/dh33.txt` | `-PwithCompatMods`; `renderDistance:4` for the run | compat | 6: dh33-0-nopack, -1-compl, -2-compl, -3-bsl, -4-bsl, -5-bsl-low | 120, 120, 120 | captured **without Distant Horizons** | 209 s; no LODs: DH is not in `-PwithCompatMods` (finding 4) |
| mods-chunkanimator | `scripts/chunkanimator.txt` | `-PwithCompatMods` | compat | 4: chunkanimator-after, -anim-a, -anim-b, -settled | 118, 120 | captured | 26 s |
| mods-renderlib | `scripts/renderlib.txt` | `-PwithCompatMods -PdevProps=demonica.debug.depthFunc=true` | compat | 2: renderlib-first, renderlib-third | 120 | captured | 23 s |
| mods-shaderpacks-scroll | `run/client/scripts/shaderpacks-scroll.txt` | `-PwithUniversalTweaks` | compat | 2: shaderpacks-scroll-drag, shaderpacks-scroll-wheel | none logged | captured | 20 s |
| mods-menu-modernsplash | `run/client/scripts/menu.txt` | `-PwithModernSplash` | compat | 0 | none | failed (crash) | 5 s, Gradle exit 1; `NoSuchMethodError: Minecraft.func_71410_x()` from Modern Splash's injected SplashProgress (finding 3); attempt 1 identical |

Second capture (B), 2026-10-22 00:16-00:27, one run per A run except mods-menu-modernsplash. Every B run: Gradle exit
0, the scripted frames written, the compat 4.6 context line, 0 shader compile/link errors, `splash: true` in meta.txt.
"Spawn" compares the first `Teleported Developer to` line (or the login position) with the A run's.

| Run | Script | Gradle args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| mods-ocscreen-B | as mods-ocscreen | `-PwithOpenComputers` | compat | 9, same names | none logged | captured; **not a floor** | 64 s; gate 23/0; spawn **moved**: first teleport -160.5, 75.0, 221.5 (A: -164.5, 76.0, 218.5), so the scene is built and shot 4 blocks east, 3 south and 1 lower |
| mods-asmodeus-sky-B | as mods-asmodeus-sky | `-PwithAsmodeusCore` | compat | 6, same names | none logged | captured, floor partner | 28 s; gate 23/0; spawn matched (-164.5, 76.0, 218.5) |
| mods-itemgui-B | as mods-itemgui | none | compat | 2, same names | none logged | captured, floor partner of mods-itemgui-C | 20 s; gate 23/0; spawn **moved** against A: -164.5, 76.0, 218.5 (A: -162.5, 76.0, 219.5); C matched it |
| mods-hudcache-stellarcore-prime-B | as A | `-PwithHudCache=stellarcore` | compat | 7, same names | none logged | captured, floor partner (informational configuration) | 32 s; gate 23/0; `stellar_core.cfg` absent before the run, written by it with `B:HudCaching=false`, otherwise identical to A's; spawn matched (-164.5, 76.0, 218.5) |
| mods-hudcache-stellarcore-B | as A | `-PwithHudCache=stellarcore` | compat | 7, same names | none logged | captured; **not a floor** | 33 s; gate 23/0; `B:HudCaching=true` set as for A (config `cmp`-identical to A's copy, kept in the run directory); log `StellarCore HUD caching bridged: mirrored 2 renderingCacheOverride stores and restored the HUD baseline before 1 cached HUD render(s)`; spawn **moved**: -160.5, 75.0, 221.5 (A: -164.5, 76.0, 218.5) |
| mods-hudcache-gnetum-B | as A | `-PwithHudCache=gnetum` | compat | 7, same names | none logged | captured, floor partner | 32 s; gate 23/0; log `Gnetum HUD caching bridged: mirrored 2 Gnetum.rendering stores`; vanilla-a/b/c byte-identical again; spawn matched (-164.5, 76.0, 218.5) |
| mods-fontpack-B | as mods-fontpack | none; `resourcePacks` as for A | compat | 2, same names | none logged | captured, floor partner | 33 s; gate 11/0; no world; the Kirino font cache was moved to `kirino-font-cache.before/` in the run directory first, and the run regenerated it (`Refreshed McTTF font. Time taken: 9773ms`) |
| mods-dh33-B | as mods-dh33 | `-PwithCompatMods`; `renderDistance:4` for the run | compat | 6, same names | 120, 120, 120 | captured **without Distant Horizons**, floor partner | 206 s; gate 23/0; spawn matched (first teleport -165.5, 116.83, 216.5); the three `Dev stats` lines have A's terrain counts (`C: 22/784`, `22/784`, `36/784`) |
| mods-chunkanimator-B | as mods-chunkanimator | `-PwithCompatMods` | compat | 4, same names | 119, 120 | captured, floor partner (anim frames excluded) | 25 s; gate 23/0; spawn matched (-165.5, 77.0, 216.5); `after` and `settled` byte-identical to A's |
| mods-renderlib-B | as mods-renderlib | `-PwithCompatMods -PdevProps=demonica.debug.depthFunc=true` | compat | 2, same names | 120 | captured, floor partner | 22 s; gate 23/0; spawn matched (-165.5, 77.0, 216.5) |
| mods-shaderpacks-scroll-B | as mods-shaderpacks-scroll | `-PwithUniversalTweaks` | compat | 2, same names | none logged | captured, floor partner | 19 s; gate 23/0; no teleport, login at -164.5, 76.0, 218.5 as in A; both `expect-list-scroll` checks passed (30 px, then 258 px) |

Retakes (C, D), 2026-10-22 00:39-00:49, for the three scenes whose spawn moved: same commit, helper (`d4dd1ead...a9d3`),
scripts, Gradle arguments and per-scene setup as A and B. Each scene was run again, suffix `-C`, then `-D`, until a
run's first `Teleported Developer to` line matched the A or the B run's. Every retake: Gradle exit 0, the scripted
frames written, the compat 4.6 context line, gate 23/0, 0 shader compile/link errors, `splash: true` in meta.txt; the
world the script created was deleted after each. Before each hudcache retake `run/client/config/stellar_core.cfg` was
written as a copy of `mods-hudcache-stellarcore-B/config/stellar_core.cfg` (`B:HudCaching=true`,
`I:HudCachingFPSLimit=20`); it was `cmp`-identical after the run, its copy is kept in the run directory and in its
SHA256SUMS, and it was deleted from `run/client/config` after the last retake.

| Run | Script | Gradle args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| mods-ocscreen-C | as mods-ocscreen | `-PwithOpenComputers` | compat | 9, same names | none logged | captured, floor partner of mods-ocscreen | 59 s; spawn matched A (-164.5, 76.0, 218.5), and all eight teleports are A's; the three gui frames show a different OpenOS boot tip than A's (section 5.5) |
| mods-itemgui-C | as mods-itemgui | none | compat | 2, same names | none logged | captured, floor partner of mods-itemgui-B | 19 s; spawn matched B (-164.5, 76.0, 218.5), not A |
| mods-hudcache-stellarcore-C | as A | `-PwithHudCache=stellarcore` | compat | 7, same names | none logged | captured; **not a floor** | 38 s; config as above; log `StellarCore HUD caching bridged: mirrored 2 renderingCacheOverride stores and restored the HUD baseline before 1 cached HUD render(s)`; spawn **moved**, to a third one: -160.5, 76.0, 219.5 (A: -164.5, 76.0, 218.5; B: -160.5, 75.0, 221.5) |
| mods-hudcache-stellarcore-D | as A | `-PwithHudCache=stellarcore` | compat | 7, same names | none logged | captured, floor partner of mods-hudcache-stellarcore | 32 s; config as above; the same `StellarCore HUD caching bridged` line; spawn matched A (-164.5, 76.0, 218.5); vanilla-a/b/c byte-identical |

### 3.4 V-perf

Command: `scripts/glsl-corpus/timing.sh <pack> <tag> -Dorg.gradle.jvmargs=-Xmx2G [args]` through `perf-run.sh`;
timing.sh adds `-PdevProps=demonica.glsmPerfDebug=true`. `vanilla.txt` shoots no pack, then I Like Vanilla;
`bsl.txt` shoots no pack, BSL, the Nether and the Overworld again.

| Run | Script | Extra args | Profile | Frames | fps | Result | Notes |
|---|---|---|---|---|---|---|---|
| perf-vanilla-a0-compat-A | `scripts/glsl-corpus/vanilla.txt` | none | compat | 2: corpus-vanilla-0-nopack, corpus-vanilla-1-pack | 120 | pass | 33 s; gate 23/0; 19 GLSMPerfDebug lines |
| perf-vanilla-a0-compat-B | same | none | compat | 2 | 120 | pass | 29 s; 19 lines |
| perf-vanilla-a0-core-A | same | `-PdevProps=demonica.glsmPerfDebug=true,demonica.openglProfile=core`; splash off | core | 2 | 120 | captured, broken output | 31 s; 17 lines; two 1282s. Attempt 1 with the splash on crashed (`_failed/`) |
| perf-bsl-a0-compat-A | `scripts/glsl-corpus/bsl.txt` | none | compat | 4: corpus-bsl-0-nopack, -1-pack, -2-nether, -3-overworld | 120, 120, 120 | pass | 51 s; 41 lines |
| perf-bsl-a0-compat-B | same | none | compat | 4 | 120, 120, 120 | pass | 50 s; 42 lines |
| perf-bsl-a0-core-A | same | as core above; splash off | core | 4 | 120, 120, 120 | captured, broken output | 51 s; 39 lines; two 1282s |

The plan asks for "core before and after"; one core run per pack was taken, since core frames and counters are not
expected to move and the core path is broken (finding 1).

## 4. Hashes

The appendix lists, for every run directory, the sha256 of each frame and of `latest.log`, plus the other files its
SHA256SUMS covers (`client.out` for capture-run.sh runs, the timing log for perf runs, and the crash report for
mods-menu-modernsplash only). The tables were generated from the SHA256SUMS files, not retyped. `sha256sum -c --quiet
SHA256SUMS` passed in all 35 run directories and in the three `_failed/*-attempt1` directories at
2026-10-21T23:52:11-04:00, again at 2026-10-21T23:59:07-04:00, after this report's tables were generated, and again on
2026-10-22 during this report's fix round. The eleven `mods-*-B` directories came later: `sha256sum -c --quiet
SHA256SUMS` passed in each of them at 2026-10-22T00:31:37-04:00, and their tables in the appendix were generated from
those files then. The four retake directories (mods-ocscreen-C, mods-itemgui-C, mods-hudcache-stellarcore-C and -D) came
last: `sha256sum -c --quiet SHA256SUMS` passed in each of them, and in all 53 directories that hold a SHA256SUMS, at
2026-10-22T00:52:20-04:00, and their appendix tables were generated from those files then. Each frame is covered: the
frame count in SHA256SUMS equals the number of PNGs in `frames/` for every run, A, B and the retakes.

Some files the report cites were not in the helpers' SHA256SUMS: the seven other crash reports (core-profile-A/B,
core-sweep-A/B and the three `_failed/` attempts), the perf runs' `perf-summary.txt`, `transform-times.txt` and
`timing-stdout.txt`, and the StellarCore config copies in mods-hudcache-stellarcore and mods-hudcache-stellarcore-B.
Their hashes were appended to the SHA256SUMS of their run directories on 2026-10-22 (32 entries; `sha256sum -c`
passes in all 49 directories afterwards), and appendix A.3 lists them.
Those hashes were taken on 2026-10-22 from the files as they stand; the files' mtimes are their capture times
(22:55-23:29 on 2026-10-21, and 00:20 on 2026-10-22 for the B config copy), so they bind the content the report read,
but no SHA256SUMS written at capture time vouches for them. The Kirino font cache copies in mods-fontpack and
mods-fontpack-B (`kirino-font-cache.before/`, about 20 MB a file) are kept but not hashed.

The scripts the runs used, the helpers, and the script hashes are in appendix A.0: `run/client/scripts/` and
`run/glsm-passthrough/` are not in the repository, so the hashes are the only record of the exact inputs.

## 5. Floors

Share of pixels with a channel differing by more than 16/255. Commands as in section 2, from
`<repo>/run/glsm-passthrough`. The full per-pair output, with mean luminances, is `baseline/floors.md`.

### 5.1 Compat, profile.txt (A2 vs B2)

| Frame | Floor % | Mean luminance A2 / B2 |
|---|---|---|
| profile-0-nopack | 0.001 | 121.9 / 121.9 |
| profile-1-portal-nopack | **1.068** | 66.1 / 66.0 |
| profile-2-portal-bsl | **1.788** | 63.9 / 64.1 |
| profile-3-bsl | 0.019 | 116.9 / 116.9 |

### 5.2 Compat, sweep.txt (three runs)

| Frame | A/B | A/C | B/C | Floor % (max) |
|---|---|---|---|---|
| sweep-0-nopack | 0.002 | 0.002 | 0.000 | 0.002 |
| sweep-1-bsl | 0.051 | 0.048 | 0.012 | 0.051 |
| sweep-2-complementary | 0.030 | 0.025 | 0.002 | 0.030 |
| sweep-3-ilikevanilla | 1.418 | 1.819 | 0.379 | **1.819** |
| sweep-4-off | 0.004 | 0.004 | 0.000 | 0.004 |
| sweep-5-bsl-cached | 0.042 | 0.070 | 0.038 | 0.070 |
| sweep-6-complementary-cached | 0.030 | 0.006 | 0.044 | 0.044 |
| sweep-7-ilikevanilla-cached | 0.219 | 0.487 | 0.945 | **0.945** |

Mean luminances agree to 0.1 on every frame and pair.

### 5.3 Perf, compat A vs B

| Frame | Floor % |
|---|---|
| corpus-vanilla-0-nopack | 0.015 |
| corpus-vanilla-1-pack (I Like Vanilla) | 0.317 |
| corpus-bsl-0-nopack | 0.016 |
| corpus-bsl-1-pack | 0.091 |
| corpus-bsl-2-nether | 0.580 |
| corpus-bsl-3-overworld | 0.130 |

### 5.4 Core (broken path), same-code

| Frame | core-profile-nosplash A2/B2 % | | Frame | core-sweep-nosplash A/B % |
|---|---|---|---|---|
| profile-0-nopack | 0.000 | | sweep-0-nopack | 0.001 |
| profile-1-portal-nopack | **0.992** | | sweep-1-bsl | 0.097 |
| profile-2-portal-bsl | **1.110** | | sweep-2-complementary | 0.085 |
| profile-3-bsl | 0.031 | | sweep-3-ilikevanilla | 0.601 |
| | | | sweep-4-off | 0.001 |
| | | | sweep-5-bsl-cached | 0.021 |
| | | | sweep-6-complementary-cached | 0.023 |
| | | | sweep-7-ilikevanilla-cached | 0.566 |

The core output is as repeatable as compat's: the same animated frames carry the same kind of floor. It is the floor
of a wrong picture, see finding 1.

**Informational, cross-profile** (compat A vs core A of the same script; not a floor): profile.txt 47.3-94.9 %
(profile-0 mean luminance 121.9 compat vs 150.5 core), sweep 52.0-73.1 %, perf vanilla 57.8 / 73.0 %, perf BSL
24.2-57.8 %. These numbers are the size of the core breakage, not a profile difference the passthrough should expect.

**Informational, HUD cache:** stellarcore-prime (cache off) vs stellarcore (cache on) 0.178-0.786 %; stellarcore vs
gnetum 4.4-14.9 % (two different mods and HUD layouts; not comparable as a floor). From the second capture:
stellarcore-prime-B vs stellarcore (A), which share a spawn, 0.177-0.826 % (F3 frame 0.826 %), the A pair's picture
again; the B-only pairs (stellarcore-prime-B vs stellarcore-B 27.1-52.7 %, stellarcore-B vs gnetum-B 30.0-54.1 %)
measure stellarcore-B's moved spawn, not the cache. From the retakes (measured for this report with imgdiff.py, not in
`floors.md`): stellarcore-prime and stellarcore-prime-B vs stellarcore-D, which share a spawn, 0.163-0.945 % (F3 frame
0.945 / 0.262 %), the A pair's picture again; stellarcore-D vs gnetum 4.6-15.3 %.

**Superseded pairs, for the record:** compat-profile-A/B 27.8-62.6 % (spawn drift, finding 2); core-profile-nosplash
A/B 0.008-81.9 %. The second pair is not spawn drift: both runs teleported to -164.5, 100.83, 218.5. The 81.9 % on
profile-0 comes from run A shooting before terrain finished building (its first `Dev stats` line reads `C: 80/3600`,
run B's `C: 170/3600`). The amendment changed only the `/tp` line, so it does not prevent this; the retake simply shot
with terrain built. Hence the `Dev stats` check in section 2.

### 5.5 Exceptions, the criterion, and the pass lines for later items

The plan's criterion is "floor numbers inside the Iris plan's experience (0.0-0.8 % on animated frames)". The Iris
plan's sweeps put I Like Vanilla's same-code floor at 0.80 % from a single pair.

- **Holds** for every static frame and every perf frame: compat nopack/off 0.000-0.004 %, BSL 0.012-0.070 %,
  Complementary 0.002-0.044 %, profile-0 and profile-3 0.001 / 0.019 %, perf 0.015-0.580 % (the 0.580 % on the BSL
  Nether frame is the largest inside the limit). It also holds for every frame of the eleven mods pairs except the three
  named below: 0.000-0.434 %, plus the F3 frames of hudcache-gnetum (0.539 %) and hudcache-stellarcore (0.743 %) and
  fontpack at 0.496 / 0.719 %.
- **Does not hold** on five frames, four animated compat frames and one mods text frame:
  - profile-1-portal-nopack 1.068 % and profile-2-portal-bsl 1.788 %: both frames contain the 3x3 end portal the script
    fills, drawn by Demonica's `EndPortalRenderer` (`src/main/java/com/demonica/render/EndPortalRenderer.java`, which
    animates by `Minecraft.getSystemTime()`, line 63; the script's header comment calls it GLSM's replacement, but
    nothing under `glsm/` draws it), whose surface scrolls with time; the shot lands at an uncontrolled phase. The
    frames without the portal (0 and 3) stay at 0.001 / 0.019 %, so the drift is gone and the remainder is the
    animation. Core shows the same (0.992 / 1.110 %).
  - sweep-3-ilikevanilla 1.819 % and sweep-7-ilikevanilla-cached 0.945 %: I Like Vanilla waves foliage and water in
    its own shaders. The three pairs range 0.379-1.819 % and 0.219-0.945 %, so one pair (as in the Iris plan) can land
    anywhere in that range; 0.80 % was one such draw.
  - hudcache-03-f3-vanilla 0.924 % in the stellarcore-prime pair (0.539 % in the gnetum pair, 0.743 % in the stellarcore
    pair A/D): the F3 screen's counters (fps, frame time, memory) change from run to run. The changed pixels lie in the
    top 505 rows, where the F3 text is (bounding box x 4-1263, y 20-505; gnetum x 28-1263, y 22-505), none in the bottom
    quarter, and the mean luminance agrees within 0.1.
- **Has no usable floor** on two mods frames: chunkanimator-anim-a 19.965 % and -anim-b 35.733 % are shot
  mid-animation, and the capture instant decides how far the chunks have risen (anim-a mean luminance 168.4 in A,
  187.5 in B). The script's `after` and `settled` frames are byte-identical between A and B.
- **Core**: its floors are in line with compat's (only the portal frames exceed 0.8 %), but it is not a usable V-core
  floor in the plan's sense, because the picture it pins is wrong (finding 1).
- **Mods: measured for all eleven pairs** (A against B, or the matched pair for the three retaken scenes,
  `baseline/floors.md` section 6):

  | Scene | Spawn of the pair (A and B unless named) | Floor % per frame | Max % | Mean luminance of the pair |
  |---|---|---|---|---|
  | asmodeus-sky | matched, -164.5, 76.0, 218.5 | day-horizon 0.052, day-up 0.000, dusk-horizon 0.054, dusk-up 0.002, night-horizon 0.033, night-up 0.001 | 0.054 | identical |
  | hudcache-stellarcore-prime | matched, -164.5, 76.0, 218.5 | 00 0.026, 01 0.028, 02 0.034, 03-f3 **0.924**, 04-bsl 0.085, 05 0.222, 06 0.187 | 0.924 | within 0.1 |
  | hudcache-gnetum | matched, -164.5, 76.0, 218.5 | 00/01/02 0.006, 03-f3 0.539, 04-bsl 0.092, 05 0.209, 06 0.298 | 0.539 | within 0.1 |
  | fontpack | no world | 0-menu 0.719, 1-reload 0.496 | 0.719 | within 0.2 |
  | dh33 | matched, -165.5, 116.83, 216.5 | 0-nopack 0.007, 1-compl 0.002, 2-compl 0.014, 3-bsl 0.022, 4-bsl 0.005, 5-bsl-low 0.078 | 0.078 | identical |
  | chunkanimator | matched, -165.5, 77.0, 216.5 | after 0.000, anim-a **19.965**, anim-b **35.733**, settled 0.000 | 35.733 (anim) | anim-a 168.4 / 187.5, anim-b 124.1 / 124.6, others identical |
  | renderlib | matched, -165.5, 77.0, 216.5 | first 0.000, third 0.003 | 0.003 | identical |
  | shaderpacks-scroll | matched (login), -164.5, 76.0, 218.5 | drag 0.043, wheel 0.043 | 0.043 | identical |
  | ocscreen | A and C matched, -164.5, 76.0, 218.5 (B moved: -160.5, 75.0, 221.5) | 0-world 0.019, 1-angle 0.003, 2-gui 0.421, 3-bsl-world 0.256, 4-bsl-angle 0.099, 5-bsl-gui 0.434, 6-compl-world 0.183, 7-compl-angle 0.064, 8-compl-gui 0.425 | 0.434 | identical, except the gui frames: 0.7-0.8 lower in C (the OpenOS tip line) |
  | itemgui | B and C matched, -164.5, 76.0, 218.5 (A moved: -162.5, 76.0, 219.5) | 0-world 0.094, 1-inventory 0.006 | 0.094 | within 0.1 |
  | hudcache-stellarcore | A and D matched, -164.5, 76.0, 218.5 (B moved: -160.5, 75.0, 221.5; C at a third spawn, -160.5, 76.0, 219.5) | 00 0.031, 01 0.029, 02 0.029, 03-f3 0.743, 04-bsl 0.191, 05 0.350, 06 0.229 | 0.743 | identical |

  The drifted pairs of the three retaken scenes are kept as notes in `floors.md`: A vs B gives ocscreen 24.312-43.902 %
  on the world and angle frames and 3.463-5.563 % on the gui frames, itemgui 37.725 / 1.135 %, hudcache-stellarcore
  27.033-52.635 %, and the unmatched retake hudcache-stellarcore-C gives 19.104-48.059 % against A and 26.176-51.493 %
  against B. Their difference is camera placement, as in finding 2's first profile.txt pair: the GUI frames of ocscreen
  and itemgui differ only through the world behind the screen. In the matched ocscreen pair the world and angle frames
  stay at 0.003-0.256 %; the three gui frames (0.421-0.434 %) differ in the same 13 rows (y 19-62, about 3,900 changed
  pixels), where the OpenOS boot banner prints a tip it picks at random each boot: "Many component methods have a short
  documentation ..." in A, "Please report bugs on the Github issue tracker, thank you!" in C (both frames cropped and
  looked at). The tip is also why C's mean luminance is 0.7-0.8 lower on those frames. fontpack differs where the main
  menu draws its yellow splash line, which the game picks at random each launch: in fontpack-0-menu A shows "Stop being
  reasonable, this is the Internet!" and B "/give @a hugs 64" (both frames looked at; 6,226 of the 6,624 changed pixels
  sit in the top quarter, around the splash line and along the top edge of the blurred panorama); fontpack-1-reload's
  changed pixels sit mostly in the same region (located, not looked at). The hudcache F3 frame is discussed above. The
  sky and pack frames of asmodeus-sky, dh33 and ocscreen and the hudcache BSL frames, which could have moved with clouds
  or pack animation, stay at or under 0.350 %.

  *Which scenes are spawn-stable.* Only fontpack, which loads no world, is stable by construction. `ocscreen.txt`,
  `itemgui.txt`, `hudcache.txt`, `dh33.txt`, `asmodeus-sky.txt`, `chunkanimator.txt` and `renderlib.txt` position the
  camera relative to the spawn point (ocscreen, itemgui and renderlib also build their scene with relative `/fill` and
  `/setblock`), and `shaderpacks-scroll.txt` shoots its screen from wherever the player logs in. Of the 18 runs of these
  scripts without `-PwithCompatMods`, retakes included, 14 started at -164.5, 76.0, 218.5, two (ocscreen-B,
  hudcache-stellarcore-B) at -160.5, 75.0, 221.5, one (itemgui-A) at -162.5, 76.0, 219.5 and one
  (hudcache-stellarcore-C) at -160.5, 76.0, 219.5; all six `-PwithCompatMods` runs (dh33, chunkanimator, renderlib)
  started at -165.5, 216.5 (y 76.8-77.0). Every pair of record of these scripts now sits at one spawn, -164.5, 76.0,
  218.5 without `-PwithCompatMods` and -165.5, 216.5 with it, and is stable only for a run that lands there, which is
  why the recipe in section 2 checks the teleport line first.

Proposed pass lines for every later item (the share against both baseline runs of record must be at or under it):

| Frame | Measured floor | Pass line | Why |
|---|---|---|---|
| every frame with a floor at or under 0.8 % (all profile-0/3, sweep-0/1/2/4/5/6, all perf) | 0.000-0.580 % | 0.8 % | the Iris plan's line; these frames have room under it |
| profile-1-portal-nopack (compat and core) | 1.068 / 0.992 % | 1.5 % | one pair only, so the observed floor underestimates what two uncontrolled animation phases can give (the sweep's three pairs spread 0.38-1.82 % on one frame); about 1.4 times the floor, rounded to a half percent |
| profile-2-portal-bsl (compat and core) | 1.788 / 1.110 % | 2.5 % | same reasoning |
| sweep-3-ilikevanilla (compat; core 0.601 %) | 1.819 % | 2.5 % | largest of three pairs, about 1.4 times, rounded |
| sweep-7-ilikevanilla-cached (compat; core 0.566 %) | 0.945 % | 1.5 % | same |
| every frame of asmodeus-sky, itemgui, dh33, renderlib and shaderpacks-scroll; the ocscreen world and angle frames (0, 1, 3, 4, 6, 7); chunkanimator-after and -settled; hudcache-00/01/02 and -04/05/06 of stellarcore-prime, stellarcore and gnetum | 0.000-0.350 % | 0.8 % | the floor has room under the line |
| fontpack-1-reload | 0.496 % | 0.8 % | same; the difference is the random splash line |
| ocscreen-2-gui, -5-bsl-gui, -8-compl-gui | 0.421-0.434 % | 0.8 % | the difference is the OpenOS boot banner's random tip line (13 rows), and the floor is about half the line; mean luminance differed by 0.7-0.8 in the floor pair; the luminance rule below applies only to frames above 0.8 % |
| fontpack-0-menu | 0.719 % | 1.5 % | under 0.8 % but within 0.1 of it, from one pair, and the difference is the random splash line, whose size depends on which two splashes are drawn; about 1.4 times the floor, rounded up to a half percent |
| hudcache-03-f3-vanilla (stellarcore-prime, gnetum and stellarcore) | 0.924 / 0.539 / 0.743 % | 1.5 % | the F3 counters change from run to run; about 1.4 times the largest floor, rounded; one line for the three scenes, because the same F3 text is the noise in each |
| chunkanimator-anim-a, chunkanimator-anim-b | 19.965 / 35.733 % (shot mid-animation) | none; excluded | the capture instant decides the frame; no line separates a regression from the animation |

A frame passing above 0.8 % also needs mean luminance within 0.2 of the reference (every same-code pair above agrees
within 0.2 on its frames above 0.8 %) and a look at the difference: the changed pixels must sit on the portal surface,
on foliage and water, on the F3 text or on the menu's splash line.
Items that touch exactly these paths (D.2 and G.3 for the end portal, and G.4 if it is ever added; D.1 for the pack
vertex stages) should not rely on the wide lines: they retake three runs of the baseline commit and compare with the
per-frame maximum, or compare frames 0 and 3 only. If the harness gains a way to pin the animation clock, the wide
lines can go.

The mods scenes compare as the compat scenes do: the item run against the scene's two runs of record, the larger share
against the line, after the spawn check of section 2. For eight scenes those are mods-<scene> and mods-<scene>-B; for
the three retaken scenes they are the matched pair: mods-ocscreen and mods-ocscreen-C, mods-itemgui-B and
mods-itemgui-C, mods-hudcache-stellarcore and mods-hudcache-stellarcore-D. Every pair of record of a script run without
`-PwithCompatMods` sits at -164.5, 76.0, 218.5, so an item run of such a scene that starts elsewhere is void and is
retaken. The drifted runs (mods-ocscreen-B, mods-itemgui, mods-hudcache-stellarcore-B and -C) are kept and hashed, but
are not compared against. The provisional lines that stood here for ocscreen, itemgui and hudcache-stellarcore are
replaced by the measured floors and lines above.

## 6. V-perf numbers

**fps.** Every `Dev stats` line of every run, compat and core, reads 118-120 fps: the client runs at `maxFps:120`
with vsync off, so fps is saturated and cannot show a per-call saving. timing.sh times the shader transforms, not
frames; the plan's "timing.sh frame times" has no source in what it prints. For a frame-time comparison, later V-perf
runs need the cap lifted (`maxFps:260`, unlimited) or a frame-time statistic; that would also need a new baseline at
this commit.

**GLSMPerfDebug stages.** From `perf-summary.txt`. A line is logged about once a second (timestamps 1-2 s apart), so
"count/line" is calls per second, at 120 fps about count/120 per frame (for example ffp.preDraw about 330 calls a
frame). Timings are sampled (about one call in 256); "mean µs" is the mean of the lines' per-sample averages, "max µs"
the largest single sample. No `bufferbuilder.*` stage appears in any run. `stream.fence` logs reclaim counters, not
timings (`stream.fence[reclaimedRegions=1,reclaimedBytes=4200000,queuePeak=1]`): 8 lines in every vanilla run and 11
in every BSL run, each with one reclaimed region of 4.20-4.22 MB and a queue peak of 1, which `perf-summary.py`'s
count/µs table does not parse, so its row there is empty. Lines per run: vanilla 19 / 19 / 17, BSL 41 / 42 / 39
(compat-A / compat-B / core-A).

I Like Vanilla corpus (`vanilla.txt`):

| Stage | lines (compat-A) | compat-A count/line | mean µs | max µs | compat-B count/line | mean µs | max µs | core-A count/line | mean µs | max µs |
|---|---|---|---|---|---|---|---|---|---|---|
| ffp.preDraw | 19 | 39333 | 32.32 | 289.68 | 40221 | 15.72 | 313.23 | 37410 | 9.44 | 246.76 |
| ffp.uniforms | 19 | 24728 | 0.22 | 13.94 | 24690 | 0.32 | 26.14 | 25171 | 0.28 | 8.89 |
| gl.clientArrayUpload | 12 | 519 | 1.82 | 5.57 | 521 | 2.79 | 8.95 | 515 | 8.79 | 135.02 |
| gl.prepareClientArrays | 13 | 21895 | 18.37 | 166.37 | 21432 | 16.71 | 92.60 | 16480 | 18.80 | 185.23 |
| quad.arrays | 19 | 2630 | 2.28 | 13.57 | 2513 | 2.53 | 59.61 | 1572 | 2.05 | 59.58 |
| stream.drawCall | 15 | 109 | 0.23 | 11.58 | 103 | 0.30 | 7.03 | 115 | 0.23 | 3.24 |
| stream.drawDirect | 17 | 24818 | 2.69 | 247.93 | 25045 | 11.00 | 983.68 | 25633 | 16.45 | 194.73 |
| stream.fenceCreate | 8 | 1.00 | 14.28 | 31.38 | 1.00 | 17.37 | 37.17 | 1.00 | 21.03 | 40.13 |
| stream.fencePoll | 8 | 1.00 | 14.05 | 33.28 | 1.00 | 14.82 | 29.25 | 1.00 | 17.37 | 36.66 |
| stream.persistentUpload | 15 | 109 | 0.05 | 7.69 | 103 | 0.04 | 3.03 | 115 | 0.11 | 10.58 |
| stream.shaderPreDraw | 15 | 109 | 0.40 | 19.33 | 103 | 0.66 | 154.83 | 115 | 0.30 | 3.82 |
| stream.uploadAndDraw | 17 | 24818 | 2.57 | 247.85 | 25045 | 10.91 | 983.53 | 25633 | 16.32 | 194.54 |

BSL corpus (`bsl.txt`):

| Stage | lines (compat-A) | compat-A count/line | mean µs | max µs | compat-B count/line | mean µs | max µs | core-A count/line | mean µs | max µs |
|---|---|---|---|---|---|---|---|---|---|---|
| ffp.preDraw | 41 | 34421 | 4.88 | 283.31 | 29899 | 9.93 | 301.21 | 35272 | 7.41 | 307.82 |
| ffp.uniforms | 41 | 15783 | 0.46 | 83.17 | 15621 | 0.42 | 19.18 | 15817 | 0.37 | 11.16 |
| gl.clientArrayUpload | 34 | 290 | 1.83 | 2.85 | 293 | 2.30 | 5.85 | 291 | 2.15 | 6.39 |
| gl.prepareClientArrays | 35 | 21794 | 7.55 | 190.77 | 16976 | 7.95 | 108.75 | 20919 | 4.88 | 72.85 |
| quad.arrays | 41 | 2749 | 2.08 | 12.14 | 2401 | 2.49 | 11.15 | 2927 | 2.05 | 9.63 |
| stream.drawCall | 27 | 79 | 0.27 | 19.74 | 80 | 0.34 | 62.91 | 84 | 0.27 | 4.89 |
| stream.drawDirect | 28 | 19594 | 13.30 | 2985.95 | 18563 | 21.14 | 2717.72 | 19926 | 33.80 | 3113.48 |
| stream.fenceCreate | 11 | 1.00 | 20.48 | 46.94 | 1.00 | 16.37 | 28.62 | 1.00 | 19.11 | 37.35 |
| stream.fencePoll | 11 | 1.00 | 14.39 | 53.92 | 1.00 | 13.09 | 30.83 | 1.00 | 13.83 | 28.01 |
| stream.persistentUpload | 26 | 82 | 0.04 | 4.37 | 83 | 0.07 | 31.78 | 88 | 0.05 | 5.43 |
| stream.shaderPreDraw | 27 | 79 | 0.33 | 21.64 | 80 | 0.36 | 23.69 | 84 | 0.28 | 3.11 |
| stream.uploadAndDraw | 28 | 19594 | 13.18 | 2985.74 | 18563 | 21.02 | 2717.41 | 19926 | 33.71 | 3113.34 |

The compat columns are the numbers the passthrough must drive to zero (plan V-perf): today the emulation stages run
on compat at the same rate as on core (ffp.preDraw about 30,000-40,000 calls a second, stream.uploadAndDraw about
19,000-25,000), because GLSM enables its FFP emulation on both profiles. On core they stay. The microsecond columns
vary by a factor of two to three between identical runs (ffp.preDraw mean 32.32 vs 15.72 µs on vanilla compat A/B),
so per-call cost should be compared as counts and as an order of magnitude, not to the decimal.

**Transform times** (`transform-times.txt`, from `scripts/glsl-corpus/transform-times.py`):

| Run | transforms | median ms | p90 ms | sum ms | first-30 median | rest median | lock wait ms / contended |
|---|---|---|---|---|---|---|---|
| perf-vanilla-a0-compat-A | 104 | 42.6 | 83.9 | 5572.8 | 76.6 | 38.4 | 1845.9 / 1843 |
| perf-vanilla-a0-compat-B | 104 | 51.6 | 96.9 | 6308.8 | 88.1 | 48.5 | 2393.8 / 2042 |
| perf-vanilla-a0-core-A | 104 | 50.7 | 89.9 | 6168.7 | 79.5 | 46.9 | 2329.4 / 1895 |
| perf-bsl-a0-compat-A | 170 | 23.1 | 54.9 | 4710.1 | 56.9 | 21.3 | 1975.1 / 2663 |
| perf-bsl-a0-compat-B | 170 | 23.3 | 53.6 | 4873.6 | 53.7 | 21.4 | 2043.6 / 2555 |
| perf-bsl-a0-core-A | 170 | 22.2 | 53.6 | 4799.6 | 49.8 | 19.0 | 1972.8 / 2973 |

Transform times are the same on both profiles and vary about 20 % between identical runs; they are not expected to
move under the passthrough before D.1.

## 7. Findings that affect the plan

**(1) The core profile is broken on `dev` at 6c3333f8.** With the FML splash on, every core run crashes during the
splash: the splash thread's first `glGetError` on the window context fails, so `SplashProgress$Texture.<init>`
(`SplashProgress.java:937`) throws `Texture creation: Invalid operation` (latest.log:254 in all six splash-on logs), and
the client thread's `GL ERROR @ Pre startup 1282` on the same shared context is logged after it (:302). Crash reports
are in `core-profile-A/`, `core-profile-B/`, `core-sweep-A/`, `core-sweep-B/` and `_failed/`. With the splash off the
game runs, but terrain is untextured under every pack and without one (block faces read as biome tint times an opaque
white texel, cross plants solid), vanilla clouds are missing without a pack, and vanilla logs GL error 1282 at Pre
startup and at Post startup. No compat run has a GL ERROR line. The triage (`baseline/triage-core-profile.md`) found:
the context is a real 4.6 core context; a core-only GL_INVALID_OPERATION is pending on the window context when the
splash thread takes it; the error flag belongs to the context, so the splash thread inherits it and fails its first
check. That the error is raised after GLSM's init-time error drain and before `SplashProgress.start` is an inference,
drawn from the splash thread's own calls being legal in core (the triage says "not proven"). The nosplash runs do not
corroborate it: their Pre-startup check (Cleanroom 0.7.4 `Minecraft.java:488`) comes after `beginMinecraftLoading`
(:465) and `drawVanillaScreen` (:468, which calls `drawSplashScreen` with the splash off), so it covers those calls too.
Kirino's `ImmediateClientServices.initAndWarmUp`, new in Cleanroom 0.7, is the prime suspect but the call is not pinned.
The white terrain plausibly comes from GLSM's binding cache drifting after a failed call; that is a hypothesis. Certain
and separate: Cleanroom 0.7.4's SplashProgress switches contexts with static `Display.makeCurrent()/releaseContext()`
and `SharedGLContext.makeCurrent()/release()`, while GLSM's redirector intercepts only `Drawable.makeCurrent`
(`GLSMRedirector.java:1033-1041`), so GLSM's splash context tracking has done nothing since `c665eba6` on either
profile. The last good core runs are 2026-09-23..27 on Cleanroom 0.6.12; PR #35 (`c665eba6`, 2026-10-14) verified only
the compat context; `c665eba6` ships in v0.8.0, v0.8.1, v0.8.2 and v0.9.0.

- *What the V-core baseline is:* a repeatable record of today's core output with the splash off (floors 0.000-1.110 %),
  good enough to detect whether a lane-A item moves core.
- *What it is not:* a picture of correct core rendering. The plan's "the core path is not edited by this plan, so the
  frames must sit inside the same-code floor" can be checked mechanically against it, but it cannot start from a
  healthy core until this is fixed, and core runs must keep `splash.properties` `enabled=false` until then. When a fix
  lands (outside this plan), V-core must be retaken at its merge commit, and every item verified before that says which
  core baseline it used.
- *Dev-only?* No, by the triage's reading: the mechanism is Cleanroom's own SplashProgress, GLSM and the driver, all in
  the release jars. A Linux or Windows user who picks OpenGL Profile = Core should hit the crash; AUTO resolves to
  compatibility there. macOS users are likely affected, unverified: AUTO resolves to core there.
- *Follow-up:* (a) one instrumented core run with the splash off, logging `glGetError()` at createDisplay TAIL, after
  `OpenGlHelper.initializeTextures`, after `new Framebuffer`, around `ImmediateClientServices.initAndWarmUp`, at
  `SplashProgress.start` HEAD and around the atlas stitch, then bisect the window it names; (b) teach GLSM's context
  tracking the 0.7.4 switch points, and only as a stopgap drain pending errors at `SplashProgress.start` HEAD
  (`MixinSplashProgress`); (c) correct SCOPE_RESEARCH §3.2's "no GL errors" for Cleanroom 0.7, add a core run to the
  smoke and release checklist, and record in the plan's open check 10 that the `Drawable.makeCurrent` redirect is dead
  since 0.7.4, which matters to A.2's resync points (the splash handover is not observed today).

**(2) Spawn drift in profile.txt, fixed there only.** See section 2. The relative teleport made two identical runs
differ by up to 62.6 %; the absolute teleport brought the floor to 0.001-1.788 %. Seven V-mods scripts still position
relative to the spawn point (`ocscreen.txt`, `itemgui.txt`, `hudcache.txt`, `dh33.txt`, `asmodeus-sky.txt`,
`chunkanimator.txt`, `renderlib.txt`), and the second capture showed what that costs: in three of the eleven pairs
(ocscreen, itemgui, hudcache-stellarcore) the two runs started up to 4 blocks apart, the same offset that gave
profile.txt its 27.8-62.6 %, and differ by up to 52.6 %. The other eight pairs landed on the same spawn by chance. The
three scenes were retaken at `6c3333f8` with the scripts unchanged until a run matched the first teleport of A or B:
mods-ocscreen-C matched A, mods-itemgui-C matched B, and of the two hudcache-stellarcore retakes C started at a third
spawn (-160.5, 76.0, 219.5) and D matched A. The matched pairs give floors of 0.003-0.434 %, 0.006-0.094 % and
0.029-0.743 % (section 5.5), so every V-mods scene now has one. The cost remains for later items: 4 of the 18 runs of
these scripts without `-PwithCompatMods` started away from -164.5, 76.0, 218.5, so an item run of these scenes will now
and then land elsewhere and have to be retaken. Follow-up, now script hygiene rather than a gap in the baseline: give
these scripts absolute teleports (and absolute `/fill` and `/setblock` coordinates for ocscreen, itemgui and renderlib)
at the pairs' spawn, -164.5, 76.0, 218.5 (-165.5, 216.5 for the `-PwithCompatMods` scripts); amending a script changes
its hash, so the item that amends one shows that the amended script reproduces its pair of record within the floor, or
retakes that scene's baseline. Also:
`run/client/scripts/` is gitignored, yet the plan's tiers name eight scripts that live there (`profile.txt`,
`ocscreen.txt`, `itemgui.txt`, `hudcache.txt`, `fontpack.txt`, `dh33.txt`, `shaderpacks-scroll.txt`, `menu.txt`).
Moving them to `scripts/` would put the verification inputs under version control; until then the hashes in appendix
A.0 identify them.

**(3) Modern Splash does not run in the dev client.** `-PwithModernSplash` crashes at "Initializing game" with
`NoSuchMethodError: Minecraft.func_71410_x()` at `SplashProgress.<clinit>(CustomSplash.java:74)`. Triage
(`baseline/triage-modern-splash.md`): Modern Splash's `ReplaceSplashTransformer` reads its `CustomSplash` bytes with
`LaunchClassLoader.getClassBytes` and renames only the class, so the injected body keeps its SRG names, which the
MCP-named dev runtime lacks. Since `c665eba6` the mod is a raw SRG jar in `devRuntimeMods`; before it (`da90bad4`,
2026-10-05) Unimined remapped the whole jar to MCP at build time. Production runs SRG names, so it should be unaffected
(inferred; not smoke-tested). Not a GLSM regression. The V-mods `menu.txt` scene has no baseline. Follow-up: pre-remap
the jar with `remapSrgModJarToMcp` (as `remapCeleritasForDev` does) for `devRuntimeMods`, or take this scene in the
Prism production instance; B.4's verify (`menu.txt` with Modern Splash, the splash-font mixin re-check) needs one of
the two first. Either way a retake alone gives no reference image: `menu.txt` is `wait 200`, `log main menu reached`,
`exit` and shoots no frame. The splash-font check also needs a `shot` step on the main menu, or the dev-run notes'
in-process splash frame dump for the loading screen.

**(4) Distant Horizons is not loaded by `-PwithCompatMods`.** `build.gradle:948-949` leaves it out ("3.3.3 ... runs on
Cleanroom 0.7.4 in Prism, but not yet tried in the dev client"). The six `mods-dh33` frames show vanilla terrain at
render distance 4 and no LODs, so the plan's V-mods line "`dh33.txt` (`-PwithCompatMods`)" covers nothing of DH today.
Follow-up: add DH 3.3.3 to the dev client (its own toggle or `-PwithCompatMods`) and retake mods-dh33 before any item
that cites it, or take the scene in the Prism `dh-fix-smoke` instance.

**(5) The Celeritas gate logs 23 "n of n" lines, not 19.** Every run that loads a world logs 23 `DemonicaQuarantine`
`Applied ...: n of n injectors found their targets` lines (two of them `0 of 0`) and no mismatch; fontpack, which
loads no world, logs 11; the crashed runs 2 or 0. Diffing the set against `run/client/logs/2026-09-27-4.log.gz`,
which has the note's 19, gives the four additions: `BlockRendererDispatcherMixin` (`e8b327c2`, 2026-10-14),
`DefaultChunkRendererCompatMixin` and `RenderSectionManagerCompatMixin` (`4ae32e07`, 2026-10-12), and
`CeleritasWorldRendererCompatMixin` (`5d8ace91`, 2026-10-21); `ChunkBuilderMeshingTaskCompatMixin` (`1511a6bd`,
2026-09-24) is already among the 19, and `2026-10-15-1.log.gz` shows 22. Follow-up: the dev-run note and
`docs/SCOPE_RESEARCH.md:449`, which also says 19, should say 23 for this HEAD; any later item that changes the count
must explain it.

**(6) Mods of fact 15 that have no script:** CodeChickenLib/Draconic Evolution, Extra Utilities 2, HBM, Botania,
VoxelMap, Lumenized/GTCEu. Extra Utilities 2 and Botania were loaded in the three `-PwithCompatMods` runs (mods-dh33,
mods-chunkanimator, mods-renderlib; `build.gradle:956` and `:965`), but no frame exercises them; the others were not
run. As §4 says, each gets a script when its compat entry is removed (G.3); until then no baseline exists for them and
no item can claim V-mods coverage of them.

**(7) V-perf measures counts, not frame time.** See section 6: fps sits at the 120 cap in every run and timing.sh
reports transform times. The GLSMPerfDebug counts are the usable per-call baseline.

## 8. Verify verdict

The plan's verify for A.0 is "the report exists and the floor numbers are inside the Iris plan's experience (0.0-0.8 %
on animated frames)".

- The report exists, records the commit, the driver strings, the script list with hashes and the hashes of every frame
  and log, and `sha256sum -c` passes on every run directory: **holds.** The crash reports, perf summaries and config
  copies were appended to their directories' SHA256SUMS on 2026-10-22 and are listed in appendix A.3.
- Floors inside 0.0-0.8 %: **holds** for all static compat frames (0.000-0.070 %), all perf frames (0.015-0.580 %) and
  the mods frames of the eleven pairs (0.000-0.743 %) except three; **does not hold** for the two end-portal frames of
  `profile.txt` (1.068 %, 1.788 %), for I Like Vanilla in the sweep (1.819 %, 0.945 %) and for the hudcache F3 frame
  (0.924 %); chunkanimator-anim-a and -anim-b (19.965 %, 35.733 %) have no usable floor. Pass lines for these frames,
  and for the fontpack menu frame, are proposed in section 5.5.
- V-core: captured twice with its own floor, but of a broken core path (untextured terrain, splash crash with the
  splash on); the "core must not move" check can only detect change, not correctness, until finding 1 is fixed.
- V-mods: 9 of the plan's 10 scripts ran (`hudcache.txt` with both StellarCore and Gnetum), each twice; `menu.txt`
  with Modern Splash failed (dev-only remap gap), and `dh33.txt` ran without DH, so 8 have a baseline of the scene
  they are meant to cover. All eleven scene pairs have a measured floor (section 5.5): for ocscreen, itemgui and
  hudcache-stellarcore, whose A and B runs started at different spawns, a retake at the same commit matched one of them
  (A and C, B and C, A and D), with floors of at most 0.434, 0.094 and 0.743 %, which replace their provisional lines.

Verdict: **PARTIAL.** The baseline is usable for lane A on compat with the per-frame pass lines of section 5.5,
including all eleven V-mods scenes, each with a measured floor that holds for an item run starting at the pair's spawn;
V-core is a change detector only; the Modern Splash and DH scenes have no baseline.

Open items:

- The core profile is broken on `dev` (finding 1): the splash crash and untextured terrain, with follow-ups (a)-(c);
  V-core must be retaken when a fix lands.
- The seven spawn-relative V-mods scripts still need absolute teleports (finding 2): every V-mods scene has a floor now,
  but it holds only for an item run that starts at the pair's spawn, and a run that lands elsewhere is retaken. The
  eight V-mods scripts under the gitignored `run/client/scripts/` are not under version control.
- Modern Splash does not run in the dev client, so `menu.txt` has no baseline and shoots no frame (finding 3).
- Distant Horizons is not loaded by `-PwithCompatMods`, so mods-dh33 covers nothing of DH (finding 4).
- The Celeritas gate count is 23, not 19; the dev-run note and `docs/SCOPE_RESEARCH.md:449` still say 19 (finding 5).
- Six mods of fact 15 have no script and no baseline (finding 6).
- V-perf has no frame-time measure (fps at the 120 cap) and no same-code core perf pair (finding 7, section 3.4).
- The end-portal, I Like Vanilla, F3 and fontpack menu frames rely on widened pass lines until the harness can pin the
  animation clock; chunkanimator-anim-a and -anim-b are excluded (section 5.5).

## 9. Reproduction

Preconditions: outside the sandbox, no other dev client (`pgrep -fa '[c]rl.dev.extrapath'` empty), no other Gradle
build, `git -C <repo> rev-parse HEAD` = the commit under test, `cmp <repo>/run/client/scripts/profile.txt
<repo>/run/glsm-passthrough/baseline/profile.txt` clean, `run/client/options.txt` equal to `baseline/options.txt.orig`,
and the script hashes of appendix A.0. Use a new run name, or `FORCE=1` to overwrite one. Delete the world the script
created (`run/client/saves/<name>`) after each run, as was done here.

```
H=<repo>/run/glsm-passthrough/capture-run.sh
# V-compat
$H compat-profile-A2 <repo>/run/client/scripts/profile.txt
$H compat-sweep-A    <repo>/scripts/glsl-corpus/sweep.txt -PdevProps=demonica.glsmPerfDebug=true
# V-core: first set enabled=false in run/client/config/splash.properties; set it back to true afterwards
$H core-profile-nosplash-A2 <repo>/run/client/scripts/profile.txt -PdevProps=demonica.openglProfile=core
$H core-sweep-nosplash-A    <repo>/scripts/glsl-corpus/sweep.txt -PdevProps=demonica.openglProfile=core,demonica.glsmPerfDebug=true
# V-mods
$H mods-ocscreen      <repo>/run/client/scripts/ocscreen.txt -PwithOpenComputers
$H mods-asmodeus-sky  <repo>/scripts/asmodeus-sky.txt -PwithAsmodeusCore
$H mods-itemgui       <repo>/run/client/scripts/itemgui.txt
$H mods-hudcache-stellarcore-prime <repo>/run/client/scripts/hudcache.txt -PwithHudCache=stellarcore
#   then B:HudCaching=true in run/client/config/stellar_core.cfg
$H mods-hudcache-stellarcore <repo>/run/client/scripts/hudcache.txt -PwithHudCache=stellarcore
$H mods-hudcache-gnetum      <repo>/run/client/scripts/hudcache.txt -PwithHudCache=gnetum
#   fontpack: resourcePacks:["demonica-custom-font"] in options.txt for the run, then restore
$H mods-fontpack      <repo>/run/client/scripts/fontpack.txt
#   dh33: renderDistance:4 in options.txt for the run, then restore
$H mods-dh33          <repo>/run/client/scripts/dh33.txt -PwithCompatMods
$H mods-chunkanimator <repo>/scripts/chunkanimator.txt -PwithCompatMods
$H mods-renderlib     <repo>/scripts/renderlib.txt -PwithCompatMods -PdevProps=demonica.debug.depthFunc=true
$H mods-shaderpacks-scroll <repo>/run/client/scripts/shaderpacks-scroll.txt -PwithUniversalTweaks
$H mods-menu-modernsplash  <repo>/run/client/scripts/menu.txt -PwithModernSplash
# V-mods, second capture (2026-10-22): the same lines with every run name suffixed -B (no -B for menu.txt);
#   before mods-hudcache-stellarcore-prime-B delete run/client/config/stellar_core.cfg so StellarCore writes it anew,
#   then set B:HudCaching=true for mods-hudcache-stellarcore-B; before mods-fontpack-B move run/client/cache/kirino/font
#   aside (into the run directory after the run, since the helper refuses a non-empty one); options.txt as for A
# V-mods retakes (2026-10-22) for the three scenes whose A and B started at different spawns: run again with suffix -C,
#   then -D, until latest.log's first `Teleported Developer to` line matches the A or the B run's (C of hudcache did
#   not; D did). Before each hudcache retake: cp baseline/mods-hudcache-stellarcore-B/config/stellar_core.cfg
#   run/client/config/; after it, cmp the two, copy the file to <run>/config/ and append its sha256 to <run>/SHA256SUMS;
#   after the last one delete run/client/config/stellar_core.cfg (journeymap_ModInfo.cfg and journeymap_server.cfg
#   were also deleted from run/client/config at that point)
$H mods-ocscreen-C             <repo>/run/client/scripts/ocscreen.txt -PwithOpenComputers
$H mods-itemgui-C              <repo>/run/client/scripts/itemgui.txt
$H mods-hudcache-stellarcore-C <repo>/run/client/scripts/hudcache.txt -PwithHudCache=stellarcore
$H mods-hudcache-stellarcore-D <repo>/run/client/scripts/hudcache.txt -PwithHudCache=stellarcore
# V-perf
P=<repo>/run/glsm-passthrough/perf-run.sh
$P vanilla a0-compat-A
$P bsl     a0-compat-A
$P vanilla a0-core-A -PdevProps=demonica.glsmPerfDebug=true,demonica.openglProfile=core   # splash off
$P bsl     a0-core-A -PdevProps=demonica.glsmPerfDebug=true,demonica.openglProfile=core   # splash off
# floors and hashes
cd <repo>/run/glsm-passthrough && python3 imgdiff.py baseline/<A>/frames baseline/<B>/frames
cd <repo>/run/glsm-passthrough/baseline/<run> && sha256sum -c SHA256SUMS
```

meta.txt prints the comma inside `-PdevProps` as `\,` (the helper's `printf %q`); on the command line it is a plain
comma. When the dev client is done, `./gradlew --stop` frees the 2G daemon.

## Appendix

### A.0 Inputs

| File | sha256 |
|---|---|
| `run/client/scripts/profile.txt` (amended, = `baseline/profile.txt`) | `be0f1d2047a9391c256805484437f82ca0e0081fe3026419337e40eb80c4bdbf` |
| `baseline/profile.txt.orig` | `a645872630db417159afc7acfb3b7eb7aaf882d2aa1ef47596374c577a5cbeaa` |
| `run/client/scripts/ocscreen.txt` | `d864bf4f699805515fd386af2165506f8fcea04b63221af900b76294cf68e7fc` |
| `run/client/scripts/itemgui.txt` | `2c1389e8817f7f0c8f134cd331329e5a0a7a882c2b5dec844eba047c5bfb4127` |
| `run/client/scripts/hudcache.txt` | `a4b5e72631a55f293db06088a635764586508e306c4458d241c3bcaaa4fe89fa` |
| `run/client/scripts/fontpack.txt` | `d13fd0c5b8941becf2da0911a45e230f4dc88a79a0f5708a131fe69e400ca11a` |
| `run/client/scripts/dh33.txt` | `e6165ef51170bf5693b13cc0670245fb8637957f1a75cd44ac1858bc82fcd4d0` |
| `run/client/scripts/shaderpacks-scroll.txt` | `6fbd1b86c4ffb3844a9b83ddcbf466ff049de8013cb2c75167716857cc34e169` |
| `run/client/scripts/menu.txt` | `e8d9d7f1395f91e2a6e3502df88ea0511a79110cb1ef4ef8825ea9951ba3097e` |
| `scripts/asmodeus-sky.txt` | `cfab0622215ea2b81b2a93abd736e512dd8a0eccc377e2c3579daa9e900e5b87` |
| `scripts/chunkanimator.txt` | `7abad863cd1134e777260a0d37eab2ea731232132a5f9fb2125cbc715c0b6151` |
| `scripts/renderlib.txt` | `13e82134da631d6e015a18a615d041b4bd4e90147eba73df517c10d0b42dfff4` |
| `scripts/glsl-corpus/sweep.txt` | `04a29969c20e501d1a8f4bfbb620d26c68b081a041edd61d0b7348637a0f7916` |
| `scripts/glsl-corpus/vanilla.txt` | `4677c49abb559be41b42364ab92c4661815736f9b5e6fc1906e2fff11f15befb` |
| `scripts/glsl-corpus/bsl.txt` | `5558a3361e9a2c73366b39e79397f8add9047d64c481b479a01ffa52734bb12b` |
| `scripts/glsl-corpus/timing.sh` | `ff7a888fc471e6637ca47d1cb9607dd03e176f7a933e6635f305b860479ec7bb` |
| `scripts/glsl-corpus/transform-times.py` | `b3821b34013caf7dad9839c8af88a5929aa32b0931a2b2e2d668c3ec430a6c5c` |
| `run/glsm-passthrough/capture-run.sh` (every run on 2026-10-21) | `30a1881ecfa296a32c11738f5f976b34da0c38ca6ff7d5648271f448a815d456` |
| `run/glsm-passthrough/capture-run.sh` (the `mods-*-B` runs and the retakes `-C`, `-D`; adds the `splash:` line to meta.txt) | `d4dd1eadbbc5af8f0cd03b867283e66d66280be5c056bbc883edb7a441a6d9a3` |
| `run/glsm-passthrough/perf-run.sh` | `cbfb37669681259989a135de6cb83f12217320f5b755b2e53a534e33f00e1e02` |
| `run/glsm-passthrough/perf-summary.py` | `89d3c3f29a9f062460f688f41e30c65e2b4c2c534c8dc5cb97c83a32aa122bbf` |
| `run/glsm-passthrough/imgdiff.py` | `f76cbfca00d5ca1edb0930940995fa387f90d39849a4f8ca94c88c0bf6d94514` |

The files under `scripts/` are in the repository at 6c3333f8; their hashes are given so a run can be checked against
the tree.

### A.1 Logs

| run | latest.log sha256 | other files in SHA256SUMS (name: sha256) |
|---|---|---|
| compat-profile-A2 | `e834f2f47dc81127b272d4a4d83f5797f8edc2900177803809e20894632e664c` | `client.out`: `f2b10a36f316daa8b88868b9726397d84b213fbe3a7603ded5e6529514d44177` |
| compat-profile-B2 | `a856c603b696cbc9e8479229226e5a506041091f1e2b1e4096ddaa7dffb52a84` | `client.out`: `e6026b3548832ee4019a40913971a19f47e3d8191f4e5d15b6f659e9e1e6cdb4` |
| compat-profile-A | `b2d41c5d22f608f724271ba5a7d31b1676b59c77eb06323aa29332851fbc9471` | `client.out`: `561f48c3b3ab22660d95ec25bffcd02348cd354d902197f7e224666e56da3892` |
| compat-profile-B | `57aef0c701e93259bb5583d3665a4e34e97675573adb732bb3940a752ed1d181` | `client.out`: `63989bcdbb4f25081467ee7dfdb44ebb8f4826f83bdbb3c23252aaacb79f4696` |
| compat-sweep-A | `2d7d35c8e97df2777fb02776d5212ba5766f8289f745de19875730f0880bfbd2` | `client.out`: `5337390ac401d0c7a1cc0a9d0eeddf4b30ce96484e9f2c0d6601db729b107422` |
| compat-sweep-B | `76ef786b7a84202662ea93c550c1727d9dc31e46ca1cb73fc2269b002eadc9e0` | `client.out`: `0c88fb35a188504bf03216c785da29b1024b6d0e5c54c3f5ead34905872e0dc1` |
| compat-sweep-C | `66f10c8e51f892a63ec400091f1579c0a1dc5a9c29b5f39786723f180797ca6d` | `client.out`: `60c42f298e84086916c5dca49d7c6aeaf2ca2453b8f56130eae324ca3a72658a` |
| core-profile-nosplash-A2 | `1e9762ac4396c9bed550238c54138dfdbc906bd137ada06d1311632553c600f6` | `client.out`: `7132621df7a3d5970acad17b0b3781ffa4f77c8d6390ffa0774def6a688d55ab` |
| core-profile-nosplash-B2 | `d929e85cac8a7611d4db5a547eeeba4a2d534132d136e8d2b4d5debf51cc164b` | `client.out`: `c578f4dd78ec2569c8c99a08d23500e47c6f050d407a95686d81946eead1eadb` |
| core-profile-nosplash-A | `9b25b1b746b886e2c8e5cf42ede1b4f25debab5e1cd117cbef0633dd7a6bc507` | `client.out`: `363aa7d7c5d767f095072c27d550a22bb0adffd80e780b403fd53b2a914f9d16` |
| core-profile-nosplash-B | `ebe9ce6af59ceb3d434396be6f500415d7c0591f87040e6ad8e1d84ad421aee9` | `client.out`: `8e969bd5e39277001bfd76437eba12b118f7c69f1c65460ce61896457e518c97` |
| core-sweep-nosplash-A | `32e36d954ac72eec028105db3f368270d49ed0db4d5a9c9d4e92f5464a31fa8f` | `client.out`: `8f60653a42d28aecf4bfeebf1daee4023fd30704bb98554f155d873ac4ab2832` |
| core-sweep-nosplash-B | `e57d116a20529e2013cd586b46f94d5c4ada11d56063ad6a6b58d585baad60d1` | `client.out`: `e32d59a88115288a86fd60cc6508ad57b29bb1e618c11b4c8eb72c318f5dc202` |
| core-profile-A | `4d34d06b1793d681761c56974bfd51ae98b2592d81d829777a00608a6f6cf078` | `client.out`: `3f972edbed3a4c822cea07523c43ba49361dcf192f10bf51a0bec495630b4600` |
| core-profile-B | `aed2532775008399287e32d6bc6bb4bc28907a8f9736804dfe261dc96cbaffc2` | `client.out`: `d9d38e6602ce535c0a0b95e99b508eb0bfc79b4d648a62c5884c694254b36ef6` |
| core-sweep-A | `211d0e5012880503a8683e46b9db8ca5530a261d4155b8a8e9d3c94f70cdf353` | `client.out`: `ff690ac799daf4b54d9a465e3686c1ff83ea228748c19faad5ad1265ccf9dc89` |
| core-sweep-B | `6300a387d70515853be4a3f929647d1e9d2f432145b9b2e941397e5b56b300fb` | `client.out`: `f992af26a1cb3a48d89c38100e242a23b81a8cdf0fb6c4e5526a28617b11c3f9` |
| mods-ocscreen | `64297e6c7514431219d9989e4fe8da8b3e0c1a4bb6ee7c8896531e7e07237b45` | `client.out`: `5aa0b1d1e498787ccc772e00e67071a6887cf227d5872c1254a2e4decf3aeea8` |
| mods-asmodeus-sky | `41bcb24686f289ea3c93ef63ee9e729b59fd88b6d7e40272c4b1394c429c4536` | `client.out`: `e1f185180275794a4f82e8c87614fd48db78bf5c1b8e6cb4a9440a1a88e0d896` |
| mods-itemgui | `275447f53479470a0f023279c1e73363242470bf40d27097f81e0dc22fb61efa` | `client.out`: `8e490d7edd256762539610ba73053a8119251c03f043112fab6b8f76782ca3b6` |
| mods-hudcache-stellarcore-prime | `f052c24e0c0651866e220f124fe8b177513a22943627ec805459ad78b8e364ac` | `client.out`: `cc342cdf746000a8de98e93882c7499c38077f837ae5bb39b4d5d4096cd00cbd` |
| mods-hudcache-stellarcore | `bf75ed421ab5528fa760bdd34fdb989f40b17734fe3119d43f46cbb49e7037fc` | `client.out`: `dd80cf8b96ba6a6ccaf8008b720cbe1e9e755975e94630c9c04b10ede6ff4a0c` |
| mods-hudcache-gnetum | `0cd925b9947b889fb611d11d71f60d3b6d82cafb37bbed2b38974bf2697189e2` | `client.out`: `550c2da0aab86a9f5d66549305e7c95523ec442bf0ca0f9f04b9fbf19e80028c` |
| mods-fontpack | `839d5343032c43211f68221e10275bb8557962b0753206640ba591433f9a6a1c` | `client.out`: `a6d6126ba9a12580ea07792a5e0eb2f5c0e76971078a95e86abc5f610b771063` |
| mods-dh33 | `f948cd3ad58da884ffdfe74e72587d03d6c43bd1390c865e566f38585ecc1e36` | `client.out`: `14f8e4fcb5fb37ef2f064eb53cccdaf96a39a2fb262b5fdca46e3a1526506722` |
| mods-chunkanimator | `80d0e7fc50752c67974f8efb78d67b3622f90c5ed24a14e12f869cb39326dd98` | `client.out`: `f293bad8c4ab6dd38cef01736e6283ffd9b010d2a7653ee8bffe46386f954c88` |
| mods-renderlib | `5c4ac2483abbda95c503bae9f78bef5d7f3a64843a050170eb325f47500097b9` | `client.out`: `1cd6f08003997b5fe5959277aa3580ceec289d7b530bad323b5609e6306f6dae` |
| mods-shaderpacks-scroll | `b5c1120ee3429e9eeeff8ea33b429399616431403b093a836317bc9892be1332` | `client.out`: `504e5cbafcc83c43b370e0886e8142ffcf266ec081a71a41cc195f88396e3dd2` |
| mods-menu-modernsplash | `97b9002cd1cb2aec646f675581a34cc2772579d3c02ad915f6a75fbc768f943a` | `client.out`: `18e47c97248191db3c64436ab68aa1a597a1e79484df9a52779b3c6690f4cf19`; `crash-2026-10-21_23.21.34-client.txt`: `6930fcec710f6373c6bfbba891616220865116f6d4f3751053e0f6f3f36da7b0` |
| mods-ocscreen-B | `44a1ecf69e9e3cbade7f360c25aeb1b361f66e31d292c9f0c455dd456ecbb363` | `client.out`: `4d6dbb84ea0511f46e105d55e4a8ad31b678a7c16b50090e8579e8952f935532` |
| mods-asmodeus-sky-B | `6b27b1e2c928b44c8a9c60c79a384a64159f925e0302ccbf1f72c71316f3234d` | `client.out`: `cd166a65883dc1a12739711800a707e5603059c1f661b8196a9525282de27529` |
| mods-itemgui-B | `f9dee4c11b9b500e6533deb4aefbf98cda11e966484b4f259bec4db2f0ee6714` | `client.out`: `6a1ab9b5b381363b52d1c40ccd9b5749cf0e287b66e39ce87090b83229947eb7` |
| mods-hudcache-stellarcore-prime-B | `1271ee8ed13f99a9105cb5e25f5466e4f6477303330cd0c70adf75852f04f088` | `client.out`: `ec71a99537bf377bd67c1419dde007bd0134eccf73b5f197eef6966bbdc71d0d` |
| mods-hudcache-stellarcore-B | `b287f74f60a543279fcb404a76d958366574534bdd841833d60d8732a622e8df` | `client.out`: `088226a89c0bb56e14648dafee6326fdb834e9742ff2056671f2db17f345e4ab` |
| mods-hudcache-gnetum-B | `7fb81b45a094f4a6934a9177d294b136c0d105b8b3687119a5aff8bf30a471e9` | `client.out`: `ba9954931691194d25a52628f03bab0d6903feb8b4c7fb27c413fafbcbe64b17` |
| mods-fontpack-B | `812a66f21a04332cda0a625bfdb645ca93e7ab497dece7678985994b04c00ad4` | `client.out`: `ec782ab7e6ed79be26aa1ef1b566e8c4ccb1546459e4ec3ebad0b39763cebe06` |
| mods-dh33-B | `dae0fa7154a47a52ebe9f9e36cf60b9fbcdb4fc2bd40b909e98f0fbfc50a4c01` | `client.out`: `ae168ae2b673ab06e27e24b4533fdb40b48a6173d5d07e6a0da436a2785327a4` |
| mods-chunkanimator-B | `836c5e8d49000799cd102ff753f8641897defac1d4b5f97c15a68e5fe39bae22` | `client.out`: `816814a58e76e7678cbff5ee2ee26bae6094dd71a55ed3b8b73718bc24fad16b` |
| mods-renderlib-B | `164cead0874495bf100fd8823e6340ddb3df69042686839e9f7d206efac993dd` | `client.out`: `71a39188e2405d1dc67ebb541b3fb7c71333afdba467e91d695ae66d8a144b04` |
| mods-shaderpacks-scroll-B | `3b7a1395854e7f6fe5da271a6270ac3a46d0beb58b64c01f8b402fd892b7639a` | `client.out`: `561e12cff0e420c9b0bdc90bdce273e714ea67efbe68b0d112f16ddbeb54a67d` |
| mods-ocscreen-C | `fc3d660f9c500f8460e1504e55315e6a3beb4353e658c85cc7a4988084251c8b` | `client.out`: `f2ae34ae7d4e967bd706804afcd6bfeb47418d92d8166051ff1318c20ee45d24` |
| mods-itemgui-C | `bd5b87950660b49d529db1f12c6dd98a2c591b567461f55a463badabb94ea3ea` | `client.out`: `9b5893387bf8f6087267da4ff443c45e8f93fd4c24c42b198c8f4ce9d1574cf7` |
| mods-hudcache-stellarcore-C | `ee72a404c00920ceeb30a8d9b15d6119a8b3f75b31469fab4ea5e764a0408bd0` | `client.out`: `99aa59f3e9a26e6bb9ab5d7d1ff0800b1a9b1cdc2f4858516198ae99cb1c4b74` |
| mods-hudcache-stellarcore-D | `67cb795dceab8d4fb87ea83b354cb730561de448704c1f8a15de1cf31db88391` | `client.out`: `a3e0b081211ee259bbd1b29398128d8a8b9da63679c3beb8c03f167abbd065e4` |
| perf-vanilla-a0-compat-A | `24edb51d546379c9325173795ac87e9248e401cc502b6e3884679fbc0c967d22` | `timing-vanilla-douira-a0-compat-A.out`: `7fb77066e71e5cf0fccb4241b929274b1d85f60afb578c4f1858f97ac8cd319b` |
| perf-vanilla-a0-compat-B | `ce4614a39cb4114ad3c5a8b9d144a7c021ad320f1bcc448a2e0f8c1c3afbe542` | `timing-vanilla-douira-a0-compat-B.out`: `2d9d2c831251971dbbc306e468f7eebc8d381efa7c6f850eb62f3fe753e30556` |
| perf-vanilla-a0-core-A | `3413c55b125cd68603e6ac11b57d03446a4cd9a2a039dc8fb88cd3484f65f22f` | `timing-vanilla-douira-a0-core-A.out`: `b67a96db812ed15d99ccf81c95310d19a91107657564f5cd7183f4de1fee34b0` |
| perf-bsl-a0-compat-A | `0490a547cb32452a504043c394eb6febb7e2dfea52cf970851086faf46f190a0` | `timing-bsl-douira-a0-compat-A.out`: `19d51adb9906d232c72b23299a578f4eaa5a29d55b8293766ba38a9129923c0d` |
| perf-bsl-a0-compat-B | `595f3904f10b0145f89229196f00c5201f6e5a9eeae8901312ffa297bf9ad9ee` | `timing-bsl-douira-a0-compat-B.out`: `70529588b797fc4627e643000600344a37eaa2650f0338c3acfa24fd70b54f38` |
| perf-bsl-a0-core-A | `6f5e42468544e0a73cd8157f24e57e18a8757512e945eef46be8cba74e4437c3` | `timing-bsl-douira-a0-core-A.out`: `25c8f24908af9f621ea6ddce254d66b6d703e993115a13f1e45eb4ad2f471df6` |

### A.2 Frames

#### compat-profile-A2

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `9c6bfc2fed50b713849648cd4315758eb18c4976c194d4e98b7deaffdf03ecfd` |
| profile-1-portal-nopack.png | `e48f2c94519fe90a2601a9e9e166bd5152216d4bc2edf5e079bdf34f423c0bf4` |
| profile-2-portal-bsl.png | `ad933c72fde91195ceefc65812609cebf03cd25c482e069d7e83c15b6607c0ed` |
| profile-3-bsl.png | `9f225db56a518399bdbe14cac7a48b306f6324a80147194d177cb1a0c4b5cd56` |

#### compat-profile-B2

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `298e6ec5ebc5ce21d0b554fbcf5379ff750465529a1983cbd4484ae5b325cc4c` |
| profile-1-portal-nopack.png | `0eacdd5dd67c2b8b7a56e657c6ccce2b83670c8cfc32205c43f35051bf506755` |
| profile-2-portal-bsl.png | `73a2ec0330cb46ff3d8d7276a6b75f59488e4dde17a997ee3272c813b729dfd8` |
| profile-3-bsl.png | `0f79321ea714c33f0db3fd1e5fc65e7d11c60eeab8efcaada17c10f9d5b380a6` |

#### compat-profile-A

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `acf8e026d20a5d0ef9f0baa81962e49f3ce4b7b7fafa4a8c583abdf5511ea9b9` |
| profile-1-portal-nopack.png | `a31b4b0e14f4f27a2a4d65d3ac0a5ea2cd234fb2f84b8c1f567d81af99be8c8a` |
| profile-2-portal-bsl.png | `4445db6540a8a338f032285802e5bbfd40b47b151e4af7dee221a92627394e08` |
| profile-3-bsl.png | `d0888fb102186d389e7abed11994a9e3594dc32714be4b2f9010943052f43d33` |

#### compat-profile-B

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `7688b8c9f8d9eaa2fe178f6274783d990e3560ab845fd486dfe47c302d0d7be7` |
| profile-1-portal-nopack.png | `6c15eeed3b7029fd5e60cac8838f2a0399a4ab08e2d0391240a3dc72c83b9e14` |
| profile-2-portal-bsl.png | `a08233093d48c1e3dc52f1ed1f865112f57d68021545643c709168f4845e2ac6` |
| profile-3-bsl.png | `48b2c1dc535c60a8381699838622a648150578c926a38d4f129abfd011a21a9e` |

#### compat-sweep-A

| frame | sha256 |
|---|---|
| sweep-0-nopack.png | `67614e7e1ff3074e95596d6cc84ba59277b16b222cba01d9ca2f0694700aacc9` |
| sweep-1-bsl.png | `c3c562c8b41b10e1ebabdf63c10b59f1e72f53ad533949ff3f1fb12b13e10d48` |
| sweep-2-complementary.png | `cd895ae4f592704e0bddd68cbf44297225ec55de0facf3052390afeaa20f2c0a` |
| sweep-3-ilikevanilla.png | `22157889f5b9301db0c61d0f32e850596ead5bae95156eeaf570387ab9bcb6fc` |
| sweep-4-off.png | `2b7a5b90ae452d946ba852bf7036c635b1a87482d2c2cec66a63d98811eb543f` |
| sweep-5-bsl-cached.png | `d627906254d605c369b53af89ff39086055119b01dacfea3f3d684d040754495` |
| sweep-6-complementary-cached.png | `601688a60be7cbaa452de67eef579c77f7bfad681a9befa63ec7968ec3972e7d` |
| sweep-7-ilikevanilla-cached.png | `3964f2229f069dac8230d0ed1f96ebd32ca2b3213980a306b2d9c469b0c8c28a` |

#### compat-sweep-B

| frame | sha256 |
|---|---|
| sweep-0-nopack.png | `7e96c3e93eb7286308d2bbb1f2f814edff5bba4c0e1abcaf8fd7e2fc5847b641` |
| sweep-1-bsl.png | `3536acbaf5cdfa7e6093281db23a52dd8f2230adcb1a73297d401547fb47dbeb` |
| sweep-2-complementary.png | `e86c279fe16f049e46069684bf4f57a482147c0b5f77e2d8a6a9f9b0658464a8` |
| sweep-3-ilikevanilla.png | `b304052bcb9b18956c085f69610de9ffaece7c31b6c310984cccd60b1c5554c5` |
| sweep-4-off.png | `6380912236a35549520d18297f7d720cd3d4673bf29b76f35ecf986c139c37d1` |
| sweep-5-bsl-cached.png | `777f7d8cddc7b6944acd308e51ad0a45871734a9eb78bd9050f03e5b6db81aae` |
| sweep-6-complementary-cached.png | `fe814f32b2721dfb2c8b6292035fe205cbe15199b71c9c0b0448dafa3a2d5912` |
| sweep-7-ilikevanilla-cached.png | `736ed8ebc2e3978b5d7164082a90a778f52bde9d1b8918be648db0948010892a` |

#### compat-sweep-C

| frame | sha256 |
|---|---|
| sweep-0-nopack.png | `bd4134e3c1dc1db2d128e918d7398abf13ea49ea28716a7f65b55db88a86f13d` |
| sweep-1-bsl.png | `5c0da92c04ba8e62b96219fee74e687a150c0ecfac057e906fe58551e5349d7e` |
| sweep-2-complementary.png | `021edc5dbf72067c1cc47b7897ca82ba9dac7ef3d8d6daf0ab3ad7f62671fbdb` |
| sweep-3-ilikevanilla.png | `104a1a080fe266c39fc699599b33d9e9b858caa1e6e364c2d17f136cb5098ca9` |
| sweep-4-off.png | `ae1499a65c866045f60d9d85e7afef5ecf02f56db82d17e0accccb3f853b3523` |
| sweep-5-bsl-cached.png | `368bc639c35ee95aae3a044b5fb6320d7d3496316f8d96dbf5e70d9b9d3f55fe` |
| sweep-6-complementary-cached.png | `6dfb20c1e5b417427452dc7f742d06afbc917e168b49429f200bced33eb7e178` |
| sweep-7-ilikevanilla-cached.png | `cfbd5dd9bae91905141f3ecaac8c0fb5b0cd9c9632ecd46cc4f8a3c312d1b4b9` |

#### core-profile-nosplash-A2

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `9b512432af9730e5513f2f420960f3a02905074372a03debbb85fdce8ac9c6b6` |
| profile-1-portal-nopack.png | `d3ec8e48c4f53f1fd1853dc65e2c3769b3df1e4e92c6706993d5d6e8fab2fcf6` |
| profile-2-portal-bsl.png | `00df36f59b4717825d15ea883071984d0278d5e699f5bb7c75872941bff19829` |
| profile-3-bsl.png | `8b4fcc5f9a83b7dd025a230e8a9bf178cecdf384b07a4aaf950eaa5e1784e71d` |

#### core-profile-nosplash-B2

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `70346f8bdce7398ba7623400338d4768fc38bc91b0d35905dc618f960d01c6d7` |
| profile-1-portal-nopack.png | `f6e13ae1dae8f0f0a8f301dc8198ae36ca2032ff816b17e17582bd39588b9a42` |
| profile-2-portal-bsl.png | `ac3399bab81b4340a0ef733a024a959404672d2d525ef35b6d5db73805f2fc29` |
| profile-3-bsl.png | `5ccc57fd3d416861d117fd2eb68b7acbc817655f6f23fec4459215527a474575` |

#### core-profile-nosplash-A

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `6ed4a142ff468afb7bf20a1db1c4087e000aa81854e7f43b4726f17ce67067df` |
| profile-1-portal-nopack.png | `bd7c4d926635aa107ac4b95c48c798ed17cf692ca9d6f30af297499cb56f89f6` |
| profile-2-portal-bsl.png | `92f6c5ee471cc82a10d5be23d5ad8727dfdc049cd0b462091b408dd9d462ad63` |
| profile-3-bsl.png | `9f9b827b62ceaa02e5ca3f472b594b919dd93ee17f88f1a2779c4f29ee4bc394` |

#### core-profile-nosplash-B

| frame | sha256 |
|---|---|
| profile-0-nopack.png | `19507c786f54fed5bfa3646b56d6f6c06256b8b7a6b665b6a62e74bc2c1a93e0` |
| profile-1-portal-nopack.png | `e6cb57373333f78971e0cb8ceb125b6714e7b2f65e6cf24501052b6083b89d4d` |
| profile-2-portal-bsl.png | `7e63d26ccda6d08b0514d0157f521b59e524aa19c4faec387837f06c3a2c7d96` |
| profile-3-bsl.png | `2a48c9f4ce673bcaf81321b1b0c605aef3f8d79bc3489a82347a9914273a4c08` |

#### core-sweep-nosplash-A

| frame | sha256 |
|---|---|
| sweep-0-nopack.png | `2f91f1a8189fd9a1803f34003dc2770cbe7622c01e26e6c232ed9205078c4c61` |
| sweep-1-bsl.png | `417305445b5a557a0970aee689b813f997292358dd452509b2eb9a65767f95c8` |
| sweep-2-complementary.png | `b7d8c0d8bdfe8fc4492ec68bbb0090565b48ccf406428ff0380f425af0f46d83` |
| sweep-3-ilikevanilla.png | `485d1803e08d824660d39b923bff82c42bdd35e9350c64058db5d72ddf0f4b5f` |
| sweep-4-off.png | `d32869e48ec74c0cfc3930ccd1f7a94bced8dbd7248ef4423bac5142d767a6fc` |
| sweep-5-bsl-cached.png | `a4f6e1b2a29777a962f3d53078f8cea6f7e103bb8ffe8ed2acb967b65f9e4881` |
| sweep-6-complementary-cached.png | `08978f96fb663a1e1f6648af79e92c8bcdbc268e62789266aa40c5be00f4ed6b` |
| sweep-7-ilikevanilla-cached.png | `751f833d9b89e890e1c7dcd62544094b44fc7700a2f09186ea1f1a890f93c22e` |

#### core-sweep-nosplash-B

| frame | sha256 |
|---|---|
| sweep-0-nopack.png | `1577b3ff3d8c4c63f290e6a01f86487f56c37904d9c999cfb0d161cd3a720591` |
| sweep-1-bsl.png | `3463fc9fa1dc291f51ef9ddbbe77fa671ac9dc976d6de1a70a3c9da2e3690b8a` |
| sweep-2-complementary.png | `bc636418cc880bc5d411d94dbee5e12d86e6d3a39fe55c6ec5a7740c86921790` |
| sweep-3-ilikevanilla.png | `59b275a094eb25231ebe0d635a76eb1f338521aa29e3ff5b23d17ef6425d9cfa` |
| sweep-4-off.png | `0753538c689a2ac2e1baca0f330fa9cd31bc8b67512b8496f0eb8969dfbcef84` |
| sweep-5-bsl-cached.png | `f72fec0b329b80611494afc8e26a1087d87d08548ad89b7c5d91e9657b3c3dbf` |
| sweep-6-complementary-cached.png | `a50b28e45731ce426f09696eead21bccc2f3701870b48411558b4dda357885d5` |
| sweep-7-ilikevanilla-cached.png | `d76d11a8126471ac717a65bc055cd36805d3a379c5410d50cac03ce5c484861e` |

#### core-profile-A

No frames (the client crashed before the first shot).

#### core-profile-B

No frames (the client crashed before the first shot).

#### core-sweep-A

No frames (the client crashed before the first shot).

#### core-sweep-B

No frames (the client crashed before the first shot).

#### mods-ocscreen

| frame | sha256 |
|---|---|
| ocscreen-0-world.png | `dc8fb738de8fad87cca9807b5cb39f77551a29917ded9f1367e5af1157e19d52` |
| ocscreen-1-angle.png | `8272a0d450e48e7618ccb06cb8c3dc104b9a48777b164f6201455dba733e0ac3` |
| ocscreen-2-gui.png | `68a2fa37c3d3de46dc5ef8b36171bcd8a7fdeea81dfba181bdc38ad5af75efcf` |
| ocscreen-3-bsl-world.png | `9622d64c763601297d1546ea1283882531b965b8585fe7ab0de9f6d3432033b8` |
| ocscreen-4-bsl-angle.png | `d1e5d829a5070891c6fb363cdb33e083f91ab75379ceb383a0d244434f096462` |
| ocscreen-5-bsl-gui.png | `412969aec7039dd0309b1e5b39b72b67ca5947bf1eb3a13f557ff36fd1246fe8` |
| ocscreen-6-compl-world.png | `1877c7793aec3d979780d3bbbc84d02c01c4d33b1fc96e5eec3808a53ccdd5f8` |
| ocscreen-7-compl-angle.png | `a1eae2f2bbcdb03ac970466398eb649409c6e8493b1d75b3c26b5a5c059ded0d` |
| ocscreen-8-compl-gui.png | `6bf578e6f6f8eeecdfd3b86be68d435eb2950fc1ca782cf91068498908dd0b96` |

#### mods-asmodeus-sky

| frame | sha256 |
|---|---|
| asmodeus-sky-day-horizon.png | `cfc5c4b08e8862b2e409bc4ebbbc2e770de00daf4588e662914ba4ee0cf2cbbe` |
| asmodeus-sky-day-up.png | `4c85052ee097addd4c8b2931a397fbd62f0b9e32b03346adc4ebb6b22d8a2a53` |
| asmodeus-sky-dusk-horizon.png | `74ddb0cac432441ae82248b27ca5736fb20818a7ccbde39a8efe82eb66040bdf` |
| asmodeus-sky-dusk-up.png | `a679fa14e8dbe0ea0ccca8dbe6025211f25f6d09952b31a1e88e13c94759c33f` |
| asmodeus-sky-night-horizon.png | `b3b84999651c1f880b48327e678ec16ead3775c9f24ab40c38589cee7621088b` |
| asmodeus-sky-night-up.png | `8e1d4a89bdd4174ee0a691513d29676e8a92768a08a8a8da7e1f336e9a685d76` |

#### mods-itemgui

| frame | sha256 |
|---|---|
| itemgui-0-world.png | `c247064004d4a4270fa7f3e1e4f7d5985a271dab116515c45afbc6ee30d8ef58` |
| itemgui-1-inventory.png | `43ea99d5c942170ff162fbbe81f026e89a81d84bbf22448688ad06c932f75acb` |

#### mods-hudcache-stellarcore-prime

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `caa3d2c7243f1c029509b62091647d89bd527cb5d120c116b7b81a171dbe7a3d` |
| hudcache-01-vanilla-b.png | `1d5d016c1759d04cda0773d486840e95256f0868943f5fa2b84ded2a93ff85dc` |
| hudcache-02-vanilla-c.png | `a31c35795afc06c788d9e9692ea2f4f873a094a617da42b982d5b5257e559ff7` |
| hudcache-03-f3-vanilla.png | `4504203f809e65c729a9a01a7f328d818713028da04e926e35123c0b6a76bc2a` |
| hudcache-04-bsl-a.png | `17481fe4a0015efaadd5060e987e76c32bcb45a5dade174a1a31d0eb9fd8c5c6` |
| hudcache-05-bsl-b.png | `7a1df9b6d0306a527d2a9abe77f3a7ef31fd0e573ab6e781b1ee68a27893573e` |
| hudcache-06-bsl-c.png | `0794d3b41d545d6e067bc251b5536e7eab312c677f78136ab368e7523fb36ea0` |

#### mods-hudcache-stellarcore

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `eda9cd93a9fbf15b2bd7f44179a47f0c50a463291c7d2b3c4c62168ae792af75` |
| hudcache-01-vanilla-b.png | `371071cc47da79ed3c8924a7cf9d7a56f6deef6819c62c861d7e8b2986eabe87` |
| hudcache-02-vanilla-c.png | `371071cc47da79ed3c8924a7cf9d7a56f6deef6819c62c861d7e8b2986eabe87` |
| hudcache-03-f3-vanilla.png | `e2b6b41100e6fb89739a8a8976d25dd08bb86776a3c835e89e6daf0600d2b500` |
| hudcache-04-bsl-a.png | `fbebfd0d64ef26141b018d6526993beb5ed7e37b1aec406d99bf2ad90f4e6f12` |
| hudcache-05-bsl-b.png | `ca64d14394e0929abf9423b9c0ed0d6edb911e51f1fe1a37f78da8dadd060839` |
| hudcache-06-bsl-c.png | `d0085dbca2b3e892bdc1c8e4707d0ab38de0874fb6d2dceb8772bbdf6372db6c` |

#### mods-hudcache-gnetum

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `b9d4926b993ef8c862d90e6cf7ad6d3c0e9465cb37995640e78134e8ea50d3b2` |
| hudcache-01-vanilla-b.png | `b9d4926b993ef8c862d90e6cf7ad6d3c0e9465cb37995640e78134e8ea50d3b2` |
| hudcache-02-vanilla-c.png | `b9d4926b993ef8c862d90e6cf7ad6d3c0e9465cb37995640e78134e8ea50d3b2` |
| hudcache-03-f3-vanilla.png | `9405aaef03fd337add4342c7584d6b300caff2d8f704f267fbbf0f4669f2be94` |
| hudcache-04-bsl-a.png | `5b92a5a0ae2127466b8424c3a5a17f03224b91907645b99cedc02efd951f903d` |
| hudcache-05-bsl-b.png | `6bc4a2182c8b8313cf89b909ddf6d93c8ec872fd34e5f35432da2d98bbba4cd0` |
| hudcache-06-bsl-c.png | `2d73d5acf881322e8091a310905a19ea961d250791cd106da885c6491b369040` |

#### mods-fontpack

| frame | sha256 |
|---|---|
| fontpack-0-menu.png | `01d0bdfa9e47fb9783d304bbb9756e0d6d12b1379c10b795e603609683d336ce` |
| fontpack-1-reload.png | `48219559d905b5b4956ae5a7641093f1ba38f571e70f317a1f411cc14677e70f` |

#### mods-dh33

| frame | sha256 |
|---|---|
| dh33-0-nopack.png | `c942092054d8f9e5368a6c41b05b36caeb56f385c933d4bf5102d661662df869` |
| dh33-1-compl.png | `d2ebf07d2eed8483f40062e847a0b170f88b3011a5bde446674724fdbebfcb22` |
| dh33-2-compl.png | `b2b7ec618b040c96cd60289963cfd11b866f275b9932ad4b0e2d5981355e7f00` |
| dh33-3-bsl.png | `dd0a22fb67fd887399ee0d360c5b514abfa19dab987e5b89f2b594ae2bf801f6` |
| dh33-4-bsl.png | `75f5aadb468c85069225b9535ef0ae46d29587594875d991c6092a908e888f00` |
| dh33-5-bsl-low.png | `5e8b6266174b1a3d0e72b84ee412667ee4a695250933f5e5ea30e043dc688c56` |

#### mods-chunkanimator

| frame | sha256 |
|---|---|
| chunkanimator-after.png | `cea6103f7c14be2ba12e339748ef32be17bc394ca8c8436bbdb8cb9d6f201e49` |
| chunkanimator-anim-a.png | `25c34b7b1a1752144b34a83b72b6316886064cb4d9f22c2f39336e0a99ecf151` |
| chunkanimator-anim-b.png | `e2cd9a7e892835ccfb0d813fcd5c77d97973ab23be1468744a5928a9c5416a98` |
| chunkanimator-settled.png | `1fbdd7de4ce06fa5c1511290ddba9a14cad6c559a5226aec6ac97c3a56240e59` |

#### mods-renderlib

| frame | sha256 |
|---|---|
| renderlib-first.png | `4f64c22ac3d33d86bf171638e72f6e41d573abeaf495cf32723e367ee1a687f9` |
| renderlib-third.png | `194c80cabc8a4a3fa623aaada2e8d3fb3bd6c27cb676e2f671214553760e1080` |

#### mods-shaderpacks-scroll

| frame | sha256 |
|---|---|
| shaderpacks-scroll-drag.png | `42675975f3604db90f7539afa6145778fab494c87f883a604aa80e65bd9fd174` |
| shaderpacks-scroll-wheel.png | `3fb8af41f2c5fbcad736f2d7ac1080d2f12dc2cabd9410af87db61b80dc21352` |

#### mods-menu-modernsplash

No frames: `menu.txt` shoots none, and the client crashed at "Initializing game".

#### mods-ocscreen-B

| frame | sha256 |
|---|---|
| ocscreen-0-world.png | `965181e0e2df767b94671a0e003de9003fef3af1088aae7917ace868ce254a9f` |
| ocscreen-1-angle.png | `d36e42e3390a8e0897fb19cc7cd23d715d0ea1cf18965db6ed5e7010ceb59933` |
| ocscreen-2-gui.png | `cd69f10fead3566338f3b77b2897652aab0fa9a5be6c99d206df09127ad92c44` |
| ocscreen-3-bsl-world.png | `a6d74e06f30d30a80da341951ff67dbe77f0b2b678817d8c0be3376450850d84` |
| ocscreen-4-bsl-angle.png | `468fdcf5a2c06a5d410d73839af1512150ed7a18cd0e6c4f3e7823641095b6c9` |
| ocscreen-5-bsl-gui.png | `d6910eac843191c800a676c8aae45a6ba8e5d007ce15ded2aaf997986146695d` |
| ocscreen-6-compl-world.png | `5b2975ecb40ad85d299748776609181fb54ae484f2ed3232d68c51f118561288` |
| ocscreen-7-compl-angle.png | `4d1d81c5d89568bab6b5add4ff59a076e2a3b1cfb6494188b8e55f6ef3c15416` |
| ocscreen-8-compl-gui.png | `083b40a23160d1c105b46fef6aca05370b05339a5a8040ca04ed78130e9d4018` |

#### mods-asmodeus-sky-B

| frame | sha256 |
|---|---|
| asmodeus-sky-day-horizon.png | `b7ad586838ec53a1e681555d31fd1c7ba3df2b098311e2b7465f0c7b07bf79df` |
| asmodeus-sky-day-up.png | `91558c5eaa9c226478c7fb93a4364ef3fbba6c5cfa7abbc6d50db112e67bbb77` |
| asmodeus-sky-dusk-horizon.png | `56f45972885b0ce1eac90cded4e59abd928cbd3d392e6e3be4a622e6327f4fea` |
| asmodeus-sky-dusk-up.png | `a456efae0c08c0e7d08fe5d4267bf4bade01daa9c25506295264272361a16680` |
| asmodeus-sky-night-horizon.png | `f412b8c929c55c13de4d1507bb6b052f4f6665e7812145b173a09776904732e9` |
| asmodeus-sky-night-up.png | `7f87982c64eec4426e7f6fb31a83e8362339d062b71d4bf1d7c6bb02f2ada5c4` |

#### mods-itemgui-B

| frame | sha256 |
|---|---|
| itemgui-0-world.png | `134a6e0ecba759c7c061c63c4f4c614399b5224541cf0743d0e3c956a343b50b` |
| itemgui-1-inventory.png | `f778fc11e6c4b1d7ad473e5f89705b1f3129cb46c62ff11c8ba028eac1a56d65` |

#### mods-hudcache-stellarcore-prime-B

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `6b03d4297973ec50a4129adf3ce67477202a2537b11d65e9e6a72d8f5faa1752` |
| hudcache-01-vanilla-b.png | `6b03d4297973ec50a4129adf3ce67477202a2537b11d65e9e6a72d8f5faa1752` |
| hudcache-02-vanilla-c.png | `f2ecef2fa4ea98867a195c0c186717a3601e76dc0c41eac5556fa2ac61d1c807` |
| hudcache-03-f3-vanilla.png | `034bd7e8b3977a7cf7d60aaa2621cae8b1e4333f28f9aac5e3053e32ef6c74c6` |
| hudcache-04-bsl-a.png | `23df44236e53fc0f46b3c6fc09e3097a6fd25bf5b2da1b2541f0a74ad63a48d3` |
| hudcache-05-bsl-b.png | `525c2412adaad860bc2e05824de5dba0e0780f71f3a1c67f47a5c0d7331af7fa` |
| hudcache-06-bsl-c.png | `a73910906cd3a7d34c2cc8ea49754757814400c11615b231730b8ba46f3f10fe` |

#### mods-hudcache-stellarcore-B

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `5e2bb0c7a23887bdc05da4adff059b4c915a96b000a12eba8aad27d3bfe2d195` |
| hudcache-01-vanilla-b.png | `5e2bb0c7a23887bdc05da4adff059b4c915a96b000a12eba8aad27d3bfe2d195` |
| hudcache-02-vanilla-c.png | `5e2bb0c7a23887bdc05da4adff059b4c915a96b000a12eba8aad27d3bfe2d195` |
| hudcache-03-f3-vanilla.png | `ff2af244ccd40b9c5645e4a65d42ef0a8aa383cd00bf7d5508223c91c65e1a7f` |
| hudcache-04-bsl-a.png | `c820a3f853f03058cd7ae5dc5b3edfcc92e1659851c5349a474245cf6474e6f1` |
| hudcache-05-bsl-b.png | `1777b68eb3082e8b9c8934f37178a76c365600b7ba772e66c2859544f88406ff` |
| hudcache-06-bsl-c.png | `e137f396a0bb1e1d63c49a14074a640b6c681cfd56f99349ca83f0059049ef34` |

#### mods-hudcache-gnetum-B

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `7b0b4938f7454ffa0ee61376ef44ef0caad46fa852f49c704494672acc603c06` |
| hudcache-01-vanilla-b.png | `7b0b4938f7454ffa0ee61376ef44ef0caad46fa852f49c704494672acc603c06` |
| hudcache-02-vanilla-c.png | `7b0b4938f7454ffa0ee61376ef44ef0caad46fa852f49c704494672acc603c06` |
| hudcache-03-f3-vanilla.png | `140d776f4c84ad9b12df6985939f84d53a745e805cfc8cd642695f113d8a2705` |
| hudcache-04-bsl-a.png | `ac92fbb3dfc866b98f9b6b2d9324112fcd82d5d68bae2451ac5a632f52096976` |
| hudcache-05-bsl-b.png | `bf6f0170535ccd6d4b2511682af5e04397d4e9905cae0d3d36e2bb67519deb06` |
| hudcache-06-bsl-c.png | `12bb8db989f6f8393d616c32940ddac58503030776023dc7730c40788a1a909b` |

#### mods-fontpack-B

| frame | sha256 |
|---|---|
| fontpack-0-menu.png | `228e6ab1ae43930fd23e8e2b421e1cf1945f2eb26feb66ea21bc92cd1e94301a` |
| fontpack-1-reload.png | `3263d862148afbba7b6cc619c88d7f23c6803a155b68209f331ce43a0c33cb44` |

#### mods-dh33-B

| frame | sha256 |
|---|---|
| dh33-0-nopack.png | `640fa62d6c170c50749d4d81eae5995c63a1f0a190705e9b9c6dbdddf665a2fc` |
| dh33-1-compl.png | `bfb01f73a339acb979c7ea707d27a6c34e10cd1391ed5465c909c94a2c4a4ace` |
| dh33-2-compl.png | `74220f9e99f0d5ac282f6a8c4872d1d0a59548c4616ffdea2fe6c1097b7d9129` |
| dh33-3-bsl.png | `224a4cf2c08e1d2e674087e2249e2b11dbef216ae39fb273c08144b39a019511` |
| dh33-4-bsl.png | `e6b6d89412aac7cc48fe628be7b3b3beeac353d54398df83e8a56a4571e326c4` |
| dh33-5-bsl-low.png | `8581433d37d511ebc8653c27245256eb459632faff99fd178eb72e9451174430` |

#### mods-chunkanimator-B

| frame | sha256 |
|---|---|
| chunkanimator-after.png | `cea6103f7c14be2ba12e339748ef32be17bc394ca8c8436bbdb8cb9d6f201e49` |
| chunkanimator-anim-a.png | `9cb719ed8d7e5bf70e9cd6a30046fa5589767ad40695cc2ef60955441c130412` |
| chunkanimator-anim-b.png | `27147667f6402edd384ebd157458dc578b1d5c8cc8d5aee67c7959d19052a7e8` |
| chunkanimator-settled.png | `1fbdd7de4ce06fa5c1511290ddba9a14cad6c559a5226aec6ac97c3a56240e59` |

#### mods-renderlib-B

| frame | sha256 |
|---|---|
| renderlib-first.png | `8cfb0340d7b6e38d39876d0ba4e17ee732ca3fc94e1c77e7c23147ef9dbe3de0` |
| renderlib-third.png | `ac76040ceb4443c04e565e8e4b1ede52205510fdf1da176507211a7d5551cbf3` |

#### mods-shaderpacks-scroll-B

| frame | sha256 |
|---|---|
| shaderpacks-scroll-drag.png | `a6cb90f8585de0849ac83b1a4f74e688765db13e7dbf4782ade94bcaaf8ea12c` |
| shaderpacks-scroll-wheel.png | `234a6a99b781a6ae6bc358bfd4ae07b6b7874488bc10caadfab8b4df549ae9ff` |

#### mods-ocscreen-C

| frame | sha256 |
|---|---|
| ocscreen-0-world.png | `bc3934a6cd81c1bcfc65776c60658b7c14ba1fb1d4d1b14a5ae6d5be9f6cc4aa` |
| ocscreen-1-angle.png | `48ff59605c70ff6d624e00494b6ded4a9879ae20078befab30d3726d8c77aa47` |
| ocscreen-2-gui.png | `7897aca36b06c1006ac9785e8e8b2803996c3d42b694f8f367ed948ce11c47df` |
| ocscreen-3-bsl-world.png | `ad360c8d990ac4d58c409402fbaa9a1e5fa6e3cd0c0a0a030175939ee685705a` |
| ocscreen-4-bsl-angle.png | `cc4fcea6a22b4769026b25c46bb5c5a041243f99735dc803697981717dd1b73a` |
| ocscreen-5-bsl-gui.png | `dc520496e4459433e9adb0bb2de16c854c21ab948da371caed352bd07e8f0aa8` |
| ocscreen-6-compl-world.png | `30d96d5508015d0f43f86067c4d5ca7fe99ab5b9e7c0c4ad7b60db61fd59f1ca` |
| ocscreen-7-compl-angle.png | `2e3cbe7ce5e5cae8833e3dcb5b8537d033669d2faab226faf475e4437fa372d9` |
| ocscreen-8-compl-gui.png | `d7d714ffd976792d72c962cb499aec247e7e02bc7b81c80f428db36192d83dd7` |

#### mods-itemgui-C

| frame | sha256 |
|---|---|
| itemgui-0-world.png | `e547be604866fe241670801f3d686bb6c94f65ce7d1381d968c87ce53e5df1e5` |
| itemgui-1-inventory.png | `8a55bf5bd6b4f65d77b0badb61efe1f7a987396362f48f9f787a77606ee14cdc` |

#### mods-hudcache-stellarcore-C

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `d264ae73d110622f6c35dd5c3e6f3e9ba9ceb81c685a11110054029e07eaff04` |
| hudcache-01-vanilla-b.png | `4bd7a467793b7fe597004b927be160653839fbadf84ae006e4a6d1c7f308f001` |
| hudcache-02-vanilla-c.png | `67469ef28c749bb8b8181a91b62a6aa41cd96a1540f9849366abc9e3aac61a97` |
| hudcache-03-f3-vanilla.png | `2182007c6e4a42d86f6898d339dbc599b84bab15eae1c6e86e2e7a74b324e427` |
| hudcache-04-bsl-a.png | `c4bb5c0864e0695fbe4d15fe9e09dcdd362879b88130ae0e38c6b054b9cce21f` |
| hudcache-05-bsl-b.png | `24c46829062b304bb7ce3f14e7fd44581c6e4ec03c275995db1d1fb299c77cc4` |
| hudcache-06-bsl-c.png | `088c933127e5adb9c1d65d09540ed055fed089f677e963db2b829c6b2eeb92c5` |

#### mods-hudcache-stellarcore-D

| frame | sha256 |
|---|---|
| hudcache-00-vanilla-a.png | `9db6528bbe14702284f49a38d22b76c06eda89aa49ff21d5935df93b972ad3f8` |
| hudcache-01-vanilla-b.png | `9db6528bbe14702284f49a38d22b76c06eda89aa49ff21d5935df93b972ad3f8` |
| hudcache-02-vanilla-c.png | `9db6528bbe14702284f49a38d22b76c06eda89aa49ff21d5935df93b972ad3f8` |
| hudcache-03-f3-vanilla.png | `5d11878af27814b2c54dd4547ef2ded9b89d51cca6e1b7def454e7ab5fcb6126` |
| hudcache-04-bsl-a.png | `2c58de233e6647f37574129361eeb75ab4993855e89960833137dfb1cba66b3b` |
| hudcache-05-bsl-b.png | `00ea8cd13607fe4c1891419c85a27800d8dc0b3d18b74972183dc4bed5ff22a5` |
| hudcache-06-bsl-c.png | `1a4190a7241f9d0b3feeb6941af622c0b64f7aca1b66d3278d1555586a73ee3a` |

#### perf-vanilla-a0-compat-A

| frame | sha256 |
|---|---|
| corpus-vanilla-0-nopack.png | `f9c599c3598e9110ab5048fc65f0c3da9a1c59e3f6042ee50c0561d2e5173464` |
| corpus-vanilla-1-pack.png | `4d8e14912cbf27528b0f95dbb2c1f35fdef068c55a984a0a63361a401bddcde4` |

#### perf-vanilla-a0-compat-B

| frame | sha256 |
|---|---|
| corpus-vanilla-0-nopack.png | `e73ddd5345692da7dec45ef10eeac807018225c17f42bbab6b3b2264d41f1458` |
| corpus-vanilla-1-pack.png | `39bafa5f9be166e5c45326de6ad1f1c198f94e74f7528296ce130c742ab73fa9` |

#### perf-vanilla-a0-core-A

| frame | sha256 |
|---|---|
| corpus-vanilla-0-nopack.png | `f99c2ef6d1698315f612a1273fbbf34b966c9ba6064c6df3e1ffc52214f7354e` |
| corpus-vanilla-1-pack.png | `89d21a98f356e9b330b6c55efb1aef0cfd8427bbb47a83fc1f4b220132e727f6` |

#### perf-bsl-a0-compat-A

| frame | sha256 |
|---|---|
| corpus-bsl-0-nopack.png | `74472f0ee4656e3cd560492a9d35d7521efc2e0f5d56738173b35c599ebcf99b` |
| corpus-bsl-1-pack.png | `246f9af628b5957a9892cc6c36dbee276d0fac036627bea0efae4c2511dd91a1` |
| corpus-bsl-2-nether.png | `d0ffd5c82c474416ed2ff4fb1e0921efe2f0dc38d64f6c85db054053e2efe569` |
| corpus-bsl-3-overworld.png | `7944ae4b9dff202017bef9f5a03b78346131309c81d2dfc6d62d1c3f5e0050b2` |

#### perf-bsl-a0-compat-B

| frame | sha256 |
|---|---|
| corpus-bsl-0-nopack.png | `fc3375082a76e76d269356793cb5d7e9e79ffaf35d2c3ab3ae4ed62049d73080` |
| corpus-bsl-1-pack.png | `25520f46b3519a56c7c7b8cf83e2d32c86409ca553e86d23b15fcb88950bf63e` |
| corpus-bsl-2-nether.png | `ce9dfe7842d81485653ef9304404f263f8143f7ef684609e32dac20f45c7fe6b` |
| corpus-bsl-3-overworld.png | `e22c06f9939e37cc13459130dbdeeaf014e04e708aa51ceee899c0bde20348f1` |

#### perf-bsl-a0-core-A

| frame | sha256 |
|---|---|
| corpus-bsl-0-nopack.png | `a194aad60be54eb0ab4f844af562dbe27cddd58cfa36b2e7bac60412825450c0` |
| corpus-bsl-1-pack.png | `93fc2dd52d14a2157f5c9aa548d43341786dd957b59909479d4fa9a48d261220` |
| corpus-bsl-2-nether.png | `019783fc943ffd86b75afce27272ce3d255d4f8725f77ea5def0b0f8686db385` |
| corpus-bsl-3-overworld.png | `0877c9fd32670786c31bab60bc753713a6096984e7f9e5deadff48c85e5216d6` |

### A.3 Files appended to SHA256SUMS on 2026-10-22

Hashed on 2026-10-22 from the files as they stand (mtimes 2026-10-21 22:55-23:29, the capture times; 2026-10-22 00:20
for the B config copy, and 00:48 and 00:49 for the C and D config copies, which the retake step copied and appended
right after each run). Paths are relative to `<repo>/run/glsm-passthrough/baseline/`.

| file | sha256 |
|---|---|
| `core-profile-A/crash-2026-10-21_22.55.29-client.txt` | `b55b2cab95a43d56f5765fa63d62f738e821879030ba3d60563ba0105c2e4656` |
| `core-profile-B/crash-2026-10-21_22.56.09-client.txt` | `552c2067fbe99a3eba001556be2a8af19b33753aefeaa3ee62198116cb419e5c` |
| `core-sweep-A/crash-2026-10-21_22.55.55-client.txt` | `4b669628c69e022c96259a09fe64738c4627fbeaac4ad3286cecdde3115d346c` |
| `core-sweep-B/crash-2026-10-21_22.56.16-client.txt` | `57e5ed3ee20036a48c0c5f63f304c08ee9d28705092e67990a25ffaea5de237e` |
| `_failed/core-profile-A-attempt1/crash-2026-10-21_22.54.45-client.txt` | `da9df5400a33d15b4fa1d7c713a89b3bb87fe1bf40440574e7aa43fd1539dcc6` |
| `_failed/mods-menu-modernsplash-attempt1/crash-2026-10-21_23.20.55-client.txt` | `d080fc4e20e211e1564ef70ce15c128a865bea7eeb1073997b1aa3931638fadf` |
| `_failed/perf-vanilla-a0-core-A-attempt1/crash-2026-10-21_23.26.21-client.txt` | `919535e7faaf67791f3085d38aa09e76f2c66efba71061cb4450f76904fd7a8f` |
| `perf-bsl-a0-compat-A/perf-summary.txt` | `0cd876f702294d8f25082662c9e5533be48017c5233aeaf4a37ba07820875cef` |
| `perf-bsl-a0-compat-B/perf-summary.txt` | `1c4d001629f120ab60c24bb9c1812f94a01ed61459d4dfe22bc7fc2e1ed40346` |
| `perf-bsl-a0-core-A/perf-summary.txt` | `b0ec8b0811d8500b7de8fb5ea8918672afc481d7015a33a8c20d097ee6f5f55f` |
| `perf-vanilla-a0-compat-A/perf-summary.txt` | `05877c56f9cc92401c7e7f5c69f00735174784e0150ff6b3a1c75c5a4f4cc4de` |
| `perf-vanilla-a0-compat-B/perf-summary.txt` | `3187a6f74c4ef3a2ff079269b74a2bdd6d7e776e3c7a68cb0aeaf8b4875a9165` |
| `perf-vanilla-a0-core-A/perf-summary.txt` | `7f2bce1889f1a624385ffe36d7cd8a55bdaa36f02d1269125468999c1e2af488` |
| `perf-bsl-a0-compat-A/transform-times.txt` | `cf107a6401720d9a80105f3ae292ea775a978c3b267181601c8230aa6448c383` |
| `perf-bsl-a0-compat-B/transform-times.txt` | `ba61531b5c98447ff07cc6401e80a2f37bc161241d6fb685c9fbb1eea73f8cb8` |
| `perf-bsl-a0-core-A/transform-times.txt` | `383e6555b477e1f1a032bd48f1b121841960b605ce47d068627450fe09ac5d39` |
| `perf-vanilla-a0-compat-A/transform-times.txt` | `a0f486951b8ced6f16c2da402dc8713629fc71ea2febb81e7eb29677bbcd4f43` |
| `perf-vanilla-a0-compat-B/transform-times.txt` | `327e3125fb893061e846c0f171a94b973a33591c506aa168b1152ef4788d4101` |
| `perf-vanilla-a0-core-A/transform-times.txt` | `c3bb689b620403a9accd67b599b886a336e6704a00bed05a2e49a33d87c5495c` |
| `perf-bsl-a0-compat-A/timing-stdout.txt` | `b4310c1864092f7ed11db38827fd337441e42fc886b9b832c23782821c7aee86` |
| `perf-bsl-a0-compat-B/timing-stdout.txt` | `7b0cf503a20e0b88cd8a91185587dedb79d0be9553b70fbcf474afa0d859fef6` |
| `perf-bsl-a0-core-A/timing-stdout.txt` | `e318734610f83ee719c114f14bad7d8d07f3ed3607c61c936e6855fdb10bbb48` |
| `perf-vanilla-a0-compat-A/timing-stdout.txt` | `d9378222fa4cb360b236dd3ffc7ace29b9ae66a877c825b36ef2859b0451be55` |
| `perf-vanilla-a0-compat-B/timing-stdout.txt` | `b0c9d8d2ef057ab6b1d065b9f64318a128fc11fc93259a376b3baf272ce8a9c1` |
| `perf-vanilla-a0-core-A/timing-stdout.txt` | `e2f1dfdb3ad428616f09669ff93339baf4c968bb54c99cff420396d7564c1a0b` |
| `_failed/perf-vanilla-a0-core-A-attempt1/perf-summary.txt` | `59a2f9f2dece4d371bdc72236b93364df42ee2df112335bc74e908979dbf2580` |
| `_failed/perf-vanilla-a0-core-A-attempt1/transform-times.txt` | `ee18f50201b065495914c7adff8557441814aa6cfc1e77517ca28b59917dc31e` |
| `_failed/perf-vanilla-a0-core-A-attempt1/timing-stdout.txt` | `805988ac794168bde8936fa556dbbce20eefcbd9d1d18031a9e2863f58a3b1b2` |
| `mods-hudcache-stellarcore/config/stellar_core.cfg` | `039fd24db5455fafeccb4c9adb51906c05b3b4c1b08f1c0674a7a0f8c63015a7` |
| `mods-hudcache-stellarcore-B/config/stellar_core.cfg` (`cmp`-identical to A's) | `039fd24db5455fafeccb4c9adb51906c05b3b4c1b08f1c0674a7a0f8c63015a7` |
| `mods-hudcache-stellarcore-C/config/stellar_core.cfg` (`cmp`-identical to B's) | `039fd24db5455fafeccb4c9adb51906c05b3b4c1b08f1c0674a7a0f8c63015a7` |
| `mods-hudcache-stellarcore-D/config/stellar_core.cfg` (`cmp`-identical to B's) | `039fd24db5455fafeccb4c9adb51906c05b3b4c1b08f1c0674a7a0f8c63015a7` |
