# S02: corpus recorder, replayer, baseline capture

Step 2 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `5fbca8b2` (S1 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds; the evidence is below.

| Done when | Evidence |
|---|---|
| Recorder and replayer committed with the Gradle wiring | `ebb9d89b` |
| `taumc` against `taumc` 100 % identical on all corpora | 424 of 424 pack and compat cases byte-identical; 16 of 16 mini-corpus cases identical (15 byte-identical outputs, and the one recorded TauMC error thrown again with the same message) |
| The mini-corpus with outputs committed | 16 cases, 68 files under `src/test/resources/transform-corpus/` in `ebb9d89b` |
| The four scripts committed | `scripts/glsl-corpus/{bsl,complementary,vanilla,compat}.txt` (and `capture.sh`) in `c211a5f6` |
| The pack corpora and baselines exist under `run/` | `run/transform-corpus/{bsl,complementary,vanilla,compat}/`, `run/baseline-screenshots/`, `run/corpus-*.out` |
| The two surveys are in the report with counts | [Directive survey](#directive-survey), [Parse survey](#parse-survey) |

## Commits

| Commit | Subject |
|---|---|
| `ebb9d89b` | glsl-transformer: S2 corpus recorder, replay test, GlslTokens and mini-corpus |
| `c211a5f6` | glsl-transformer: S2 corpus capture scripts |
| the commit that adds this report | glsl-transformer: S2 report and status |

The pack corpora were recorded at `ebb9d89b` with a clean tree (`gitDirty=false` in every case). The capture scripts
were untracked files then and were committed unchanged in `c211a5f6`.

## What changed

Added:
- `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/debug/TransformCorpus.java`: the corpus writer. Inert unless
  `-Ddemonica.glsl.corpus=<dir>`: `isEnabled()` is a `static final` null check, and nothing else (directory scan,
  `git` calls) happens before the first recorded case. Writes one directory per call, dedupes by the SHA-256 of the
  case's inputs, never throws (the first failure is logged with its stack trace, later ones are counted in
  `recorderFailures`). `recordCompat(...)` is the compat path's entry point; `readCaseProperties(Path)` is the one
  reader of the format.
- `shader/src/main/java/net/coderbot/iris/pipeline/transform/corpus/TransformCorpusRecorder.java`: describes a
  `TransformPatcher` cache miss (`begin`, before the engine runs, because the engine mutates `Parameters.type`) and
  completes it with the engine's output (`finish`, after the result is cached, so the file writes stay out of the
  `transformMs` the cache logs). Both catch everything.
- `src/test/java/net/coderbot/iris/pipeline/transform/GlslTokens.java` and `GlslTokensTest.java` (8 tests).
- `src/test/java/net/coderbot/iris/pipeline/transform/TransformCorpusReplayTest.java`: the replayer.
- `src/test/java/net/coderbot/iris/pipeline/transform/GlslCorpusParseSurveyTest.java`: the parse survey.
- `src/test/resources/transform-replay/accepted.txt`: the format, no entries.
- `src/test/resources/transform-corpus/`: the mini-corpus (below).
- `scripts/glsl-corpus/bsl.txt`, `complementary.txt`, `vanilla.txt`, `compat.txt`, `capture.sh`.

Changed:
- `TransformPatcher.java`: both cache-miss paths call the recorder when it is on; an engine exception is recorded as
  `outcome=error` and rethrown. `transformStart` is now also taken when the recorder is on. Behaviour with the
  property unset is unchanged apart from one constant check per miss.
- `CompatShaderTransformer.transform`: on the AST path (a source that needs transformation, cache miss) it records
  `(source, isFragment, output, fallback)`. The fix-up-only path (`#version 330 core` and up) is not recorded.
- `RenderSystem`: `initializeGlslCapabilityForTesting(int, boolean ssbo, boolean imageLoadStore)` (the one-argument
  form stays), and `getContextProfile()` (`core`, `compatibility` or `unknown`), set from the
  `GL_CONTEXT_PROFILE_MASK` read `initRenderer` already made.
- `ShaderTransformer`: `public static String versionHoistingState()` (the hoisting keywords `init()` enabled, or
  `none`) and package-private `resetVersionHoistingForTesting()`. Needed because Iris runs its transform warm-up
  before `ShaderTransformer.init()` (`Iris.java:652-653`), so the two warm-up cases of every run are transformed
  without hoisting and every later one with it.
- `AdaptiveShadowBoundsStats`: `activateForTesting(int binding)`, an enabled instance without a GL buffer (negative
  binding: disabled).
- `build.gradle` `test {}`: forwards `-PglslCorpusDir`, `-PglslReplayEngine`, `-PglslReplayPatches`,
  `-PglslReplayRecord` as `demonica.glsl.corpus.dir`, `demonica.glsl.replay.engine`, `demonica.glsl.replay.patches`,
  `demonica.glsl.replay.record`; with a corpus dir set, the task is never up to date, never taken from the build cache
  (`org.gradle.caching=true` here), and shows standard out so that the summary lines reach the console.
