# glsl-transformer adoption: status

One row per step of [the adoption plan](ADOPTION_PLAN.md). Every step reads this page and the reports of the steps it
depends on before it starts, and updates its own row when it is done. Branch: `feat/glsl-transformer`, created from
`dev` at `2ed28444`.

| Step | Status | Commit | Date | Report |
|---|---|---|---|---|
| S1 build wiring, license, spike | done | `4104a798`, `f7232573`, `114feb14` (report); verification fix `86e62ef7` | 2026-09-28 | [S01-build-wiring.md](reports/S01-build-wiring.md) |
| S2 corpus recorder, replayer, baselines | done | `ebb9d89b`, `c211a5f6`, `6eeea2da` (report) | 2026-09-28 | [S02-corpus.md](reports/S02-corpus.md) |
| S3 `ShaderAst` core verbs | done | `3e1f5fa9`, `80fda189`, `2ab0db35` (report); verification fix `3f9f926f` | 2026-09-28 | [S03-shaderast-core.md](reports/S03-shaderast-core.md) |
| S4 `ShaderAst` structural verbs | done | `53bc8702`, `490fb1bd` (report) | 2026-09-28 | [S04-shaderast-structural.md](reports/S04-shaderast-structural.md) |
| S5 orchestrator, COMPOSITE and COMPUTE | done | `8b7b3489`, `448264f5` (report); verification fix `c9b2eeb2` | 2026-09-28 | [S05-orchestrator-composite.md](reports/S05-orchestrator-composite.md) |
| S6 ATTRIBUTES and CELERITAS_TERRAIN | done | `09e53d27`, `78534be0` (report), `0eb8356c` (status); report fixes `0c9b9579`, `a9de9aa2` | 2026-09-28 | [S06-attributes-terrain.md](reports/S06-attributes-terrain.md) |
| S7 DH and AdaptiveShadowBounds | done | `3f835a76`; report and status in the commit that adds the report | 2026-09-28 | [S07-dh-shadow-bounds.md](reports/S07-dh-shadow-bounds.md) |
| S7b pre-flip hardening (added by the orchestrator) | done | `307d84ce`; report and status in the commit that adds the report; verification fix in `glsl-transformer: S7b fix extension line comments` | 2026-09-29 | [S7b-hardening.md](reports/S7b-hardening.md) |
| S8 flip the default, port the tests, full run (exit point A) | not started | | | |
| S9 GLSM subtractions and utilities | not started | | | |
| S10 `CompatShaderTransformer` | not started | | | |
| S11 remove TauMC, release checks (exit point B) | not started | | | |
| S12 optional payoff | not started | | | |

## Facts every step needs

- Engine switch: system property `demonica.glsl.engine` (`taumc`, the default, or `douira`), read once by
  `TransformPatcher.engine()` and logged at first use as
  `[TransformPatcher] GLSL transform engine: <id> (demonica.glsl.engine)`. An unknown value logs a WARN,
  `[TransformPatcher] Unknown GLSL transform engine '<value>' in demonica.glsl.engine; using taumc`, and falls back
  to `taumc`. The engine is fixed per JVM, and the transform cache key does not include it. The new engine is
  `shader/.../pipeline/transform/AstShaderTransformer.java`.
- glsl-transformer sources: `run/lib-src/glsl-transformer/` (gitignored; Step 1 unpacked them). Read by class.
- Reference parser configuration: `src/test/java/net/coderbot/iris/pipeline/transform/GlslTransformerSpikeTest.java`.
- `Iris.logger` (`IrisLogging`) has no `warn(String, Object...)`: `Iris.logger.warn("... {}", x)` logs
  `[Ljava.lang.Object;@...`. Concatenate warnings, or use `info`/`error`/`debug`, which have the overload.
- Comparing GLSL: `src/test/java/net/coderbot/iris/pipeline/transform/GlslTokens.java` (public; `of`, `tokens`,
  `text`, `contains`, `count`, `diff`). Floats compare by value, so `0.0` equals glsl-transformer's `0.0f`. No test
  compares raw transformed strings.
