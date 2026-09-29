# S12: optional payoff (one upstream port, the porting guide, the reserved-word question)

Step 12 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `964bf06d` (S1 to S11 and
S7b done, exit point B), on 2026-09-29. Dev-client log stamps are local time (UTC-4); test XML stamps are UTC.

## Status

**Done.**

| Done when | Evidence |
|---|---|
| The guide exists | [`../PORTING_GUIDE.md`](../PORTING_GUIDE.md) (commit `aaf9c73f`): file map, the two ways to write a transformation, the verification workflow, the Minecraft-code note with the `cleanroom` tool's answers |
| The demonstrated port replays clean | Mini-corpus (brief's Verify 1): `cases=44 identical=27 (byte-identical 0) accepted=17 failing=0 unsupported=0`, `accepted entries in scope=17 used=17 stale=0`, `BUILD SUCCESSFUL`. Pack corpus (Verify 2): `cases=424 identical=424 ... accepted=0 failing=0`. Also `run/transform-corpus-dh` 140/140 and `run/transform-corpus-s7-taumc` 175/175 identical; the concurrent pass (`-PglslReplayThreads=8`) `differing=0` on all four |
| ... and has tests | `transformer/CompatibilityTransformerTest` +6 (empty declarations, tessellation pairing, type-mismatch cast, unsigned zero, array types, a deterministic order); `ShaderAstSnapshotTest.irisTransformGroupedAgainstTauMc` (6 named-deviation cases); `ShaderTransformerTest.groupedTypesCompareAsSpelled` and `dhPrograms` turned into named deviations; five mini-corpus cases. Verify 3 (`net.coderbot.iris.pipeline.transform.*`): `BUILD SUCCESSFUL`, XML `classes=15 tests=494 skipped=1 failures=0 errors=0`. Full `:test --rerun`: `classes=134 tests=1061 skipped=2 failures=0 errors=0` |
| The reserved-word report is written | "The reserved-word question" below: no parse failure in any recorded corpus, but 114 pack cases and one mini-corpus case would emit GLSL that does not compile without `replaceTexture`, and `sample` at 400 and above does not parse without `renameReservedWords`. Not proposed for removal |

## Commits

| Commit | Subject |
|---|---|
| `32551715` | glsl-transformer: S12 port Iris 26.1's empty-declaration removal and transformGrouped |
| `aaf9c73f` | glsl-transformer: S12 porting guide, capture.sh protects the S7 TauMC corpus, S11 correction |
| the commit that adds this page | glsl-transformer: S12 report and status |

## What changed

Added:
- `docs/glsl-transformer_adoption/PORTING_GUIDE.md`.
- Mini-corpus cases (`src/test/resources/transform-corpus/`), all COMPOSITE, hand-written: `transform-each-empty-declaration`
  (a `;;`, a `};` after a function, a lone `;`), `transform-grouped-type-mismatch` (`out vec3 tint, glow` against
  `in vec4 tint`), `transform-grouped-geometry` (vertex, geometry, fragment; geometry array inputs, one without a
  vertex output), `transform-grouped-arrays` (an array type, an array declarator of another type, an unassigned array
  declarator), `transform-grouped-tessellation` (vertex, tessellation control and evaluation, fragment; the control
  stage reads `vNormal`, which the vertex stage lacks). Their reference is `out.douira.*`, recorded from the engine
  *before* the port (record mode into `run/s12-mini-new/`, then copied).

Changed:
- `shader/.../transform/transformer/CompatibilityTransformer.java`: `transformEach` keeps Sildur's patch and the two
  TauMC-parity verbs, then runs Iris 26.1's empty-declaration block; `transformGrouped` runs Iris 26.1's
  `transformGrouped` over the stages' trees. The Iris code is in the nested class `Upstream`, copied with its
  comments, tabs and warnings. Against Iris's file, `transformGrouped` differs in 8 lines (2 loops, 6 warning strings;
  `diff` of the method) and `DeclarationMatcher` in none. Every change is marked `// Demonica:`:
  - adaptation: the code runs inside `ast.build(...)` (`ShaderAst.BUILD_LOCK`); `Upstream` is nested so its
    `Template`s and `Matcher`s, which glsl-transformer builds on its static build stack, are built at first use,
    inside the lock;
  - adaptation: Demonica's `Parameters` has no `name`; the warnings name the patch (`programName`);
  - adaptation: only the empty-declaration block of Iris's `transformEach` is taken (the report of what was left out
    is in the class comment);
  - fix: `getInitializer` uses `0u` for unsigned types. glsl-transformer's `LiteralExpression.getDefaultValue` returns
    an `INT32` 0 for them, so Iris writes `id = 0;` for a `flat out uint id`; glslangValidator at 330: `'assign' :
    cannot convert from ' const int' to ' flat out uint'` (at 400: no error). TauMC's engine wrote `0u`;
  - fix: the two declaration loops iterate `tree.getChildren()` in document order (`inDocumentOrder`), not
    `root.nodeIndex.get(DeclarationExternalDeclaration.class)`, a `HashSet` in identity-hash order
    (`NodeIndex.withUnordered`, from `RootSupplier.PREFIX_UNORDERED_ED_EXACT`). Before the fix the same geometry and
    fragment inputs (`dh-terrain-legacy` and `dh-generic-legacy` share them) gave the injected outputs in two different
    orders in one replay.