- `GlslTransformerSpikeTest.newTransformer` is package-private (the survey uses it).

Outside git (`run/` is ignored): `run/transform-corpus/{bsl,complementary,vanilla,compat}/` (57 MB), the client logs
`run/corpus-{bsl,complementary,vanilla,compat}.out`, the frames in `run/baseline-screenshots/` (copies of
`run/client/screenshots/corpus-*.png`), the compat run's dump `run/client/compat_shaders/` (116 files), and the test
outputs `run/s2-*.out`. The save `run/client/saves/corpus` is deleted by `capture.sh` after each run.

### Case format

`<dir>/<seq>-<kind>-<hash8>/`: `seq` is five digits (continuing after the highest one already in `<dir>`), `kind` is
the `Patch` or `COMPAT`, `hash8` starts the SHA-256 of the inputs. Files: `case.properties`, `in.<stage>.glsl` and
`out.<engine>.<stage>.glsl` (stages `vertex`, `geometry`, `tess_control`, `tess_eval`, `fragment`, `compute`), or
`in.glsl`/`out.<engine>.glsl` for the compat domain. `case.properties` is `key=value` lines (backslash escapes for
`\`, newline and CR only; read it with `TransformCorpus.readCaseProperties`, not `java.util.Properties`):

| Keys | Domain | Meaning |
|---|---|---|
| `domain`, `patch`, `hash` | both | `iris` or `compat`; the kind; the full input hash |
| `parameters`, `textureStage`, `hasGeometry`, `inputs.texture`, `inputs.lightmap`, `inputs.color`, `textureMap` (`null` or a count), `textureMap.N=name\|TYPE\|STAGE=replacement` | iris | the `Parameters`, by class; the texture map in iteration order |
| `stages` | iris | the stages with input |
| `glsl.maxVersion`, `glsl.ssbo`, `glsl.imageLoadStore`, `versionHoisting` | iris | the capability and the hoisting state at the call |
| `shadowBounds.instrumentation`, `shadowBounds.binding` | iris | from the cache key |
| `isFragment`, `minGlslVersion` | compat | the call's argument; the backend's minimum |
| `files.in`, `files.out` | both | file names |
| `engine`, `outcome` (`ok`, `error`, compat `fallback`), `error`, `transformMs`, `thread`, `recordedAt` | both | the result |
| `gitHead`, `gitDirty`, `openglProfile.property`, `openglProfile.context`, `recorderFailures` | both | provenance: the tree the client was built from, `-Ddemonica.openglProfile` (`unset` here) and the context `RenderSystem` detected |

Everything above `files.in` is hashed; result and provenance keys are not. A second call with the same inputs (a
`raceReuse`, a pack reloaded after a dimension change) is not recorded again.

### The replayer

`TransformCorpusReplayTest.replayCorpus` walks `-PglslCorpusDir` recursively (a pack directory or the whole
`run/transform-corpus/`). Per case it restores the capability, the hoisting state (reset, then `init()` unless
`none`; a state it cannot reproduce counts as unsupported), the shadow-bounds instrumentation and the `Parameters`,
then calls `ShaderTransformer`/`AstShaderTransformer` (or `CompatShaderTransformer.transform` after `clearCache()`)
directly. Each stage is compared with `out.taumc.<stage>.glsl`: equal strings are byte-identical, otherwise
`GlslTokens.diff`; a non-empty diff goes to `build/reports/transform-replay/<case>.<stage>.diff` and fails unless
`accepted.txt` matches (`<case glob> | <stage> | <reason>`, glob against the path under the corpus dir or the case
directory's name, stage `compat` for the compat domain, `*` for any). An `UnsupportedOperationException` whose message
contains "not ported yet" is unsupported; compat cases on `douira` are unsupported until Step 10. A case recorded with
`outcome=error` is identical if the replay throws the same `class: message`. Record mode writes
`out.<engine>.<stage>.glsl` instead. Summary lines start with `replay:` and are also written to
`build/reports/transform-replay/summary.txt`.

### `GlslTokens`

Public, in the test tree, package `net.coderbot.iris.pipeline.transform`: `of(String)`, `tokens()`, `text()`,
`contains(String)`, static `contains(GlslTokens, String)` and `contains(String glsl, String snippet)`, `count(String)`,
static `diff(GlslTokens, GlslTokens)` and `diff(String, String)`, `equals`/`hashCode`. Comments are removed (a block
comment keeps its newlines); a backslash-newline joins lines; a line starting with `#` is one token, `#` plus the
directive's own tokens one space apart (so `#extension X: enable`, glsl-transformer's form, equals TauMC's
`#extension X : enable`). Floats compare by value (`Double.toString`, `f`/`F` dropped; `lf`/`hf` kept, lower-cased);
integers become their decimal value with `u` if unsigned (hex, octal and decimal of one value are one token);
`1`, `1u`, `1.0` and `1.0lf` are four different tokens. `text()` breaks lines after `;`, `{`, `}` and around each
directive; `diff` is a line LCS with two lines of context and `@@ -a,n +b,m @@` hunk headers.

