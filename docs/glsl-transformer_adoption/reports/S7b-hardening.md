# S7b: pre-flip hardening (added by the orchestrator)

A step the orchestrator added between Step 7 and Step 8 of [the adoption plan](../ADOPTION_PLAN.md); it has no brief in
the plan. Its brief: before Step 8 makes the new engine the default, close the gaps the verifiers of Steps 5 to 7 found
where the new engine is slower or fails where TauMC did not. Branch `feat/glsl-transformer`, from `874a7137` (S1 to S7
done), on 2026-09-29 (UTC; the local date was 2026-09-28). Times below are UTC unless a log line says otherwise (the
dev-client logs print local time, UTC-4).

## Status

**Done.** Every item under "Done when" holds.

| Done when | Evidence |
|---|---|
| In-game timing table; the new engine's median per transform within about 1.3x of TauMC's on both packs | Measurements, table 1: two runs per engine and pack, same session, same script. Pooled medians: BSL 20.4 ms (new) against 20.7 ms (TauMC), 0.99x; Complementary Reimagined 67.9 against 69.8 ms, 0.97x. Per run pair 0.87x to 1.29x, inside the run-to-run spread of either engine. Before the fix, in the same session: BSL 41.6 against 19.8 ms (2.1x), Complementary 92.1 against 68.0 ms (1.35x) |
| Each input shape of item 2 gives equivalent working output, or is documented as failing on TauMC too | `#extension all : warn`, an `#extension` in a function body and one before `#version` now give TauMC's program (same tokens; glslangValidator accepts both engines' outputs). `patch` as an identifier hoisted to 420 still throws; TauMC's output there is broken GLSL (`<missing ';'>` in it, glslangValidator "syntax error, unexpected PATCH"). Table under "Item 2" |
| The `gl_MultiTexCoord3` shapes give valid programs | ATTRIBUTES and CELERITAS_TERRAIN, declared and built-in: the new engine's four vertex outputs pass glslangValidator (TauMC's: "redefinition", "undeclared identifier", or a throw). Table under "Item 3" |
| Scanner fixes in, with tests | (a) lone CR, (b) `#error`/`#endif` backslash, (c) comments on strict directives, (d) `#endif` without `#if`, plus four more shapes, matched; (e) and three others documented and tested as remaining differences. `TerrainVertexFormatScanParityTest` 46 tests (23 before), 0 failures; corpus mode `differing=0` on six corpora |
| `capture.sh` fixed | A run whose script writes no frame: `exit=3`, `MISSING frames (not written by this run): corpus-vanilla-0-nopack corpus-vanilla-1-pack`, nothing copied; the old script copied the two stale frames and exited 0. A normal run: `frames 4 of 4` |
| Replays clean on `douira`, byte-identical on `taumc` | Packs `cases=424 identical=387 accepted=0 failing=0 unsupported=37`; mini-corpus `cases=30 identical=17 accepted=9 failing=0 unsupported=4`, `stale=0`; DH `cases=140 identical=118 failing=0 unsupported=22`; the unsupported cases are GLSM's compat cases (Step 10). TauMC: 424/424, 30/30, 140/140 byte-identical |
| Appendix C transform tests and `TransformPatcherCacheTest` green on both engines | 17 classes, 513 tests, 0 failures, 3 skipped (the corpus-gated ones), once with the default engine (log line `GLSL transform engine: taumc`) and once with `-PglslEngine=douira` (`GLSL transform engine: douira`); `TransformPatcherCacheTest` 5 tests and `TransformPatcherTest` 3 tests in both |
| One full build green | `./gradlew build`: `BUILD SUCCESSFUL in 11s`, 133 classes, 1,048 tests, 0 failures, 5 skipped; the seven `verify*` tasks ran |
| Report and STATUS written; committed | this page; `STATUS.md` row "S7b pre-flip hardening (added by the orchestrator)"; commits below |

## Commits

| Commit | Subject |
|---|---|
| `307d84ce` | glsl-transformer: S7b pre-flip hardening |
| `33916dfc` | glsl-transformer: S7b report and status |
| `658bf2cf` | glsl-transformer: S7b fix extension line comments (verification follow-up) |
| the commit that adds "Verification follow-up 2" | glsl-transformer: S7b fix continued line comments on extension lines |

## What changed

