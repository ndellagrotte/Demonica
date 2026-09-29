# S07: DH transformers and AdaptiveShadowBoundsTransformer

Step 7 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `a9de9aa2` (S1 to S6 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds, and the orchestrator's additions are closed (the S6 row, the 53 "S7
pending" entries and their verifier, a stale-entry check, the `replaceFunctionDefinition` count, the engine-neutral
code moved out of old-engine classes, and real Distant Horizons cases).

| Done when | Evidence |
|---|---|
| Replay clean for all kinds | Pack corpora (Verify 1, `run/s7-verify1.out`): `cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37`, the 37 being GLSM's compat cases (Step 10). Mini-corpus (Verify 2, `run/s7-verify2.out`): `cases=21 identical=14 accepted=3 failing=0 unsupported=4` (4 compat). Real DH corpus (`run/s7-replay-dh.out`): `cases=140 identical=118 accepted=0 failing=0 unsupported=22` (22 compat), DH_GENERIC 1 and DH_TERRAIN 2 identical. Every run: `accepted entries ... stale=0` |
| Both AdaptiveShadowBounds tests green | Verify 3 (`run/s7-verify3.out`, 00:13:44 UTC): the TauMC engine's `AdaptiveShadowBoundsTransformerTest` 9 tests and the new `transformer/AdaptiveShadowBoundsTransformerTest` 14 tests, 0 failures |
| Report | this page |

## Commits

| Commit | Subject |
|---|---|
| `3f835a76` | glsl-transformer: S7 DH transformers and AdaptiveShadowBoundsTransformer on glsl-transformer |
| the commit that adds this page | glsl-transformer: S7 report and status |

## What changed