### The mini-corpus

Hand-written (nothing recorded from a pack or a mod), capability 460 with SSBO and image load/store, full hoisting;
outputs from record mode on TauMC.

| Case | Kind | What it exercises |
|---|---|---|
| `composite-120` | COMPOSITE | `#version 120`, `gl_FragColor`, `texture2D`, `gl_TexCoord[0]`; a texture-map entry for this stage (renamed) and one for `DEFERRED` (not) |
| `composite-330` | COMPOSITE | `#version 330 core`, `in`/`out`, a layout output, `depthtex0` |
| `compute` | COMPUTE | `#version 430`, `local_size`, `image2D`, `imageStore`, `texelFetch` |
| `attributes` | ATTRIBUTES | `mc_Entity`, `mc_midTexCoord`, `gl_MultiTexCoord3`, `gl_TextureMatrix`, a sampler named `texture`, `gl_FragData[0]` |
| `celeritas-terrain` | CELERITAS_TERRAIN | vertex + fragment; the Celeritas header lands in the vertex output |
| `dh-terrain`, `dh-generic` | DH_TERRAIN, DH_GENERIC | `dhMaterialId`, legacy matrices |
| `transform-each` | COMPOSITE | an unused helper (removed) and a `const` local initialized from a `const` parameter (`const` dropped) |
| `transform-grouped` | COMPOSITE | `#version 120` varyings the vertex stage does not write: one declared (initialized), one undeclared (declared and initialized) |
| `transform-grouped-330-undeclared` | COMPOSITE | the same at `#version 330 core`, which TauMC cannot transform: recorded as `outcome=error` (see Residual diffs) |
| `shadow-bounds` | ATTRIBUTES | a `texture2DShadow2x2` PCF helper with `shadowMapResolution` |
| `shadow-bounds-instrumented` | ATTRIBUTES | the same with the counters on at binding 7 (three `atomicAdd` in the output) |
| `compat-120-vertex`, `compat-120-fragment` | COMPAT | `gl_ModelViewMatrix`, `gl_Vertex`, `varying`, `ftransform`; `shadow2D`, `texture2D`, `gl_FragColor` |
| `compat-betterportals-vertex`, `-fragment` | COMPAT | copies of the fixtures already committed in `src/test/resources/compat_shaders/` |

## Commands run and their outcomes

The brief's Verify commands, in order, on the final tree:
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=taumc 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=taumc corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=424 identical=424 (byte-identical 424) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
    replay:   ATTRIBUTES {IDENTICAL=339}
    replay:   CELERITAS_TERRAIN {IDENTICAL=12}
    replay:   COMPAT {IDENTICAL=37}
    replay:   COMPOSITE {IDENTICAL=36}
BUILD SUCCESSFUL in 13s
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=taumc 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=taumc corpus=/home/nick/IdeaProjects/Demonica/src/test/resources/transform-corpus cases=16 identical=16 (byte-identical 16) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
    replay:   ATTRIBUTES {IDENTICAL=3}
    replay:   CELERITAS_TERRAIN {IDENTICAL=1}
    replay:   COMPAT {IDENTICAL=4}
    replay:   COMPOSITE {IDENTICAL=5}
    replay:   COMPUTE {IDENTICAL=1}
    replay:   DH_GENERIC {IDENTICAL=1}
    replay:   DH_TERRAIN {IDENTICAL=1}
BUILD SUCCESSFUL in 2s
$ find run/transform-corpus -name 'case.properties' | wc -l; grep -h '^patch=' run/transform-corpus/*/*/case.properties | sort | uniq -c
424
    339 patch=ATTRIBUTES
     12 patch=CELERITAS_TERRAIN
     37 patch=COMPAT
     36 patch=COMPOSITE