Main code:
- `glsm/.../transformer/ShaderAst.java` (1,682 to 1,940 lines).
  - `parse`: the ANTLR parse of a program runs on a parser of its own (`newProgramParser`: a plain `EnhancedParser`,
    which is what `ParsingCacheStrategy.NONE` builds, with the program's filter and lexer version), outside
    `BUILD_LOCK`; only `ASTBuilder.buildSubtree` holds the lock. `ASTParser.parseTranslationUnit` made the same two
    calls on the shared parser, under the lock. The shared parser (`PARSER`) now parses snippets only, and its filter
    records no type spellings (`DirectiveFilter.forSnippets`).
  - `Timing` (new, public): per thread, the programs' parse time, their AST build time, and the waits for and holds of
    `BUILD_LOCK` (counted in `lockBuild`/`unlockBuild`, two `nanoTime` calls per acquisition; reentrant acquisitions are
    not counted).
  - `ExtensionLines` (new): the `#extension` lines are found in the text (outside comments, where a line's first
    non-blank character is `#` followed by `extension`; backslash continuations joined), blanked in the text that is
    parsed (line breaks kept, so line numbers stay), and formatted `#extension NAME : behavior`. Those of the leading
    directive block (the rule of `leadingExtensionCount`, unchanged) are `extensionDirectives()`; the others are
    dropped and listed in `droppedDirectives()` with the suffix `(after the leading directives)`, as before. The tree
    no longer holds `ExtensionDirective` nodes; one the scan misses (after a comment on its line) is dropped the same
    way. Javadocs of the class, `BUILD_LOCK`, `t`, `tree`, `parse`, `newParser`, `extensionDirectives`, `print`.
- `shader/.../transform/AstShaderTransformer.java` (417 to 440): logs `[AstShaderTransformer] <kind> timing totalMs=..
  parseMs=.. buildMs=.. lockWaitMs=.. lockHeldMs=.. locks=.. contended=..` per transform when
  `-Ddemonica.glsmPerfDebug=true`; `patchMultiTexCoord3` delegates to the new `CommonTransformer.patchMultiTexCoord3`;
  class javadoc (threads, extensions).
- `shader/.../transform/transformer/CommonTransformer.java`: `patchMultiTexCoord3(ast, parameters, declaration)` (new;
  Item 3). `AttributeTransformer.java`: calls it with `in vec4 mc_midTexCoord;`.
- `shader/.../celeritas/vertices/TerrainVertexFormatRequirements.java` (228 to 351): the scan's line comments end at a
  lone CR too; directive lines are read per directive as TauMC's lexer modes read them (`endOfDirective`); a `#` that
  is not first on its line, and an `#endif` without an open block, throw. Javadoc lists every remaining difference
  (Item 4).

Tests and corpus:
- `AstShaderTransformerTest` (15 to 19 tests): `extensionLinesTheGrammarRejects`, `patchAsAnIdentifier`,
  `multiTexCoord3Shapes`, `legacyTextureCallsKeepTheirNames` (Item 6's pin); `theMultiTexCoord3Case` now asserts one
  declaration of `mc_midTexCoord`.
- `TerrainVertexFormatScanParityTest` (23 to 46 tests): ten rows where the scan now agrees with the TauMC lexer, thirteen
  rows of lexer errors that keep the complete format, `remainingDifferences` (was `differencesTowardTheCompleteFormat`);
  the oracle returns null when the TauMC lexer throws (`EmptyStackException`), as `analyze` treated it.
- `TransformCorpusReplayTest`: the stage `threw` (a case recorded with an output whose replay throws; before, such a case
  failed with no way to accept it); the dead `not ported yet` catch is gone (Item 7); javadoc.
- `TransformPatcherTest`: its three raw-string assertions compare as `GlslTokens` (Deviations 2).
- Nine mini-corpus cases (30 now), TauMC outputs recorded with `-PglslReplayEngine=taumc -PglslReplayRecord=true`
  (`run/s7b-record.out`): `composite-extension-all`, `composite-extension-in-function`,
  `composite-extension-before-version`, `composite-patch-identifier`, `composite-patch-hoisted`,
  `composite-legacy-textures`, `attributes-multitexcoord3-declared`, `attributes-multitexcoord3-builtin`,
  `celeritas-terrain-multitexcoord3-builtin`.
- `accepted.txt`: the stage `threw` in the header; the `celeritas-terrain-multitexcoord3` reason updated; six entries
  with a Step 7b block (Residual diffs). Nine entries in all.

Scripts:
- `scripts/glsl-corpus/capture.sh` (Item 5).
- `scripts/glsl-corpus/timing.sh` (new): `timing.sh <pack> taumc|douira <tag>` runs the pack's corpus script with
  `demonica.glsmPerfDebug=true` and the engine, without the recorder, into `run/timing-<pack>-<engine>-<tag>.out`, and
  prints the summary.
- `scripts/glsl-corpus/transform-times.py` (new): per log, transforms, median, p90, sum, first 30 against the rest, and
  the sums of the `[AstShaderTransformer] ... timing` lines.

Docs: `ADOPTION_PLAN.md` section 3.2, one dated note after the `UnsupportedOperationException` sentence (Item 7);
`reports/S06-attributes-terrain.md`, two dated corrections (`mcEntityTypes`: ten declared types plus the undeclared
case, and `mat2`).

Outside git: `run/s7b-*.out`, `run/timing-*.out`, `run/s7b-probe/`, `run/s7b-record/`, `run/s7b-record-douira/`, the
new engine's BSL corpus `run/transform-corpus-s7b-douira/bsl/` (175 cases, recorded on `874a7137` plus this step's
uncommitted work) with its log `run/corpus-bsl-transform-corpus-s7b-douira.out` and frames
`run/engine-screenshots/transform-corpus-s7b-douira/`, and `run/transform-corpus-s7b-capturetest/` (Item 5's test).

### Item 1: where the in-game time went

Instrumented first (`ShaderAst.Timing`), before changing anything. The in-game transform threads: `Iris`'s
`ShaderTransformExecutor` is a `ForkJoinPool` of `max(2, min(12, cores / 2))` threads, eight on this machine, fed a
pack's programs at once. The first in-game run on the committed behaviour (the parse inside the lock, restored for the
measurement by a temporary switch that is not in the commit), BSL on the new engine
(`run/timing-bsl-douira-exp-underlock-1.out`):

```
transforms=170 medianMs=41.6 p90Ms=91.2 sumMs=8136.2 first30MedianMs=82.5 restMedianMs=36.5
timing lines=172 totalMs=8145.3 parseMs=431.4 buildMs=364.8 lockWaitMs=4895.0 lockHeldMs=917.2 locks=10645 contended=3946 medianLockWaitMs=24.42
```

60 % of the transform time was waiting for `BUILD_LOCK`, which was held for only 917 ms in all: 10,645 acquisitions
(about 62 per transform: every verb that parses a snippet takes it), 3,946 of them contended, so the eight threads
queued behind each other's parses. Offline, the same pattern at eight threads (the replay's concurrent pass on the 387
pack cases of the Iris kinds): wall 2,426.0 ms and a call sum of 19,292.0 ms with the parse inside the lock, 1,611.5 and
12,816.2 ms with it outside, TauMC 2,527.5 and 20,089.2 ms (`run/s7b-exp-offline-*.out`).

The other candidates, checked:
- **Warm-up.** The first 30 transforms against the rest, per run: the new engine 49.9/56.4 against 17.4/20.8 ms after
  the fix, TauMC 41.2/39.9 against 17.9/22.0 ms. Both engines pay a cold start, the new one 1.2 to 1.4x TauMC's on the
  first 30 and the same afterwards. `Iris.ShaderTransformExecutor.warmup()` already runs one COMPOSITE and one
  ATTRIBUTES transform of a trivial program at startup on whichever engine is selected, so class loading is done
  before a pack loads; what is left is ANTLR's DFA and the JIT on the pack's own programs. Not changed.
- **Parser construction per call.** `new EnhancedParser()` costs 0.3 µs warm (0.9 µs over the first 2,000;
  `run/s7b-exp-construct.out`), against about 0.5 ms for a program's parse: a fresh parser per program costs nothing
  measurable, so there is no pool or thread-local (and no token stream of an old program stays referenced).