Added:
- `shader/.../pipeline/transform/transformer/DHTerrainTransformer.java` (112 lines) and `DHGenericTransformer.java`
  (120): the TauMC classes verb for verb on `ShaderAst`: `replaceExpression` x9, `rename` x6, `injectFunction` x4
  (with `injectVertInit`'s two), `prependMain`, and `addIfNotExists`/`applyIntelHd4000Workaround` from
  `AstShaderTransformer`, with the same injected strings in the same order.
- `shader/.../pipeline/transform/transformer/AdaptiveShadowBoundsTransformer.java` (241): the rewrite on `ShaderAst`
  (below), and the engine-neutral `mayInjectRuntimeStats` pre-check that both engines call.
- `src/test/.../pipeline/transform/transformer/AdaptiveShadowBoundsTransformerTest.java` (318 lines, 14 tests; below).
- Mini-corpus cases (hand-written, TauMC outputs recorded, below): `dh-terrain-legacy` and `dh-generic-legacy`
  (vertex, geometry and fragment stages: `ftransform()`, the texture matrices, `gl_MultiTexCoord0` to `7` except 2,
  the inverse and combined matrices, legacy matrices in every stage, a pack-declared `modelOffset`), and
  `dh-terrain-multitexcoord2` (the named difference below). The mini-corpus has 21 cases.

Changed:
- `AstShaderTransformer` (402 to 417 lines): `doTransform` dispatches DH_TERRAIN and DH_GENERIC; `PORTED` and
  `notPorted` are gone, since every `Patch` is ported, and an unknown kind throws
  `IllegalStateException("Unknown patch type: ...")` in `doTransform`, as the TauMC engine does. It holds
  `computeCeleritasHeader()` now (moved from `ShaderTransformer`, unchanged) and calls
  `transformer/AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats`.
- `transformer/CommonTransformer`: the `TODO(S7)` is `AdaptiveShadowBoundsTransformer.transform(root, parameters.type)`,
  at the TauMC engine's position (after `renameAndWrapShadow`).
- Old engine: `ShaderTransformer` calls `AstShaderTransformer.computeCeleritasHeader()` and the new
  `mayInjectRuntimeStats` (its own copies are deleted); the old `AdaptiveShadowBoundsTransformer` lost
  `mayInjectRuntimeStats` and says in its javadoc that Step 11 deletes it. Nothing else in the old engine changed; the
  TauMC engine replays all three corpora byte-identically (Commands).
- `Iris.java` calls `VersionNegotiation.init()` and `corpus/TransformCorpusRecorder` calls
  `VersionNegotiation.versionHoistingState()` directly (they went through `ShaderTransformer`'s delegates);
  `VersionNegotiation`'s javadoc says so. `ShaderTransformer.init()`, `versionHoistingState()` and
  `resetVersionHoistingForTesting()` stay for the tests that call them.
- `TransformCorpusReplayTest` (832 to 803 lines): `S7_PENDING`, `S7_HELPER_SUFFIX`, `PCF_HELPERS`, `verifyS7Pending`,
  `taumcStage`, `s7Verified` and `compare`'s check parameter are gone. New: the stale-entry check (below) and its unit
  test `acceptedEntriesThatTolerateNothingAreStale`; `AcceptedDiff` carries its source line (`accepted.txt:N: ...`) and
  has `matchesCase`; the capability restore calls `VersionNegotiation` directly.
- `accepted.txt` (106 to 57 lines): the 53 "S7 pending" entries and their comment block are gone; the header describes
  the stale check; a Step 7 note; one new entry, `dh-terrain-multitexcoord2 | vertex` (below).
- `AstShaderTransformerTest` (13 to 15 tests): `unportedKindsThrow` became `everyKindIsPorted` (a DH_TERRAIN
  transform, the parameter type reset); new `dhPrograms` (both DH kinds, with and without a geometry stage, against
  the TauMC engine as `GlslTokens`, plus the `_vert_init();` position, `getVertexPosition()`, the input declarations and
  the one `modelOffset` declaration) and `dhMultiTexCoord2Alias`.
- `GlslCorpusParseSurveyTest`: calls the moved `mayInjectRuntimeStats`.
- `scripts/glsl-corpus/capture.sh`: `GLSL_CORPUS_ROOT=<dir>` records into `<dir>/<name>/`, logs to
  `run/corpus-<name>-<basename>.out` and copies the frames to `run/engine-screenshots/<basename>/`; it refuses the
  default roots' names. Without it the script behaves as before. The orchestrator asked for a DH recording into a
  separate directory through this script; as it was, `capture.sh complementary` deletes
  `run/transform-corpus/complementary/` first.

Outside git: `run/s7-*.out`; the TauMC DH corpus `run/transform-corpus-dh/complementary/` (140 cases, recorded on
`a9de9aa2` plus the `capture.sh` change, `gitDirty=true`) and its log `run/corpus-complementary-transform-corpus-dh.out`;
the new engine's in-game corpora `run/transform-corpus-dh-douira/`, `run/transform-corpus-s7-douira/`,
`run/transform-corpus-s7-douira2/` and a TauMC one, `run/transform-corpus-s7-taumc/` (all recorded on `3f835a76`),
their logs `run/corpus-<pack>-<root>.out` and frames `run/engine-screenshots/<root>/`; scratch corpora `run/s7-record/`,
`run/s7-guard/`; `run/s7-dh-{taumc,douira}.warn`.

### The rewrite on `ShaderAst`

The candidate detection and the patched source are the TauMC class's string logic, copied: helper names
`texture2DShadow2x2`/`SampleFilteredShadow`, return type `float`/`vec3`, the parameter types and the coordinate name
`shadowPos`, the body needles, the existing-guard needles, `1.5 / shadowMapResolution`, and the counters from
`AdaptiveShadowBoundsStats`. What the tree gave TauMC now comes from `ShaderAst.functions()`:

| TauMC | New engine |
|---|---|
| a `ParseTreeWalker` over `function_definition` | `ast.functions()` (document order, top-level definitions with a body) |
| no `function_parameters` (`f()`): not a candidate | an empty parameter list: not a candidate (kept so the `inspected=` count matches) |
| a parameter without a name or type (`f(void)`, `f(vec3)`): not a candidate | the same, from `Parameter.name() == null` |
| `fully_specified_type().getText()` (`highpfloat`) | `returnType()` (`highp float`); neither is `float`, so both skip it (test `ignoresQualifiedReturnType`) |
| `compound_statement_no_new_scope().getText()` | `bodyText()` (the same compact form; literals `1.0f`, which no needle contains) |
| the definition printed token-spaced (`getFormattedShader`) | `ShaderAst.source(node)`, the indented print; the guard goes after the first `{`, the body's, in both |
| `replaceExpression(source, patched, GLSLParser::function_definition)` | `replaceFunctionDefinition(name, patched)`, which must return 1 |

**The count.** `replaceFunctionDefinition(name, newSource)` replaces every top-level definition named `name` with the
parameter types of `newSource`, and returns how many. The patched source keeps the prototype, so in a valid program it
hits exactly the candidate. Anything else throws `IllegalStateException("[AdaptiveShadowBounds] replacing <name>
replaced <n> definitions, expected 1: <patched>")`, so the transform fails instead of guessing. Test `failsLoudlyWhenAReplacementDoesNotHitExactlyOneDefinition`: two identical definitions of
the helper (a program that parses but that no driver accepts) give `replaced 2 definitions, expected 1`. Test
`rewritesOnlyTheRecognizedOverload`: of two overloads (`sampler2DShadow` and `sampler2D`), only the recognized one is
replaced, in its place.

**The guard as the new engine prints it** (handoff; `run/s7-guard/*/out.douira.fragment.glsl`, recorded from the
mini-corpus cases `shadow-bounds` and `shadow-bounds-instrumented` with `-PglslReplayRecord=true`):

```glsl
float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
	if (!(shadowPos.x > 1.5f / shadowMapResolution && shadowPos.x < 1.0f - 1.5f / shadowMapResolution && shadowPos.y > 1.5f / shadowMapResolution && shadowPos.y < 1.0f - 1.5f / shadowMapResolution && shadowPos.z > 0.0f && shadowPos.z < 1.0f)) return 1.0f;
	vec2 offset = vec2(0.5f / shadowMapResolution);
	...
```

Instrumented (`#version 430 core`, and `layout(std430, binding = 7) buffer ActiniumShadowBoundsStats { ... }` declared
once before the first function):

```glsl
float texture2DShadow2x2(sampler2D shadowtex, vec3 shadowPos) {
	atomicAdd(actiniumShadowBoundsStats.textureCalls, 1u);
	if (!(shadowPos.x > 1.5f / shadowMapResolution && ... && shadowPos.z < 1.0f)) {
		atomicAdd(actiniumShadowBoundsStats.textureRejected, 1u);
		atomicAdd(actiniumShadowBoundsStats.textureSamplesSaved, 4u);
		return 1.0f;
	}
	...
```

`SampleFilteredShadow` gets the same with `return vec3(1.0f);` and the `filtered*` counters (1 sample saved).

### The new-engine test

`transformer/AdaptiveShadowBoundsTransformerTest` runs the TauMC test's nine fixtures (and five more) through the new
class. No assertion reads either library's tree: each output is printed and parsed again with `ShaderAst.parse` (it
must parse), then read through `functions()`, `ShaderAst.source` and `GlslTokens`: one `if` in the helper, the whole
guard as the body's first statement followed by the unchanged body, `return 1.0` or `vec3 ( 1.0 )`, the definition
order kept; instrumented: three `atomicAdd` per helper in order (call counter, then in the guard the rejection and the
samples saved), `layout ( std430 , binding = 7 ) buffer` once; the negative cases (an existing `abs(...)` guard, an
existing comparison guard, a wrong signature, a non-PCF function, no coordinate parameter, a qualified return type, no
`shadowMapResolution`, a vertex shader) leave the helper's tokens unchanged. Plus the overload, the count and the
`mayInjectRuntimeStats` cases. The old test stays and keeps testing the TauMC class.

### The stale-entry check

The replayer never reported an entry that matches no diff, so dead entries could pile up. Now, when an engine other than
`taumc` replays, every entry whose case glob matches a case the run replayed (not filtered out, not unsupported) must
have tolerated a difference of it; otherwise the summary prints `replay:   STALE accepted.txt:<line>: <entry> (its glob
matches cases of this run, but it tolerated no difference)` and the test fails (`stale accepted.txt entries ...`). The
summary line is `replay: accepted entries in scope=N used=M stale=K`. An entry counts as used when it is the first
entry matching a differing stage (or a differing error outcome), as before. Entries for cases this run did not replay
(another corpus, a filtered kind, compat on `douira`) are not judged, because one `accepted.txt` serves every corpus.
The `taumc` engine replays its own recordings, where no entry applies, and record mode compares nothing, so those runs
print `accepted entries not checked for staleness (the reference engine)` / `(record mode)`.

Run against what it guards (`run/s7-stale-tamper.out`): two entries appended to a copy of the committed file, one of
the removed S7 lines (`shadow-bounds | fragment | S7 pending ...`) and one for an identical case (`composite-330 |
fragment | tamper ...`), then the mini-corpus replay on `douira`:

```
    replay: accepted entries in scope=5 used=3 stale=2
    replay:   STALE accepted.txt:58: shadow-bounds | fragment | S7 pending (adaptive shadow bounds, Step 7) (its glob matches cases of this run, but it tolerated no difference)
    replay:   STALE accepted.txt:59: composite-330 | fragment | tamper: this case replays identically (its glob matches cases of this run, but it tolerated no difference)
TransformCorpusReplayTest > replayCorpus() FAILED
BUILD FAILED in 2s
```

The file was restored from the copy and compared with `cmp`. The unit test `acceptedEntriesThatTolerateNothingAreStale`
replays `composite-330` (identical) and `transform-grouped-330-undeclared` (accepted `error-succeeded`) with three
entries: the first is stale, the second used, the third (`shadow-bounds`, not replayed) not judged.

### Engine-neutral code borrowed from old-engine classes (handoff to Step 11)

`grep` over the new code (`AstShaderTransformer`, `VersionNegotiation`, `CompatibilityPatches`, `corpus/`,
`transformer/`, GLSM's `ShaderAst`) for the old classes' names, and over main code outside `transform/`:

| Where | Old-engine reference | Now |
|---|---|---|
| `AstShaderTransformer` (2 calls) | `ShaderTransformer.computeCeleritasHeader()` | moved into `AstShaderTransformer`; the old engine calls it there |
| `AstShaderTransformer` | `AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats` (old class) | moved to `transformer/AdaptiveShadowBoundsTransformer`; both engines call it there |
| `corpus/TransformCorpusRecorder` | `ShaderTransformer.versionHoistingState()` | `VersionNegotiation.versionHoistingState()` |
| `Iris.java:653` | `ShaderTransformer.init()` (version hoisting, which both engines read) | `VersionNegotiation.init()` |
| `TransformPatcher` | `ShaderTransformer.transform`, `transformCompute`, `clearSessionState` | unchanged: the engine switch's TauMC branch, which Step 11 removes with the switch |
| javadoc only: `VersionNegotiation` (lines 13, 19, 20), `CompatibilityPatches` (8, 10), `AstShaderTransformer` (33) | `{@link ShaderTransformer}`, `{@link CompatibilityTransformer}` (old package) | unchanged; after Step 11's rename `ShaderTransformer` names the new engine, and `CompatibilityPatches`' link to the old `CompatibilityTransformer` must be edited |

No code in `transformer/` or `corpus/` imports an old-engine class (their imports from the old package are `Patch`,
`PatchShaderType`, `AstShaderTransformer`, `VersionNegotiation`), and no main code outside `transform/` names
`ShaderTransformer` now. Test code still does (the TauMC oracles, the replayer's TauMC engine,
`ShaderTransformer.init()`/`resetVersionHoistingForTesting()` in `AstShaderTransformerTest`); Steps 8 and 11 own the
tests.

## Commands run and their outcomes

Every Gradle run one at a time; dev clients one at a time in the background under `timeout`, with no Gradle build
meanwhile. Test counts from `build/test-results/test/*.xml`.

The brief's Verify 1 (`run/s7-verify1.out`; with `--rerun`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira --rerun 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37 recorded=0 filtered=0
    replay:   ATTRIBUTES {IDENTICAL=339}
    replay:   CELERITAS_TERRAIN {IDENTICAL=12}
    replay:   COMPAT {UNSUPPORTED=37}
    replay:   COMPOSITE {IDENTICAL=36}
    replay:   unsupported 37x: compat on douira: CompatShaderTransformer has no engine switch yet
    replay: accepted entries in scope=0 used=0 stale=0
    replay: transformMs engine=douira total=8685.7 ATTRIBUTES=7709.3/339 CELERITAS_TERRAIN=364.1/12 COMPOSITE=612.3/36
BUILD SUCCESSFUL in 13s
```
S6 had `identical=336 accepted=51` on the 387 Iris cases; the 51 "S7 pending" stages are identical now.

The brief's Verify 2 (`run/s7-verify2.out`, as written):
```
    replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/src/test/resources/transform-corpus cases=21 identical=14 (byte-identical 0) accepted=3 failing=0 unsupported=4 recorded=0 filtered=0
    replay:   ATTRIBUTES {IDENTICAL=3}
    replay:   CELERITAS_TERRAIN {IDENTICAL=1, ACCEPTED=1}
    replay:   COMPAT {UNSUPPORTED=4}
    replay:   COMPOSITE {IDENTICAL=5, ACCEPTED=1}
    replay:   COMPUTE {IDENTICAL=1}
    replay:   DH_GENERIC {IDENTICAL=2}
    replay:   DH_TERRAIN {IDENTICAL=2, ACCEPTED=1}
    replay:   unsupported 4x: compat on douira: CompatShaderTransformer has no engine switch yet
    replay: accepted entries in scope=3 used=3 stale=0
    replay: transformMs engine=douira total=83.8 ATTRIBUTES=21.1/3 CELERITAS_TERRAIN=18.9/2 COMPOSITE=14.4/6 COMPUTE=5.7/1 DH_GENERIC=15.3/2 DH_TERRAIN=8.4/3
BUILD SUCCESSFUL in 2s
```
The same with `-PglslReplayThreads=8 --rerun` (`run/s7-replay-mini-1.out`): the same counts and `replay: concurrent
engine=douira threads=8 cases=15 groups=2 sequentialMs=32.8 concurrentWallMs=19.9 concurrentCallMs=127.5 differing=0`.

The brief's Verify 3 (`run/s7-verify3.out`, as written): `BUILD SUCCESSFUL in 2s`;
`net.coderbot.iris.pipeline.transform.AdaptiveShadowBoundsTransformerTest` 9 tests and
`net.coderbot.iris.pipeline.transform.transformer.AdaptiveShadowBoundsTransformerTest` 14 tests, 0 failures, 0 errors
(00:13:44 UTC). The brief's filter prints no `Tests run` lines for Gradle 9; the counts are from the XML files.

Real DH cases, replayed on the new engine (`run/s7-replay-dh.out`, `-PglslReplayThreads=8 --rerun`):
```
    replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus-dh cases=140 identical=118 (byte-identical 0) accepted=0 failing=0 unsupported=22 recorded=0 filtered=0
    replay:   ATTRIBUTES {IDENTICAL=102}
    replay:   CELERITAS_TERRAIN {IDENTICAL=3}
    replay:   COMPAT {UNSUPPORTED=22}
    replay:   COMPOSITE {IDENTICAL=10}
    replay:   DH_GENERIC {IDENTICAL=1}
    replay:   DH_TERRAIN {IDENTICAL=2}
    replay: accepted entries in scope=0 used=0 stale=0
    replay: concurrent engine=douira threads=8 cases=118 groups=2 sequentialMs=4322.7 concurrentWallMs=1303.4 concurrentCallMs=9934.1 differing=0
BUILD SUCCESSFUL in 13s
```

The TauMC engine on its own recordings, after the old-engine changes (`run/s7-replay-taumc-*.out`): packs `cases=424
identical=424 (byte-identical 424)`, DH corpus `cases=140 identical=140 (byte-identical 140)`, mini-corpus `cases=21
identical=21 (byte-identical 21)`, each `failing=0`, `BUILD SUCCESSFUL`.

The stale check against dead entries: above (`run/s7-stale-tamper.out`, `BUILD FAILED` as intended).

Unit tests while working: `run/s7-test-1.out` (`dhPrograms` failed on the `gl_MultiTexCoord2` difference, below; the
test was split), `run/s7-test-2.out` (`BUILD SUCCESSFUL`: AstShaderTransformerTest 15, both AdaptiveShadowBounds tests
9 and 14, TransformCorpusReplayTest 3 with 1 skipped).

Recording the three new mini-corpus cases (`run/s7-record.out`): a scratch copy under `run/s7-record/`,
`-PglslReplayEngine=taumc -PglslReplayRecord=true`: `cases=3 ... recorded=3`, `BUILD SUCCESSFUL in 2s`; the
`out.taumc.*` files were copied into the mini-corpus.

The step's one full build (`run/s7-build.out`, on the code committed right after as `3f835a76`): `./gradlew build`,
`BUILD SUCCESSFUL in 11s`; `:test` ran 133 classes, 1,021 tests, 0 failures, 0 errors, 5 skipped (00:14:05 to 00:14:08
UTC; S6: 132 classes, 1,004 tests, 5 skipped; the difference is the new test class with 14 tests, 2 more in
`AstShaderTransformerTest` and 1 in the replayer). `verifyCeleritasPin`, `verifyDiagnosticsJar`,
`verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath`, `verifyS8tnlibPin`
ran. `build/libs/Demonica-0.5.0-SNAPSHOT.jar` holds 34 `transformer/*.class` entries (S6: 29; now also
`AdaptiveShadowBoundsTransformer` with its `$FunctionCandidate` and `$Parameter`, `DHGenericTransformer`,
`DHTerrainTransformer`).

### Dev runs (five clients)

Read first: the dev-run memory note. All through `scripts/glsl-corpus/capture.sh` with `GLSL_CORPUS_ROOT`, so no
default corpus was touched; the script's properties as S2/S6 (`demonica.glsl.corpus`, `demonica.glsmPerfDebug=true`,
so the adaptive-shadow-bounds instrumentation is on), default OpenGL profile.

| Run | Engine | Log | Result |
|---|---|---|---|
| Complementary with `-PwithCompatMods` (Distant Horizons 3.3.0) | TauMC, on `a9de9aa2` + `capture.sh` | `run/corpus-complementary-transform-corpus-dh.out` | exit 0, `BUILD SUCCESSFUL in 48s`, `ComplementaryReimagined_r5.9.3.zip (loaded: true)`, 140 cases |
| the same | douira, on `3f835a76` | `run/corpus-complementary-transform-corpus-dh-douira.out` | exit 0, `BUILD SUCCESSFUL in 48s`, loaded, 140 cases, same patch and input-hash set |
| BSL | douira | `run/corpus-bsl-transform-corpus-s7-douira.out` | exit 0, `BUILD SUCCESSFUL in 53s`, 175 cases, same set as S2's TauMC corpus |
| BSL | TauMC (noise floor) | `run/corpus-bsl-transform-corpus-s7-taumc.out` | exit 0, `BUILD SUCCESSFUL in 52s`, 175 cases |
| BSL again | douira | `run/corpus-bsl-transform-corpus-s7-douira2.out` | exit 0, 175 cases |

**Real DH programs.** Complementary ships `shaders/program/dh_terrain.glsl` and `dh_water.glsl` with per-dimension
`dh_terrain`/`dh_water` `.vsh`/`.fsh` (`unzip -l`; no `dh_generic`, no `dh_shadow`). With Distant Horizons loaded,
`DHCompatInternal` creates the programs at pipeline creation: the log has `Transformed shader for DH_TERRAIN` twice and
`DH_GENERIC` once (its recorded inputs are byte-identical to the first DH_TERRAIN case's: the pack has no
`dh_generic`, and the generic program got the terrain sources), recorded as
`00138-DH_TERRAIN-df6768a5`, `00139-DH_GENERIC-ccfa5fb3`, `00140-DH_TERRAIN-7be0f1b1` (vertex and fragment, recorded
instrumented, `outcome=ok`). Their inputs use `gl_MultiTexCoord1`, `gl_TextureMatrix` and declare
`shadowMapResolution`, but have no PCF helper, no `ftransform()` and no `gl_MultiTexCoord2`. Both engines' runs
transformed the same kinds (102 ATTRIBUTES, 4 CELERITAS_TERRAIN, 10 COMPOSITE, 1 DH_GENERIC, 2 DH_TERRAIN); the new
engine's run logged `[TransformPatcher] GLSL transform engine: douira`, 0 `Shader compilation failed`/`Failed to
compile`/`UnsupportedOperation`/`SyntaxException`/`not ported yet`, 0 `ClassNotFoundException`/`NoClassDefFoundError`/
`LinkageError`, 19 "n of n injectors" lines. The adaptive-shadow-bounds lines are the same in both runs: 65 ×
`inspected=0 injected=0` and 52 × `inspected=1 injected=0 alreadyGuarded=0 unsupported=1` (Complementary's helper is
recognized by name and left alone, on both engines). The distinct WARN/ERROR/exception lines, normalized, differ only
in timestamps, native addresses and which DH loader thread logged DH's `minecraft:snow` warning.

**The rewrite in game.** BSL on the new engine logs what TauMC's S2 and this session's TauMC run log: 51 ×
`[AdaptiveShadowBounds] injected function=SampleFilteredShadow`, 51 × `inspected=1 injected=1`, 119 × `inspected=0`,
0 compile failures; its recording has the instrumented `SampleFilteredShadow` in 51 fragment outputs (for example
`run/transform-corpus-s7-douira/bsl/00021-ATTRIBUTES-825c8f36/out.douira.fragment.glsl`). The instrumented programs link
and count: the perf lines read
`adaptiveShadowBounds[calls=169918679,rejected=33924744,passRate=80.0%,...,filteredCalls=169918679,...]` (TauMC S2:
`calls=168534433,rejected=33651405,passRate=80.0%`). Dev stats: 120 fps; `terrain C: 166/3600`, `432/3136`,
`166/3600`; `shadow sections 224`, `601`, `225` (S2's TauMC run: the same chunk counts, `224`, `600`, `225`, 119 fps in
the second; this session's TauMC run `224`, `603`, `225`).

## Measurements

### Screenshots

rgb24 through ffmpeg into numpy, 1200 x 720, "pixels" the share with a channel differing by more than 16/255.

| Frame | Pair | Mean abs | Pixels | Max |
|---|---|---|---|---|
| Complementary + DH, no pack | TauMC run against douira run | 0.80 | 0.76 % | 255 |
| **Complementary + DH, pack** | TauMC run against douira run | 1.21 | 0.68 % | 178 |
| BSL no pack | S7 TauMC against S7 douira 1 / 2 | 0.00 / 0.00 | 0.01 % / 0.01 % | 94 / 89 |
| **BSL pack** | S7 TauMC against S7 douira 1 / 2 | 1.10 / 0.54 | 1.10 % / 0.08 % | 106 / 71 |
| BSL pack | S7 douira 1 against S7 douira 2 (same code) | 0.99 | 1.08 % | 105 |
| BSL pack | S2 TauMC against S7 TauMC (noise) | 0.47 | 0.05 % | 68 |
| BSL Nether | S7 TauMC against douira 1 / 2; TauMC S2 against S7 | 0.59 / 0.29; 0.74 | 1.63 % / 0.72 %; 2.28 % | 111 / 84; 129 |
| BSL back in the overworld | S7 TauMC against douira 1 / 2; TauMC S2 against S7 | 0.86 / 0.82; 0.96 | 0.57 % / 0.53 %; 0.78 % | 97 / 97; 101 |

The first new-engine BSL pack frame differs from both TauMC frames by about 1.1 %, and from the second new-engine frame
of the same code by 1.08 %: it is that run's frame, not the engine. Its difference mask
(a scratch image, not kept) lies on grass tufts, leaf edges and the water's edge, denser toward the camera, no
region-shaped difference: waving plants. The second new-engine frame is 0.08 % from this session's TauMC frame.
Complementary with DH: the no-pack frame (no transform involved) differs more between the two runs (0.76 %) than the
pack frame (0.68 %). Complementary's pack frame with DH differs from S2's pack frame without DH by 54 % of pixels, so
DH changes that scene; I did not investigate how. Frames looked at: the new engine's Complementary-with-DH pack frame
renders normally.

### Transform time

| | TauMC | new engine |
|---|---|---|
| Replay engine time, pack ATTRIBUTES (339) | 10,442.5 ms (`run/s7-replay-taumc-transform-corpus.out`) | 7,709.3 ms (Verify 1; S6 7,447.6 ms without the rewrite) |
| Replay, BSL alone, two threads (`run/s7-ab-*.out`) | total 2,319.9 ms, `concurrentCallMs=1966.1` | total 1,528.6 ms, `concurrentCallMs=1633.4` |
| Replay, DH_TERRAIN (2) / DH_GENERIC (1), real cases | 114.1 / 38.0 ms | 87.9 / 25.1 ms |
| In game, sum of `transformMs` cache-miss lines, BSL (170) | 4,212.9 ms (this session); S2 4,720 ms | 7,946 ms (run 1), 5,688.1 ms (run 2); S6 5,223 ms |
| Median per BSL transform in game | 17.3 ms (this session) | 33.4 ms (both runs); S6 27.5 ms |
| In game, Complementary + DH (119 lines) | 13,649.5 ms | 13,923.8 ms |

In the replay the new engine, rewrite included, is faster than TauMC. In game its BSL sum varies by 40 % between two
runs of the same code, and its median per transform is about twice TauMC's in this session. Only the first run had the
~266 ms outliers. The rewrite's own cost is small in the replay (BSL ATTRIBUTES with it: 1,332.2 ms for 151 cases).
Not investigated further: Step 8 measures load times (Open questions 2).

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 339 pack ATTRIBUTES (the 51 former "S7 pending" among them), 12 CELERITAS_TERRAIN, 36 COMPOSITE | all | identical (tokens) | the 53 "S7 pending" entries removed |
| DH corpus: 102 ATTRIBUTES, 3 CELERITAS_TERRAIN, 10 COMPOSITE, `00138-DH_TERRAIN-df6768a5`, `00139-DH_GENERIC-ccfa5fb3`, `00140-DH_TERRAIN-7be0f1b1` | all | identical | none |
| mini-corpus `shadow-bounds`, `shadow-bounds-instrumented` | fragment | identical now | entries removed |
| mini-corpus `dh-terrain`, `dh-generic`, `dh-terrain-legacy`, `dh-generic-legacy` | all | identical | none |
| mini-corpus `dh-terrain-multitexcoord2` (new) | vertex | **TauMC quirk**: both DH transformers rename `gl_MultiTexCoord2` to `gl_MultiTexCoord1`, then replace `gl_MultiTexCoord1` with the DH light coordinate; TauMC's `replaceExpression` misses the renamed references (stale by-text cache, S3 remark 3) and leaves `gl_MultiTexCoord1`, which a core-profile program does not have. The new engine replaces them. One statement differs | accepted, `dh-terrain-multitexcoord2 \| vertex \| old engine's stale by-text cache left ...`; `AstShaderTransformerTest.dhMultiTexCoord2Alias` asserts both outputs and that nothing else differs |
| mini-corpus `celeritas-terrain-multitexcoord3`, `transform-grouped-330-undeclared` | error-succeeded | as at S6 | unchanged |
| compat cases (37 packs, 22 DH corpus, 4 mini) | compat | unsupported on `douira` | Step 10 |

No recorded pack input uses `gl_MultiTexCoord2` in any vertex stage (`grep -l` over every `in.vertex.glsl` of both
pack corpora: none).

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1" (the
   orchestrator's rule).
2. The brief's Risk says a DH world is not available in the harness; it is, with `-PwithCompatMods`, and the
   orchestrator asked for it. Three real DH cases are recorded and replay identically, and the new engine ran them in
   game. `capture.sh` got `GLSL_CORPUS_ROOT` for it (What changed).
3. Beyond the brief (orchestrator): the stale check, the `replaceFunctionDefinition` count check, moving
   `computeCeleritasHeader` and `mayInjectRuntimeStats`, and pointing `Iris` and the recorder at `VersionNegotiation`
   (which touches `Iris.java` and the TauMC engine; the TauMC replays are byte-identical).
4. Beyond the brief (mine): three DH mini-corpus cases and two DH tests in `AstShaderTransformerTest`, because the two
   existing DH cases exercise none of `ftransform()`, the texture matrices or `gl_MultiTexCoord0`/`4`-`7`, and the
   TauMC oracle leaves in Step 11 while the mini-corpus stays; four dev clients beyond the DH recording (DH and BSL on
   the new engine, a TauMC BSL noise floor, a repeat of BSL on the new engine to separate a noisy frame and time from
   the engine).
5. `PORTED` and the "not ported yet" exception are gone with the last unported kind; `unportedKindsThrow` became
   `everyKindIsPorted`. The replayer still classifies a "not ported yet" message as unsupported; nothing throws it.
6. Verify 1 ran with `--rerun`, and its `grep` prints no `Tests run` lines under Gradle 9 (as at S6); the test counts
   come from the XML reports.
7. The new test lives in `transformer/` under the same simple name as the old one; `--tests '*AdaptiveShadowBounds*'`
   runs both.

## Open questions

1. `gl_MultiTexCoord2` in DH programs: the new engine now does what the transformer means, where TauMC produced a
   program with `gl_MultiTexCoord1` in a core profile, which cannot compile. That changes behaviour for such a pack
   (for the better, once the new engine is the default). Iris 26.1 may handle the alias differently; worth checking in
   Step 8's review.
2. In-game `transformMs` on the new engine: the median per BSL transform is about twice TauMC's in this session, while
   the replay is faster than TauMC. Together with S6's open question 2 and S5's open question 4 (`BUILD_LOCK` around
   every parse), is it worth measuring where in-game time goes (lock waits against the client thread's transforms,
   JIT warm-up) before the default flips?
3. S6's open questions 1 and 2, and S5's 1 to 3, 5 and 6, stand.

## Notes for the next step

- Every patch kind runs on `AstShaderTransformer`; nothing throws "not ported yet". Replaying all kinds on `douira`:
  packs `identical=387 unsupported=37` (compat), mini-corpus `identical=14 accepted=3 unsupported=4`, DH corpus
  `identical=118 unsupported=22`, all `failing=0 stale=0`.
- `accepted.txt` holds three entries: `transform-grouped-330-undeclared` and `celeritas-terrain-multitexcoord3`
  (`error-succeeded`, TauMC threw) and `dh-terrain-multitexcoord2 | vertex`. An entry that tolerates nothing in a run
  that replays its case now fails the replay (`STALE`), so remove entries together with their differences.
- Real DH cases: `run/transform-corpus-dh/complementary/` (TauMC, 140 cases, 3 DH; replay it with
  `-PglslCorpusDir=$PWD/run/transform-corpus-dh`). Re-record with
  `GLSL_CORPUS_ROOT=run/transform-corpus-dh scripts/glsl-corpus/capture.sh complementary -PwithCompatMods`; with
  `GLSL_ENGINE=douira` and another root for the new engine.
- The adaptive-shadow-bounds rewrite on the new engine prints the guard shown under "What changed" (for Step 8's
  review); `replaceFunctionDefinition` must replace exactly one definition or the transform throws.
- `computeCeleritasHeader()` is in `AstShaderTransformer`; `mayInjectRuntimeStats` in
  `transformer/AdaptiveShadowBoundsTransformer`; `Iris` and the recorder call `VersionNegotiation`. What still names
  the old engine is the switch in `TransformPatcher`, javadoc links, and tests (table above).
- The old `AdaptiveShadowBoundsTransformerTest` (9 tests) keeps testing the TauMC class until Step 11; the new one is
  `transformer/AdaptiveShadowBoundsTransformerTest`.
- The mini-corpus has 21 cases (three new DH ones).
