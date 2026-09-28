# glsl-transformer adoption: status

One row per step of [the adoption plan](ADOPTION_PLAN.md). Every step reads this page and the reports of the steps it
depends on before it starts, and updates its own row when it is done. Branch: `feat/glsl-transformer`, created from
`dev` at `2ed28444`.

| Step | Status | Commit | Date | Report |
|---|---|---|---|---|
| S1 build wiring, license, spike | done | `4104a798`, `f7232573`; report and this page in the commit after | 2026-09-28 | [S01-build-wiring.md](reports/S01-build-wiring.md) |
| S2 corpus recorder, replayer, baselines | not started | | | |
| S3 `ShaderAst` core verbs | not started | | | |
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
  `[TransformPatcher] GLSL transform engine: <id> (demonica.glsl.engine)`. The new engine is
  `shader/.../pipeline/transform/AstShaderTransformer.java`.
- glsl-transformer sources: `run/lib-src/glsl-transformer/` (gitignored; Step 1 unpacked them). Read by class.
- Reference parser configuration: `src/test/java/net/coderbot/iris/pipeline/transform/GlslTransformerSpikeTest.java`.