- **The root's prefix index.** `PREFIX_UNORDERED_ED_EXACT` against `EXACT_UNORDERED_ED_EXACT` (nothing uses
  `prefixQueryFlat`): the 82 distinct recorded BSL inputs (77 Iris stage inputs, 5 of GLSM's compat inputs) build in 26.5 to 29.4 ms warm with the prefix index and 26.3 to
  28.6 ms without (`run/s7b-exp-root-*.out`). No difference; kept.
- **Parsing cache.** The recorded BSL corpus has 340 stage inputs and 77 distinct texts, Complementary 230 and 64, so a
  cache of parse trees could skip most program parses. Its upper bound is the parse time itself: 406 to 434 ms (BSL)
  and 963 to 1,218 ms (Complementary) summed over the eight threads after the fix. Not pursued: it needs parse trees
  (or ASTs to clone, under the lock) kept per pack and the channel filter's records replayed, S3's reasons for `NONE`.
- **Recorder and perf debug.** The timing runs have perf debug on for both engines (it is what logs `transformMs`) and
  no recorder. One run on the final code with the recorder (`capture.sh`, `run/corpus-bsl-transform-corpus-s7b-douira.out`)
  had a median of 23.4 ms against 19.6 and 21.3 without; one run, not separated further. S7's 33.4 ms were with the
  recorder and the old lock.

**Fix: the ANTLR parse outside the lock** (S5 open question 4). A program's lexing, channel filtering and parse tree
touch no glsl-transformer build state (the static build-root stack is used only while AST nodes are constructed), and
ANTLR shares its DFA between parser instances thread-safely; the AST build stays under the lock. After it, BSL
(`run/timing-bsl-douira-exp-split-1.out`): `medianMs=22.3 ... lockWaitMs=1484.9 lockHeldMs=360.1 ... contended=3308`.

## Commands run and their outcomes

Every Gradle run one at a time; dev clients one at a time, in the foreground under `timeout`, with no Gradle build
meanwhile. Test counts from `build/test-results/test/*.xml` (Gradle 9 prints no "Tests run" lines). The step has no
Verify list of its own; the commands below check each "Done when" item.

Appendix C's transform tests, default engine (`run/s7b-appc-taumc.out`, 01:24:56 to 01:24:57), and with the new engine
(`run/s7b-appc-douira.out`, 01:24:42 to 01:24:43):
```
$ ./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' --tests 'com.gtnewhorizons.angelica.glsm.CompatShaderTransformerTest' --tests 'com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGeneratorTest' --tests 'net.coderbot.iris.celeritas.vertices.TerrainVertexFormatRequirementsTest' --rerun
BUILD SUCCESSFUL in 2s            classes 17 tests 513 skipped 3 failures 0 errors 0     GLSL transform engine: taumc
$ (the same) -PglslEngine=douira
BUILD SUCCESSFUL in 3s            classes 17 tests 513 skipped 3 failures 0 errors 0     GLSL transform engine: douira
```
`TransformPatcherCacheTest` 5 tests and `TransformPatcherTest` 3 tests, 0 failures, in both. Before `TransformPatcherTest`
was ported, the douira run gave `513 tests completed, 2 failed` (`terrainVertexGlColorStaysOnCeleritasVertexColor`,
`compositeVertexLegacyGlColorIsRewritten`, the raw-string assertions S5 and S6 named).

Replays, final code (`run/s7b-final-replay-*.out`; `douira` with `-PglslReplayThreads=8`):
```
replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37 recorded=0 filtered=0
replay: accepted entries in scope=0 used=0 stale=0
replay: concurrent engine=douira threads=8 cases=387 groups=2 sequentialMs=8257.5 concurrentWallMs=1574.9 concurrentCallMs=12510.7 differing=0
replay: engine=taumc corpus=.../run/transform-corpus cases=424 identical=424 (byte-identical 424) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=30 identical=17 (byte-identical 0) accepted=9 failing=0 unsupported=4 recorded=0 filtered=0
replay: accepted entries in scope=9 used=9 stale=0
replay: concurrent engine=douira threads=8 cases=23 groups=2 sequentialMs=39.6 concurrentWallMs=17.6 concurrentCallMs=118.1 differing=0
replay: engine=taumc corpus=.../src/test/resources/transform-corpus cases=30 identical=30 (byte-identical 30) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
replay: engine=douira corpus=.../run/transform-corpus-dh cases=140 identical=118 (byte-identical 0) accepted=0 failing=0 unsupported=22 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=118 groups=2 sequentialMs=4166.3 concurrentWallMs=823.0 concurrentCallMs=6360.0 differing=0
replay: engine=taumc corpus=.../run/transform-corpus-dh cases=140 identical=140 (byte-identical 140) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
```
All `BUILD SUCCESSFUL`. The unsupported cases are GLSM's compat cases (`compat on douira: CompatShaderTransformer has no
engine switch yet`, Step 10).

`TerrainVertexFormatScanParityTest` corpus mode (`run/s7b-tvfr-*.out`): `vertexOutputs=387 differing=0`
(`run/transform-corpus`), `118 differing=0` (`run/transform-corpus-dh`), `385 differing=0` (`run/transform-corpus-douira`),
`118 differing=0` (`run/transform-corpus-dh-douira`), `170 differing=0` (`run/transform-corpus-s7b-douira`, this step's
in-game recording), `23 differing=0` (mini-corpus). Terrain formats as before: TauMC packs `[12, 12, 4, 1, 9]` of 12.

`ShaderAstParityTest` corpus mode (`run/s7b-shaderast-parity-packs.out`): `inputs=811 distinct=221 parseFailures=0
skipped=0 unexplained=0 seconds=26`; 400 tests, 0 failures.

The step's one full build (`run/s7b-build.out`, 01:27:29 to 01:27:32, on the code committed as `307d84ce`):
`./gradlew build`, `BUILD SUCCESSFUL in 11s`, 133 classes, 1,048 tests, 0 failures, 5 skipped (S7: 1,021; the
difference is 4 in `AstShaderTransformerTest` and 23 in `TerrainVertexFormatScanParityTest`). `verifyCeleritasPin`,
`verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`,
`verifyRunClasspath`, `verifyS8tnlibPin` ran. The jar holds 36 `transformer/*.class` entries (S7: 34; now also
`ShaderAst$ExtensionLines`, `ShaderAst$Timing`).

Dev clients (read the dev-run memory note first): 17. Six investigation runs (`run/timing-*-exp-*.out`, table 2), the eight
timing runs of table 1, two `capture.sh` runs for Item 5 (vanilla with a script that writes no frame, once with the new
script and once with the old one), and one `capture.sh` run of BSL on the new engine with the recorder. Every one:
`exit 0`, the pack `(loaded: true)` where a pack was set, 0 `Shader compilation failed`/`Failed to compile`/
`UnsupportedOperation`, 19 "n of n injectors" lines in the BSL capture. In the BSL capture log, the pattern
`SyntaxException` matched twice: gson's `JsonSyntaxException` under Mojang authlib's
`AuthenticationUnavailableException` (the offline skin lookup), not a transform. Its frames against S7's
(`run/engine-screenshots/`): pack 0.04 % of pixels against S7's second new-engine run and 0.11 % against S7's TauMC run,
Nether 0.48 % and 0.86 %, back in the overworld 0.10 % and 0.35 %: S7's noise floor.

Probes (scratch tests, deleted before the commit): both engines on the Item 2, 3 and 6 shapes (`run/s7b-probe/`), the
TauMC lexer against the scan on 103 directive and line-end shapes (`run/s7b-scan-probe-*.out`). glslangValidator
16.4.0 (`/usr/bin/glslangValidator`) judged the outputs.

## Measurements

### Table 1: in-game transform time, final code

`scripts/glsl-corpus/timing.sh <pack> <engine> final-<run>`: the pack's corpus script (`scripts/glsl-corpus/bsl.txt`:
overworld, Nether, overworld; `complementary.txt`: overworld), `-Ddemonica.glsmPerfDebug=true`, no recorder, default
OpenGL profile, the runs interleaved (TauMC, new, TauMC, new) in one session between 01:16 and 01:21 UTC (the first run started at 21:16:21 local time). Per
`[ShaderTransformCache] ... miss transformMs=` line (a transform on its `Shader-Transform` thread, call to result):

| Engine | Pack | Run | Transforms | Median ms | p90 ms | Sum ms |
|---|---|---|---|---|---|---|
| TauMC | BSL | 1 | 170 | 18.5 | 66.8 | 5,159.0 |
| TauMC | BSL | 2 | 170 | 23.0 | 52.7 | 4,642.9 |
| new | BSL | 1 | 170 | 19.6 | 49.7 | 4,025.4 |
| new | BSL | 2 | 170 | 21.3 | 54.7 | 4,337.9 |
| TauMC | Complementary | 1 | 115 | 72.4 | 225.4 | 11,606.1 |
| TauMC | Complementary | 2 | 115 | 61.6 | 174.8 | 9,436.8 |
| new | Complementary | 1 | 115 | 63.2 | 135.6 | 8,232.5 |
| new | Complementary | 2 | 115 | 79.3 | 146.8 | 9,617.8 |

Pooled over both runs: BSL TauMC median 20.7 ms, p90 61.7; new 20.4, p90 52.2 (0.99x). Complementary TauMC 69.8, p90
197.6; new 67.9, p90 139.7 (0.97x). The new engine's sums are lower in three of the four pairs. The two runs of one
engine and pack differ by up to 25 % in the median (Complementary, new) and 17 % (Complementary, TauMC).

Where the new engine's time goes now (`[AstShaderTransformer] ... timing` sums per run):

| Pack | Run | Transform ms | Parse ms | AST build ms | Lock wait ms | Lock held ms | Locks | Contended |
|---|---|---|---|---|---|---|---|---|
| BSL | 1 | 4,027.5 | 406.3 | 260.6 | 1,350.8 | 343.7 | 10,645 | 2,978 |
| BSL | 2 | 4,352.2 | 433.6 | 279.4 | 1,483.4 | 367.1 | 10,645 | 2,987 |
| Complementary | 1 | 8,260.4 | 963.4 | 487.9 | 1,676.6 | 538.5 | 7,418 | 1,711 |
| Complementary | 2 | 9,657.8 | 1,217.7 | 552.6 | 1,865.2 | 611.3 | 7,418 | 1,825 |

The lock still costs 19 to 34 % of the summed transform time; a thread now waits behind program AST builds (0.8 ms
each on average for BSL, 2.1 ms for Complementary) rather than behind whole parses (Open questions 1).

### Table 2: the investigation runs, same session, earlier

| Engine / variant | Pack | Transforms | Median ms | p90 ms | Sum ms | Lock wait ms | Log |
|---|---|---|---|---|---|---|---|
| new, parse inside the lock (as committed at `874a7137`) | BSL | 170 | 41.6 | 91.2 | 8,136.2 | 4,895.0 | `timing-bsl-douira-exp-underlock-1.out` |
| new, parse outside the lock | BSL | 170 | 22.3 | 48.4 | 4,120.3 | 1,484.9 | `timing-bsl-douira-exp-split-1.out` |
| TauMC | BSL | 170 | 19.8 | 60.0 | 4,602.8 | | `timing-bsl-taumc-exp-1.out` |
| new, parse inside the lock | Complementary | 115 | 92.1 | 205.2 | 12,534.1 | 6,161.5 | `timing-complementary-douira-exp-underlock-1.out` |
| new, parse outside the lock | Complementary | 115 | 70.8 | 167.8 | 9,577.1 | 2,301.4 | `timing-complementary-douira-exp-split-1.out` |
| TauMC | Complementary | 115 | 68.0 | 181.8 | 9,744.0 | | `timing-complementary-taumc-exp-1.out` |

### Replay engine time

Final replays, first pass: packs TauMC 10,950.1 ms, new 8,449.8 ms (ATTRIBUTES 9,597.4 against 7,487.2); DH corpus
6,188.0 against 4,606.0 ms. Eight threads, the 387 pack cases of the Iris kinds: new engine wall 1,574.9 ms (S7:
not measured on all kinds; S6: 2,265.3 ms), call sum 12,510.7 ms.

## Item 2: syntax errors where TauMC recovered

Both engines on each shape (`run/s7b-probe/`, then the mini-corpus cases); glslangValidator on every output. The
"before" column is the probe run before the pre-pass existed (the parse split of Item 1 was already in; it does not
change what parses), on probe sources of the same shapes, so the messages' line numbers are the probe's.

| Shape (mini-corpus case) | TauMC's output | Compiles? | New engine before | New engine now |
|---|---|---|---|---|
| `#extension all : warn` in the leading block, `#extension all : disable` after code (`composite-extension-all`) | header `#extension GL_ARB_gpu_shader5 : enable`, `#extension all : warn`; the late one dropped | yes | `SyntaxException: line 2:11 mismatched input 'all' expecting NR_IDENTIFIER` (its directive mode lexes `all` as `NR_ALL`, the keyword of `#pragma invariant(all)`) | TauMC's program, compiles |
| `#extension` inside a function body (`composite-extension-in-function`) | dropped, body intact | yes | `SyntaxException: line 4:0 mismatched input '#' ...` | TauMC's program, compiles |
| `#extension` before `#version` (`composite-extension-before-version`) | `#version 330 core`, then the extension in the header | yes | `SyntaxException: line 2:1 no viable alternative at input '#version'` | TauMC's program, compiles |
| `float patch` in a 120 shader raised to 330 (`composite-patch-identifier`) | broken: `float patch uniform sampler2D colortex0 ; ... <missing ';'>` (TauMC's lexer reads `patch` as a keyword at every version) | no: "syntax error, unexpected UNIFORM" | the pack's program (glsl-transformer's lexer is version-aware: `patch` is a keyword from 400) | unchanged, compiles |
| `float patch` in a 120 shader hoisted to 420 by `imageLoad` (`composite-patch-hoisted`) | broken, as above | no: "syntax error, unexpected PATCH" | `SyntaxException: line 3:20 no viable alternative at input 'float patch'` | unchanged: throws (`line 5:10` on the mini-corpus case) |

The fix for the three `#extension` shapes is the pre-pass (`ShaderAst.ExtensionLines`): it needs no grammar for the
line, so the name and the version do not matter. For `patch` at 400 and above, TauMC's output would not have compiled
either, so the exception stays: `patch` is a keyword there, and passing it through `renameReservedWords`/
`restoreReservedWords` would restore `patch` into a `#version 420 core` program that a conforming compiler rejects
(glslangValidator's "unexpected PATCH" on TauMC's output is that rejection). Renaming it for good would compile but
changes the program's interface names (a uniform named `patch` would no longer receive its value); not done (Open
questions 4).

## Item 3: `gl_MultiTexCoord3`

Iris 26.1 (`transformer/CommonTransformer.patchMultiTexCoord3`, called from `SodiumTransformer` and
`VanillaTransformer`) tests `root.identifierIndex.has` (any use) for both names and always injects
`attribute vec4 mc_midTexCoord;`. So it patches the built-in, and it too declares `mc_midTexCoord` twice when the
shader declared `gl_MultiTexCoord3`. The new engine's `CommonTransformer.patchMultiTexCoord3` tests any use of
`gl_MultiTexCoord3` (Iris), a declaration of `mc_midTexCoord` (TauMC; a shader that reads `mc_midTexCoord` without
declaring it gets the declaration it lacks), renames, and injects the declaration only if the shader did not declare
`gl_MultiTexCoord3` itself. CELERITAS_TERRAIN keeps its order (the patch, then `replaceMidTexCoord`, which removes the
one declaration and reads the coordinate through `iris_MidTex`, scaled, from Celeritas's `in vec2 mc_midTexCoord`).

| Vertex shader | TauMC | New engine at `874a7137` | New engine now (glslangValidator) |
|---|---|---|---|
| ATTRIBUTES, declares `gl_MultiTexCoord3` (`attributes-multitexcoord3-declared`) | `in vec4 mc_midTexCoord ;` twice: "'mc_midTexCoord' : redefinition" | the same | one `in vec4 mc_midTexCoord;` (OK) |
| ATTRIBUTES, reads the built-in (`attributes-multitexcoord3-builtin`) | `gl_MultiTexCoord3` kept: "undeclared identifier" | the same | `in vec4 mc_midTexCoord;`, `midcoord = mc_midTexCoord.xy;` (OK) |
| CELERITAS_TERRAIN, declares it (`celeritas-terrain-multitexcoord3`) | threw `IndexOutOfBoundsException: Index: -1, Size: 30` | `in vec2` and `in vec4 mc_midTexCoord`: "redefinition" | one `in vec2 mc_midTexCoord;`, `iris_MidTex` (OK) |
| CELERITAS_TERRAIN, reads the built-in (`celeritas-terrain-multitexcoord3-builtin`) | `gl_MultiTexCoord3` kept: "undeclared identifier" | the same | one `in vec2 mc_midTexCoord;`, `iris_MidTex` (OK) |

The declared form is itself not valid GLSL (glslangValidator on the pack's input: "'gl_MultiTexCoord3' : identifiers
starting with "gl_" are reserved"); some drivers accept it. A shader that reads the built-in and declares
`mc_midTexCoord` (the mini-corpus case `attributes`) is left alone by TauMC, Iris 26.1 and the new engine, and keeps a
core-profile-less built-in (Open questions 3). No recorded pack input uses `gl_MultiTexCoord3` (S6).

## Item 4: `TerrainVertexFormatRequirements`' scan

Probed the TauMC lexer (`TerrainVertexFormatScanParityTest.taumc`) against the scan on 103 shapes: each directive with a
trailing backslash, a line comment, a block comment and a lone CR; top-level `#endif`/`#else`/`#elif`; `#line`; a `#`
inside a line; malformed directive contents (`run/s7b-scan-probe-1.out`, `-2.out`). Matched now, each a test row:

| Shape | TauMC lexer | Scan before | Scan now |
|---|---|---|---|
| (a) `// c` ended by a lone CR | counts the next line | swallowed it | counts it |
| (b) `#error foo \` then code; `#endif \` then code | counts the code (no continuation in those modes) | skipped it | counts it |
| `#endif` or `#else` ended by a lone CR | ends the directive there | swallowed the next line | ends there |
| a block comment over two lines on a `#define`/`#if` line | part of the directive | read its second line as code | part of the directive |
| (c) a comment on `#ifdef`, `#ifndef`, `#undef`, `#pragma`, `#extension`, `#version`; a backslash continuing one of them | lexer error (complete format) | counted | throws (complete format) |
| a lone CR ending any other directive line | lexer error | counted, swallowing a line | throws |
| (d) `#endif` without an open block (also one `#endif` too many) | `EmptyStackException` (complete format) | counted | throws |
| a `#` inside a line of code | lexer error | skipped the rest of the line as a directive | throws |

Left as they are, listed in the javadoc and asserted in `remainingDifferences`:
- (e) after `#line`, after an `#else`/`#elif` without an open block, and after `#ifdef_X`, the TauMC lexer counted
  nothing more in the source; the scan goes on. Matching would drop attributes the program reads (TauMC's
  under-count), so the scan's format is larger, never smaller.
- The text inside `#if`/`#ifdef`/`#ifndef` blocks: scanned as code (the lexer read one opaque token). As at S6.
- An unterminated block comment: the scan throws, the lexer counted the rest as code. As at S6.
- `#version abc`, `#version 330 foo`, `#pragma @@`, `#pragma "x"`: lexer errors, the scan counts (it does not check
  those lines' contents).

None of these reaches the scan from a transformed source (its directives are `#version`, the `#extension` lines and the
Celeritas header's, one per line, without comments); the corpus mode is `differing=0` on six corpora.

## Item 5: `capture.sh`

The script reads the frames it expects from the `shot <name>` steps of the pack's script, touches a marker before the
client starts, and after the run copies only `run/client/screenshots/<shot>.png` newer than the marker. A missing or
older frame is named on stderr, `MISSING frames (not written by this run): ...`, nothing stale is copied, and the
script exits 3 if the client exited 0. Run against what it guards: the vanilla corpus script overridden by a script that
writes no frame (`-PdevScript=@<scratch>/noshot.txt`, which Gradle takes over the script's own), while
`run/client/screenshots/corpus-vanilla-{0-nopack,1-pack}.png` from 19:31 local time existed:
- the new script (`run/s7b-capture-missing.out`): `exit=3`, `frames 0 of 2`, `capture vanilla: MISSING frames (not
  written by this run): corpus-vanilla-0-nopack corpus-vanilla-1-pack`, no frame directory created. That is the script
  as committed in `307d84ce`, which made the frame directory only when a frame was fresh; since `658bf2cf`
  (verification follow-up, Remark 2) it runs `mkdir -p` whenever a frame directory is set, removes a missing shot's
  older copy from it, and prints `exit 3 (client 0)`;
- the committed script (a temporary copy of `874a7137`'s, deleted after; `run/s7b-capture-missing-old.out`): `exit=0`
  and both stale frames copied (`cmp`: identical to the 19:31 files);
- a normal BSL run on the new engine (`run/s7b-capture-bsl.out`): `exit=0`, `175 cases`, `frames 4 of 4`.

## Item 6: legacy texture calls under `#version 330 core` (documented, not fixed)

`GlslTransformUtils.TEXTURE_RENAMES` (the Iris pipeline's `renameFunctionCall` map) renames `texture2D`, `texture3D`,
the `Lod`/`Proj`/`Grad` variants, `texelFetch2D/3D`, `textureSize2D`, but not `texture2DRect`, `textureCube`,
`texture1D`, `texture2DArray` (nor `texture1DArray`, `textureCubeArray`). TauMC's grammar lexes those as keywords, so
its output is broken GLSL; the new engine keeps the calls. Under `#version 330 core`, glslangValidator on one call each
(scratch shaders): `texture2DRect`, `textureCube` and `texture1D` make glslangValidator abort on an internal assertion
(`ParseHelper.cpp:3931 nonOpBuiltInCheck`), `texture2DArray` gives "required extension not requested:
GL_EXT_texture_array"; `texture(...)` compiles for all four sampler types. The GLSL 3.30 core profile does not have the
old names. GLSM already renames all six to `texture` before its own parse
(`GlslTransformUtils.renameParseBreakingTextureFunctions`, used by `CompatShaderTransformer` only). The mini-corpus
case `composite-legacy-textures` records TauMC's output (accepted, reason in `accepted.txt`), and
`AstShaderTransformerTest.legacyTextureCallsKeepTheirNames` pins the new engine's statement token by token. Open
questions 2.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 387 pack cases of the Iris kinds, 118 DH-corpus cases | all | identical (tokens) | none |
| mini-corpus: the 14 cases identical at S7, plus `composite-extension-all`, `composite-extension-in-function`, `composite-extension-before-version` | all | identical (tokens) | none |
| `attributes-multitexcoord3-declared` | vertex | intended: one declaration of `mc_midTexCoord` where TauMC had two | accepted (Step 7b entry) |
| `attributes-multitexcoord3-builtin` | vertex | intended: the built-in becomes `mc_midTexCoord`, declared (Iris 26.1) | accepted |
| `celeritas-terrain-multitexcoord3-builtin` | vertex | intended: the built-in is read through `iris_MidTex`; `in vec2 mc_midTexCoord` injected before `in uint mc_Entity` (declaration order follows) | accepted |
| `celeritas-terrain-multitexcoord3` | error-succeeded | TauMC threw; the new engine's output now declares `mc_midTexCoord` once | entry's reason updated |
| `composite-patch-identifier` | fragment | TauMC's output is broken GLSL; the new engine's compiles | accepted |
| `composite-patch-hoisted` | threw | the new engine throws a syntax error; TauMC's output is broken GLSL | accepted with the new stage `threw` |
| `composite-legacy-textures` | fragment | TauMC's output is broken GLSL; the new engine keeps non-core calls | accepted; Open questions 2 |
| `transform-grouped-330-undeclared`, `dh-terrain-multitexcoord2` | error-succeeded, vertex | as at S7 | unchanged |
| compat cases (37 packs, 22 DH, 4 mini) | compat | unsupported on `douira` | Step 10 |

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the orchestrator's rule).
2. `TransformPatcherTest`'s three raw-string assertions compare as `GlslTokens` now (Step 8's item 2 for this class), so
   that the Appendix C tests are green on both engines, as "Done when" asks; the other Step 8 test ports are left.
3. The replayer has a new accepted stage, `threw` (a case recorded with an output whose replay throws). Without it
   `composite-patch-hoisted` could only fail; the stage is checked like the others and `*` does not cover it.
4. `patchMultiTexCoord3` is Iris 26.1's with four differences, each following TauMC (the parity rule): no second
   declaration when the shader declared `gl_MultiTexCoord3`; `mc_midTexCoord` tested for a declaration (TauMC) rather
   than any use (Iris); the declaration placed through TauMC's `injectVariable` anchor, not Iris's
   `BEFORE_DECLARATIONS`; and ATTRIBUTES injecting `in vec4 mc_midTexCoord;` where Iris writes `attribute vec4`
   (the last two listed by the verification follow-up; before, only the javadoc named them).
5. Timing runs through a new `scripts/glsl-corpus/timing.sh` (no recorder), not `capture.sh`; one `capture.sh` run on
   the final code for Item 5 and the frames. `ShaderAst.Timing` and the `[AstShaderTransformer] ... timing` log line
   stay in main code (perf debug only), for Step 8's load-time measurements.
6. Item 4 matches more shapes than (a) to (d) (the lone CR on `#else`/`#endif`, block comments over two lines, strict
   directives, `#` inside a line) and deliberately not (e) nor the top-level `#else`/`#elif`, where matching TauMC would
   under-count.
7. Nine mini-corpus cases rather than one per item: three `#extension` shapes, two `patch` shapes, three
   `gl_MultiTexCoord3` shapes, one legacy-texture case.
8. The plan note and the S06 corrections are dated 2026-09-29, the UTC date of this step (as the orchestrator gave it);
   the local date was 2026-09-28.
9. The investigation's "parse inside the lock" runs used a temporary environment switch in `ShaderAst`
   (`S7B_PARSE_UNDER_LOCK`), and the root comparison a temporary `S7B_EXACT_ROOT`; both removed before the commit
   (`grep -rn S7B` finds nothing), as were three scratch test classes.

## Open questions

1. `BUILD_LOCK` after the fix: 19 to 34 % of the summed in-game transform time is still spent waiting for it, behind
   program AST builds. Removing it would need glsl-transformer 3.0.0-pre3's build-root stack, a private static
   `ArrayDeque` in `Root` (`activeBuildRoots`), made thread-local (by reflection, the field is not final) and a snippet
   parser per thread (its AST cache is an unsynchronized LRU). The median is already at TauMC's; worth it for load time,
   given the risk of a library-internal hack?
2. Legacy texture calls under core (Item 6): add `texture2DRect`, `textureCube`, `texture1D`, `texture2DArray`,
   `texture1DArray`, `textureCubeArray` to the Iris pipeline's renames (`texture`), as GLSM's
   `renameParseBreakingTextureFunctions` does? A behaviour change for both engines (TauMC would stop emitting broken
   GLSL for them); `composite-legacy-textures` and `legacyTextureCallsKeepTheirNames` change with it.
3. `gl_MultiTexCoord3` read as the built-in by a shader that also declares `mc_midTexCoord` stays in the program on
   every engine (Iris 26.1 has a TODO for it). Map it to a converted `mc_midTexCoord` after Step 8?
4. `patch` as an identifier at 400 and above: keep the syntax error (TauMC's output never compiled), or rename it
   without restoring it (compiles, but renames a uniform or varying named `patch`)?
5. S5's open question 1 (syntax errors) is narrower now: the three `#extension` shapes no longer throw. A source that
   does not parse still throws where TauMC returned an error-recovered program; keep that at Step 8?
6. S5's open questions 2, 3, 5 and 6, S6's 2 (answered by this step's timing) and S7's 1 (`gl_MultiTexCoord2` in DH
   programs) stand; S5's 4 and S7's 2 are answered above.

## Notes for the next step

- Timing: `scripts/glsl-corpus/timing.sh <bsl|complementary|vanilla> <taumc|douira> <tag>` runs one pack load per
  engine without the recorder and prints `transforms`, `medianMs`, `p90Ms`, `sumMs`, first 30 against the rest; on the
  new engine also the sums of `[AstShaderTransformer] <kind> timing ...` (parse, AST build, lock wait and hold).
  `scripts/glsl-corpus/transform-times.py <log>...` does the same for any perf-debug log (for example Step 8's `cp4`
  sweep). Run engines interleaved in one session: the run-to-run spread is up to 25 % in the median.
- `TransformPatcherTest` already compares as `GlslTokens`: green on both engines. The other Step 8 test ports are
  untouched.
- `accepted.txt` has nine entries, each with a checkable reason; six are this step's intended changes (Residual
  diffs). Accepted stages are now `vertex`, `geometry`, `tess_control`, `tess_eval`, `fragment`, `compute`, `compat`,
  `error-succeeded`, `error-threw`, `threw`, `*`.
- The mini-corpus has 31 cases (30 at `307d84ce`; the verification follow-up added `composite-extension-line-comment`).
- `capture.sh` exits 3 and names the frames when a run does not write every frame of its script; it no longer copies
  stale frames.
- `ShaderAst.parse` no longer puts `#extension` nodes in the tree: `extensionDirectives()` comes from the text
  (`ExtensionLines`). GLSM's `CompatShaderTransformer` (Step 10) separates its own preamble first, as S5 noted.
- New-engine BSL recording on the final code: `run/transform-corpus-s7b-douira/bsl/` (175 cases, all `outcome=ok`).

## Verification follow-up

Independent verification of `307d84ce` found one blocking issue and eight remarks. Fixed in the commit
`glsl-transformer: S7b fix extension line comments` (on top of `33916dfc`); the status stays done.

### Blocking: `ExtensionLines` deleted program text after `#extension X : enable // a /* b`

Confirmed and fixed. `ExtensionLines.lineEnd`, in directive mode, took every `/*` for a block comment, one inside a
`//` comment included, and scanned to the next `*/` or to the end of the source; `ExtensionLines.of` then blanked all of
it with the directive. Now a `//` in a directive ends the directive at that comment's own line break (which a backslash
still continues; no longer since follow-up 2), before any `/*` is looked at (`ShaderAst.java`, `lineEnd`; its javadoc says so).