$ grep -o 'transformMs=[0-9.]*' run/corpus-bsl.out | cut -d= -f2 | paste -sd+ | python3 -c 'import sys; print(eval(sys.stdin.read()))'
4734.377216
```
The 16 mini-corpus cases count `transform-grouped-330-undeclared` as identical because the replay throws the recorded
`java.lang.IndexOutOfBoundsException: Index: -1, Size: 23`. The pack replay was run a second time at the end
(`run/s2-replay-packs-final.out`) with the same summary line.

Tests (the filtered set of Appendix C plus the new classes, `run/s2-transform-tests.out`):
```
$ ./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' --tests 'com.gtnewhorizons.angelica.glsm.CompatShaderTransformerTest' --tests 'com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGeneratorTest' --tests 'net.coderbot.iris.celeritas.vertices.TerrainVertexFormatRequirementsTest'
BUILD SUCCESSFUL in 2s
```
Per class: CompatShaderTransformerTest 20, VertexShaderGeneratorTest 8, TerrainVertexFormatRequirementsTest 4,
AdaptiveShadowBoundsTransformerTest 9, CeleritasTransformerTest 3, CompatibilityTransformerCaveSkyholeTest 1,
CompatibilityTransformerTest 6, GlslCorpusParseSurveyTest 1 (skipped: no corpus), GlslTokensTest 8,
GlslTransformerSpikeTest 3, TransformCorpusReplayTest 1 (skipped: no corpus), TransformPatcherCacheTest 5,
TransformPatcherTest 3; 0 failures. Rerun after the last main-code edit (`run/s2-transform-tests2.out`): `BUILD SUCCESSFUL`.

Full build (`run/s2-build.out`, the step's one `check` run, after both code commits' content was final):
```
$ ./gradlew build 2>&1 | grep -E 'FAILED|error:|BUILD' | tail -20
BUILD SUCCESSFUL in 9s
```
`:test` executed (not from cache): 127 classes, 559 tests, 3 skipped (the pre-existing one, the replay and the
survey), 0 failures, 0 errors, 18:42:01 to 18:42:02 UTC. `verifyCeleritasPin`, `verifyDiagnosticsJar`,
`verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath` and
`verifyS8tnlibPin` ran. S1 had 124 classes and 549 tests: the difference is GlslTokensTest (8), the replay (1) and
the survey (1).

Mini-corpus record mode (`run/s2-mini-record.out`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=taumc -PglslReplayRecord=true
    replay: engine=taumc (record) corpus=.../src/test/resources/transform-corpus cases=16 identical=1 (byte-identical 1) accepted=0 failing=0 unsupported=0 recorded=15 filtered=0
```
(The one "identical" is the error case, which has no output to record.) The first record run, before the
`transform-grouped` case was split, failed that case with
`java.lang.IndexOutOfBoundsException: Index: -1, Size: 23 at ... org.taumc.glsl.Transformer.injectVariable(Transformer.java:121) < net.coderbot.iris.pipeline.transform.CompatibilityTransformer.transformGrouped(CompatibilityTransformer.java:229)`.

Other engine and failure paths (not in the brief):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE,COMPAT
    replay: engine=douira corpus=... cases=10 identical=0 (byte-identical 0) accepted=0 failing=0 unsupported=10 recorded=0 filtered=6
    replay:   unsupported 4x: compat on douira: CompatShaderTransformer has no engine switch yet
    replay:   unsupported 5x: glsl-transformer engine: COMPOSITE not ported yet
    replay:   unsupported 1x: glsl-transformer engine: COMPUTE not ported yet
