# S08: flip the default engine, port the Iris-side tests, full run (exit point A)

Step 8 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `41fd5df1` (S1 to S7 and the
orchestrator's S7b done), on 2026-09-29 (UTC; the local date was 2026-09-28 when the step started). Times below are
UTC unless a log line says otherwise (the dev-client logs print local time, UTC-4).

## Status

**Done. Exit point A is reached:** Iris programs run on glsl-transformer by default, GLSM still runs on TauMC, both
libraries ship as nested jars, and the release-shaped jar loaded glsl-transformer through Cleanroom's contained-deps
path and rendered BSL in the user's Prism instance.

| Done when | Evidence |
|---|---|
| Default flipped | `TransformPatcher.Engine.DEFAULT = DOUIRA`. Every run without the property logs `[TransformPatcher] GLSL transform engine: douira (demonica.glsl.engine)`: the sweep (`run/s8-sweep-douira.out`, 23:50:51 local), both compat runs, the full `:test` (engine found in the test XML: `['douira']`) and the Prism smoke test (23:58:50 local) |
| All transform tests green on the default engine | Appendix C's set plus the two new classes, `--rerun` (`run/s8-appc-douira-1.out`): `BUILD SUCCESSFUL in 11s`, from `build/test-results/test/*.xml`: `classes 20 tests 530 skipped 2 failures 0 errors 0 engines ['douira']`. The same with `-PglslEngine=taumc` (`run/s8-appc-taumc-1.out`): `classes 20 tests 530 skipped 2 failures 0 errors 0 engines ['taumc']` |
| Replay clean | Default engine, all kinds, `-PglslReplayThreads=8`: packs `cases=424 identical=387 accepted=0 failing=0 unsupported=37`, DH corpus `cases=140 identical=118 failing=0 unsupported=22`, S7's TauMC BSL corpus `cases=175 identical=170 failing=0 unsupported=5`, mini-corpus (now in every `:test`) `cases=32 identical=19 accepted=9 failing=0 unsupported=4`, `stale=0` everywhere, every concurrent pass `differing=0`. The unsupported cases are GLSM's compat cases (Step 10) |
| The sweep renders all three packs with baseline-level screenshot differences | BSL, Complementary Reimagined and I Like Vanilla `(loaded: true)` in every run, 0 `Shader compilation failed`/`Failed to compile`, 0 `SyntaxException`. Frames within the TauMC noise floor except one: the new engine's I Like Vanilla frame differs from the S2 baseline frame on 1.75 % of pixels against a TauMC floor of 0.97 %; the mask is edge jitter, and the same frame is 0.63 % from S6's TauMC frame (Measurements, table 3) |
| The rejection run behaves as on the old engine | `run/s8-gate.out`: the same `[DemonicaQuarantine]` rejection, 18 "Not applying" lines as on 2026-09-27 (`run/gate-rejected.out`: 18), `ShadersUnavailableScreen is open`, `Dev shader pack: not switched to BSL_v10.1.8.zip, Iris is off`, script finished; its three frames 0.00 % of pixels from the 2026-09-27 frames |
| Full `:test` green | `./gradlew :test --rerun` (`run/s8-full-test.out`): `BUILD SUCCESSFUL in 5s`, `classes 136 tests 1065 skipped 4 failures 0 errors 0 engines ['douira']` (test timestamps 03:57:07 to 03:57:10) |
| Timing table in the report | Measurements, tables 1 and 2 |

Added by the orchestrator: a production-shaped smoke test through the Prism instance `prod-smoke-test` (Commands run,
"Prism"): passed.

## Commits

| Commit | Subject |
|---|---|
| `72190445` | glsl-transformer: S8 flip the default engine to glsl-transformer |
| the commit that adds this page | glsl-transformer: S8 report and status |

## What changed

Main code:
- `shader/.../transform/TransformPatcher.java`: `Engine.DEFAULT = DOUIRA`; an unset property and an unknown value both
  resolve to it (the WARN now says `...; using douira`). Javadocs.
- `glsm/.../transformer/ShaderAst.java`: `ShaderAst.Timing` counts only while `GLSMPerfDebug.isEnabled()`
  (orchestrator decision 3). `Timing.current()` returns null otherwise, and `lockBuild`/`unlockBuild`/`parse` skip the
  `ThreadLocal` lookup and the `nanoTime` calls for a null timing, so without perf debug a lock acquisition costs one
  volatile read more than the bare `ReentrantLock` (the counts stay zero; nothing logs them then). With perf debug the
  counts are as before: BSL `locks=10645` in `run/timing-bsl-douira-s8.out`, as in S7b.
- `shader/.../transform/AstShaderTransformer.java`: class javadoc (the default engine).

Tests:
- `src/test/java/.../transform/transformer/CompatibilityTransformerTest.java` (new, 6 tests): the TauMC test's
  fixtures; the pack text patches are the engine-neutral `CompatibilityPatches`, and their "0 syntax errors" oracle is
  now "`ShaderAst.parse` succeeds"; `transformGrouped` is `transformer/CompatibilityTransformer`'s, its output printed,
  parsed again and read with `ShaderAst.findQualifiers(StorageType.OUT)` (`typeName` `vec3`/`vec2`/`float`,
  `typeText` `flat out float`, no array), plus the three initializers in `main` (`GlslTokens`).
- `src/test/java/.../transform/transformer/CeleritasTransformerTest.java` (new, 3 tests): the listener becomes
  `GlslTokens` counts on `printBody()` (`uniform vec3 u_RegionOffset ;` once, no `chunkOffset` token),
  `findQualifiers(UNIFORM)` and `functions()` (the pack function reads `u_RegionOffset` once); the geometry test checks
  `vertex = vertex ;` once and no `toClipSpace3 ( mat3 ( gbufferModelView )`. Each output is parsed again.
- The TauMC `CompatibilityTransformerTest` and `CeleritasTransformerTest` stay unchanged and keep testing the TauMC
  classes until Step 11 (brief: "Delete nothing yet"), as S7 did with `AdaptiveShadowBoundsTransformerTest`.
  `TransformPatcherTest` was ported by S7b; `CompatibilityTransformerCaveSkyholeTest`, `TransformPatcherCacheTest` and
  `TerrainVertexFormatRequirementsTest` are library-independent and unchanged.
- `TransformCorpusReplayTest`: without `-PglslCorpusDir` it replays the committed mini-corpus
  (`src/test/resources/transform-corpus`) on the default engine instead of skipping (orchestrator decision 2), so every
  `:test` and `check` runs it; `-PglslCorpusDir` still selects another corpus. Record mode without an explicit
  `-PglslCorpusDir` fails, so the committed snapshot is never rewritten by accident. `miniCorpus()` helper; javadoc.
- `src/test/resources/transform-replay/accepted.txt`: a Step 8 review note in the header, a blank line before the
  Step 7b block. No entry added, changed or removed ("accepted.txt review").

Build and scripts:
- `build.gradle`, root `test {}` block: comments (the mini-corpus default, `default douira since Step 8`). No logic
  change: without `-PglslCorpusDir` the test task stays cacheable, and the mini-corpus is a declared input (test
  resources).
- `scripts/glsl-corpus/capture.sh`: always passes `demonica.glsl.engine=$engine` (default `taumc`). Before, it passed
  the property only for another engine, so after the flip a plain `capture.sh bsl` would have recorded the new engine
  into the TauMC reference corpus.
- `scripts/glsl-corpus/transform-times.py`: `--phases` splits a log at the harness's `Dev marker:` lines and prints
  transforms (misses), cache hits, median, p90 and sum per phase.
- `scripts/glsl-corpus/sweep.txt` (new): the cp4-shaped sweep at the corpus camera (Deviations 1).

Outside git: `run/s8-*.out`, `run/timing-*-s8.out`, `run/timing-vanilla-*-s8b.out`, `run/s8-imgdiff.txt`, frames under
`run/engine-screenshots/s8-{douira,taumc,sweep-douira,sweep-taumc,gate,compat}/`, `run/s8-mini-record/` (the
mini-corpus with the new engine's outputs recorded, for the glslangValidator review), and in the Prism instance
`minecraft/smoke-backup-glsl-transformer-s8/`. The compat run with `angelica.dumpShaders=true` replaced S2's dump in
`run/client/compat_shaders/` (116 files then, 116 now); S2's compat cases are unaffected
(`run/transform-corpus/compat/`).

## Decisions recorded (made by the orchestrator for this step)

1. **Syntax errors (S5 open question 1): the new engine keeps throwing.** After S7b, the only known pack-shaped input
   that parses on TauMC and not on glsl-transformer is `patch` used as an identifier in a shader hoisted to 400 or
   later (mini-corpus `composite-patch-hoisted`), where TauMC's output was broken GLSL too: glslangValidator 16.4.0 on
   TauMC's recorded fragment, `exit=2`, `ERROR: 0:29: '' :  syntax error, unexpected PATCH, expecting COMMA or
   SEMICOLON`. No recorded pack input fails to parse (every replay above has `failing=0`, and no dev run logged a
   `SyntaxException`).
2. **Mini-corpus replay in every `:test` (S2 open question 3):** done (What changed, Tests).
3. **`ShaderAst.Timing` gated behind `demonica.glsmPerfDebug`:** done (What changed, Main code). Its remaining cost
   without perf debug is one volatile read of `GLSMPerfDebug.enabled` per lock acquisition (about 10,600 per BSL
   load); that is an argument, not a measurement: the removed cost (a `ThreadLocal` get and two `nanoTime` calls per
   acquisition) is below the run-to-run spread of the in-game timings, so no run could show it.
4. **`Iris.java`'s `Shader-Transform-0` thread naming: left alone**, listed under Open questions.

## Commands run and their outcomes

Every Gradle invocation one at a time, test runs with `--rerun`, counts read from `build/test-results/test/*.xml`
(Gradle 9 prints no "Tests run" lines). Dev clients one at a time, in the foreground under `timeout`, default
`demonica.openglProfile` (unset, as in S2's baselines), no Gradle build meanwhile.

Appendix C's transform tests (the `net.coderbot.iris.pipeline.transform.*` pattern includes `transformer/`):
```
$ ./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' --tests 'com.gtnewhorizons.angelica.glsm.CompatShaderTransformerTest' --tests 'com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGeneratorTest' --tests 'net.coderbot.iris.celeritas.vertices.TerrainVertexFormatRequirementsTest' --rerun
BUILD SUCCESSFUL in 11s        classes 20 tests 530 skipped 2 failures 0 errors 0 engines ['douira']      (run/s8-appc-douira-1.out)
$ (the same) -PglslEngine=taumc
BUILD SUCCESSFUL in 3s         classes 20 tests 530 skipped 2 failures 0 errors 0 engines ['taumc']       (run/s8-appc-taumc-1.out)
```
530 = S7b's 521 + 6 + 3 new tests; skipped 2 (S7b: 3): the replay no longer skips. In those runs the mini-corpus replay
printed (from the XML's system-out):
```
replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=32 identical=19 (byte-identical 0) accepted=9 failing=0 unsupported=4 recorded=0 filtered=0
replay: accepted entries in scope=9 used=9 stale=0
replay: engine=taumc corpus=.../src/test/resources/transform-corpus cases=32 identical=32 (byte-identical 32) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
```

Full replay, default engine, all kinds (the brief's Verify 2, per corpus; `run/s8-replay-<corpus>.out`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/<corpus> -PglslReplayThreads=8 --rerun
replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37 recorded=0 filtered=0
replay: accepted entries in scope=0 used=0 stale=0
replay: concurrent engine=douira threads=8 cases=387 groups=2 sequentialMs=7484.3 concurrentWallMs=1446.0 concurrentCallMs=11494.6 differing=0
replay: engine=douira corpus=.../run/transform-corpus-dh cases=140 identical=118 (byte-identical 0) accepted=0 failing=0 unsupported=22 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=118 groups=2 sequentialMs=4459.0 concurrentWallMs=921.5 concurrentCallMs=7088.5 differing=0
replay: engine=douira corpus=.../run/transform-corpus-s7-taumc cases=175 identical=170 (byte-identical 0) accepted=0 failing=0 unsupported=5 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=170 groups=2 sequentialMs=1261.2 concurrentWallMs=288.7 concurrentCallMs=2278.2 differing=0
```
All `BUILD SUCCESSFUL`; `unsupported` is `compat on douira: CompatShaderTransformer has no engine switch yet` in each.
The corpora recorded on the new engine (`run/transform-corpus-douira`, `-s7-douira*`, `-s7b-douira`, `-dh-douira`) hold
no TauMC outputs to compare against and were not replayed.

Record run for the `accepted.txt` review (`run/s8-mini-record.out`, into a scratch copy):
`replay: engine=douira (record) corpus=.../run/s8-mini-record cases=32 ... recorded=27`, then glslangValidator 16.4.0
on both engines' outputs (next section).

The step's one full `:test` (Verify 1; `run/s8-full-test.out`) and the jar build (`run/s8-build.out`):
```
$ ./gradlew :test --rerun          BUILD SUCCESSFUL in 5s    classes 136 tests 1065 skipped 4 failures 0 errors 0
$ ./gradlew build                  BUILD SUCCESSFUL in 8s    verifyCeleritasPin, verifyDiagnosticsJar, verifyDiagnosticsRemap,
                                                             verifyDistributedJar, verifyModuleBoundaries, verifyRunClasspath,
                                                             verifyS8tnlibPin ran
```
1065 = S7b's last full count 1,056 (1,054 plus follow-up 2's net two) + 9. The skipped 4 are the corpus-gated modes of
other tests. The jar holds `glsl-transformer-3.0.0-pre3.jar`, `glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar`,
`antlr4-runtime-4.13.2.jar`, `jcpp-1.4.14.jar`; manifest `NonModDeps: true`, `ContainedDeps:` lists them.

Dev clients (read the dev-run memory note first), 13 in all, every one `BUILD SUCCESSFUL`, 19 "n of n injectors" lines
in every run with the pinned Celeritas (the gate run, which rejects it, has the fog patch's one), 0 `SyntaxException`, 0 `Shader compilation failed`/`Failed to compile`, 0
`ClassNotFoundException`/`NoClassDefFoundError`/`LinkageError`:

| Run | Log | What it shows |
|---|---|---|
| `timing.sh bsl douira s8`, `complementary`, `vanilla` | `run/timing-<pack>-douira-s8.out` | first loads on the new engine; frames to `run/engine-screenshots/s8-douira/` |
| `timing.sh <pack> taumc s8` (same session) | `run/timing-<pack>-taumc-s8.out` | the same on TauMC; frames to `s8-taumc/` |
| `timing.sh vanilla douira s8b`, then `taumc s8b` | `run/timing-vanilla-*-s8b.out` | a second I Like Vanilla pair (the first pair differed most) |
| sweep, default engine | `run/s8-sweep-douira.out` | Verify 3 (below); frames `s8-sweep-douira/` |
| sweep, `demonica.glsl.engine=taumc` | `run/s8-sweep-taumc.out` | the same on TauMC; frames `s8-sweep-taumc/` |
| `gate-rejected.txt`, `-PdevProps=demonica.celeritas.pinsOnly=true` | `run/s8-gate.out` | Verify 4 (below) |
| `-PwithCompatMods`, `compat.txt`, default engine | `run/s8-compat.out` | co-existence with the 17 compat mods; frames `s8-compat/` |
| the same with `angelica.dumpShaders=true,demonica.glsmPerfDebug=true` | `run/s8-compat-dump.out` | GLSM's TauMC path ran: 116 dumped files (caller lines: DH 52, `OpenGlHelper` 26, Botania 26, Celeritas 12), the same count as S2's dump (contents not compared) |

Verify 3, on the sweep (the brief's `cp4.txt` replaced by `sweep.txt`, Deviations 1):
```
$ ./gradlew runClient -PdevScript=@$PWD/scripts/glsl-corpus/sweep.txt -PdevProps=demonica.glsmPerfDebug=true > run/s8-sweep-douira.out 2>&1
[23:51:03] Dev shader pack: BSL_v10.1.8.zip (loaded: true)            [23:51:10] Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224
[23:51:12] Dev shader pack: ComplementaryReimagined_r5.9.3.zip (loaded: true)   [23:51:18] Dev stats: 120 fps; ...; shadow sections 190
[23:51:19] Dev shader pack: I Like Vanilla v1.4.4.zip (loaded: true)  [23:51:26] Dev stats: 120 fps; ...; shadow sections 211
[23:51:31] BSL (loaded: true) / [23:51:39] Complementary (loaded: true) / [23:51:46] I Like Vanilla (loaded: true), the cached reloads, same stats
Shader compilation failed|Failed to compile: 0 lines
transformMs sum (the brief's pipeline): 13663.6 (TauMC sweep: 15123.7)
```
The exceptions in both sweep logs are the same list, all known and engine-independent: 6 "Mixin config does not reside
in a jar file", 2 I Like Vanilla `Variable shadows build in uniform: pi`, 2 Complementary `Unknown variable:
BIOME_SULFUR_CAVES`, 2 `Unknown variable: endFlashIntensity`, 1 narrator "No null terminator found".

Verify 4:
```
$ ./gradlew runClient -PdevScript=@scripts/gate-rejected.txt -PdevProps=demonica.celeritas.pinsOnly=true > run/s8-gate.out 2>&1
[23:54:41] [main/INFO] [DemonicaQuarantine]: Loaded the Celeritas patch quarantine (com.demonica.mixin.celeritas)
[23:54:41] [main/ERROR] [DemonicaQuarantine]: celeritas-forge-mc12.2-2.4.0-autobuild.06999aab.jar is not the Celeritas build this Demonica was made for: ... found SHA-256 0c2b9b77...  Shaders are off, and only the fog patch (S15) applies.
[23:54:46] Dev shader pack: not switched to off, Iris is off
[23:54:59] Dev screen: ShadersUnavailableScreen is open
[23:55:00] Dev shader pack: not switched to BSL_v10.1.8.zip, Iris is off
[23:55:08] Dev script finished; shutting down
```
The same lines as the TauMC-era run of 2026-09-27 (`run/gate-rejected.out`), apart from the order of the two accepted
SHA-256s in the message (a set). With Iris off, no transform runs: the log has no `TransformPatcher` line at all, so
the rejection path does not reach either engine. `GLStateManagerFogServiceMixin: 7 of 7 injectors`.

**Prism** (production-shaped smoke test; the memory note followed): launched only with `flatpak run
org.prismlauncher.PrismLauncher --launch prod-smoke-test` (`exit=0`, 23:58:30 to 23:59:12 local; Prism exited by
itself). Mods: this build's `Demonica-0.5.0-SNAPSHOT.jar` (SHA-256 `6e10889f...`) and
`Demonica-diagnostics-0.5.0-SNAPSHOT.jar` (`8d3dd191...`) in place of the 0.4.0 jars; `relauncher.json` args
`-XX:+UseCompactObjectHeaders -Ddemonica.glsmPerfDebug=true -Ddemonica.dev.script=@<instance>/scripts/smoke-glsl-s8.txt`;
`options.txt` with only `pauseOnLostFocus:false`. Script: `pack off`, a new world, a frame, BSL, a frame, `pack off`,
`exit`. Results:
- Parent (Forge) JVM, `logs/debug-1.log.gz`: `[23:58:33] [main/DEBUG] [FML]: Extracting ContainedDep
  glsl-transformer-3.0.0-pre3.jar from .../mods/Demonica-0.5.0-SNAPSHOT.jar to ...` and `Extracted ContainedDep
  glsl-transformer-3.0.0-pre3.jar ...`; the extracted `mods/1.12.2/glsl-transformer-3.0.0-pre3.jar` has SHA-256
  `0d55650f...`, the same as the Maven Central jar in the Gradle cache.
- Relaunched (Cleanroom) JVM, `logs/debug.log`: `Found existing ContainDep extracted to
  .../mods/1.12.2/glsl-transformer-3.0.0-pre3.jar, skipping extraction`, `Adding glsl-transformer-3.0.0-pre3.jar to the
  mod list`, `Examining for coremod candidacy glsl-transformer-3.0.0-pre3.jar`.
- `logs/latest.log`: `DemonicaQuarantine: 01-celeritas-forge-mc12.2-2.4.0-dev.jar is the Celeritas build this Demonica
  was made for`; `[23:58:50] [Shader-Transform-0/INFO] [Demonica]: [TransformPatcher] GLSL transform engine: douira
  (demonica.glsl.engine)`; `[23:59:04] Dev shader pack: BSL_v10.1.8.zip (loaded: true)`; `Dev stats: 60 fps; terrain C:
  344/7056 D: 12 A: 4; shadow sections 504`; `Dev script finished`. 0 `Shader compilation failed`/`Failed to compile`,
  0 `ClassNotFoundException`/`NoClassDefFoundError`/`LinkageError`, 0 `ShaderAst` lines, 19 "n of n injectors" lines.
  106 transforms on the new engine: `medianMs=28.8 p90Ms=55.6 sumMs=3490.1`, `locks=6627`. The BSL frame
  (`gt-s8-1-bsl.png`, 854x480) shows BSL with its clouds and terrain shadows.
- Afterwards: everything added (both jars, the script, the used `relauncher.json` and `options.txt`, the
  `options.txt` the game wrote, `demonica-options.json` and `shaders.properties` before and after, `forge_early.cfg`
  before), the logs (`latest.log`, `debug.log`, `debug-1.log.gz`, `debug-2.log.gz`, `cleanmix.log`), both frames, the
  world `saves/gts8` and the extracted `glsl-transformer-3.0.0-pre3.jar` are in
  `minecraft/smoke-backup-glsl-transformer-s8/`. The instance is back on `Demonica-0.4.0.jar` and
  `Demonica-diagnostics-0.4.0.jar`; `relauncher.json`, `demonica-options.json` and `shaders.properties` are the
  originals again (`cmp`), `forge_early.cfg` was not changed, there is no `options.txt`, and `mods/1.12.2/` holds what
  it held before. The run had added `"opengl_profile": "AUTO"` to `demonica-options.json` (the 0.5.0 option) and
  rewrote the date in `shaders.properties`; both files were restored.

## `accepted.txt` review

Nine entries, all for mini-corpus cases, all in scope and used in the default replay (`accepted entries in scope=9
used=9 stale=0`); no pack or DH case needs an entry. Each case exists, each test an entry cites exists
(`theGroupedCaseTauMCCouldNotTransform`, `theMultiTexCoord3Case`, `dhMultiTexCoord2Alias`, `multiTexCoord3Shapes`,
`patchAsAnIdentifier`, `legacyTextureCallsKeepTheirNames` in `AstShaderTransformerTest`), and the recorded TauMC errors
are what the entries say (`case.properties`: `IndexOutOfBoundsException: Index: -1, Size: 23` and `Size: 30`).
glslangValidator 16.4.0 on the recorded TauMC outputs and the new engine's (`run/s8-mini-record/`), exit status read:

| Entry | Stage | TauMC's output | New engine's output | Entry still true |
|---|---|---|---|---|
| `transform-grouped-330-undeclared` | error-succeeded | none (threw) | vertex, fragment exit 0 | yes |
| `celeritas-terrain-multitexcoord3` | error-succeeded | none (threw) | vertex exit 0 | yes |
| `dh-terrain-multitexcoord2` | vertex | exit 2, `'gl_MultiTexCoord1' : undeclared identifier` | exit 0 | yes |
| `attributes-multitexcoord3-declared` | vertex | exit 2, `'mc_midTexCoord' : redefinition` | exit 0 | yes |
| `attributes-multitexcoord3-builtin` | vertex | exit 2, `'gl_MultiTexCoord3' : undeclared identifier` | exit 0 | yes |
| `celeritas-terrain-multitexcoord3-builtin` | vertex | exit 2, `'gl_MultiTexCoord3' : undeclared identifier` | exit 0 | yes |
| `composite-patch-identifier` | fragment | exit 2, `syntax error, unexpected UNIFORM` | exit 0 | yes |
| `composite-patch-hoisted` | threw | exit 2, `syntax error, unexpected PATCH` | none (throws) | yes |
| `composite-legacy-textures` | fragment | exit 2, `'texture2DRect' : undeclared identifier` | exit 134 (glslangValidator aborts on its assertion) | yes; open question (S7b) |

Seven of the nine make the new engine's output compile where TauMC's did not; `composite-patch-hoisted` and
`composite-legacy-textures` are broken on both engines and stay open questions. S7's open question 1 is answered:
Iris 26.1's `DHTerrainTransformer`/`DHGenericTransformer` also rename `gl_MultiTexCoord2` to `gl_MultiTexCoord1` and
then call `root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", ...)`, which sees the renamed references, as the
new engine does (`dh-terrain-multitexcoord2`).

## Measurements

### Table 1: first pack load, transform time per pack

Per `[ShaderTransformCache] ... miss transformMs=` line (one transform on its `Shader-Transform` thread, call to
result; `transform-times.py`), the pack's corpus script, `-Ddemonica.glsmPerfDebug=true`. Pooled over the runs of a row.
S2's runs had the recorder on; S2's STATUS sums (bsl 4,734.4, complementary 13,151.2, vanilla 5,703.3 ms) also count
race-reuse and warm-up lines, which the miss-only sums below do not.

| Pack | Engine, runs | Transforms | Median ms | p90 ms | Sum ms per run |
|---|---|---|---|---|---|
| BSL | TauMC, S2 (recorder) | 170 | 21.4 | 55.4 | 4,720.2 |
| BSL | TauMC, S7b x2 | 170 | 20.7 | 61.7 | 4,901.0 |
| BSL | TauMC, S8 | 170 | 19.2 | 59.0 | 4,658.2 |
| BSL | new, S7b x2 | 170 | 20.4 | 52.2 | 4,181.7 |
| BSL | **new, S8** | 170 | **17.7** | 53.5 | 4,032.1 |
| Complementary | TauMC, S2 (recorder) | 115 | 77.8 | 230.6 | 13,082.5 |
| Complementary | TauMC, S7b x2 | 115 | 69.8 | 197.6 | 10,521.5 |
| Complementary | TauMC, S8 | 115 | 73.8 | 174.6 | 9,917.1 |
| Complementary | new, S7b x2 | 115 | 67.9 | 139.7 | 8,925.2 |
| Complementary | **new, S8** | 115 | **76.5** | 143.4 | 9,722.8 |
| I Like Vanilla | TauMC, S2 (recorder) | 100 | 41.5 | 68.3 | 5,190.2 |
| I Like Vanilla | TauMC, S6 | 100 | 44.1 | 70.4 | 5,598.5 |
| I Like Vanilla | TauMC, S8 x2 | 100 | 41.6 | 66.5 | 5,125.6 |
| I Like Vanilla | **new, S8 x2** | 100 | **46.3** | 69.7 | 5,218.8 |

New against TauMC in the same session: BSL median 0.92x, sum 0.87x; Complementary 1.04x, 0.98x; I Like Vanilla 1.11x,
1.02x (the two vanilla pairs: 49.1 against 40.9 ms, then 43.1 against 42.1 ms). Far from the brief's "more than about
twice as slow"; within the up-to-25 % run-to-run spread S7b measured. The new engine's p90 is lower on BSL and
Complementary. Where its time goes (`[AstShaderTransformer] ... timing` sums): BSL parse 346.6, AST build 276.0, lock
wait 1,432.9, lock held 360.8 ms, `locks=10645 contended=3318`; Complementary 1,126.3 / 649.4 / 2,250.9 / 707.9;
I Like Vanilla 844.8 / 387.4 / 1,884.8 / 440.0 (`run/timing-*-douira-s8.out`).

### Table 2: the sweep, first load and cached reload

`scripts/glsl-corpus/sweep.txt`, one run per engine, `transform-times.py --phases`:

| Phase | New: transforms / hits | New median / p90 / sum ms | TauMC: transforms / hits | TauMC median / p90 / sum ms |
|---|---|---|---|---|
| warm-up (before the first pack) | 2 / 0 | 36.6 / 60.5 / 73.1 | 2 / 0 | 8.6 / 10.8 / 17.2 |
| first BSL | 104 / 3 | 27.2 / 61.1 / 3,158.5 | 104 / 3 | 25.7 / 61.8 / 3,155.9 |
| first Complementary | 113 / 2 | 53.8 / 128.7 / 7,213.2 | 113 / 2 | 53.4 / 144.9 / 8,466.8 |
| first I Like Vanilla | 98 / 3 | 28.5 / 40.0 / 2,891.1 | 98 / 3 | 29.5 / 42.9 / 3,098.9 |
| cached BSL | 0 / 108 | none | 0 / 108 | none |
| cached Complementary | 0 / 116 | none | 0 / 116 | none |
| cached I Like Vanilla | 0 / 110 | none | 0 / 110 | none |

A cached reload transforms nothing on either engine: every program is a cache hit (Iris clears the cache only after
120 s without a transform), so its transform time is zero whatever the engine. The sweep's BSL has 104 transforms
(no Nether visit). The warm-up line is the two trivial programs of `ShaderTransformExecutor.warmup()`: 36.6 ms median
against 8.6 ms, the new engine's class loading, paid once per JVM before any pack loads. No wall-clock load time is
logged at a resolution that would separate the engines (the harness logs seconds).

### Table 3: frames

numpy over ffmpeg's rgb24 decode, 1200x720; "pixels" is the share of pixels with a channel differing by more than
16/255 (`run/s8-imgdiff.txt`). Baseline = `run/baseline-screenshots/` (S2, TauMC).

| Frame | New (S8) vs baseline | TauMC (S8) vs baseline | New vs TauMC, same session | TauMC noise floor (S6 table) |
|---|---|---|---|---|
| BSL no pack | 0.00 %, mean 0.00 | 0.01 % | 0.01 % | 2.92 % (S1 no-pack) |
| BSL pack | 0.03 %, mean 0.33 | 0.01 % | 0.02 % | 0.15 to 0.67 % |
| BSL Nether | 2.39 %, mean 0.75 | 2.60 % | 0.15 % | none earlier; this session's TauMC 2.60 % |
| BSL back in the overworld | 0.50 %, mean 0.82 | 0.46 % | 0.08 % | none earlier; this session's TauMC 0.46 % |
| Complementary no pack | 0.01 % | 0.00 % | 0.02 % | 0.00 % |
| Complementary pack | 0.02 %, mean 0.50 | 0.08 % | 0.06 % | none earlier; this session's TauMC 0.08 % |
| I Like Vanilla no pack | 0.00 % | 0.01 % | 0.01 % | 0.02 % |
| I Like Vanilla pack | **1.75 %**, mean 1.75, max 229 | 0.42 % | 1.55 % | 0.97 % (S6 TauMC vs baseline) |
| compat world / inventory | 0.05 % / 0.00 % | | | |
| gate 0/1/2 vs 2026-09-27 | 0.00 % / 0.00 % / 0.00 % | | | |

The I Like Vanilla pack frame is the one above the floor. Its difference mask (drawn over the frame and looked at) lies
on edges everywhere: block top edges and outlines, flowers, cloud edges, tree tops, a sub-pixel jitter pattern and not a
shading change, as S6 found for this pack. The same new-engine frame against other TauMC frames: 0.63 % (S6's TauMC
run), 0.67 % (S6's new-engine run); S6's two frames against each other 0.47 %; this session's TauMC frame against S6's
TauMC frame 0.69 %. So the baseline frame and this session's new-engine frame are an unlucky pair, not an engine
difference. In the sweep, where both engines load the pack after BSL and Complementary, new against TauMC: 0.21 %
(first load) and 0.18 % (cached reload).

Sweep frames, new against TauMC at the same point: no pack 0.00 %, BSL 0.01 %, Complementary 0.08 %, I Like Vanilla
0.21 %, off 0.00 %, cached BSL 0.02 %, cached Complementary 0.02 %, cached I Like Vanilla 0.18 %. The sweep's first
BSL frame (the same steps as `bsl.txt` before it) against the baseline: 0.08 % on both engines. Sweep frames of later
phases against the baseline carry scene drift (the clouds move between phases: pack off against no pack 4.00 % within
one run), so they are not compared with the floor. BSL on against off: 97.06 %, the pack is on.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 387 pack cases, 118 DH-corpus cases, 170 cases of S7's TauMC BSL corpus | all | identical (tokens) | none |
| mini-corpus: 19 cases | all | identical (tokens) | none |
| mini-corpus: the nine `accepted.txt` entries | as listed | intended (seven) or broken on both engines (two) | kept, reviewed above |
| compat cases (37 packs, 22 DH, 5 S7 BSL, 4 mini) | compat | unsupported on the new engine | Step 10 |
| I Like Vanilla pack frame against the S2 baseline | frame | 1.75 %, edge jitter | none (Measurements, table 3) |

## Deviations from the brief

1. The sweep is `scripts/glsl-corpus/sweep.txt`, not `run/client/scripts/cp4.txt`. `cp4.txt` places the camera
   relative to the spawn in its own world (`world cp4`, `/tp @p ~ ~25 ~`), so its frames cannot be compared with
   `run/baseline-screenshots/`; `sweep.txt` keeps cp4's shape (the three packs switched mid-game, pack off, BSL again)
   at the corpus camera, and loads each pack a second time for the cached reload. Baseline-comparable first-load frames
   and times come from the three corpus scripts (`timing.sh`), run on both engines in the same session.
2. The brief's Verify 3 pipes `transformMs=` sums; the timing tables use `transform-times.py` (medians and p90, as the
   orchestrator asked); the Verify pipeline's sum is quoted too.
3. The ports are new classes in `transform/transformer/` next to unchanged TauMC tests, not in-place rewrites: the TauMC
   engine stays selectable until Step 11 and its tests keep it tested (S7's precedent).
4. Extra runs: a second I Like Vanilla timing pair (the first pair differed most), TauMC timing runs and a TauMC sweep
   in the same session (for table 2's cached reloads, which no earlier step measured), and a second compat run with the
   shader dump. 13 dev clients where the budget planned 4.
5. Added by the orchestrator: the Prism smoke test; the decisions above; `capture.sh` fixed so that the flip cannot
   make it record the new engine into the TauMC corpus.
6. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the orchestrator's rule).

## Open questions

1. `Iris.java` names every transform thread `Shader-Transform-0` (it reads the pool index before the thread is
   registered; S7b remark 5). Left alone by decision; fix on `dev`?
2. `patch` as an identifier at 400 and later throws a syntax error (decision 1). Rename it without restoring it (the
   program compiles, but a uniform or varying named `patch` changes its interface name), or keep the error? (S7b 4.)
3. Legacy `texture2DRect`, `textureCube`, `texture1D`, `texture2DArray` (and `texture1DArray`, `textureCubeArray`) calls
   are not renamed by either engine; the output does not compile under `#version 330 core` (S7b 2).
4. `gl_MultiTexCoord3` read as the built-in by a shader that also declares `mc_midTexCoord` stays in the program (S7b 3,
   the mini-corpus case `attributes`).
5. `BUILD_LOCK` waits are 35 % of the new engine's summed transform time for BSL (1,432.9 of 4,058.3 ms), 23 % for
   Complementary, 31 % for I Like Vanilla (S7b 1). The median already equals TauMC's; worth the library-internal hack?
6. S5's open questions 2 (non-`#extension` directives dropped from the header), 3 (`gl_TexCoord` under core) and 6
   (late `#extension` lines dropped) stand. S7's 1 is answered (`accepted.txt` review), S2's 3 and S5's 1 and 5 by the
   decisions above.

## Notes for the next step

- The default engine is `douira`; `-Ddemonica.glsl.engine=taumc` (dev: `-PdevProps=demonica.glsl.engine=taumc`; tests:
  `-PglslEngine=taumc`) selects TauMC until Step 11. GLSM's `CompatShaderTransformer` still runs on TauMC (Step 10), in
  the same JVM as the new engine: the compat run dumped 116 files, as many as S2's.
- `./gradlew :test` now replays the committed mini-corpus on the default engine (`replay: engine=douira ... cases=32
  identical=19 accepted=9 failing=0 unsupported=4`, in the test XML's system-out). A change to the transform output
  fails `check` unless the mini-corpus's TauMC snapshot or `accepted.txt` says why. Recording new TauMC outputs needs
  an explicit `-PglslCorpusDir` (the default refuses record mode); S7b recorded into a scratch copy and copied the
  `out.taumc.*` files in.
- `scripts/glsl-corpus/capture.sh` always passes the engine; plain `capture.sh <pack>` still records TauMC into
  `run/transform-corpus/<pack>/`. `transform-times.py --phases <log>` splits a log at `log` steps; `sweep.txt` gives
  first loads and cached reloads of the three packs in one run.
- `ShaderAst.Timing` counts only with `-Ddemonica.glsmPerfDebug=true`; without it, the `[AstShaderTransformer] ...
  timing` numbers would be zero (nothing logs them then).
- Step 9 may delete nothing the new engine uses: the `accepted.txt` cases and the Iris-side tests are library-neutral
  or on `ShaderAst` now, except the TauMC copies Step 11 removes (`transform/CompatibilityTransformerTest`,
  `transform/CeleritasTransformerTest`, `transform/AdaptiveShadowBoundsTransformerTest`, `ShaderAstParityTest`,
  `TerrainVertexFormatScanParityTest`).
- Production shape: the nested `glsl-transformer-3.0.0-pre3.jar` is extracted by the parent Forge JVM into
  `mods/1.12.2/` and loaded from there by the Cleanroom JVM; the evidence is in the instance's
  `smoke-backup-glsl-transformer-s8/run/debug-1.log.gz` and `debug.log`.