Tests added (the earlier fix agent had written them before it was interrupted; kept as they were):

- `ShaderAstExtensionLinesTest` (new, 5 tests): the line comment with `/*` in the leading block, the same before a later
  `/* ... */`, in a function body (the program then parses and keeps `main`'s body), a block comment on the directive
  line still spanning two lines, and a backslash-continued line comment (that test asserted the deletion of the next line; follow-up 2
  replaces it). Each asserts the exact blanked text.
- `AstShaderTransformerTest.extensionLineWithALineCommentHoldingABlockCommentStart`: the new mini-corpus case against
  TauMC (`assertSameProgram`), plus the fragment with a later block comment.
- Mini-corpus case `composite-extension-line-comment` (COMPOSITE; the fragment has the comment in the leading block,
  the vertex shader in `main`), TauMC outputs recorded.

On the unfixed `ShaderAst.java` (`git show HEAD:...`, `run/s7b-fix-prefix-check.out`) five of the six new tests fail:
`25 tests completed, 5 failed`, `BUILD FAILED`; `aBlockCommentOnTheLineStillSpansLines` passes there too, as it
should (it guards the block-comment path the fix keeps).

The verifier's probes, rebuilt in the scratchpad (`p-a`: the comment in the leading block; `p-b`: the same with a
later `/* x */`; `p-c`: the comment inside `main`), recorded on both engines with `-PglslReplayRecord=true`
(`run/s7b-fix-probe-{douira,taumc}.out`, `recorded=3` each): every output of both engines, vertex and fragment, has no
`ERROR` from `glslangValidator`, and the new engine's fragments keep `uniform sampler2D colortex0;` and `main`. (A
negative control, an undeclared identifier, printed `ERROR: 0:2: 'x' : undeclared identifier`; the check grepped for
`ERROR`. Follow-up 2 corrects the claim this sentence made here, that glslangValidator exits 0 either way: 16.4.0
exits 2 on a compile error, `run/s7b-fix2-glslang.out`.)