- `src/test/.../TransformCorpusReplayTest.java`: a case without `out.taumc.*` is compared with its `out.douira.*`
  (`LATER_REFERENCE_ENGINE`); the diff header names the reference used.
- `src/test/.../ShaderAstSnapshotTest.java`: TauMC's `transformGrouped` written on `ShaderAst` (the code the engine ran
  until this step) moves back into the test as `transformGrouped(Map)`, so `transformGroupedWrittenOnShaderAst` still
  checks the verbs against TauMC's frozen outputs, unchanged; its cases moved into `groupedCases()`. New
  `irisTransformGroupedAgainstTauMc`: the engine's method on the same six cases against the same frozen outputs, as
  named deviations (the same lines in another order, plus: `tint = vec4(0.0)`, which TauMC's text-prefix
  `hasAssignment` skipped because of `tintOut = ...`; `color`/`texcoord` initializations for the 120 vertex stage's
  outputs, since Iris always initializes a missing output; `m = mat2(0.0)` and `k = mat3(0.0)`, since Iris compares
  `Type`s and `mat2x2` is `mat2`).
- `src/test/.../ShaderTransformerTest.java`: `groupedTypesCompareAsSpelled` (name kept: it is the snapshot key) now
  asserts the deviation above; `dhPrograms` compares the geometry stage as sorted lines (declaration order).
- `src/test/.../transformer/CompatibilityTransformerTest.java`: six tests (Status table).
- `src/test/resources/transform-replay/accepted.txt`: a Step 12 section, eight entries (Residual diffs).
- `scripts/glsl-corpus/capture.sh`: `transform-corpus-s7-taumc` added to the refused `GLSL_CORPUS_ROOT` names and to
  the header comment. Checked: `GLSL_CORPUS_ROOT=run/transform-corpus-s7-taumc bash scripts/glsl-corpus/capture.sh bsl`
  prints `GLSL_CORPUS_ROOT must not be a default or reference corpus root (transform-corpus-s7-taumc)`, exit 2, and
  `run/transform-corpus-s7-taumc/bsl` still holds 175 cases.
- `reports/S11-remove-taumc.md`: a dated correction under its cp4 line (four of five switches loaded a pack; the fifth
  was `pack off`, `(off) (loaded: false)`, `run/s11-cp4.out` line 1210).

Deleted (not in git): `run/client/mods/1.12.2/antlr4-runtime-4.13.2.jar` (326,307 bytes, 2026-09-23 21:41, SHA-256
`dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7`), before the sweep. The Prism instance was not
touched.

## Commands run and their outcomes