- Transform corpus: recorded with `-Ddemonica.glsl.corpus=<dir>` (`glsm/.../debug/TransformCorpus`,
  `transform/corpus/TransformCorpusRecorder`); the pack corpora are local, `run/transform-corpus/{bsl,complementary,
  vanilla,compat}/` (424 cases), re-recorded with `scripts/glsl-corpus/capture.sh <name>`; the committed mini-corpus
  is `src/test/resources/transform-corpus/` (21 cases at S7, 30 at S7b, 31 since S7b's verification follow-up; S5's verification follow-up added
  `composite-extension-placement`, S6 `celeritas-terrain-multitexcoord3`, a recorded TauMC error, S7
  `dh-terrain-legacy`, `dh-generic-legacy`, `dh-terrain-multitexcoord2`).
  `GLSL_ENGINE=douira scripts/glsl-corpus/capture.sh <name>` (S6) runs a pack on the new engine and records into
  `run/transform-corpus-douira/<name>/` (log `run/corpus-<name>-douira.out`, frames
  `run/engine-screenshots/douira/`) without touching the TauMC corpus; plain `capture.sh` deletes and re-records the
  TauMC one. `GLSL_CORPUS_ROOT=<dir>` (S7) records into `<dir>/<name>/` instead (log
  `run/corpus-<name>-<basename>.out`, frames `run/engine-screenshots/<basename>/`); real Distant Horizons cases:
  `run/transform-corpus-dh/complementary/` (TauMC, 140 cases, 2 DH_TERRAIN and 1 DH_GENERIC), recorded with
  `GLSL_CORPUS_ROOT=run/transform-corpus-dh scripts/glsl-corpus/capture.sh complementary -PwithCompatMods`. Replay:
  `./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=<abs> -PglslReplayEngine=taumc|douira
  [-PglslReplayPatches=...,COMPAT]`, filtered with `grep -E 'replay|Tests run|FAILED|BUILD'`; tolerated differences in
  `src/test/resources/transform-replay/accepted.txt`. At S2, `taumc` replays all of it identically.
- `ShaderAst` (S3): `glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAst.java` (in `glsm`,
  because GLSM's `CompatShaderTransformer` needs it too; package `net.coderbot.iris.pipeline.transform.transformer`).
  Construct with `ShaderAst.parse(source[, version])`; a syntax error is `ShaderAst.SyntaxException`. glsl-transformer
  3.0.0-pre3 keeps the root of nodes being built on a static stack, so node construction is not thread-safe:
  `ShaderAst` holds `ShaderAst.BUILD_LOCK` around every build, and code that builds nodes through `t`/`tree`/`root`
  itself must hold it too. Parity with TauMC: `ShaderAstParityTest`, and with `-PglslCorpusDir` its corpus mode
  (`grep shader-ast-parity`).
- glsl-transformer's `Matcher`/`AutoHintedMatcher` accept a candidate that is a prefix of the pattern (`f(a)` for
  `f(a, b)`) and ignore argument-list boundaries (`f(g(a), b)` for `f(g(a, b))`). `ShaderAst.replaceExpression` compares
  exact structures instead; do not use `Matcher` for pattern replacement without the same care (S3 verification
  follow-up).
- `GlslTokens` also treats `mat2x2` as `mat2` and `((x))` as `(x)` (S3): glsl-transformer prints both that way.
- `ShaderAst` has all nineteen verbs (S4): the S3 twelve plus `renameAndWrapShadow`, `removeUnusedFunctions`,
  `removeConstAssignment`, `findQualifiers(StorageQualifier.StorageType)` (a `QualifiedDeclaration` record: `typeText`
  with qualifiers, `flat out float`; `typeName`; `arraySpecifierText` of the type), `hasAssignment`, `initialize`,
  `replaceFunctionDefinition(name, source)`; queries `functions()` (`FunctionInfo`), `source`, `text`,
  `isDeclaredGlobal`. It rebuilds TauMC's rule-context cache order (the parsed program, then what its verbs added) for
  the injection anchors, `findType`, `removeVariable`, `findQualifiers` and `removeConstAssignment`; nodes built through
  `t`/`tree`/`root` directly are not recorded as additions. `findQualifiers` iterates in TauMC's `HashMap` order, which
  `transformGrouped`'s injection order depends on; `ShaderAstParityTest.transformGrouped` is TauMC's
  `transformGrouped` written on `ShaderAst` and matches it (S4 report; S5 lifted it into
  `transformer/CompatibilityTransformer.transformGrouped`, which the test now calls).