Re-run after the fix:
```
Appendix C tests, -PglslEngine=douira (run/s7b-fix-appc-douira.out): BUILD SUCCESSFUL, classes 18 tests 519 skipped 3 failures 0 errors 0
Appendix C tests, default taumc     (run/s7b-fix-appc-taumc.out):  BUILD SUCCESSFUL, classes 18 tests 519 skipped 3 failures 0 errors 0
replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=31 identical=18 (byte-identical 0) accepted=9 failing=0 unsupported=4 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=24 groups=2 sequentialMs=40.1 concurrentWallMs=16.6 concurrentCallMs=111.4 differing=0
replay: engine=taumc corpus=.../src/test/resources/transform-corpus cases=31 identical=31 (byte-identical 31) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=387 groups=2 sequentialMs=7760.2 concurrentWallMs=1632.6 concurrentCallMs=12924.3 differing=0
replay: engine=douira corpus=.../run/transform-corpus-dh cases=140 identical=118 (byte-identical 0) accepted=0 failing=0 unsupported=22 recorded=0 filtered=0
replay: concurrent engine=douira threads=8 cases=118 groups=2 sequentialMs=4196.2 concurrentWallMs=837.4 concurrentCallMs=6443.6 differing=0
tvfr-parity: corpus=.../src/test/resources/transform-corpus vertexOutputs=24 differing=0
./gradlew build (run/s7b-fix-build.out, 03:19:05 to 03:19:18): BUILD SUCCESSFUL in 12s, 134 classes, 1,054 tests, 0 failures, 5 skipped; the seven verify* tasks ran
```
Replay logs `run/s7b-fix-replay-{mini-douira,mini-taumc,transform-corpus-douira,transform-corpus-dh-douira}.out`,
TVFR `run/s7b-fix-tvfr-mini.out`. The new case is identical (tokens) on the new engine with no accepted entry
(`identical` 17 to 18, `accepted` 9 unchanged). 1,054 = 1,048 + the six new tests. The TauMC replays of the two pack
corpora were not re-run: the change is in `ShaderAst`, which only the new engine uses. No dev client was run for the
fix: no recorded pack has a `/*` inside a line comment on a directive line (verifier), so an in-game run would not
exercise it.