Every Gradle invocation one at a time, `--rerun` on test runs, counts from `build/test-results/test/*.xml`; the replay
summaries from the XML's standard out (the default mini-corpus run) or the console (`-PglslCorpusDir` runs, which turn
on `showStandardStreams`). Memory notes read first (dev-run gotchas; the sync gotchas, since no build file changed, only
for reference).

Baseline before any change (`run/s12-baseline-mini.out`): mini-corpus `cases=39 identical=30 accepted=9 failing=0`,
`scope=9 used=9 stale=0`.

Brief's Verify:
```
$ ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/src/test/resources/transform-corpus 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
    replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=44 identical=27 (byte-identical 0) accepted=17 failing=0 unsupported=0 recorded=0 filtered=0
    replay: accepted entries in scope=17 used=17 stale=0
    replay: transformMs engine=douira total=87.4 ATTRIBUTES=21.3/5 CELERITAS_TERRAIN=17.1/3 COMPOSITE=27.6/18 COMPUTE=3.8/1 DH_GENERIC=10.5/2 DH_TERRAIN=7.1/3
BUILD SUCCESSFUL in 2s
$ ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/run/transform-corpus 2>&1 | grep ... | tail -20
    replay: engine=douira corpus=.../run/transform-corpus cases=424 identical=424 (byte-identical 0) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0
    replay: accepted entries in scope=0 used=0 stale=0
BUILD SUCCESSFUL in 12s
$ ./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' --rerun 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
BUILD SUCCESSFUL in 2s          (XML: classes=15 tests=494 skipped=1 failures=0 errors=0)
```
(`run/s12-verify-{1,2,3}.out`; Gradle 9 prints no "Tests run" lines.)

Replays after the port with the concurrent pass (`-PglslReplayThreads=8`, `run/s12-port-<corpus>.out`, each
`BUILD SUCCESSFUL`):
```
run/transform-corpus          cases=424 identical=424 accepted=0 failing=0   concurrent cases=387 differing=0
run/transform-corpus-dh       cases=140 identical=140 accepted=0 failing=0   concurrent cases=118 differing=0
run/transform-corpus-s7-taumc cases=175 identical=175 accepted=0 failing=0   concurrent cases=170 differing=0
mini-corpus                   cases=44 identical=27 accepted=17 failing=0    concurrent cases=30 differing=0   scope=17 used=17 stale=0
```
The port's warnings (`compatibility-patched`, `Removed empty external`, `bailing compatibility`) in the three pack
replays: the only two in each run's XML come from `acceptedEntriesThatTolerateNothingAreStale`, which replays the
mini-corpus case `transform-grouped-330-undeclared` in the same class; no pack program triggers the port.

Full `:test --rerun` (`run/s12-full-test.out`): `BUILD SUCCESSFUL in 4s`, `classes=134 tests=1061 skipped=2
failures=0 errors=0`, 06:16:49 to 06:16:52 UTC (S11: 1,049; +6 `CompatibilityTransformerTest`, +6
`irisTransformGroupedAgainstTauMc`). `./gradlew check` once (`run/s12-check-1.out`): `BUILD SUCCESSFUL in 7s`,
`verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`,
`verifyModuleBoundaries`, `verifyRunClasspath`, `verifyS8tnlibPin` ran; `:test UP-TO-DATE` (the full run above
executed it on the same tree).

**Compile checks** (`run/s12-glslang.txt`; glslangValidator 16.4.0; each stage, then `-l` over the stages;
post-port outputs recorded into `run/s12-mini-post/`):