- Baselines (S2): frames in `run/baseline-screenshots/corpus-*.png`; `transformMs` log sums bsl 4,734.4 ms,
  complementary 13,151.2 ms, vanilla 5,703.3 ms (`run/corpus-<pack>.out`, `-Ddemonica.glsmPerfDebug=true`, recorder on,
  default `demonica.openglProfile`).
- New engine (S5): `AstShaderTransformer` transforms COMPOSITE and COMPUTE (replay: 36/36 pack COMPOSITE cases and 6
  mini-corpus cases identical, 1 accepted); until S7, other kinds threw `UnsupportedOperationException(
  "glsl-transformer engine: <kind> not ported yet")` at entry (S7 removed that with `PORTED`). The ported transformers are
  `shader/.../pipeline/transform/transformer/*` on `ShaderAst`; engine-neutral code: `transform/VersionNegotiation`
  (hoisting, stage minimum, negotiation; `ShaderTransformer.init()`, `versionHoistingState()` and
  `resetVersionHoistingForTesting()` delegate to it) and `transform/CompatibilityPatches` (pack text patches).
- `ShaderAst` (S5): one parser shared by every program, guarded by `BUILD_LOCK` (measured faster than a parser per call
  and than a lock around the whole transform; S5 report). *(S7b: only the verbs' snippets use it now; a program's ANTLR
  parse runs on a parser of its own outside the lock, and only its AST build holds the lock.)* Code that builds nodes through `t`/`tree`/`root` must run
  inside `ast.build(() -> ...)`, which holds the lock and restores the program's lexer version. The parser's snippet
  AST cache is keyed on the snippet's text and rule, not on the lexer version (S5 verification; `newParser` javadoc).
  `findQualifiers(...).typeName()` is the type as spelled in the source (`mat2x2` is not `mat2`), as TauMC compared it.
  `extensionDirectives()` gives the `#extension` lines for the header: TauMC's rule, those of the leading directive
  block only (the directive lines at the very start of the source, up to the first blank line, comment, indented line
  or code); a later `#extension` is dropped, as TauMC dropped it, and listed in `droppedDirectives()` (S5
  verification). The header no longer re-emits other directives.
  A source that does not parse throws `ShaderAst.SyntaxException` (TauMC recovered).
- Replay (S5): `-PglslReplayThreads=N` adds a concurrent pass (`replay: concurrent ... differing=0` must stay 0);
  `replay: transformMs` gives the engine time per kind. `accepted.txt` stages are checked (`vertex`, `geometry`,
  `tess_control`, `tess_eval`, `fragment`, `compute`, `compat`, `error-succeeded`, `error-threw`, `*`; S6 split S5's
  `error`): `error-succeeded` accepts a case recorded as a TauMC error whose replay transforms it, `error-threw` one
  whose replay throws something else (report `<case>.error.diff`); `*` accepts output stages only. (S6's verified
  "S7 pending" entries and their verifier are gone since S7.) `-PglslEngine=taumc|douira` sets `demonica.glsl.engine`
  in the test JVM.
- New engine (S6): also transforms ATTRIBUTES and CELERITAS_TERRAIN (`transformer/AttributeTransformer`,
  `transformer/CeleritasTransformer`; `patchMultiTexCoord3`, `replaceMidTexCoord`, `replaceMCEntity` in
  `AstShaderTransformer`); only DH_TERRAIN and DH_GENERIC throw "not ported yet". Replay: packs `cases=387
  identical=336 accepted=51 failing=0` (the 51 are "S7 pending", BSL fragment stages), mini-corpus `cases=12
  identical=8 accepted=4 failing=0`. BSL, Complementary and I Like Vanilla load and render on it
  (`-Ddemonica.glsl.engine=douira`), frames within the TauMC noise floor (S6 report).