Residual diffs, addition:

| Case | Stage | Classification | Action |
|---|---|---|---|
| mini-corpus `composite-extension-line-comment` | vertex, fragment | identical (tokens) | none |

### Remarks

1. Evidence mislabel: `run/s7b-tvfr-transform-corpus.out` holds the mini-corpus run (`vertexOutputs=23`), not the
   `run/transform-corpus` one, so "Commands run" cites the wrong file for `vertexOutputs=387 differing=0`. The verifier
   reproduced 387 on `run/transform-corpus`; the claim stands, the file for it is not in `run/`. Not re-run here (the
   fix does not touch TauMC's lexer or the scanner). The mini-corpus is now `vertexOutputs=24 differing=0`.
2. `capture.sh`: fixed. The summary line now comes after the missing-frame check and prints both, `exit <script
   status> (client <client status>)`, and a missing shot's older copy is removed from the frame directory
   (`run/engine-screenshots/<tag>/`) instead of staying next to the fresh frames. Checked with a stub `gradlew` in the
   scratchpad (not a client run): one of two shots written gives `exit 3 (client 0)`, script exit 3, and the stale
   `b.png` removed from the frame directory; both written gives `exit 0 (client 0)`; a failing client that writes none
   gives `exit 1 (client 1)`.
3. `patchMultiTexCoord3`'s other two differences from Iris 26.1 (TauMC's `injectVariable` anchor; `in vec4
   mc_midTexCoord;` rather than `attribute vec4`): added to Deviation 4 above. Both follow TauMC, per the parity rule.
