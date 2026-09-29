# S06: ATTRIBUTES and CELERITAS_TERRAIN

Step 6 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `e2278733` (S1 to S5 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds, and the orchestrator's additions (the TauMC by-text cache case, the S5
leftovers, the class-loading check) are closed.

| Done when | Evidence |
|---|---|
| Replay clean for the four kinds ported so far | Pack corpora (the brief's Verify 1, `run/s6-verify1.out`): `cases=387 identical=336 (byte-identical 0) accepted=51 failing=0 unsupported=0`, `S7 pending stages verified as adaptive shadow bounds only: 51`. Mini-corpus (`run/s6-replay-mini-final.out`): `cases=12 identical=8 accepted=4 failing=0 unsupported=0`, 2 S7 pending verified, `concurrent ... differing=0`. Every accepted stage is in "Residual diffs" |
| `TerrainVertexFormatRequirementsTest` green | Unchanged, 4 tests, 0 failures (Verify 2, `run/s6-verify2.out`, 23:33:26 UTC); also in the full build |
| The three packs load and render on the new engine, screenshot differences at the level of moving foliage and water | BSL, Complementary Reimagined and I Like Vanilla: `Dev shader pack: ... (loaded: true)`, 0 `Shader compilation failed`/`Failed to compile`/`UnsupportedOperation`/`SyntaxException`; every frame within the TauMC-against-TauMC noise floor (Measurements) |
| Report | this page |

## Commits

| Commit | Subject |
|---|---|
| `09e53d27` | glsl-transformer: S6 ATTRIBUTES and CELERITAS_TERRAIN on glsl-transformer |
| `78534be0` | glsl-transformer: S6 report (this page, and the `accepted.txt` comment correction) |
| `0eb8356c` | glsl-transformer: S6 status |
| `0c9b9579` | glsl-transformer: S6 report commit table |
| the commit that records this row | glsl-transformer: S6 report line count |

## What changed

Added:
- `shader/.../pipeline/transform/transformer/AttributeTransformer.java` (80 lines) and `CeleritasTransformer.java`
  (102): the TauMC classes verb for verb on `ShaderAst`, with the same injected source strings and the same `HashMap`s
  (their iteration order decides the order of the replacements and renames).
- `src/test/.../celeritas/vertices/TerrainVertexFormatScanParityTest.java` (199 lines, 23 tests): the new identifier
  scan against the TauMC `GLSLLexer` scan it replaced (reproduced in the test as the oracle): 12 shapes with equal
  counts, 9 lexer-error shapes that keep the complete format, the two named differences, and a corpus mode
  (`-PglslCorpusDir`) over every recorded `out.taumc.vertex.glsl` and `out.douira.vertex.glsl`. Uses TauMC; Step 11
  deletes it.
- `src/test/.../pipeline/transform/transformer/ShaderAstLineEndTest.java` (4 tests): `leadingExtensionCount` with LF,
  CRLF and lone CR, and that a source whose directives end in lone CRs does not parse.
- Mini-corpus case `src/test/resources/transform-corpus/celeritas-terrain-multitexcoord3/` (the TauMC cache case,
  below; recorded as a TauMC error). The mini-corpus has 18 cases.

Changed:
- `AstShaderTransformer` (285 to 402 lines): `PORTED` has ATTRIBUTES and CELERITAS_TERRAIN; `doTransform` dispatches
  them (CELERITAS_TERRAIN: `CeleritasTransformer`, `patchMultiTexCoord3`, `replaceMidTexCoord(MID_TEX_SCALE)`,
  `replaceMCEntity`, `applyIntelHd4000Workaround`, as the TauMC engine); the four helpers and `addIfNotExists`,
  `addIfNotExistsType` are ported next to `applyIntelHd4000Workaround` (the brief's first option, as S5 suggested).
  The Celeritas header stays text between the extension lines and the body (the S5 hook), and `restoreReservedWords`
  runs over it; nothing about the header changed.
- `TerrainVertexFormatRequirements` (133 to 228 lines): no ANTLR or TauMC import any more. `countIdentifiers` is a
  library-free scan (below); `analyze` keeps its contract.
- `TransformCorpusReplayTest`: the `error` stage is split into `error-succeeded` and `error-threw`, and `*` matches
  neither (S5 leftover); an `accepted.txt` entry whose reason starts with `S7 pending` is verified, not trusted
  (below); the summary prints `replay: S7 pending stages verified as adaptive shadow bounds only: N`.
- `accepted.txt` (24 to 106 lines): the format comment (error stages, `*`, the S7 check); Step 5's comment says six
  mini-corpus cases (S5 leftover); `transform-grouped-330-undeclared` is `error-succeeded`; the Step 6 entries.
- `ShaderAst.leadingExtensionCount`: a line ends at CRLF, LF or a lone CR (S5 leftover; before, only LF ended one, so
  CRLF worked and a CR-only source was one line). Both lexers end a directive only at LF (`NEWLINE: '\r'? '\n'`), so a
  source with lone-CR directives does not parse in glsl-transformer (`ShaderAstLineEndTest`); the count then never
  matters. Javadoc says so.
- `AstShaderTransformerTest` (8 to 13 tests): `unportedKindsThrow` uses DH_TERRAIN; new `theMultiTexCoord3Case`,
  `mcEntityTypes`, `mcMidTexCoordTypes`, `geometryStages`, `attributeInputAvailability` (below).
- `transformer/EntityPatcher`: javadoc (its caller exists now).
- `scripts/glsl-corpus/capture.sh`: `GLSL_ENGINE=taumc|douira` (default `taumc`, unchanged behaviour). Another engine
  records into `run/transform-corpus-<engine>/<name>/`, logs to `run/corpus-<name>-<engine>.out`, adds
  `demonica.glsl.engine=<engine>` to the properties and copies the frames to `run/engine-screenshots/<engine>/`. The
  script as it was deletes `run/transform-corpus/<name>/` first, so running it for the new engine would have replaced
  the TauMC corpus the replay compares against.

Outside git: `run/s6-*.out`, the new engine's in-game corpora `run/transform-corpus-douira/{bsl,complementary,vanilla}/`
(recorded on `e2278733` plus this step's uncommitted work, `gitDirty=true`), `run/corpus-<pack>-douira.out`,
`run/engine-screenshots/{douira,taumc-noise}/`, the scratch corpora `run/s6-record/`, `run/s6-tamper/`,
`run/s6-tamper2/`.

### The `Type` mapping (handoff)

`findType` gives `ShaderAst.DeclaredType` (null where TauMC gave 0); the switches use `DeclaredType.is(Type)`:

| Declared type | TauMC token | `Type` | `replaceMCEntity` | `replaceMidTexCoord` |
|---|---|---|---|---|
| none | 0 | null | replace only, return | replace only, return |
| `bool` | `BOOL` | `BOOL` | replace only, return | replace only, return |
| `float` | `FLOAT` | `FLOAT32` | `float iris_Entity = float(...)` | `float iris_MidTex = (...).x` |
| `vec2`, `vec3`, `vec4` | `VEC2..4` | `F32VEC2..4` | `vecN iris_Entity = ...` | `vecN iris_MidTex = ...` |
| `uint` | `UINT` | `UINT32` | `uint iris_Entity = ...` | falls through: `in vec2 mc_midTexCoord;` only |
| `int`, `ivec2..4` | `INT`, `IVEC2..4` | `INT32`, `I32VEC2..4` | `int`/`ivecN iris_Entity = ...` | falls through, as `uint` |
| anything else | | | throws `IllegalStateException` | falls through, as `uint` |

The injected strings are TauMC's, `>>` and `&` included; the printer keeps them (the corpus's 12 CELERITAS_TERRAIN
vertex stages replay identically, and `mcEntityTypes` compares all eleven declarations with TauMC; *(correction,
2026-09-29, Step 7b: ten declared types, `float`, `vec2` to `vec4`, `int`, `ivec2` to `ivec4`, `uint` and `bool`, plus
the undeclared case, compared with TauMC, and `mat2`, which throws in both)*). The corpora
declare only `vec3`/`vec4` `mc_Entity` and `vec2`/`vec4` `mc_midTexCoord`; the unit tests cover the rest.
`MID_TEX_SCALE` is concatenated as Java prints it, `3.0517578E-5`, as before.

### `TerrainVertexFormatRequirements` without ANTLR

`countIdentifiers(source)` counts, per attribute, the identifier tokens that spell its name, in one pass: block and
line comments skipped (a backslash continues a line comment, as TauMC's `LINE_COMMENT`), a preprocessor line skipped
from `#` to its end (backslash continuations included), numbers read with their suffixes so `1.0f` or `0x1Fu` yield no
identifier, identifiers `[A-Za-z_][A-Za-z0-9_]*`. What TauMC's lexer reported as an error throws, and `analyze` keeps
the complete format, as before (I read `LexerErrorCounter`'s use: any error made `isReferenced` throw
`IllegalArgumentException`, which `analyze` catches): a character no GLSL token starts with outside comments and
directive lines (`@`, `$`, backquote, a quote, a backslash not before a line break, a form feed, non-ASCII) and an
unknown directive name (`#include`). String literals: GLSL has none in code (TauMC's lexer reported a quote there as
an error, and so does the scan); on directive lines (`#error "..."`) they are skipped with the line.

Two differences from the lexer, both toward the complete format, neither reachable from a transformed source: an
unterminated block comment is an error here (the lexer read the rest as code), and text inside `#if`/`#ifdef` blocks
is scanned as code (TauMC's lexer read it as one `PROGRAM_TEXT` token, so an attribute read only there did not count;
the Celeritas header is the only conditional text in a transformed source and names none of the five attributes).
Not validated, as TauMC's lexer did: the contents of `#pragma`, `#extension`, `#version` lines. Test
`differencesTowardTheCompleteFormat` shows both differences against the oracle.

### The replay's error stages (S5 leftover)

`error-succeeded` accepts a case recorded as a TauMC error whose replay transforms it; `error-threw` one whose replay
throws something else. An entry accepts only the outcome it names, so a replay that starts throwing (a
`SyntaxException`, say) is not accepted by an entry written for a success; `*` accepts output stages only. The old
`error` stage is now an unknown stage and fails the test (`acceptedEntriesNeedAReasonAndAKnownStage`).

### "S7 pending" entries are verified

The orchestrator asked that the S7-pending check be scripted over every case. It is part of the replayer: for a stage
accepted by an entry whose reason starts with `S7 pending`, `verifyS7Pending` reruns the TauMC engine on the case twice
(same capability, hoisting and instrumentation): as recorded, and with the PCF helper names `texture2DShadow2x2` and
`SampleFilteredShadow` suffixed `_s7pendingcheck` in every stage's input, so `AdaptiveShadowBoundsTransformer` (which
matches the names exactly) finds no candidate, while its runtime-stats pre-check (`mayInjectRuntimeStats`, a substring
test) and so the version hoisting stay the same; the suffix is removed from the output. The first run must reproduce the
recorded output, the second must equal the replay's and differ from the recorded one (all as `GlslTokens`). Then the
recorded-against-replay difference is exactly what the rewrite changes, and nothing else. Otherwise the stage fails,
with the reason.

Both failure paths were run against the thing they guard against, on a one-case copy of `bsl/00020-ATTRIBUTES-825c8f36`:
a recording with one literal changed outside the helper (`* 0.5 + 0.5` to `* 0.25 + 0.5`; `run/s6-tamper-recording.out`:
`FAILING ... (accepted as 'S7 pending (adaptive shadow bounds, Step 7)', but the TauMC engine rerun does not reproduce
the recorded output)`), and the new engine changed temporarily to inject an extra uniform into fragment stages
(`run/s6-tamper-engine.out`: `... but it differs beyond adaptive shadow bounds (TauMC without the rewrite (-) against
douira (+))`). The engine file was restored from a copy and compared with `cmp`; `git diff` showed nothing for it.

### The TauMC by-text cache case

The orchestrator asked for a mini-corpus case with the one production shape of S3 remark 3: CELERITAS_TERRAIN's
`patchMultiTexCoord3` followed by `replaceMidTexCoord` on a vertex shader that declares `gl_MultiTexCoord3` (and not
`mc_midTexCoord`). `celeritas-terrain-multitexcoord3` declares `attribute vec4 gl_MultiTexCoord3;` and reads it twice.
Recording its TauMC output (`-PglslReplayEngine=taumc -PglslReplayRecord=true` on a scratch copy,
`run/s6-record.out`) showed that **TauMC throws** on that shape:
`java.lang.IndexOutOfBoundsException: Index: -1, Size: 30` from `Transformer.injectVariable` in `replaceMidTexCoord`
(`ShaderTransformer.java:326`, its final `injectVariable("in vec2 mc_midTexCoord;")`). A probe that ran TauMC's verbs
in the engine's order and read the private `variable` field after each (`run/s6-probe2.out`; the scratch test was
deleted) found why: TauMC moves its variable anchor to each declaration it injects (after `patchMultiTexCoord3` the
anchor is the injected `attribute vec4 mc_midTexCoord;`), and `removeVariable("mc_midTexCoord")` removes the last
matching declaration in its cache order, which is that injected one; the next `injectVariable` finds no anchor. The
same class of failure as `transform-grouped-330-undeclared` (S5). Before the throw, TauMC's `replaceExpression` had
missed the renamed references, as S3 remark 3 predicted: after it, the probe's tree still has
`midcoord = ( iris_TextureMatrix * mc_midTexCoord ) . xy ;`.

So the case is recorded as `outcome=error` with that message (hand-written, as `transform-grouped-330-undeclared`). The
new engine transforms it: the references become `iris_MidTex` (the verb's documented semantics, S3's named deviation),
the removed anchor is fixed again (S3/S4), and it declares `vec4 iris_MidTex = vec4(mc_midTexCoord.xy * 3.0517578E-5,
0.0, 1.0);`. **Classification: TauMC quirk (a throw after a stale-cache miss), not a port error; the new engine applies
the verbs correctly.** Its output is still not a valid program: it declares `mc_midTexCoord` twice, `in vec2` (injected
by `replaceMidTexCoord`) and `in vec4` (the renamed `gl_MultiTexCoord3`), because `removeVariable` removed the other of
the two declarations. That is the transformer logic both engines share (Open questions 1), not the verbs. In game the
program fails either way: under TauMC the transform throws, under the new engine the driver rejects a redeclaration.
Accepted as `error-succeeded` with that reason; `AstShaderTransformerTest.theMultiTexCoord3Case` asserts TauMC's
throw and message, the stale-cache miss on TauMC's verbs, the new engine's replaced references and its two
declarations. No recorded input uses `gl_MultiTexCoord3` at all (grep over every `in.*.glsl`: 0 files).

### New engine tests beyond the corpus

`mcEntityTypes` (eleven declarations, none, and `mat2`, which throws in both; *correction, 2026-09-29, Step 7b: ten
declared types, not eleven*), `mcMidTexCoordTypes` (six and none),
`geometryStages` (CELERITAS_TERRAIN and ATTRIBUTES with a geometry stage; no corpus has one: Celeritas's geometry branch
replaces `toClipSpace3(...)` with `vertex`, and the vertex stage projects a displaced `worldpos`), and
`attributeInputAvailability` (all eight texture/lightmap/color combinations; the corpora have five). Each compares the
two engines stage by stage as `GlslTokens`.

## Commands run and their outcomes

Every Gradle run one at a time, with `--rerun`; test counts from `build/test-results/test/*.xml`. Dev clients ran one
at a time in the background under `timeout`, with no Gradle build meanwhile.

The brief's Verify 1 on the final code (`run/s6-verify1.out`):
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE,ATTRIBUTES,CELERITAS_TERRAIN --rerun 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=douira corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus cases=387 identical=336 (byte-identical 0) accepted=51 failing=0 unsupported=0 recorded=0 filtered=37
    replay:   ATTRIBUTES {IDENTICAL=290, ACCEPTED=49}
    replay:   CELERITAS_TERRAIN {IDENTICAL=10, ACCEPTED=2}
    replay:   COMPOSITE {IDENTICAL=36}
    replay: S7 pending stages verified as adaptive shadow bounds only: 51
    replay: transformMs engine=douira total=8490.5 ATTRIBUTES=7447.6/339 CELERITAS_TERRAIN=353.3/12 COMPOSITE=689.7/36
BUILD SUCCESSFUL in 17s
```
Before the entries existed (`run/s6-replay-packs-1.out`): `cases=351 identical=300 ... failing=51`, all 51 fragment
stages of BSL cases. With the concurrent pass (`run/s6-replay-packs-2.out`, before the S7 check also required the
recording to reproduce; engine code unchanged since): same counts and `replay: concurrent engine=douira threads=8
cases=387 groups=2 sequentialMs=7663.1 concurrentWallMs=2265.3 concurrentCallMs=18001.2 differing=0`.

The mini-corpus on the new engine (`run/s6-replay-mini-final.out`, `run/s6-replay-mini-final-all.out`):
```
    replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=12 identical=8 (byte-identical 0) accepted=4 failing=0 unsupported=0 recorded=0 filtered=6
    replay:   ATTRIBUTES {IDENTICAL=1, ACCEPTED=2}
    replay:   CELERITAS_TERRAIN {IDENTICAL=1, ACCEPTED=1}
    replay:   COMPOSITE {IDENTICAL=5, ACCEPTED=1}
    replay:   COMPUTE {IDENTICAL=1}
    replay: S7 pending stages verified as adaptive shadow bounds only: 2
    replay: concurrent engine=douira threads=8 cases=10 groups=2 sequentialMs=17.9 concurrentWallMs=11.9 concurrentCallMs=78.0 differing=0
BUILD SUCCESSFUL in 2s
    (all kinds) cases=18 identical=8 (byte-identical 0) accepted=4 failing=0 unsupported=6
    replay:   unsupported 4x: compat on douira: CompatShaderTransformer has no engine switch yet
    replay:   unsupported 1x: glsl-transformer engine: DH_GENERIC not ported yet
    replay:   unsupported 1x: glsl-transformer engine: DH_TERRAIN not ported yet
```

The TauMC engine on its own recordings (`run/s6-replay-packs-taumc.out`, `run/s6-replay-mini-taumc.out`): packs
`cases=424 identical=424 (byte-identical 424) accepted=0 failing=0`; mini-corpus `cases=18 identical=18
(byte-identical 18)`, the new case "failed as recorded".

The brief's Verify 2 (`run/s6-verify2.out`): `BUILD SUCCESSFUL in 2s`; AdaptiveShadowBoundsTransformerTest 9,
AstShaderTransformerTest 13, CeleritasTransformerTest 3, CompatibilityTransformerCaveSkyholeTest 1,
CompatibilityTransformerTest 6, GlslCorpusParseSurveyTest 1 (skipped), GlslTokensTest 10, GlslTransformerSpikeTest 3,
ShaderAstLineEndTest 4, ShaderAstParityTest 400 (1 skipped), TerrainVertexFormatRequirementsTest 4,
TransformCorpusReplayTest 2 (1 skipped), TransformPatcherCacheTest 5, TransformPatcherTest 3: 464 tests, 0 failures,
0 errors, 3 skipped (23:33:26 to 23:33:27 UTC). S5's 451 plus 5 orchestrator tests, 4 line-end tests and the 4
`TerrainVertexFormatRequirementsTest` tests the brief adds to the filter.

The brief's Verify 3, for each pack (the dev runs below; the grep on each log):
```
== bsl            0    Dev shader pack: BSL_v10.1.8.zip (loaded: true)
                       Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224
                       Dev stats: 119 fps; terrain C: 432/3136 D: 8 A: 5; shadow sections 600
                       Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 225
== complementary  0    Dev shader pack: ComplementaryReimagined_r5.9.3.zip (loaded: true)
                       Dev stats: 120 fps; terrain C: 179/3600 D: 8 A: 4; shadow sections 203
== vanilla        0    Dev shader pack: I Like Vanilla v1.4.4.zip (loaded: true)
                       Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 211
```
(the `0` is `grep -cE 'Shader compilation failed|Failed to compile|UnsupportedOperation'`). BSL's and I Like Vanilla's
stats equal S2's; Complementary's chunk counts differ from S2's (166/3600, 190 shadow sections), which is how far
loading had got when `stats` ran.

Dev runs: `GLSL_ENGINE=douira scripts/glsl-corpus/capture.sh <pack>` for bsl, complementary and vanilla, which runs
`./gradlew runClient -PdevScript=@<abs>/scripts/glsl-corpus/<pack>.txt
-PdevProps=demonica.glsl.corpus=<abs>/run/transform-corpus-douira/<pack>,demonica.glsmPerfDebug=true,demonica.glsl.engine=douira`,
default OpenGL profile, as the S2 baselines (which had the same properties but the engine):

| Run | Result | Steps | Cases recorded | Engine line |
|---|---|---|---|---|
| bsl | exit 0, `BUILD SUCCESSFUL in 55s` | 38 (S2: 38) | 175 (S2: 175; same patch and input hash set) | `[TransformPatcher] GLSL transform engine: douira (demonica.glsl.engine)` |
| complementary | exit 0, 37 s | 24 (24) | 120 (120) | same |
| vanilla | exit 0, 33 s | 24 (24) | 105 (105) | same |

In each log: 19 "n of n injectors found their targets" lines and the `[DemonicaQuarantine]` gate line; 0
`Shader compilation failed`, `Failed to compile`, `UnsupportedOperation`, `SyntaxException`, `not ported yet`; every
recorded case `outcome=ok` (the Iris cases `engine=douira`, the five compat cases `engine=taumc`). `Exception` lines:
bsl 7, complementary 9, vanilla 8, all the known ones (six "Mixin config does not reside in a jar file", the
narrator's "No null terminator found", Complementary's `Unknown variable: BIOME_SULFUR_CAVES` and
`Unknown variable: endFlashIntensity`, I Like Vanilla's `Variable shadows build in uniform: pi`). The set of distinct
WARN, ERROR and exception lines (timestamps, addresses and times normalized) is the same as in the pack's S2 log, in all
three: `comm` of the two sets printed nothing either way. Transformed kinds (`Transformed shader for`): bsl 151
ATTRIBUTES, 8 CELERITAS_TERRAIN, 13 COMPOSITE; complementary 102, 4, 10; vanilla 92, 4, 12. No DH program ran in these
scenes, so nothing threw "not ported yet".

**Class loading.** This is the first time glsl-transformer loads in the game. In the three logs,
`grep -cE 'ClassNotFoundException|NoClassDefFoundError|LinkageError'` gives 0, so none mentions `io.github.douira` or
`org.antlr`.

A fourth client, for the noise floor: the vanilla script on the TauMC engine with `-PdevProps=demonica.glsmPerfDebug=true`
and no recorder (`run/s6-noise-vanilla-taumc.out`: engine `taumc`, `I Like Vanilla v1.4.4.zip (loaded: true)`, 0
compile failures, `BUILD SUCCESSFUL in 30s`; frames copied to `run/engine-screenshots/taumc-noise/`).

Other runs:
- `TerrainVertexFormatScanParityTest` corpus mode (`run/s6-tvfr-transform-corpus.out`,
  `run/s6-tvfr-transform-corpus-douira.out`, `run/s6-tvfr-mini.out`): `vertexOutputs=387 differing=0` (TauMC
  recordings), `vertexOutputs=385 differing=0` (the new engine's in-game recordings; the two compat-directory warm-ups
  are not in it), mini-corpus `11 differing=0`. CELERITAS_TERRAIN vertex outputs requiring
  `[MC_ENTITY, MID_TEX_COORD, TANGENT, MID_BLOCK, NORMAL]`: TauMC `[12, 12, 4, 1, 9]` of 12, new engine
  `[12, 12, 4, 1, 9]` of 12: the same terrain vertex format.
- `ShaderAstParityTest` with the corpus mode after the `ShaderAst` change (`run/s6-corpus-parity-packs.out`):
  `inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=29`; 400 tests, 0 failures.
- `TransformPatcherTest` and `TransformPatcherCacheTest` with `-PglslEngine=douira`
  (`run/s6-transformpatcher-douira.out`): cache test 5 tests, 0 failures; `TransformPatcherTest` 3 tests, 2 failed, both
  on raw-string assertions only: `terrainVertexGlColorStaysOnCeleritasVertexColor` (it threw
  `UnsupportedOperationException` at S5) looks for `color = _vert_color ;`, the output has `color = _vert_color;`;
  `compositeVertexLegacyGlColorIsRewritten` as at S5 (`color = iris_FrontColor;`). Step 8 ports both to `GlslTokens`.
- Full build, the step's one `check` run (`run/s6-build.out`, on the code committed right after it as `09e53d27`):
  `./gradlew build` gave `BUILD SUCCESSFUL in 11s`; `:test` executed 132 classes, 1,004 tests, 0 failures, 0 errors, 5
  skipped (23:33:51 to 23:33:54 UTC; S5: 130 classes, 972 tests, 4 skipped; the difference is the two new classes, 32
  tests, and the parity test's corpus mode skipped). `verifyCeleritasPin`, `verifyDiagnosticsJar`,
  `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath`, `verifyS8tnlibPin`
  ran. `build/libs/Demonica-0.5.0-SNAPSHOT.jar` holds 29 `transformer/*.class` entries (S5: 26; now also
  `AttributeTransformer`, `CeleritasTransformer`, `CeleritasTransformer$1`).
- After the commit, one `accepted.txt` comment was corrected (it said the 103 identical helper inputs were
  Complementary's and I Like Vanilla's; they are 51 BSL and 52 Complementary ones). Comment lines only; the replays
  that read the file ran again on it: Verify 1 (`run/s6-verify1-final.out`) `cases=387 identical=336 accepted=51
  failing=0`, 51 verified; mini-corpus (`run/s6-replay-mini-final2.out`) `cases=12 identical=8 accepted=4 failing=0`,
  2 verified; TransformCorpusReplayTest 2 tests, 0 failures, 0 skipped (23:38:04 UTC). No second `check` run.
- Skipped: the brief's literal Verify 3 command writing `run/s6-bsl.out` (the capture script ran the same script, see
  Deviations 2); a `check` run after the comment correction (above).

## Measurements

### Screenshots (handoff)

rgb24 through ffmpeg into numpy, 1200 x 720; "pixels" is the share with a channel differing by more than 16/255. The
new engine's frames (`run/engine-screenshots/douira/`) against the S2 baselines (`run/baseline-screenshots/`):

| Frame | Mean abs | Pixels | Max |
|---|---|---|---|
| bsl no pack | 0.01 | 0.01 % | 89 |
| **bsl pack** | 0.55 | 0.11 % | 120 |
| bsl Nether | 0.70 | 2.26 % | 136 |
| bsl back in the overworld | 0.73 | 0.34 % | 116 |
| complementary no pack | 0.11 | 0.01 % | 204 |
| **complementary pack** | 0.63 | 0.02 % | 54 |
| vanilla no pack | 0.01 | 0.02 % | 94 |
| **vanilla pack** | 1.43 | 1.04 % | 173 |

Noise floor, TauMC against TauMC at the same camera:

| Pair | Mean abs | Pixels | Max |
|---|---|---|---|
| bsl pack: S1 `s1-base-1-bsl` against S2 baseline | 0.55 | 0.15 % | 66 |
| bsl pack: S1 `s1-base-1-bsl` against S1 `s1-taumc-1-bsl` | 0.85 | 0.55 % | 81 |
| bsl pack: S1 `s1-taumc-1-bsl` against S2 baseline | 0.90 | 0.67 % | 93 |
| vanilla pack: S2 baseline against this step's TauMC run | 1.35 | 0.97 % | 152 |
| vanilla pack: this step's TauMC run against this step's new-engine run | 1.07 | 0.47 % | 234 |
| vanilla no pack: S2 against this step's TauMC run | 0.01 | 0.02 % | 94 |

Every pack frame of the new engine is within the TauMC noise of its pack (BSL 0.11 % against 0.15 to 0.67 %; I Like
Vanilla 1.04 % against 0.97 %, and 0.47 % against a TauMC frame of the same session; Complementary 0.02 %, lower than
any measured floor; no second TauMC Complementary frame exists). I looked at the difference masks: I Like Vanilla's
differing pixels lie on edges everywhere (clouds, trees, block outlines, flowers), the pattern of anti-aliasing jitter
and waving plants, not of a shading change; the Nether's lie on lava (the pack's lava pool and the tops of lava
blocks), an animated texture. There is no Nether noise pair. Frames looked at: the new engine's BSL and Complementary
pack frames render normally (BSL's grey-white water as before, S1 open question 3). Pairs that are not a noise floor:
S2's `corpus-bsl-1-pack` against `corpus-bsl-3-overworld` (same run, same camera, after the Nether round trip) differ
by 31.55 %, so the return frame is not a repeat of the first.

No program compiled on TauMC and failed on the new engine: no run logged a compilation failure on either engine.

### Transform time

| | TauMC | new engine |
|---|---|---|
| Replay engine time, pack ATTRIBUTES (339 cases) | 9,886.0 ms (`run/s6-replay-packs-taumc.out`) | 7,447.6 ms (Verify 1) |
| Replay engine time, pack CELERITAS_TERRAIN (12) | 454.1 ms | 353.3 ms |
| In game, sum of `transformMs`, bsl | 4,734.4 ms (S2, 172 lines) | 5,247.7 ms (172 lines) |
| In game, complementary | 13,151.2 ms (116) | 13,742.0 ms (116) |
| In game, vanilla | 5,703.3 ms (S2, 109); 6,025.3 ms (this step's TauMC run, 109) | 7,644.1 ms (108) |

The new engine does less work (no adaptive shadow bounds until Step 7) and is faster alone in the replay; in game it
is 4 to 34 % slower in these single runs (the two TauMC vanilla runs differ by 6 % among themselves). The in-game sum
adds calls that run on several `Shader-Transform-*` threads at once, and `BUILD_LOCK` serializes the new engine's
parses (S5 open question 4). Step 8 measures load times; these numbers are one run each.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 290 pack ATTRIBUTES and 10 CELERITAS_TERRAIN cases | all | identical (tokens) | none |
| 36 pack COMPOSITE cases | all | identical (tokens) | none |
| 49 BSL ATTRIBUTES cases, `bsl/00102-CELERITAS_TERRAIN-4a7ab12b`, `bsl/00103-CELERITAS_TERRAIN-2eb1d79a` | fragment | S7 pending: TauMC's adaptive-shadow-bounds rewrite (bounds guard in `SampleFilteredShadow`/`texture2DShadow2x2`, the stats counters and their SSBO; all 51 recorded instrumented), verified by the replayer as the whole difference | accepted, one entry per case, `S7 pending (adaptive shadow bounds, Step 7)` |
| the other 103 pack inputs that name a PCF helper (51 BSL, 52 Complementary) | fragment | identical: TauMC's rewrite left them alone (its recording-run log: BSL only `injected=1` (51) and `inspected=0` lines; Complementary 52 × `inspected=1 injected=0 unsupported=1`) | none |
| mini-corpus `shadow-bounds`, `shadow-bounds-instrumented` (ATTRIBUTES) | fragment | S7 pending, verified | accepted |
| mini-corpus `attributes`, `celeritas-terrain`, and the six COMPOSITE/COMPUTE cases other than `transform-grouped-330-undeclared` | all | identical | none |
| mini-corpus `celeritas-terrain-multitexcoord3` (new) | error-succeeded | TauMC threw (removed injection anchor, after its stale by-text cache missed the renamed references); the new engine applies the verbs correctly; the output's duplicate `mc_midTexCoord` declaration comes from the shared transformer logic | accepted: `old engine threw (removed injection anchor) after missing the renamed references (stale by-text cache); the new engine transforms it, checked by AstShaderTransformerTest`; Open questions 1 |
| mini-corpus `transform-grouped-330-undeclared` | error-succeeded | as at S5 | entry's stage renamed from `error` |
| DH_TERRAIN, DH_GENERIC, COMPAT | all | unsupported (not ported) | Steps 7, 10 |

No formatting or ordering diff reached `GlslTokens` on any ATTRIBUTES or CELERITAS_TERRAIN stage.

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1" (the
   orchestrator's rule).
2. The dev runs used `GLSL_ENGINE=douira scripts/glsl-corpus/capture.sh <pack>`, not the literal Verify 3 command: the
   same script and profile, plus `demonica.glsmPerfDebug=true` and the recorder, the properties the S2 baselines ran
   with (the orchestrator allowed either). So the logs are `run/corpus-<pack>-douira.out`, not `run/s6-bsl.out`. The
   brief's greps were run on them. `capture.sh` needed the engine switch because it deletes the corpus directory it
   records into.
3. One extra TauMC client (vanilla) for a noise floor, as the orchestrator suggested.
4. `TerrainVertexFormatRequirements`: the scan is a small tokenizer rather than a regex over stripped text (it has to
   find the lexer errors anyway, and numbers must not yield identifiers); the two named differences toward the
   complete format; string literals in code are lexer errors, as they were, rather than stripped.
5. `replaceMCEntity`'s exception for an unsupported type names the type's keyword (`mat2`); TauMC's named its lexer
   token number.
6. Beyond the brief: the replay's S7 check, the error-stage split, the line-end fix, `TerrainVertexFormatScanParityTest`
   (TauMC as oracle; Step 11 deletes it), `ShaderAstLineEndTest`, five orchestrator tests, the mini-corpus case, and
   `capture.sh`'s engine switch.
7. The by-text cache case records a TauMC error, not a TauMC output: TauMC throws on that shape.
8. `ShaderTransformer.computeCeleritasHeader()` stays where S5 left it (package-private in the old engine); the brief
   does not ask to move it. Step 11 must.

## Open questions

1. `gl_MultiTexCoord3`. Both `patchMultiTexCoord3` (CELERITAS_TERRAIN) and its ATTRIBUTES twin in
   `AttributeTransformer` test `hasVariable`, which means "declared" (S3). A pack that reads the built-in without
   declaring it (the normal case) is not patched, so `gl_MultiTexCoord3` stays in a core-profile program, which does
   not have it. A pack that declares it gets a second `mc_midTexCoord` declaration: ATTRIBUTES on both engines (the
   renamed one and the injected `in vec4`), CELERITAS_TERRAIN on the new engine (the renamed `in vec4` and the
   injected `in vec2`), while TauMC throws. Iris 26.1 tests `identifierIndex.has` (any use) and injects the
   declaration only then. No recorded input uses `gl_MultiTexCoord3`. Port Iris's shape after Step 8?
2. The in-game `transformMs` sums of the new engine are 4 to 34 % above TauMC's in single runs, while the replay alone
   is faster. Is this worth splitting the ANTLR parse out of `BUILD_LOCK` (S5 open question 4) once Step 8 has
   measured real load times?
3. S5's open questions 1 to 3, 5 and 6 stand.

## Notes for the next step

- **S7 pending entries.** `accepted.txt` has 53 (51 pack, 2 mini-corpus), each `<case> | fragment | S7 pending
  (adaptive shadow bounds, Step 7)`. The replayer verifies them (`verifyS7Pending`): the TauMC engine rerun with
  `texture2DShadow2x2`/`SampleFilteredShadow` renamed must give the replay's output. When Step 7 ports
  `AdaptiveShadowBoundsTransformer`, those stages should become identical; remove all 53 entries
  (`grep -n "S7 pending (" accepted.txt`) and replay, because the replayer does not report an entry that no longer
  matches any diff. `verifyS7Pending` and `S7_HELPER_SUFFIX` can go with them.
- The replay's accepted stages are now `vertex, geometry, tess_control, tess_eval, fragment, compute, compat,
  error-succeeded, error-threw, *`; `*` does not accept an error outcome.
- ATTRIBUTES and CELERITAS_TERRAIN go through `CommonTransformer.transform`, whose `TODO(S7)` is the one place the
  rewrite belongs. `AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats` and `ShaderTransformer.computeCeleritasHeader`
  are still old-engine code the new one calls.
- The new engine now loads packs: `GLSL_ENGINE=douira scripts/glsl-corpus/capture.sh <pack>` records its in-game
  corpus into `run/transform-corpus-douira/<pack>/` (log `run/corpus-<pack>-douira.out`, frames
  `run/engine-screenshots/douira/`), without touching the TauMC corpus. The three packs' scenes run no DH program;
  Step 7 needs the mini-corpus (and `-PwithCompatMods` with Distant Horizons, if an end-to-end DH run is wanted).
- `TransformPatcherTest` on the new engine fails only its two raw-string assertions (Step 8).
- `TerrainVertexFormatRequirements` no longer imports ANTLR or TauMC (one fewer file for Step 11's inventory).
- The mini-corpus has 18 cases; `celeritas-terrain-multitexcoord3` is a recorded TauMC error.