BUILD SUCCESSFUL in 1s
```
A scratch copy of `composite-120` with one literal changed in `out.taumc.fragment.glsl` (`run/s2-tamper.out`):
`replay: ... failing=1`, `FAILING composite-120 [fragment]`, `BUILD FAILED`, and
`build/reports/transform-replay/composite-120.fragment.diff` held the one-line hunk
(`- ... * 0.25 ;` / `+ ... * 0.5 ;`). The final pack replay above cleared that report directory again.

Dev runs, one pack per run, sequentially, each in the background under `timeout` and watched through grep, with
`demonica.openglProfile` at its default for every run (no `-PdevProps` entry; every case records
`openglProfile.property=unset`, `openglProfile.context=compatibility`) and the default engine (`taumc`):
```
$ scripts/glsl-corpus/capture.sh bsl            # capture bsl: exit 0, 175 cases
$ scripts/glsl-corpus/capture.sh complementary  # capture complementary: exit 0, 120 cases
$ scripts/glsl-corpus/capture.sh vanilla        # capture vanilla: exit 0, 105 cases
$ scripts/glsl-corpus/capture.sh compat         # capture compat: exit 0, 24 cases
```
`capture.sh <name>` runs `./gradlew runClient -PdevScript=@<abs>/scripts/glsl-corpus/<name>.txt
-PdevProps=demonica.glsl.corpus=<abs>/run/transform-corpus/<name>,demonica.glsmPerfDebug=true > run/corpus-<name>.out`,
and for `compat` adds `-PwithCompatMods` and `angelica.dumpShaders=true`.

| Run | Gradle time | Steps logged | Harness lines |
|---|---|---|---|
| bsl | 53 s | 38 | `Dev shader pack: BSL_v10.1.8.zip (loaded: true)`; `Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224` (overworld), `119 fps; terrain C: 432/3136 ...; shadow sections 600` (Nether), `120 fps; ...; shadow sections 225` (back) |
| complementary | 36 s | 24 | `Dev shader pack: ComplementaryReimagined_r5.9.3.zip (loaded: true)`; `Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 190` |
| vanilla | 30 s | 24 | `Dev shader pack: I Like Vanilla v1.4.4.zip (loaded: true)`; `Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 211` |
| compat | 32 s | 21 | `Dev stats: 119 fps; terrain C: 141/3600 D: 8 A: 4; shadow sections -1` (no pack) |

Every run logged the engine line `[TransformPatcher] GLSL transform engine: taumc (demonica.glsl.engine)`, the
`[TransformCorpus] recording shader transforms to ... (0 earlier cases; git ebb9d89b)` line, 19 "n of n injectors
found their targets" lines, the `[DemonicaQuarantine]` gate line, and no `Shader compilation failed`, no
`recording failed`. Exceptions in the logs, all known: "Mixin config does not reside in a jar file" (six per pack
run, fifteen in the compat run, one per mixin config) and the narrator's "No null terminator found" (two lines per
run); Complementary's custom-uniform warnings `Unknown variable: BIOME_SULFUR_CAVES` and
`Unknown variable: endFlashIntensity` (also in `run/cp4.out`, `cp5.out`, `cp6.out`); I Like Vanilla's
`Variable shadows build in uniform: pi` (also in `run/cp4.out`); in the compat run, 38 lines from ExtraUtils2's and
ArchitectureCraft's missing model variants and iChunUtil's offline resource fetch (`FileNotFoundException`), all
also in `run/cp9.out` and `cp9b.out`, the earlier `-PwithCompatMods` runs.

Surveys: `run/s2-parse-survey.out` (below); the directive greps were run in the shell.

## Measurements

### Corpus counts

Cases per corpus and kind (every case `outcome=ok`; stages count input files):

| Corpus | Cases | ATTRIBUTES | CELERITAS_TERRAIN | COMPOSITE | COMPAT | vertex | fragment | compat inputs |
|---|---|---|---|---|---|---|---|---|
| bsl | 175 | 151 | 6 | 13 | 5 | 170 | 170 | 5 |
| complementary | 120 | 102 | 3 | 10 | 5 | 115 | 115 | 5 |
| vanilla | 105 | 85 | 3 | 12 | 5 | 100 | 100 | 5 |
| compat | 24 | 1 | 0 | 1 | 22 | 2 | 2 | 22 |
| total | 424 | 339 | 12 | 36 | 37 | 387 | 387 | 37 |

No COMPUTE, DH_TERRAIN or DH_GENERIC case, and no geometry or tessellation stage, in any pack corpus (the three packs
at their default options have none in these scenes); the mini-corpus covers COMPUTE and both DH kinds. 403 of the 424
cases are distinct across the four directories: 7 input hashes recur, Iris's two warm-up cases (a COMPOSITE and an
ATTRIBUTES, in all four) and vanilla's five entity-outline post-process shaders (COMPAT, in all four). 68 cases were
recorded on the client thread (the 37 compat cases and 31 ATTRIBUTES cases, which Iris transformed synchronously),
356 on `Shader-Transform-*` threads (the warm-ups among them).

Capability as recorded: `glsl.maxVersion=4600` (see Open questions 1), SSBO and image load/store on, full hoisting
(`std430,iimage,uimage,imageLoad,imageStore,uint,uvec2,uvec3,uvec4,flat`) in 379 cases and `none` in the 8 warm-up
cases. Shadow-bounds instrumentation was on (binding 95) in the same 379 cases: `-Ddemonica.glsmPerfDebug=true` turns
the SSBO counters on when SSBO and GLSL 430 are available, so every pack case after the warm-up carries them.

Cache events in the logs: bsl 170 misses, 2 `raceReuse`, 38 hits (the return from the Nether); complementary 115,
1, 2; vanilla 100, 9, 3; compat 2 misses. Misses plus race reuses equal the recorded Iris cases plus the deduplicated
race duplicates (bsl 172 calls, 170 cases).

### Compat mods that submitted shaders

From the dump `run/client/compat_shaders/` (58 `glShaderSource` calls; its `// Caller:` header) and the cases:

| Source | Calls | Transformed | Cases |
|---|---|---|---|
| Botania (`vazkii.botania.client.core.helper.ShaderHelper.createShader`) | 13 | 13 | 13 (`00001`-`00013`) |
| `net.minecraft.client.renderer.OpenGlHelper.glShaderSource`: vanilla's entity-outline post-process programs (`ProjMat`, `DiffuseSampler`) | | | 5 (`00018`-`00022`, also in every pack corpus) |
| the same entry point: Scannable (matched by content: the jar ships `assets/scannable/shaders/{copy,scanner}.{vsh,fsh}`, and its `copy.fsh` declares `depthTex`; the cases declare `depthTex` and `camPos`/`center`/`radius`) | 13 together with vanilla's | 13 together | 4 (`00014`-`00017`) |
| Distant Horizons (`GlShader.safeShaderSource`) | 26 | 0 (already `330 core` or higher) | 0 |
| Celeritas (`LWJGL3Service.glShaderSourceSafe`) | 6 | 0 | 0 |

26 transformed calls became 22 cases (4 repeated sources). No compat case fell back to the version fix-up. LittleTiles,
ExtraUtils2, FluxLoading, CoFH and the other compat mods loaded (the log names them) but submitted no shader through
GLSM in this script.

### `transformMs`

Sum of the `[ShaderTransformCache] ... transformMs=` lines per run (the brief's command; misses and race reuses), and
the case files' own `transformMs` (engine call only, the recorder's writes excluded; deduplicated):

