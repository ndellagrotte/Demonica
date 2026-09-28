# glsl-transformer adoption: status

One row per step of [the adoption plan](ADOPTION_PLAN.md). Every step reads this page and the reports of the steps it
depends on before it starts, and updates its own row when it is done. Branch: `feat/glsl-transformer`, created from
`dev` at `2ed28444`.

| Step | Status | Commit | Date | Report |
|---|---|---|---|---|
| S1 build wiring, license, spike | done | `4104a798`, `f7232573`, `114feb14` (report); verification fix `86e62ef7` | 2026-09-28 | [S01-build-wiring.md](reports/S01-build-wiring.md) |
| S2 corpus recorder, replayer, baselines | done | `ebb9d89b`, `c211a5f6`, `6eeea2da` (report) | 2026-09-28 | [S02-corpus.md](reports/S02-corpus.md) |
| S3 `ShaderAst` core verbs | done | `3e1f5fa9`, `80fda189`, and the report commit | 2026-09-28 | [S03-shaderast-core.md](reports/S03-shaderast-core.md) |
| S4 `ShaderAst` structural verbs | not started | | | |
| S5 orchestrator, COMPOSITE and COMPUTE | not started | | | |
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
- `GlslTokens` also treats `mat2x2` as `mat2` and `((x))` as `(x)` (S3): glsl-transformer prints both that way.
- Baselines (S2): frames in `run/baseline-screenshots/corpus-*.png`; `transformMs` log sums bsl 4,734.4 ms,
  complementary 13,151.2 ms, vanilla 5,703.3 ms (`run/corpus-<pack>.out`, `-Ddemonica.glsmPerfDebug=true`, recorder on,
  default `demonica.openglProfile`).