- New engine (S7): every patch kind is ported (`transformer/DHTerrainTransformer`, `DHGenericTransformer`) and the
  adaptive-shadow-bounds rewrite runs in `transformer/CommonTransformer` (`transformer/AdaptiveShadowBoundsTransformer`;
  `replaceFunctionDefinition` must replace exactly one definition, or it throws). Replay on `douira`: packs
  `cases=424 identical=387 accepted=0 failing=0 unsupported=37` (compat), mini-corpus `identical=14 accepted=3
  unsupported=4`, DH corpus `identical=118 unsupported=22`. `accepted.txt` has three entries; when an engine other than
  `taumc` replays, an entry whose glob matches a replayed case but that tolerates no difference is `STALE` and fails the
  replay (`replay: accepted entries in scope=N used=M stale=K`). Engine-neutral code moved out of old-engine classes:
  `AstShaderTransformer.computeCeleritasHeader()`, `transformer/AdaptiveShadowBoundsTransformer.mayInjectRuntimeStats`;
  `Iris` and the corpus recorder call `VersionNegotiation` directly. Named difference: `gl_MultiTexCoord2` in DH
  programs (TauMC left `gl_MultiTexCoord1`; mini-corpus `dh-terrain-multitexcoord2`, accepted). BSL and Complementary
  with Distant Horizons load and render on the new engine; the DH programs and the instrumented rewrite run in game
  (S7 report).
- `TerrainVertexFormatRequirements` (S6) scans identifiers without ANTLR; `TerrainVertexFormatScanParityTest` keeps
  TauMC's lexer as its oracle until Step 11.
- S7b (pre-flip hardening, S7b report): in game the new engine's median per transform equals TauMC's (BSL 20.4 against
  20.7 ms, Complementary Reimagined 67.9 against 69.8 ms, two runs each); before, the programs' parses queued on
  `BUILD_LOCK` (BSL 41.6 against 19.8 ms). With `-Ddemonica.glsmPerfDebug=true` each new-engine transform logs
  `[AstShaderTransformer] <kind> timing totalMs=.. parseMs=.. buildMs=.. lockWaitMs=.. lockHeldMs=.. locks=..
  contended=..` (`ShaderAst.Timing`). `scripts/glsl-corpus/timing.sh <pack> <engine> <tag>` times one pack load
  without the recorder (log `run/timing-<pack>-<engine>-<tag>.out`); `scripts/glsl-corpus/transform-times.py <log>...`
  summarizes any perf-debug log (transforms, median, p90, sum, first 30 against the rest, lock sums). `ShaderAst` takes
  `#extension` lines out of the text before the parse (`ExtensionLines`), so `#extension all : warn`, an `#extension`
  in a function body and one before `#version` transform as with TauMC (a `//` comment on the line ends it at its line
  break, a `/*` inside it included: S7b verification follow-up, `ShaderAstExtensionLinesTest`); `patch` as an identifier at 400 and above still
  throws (TauMC's output was broken GLSL). `gl_MultiTexCoord3` in ATTRIBUTES and CELERITAS_TERRAIN vertex shaders is
  handled as Iris 26.1 does, with one declaration of `mc_midTexCoord` (`transformer/CommonTransformer.patchMultiTexCoord3`).
  `capture.sh` copies only frames the run wrote, removes a missing frame's older copy, and exits 3 when one is missing. The replay's accepted stages add
  `threw` (a recorded output whose replay throws). `accepted.txt` has nine entries. `TransformPatcherTest` compares as
  `GlslTokens` and is green on both engines. Legacy `texture2DRect`/`textureCube`/`texture1D`/`texture2DArray` calls
  stay unrenamed on both engines (open question for the maintainer).