| Run | Log lines | Log sum (ms) | Iris cases sum (ms) | Compat cases sum (ms) | Slowest Iris case |
|---|---|---|---|---|---|
| bsl | 172 | 4,734.4 | 4,719.0 | 101.4 | `00026-COMPOSITE-58084cfa`, 219.4 ms |
| complementary | 116 | 13,151.2 | 12,969.4 | 87.4 | `00016-COMPOSITE-58888d76`, 370.1 ms |
| vanilla | 109 | 5,703.3 | 5,189.4 | 88.1 | `00016-COMPOSITE-0d4ead59`, 202.0 ms |
| compat | 2 | 10.4 | 10.4 | 164.8 | the warm-up |

The sums add wall time of calls that run in parallel on the transform threads; they are totals to compare between
engines, not load times. They were measured with the recorder on, as the brief asks; its writes happen after the
timed section.

### Baseline frames

`run/baseline-screenshots/`: `corpus-bsl-{0-nopack,1-pack,2-nether,3-overworld}.png`,
`corpus-complementary-{0-nopack,1-pack}.png`, `corpus-vanilla-{0-nopack,1-pack}.png`,
`corpus-compat-{0-world,1-inventory}.png` (1200 x 720). Camera: seed 1234567, save `corpus`, absolute teleport to
`-164.5 100.82940159667969 218.5`, yaw 225, pitch 15, time 6000 (S1's `s1-base.txt` camera). The Nether frame is at
the same x/z, y 64; the return frame at y 100. Measured as in S1 (rgb24 through ffmpeg; "pixels" = share with a
channel differing by more than 16/255):

| Comparison | Mean abs | Pixels | Max | Reading |
|---|---|---|---|---|
| bsl pack vs its no-pack | 33.81 | 96.99 % | 211 | BSL on |
| complementary pack vs its no-pack | 36.74 | 96.09 % | 244 | Complementary on |
| vanilla pack vs its no-pack | 14.75 | 43.80 % | 247 | I Like Vanilla on (a vanilla-like pack) |
| bsl pack vs S1's `s1-taumc-1-bsl.png` | 0.90 | 0.67 % | 93 | same render; S1 measured 0.55 % between its own runs |
| bsl no-pack vs S1's `s1-taumc-0-nopack.png` | 1.19 | 2.92 % | 248 | S1's no-pack noise floor (2.92 %) |
| complementary no-pack vs bsl no-pack | 0.00 | 0.00 % | 89 | same world, same frame |
| vanilla no-pack vs bsl no-pack | 0.00 | 0.00 % | 94 | same |

The Nether frame was looked at: BSL's Nether fog and lava glow. Pre-existing and not investigated (S1's open question
3): BSL's water renders grey-white in this camera.

### Directive survey

The brief's command, per corpus (files matched) and what the matches are:

| Corpus | Inputs | `grep -lE '^\s*#\s*(if\|ifdef\|...)'` | The matches |
|---|---|---|---|
| bsl | 345 | 336 | 1,680 `#define` lines: BSL's settings documentation (`#define with a name only is a toggleable feature.`, `#define FEATURE -> ...`), inside a `/* */` comment near the top of each of BSL's 336 program inputs (the other 9 inputs are the warm-up and compat shaders) |
| complementary | 235 | 1 | `#ifndef CUSTOM_PBR` / `#endif` inside a commented-out block (`/*if (materialMask == 254) {`) in one COMPOSITE fragment |
| vanilla | 205 | 196 | `#if COLORED_LIGHTING_ENABLED == 1`, `#if defined VOXY \|\| defined DISTANT_HORIZONS`, `#ifndef VOXY`, `#endif`, inside a block comment (checked in one file: the comment opens at line 13, the first match is at line 19) |
| compat | 26 (22 compat inputs, 4 warm-up stage inputs) | 0 | |

Counted outside comments (comments stripped first, line structure kept; a scratch script, not committed): every one
of the 811 inputs has exactly one `#version`; 111 BSL inputs have `#extension` lines (111 lines); there is no other
directive in any input. The parse survey's dropping filter agrees: it dropped nothing. So, as the plan expected, the
recorded inputs carry no directive beyond `#version` and `#extension`; the brief's grep over-counts because it does not
skip comments.

### Parse survey

`GlslCorpusParseSurveyTest` with `GlslTransformerSpikeTest.newTransformer` (a fresh transformer and filter per input),
catching `ParsingException` and `ParseCancellationException` (`run/s2-parse-survey.out`):
```
$ ./gradlew :test --tests '*GlslCorpusParseSurveyTest' -PglslCorpusDir=$PWD/run/transform-corpus
    parse-survey: corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=424
    parse-survey: raw inputs=811 parsed=811 failed=0 inputsWithDroppedDirectives=0 dropped={}
    parse-survey: prepared inputs=774 parsed=774 failed=0 inputsWithDroppedDirectives=0 dropped={}
BUILD SUCCESSFUL in 9s
```
"raw": every input as recorded (774 Iris stage inputs and 37 compat inputs), lexer version from its own `#version`.
"prepared": the 774 Iris inputs as TauMC parses them (hoisting, the stage minimum, `replaceTexture`,
`renameReservedWords`, `fixupQualifiers`, the cloud patches) with the `#version` line rewritten to the effective
version (330 and up, `core`), which is what the new engine will parse. No input fails either pass: there is no
GLSL 120 construct in these three packs or these mods that glsl-transformer 3.0.0-pre3 rejects. On the mini-corpus
(`run/s2-survey-mini.out`) the two BetterPortals fixtures fail the raw pass with
`IllegalArgumentException: No #version directive found` (the reference configuration requires one; they are
`#ifdef GL_ES` sources without it); everything else parses.

