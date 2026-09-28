# S05: the orchestrator, COMPOSITE and COMPUTE

Step 5 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `10750ef6` (S1 to S4 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds, and the orchestrator's carry-overs (a) to (g) are closed.

| Done when | Evidence |
|---|---|
| Replay for COMPOSITE and COMPUTE has zero unexplained diffs on all corpora | Pack corpora: `cases=36 identical=36 ... accepted=0 failing=0 unsupported=0` (the brief's Verify command). Mini-corpus: `cases=6 identical=5 ... accepted=1 failing=0`; the accepted case is TauMC's recorded error, below. No other corpus exists (S2). After the verification follow-up, with the new case `composite-extension-placement`: packs `cases=36 identical=36 ... failing=0`, mini-corpus `cases=7 identical=6 ... accepted=1 failing=0` |
| The old-engine transform tests still green | `./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*'`: `BUILD SUCCESSFUL`, 12 classes, 429 tests, 0 failures, 3 skipped (the corpus-gated ones); and the TauMC engine still replays its own recordings byte for byte: 424/424 packs, 16/16 mini-corpus. After the verification follow-up: 451 tests, 0 failures, 3 skipped; mini-corpus 17/17 on TauMC |
| `TransformPatcherCacheTest` green on both engines | default (`taumc`): 5 tests, 0 failures (inside the run above); `-PglslEngine=douira`: 5 tests, 0 failures, the test JVM logged `GLSL transform engine: douira` |
| Report | this page |

Orchestrator carry-overs:

| # | Carry-over | Resolution |
|---|---|---|
| (a) | A recorded-error case could not be accepted | **Fixed.** A case recorded with `outcome=error` whose replay succeeds, or throws something else, now writes `<case>.error.diff` (the difference and the replay's output) and is accepted by an `accepted.txt` entry with the new stage `error`. **Decision:** the new engine's success on `transform-grouped-330-undeclared` is accepted as "old engine threw", because TauMC's `IndexOutOfBoundsException` is a TauMC failure, not a behaviour to keep: the vertex shader calls no `ftransform()`, so `removeUnusedFunctions` removed the injected `iris_ftransform`, which was TauMC's variable anchor, and `transformGrouped`'s next injection found no anchor. `AstShaderTransformerTest.theGroupedCaseTauMCCouldNotTransform` shows both halves (TauMC throws `Index: -1, Size: 23`; with a `ftransform()` call added it transforms) and checks the new engine's output (declares `out vec3 viewDir;`, prepends `viewDir = vec3(0.0f);`, both stages parse again) |
| (b) | `readAccepted` accepted any stage name | **Fixed.** Stages outside `vertex, geometry, tess_control, tess_eval, fragment, compute, compat, error, *` fail with the line number; test `acceptedEntriesNeedAReasonAndAKnownStage` (typo, wrong case, blank reason, missing field; the committed file parses) |
| (c) | `TransformCorpus.Writer.git()` read the pipe before its timed wait | **Fixed.** The call is `TransformCorpus.run(dir, timeoutSeconds, command...)`: output goes to a temporary file, then `waitFor` with the timeout, then the file is read; a process still alive is destroyed. `TransformCorpusTest`: `sleep 30` with a 1 s timeout returns null after 1.13 s; with S2's read-then-wait order put back temporarily the same test failed after the full sleep (`BUILD FAILED in 32s`, `run/s5-corpus-unit-s2-order.out`) |
| (d) | `composite-120` keeps `gl_TexCoord[0]` under `#version 330 core` | **Reproduced, as a faithful port must.** The committed reference has `gl_TexCoord [ 0 ] = iris_MultiTexCoord0 ;` (vertex) and `texture ( colortex0 , gl_TexCoord [ 0 ] . st )` (fragment) under `#version 330 core`: TauMC's COMPOSITE path renames `gl_MultiTexCoord0` but leaves the `gl_TexCoord` built-in, which a core profile does not have. The new engine replays the case identically, so it keeps it too. Not a port question; see Open questions 3 |
| (e) | `transformGrouped` compared glsl-transformer's canonical type name | **Fixed.** `QualifiedDeclaration.typeName` is now the type as the source spells it: `ShaderAst`'s token filter records every numeric type keyword the lexer reads (glsl-transformer lexes `mat2` and `mat2x2` as one token type, `F32MAT2X2`, but keeps the text), and the parse pairs them in document order with the `BuiltinNumericTypeSpecifier`s it built (checked: same count, same `Type` per pair; otherwise no spelling is kept). The verifier's repro is a parity case in `transformGroupedWrittenOnShaderAst` ("square matrices spelled differently: nothing initialized"; TauMC leaves the vertex stage unchanged, and so does the port), with its counterpart "spelled alike: both initialized", and the same pair through both whole engines in `AstShaderTransformerTest.groupedTypesCompareAsSpelled`. `findQualifiers`' parity rows now compare TauMC's `type_specifier_nonarray` text unnormalized |
| (f) | `renameAndWrapShadow` recorded its wrappers in document order | **Matched TauMC** (cheap): the calls are wrapped in TauMC's cache order (`inTauMCOrder`: the program's own calls in document order, then the calls inside earlier additions). Parity case "a second rename's wrappers in TauMC's cache order, as removeConstAssignment sees them" (`WRAP_ORDER_330`: a `shadow2DLod` inside a `shadow2D` and one after it, in `const` initializers fed by a `const` parameter); probed against the TauMC jar first: TauMC removes `const` from `a` only, document order would remove it from `b` too |
| (g) | `'- -0.001'` merged into `'--0.001'` | **Named.** `renameAndWrapShadow`'s javadoc; test `deviationWrappedShadowCallKeepsANegatedLiteral`: TauMC's output contains `p . z -- 0.001` (its re-parse fails with `no viable alternative at input 'shadow2D(s,vec3(p.xy,p.z--0.001'` and error recovery leaves the call unrenamed and duplicated; glsl-transformer cannot parse it), `ShaderAst` prints `p . z - - 0.001` and its output parses |

Each new check was run against the code it guards against. With S4's canonical `typeName` and document-order wrapping put back temporarily (`run/s5-s4-behaviour.out`): `386 tests completed, 5 failed` (`groupedTypesCompareAsSpelled`, two `findQualifiers` rows, the wrapper-order case, the spelled-differently grouped case); the fixed file was restored and compared with `cmp`.

## Commits

| Commit | Subject |
|---|---|
| `8b7b3489` | glsl-transformer: S5 orchestrator, COMPOSITE and COMPUTE on glsl-transformer |
| the commit that adds this page | glsl-transformer: S5 report and status |

## What changed

Added:
- `shader/.../pipeline/transform/VersionNegotiation.java` (157 lines): the engine-neutral version code, moved unchanged
  out of `ShaderTransformer`: `VERSION_PATTERN`, `VERSION_REQUIREMENTS`, `init()`, `versionHoistingState()`,
  `resetForTesting()`, `getRequiredVersion`, `getStageMinimumVersion`, `negotiateVersion`, `NegotiationResult`.
- `shader/.../pipeline/transform/CompatibilityPatches.java` (130 lines): the three pre-parse pack patches
  (`patchCaveSkyholeClouds`, `patchVolumetricCloudReferenceDistance`, `patchCloudMovementTime`) and their patterns,
  moved unchanged out of the old `CompatibilityTransformer`, which delegates.
- `shader/.../pipeline/transform/transformer/` (in the package of `ShaderAst`, which lives in `glsm`):
  `CommonTransformer`, `CompatibilityTransformer` (`transformEach`, `transformGrouped`), `CompositeDepthTransformer`,
  `ComputeTransformer`, `TextureTransformer`, `EntityPatcher`, `CoreTransformHelper`. Each is its TauMC counterpart
  verb for verb with `ShaderAst` for `Transformer`; `findType`'s `0` is `null`, the sampler switch uses
  `DeclaredType.is(BuiltinType...)`. `CommonTransformer`'s `AdaptiveShadowBoundsTransformer.transform(root, type)` is a
  `TODO(S7)` comment. `CoreTransformHelper` keeps the `HashMap` whose iteration order decides the replacement order.
  `transformGrouped` is the S4 test method lifted into main code; `ShaderAstParityTest.transformGrouped` delegates to it.
- `src/test/.../transform/AstShaderTransformerTest.java` (7 tests), `src/test/.../glsm/debug/TransformCorpusTest.java`
  (2 tests).

Changed:
- `AstShaderTransformer` (34 to 282 lines): the orchestrator (below). COMPOSITE and COMPUTE are ported; any other kind
  throws `UnsupportedOperationException("glsl-transformer engine: <kind> not ported yet")` at entry, before any work.
- `ShaderTransformer`: the extracted code is gone; `init()`, `versionHoistingState()` and
  `resetVersionHoistingForTesting()` delegate to `VersionNegotiation`, so `Iris.java:653`, the recorder and the
  replayer are unchanged; `computeCeleritasHeader()` is package-private (the new engine uses it). Its transform code is
  otherwise untouched (the TauMC self-replay is byte-identical).
- `ShaderAst` (1,467 to 1,600 lines): one shared parser (`PARSER`) used only under `BUILD_LOCK`; a public
  `build(Supplier)` that holds the lock and restores the program's lexer version (every verb's snippet parse goes
  through it; later steps' Iris idioms must too); the spelled type names (e); TauMC order for the shadow wrappers (f)
  and the named deviation (g); `extensionDirectives()`.
- `TransformCorpus`: `run(dir, timeout, command...)` (c).
- `TransformCorpusReplayTest`: the `error` stage and `<case>.error.diff` (a), stage validation through
  `parseAccepted` (b), the engine time per patch kind (`replay: transformMs`), and `-PglslReplayThreads=N`, a concurrent
  pass: every case the engine replayed successfully is transformed again on this thread and then all at once on N
  threads (grouped by the global state they need), and each concurrent output must equal the replayed one.
- `ShaderAstParityTest`: the (e), (f), (g) cases above; `transformGrouped` delegates. `GlslCorpusParseSurveyTest`,
  `ShaderAstCorpusDifferential`: their reflection on `getRequiredVersion` targets `VersionNegotiation`.
- `accepted.txt`: the format documents `error` and the stage check; one entry (Residual diffs).
- `build.gradle` `test {}`: `-PglslEngine=taumc|douira` becomes `demonica.glsl.engine` in the test JVM (set only when
  given), `-PglslReplayThreads` becomes `demonica.glsl.replay.threads`.

Outside git: `run/s5-*.out`, the measurement runs `run/s5-measure/` (outputs and copies of the cache test's XML).

### The orchestrator

`AstShaderTransformer.transform`/`transformCompute` follow `ShaderTransformer` line for line up to the parse: the
version regex; hoisting with the scan quirks (the Celeritas header for CELERITAS_TERRAIN vertex shaders, the
adaptive-shadow-bounds marker for instrumented fragment shaders that `mayInjectRuntimeStats`); the stage minimum;
the negotiation; `replaceTexture`, `renameReservedWords`, `fixupQualifiers`, the two COMPOSITE fragment cloud patches,
`patchCloudMovementTime` (compute: only the first two). Then `ShaderAst.parse(input, effectiveVersion)` (the lexer at the
effective version; the source's own `#version` line is dropped at print), the extension lines, `doTransform`
(`CompositeDepthTransformer` or `ComputeTransformer`, then `TextureTransformer` and `transformEach`), across stages
`transformGrouped`, and each stage `restoreReservedWords(ast.print(header))` with the header
`#version N core\n` + (`\n` + extension lines). The Celeritas header is appended to the header text for CELERITAS_TERRAIN
vertex shaders (the Step 6 hook; unreachable until that kind is ported). It logs
`[Load #n] Transformed shader for <kind> in <time>` (and `... compute shader ...`) as the old engine does.

**Extensions.** *(Corrected in the verification follow-up; the first version of this paragraph said TauMC's header held
every directive of the source, and the new engine took every `#extension` of the tree.)* `ShaderAst.extensionDirectives()`
reads the `ExtensionDirective` nodes of the parsed program's leading directive block, in document order, right after
the parse, and formats them as TauMC's token printer did: `#extension NAME : behavior` (behavior by token, because
3.0.0-pre3 names the constant for `require` `DEBUG`). They are joined with `\n`. The leading block is TauMC's: its
pre-parser read the lexer's tokens on every channel (a `BufferedTokenStream`) and stopped at the first token outside a
directive, so its header held only the directive lines at the very start of the source, up to the first blank line,
comment, indented line or code. `ShaderAst.leadingExtensionCount` finds that block in the source text and counts its
`#extension` lines; `extensionDirectives()` returns that many of the tree's `ExtensionDirective` nodes, and any later
one is dropped (listed in `droppedDirectives()`, logged, removed at print), as TauMC's engine dropped it. The nodes come
from the parsed tree, not from a regex over the input, as the brief suggests: a regex would also match directives
inside comments (BSL's 336 inputs carry `#define` text in a block comment, S2), so it would need GLSL comment handling;
the parser already has it. TauMC printed every directive of that leading block except `#version`, so a `#define` or
`#pragma` there came back in its header; the new engine drops those (logged by `ShaderAst`). Named in the class
javadoc; test `differenceOtherDirectivesAreNotReemitted`. No recorded input has one (S2's survey).

**Old-engine code the new engine still calls, and where it lives:**

| Call | Where | After Step 11 |
|---|---|---|
| `VersionNegotiation.*` | `transform/VersionNegotiation` (new, engine-neutral) | stays |
| `CompatibilityPatches.patch*` | `transform/CompatibilityPatches` (new, engine-neutral) | stays |
| `GlslTransformUtils.replaceTexture`, `renameReservedWords`, `restoreReservedWords`, `TEXTURE_RENAMES` | `glsm` | stays (regex) |
| `CompatShaderTransformer.fixupQualifiers` | `glsm` | stays (library-free) |
| `AdaptiveShadowBoundsStats.isInstrumentationEnabled()`, `shaderVersionMarker()` | `pipeline/AdaptiveShadowBoundsStats` | stays |
| `AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats(String)` | the old TauMC class (string-only, package-private) | Step 7 moves it with the port |
| `ShaderTransformer.computeCeleritasHeader()` | the old engine (now package-private) | must move before Step 11 deletes the class |

## Commands run and their outcomes

The brief's Verify commands, in order, on the tree committed as `8b7b3489` (`run/s5-verify.out`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=36 identical=36 (byte-identical 0) accepted=0 failing=0 unsupported=0 recorded=0 filtered=388
    replay:   COMPOSITE {IDENTICAL=36}
    replay: transformMs engine=douira total=733.0 COMPOSITE=733.0/36
BUILD SUCCESSFUL in 2s
$ ./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
BUILD SUCCESSFUL in 2s
$ ./gradlew :test --tests '*TransformPatcherCacheTest' -PglslEngine=douira 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
BUILD SUCCESSFUL in 2s
$ ls build/reports/transform-replay | head; wc -l src/test/resources/transform-replay/accepted.txt
summary.txt
24 src/test/resources/transform-replay/accepted.txt
```
Gradle prints no "Tests run" line. From `build/test-results/test/` after the second command (`run/s5-verify2.out`;
executed at 22:11:51 UTC, a repeat was served from the build cache): AdaptiveShadowBoundsTransformerTest 9,
AstShaderTransformerTest 7, CeleritasTransformerTest 3, CompatibilityTransformerCaveSkyholeTest 1,
CompatibilityTransformerTest 6, GlslCorpusParseSurveyTest 1 (skipped), GlslTokensTest 10, GlslTransformerSpikeTest 3,
ShaderAstParityTest 379 (1 skipped), TransformCorpusReplayTest 2 (1 skipped), TransformPatcherCacheTest 5,
TransformPatcherTest 3: 429 tests, 0 failures, 0 errors. After the third: TransformPatcherCacheTest 5 tests, 0
failures, 0 errors, and its output holds `GLSL transform engine: douira`. The report directory holds only
`summary.txt` because the pack replay had no diff.

The mini-corpus on the new engine, with the concurrent pass (`run/s5-final-mini-douira.out`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE -PglslReplayThreads=8
    replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=6 identical=5 (byte-identical 0) accepted=1 failing=0 unsupported=0 recorded=0 filtered=10
    replay:   COMPOSITE {IDENTICAL=4, ACCEPTED=1}
    replay:   COMPUTE {IDENTICAL=1}
    replay: transformMs engine=douira total=100.6 COMPOSITE=95.8/5 COMPUTE=4.8/1
    replay: concurrent engine=douira threads=8 cases=5 groups=1 sequentialMs=10.2 concurrentWallMs=5.8 concurrentCallMs=25.0 differing=0
BUILD SUCCESSFUL in 2s
```
The pack corpora with the concurrent pass (`run/s5-final-packs-douira.out`): `cases=36 identical=36 ... failing=0`,
`replay: concurrent engine=douira threads=8 cases=36 groups=2 sequentialMs=482.8 concurrentWallMs=183.0
concurrentCallMs=1340.5 differing=0`.

Every kind on the new engine, the baseline for Step 6 (`run/s5-final-packs-douira-all.out`,
`run/s5-final-mini-douira-all.out`):
```
    replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=36 (byte-identical 0) accepted=0 failing=0 unsupported=388 recorded=0 filtered=0
    replay:   unsupported 37x: compat on douira: CompatShaderTransformer has no engine switch yet
    replay:   unsupported 339x: glsl-transformer engine: ATTRIBUTES not ported yet
    replay:   unsupported 12x: glsl-transformer engine: CELERITAS_TERRAIN not ported yet
    replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=16 identical=5 (byte-identical 0) accepted=1 failing=0 unsupported=10 recorded=0 filtered=0
```

The old engine after the extraction (`run/s5-replay-packs-taumc.out`, `run/s5-replay-mini-taumc.out`):
```
    replay: engine=taumc corpus=.../run/transform-corpus cases=424 identical=424 (byte-identical 424) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
    replay: engine=taumc corpus=.../src/test/resources/transform-corpus cases=16 identical=16 (byte-identical 16) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
```

`ShaderAst`'s corpus mode after its changes (`run/s5-corpus-parity-packs.out`, `run/s5-corpus-parity-mini.out`):
```
shader-ast-parity: corpus=.../run/transform-corpus inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=29
shader-ast-parity:   renameAndWrapShadow(Common) {IDENTICAL=221} changedTheProgram=26
shader-ast-parity:   transformGrouped {IDENTICAL=107} changedTheProgram=0
shader-ast-parity:   query findQualifiers same=1547 different=0
shader-ast-parity: corpus=.../src/test/resources/transform-corpus inputs=27 distinct=24 parseFailures=0 skipped=0 unexplained=0 seconds=0
```
(all other lines as in S4's final run).

The unit tests for (b) and (c) (`run/s5-corpus-unit.out`): TransformCorpusTest 2 tests, 0 failures
(`aCommandThatOutlivesItsTimeoutIsStopped` 1.131 s), TransformCorpusReplayTest 2 tests, 1 skipped (the corpus-gated
replay), 0 failures.

Full build, the step's one `check` run (`run/s5-build.out`, the final code before the commit, which changed nothing
since): `./gradlew build` gave `BUILD SUCCESSFUL in 11s`; `:test` executed: 130 classes, 950 tests, 0 failures, 0
errors, 4 skipped (22:11:04 to 22:11:07 UTC). `verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`,
`verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath` and `verifyS8tnlibPin` ran.
`build/libs/Demonica-0.5.0-SNAPSHOT.jar` holds 26 `net/coderbot/iris/pipeline/transform/transformer/*.class` entries
(S4: 17, `ShaderAst*` only) and `AstShaderTransformer`, `VersionNegotiation`, `CompatibilityPatches`. S4 had 128
classes and 936 tests: the difference is AstShaderTransformerTest (7), TransformCorpusTest (2), the replay's
accepted-file test (1) and four parity cases.

Skipped: dev runs. The brief asks for none (end-to-end runs start in Step 6: a pack needs ATTRIBUTES), and the
engine is still `taumc` by default.

## Measurements

### Lock and parser factory (the brief's item 3)

Three configurations of the new engine, each run three times (`run/s5-measure/`), against the TauMC engine. The replay
column is `TransformCorpusReplayTest` on the 36 COMPOSITE pack cases (`-PglslReplayPatches=COMPOSITE,COMPUTE
-PglslReplayThreads=8`): "first" is the replay's own pass (cold JIT), "warm" the concurrent pass's sequential re-run,
"8 threads" all 36 cases submitted at once to eight threads (wall time, and the sum of the calls' own times). The cache
test is `TransformPatcherCacheTest` under `-PglslEngine` (suite time, and its eight-thread `concurrentMisses` test).
Means with the range of the three runs, in milliseconds:

| Configuration | Replay first | Replay warm | 8 threads, wall | 8 threads, sum of calls | Cache test suite | Cache test, 8 concurrent misses | Differing outputs |
|---|---|---|---|---|---|---|---|
| one parser per call, lock per build | 778.4 (769.3-783.9) | 513.6 (493.7-537.2) | 197.6 (191.1-209.3) | 1,451.3 | 295 (286-304) | 35 (34-37) | 0 |
| one shared parser, lock around the whole transform | 760.9 (756.1-764.9) | 486.1 (482.9-489.7) | 480.7 (469.5-497.7) | 3,343.7 | 266 (262-270) | 21 (19-22) | 0 |
| **one shared parser, lock per build (kept)** | 762.5 (746.6-774.3) | 483.9 (475.0-491.5) | 183.1 (176.6-188.0) | 1,339.6 | 266 (259-272) | 17 (15-20) | 0 |
| TauMC engine (reference) | 1,124.7 (1,114.1-1,135.9) | 668.2 (651.8-695.1) | 173.3 (171.9-175.1) | 1,145.0 | 250 (247-252) | 11 (10-13) | 0 |

**Decision: one shared parser behind the existing `ReentrantLock`, `ShaderAst.BUILD_LOCK`, held per parse and per node
build, not around the whole transform.** It is the fastest new-engine variant on every column. The whole-transform
lock is as fast alone and serializes eight threads (wall ≈ the sequential time). Against one parser per call, the
shared parser keeps its AST cache of the verbs' snippets across programs (`uniform float iris_FogDensity;` is parsed
once and cloned after). All nine replay runs and nine cache-test runs passed, every concurrent output equal to the
sequential one. The shared-parser variants were measured before a race I found by reading my own code (below) was
fixed; the fix adds two list copies under the lock, and the final code measured the same (`sequentialMs=482.8
concurrentWallMs=183.0`, above).

What `BUILD_LOCK` costs: alone, the new engine is about 28 % faster than TauMC's (warm 484 against 668 ms; first pass
763 against 1,125 ms). On eight threads TauMC's wall time falls to 26 % of its sequential time, the new engine's only
to 38 %, because every parse, which holds the lock for the ANTLR parse as well as the AST build (S3 remark 6), is
serialized; so on eight threads the new engine is about 6 % slower than TauMC (183 against 173 ms), and the cache
test's eight concurrent misses take 17 against 11 ms. For scale, the same 36 cases took 5,320.2 ms of `transformMs` in
the recording runs (TauMC, in game, on the transform threads, cold).

The race: with a shared parser, the program's token filter stays set on it after the parse, and the next parse (another
program's snippet, on another thread) calls `resetState()` on that filter, which clears the maps `parse` read after
releasing the lock (dropped directives, type tokens). `parse` now copies them before releasing the lock. The
per-call parser did not have the problem.

### Other

- The replay's engine time on the pack COMPOSITE cases, first pass, across this step's runs: 733.0 to 783.9 ms
  (TauMC 951.2 ms in the full-corpus replay, 1,114 to 1,136 ms filtered to COMPOSITE).
- COMPOSITE in the pack corpora: 36 cases, 32 recorded with the adaptive-shadow-bounds instrumentation on; none of the
  36 fragment inputs has a PCF helper (`shadowMapResolution` and `texture2DShadow2x2` or `SampleFilteredShadow`, what
  `mayInjectRuntimeStats` tests). The inputs that have one: 148 ATTRIBUTES and 6 CELERITAS_TERRAIN cases.
- `ShaderAst.java` 1,600 lines (1,467); `AstShaderTransformer.java` 282; the seven ported classes 445 together.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 36 pack COMPOSITE cases | vertex, fragment | identical (tokens) | none |
| mini-corpus `composite-120`, `composite-330`, `transform-each`, `transform-grouped` (COMPOSITE), `compute` (COMPUTE) | all | identical (tokens) | none |
| `transform-grouped-330-undeclared` (mini-corpus) | error | TauMC threw (removed injection anchor, see (a)); the new engine transforms it into valid GLSL | accepted: `transform-grouped-330-undeclared \| error \| old engine threw (removed injection anchor); the new engine transforms it, checked by AstShaderTransformerTest` |
| `composite-120` (mini-corpus) | vertex, fragment | TauMC behaviour kept: `gl_TexCoord[0]` under `#version 330 core` (d) | none (identical); Open questions 3 |
| `composite-extension-placement` (mini-corpus, added by the verification follow-up) | vertex, fragment | identical (tokens): only the leading `#extension` reaches the header, as in TauMC | none |
| COMPOSITE and COMPUTE cases with a PCF helper | fragment | S7 pending | none needed: there are none; the adaptive-shadow-bounds `TODO(S7)` changes no COMPOSITE or COMPUTE output in any corpus |
| ATTRIBUTES, CELERITAS_TERRAIN, DH, COMPAT | all | unsupported (not ported) | Steps 6, 7, 10 |

No formatting diff reached `GlslTokens`, and no ordering diff occurred: the injected declarations came out in TauMC's
order in every case.

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1" (the orchestrator's
   rule).
2. The extension lines come from the parsed tree (`ShaderAst.extensionDirectives()`), not from a regex over the input
   (reason under "Extensions"); only those of the leading directive block, as TauMC (verification follow-up). The other
   directives of that block, which TauMC re-emitted in its header, are not re-emitted (named, tested).
3. The factory: three configurations were measured, not two, and the one kept is a shared instance behind the
   existing `BUILD_LOCK` held per build, not a lock around the whole transform (numbers above). There is no separate
   `ReentrantLock` in the orchestrator. `ShaderAst.build(Supplier)` is new public API for code that builds nodes itself.
4. `VersionNegotiation` also holds `init`, `versionHoistingState` and the hoisting reset (the orchestrator asked to keep
   `ShaderTransformer.versionHoistingState()` and a reset hook; they delegate). Two tests reflect on
   `VersionNegotiation.getRequiredVersion` instead of `ShaderTransformer`'s.
5. The pack text patches moved to a new `CompatibilityPatches` (the brief allowed it); the old class delegates, so
   `CompatibilityTransformerCaveSkyholeTest` is unchanged.
6. The ported classes are public (the old ones were partly package-private): they sit in `transformer/`, the
   orchestrator in `transform/`.
7. An unported kind throws at the entry of `transform`/`transformCompute`, before hoisting or parsing, and also in
   `doTransform`'s default branch.
8. A source that does not parse throws `ShaderAst.SyntaxException`; TauMC re-parsed with ANTLR's error recovery and
   returned what it recovered (test `differenceASyntaxErrorThrows`; Open questions 1).
9. Beyond the brief: carry-overs (a) to (g), the replay's timing line and concurrent pass (`-PglslReplayThreads`),
   `TransformCorpus.run`, and the tests `AstShaderTransformerTest` and `TransformCorpusTest`.
10. No `accepted.txt` entry says "S7 pending": no COMPOSITE or COMPUTE case needs one (Measurements).

## Open questions

1. Syntax errors: the new engine throws where TauMC returned an error-recovered program (which the driver then
   rejected or, by luck, compiled). Iris throws too. No corpus input fails to parse (S2's survey: 774 of 774 prepared
   Iris inputs parse). *(Verification follow-up: the throw is reachable from pack-shaped input: an identifier `patch`
   in a 120 shader hoisted to 400 or later, which `renameReservedWords` does not rename; an `#extension` inside a
   function body; an `#extension` before `#version`.)* Keep throwing when Step 8 flips the default, or catch and fall
   back to the untransformed source?
2. The header drops the `#define`, `#pragma` and other directives of the leading block that TauMC re-emitted. Sources
   are preprocessed first (`JcppProcessor`), so this should never matter; confirm it is acceptable.
3. `composite-120`: both engines leave `gl_TexCoord[0]` in a `#version 330 core` program, which a strict core compiler
   rejects (the output is recorded; it was not compiled). It predates the migration. Port Iris's `gl_TexCoord`
   handling after Step 8, or leave it?
4. `BUILD_LOCK` makes the new engine about 6 % slower than TauMC on eight threads (and 28 % faster alone). If load time
   under concurrency matters, splitting the ANTLR parse (thread-safe with a parser per thread) from the AST build
   (which needs the lock) means driving `EnhancedParser` and `ASTBuilder` directly. Worth it after Step 8's real load
   times?
5. S2's open question 3 stands: the mini-corpus replay runs only with `-PglslCorpusDir`, so `check` does not guard it.
6. *(Verification follow-up.)* `#extension` placement: the new engine drops an `#extension` after the leading directive
   block, as TauMC did. Hoisting such a line into the header instead would suit a pack whose include order puts one
   after code (the GLSL specification, section 3.3, wants extension directives before any non-preprocessor token
   unless the extension says otherwise), but it changes output, and a hoisted `require` of an extension the driver
   lacks fails a program that compiled before. Revisit after Step 8's end-to-end runs, if a pack needs it?

## Notes for the next step

- Replay: `./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=<abs> -PglslReplayEngine=douira
  -PglslReplayPatches=ATTRIBUTES,CELERITAS_TERRAIN [-PglslReplayThreads=8]`, then `grep -E 'replay|BUILD'`. The
  summary now has `replay: transformMs` (engine time per kind) and, with threads, `replay: concurrent ...
  differing=0`, which must stay 0. A recorded error that the replay does not reproduce shows as `[error]`; accept it
  with the stage `error`, only with a checked reason. Unknown stages in `accepted.txt` fail the test.
- Porting a kind: add it to `AstShaderTransformer.PORTED` and to `doTransform`'s switch. The CELERITAS_TERRAIN hoisting
  scan and header hook are already in `transformInternal`. `patchMultiTexCoord3`, `replaceMidTexCoord`,
  `replaceMCEntity`, `addIfNotExists`, `addIfNotExistsType` are still only in `ShaderTransformer` (TauMC); port them
  next to `applyIntelHd4000Workaround` in `AstShaderTransformer`. `findType` gives `ShaderAst.DeclaredType` or null:
  `mc_Entity`'s switch is `Type.FLOAT32`, `F32VEC2..4`, `UINT32`, `INT32`, `I32VEC2..4`, `BOOL` (S3's table).
- `EntityPatcher` is already ported (`transformer/EntityPatcher`), waiting for `AttributeTransformer`.
- S7 pending: the ATTRIBUTES and CELERITAS_TERRAIN cases whose fragment input has a PCF helper (148 and 6 in the pack
  corpora; `grep -l shadowMapResolution` plus `texture2DShadow2x2|SampleFilteredShadow` on `in.fragment.glsl`) will
  differ until Step 7; with 379 of 424 cases recorded instrumented, most of those are instrumented. Accept them per
  case directory, fragment stage only.
- Code that builds glsl-transformer nodes itself (Iris idioms) must run inside `ast.build(() -> ...)`: the parser `t` is
  shared by every program and guarded by `BUILD_LOCK`, and `build` also restores the program's lexer version.
- `typeName` of `findQualifiers` is the spelled type for the parsed program's declarations; a declaration a verb added
  has the compact name (`mat2`). `typeText` is printed by glsl-transformer, so it always has the compact name.
- `ShaderTransformer.computeCeleritasHeader()` and `AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats` are
  old-engine code the new engine calls; Step 7 and Step 11 must move them (table under "What changed").
- The `-PglslEngine=douira` switch reaches the test JVM, so a test that goes through `TransformPatcher` can run on the
  new engine. `TransformPatcherTest` on it (`run/s5-transformpatcher-douira.out`): `3 tests completed, 2 failed`:
  `compositeVertexLegacyGlColorIsRewritten` fails its raw-string assertion (`color = iris_FrontColor ;`, TauMC's token
  spacing, against the indented print; formatting only, Step 8 ports it to `GlslTokens`), and
  `terrainVertexGlColorStaysOnCeleritasVertexColor` throws `UnsupportedOperationException` (CELERITAS_TERRAIN, Step 6).

## Verification follow-up

An independent verification (outputs `run/s5v-*.out`, probes in `run/s5v-probe-min/` and `run/s5v-probe-corpus/`)
found one blocking issue and made seven remarks. Fixed on 2026-09-28 on `feat/glsl-transformer` in the commit
"glsl-transformer: S5 fix #extension lines after the leading directives" (code, tests, a mini-corpus case, this
section); the next commit records it in `STATUS.md`. Status stays **done**. Outputs of this follow-up:
`run/s5f-*.out`.

### Blocking: an `#extension` after code went into the header; TauMC dropped it

**Confirmed, and wider than reported.** TauMC's `ShaderParser.parseShader` (`run/lib-src/taumc/ShaderParser.java`)
gives its `GLSLPreParser` a `BufferedTokenStream`, which, unlike the main parser's `CommonTokenStream`, does not filter
channels, and the pre-parser stops at the first token outside a directive. Whitespace and comments are tokens on hidden
channels (`GLSLLexer.g4` lines 297-301), so the pre-parser stops at a blank line, a comment or an indented line as well
as at code. Probed on the pinned jar with jshell (`ShaderParser.parseShader(source).pre()` printed with
`GlslTransformUtils.getFormattedShader`, as `ShaderTransformer` builds its header), every source starting
`#version 330 core`:

| What follows `#version 330 core` | `#extension` lines in TauMC's header |
|---|---|
| an `#extension` directly; or two | 1; 2 |
| a declaration, then an `#extension` (the verifier's `p9-midfile-extension`) | 0 |
| an empty declaration `;`, a function, or `precision highp float;`, then an `#extension` | 0 |
| a blank line, then an `#extension` | 0 |
| a `// comment` or `/* comment */` line, then an `#extension` | 0 |
| an indented `  #extension` | 0 |
| `#define X 1`, `#pragma optimize(on)` or `#line 5`, then an `#extension` | 1 (and the `#define`, `#pragma` or `#line` too) |
| a `#define` continued with a backslash on the next line, then an `#extension` | 1 |
| `# extension` (a space after `#`) | 1 |
| CRLF line ends; trailing blanks on the `#version` line | 1 |
| (a blank line before `#version`) | 0, and no other directive |
| `#extension A : enable /* c */`, then `#extension B` | 1 (A) |
| `#version 330 core /* c */`, then an `#extension` | 1 |
| `#version 330 core // c`, then an `#extension` | 0; lexer errors swallow the program text after it too |

So the verifier's first proposed rule, "the extension directives before the first non-directive external
declaration", is not TauMC's either: glsl-transformer's tree has no blank lines or comments in it. The last three rows
are TauMC's lexer error recovery: its directive lexer modes have no comment token.

**Fix: TauMC's behaviour, not a named deviation.** The step's goal is replay parity, and moving a `require` into the
header can make a program that compiled with TauMC's output fail on a driver without the extension. A behaviour change,
if wanted, is a decision for after Step 8 (Open questions 6). `ShaderAst.parse` counts the `#extension` lines of the
leading block in the source text (`leadingExtensionCount`: from the start of the source, the lines that begin with `#`
in column 0, up to the first line that does not; a backslash before the line break continues a line, as in a
`#define`). `extensionDirectives()` returns that many of the tree's `ExtensionDirective` nodes, in document order (the
block is a prefix of the document, so they are its first ones); every later one is listed in `droppedDirectives()` as
`#extension NAME : behavior (after the leading directives)` and logged with the other dropped directives (the message
is now `[ShaderAst] Dropped N preprocessor directive(s): [...]`; it said "that the transform does not evaluate", which
does not fit an `#extension`). `print` removes every `#extension` from the body, as before, so a late one is gone from
the output, as in TauMC's. The rule sits in `ShaderAst`, so every patch kind ported later gets it. Not modelled, because preprocessed sources have neither: a comment on a directive line (the
last three rows; the rule keeps the next directive; named in the javadoc, test `deviationACommentOnADirectiveLine`), and
conditional directives, after which TauMC's lexer reads program text into the block.

**Corpus.** A scan of all 797 recorded inputs (`in.*.glsl` under `run/transform-corpus/` and the mini-corpus, Python,
with the same leading-block rule): 111 inputs contain an `#extension`, one line each, and all 111 lines are in the
leading block; in every one of those cases, TauMC's recorded output has as many extension lines as the rule predicts.
So the fix changes no recorded output, and the corpus replay could not have caught the issue (the verifier found 0
inputs of this shape).

**Tests.**
- `ShaderAstParityTest.extensionHeaderLines`: 20 rows (the table's shapes, without the comment-on-a-directive rows).
  Each asserts the number of lines TauMC's header keeps, on the pinned jar (so the rows document the rule), that
  `extensionDirectives()` equals TauMC's lines, that each later `#extension` is in `droppedDirectives()`, and that the
  printed program has no `#extension`.
- `ShaderAstParityTest.deviationACommentOnADirectiveLine`: TauMC keeps A only; `ShaderAst` keeps A and B.
- `AstShaderTransformerTest.extensionsAfterTheLeadingDirectivesAreDropped`: the verifier's `p9` program through both
  whole engines (same tokens; no `#extension` in the new engine's fragment output), and a fragment shader with one
  `#extension` leading, one after a blank line and one after a comment (same tokens; only the leading one, directly
  under `#version 330 core`).
- Mini-corpus case `composite-extension-placement` (hand-written; recorded with `-PglslReplayEngine=taumc
  -PglslReplayRecord=true` into a scratch copy, `run/s5f-record.out`, and the two `out.taumc.*` files copied in): the
  vertex shader has an `#extension` after a blank line; the fragment shader one leading, one after a declaration
  (`require`), one after a comment. TauMC's header keeps the fragment's first only. The replay now covers the shape.

The tests and the case were run against the old rule, with `extensionDirectives()` returning every node again (the
node count passed to the constructor, one edit; `run/s5f-unit-old-rule.out`, `run/s5f-mini-douira-old-rule.out`; the
deviation test passes under both rules, which both keep B):
```
AstShaderTransformerTest > extensionsAfterTheLeadingDirectivesAreDropped() FAILED
ShaderAstParityTest > extensionHeaderLines() > after a declaration (the verifier's repro) FAILED
... (ten more rows: one leading and one after a declaration, after an empty declaration, after a function, after a
default precision, after a blank line, one leading and one after a blank line, after a line comment, after a block
comment, indented, a blank line before #version)
408 tests completed, 12 failed, 1 skipped
    replay:   FAILING composite-extension-placement [vertex, fragment]
```
with the diffs `+ #extension GL_EXT_gpu_shader4 : require` and `+ #extension GL_ARB_gpu_shader5 : enable` (fragment)
and `+ #extension GL_ARB_explicit_attrib_location : enable` (vertex). The fixed file was restored and compared with
`cmp`. The verifier's probe on the fixed engine (`run/s5f-probe-p9-douira.out`): `cases=1 identical=1 ... failing=0`
(it was `FAILING probe/p9-midfile-extension [fragment]`, `run/s5v-probe-min-douira.out`).

**Text corrected.** The `AstShaderTransformer` class javadoc; `ShaderAst`'s javadocs of `parse`,
`extensionDirectives`, `droppedDirectives` and the new `leadingExtensionCount`; the javadoc of
`differenceOtherDirectivesAreNotReemitted` (TauMC's header held the directives of the leading block, not every
directive; the test's `#define` and `#pragma` are in that block, so it stands); and this report's "Extensions"
paragraph, Deviation 2 and Open question 2 (corrected in place).

**Two more `#extension` shapes where the engines differ**, found while probing (jshell against `glsm`'s classes), both
covered by Deviation 8 and Open question 1 (the new engine throws where TauMC went on): an `#extension` inside a
function body (TauMC's parser ignores directives, so it is dropped; glsl-transformer throws
`SyntaxException: line 3:0 mismatched input '#'`), and an `#extension` before `#version` (TauMC's header keeps it;
glsl-transformer throws `no viable alternative at input '#version'`). No recorded input has either: all 111 are in the
leading block.

### What changed

- `glsm/.../transformer/ShaderAst.java`: `leadingExtensionCount` (new, package-private); `parse` counts the leading
  block's `#extension` lines, lists the later ones in `droppedDirectives()` and logs them; `extensionDirectives()`
  returns the leading ones only; `extensionLine` (extracted); the log message; javadocs of `parse`,
  `extensionDirectives`, `droppedDirectives` and `newParser` (the snippet cache, remark 3).
- `shader/.../transform/AstShaderTransformer.java`: the class javadoc.
- `glsm/.../debug/TransformCorpus.java`: `Writer`'s javadoc back above `Writer` (remark 4).
- `src/test/.../AstShaderTransformerTest.java`: `extensionsAfterTheLeadingDirectivesAreDropped`; two javadocs.
- `src/test/.../ShaderAstParityTest.java`: `taumcHeaderExtensions`, `extensionHeaderLines` (20 rows),
  `deviationACommentOnADirectiveLine`.
- Added `src/test/resources/transform-corpus/composite-extension-placement/` (`case.properties`, two inputs, two
  TauMC outputs).
- This report (corrections in place, this section) and `STATUS.md`.

### Remarks

| # | Remark | Resolution |
|---|---|---|
| 1 | Only one failing-or-accepted diff exists (`transform-grouped-330-undeclared`), so three could not be opened | Nothing to change. After this follow-up's mini-corpus replay, `build/reports/transform-replay/` holds `summary.txt` and `transform-grouped-330-undeclared.error.diff`; after the pack replay, `summary.txt` only |
| 2 | TauMC's removed-anchor `IndexOutOfBoundsException` also hits the verifier's probes `p1-geometry` (vertex, geometry and fragment at 330) and `p7-declarators`; the new engine transforms both validly | Not re-run here. Carried to the Notes: expect more `error`-stage entries of this class from Step 6 on, and accept each only after checking the new engine's output, as `AstShaderTransformerTest.theGroupedCaseTauMCCouldNotTransform` does |
| 3 | The snippet AST cache is keyed on (text, rule), not on the lexer version | **Confirmed** in the sources: `ASTParser.parseNodeCachedUncloned` calls `buildCache.cachedGet(input, parseShape.ruleType, ...)`; `TypedTreeCache` is an `LRUCache` keyed on `CacheKey(String input, Class ruleType)`, default size 400. **Documented** in `ShaderAst.newParser`'s javadoc: a snippet first parsed for a program at one version is reused at others, and `build` sets the version only for a miss. The risk stays small: the verbs' snippets are Iris's own code, and `renameReservedWords` renames `sample` and `new` (and `sampler` from 400) in pack code first |
| 4 | `TransformCorpus.Writer`'s javadoc sat orphaned above `run()`'s | **Fixed**: moved back above `Writer`, where it was at `10750ef6` |
| 5 | `STATUS.md`'s S5 row names "the S5 report-and-status commit", not `448264f5` | **Fixed** in the status commit that follows this one, which also names this fix |
| 6 | Gradle prints no "Tests run" lines, and a plain re-run of the same `:test` command can come FROM-CACHE | **Agreed.** Every Gradle test run of this follow-up used `--rerun`; the counts below are from `build/test-results/test/*.xml`, with their timestamps |
| 7 | Probe `p6` (`sample`, `patch` in a 120 shader hoisted to 420): `renameReservedWords` does not rename `patch`, so the new engine throws `SyntaxException`; TauMC failed to parse too and emitted broken GLSL | **Confirmed by reading the table**: `GlslTransformUtils.VERSIONED_RESERVED_WORDS` has `sample` and `new` (every version) and `sampler` (400 on), no `patch`. Not re-probed. Added to Open question 1 as evidence that the throw is reachable from pack-shaped input |

Open question 1 now names these three inputs (remark 7 and the two `#extension` shapes above), and Open question 6 is
new; both are edited in place under "Open questions".

### Commands run and their outcomes

Every Gradle run one at a time, with `--rerun`; counts from `build/test-results/test/*.xml`.

- The two affected classes (`run/s5f-unit.out`): `./gradlew :test --tests '*AstShaderTransformerTest' --tests
  '*ShaderAstParityTest' --rerun`: `BUILD SUCCESSFUL in 4s`; AstShaderTransformerTest 8 tests, 0 failures;
  ShaderAstParityTest 400 tests, 0 failures, 1 skipped (22:40:51 UTC). A first run failed one row I had added,
  "at the end of the source" (`#version 330 core\n#extension ...` with no final newline):
  `SyntaxException: line 2:45 missing NR_EOL at '<EOF>'`, glsl-transformer's grammar needing a line end after a
  directive. Not related to the rule; the row was removed.
- The brief's Verify 1, with the concurrent pass (`run/s5f-packs-douira.out`):
  ```
  replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=36 identical=36 (byte-identical 0) accepted=0 failing=0 unsupported=0 recorded=0 filtered=388
  replay:   COMPOSITE {IDENTICAL=36}
  replay: transformMs engine=douira total=873.1 COMPOSITE=873.1/36
  replay: concurrent engine=douira threads=8 cases=36 groups=2 sequentialMs=532.6 concurrentWallMs=211.8 concurrentCallMs=1544.1 differing=0
  BUILD SUCCESSFUL in 4s
  ```
  `build/reports/transform-replay/` then held `summary.txt` only.
- The mini-corpus on the new engine (`run/s5f-mini-douira-final.out`):
  ```
  replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/src/test/resources/transform-corpus cases=7 identical=6 (byte-identical 0) accepted=1 failing=0 unsupported=0 recorded=0 filtered=10
  replay:   COMPOSITE {IDENTICAL=5, ACCEPTED=1}
  replay:   COMPUTE {IDENTICAL=1}
  replay: concurrent engine=douira threads=8 cases=6 groups=1 sequentialMs=14.8 concurrentWallMs=10.0 concurrentCallMs=52.4 differing=0
  BUILD SUCCESSFUL in 2s
  ```
- The mini-corpus on TauMC, every kind (`run/s5f-mini-taumc.out`): `cases=17 identical=17 (byte-identical 17)
  accepted=0 failing=0 unsupported=0`. The TauMC pack replay was not re-run for every kind: no TauMC-engine code
  changed. Its COMPOSITE cases ran for the timing below (`run/s5f-packs-taumc-timing.out`: `cases=36 identical=36
  (byte-identical 36)`).
- `ShaderAst`'s corpus mode on the pack corpora (`run/s5f-corpus-parity-packs.out`): `inputs=811 distinct=221
  parseFailures=0 skipped=0 unexplained=0 seconds=33`; ShaderAstParityTest 400 tests, 0 failures, 0 skipped.
- The brief's Verify 2 (`run/s5f-verify2.out`): `BUILD SUCCESSFUL in 2s`; AdaptiveShadowBoundsTransformerTest 9,
  AstShaderTransformerTest 8, CeleritasTransformerTest 3, CompatibilityTransformerCaveSkyholeTest 1,
  CompatibilityTransformerTest 6, GlslCorpusParseSurveyTest 1 (skipped), GlslTokensTest 10, GlslTransformerSpikeTest 3,
  ShaderAstParityTest 400 (1 skipped), TransformCorpusReplayTest 2 (1 skipped), TransformPatcherCacheTest 5,
  TransformPatcherTest 3: 451 tests, 0 failures, 0 errors, 3 skipped (22:42:57 to 22:42:58 UTC). The 22 more than
  S5's 429: 20 rows, the deviation test and the orchestrator test.
- The brief's Verify 3 (`run/s5f-verify3.out`): TransformPatcherCacheTest 5 tests, 0 failures, 0 errors (22:43:07
  UTC); its output holds `GLSL transform engine: douira`.
- Verify 4: `ls build/reports/transform-replay` after the pack replay: `summary.txt`; `accepted.txt` 24 lines
  (unchanged).
- Full build, the step's second `check` run (`run/s5f-build.out`): `./gradlew build` gave `BUILD SUCCESSFUL in 11s`;
  `:test` executed 130 classes, 972 tests, 0 failures, 0 errors, 4 skipped (22:43:22 to 22:43:25 UTC; S5: 950).
  `verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`,
  `verifyModuleBoundaries`, `verifyRunClasspath` and `verifyS8tnlibPin` ran. It ran before one last edit to
  `ShaderAst` (the dropped-directive log message, and a sentence of `newParser`'s javadoc); after that edit, with no
  third `check` run, the Verify commands ran again on the committed code: Verify 2 (`run/s5f-verify2-final.out`) 12
  classes, 451 tests, 0 failures, 0 errors, 3 skipped (22:48:45 to 22:48:46 UTC); Verify 1
  (`run/s5f-verify1-final.out`) `cases=36 identical=36 ... failing=0`; the mini-corpus (`run/s5f-mini-douira-final2.out`)
  `cases=7 identical=6 ... accepted=1 failing=0`, `concurrent ... differing=0`; Verify 3 (`run/s5f-verify3-final.out`)
  5 tests, 0 failures, engine `douira`. The timing runs below swapped five source files and restored them (compared
  with `cmp`) before that edit.
- Skipped: dev runs (as in S5: no pack runs on the new engine before Step 6), the `taumc` all-kind pack replay (no
  TauMC-engine code changed), the S5 lock measurements (the lock and parser are unchanged).

**Timing.** This follow-up's replay times are higher than S5's on both engines: the machine was loaded by processes
that are not mine (load average 4.07 with no Gradle run; a `javap` at 257 % CPU). TauMC's engine on the 36 COMPOSITE
cases: first pass 1,253.1 ms, warm 703.5 ms (S5: 1,114 to 1,136 and 652 to 695). For the fix's own cost, the S5 code
and the fixed code were run in turn, three times each, under the same load (`run/s5f-ab-*.out`; the five changed
source files swapped, then restored and compared with `cmp`):

| Code | Replay first pass (ms) | Warm (ms) | 8 threads, wall (ms) |
|---|---|---|---|
| S5 (`448264f5`) | 918.4, 853.9, 857.8 (mean 876.7) | 541.7, 534.8, 526.8 (534.4) | 224.1, 224.5, 213.3 |
| this fix | 944.8, 867.9, 906.3 (mean 906.3) | 522.2, 530.3, 539.0 (530.5) | 209.0, 198.1, 219.0 |

Warm and concurrent times are the same; the first pass differs by 3 %, inside the spread of either side. The scan
reads only the leading directive lines of each source.

### Notes for the next step (additions)

- `ShaderAst.extensionDirectives()` is TauMC's header rule: the leading directive block only. Later `#extension`
  lines are dropped and listed in `droppedDirectives()` with the suffix `(after the leading directives)`. Every recorded
  input of every kind has its `#extension` lines in the leading block, so ATTRIBUTES and CELERITAS_TERRAIN should
  show no diff from it.
- GLSM's `CompatShaderTransformer` separates its preamble with its own text code (`separatePreprocessorPreamble`) and
  does not use TauMC's pre-parser; Step 10 should keep that and not switch to `extensionDirectives()` without
  comparing.
- TauMC's removed-anchor `IndexOutOfBoundsException` (remark 2): more `error`-stage cases of this class will appear.
  Accept one only after checking the new engine's output parses and declares what the grouped step injects.
- Code that hands pack-derived text to a verb as a snippet (not Iris's fixed strings) meets remark 3: the snippet
  cache ignores the lexer version.
- The mini-corpus has 17 cases now (`composite-extension-placement` added).