| Case | Before the port | After |
|---|---|---|
| transform-each-empty-declaration | vertex and fragment: `'extraneous semicolon' : not supported for this version` | ok, links |
| transform-grouped-type-mismatch | stages ok; link: `Types must match` | ok, links |
| transform-grouped-tessellation | ok, links (glslang does not report that the vertex stage never writes the control stage's `vNormal`) | ok, links; the vertex stage writes `vNormal` |
| transform-grouped-geometry | ok, links | ok, links |
| transform-grouped | ok, links (TauMC output) | ok, links |
| transform-grouped-arrays | vertex: `cannot convert from ' const float' to ' smooth out 2-element array of float'` (`lit = 0.0f`); link: `Types must match` | vertex: the same `lit = 0.0` error; with that line removed, `cannot convert from ' const 3-component vector of float' to ' temp float'` (`iris_template_w[0] = ...`). Broken before and after |
| dh-terrain-legacy, dh-generic-legacy | geometry: `'glcolor' : redeclaring non-array as array` (TauMC output) | the same error; the hand-written legacy geometry stage, not the port |

`glslangValidator` on the extraneous semicolon: rejected at 330 and 450, accepted at 460 (scratch shaders).

**Sweep** (the orchestrator's note; memory notes read; no other Gradle build of this repository running; one run):
`./gradlew runClient -PdevScript=@$PWD/scripts/glsl-corpus/sweep.txt -PdevProps=demonica.glsmPerfDebug=true >
run/s12-sweep.out`, 02:13:55 to 02:15:06, `BUILD SUCCESSFUL in 1m 11s`, `Dev script finished; shutting down`.
Six pack loads `(loaded: true)` (BSL, Complementary Reimagined, I Like Vanilla, then each from the cache), two
`(off) (loaded: false)`; 0 `Shader compilation failed`, `Failed to compile`, `SyntaxException`,
`ClassNotFoundException`, `NoClassDefFoundError`, `LinkageError`; 19 "injectors found their targets"; 0 of the port's
warnings in `run/s12-sweep.out` and in `latest.log`. Frames `run/engine-screenshots/s12-sweep/`, logs
`run/s12-sweep-{latest,debug}.log`.

ANTLR on the class path (`run/s12-sweep-debug.log`): `antlr4-runtime-4.13.2` 0 lines, `antlr4-runtime-4.13.1` 6 lines
(`[02:13:58] [main/DEBUG] [Cleanroom]: Examining for coremod candidacy antlr4-runtime-4.13.1.jar`, the Gradle-cache
path at `Found a minecraft related file`, `Examining file antlr4-runtime-4.13.1.jar for potential mods`, ...). S11's
cp4 debug log had 5 lines for 4.13.2 (`run/s11-cp4-debug.log`).

`cleanroom` MCP tool, `find_equivalent` with `from: "modern-minecraft"`, on classes Iris 26.1's pipeline code outside
`transform/` imports: `net.minecraft.resources.Identifier.fromNamespaceAndPath` (Iris `CustomTextureManager.java:141`),
`Identifier`, `ResourceLocation`: "Found 0 equivalents" each; `RenderSystem`: one pattern-change result (modern
`PoseStack`/`RenderSystem` "becomes direct GlStateManager/Tessellator calls", topic block-entity-renderer, "validated
against cleanroom 0.6.3-alpha"). The guide records these. Iris 26.1's `pipeline/transform/` has 0 `net.minecraft`/
`com.mojang` imports (grep).

## Measurements

### Table 1: the sweep, frames against S11's

Share of pixels with a channel differing by more than 16/255 (numpy over ffmpeg's rgb24 decode,
`run/s12-imgdiff-sweep.txt`). S11 made two sweeps; its own two runs are the noise column.

| Frame | S12 vs S11 run 1 | S12 vs S11 run 2 | S11 run 1 vs run 2 | S12 vs S8 (glsl-transformer) | S12 vs S8 (TauMC) |
|---|---|---|---|---|---|
| 0 no pack | 0.01 % | 0.01 % | 0.01 % | 0.02 % | 0.02 % |
| 1 BSL | 0.08 % | 0.10 % | 0.22 % | 0.07 % | 0.07 % |
| 2 Complementary | 0.02 % | 0.02 % | 0.02 % | 0.04 % | 0.02 % |
| 3 I Like Vanilla | 1.41 % | 0.29 % | 0.80 % | 0.25 % | 0.37 % |
| 4 off | 0.01 % | 0.01 % | 0.01 % | 0.02 % | 0.02 % |
| 5 BSL cached | 0.07 % | 0.08 % | 0.15 % | 0.13 % | 0.07 % |
| 6 Complementary cached | 0.02 % | 0.00 % | 0.03 % | 0.03 % | 0.02 % |
| 7 I Like Vanilla cached | 0.59 % | 0.41 % | 0.53 % | 0.45 % | 0.42 % |

Every frame is within the spread of S11's two runs or of the S8 frames; the one value above 1 % (I Like Vanilla
against S11 run 1) is against S11's outlier run (its I Like Vanilla phase was twice as slow, S11 table 2), and the same
frame is 0.29 % from S11 run 2 and 0.37 % from S8's TauMC frame. The port changes no recorded pack program, so no
frame difference is expected from it.

### Table 2: the sweep's transform times

`scripts/glsl-corpus/transform-times.py --phases run/s12-sweep-latest.log`: 317 transforms, median 36.9 ms, p90 84.3,
sum 14,049.1 ms.

| Phase | S12: transforms / hits, median / p90 / sum ms | S11 run 1 | S11 run 2 |
|---|---|---|---|
| warm-up | 2 / 0, 11.3 / 16.7 / 22.6 | 11.3 / 15.5 / 22.6 | 8.2 / 10.6 / 16.4 |
| first BSL | 104 / 3, 30.7 / 57.0 / 3,410.9 | 36.1 / 68.5 / 3,704.1 | 32.8 / 60.8 / 3,304.2 |
| first Complementary | 113 / 2, 56.4 / 124.5 / 7,330.7 | 56.6 / 158.5 / 8,432.9 | 51.9 / 121.9 / 7,387.9 |
| first I Like Vanilla | 98 / 3, 32.8 / 43.0 / 3,284.9 | 72.3 / 123.5 / 7,598.5 | 37.1 / 52.2 / 3,731.3 |
| cached BSL / Complementary / I Like Vanilla | 0 / 108, 0 / 116, 0 / 110 | the same | the same |

## The reserved-word question

Method: a scratch patch (`run/s12-reserved-words-scratch.patch`, never committed; applied, run, reverted with
`git checkout`) disabled `replaceTexture`, `renameReservedWords` and `restoreReservedWords` in `ShaderTransformer`
(both paths) and in GLSM's `CompatShaderTransformer`; for compat cases it also undid, at the start of
`transformInternal`, the rename `GLStateManager.glShaderSource` applies before the recorder sees the source (restore
on the input), so the replay saw the mods' original text. `renameParseBreakingTextureFunctions` (not in the question)
stayed. Then every corpus was replayed against its recorded reference.

| Corpus | Cases | Parse failures | Differing | What differs |
|---|---|---|---|---|
| `run/transform-corpus` | 424 | 0 | 106 (8 BSL, fragment; 98 I Like Vanilla: 87 vertex and fragment, 11 vertex) | 193 diff lines, all one line: `uniform sampler2D gtexture ;` becomes `uniform sampler2D texture ;` |
| `run/transform-corpus-dh` | 140 | 0 | 0 | |
| `run/transform-corpus-s7-taumc` | 175 | 0 | 8 (BSL fragment) | the same line |
| mini-corpus | 39 | 0 | 1 (`compat-shadow-fragment`) | `texture ( sky , ...)` and `texture ( rect , ...)` become `gtexture ( ... )` |

(`run/s12-rw-<corpus>.out`, diffs in `run/s12-rw-reports-<corpus>/`.) Every difference comes from `replaceTexture`:
the packs declare an unused `uniform sampler2D texture;`. With the pass, it becomes `actinium_renamed_texture`, which
`CommonTransformer` renames to `gtexture`; without it, the declaration keeps the name `texture` while the program calls
`texture(...)`, and `CommonTransformer`'s own rename does not fire (at that point the pack still calls `texture2D`).
The result does not compile: glslangValidator on `bsl/00096-ATTRIBUTES-8daa4e02`'s recorded fragment output with that
one line changed: `ERROR: 0:96: 'texture' : can't use function syntax on variable` (the recorded output: 0 errors). In
GLSM the rename of the variable `texture` to `gtexture` renames the calls too (`gtexture(sky, ...)`).

The corpora never exercise the other words at a version where they matter, so a probe (`run/s12-rw-probe-{with,without}/`,
seven COMPOSITE cases recorded with and without the passes) did:

| Probe | Without the passes | With the passes |
|---|---|---|
| `float sample = ...;` at 400 core | `ShaderAst$SyntaxException: line 6:10 no viable alternative at input 'float sample'` | transforms; the output names `sample`, which glslang rejects at 400 (`syntax error, unexpected SAMPLE`); `GLStateManager.glShaderSource` renames it again before the driver sees it |
| `float sample(vec2 p)` at 400 core | `SyntaxException: line 2:35 no viable alternative at input 'float sample'` | transforms |
| `float sample` at 330, `bool new` at 330, `uniform sampler2D sampler` at 460 | identical to the output with the passes, glslang 0 errors | |
| `uniform sampler2D texture` read through `texture2D(texture, uv)` at 330 | `gtexture(colortex0, uv)`: `'gtexture' : can't use function syntax on variable` | `texture(gtexture, uv) + texture(colortex0, uv)`, 0 errors |
| a local `vec4 texture = texture(...)` at 330 | `gtexture(colortex0, uv)`: `no matching overloaded function found` | 0 errors |

Answer: no recorded input fails to parse without the passes (the list is empty), but the passes are not removable.
`replaceTexture` is semantic, not a parser workaround: without it, 114 recorded pack programs (106 + 8) and one
recorded mod shader would be emitted as GLSL that does not compile. `renameReservedWords` is what lets a program that
names something `sample` parse at 400 and above (glsl-transformer's lexer is version-aware, so the keyword is real
there); `new` and `sampler` changed nothing in any corpus or probe. `restoreReservedWords` is the inverse of both.
Proposal: keep all three. What could go instead is narrower: the `new` rename (not a keyword to glsl-transformer at any
version the probes tried) and, as a separate behaviour change, Iris 26.1's AST-level rename of `texture` and `sample`
in `transformEach` (non-call identifiers to `iris_renamed_*`, no restore), which would still need the pre-parse
`sample` rename for 400 and above. Neither is done here.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 424 + 140 + 175 pack cases | all | identical (tokens) after the port | none |
| transform-each-empty-declaration | vertex, fragment | Iris 26.1: empty declarations removed | accepted (`*`) |
| transform-grouped | vertex | Iris 26.1: the missing out injected before the declarations (declaration order) | accepted |
| dh-terrain-legacy, dh-generic-legacy | geometry | Iris 26.1: injection order (the fragment stage's order) | accepted; `ShaderTransformerTest.dhPrograms` |
| transform-grouped-geometry | vertex | Iris 26.1: declaration order | accepted |
| transform-grouped-tessellation | vertex | Iris 26.1: tessellation stages paired (`vNormal` added; `uv`, `normal` no longer) | accepted |
| transform-grouped-type-mismatch | vertex | Iris 26.1: type-mismatch cast | accepted |
| transform-grouped-arrays | vertex | Iris 26.1: array declarator `w` cast as a scalar (broken before and after) | accepted; Open questions 1 |
| the nine older mini-corpus entries | as listed | unchanged | kept |
| ShaderAstSnapshotTest.irisTransformGroupedAgainstTauMc, ShaderTransformerTest.groupedTypesCompareAsSpelled/dhPrograms | | named deviations from the frozen TauMC outputs; no snapshot file edited | tests |
| sweep frames | frame | within S11's run-to-run spread | none |

## Deviations from the brief

1. The port is Iris 26.1's whole `transformGrouped`, not only its array-specifier and geometry-stage handling: those
   are not separable lines of Iris's method (the array check sits inside the type-mismatch branch, the stages are the
   `pipeline` array the whole method walks), and taking part of it would be a translation, which the step exists to
   avoid. So the port also brings the type-mismatch cast, Iris's "always initialize a missing out" and its
   `iris_template_` bail. Of `transformEach` only the empty-declaration block is taken, as the brief says.
2. Two fixes to the Iris code (the unsigned zero, the document order), both found by this step's checks; each is
   marked `// Demonica:` and described in the guide. Neither exists in Iris 26.1.
3. New mini-corpus cases cannot have a TauMC reference any more: the replay now falls back to `out.douira.*`, recorded
   from the engine before the port. Five cases instead of "one each"; `transform-grouped-arrays` compiles neither
   before nor after, kept because it pins what Iris does with array declarators.
4. The frozen-TauMC `transformGrouped` test keeps its assertion by moving TauMC's algorithm (on `ShaderAst`) back into
   the test; the engine's behaviour against the same snapshots is a new named-deviation test. No snapshot was edited
   or re-recorded.
5. The reserved-word flag is a scratch patch, not a property (the orchestrator allowed either). The compat path's
   `GLStateManager` rename was undone on the recorded input for the experiment; the experiment also used a probe
   corpus (`run/s12-rw-probe-*`) because no recorded input uses `sample` or `sampler` at 400 and above.
6. The orchestrator's additions: sweep with the port in (one run; S11 made two), the stale ANTLR jar deleted before
   it, `capture.sh`'s protected roots, the S11 correction, the `cleanroom` tool demonstrated (four queries, where the
   note said once: the first three found nothing, see the guide). Test runs use `--rerun`; the commit trailer names
   Claude Opus 5.5.

## Open questions

1. Iris 26.1's `transformGrouped` treats an array *declarator* (`out vec3 w[2]`) as a scalar: a type mismatch gets a
   scalar `iris_template_w` global (does not compile), and an unassigned `out float lit[2]` gets `lit = 0.0` (does not
   compile; TauMC's `lit = 0.0f` neither). No pack replay logged any of the port's warnings, so no recorded pack
   program has such a pair. Fix in Demonica's copy (skip
   members with an array specifier, as the array-type branch does), report upstream, or leave it as Iris has it?
2. The unsigned-zero and document-order fixes are upstream bugs too (Iris 26.1 has both); report them to Iris or
   glsl-transformer (`LiteralExpression.getDefaultValue` for `UNSIGNED_INTEGER`)?
3. Reserved words: drop the `new` rename (it changed nothing anywhere), and/or take Iris's AST-level `texture`/`sample`
   rename? Both are behaviour changes with their own replay and sweep.
4. `ShaderAst.findQualifiers`, `hasAssignment` and `initialize` are no longer used by main code (only by
   `ShaderAstSnapshotTest`, `VertexShaderGeneratorTest` and `CeleritasTransformerTest`). Keep them as verbs, or remove
   them with their snapshot tests later?
5. S11's open questions stand, except its first: the dev client's stale `antlr4-runtime-4.13.2.jar` is deleted; the
   Prism instance and players upgrading from 0.4.0 still have it (the release-notes question stays).

## Notes for the next step

There is no Step 13 in the plan. For whoever ports the next Iris change:
- Read [`../PORTING_GUIDE.md`](../PORTING_GUIDE.md). The pattern to copy is `transformer/CompatibilityTransformer`:
  Iris's text in a nested class, run inside `ast.build(...)`, `// Demonica:` on every change.
- New mini-corpus cases get an `out.douira.*` reference recorded before the change (record mode into a scratch
  directory, then copy); `accepted.txt` entries name the upstream change.
- Pack corpora are `run/transform-corpus` (424), `run/transform-corpus-dh` (140), `run/transform-corpus-s7-taumc` (175);
  replay each with `-PglslReplayThreads=8` and require `differing=0`.
- The dev client's `mods/1.12.2/` now holds no ANTLR jar; the class path has only the Gradle cache's 4.13.1.