## Residual diffs

The `taumc` self-replay has none: 424 of 424 recorded cases and 16 of 16 mini-corpus cases are byte-identical, so the
old engine is deterministic on this corpus and `accepted.txt` stays empty. What the step found:

| Case | Stage | Classification | Action |
|---|---|---|---|
| all 440 cases, `taumc` against `taumc` | all | identical (byte for byte) | none |
| `transform-grouped-330-undeclared` (mini-corpus) | vertex + fragment | old-engine failure: at `#version 330 core`, `transformGrouped` injecting `out vec3 viewDir;` into a vertex shader that does not declare it throws `IndexOutOfBoundsException: Index: -1, Size: 23` from `Transformer.injectVariable` (`CompatibilityTransformer.java:229`); the `#version 120` varying shape works | recorded as `outcome=error`; the new engine will likely transform it, which the replay reports as failing until Step 5 accepts it with a reason (see Notes) |
| BSL's settings comment, Complementary's and I Like Vanilla's commented-out `#if` | inputs | survey artefact: directives inside comments | none; reported under Directive survey |

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1" (the orchestrator's
   rule).
2. Corpus layout. Section 3.5 says `<dir>/<domain>/<seq>-<patch>-<hash8>/`; the Verify commands glob
   `run/transform-corpus/*/*/case.properties`, which that extra level would break. The layout is flat,
   `<dir>/<seq>-<kind>-<hash8>/`, with `domain` a key and `COMPAT` as the compat kind (so
   `-PglslReplayPatches=COMPAT` selects the compat cases, as Step 10's brief suggests).
3. Two classes instead of one: `glsm` cannot see `shader`, so `CompatShaderTransformer` cannot call a class in
   `transform/corpus/`. The writer is `glsm/.../debug/TransformCorpus`; `transform/corpus/TransformCorpusRecorder`
   describes Iris's calls and uses it.
4. The recorder writes more than the brief lists: the hoisting state and the capability flags (needed to replay the
   warm-up cases exactly), the orchestrator's provenance keys (`engine`, `openglProfile.property` and
   `openglProfile.context`, `gitHead` and `gitDirty`), and `hash`, `files.in`/`files.out`, `thread`, `recordedAt`,
   `recorderFailures`. It records engine exceptions (`outcome=error`) and the compat fallback (`outcome=fallback`).
   "As resolved": the resolved `OpenGlProfile` lives in the root project (`DemonicaStartupConfig`), which neither
   `glsm` nor `shader` can reach, so the recorder writes the property as given and the context profile
   `RenderSystem` detected (a new `getContextProfile()`).
