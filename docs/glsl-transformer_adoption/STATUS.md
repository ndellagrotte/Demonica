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
| S5 orchestrator, COMPOSITE and COMPUTE | done | `8b7b3489`, the S5 report-and-status commit | 2026-09-28 | [S05-orchestrator-composite.md](reports/S05-orchestrator-composite.md) |
| S6 ATTRIBUTES and CELERITAS_TERRAIN | not started | | | |
| S7 DH and AdaptiveShadowBounds | not started | | | |
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
  is `src/test/resources/transform-corpus/` (16 cases). Replay:
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
- New engine (S5): `AstShaderTransformer` transforms COMPOSITE and COMPUTE (replay: 36/36 pack COMPOSITE cases and 5
  mini-corpus cases identical, 1 accepted); other kinds throw `UnsupportedOperationException("glsl-transformer engine:
  <kind> not ported yet")` at entry (keep the phrase: the replay's "unsupported" depends on it). Port a kind by adding it
  to `AstShaderTransformer.PORTED` and `doTransform`. The ported transformers are
  `shader/.../pipeline/transform/transformer/*` on `ShaderAst`; engine-neutral code: `transform/VersionNegotiation`
  (hoisting, stage minimum, negotiation; `ShaderTransformer.init()`, `versionHoistingState()` and
  `resetVersionHoistingForTesting()` delegate to it) and `transform/CompatibilityPatches` (pack text patches).
  Adaptive shadow bounds is a `TODO(S7)` no-op in `transformer/CommonTransformer`.
- `ShaderAst` (S5): one parser shared by every program, guarded by `BUILD_LOCK` (measured faster than a parser per call
  and than a lock around the whole transform; S5 report). Code that builds nodes through `t`/`tree`/`root` must run
  inside `ast.build(() -> ...)`, which holds the lock and restores the program's lexer version.
  `findQualifiers(...).typeName()` is the type as spelled in the source (`mat2x2` is not `mat2`), as TauMC compared it.
  `extensionDirectives()` gives the `#extension` lines for the header; the header no longer re-emits other directives.
  A source that does not parse throws `ShaderAst.SyntaxException` (TauMC recovered).
- Replay (S5): `-PglslReplayThreads=N` adds a concurrent pass (`replay: concurrent ... differing=0` must stay 0);
  `replay: transformMs` gives the engine time per kind. `accepted.txt` stages are checked (`vertex`, `geometry`,
  `tess_control`, `tess_eval`, `fragment`, `compute`, `compat`, `error`, `*`); `error` accepts a case recorded as a
  TauMC error whose replay differs (report `<case>.error.diff`). `-PglslEngine=taumc|douira` sets
  `demonica.glsl.engine` in the test JVM.