4. `ShaderAst.Timing` counts every `BUILD_LOCK` acquisition even without perf debug: not changed. The cost is a
   `ThreadLocal` get and two `nanoTime` calls per acquisition (about 10k per pack); gating it would thread the perf
   flag into `ShaderAst`. Step 8 can gate it if its load-time measurements show it.
5. Every `Shader-Transform` worker logs as `Shader-Transform-0` (`Iris.java:178` reads the pool index before the
   thread is registered): existing code, not changed; the "eight threads" is the pool size, not a log observation.
6. Table 1's time span: corrected above to 01:16 to 01:21 UTC.
7. An `#extension` line continued with a backslash (`#extension GL_ARB_gpu_shader5 \` then ` : enable`): TauMC throws
   and the new engine transforms it. Reproduced in the scratchpad (`run/s7b-fix-probe2-{taumc,douira}.out`): TauMC
   `IndexOutOfBoundsException: Index: -1, Size: 36 ... at org.taumc.glsl.Transformer.injectFunction`, the new engine
   writes `#extension GL_ARB_gpu_shader5 : enable` in the header and its fragment output has no glslangValidator
   `ERROR`. An improvement of this step's pre-pass, now documented; not a mini-corpus case, since TauMC has no output
   to record.
8. Still broken on both engines, not a regression, unchanged: mini case `attributes` (Open question 3) and
   `composite-legacy-textures` (Open question 2, Item 6).

## Verification follow-up 2

A second independent verification of `658bf2cf` found one blocking issue and two report inaccuracies. Fixed in the
commit `glsl-transformer: S7b fix continued line comments on extension lines` (on top of `658bf2cf`); the status stays
done.

### Blocking: a backslash at the end of a line comment on an `#extension` line blanked the next line

Confirmed and fixed. `ExtensionLines.lineEnd` read a backslash before the line break as a continuation in a line
comment too, so `#extension GL_ARB_gpu_shader5 : enable // c \` blanked the next line with the directive: a declaration
in the leading block (`uniform float u;`, then "'u' : undeclared identifier" wherever it is used) or a statement in
`main` (`outColor = texture(colortex0, texcoord);` silently gone). GLSL has no line continuation before 4.20,
glsl-transformer's lexer ends a line comment at the line break (`LINE_COMMENT_frag: '//' NO_NEWLINE*`, every mode), and
the TauMC engine keeps the next line. The fix matches glsl-transformer's lexer, since it parses the text the pre-pass
leaves: `lineEnd` is split into `directiveEnd` (backslash continuation, block comments across lines, as before) and
`lineCommentEnd` (the first `\n` or `\r`, no continuation), which both the directive's `//` and a `//` outside
directives now use (the latter also kept a `// c \` from hiding an `#extension` on the next line). No version-dependent
rule: at 420 and above glsl-transformer's lexer still keeps the next line, so continuing there would blank text the
parser reads as code. The class javadoc names the rule.

Tests: `ShaderAstExtensionLinesTest.aContinuedLineCommentEndsAtTheNextLineBreak` (which asserted the deletion) is
replaced by `aContinuedLineCommentDoesNotTakeTheNextLine` (leading block: only the directive line blanked, the parse
keeps `uniform float u ;`), `aContinuedLineCommentInAFunctionBodyKeepsTheNextStatement` (only the directive blanked,
`main` keeps both statements) and `aContinuedLineCommentBeforeAnExtensionLineEndsAtItsLineBreak` (a `// c \` line
before an `#extension` in `main`: the directive is still found and blanked). Mini-corpus case
`composite-extension-continued-comment` (COMPOSITE; both stages have both shapes, `// c \` in the leading block before
`uniform float u;` and in `main` before a statement using `u`; vertex at `#version 120`, fragment at `#version 330
core`), TauMC outputs recorded with `-PglslReplayEngine=taumc -PglslReplayRecord=true` into a scratch copy
(`run/s7b-fix2-record.out`, `recorded=1`) and the two `out.taumc.*` files copied in. TauMC keeps `uniform float u ;`
and the statement in both stages.

On the unfixed `ShaderAst.java` (`git show HEAD:...`, then the fix restored): the three new tests fail
(`run/s7b-fix2-unit-unfixed.out`: `7 tests completed, 3 failed`, `BUILD FAILED`), and the new case fails the replay
(`run/s7b-fix2-replay-newcase-unfixed-douira.out`: `failing=1`, `FAILING composite-extension-continued-comment [vertex,
fragment]`); the new engine's unfixed outputs (`run/s7b-fix2-probe-unfixed-douira.out`, record mode) have no
`uniform float u` and end `main` at `outColor = vec4(0.0f);` / `texcoord = vec2(0.0f);`.

glslangValidator 16.4.0 (`run/s7b-fix2-glslang.out`; `-S vert|frag`, exit status read, not only `ERROR` lines): TauMC's
two outputs, the new engine's two fixed outputs (`run/s7b-fix2-probe-fixed-douira.out`, which keep the declaration and
both statements) and its two unfixed outputs all exit 0 with no `ERROR` (the unfixed ones compile only because the use
of `u` went with its declaration). The input fragment at `#version 330 core` exits 0 with `WARNING: 0:2: 'line
continuation' : used at end of comment, but this version does not provide line continuation` (and the same at 0:9). A
negative control (`vec4(x)`, `x` undeclared) prints `ERROR: 0:3: 'x' : undeclared identifier` and exits 2.

Re-run after the fix (all with `--rerun`, one Gradle process at a time; counts from `build/test-results/test/*.xml`):
```
Appendix C tests, default taumc     (run/s7b-fix2-appc-taumc.out):  BUILD SUCCESSFUL, classes 18 tests 521 skipped 3 failures 0 errors 0, GLSL transform engine: taumc
Appendix C tests, -PglslEngine=douira (run/s7b-fix2-appc-douira.out): BUILD SUCCESSFUL, classes 18 tests 521 skipped 3 failures 0 errors 0, GLSL transform engine: douira
replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=32 identical=19 (byte-identical 0) accepted=9 failing=0 unsupported=4 recorded=0 filtered=0
replay: accepted entries in scope=9 used=9 stale=0
replay: concurrent engine=douira threads=8 cases=25 groups=2 sequentialMs=36.6 concurrentWallMs=17.8 concurrentCallMs=123.2 differing=0
replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=387 (byte-identical 0) accepted=0 failing=0 unsupported=37 recorded=0 filtered=0
replay: accepted entries in scope=0 used=0 stale=0
replay: concurrent engine=douira threads=8 cases=387 groups=2 sequentialMs=8102.1 concurrentWallMs=1611.2 concurrentCallMs=12800.6 differing=0
replay: engine=douira corpus=.../run/transform-corpus-dh cases=140 identical=118 (byte-identical 0) accepted=0 failing=0 unsupported=22 recorded=0 filtered=0
replay: accepted entries in scope=0 used=0 stale=0
replay: concurrent engine=douira threads=8 cases=118 groups=2 sequentialMs=4222.7 concurrentWallMs=854.4 concurrentCallMs=6468.6 differing=0
replay: engine=taumc corpus=.../src/test/resources/transform-corpus cases=32 identical=32 (byte-identical 32) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
tvfr-parity: corpus=.../src/test/resources/transform-corpus vertexOutputs=25 differing=0
```
Every run `BUILD SUCCESSFUL`. Replay logs `run/s7b-fix2-replay-{mini,transform-corpus,transform-corpus-dh}-douira.out`
and `run/s7b-fix2-replay-mini-taumc.out`, TVFR `run/s7b-fix2-tvfr-mini.out`. 521 = 519 + 3 new tests - the replaced
one. The new case is identical (tokens) on the new engine with no accepted entry (`identical` 18 to 19, `accepted` 9
unchanged). Skipped: `./gradlew build` (the Appendix C runs compiled and ran the changed classes; the fix touches no
build logic), the TauMC replays of the two pack corpora (the change is in `ShaderAst`, which only the new engine uses),
and a dev client (no recorded pack has a backslash after a line comment on an `#extension` line: `grep -rlE
'^[[:space:]]*#[[:space:]]*extension.*//.*\\$'` over `run/transform-corpus*` finds none;
over the mini-corpus it finds the new case's two inputs).

Report corrections: the follow-up's parenthesis that glslangValidator "exits 0 either way" is corrected above (16.4.0
exits 2 on a compile error); Item 5's "no frame directory created" now says it describes the `307d84ce` script.

Residual diffs, addition:

| Case | Stage | Classification | Action |
|---|---|---|---|
| mini-corpus `composite-extension-continued-comment` | vertex, fragment | identical (tokens) | none |