5. It dedupes by input hash, in a run and against the case directories already in `<dir>`, so the counts are
   distinct cases, not calls (the log's miss count is the call count).
6. Test hooks beyond `initializeGlslCapabilityForTesting`: the brief allowed skipping instrumented cases if the
   shadow-bounds flags had no hook; since `glsmPerfDebug` turns the counters on, that would have left 379 of 424 cases
   unsupported, so `AdaptiveShadowBoundsStats.activateForTesting` was added. `ShaderTransformer.versionHoistingState`
   and `resetVersionHoistingForTesting` were added for the warm-up cases.
7. The mini-corpus has 16 cases, not 13: the `#version 120` compat case is a vertex and a fragment shader (one GLSL
   file cannot be both); `shadow-bounds-instrumented` covers the counters; the `transformGrouped` case is split into a
   working `#version 120` case and the `#version 330 core` shape TauMC cannot transform. The BetterPortals cases copy
   the fixtures already committed in `src/test/resources/compat_shaders/` (from `c7931043`); nothing new from a mod or
   a pack was committed.
8. The parse survey is a committed test (`GlslCorpusParseSurveyTest`, skipped without `-PglslCorpusDir`), not a
   throwaway, so a later step (Step 12's reserved-word question) can rerun it; it adds the "prepared" pass.
9. The directive survey reports the brief's grep as given and a comment-aware count (a scratch script in the session's
   scratchpad, not committed), because the grep's matches are all inside comments.
10. `scripts/glsl-corpus/capture.sh` is new: it runs one script with the right properties, replaces the corpus
    directory, and deletes the save.
11. BSL's Nether round trip: `cp5.txt`'s is two `/forge setdimension` commands, not a portal walk. It was included
    (the brief's condition read as "if it is simple"); it added BSL's Nether programs to the corpus.
12. `compat.txt` shoots the world and the creative inventory only: no compat mod's own screen is reachable without its
    items, and the shaders that exist were submitted at startup and world load anyway.
13. The replay's default engine, without `-PglslReplayEngine`, is `TransformPatcher.engine()`. Its summary also counts
    byte-identical cases and is written to `build/reports/transform-replay/summary.txt`; with `-PglslCorpusDir` set
    the `test` task shows standard out (the summary lines), is never up to date and never cached.
14. `GlslTokens` goes beyond 3.5's wording in two places: a directive is tokenized (not only whitespace-collapsed), so
    that glsl-transformer's `#extension X: enable` equals TauMC's `#extension X : enable`; and integer literals are
    canonical by value (glsl-transformer keeps the radix but lower-cases hex digits and the `u` suffix).
15. `GlslTransformerSpikeTest.newTransformer` became package-private.

## Open questions

1. `RenderSystem.getMaxGlslVersion()` is 4600 on this machine: `parseGlVersionString("4.60 NVIDIA ...")` returns
   major + minor + bugfix, "4" + "60" + "0". All 57 run logs under `run/` show `Max GLSL version: 4600`. With 4600,
   `negotiateVersion` can never fail and every `>= 130`/`>= 430` check passes. It predates this plan; the corpus records
   and replays 4600 faithfully. A separate fix on `dev`? (If fixed, a recorded corpus still replays with 4600.)
2. TauMC cannot transform the `#version 330 core` `transformGrouped` shape (Residual diffs). No pack case hit it. Should
   Step 5 accept the new engine's success on `transform-grouped-330-undeclared` with the reason "the old engine threw",
   or should the case be dropped?
3. The mini-corpus replay is skipped unless `-PglslCorpusDir` is given, as the brief says, so `./gradlew check` does
   not run it. The plan's end state wants the mini-corpus to guard the transform output; a default that replays
   `src/test/resources/transform-corpus` when no directory is configured would do that. Step 8's decision?
4. The corpora cover the packs at their default options, daytime overworld (and BSL's Nether), no DH world. Options
   that switch programs on (e.g. volumetric clouds, compute passes) are not in the corpus.

## Notes for the next step

- Replay: `./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=<abs> -PglslReplayEngine=taumc|douira
  [-PglslReplayPatches=COMPOSITE,COMPUTE,...,COMPAT] [-PglslReplayRecord=true]`, then
  `grep -E 'replay|Tests run|FAILED|BUILD'`. Diffs: `build/reports/transform-replay/<case>.<stage>.diff` (the case path
  with `/` turned into `_`), summary in `summary.txt`. `accepted.txt`: `<case glob> | <stage> | <reason>`; stage
  `compat` for compat cases.
- "Unsupported" is detected by the substring "not ported yet" in an `UnsupportedOperationException`: keep that phrase
  in `AstShaderTransformer.notPorted` while kinds remain unported.
- The replay calls `AstShaderTransformer.transform`/`transformCompute` directly and restores global state only for
  the old engine's needs: `RenderSystem` capability, `ShaderTransformer` hoisting (reset or `init()`), and
  `AdaptiveShadowBoundsStats.activateForTesting`. When Step 5 extracts `VersionNegotiation`, it must keep
  `versionHoistingState()` (the recorder calls it) and a reset the replay can call, and the new engine must read the
  same state.
- 379 of the 424 recorded cases have `shadowBounds.instrumentation=true` (binding 95). Until Step 7 ports
  `AdaptiveShadowBoundsTransformer`, every fragment stage with a recognized PCF helper will differ on the new engine,
  with or without the counters; those are the `S7 pending` entries Step 5's brief expects.
- `GlslTokens` is ready for Step 3 (`ShaderAstParityTest`) and every later test; see the API above. Its float
  canonicalization makes `0.0` and `0.0f` one token; `text()` puts each directive on its own line.
- Pack corpora hold only ATTRIBUTES, CELERITAS_TERRAIN, COMPOSITE and COMPAT; COMPUTE, DH and the geometry and
  tessellation stages are covered by the mini-corpus alone.
- The compat corpus is `run/transform-corpus/compat/` (22 compat cases plus the two warm-ups); each pack corpus also
  holds vanilla's five entity-outline compat cases. Step 10 adds the engine dispatch in
  `TransformCorpusReplayTest.Replayer.replayCompat` and records `engine` in `TransformCorpus.recordCompat`'s caller.
- To re-record: `scripts/glsl-corpus/capture.sh <bsl|complementary|vanilla|compat>` (one at a time, no other Gradle
  build meanwhile; 30 to 53 s each). It deletes the old corpus directory first. Add `-PdevProps` entries by passing
  Gradle arguments after the name only if they do not repeat `-PdevProps` (the script already sets it).
- The recorder is main code in the jar and costs one `static final` null check per transform miss when the property is
  unset. `-Ddemonica.glsl.corpus=<dir>` works in any dev or production run.
