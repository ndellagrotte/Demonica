# Iris porting plan: item reports

The per-item reports of [IRIS_PORTING_PLAN.md](../../IRIS_PORTING_PLAN.md), copied from the gitignored
`run/iris-plan/` working directory on 2026-10-11 when the plan was closed out. Each report was written by the agent
that landed the item and checked by an independent verifier (the `iris-plan-item` workflow); the plan's "As landed"
bullets summarise them. Absolute paths under the user's home are written `<repo>/`.

| Item | Report |
|---|---|
| 3.1 `replaceGlMultiTexCoordBounded` + 3.4 `gl_TextureMatrix[2]` | [3.1-multitexcoord-bounded-report.md](3.1-multitexcoord-bounded-report.md) |
| 3.2 `gtexture` two-declaration merge | [3.2-gtexture-merge-report.md](3.2-gtexture-merge-report.md) |
| 3.3 DH transformer deltas | [3.3-dh-transformer-deltas-report.md](3.3-dh-transformer-deltas-report.md) |
| 3.3 open check 1: DH 3.3.0's own vertex shader | [dh-shader-check.md](dh-shader-check.md) |
| 4.1 `IrisShadowRenderCallback` | [4.1-shadow-render-callback-report.md](4.1-shadow-render-callback-report.md) |
| 4.2 `ShaderCompileException` | [4.2-shader-compile-exception-report.md](4.2-shader-compile-exception-report.md) |
| 4.3 IncludeGraph "did you mean" | [4.3-include-did-you-mean-report.md](4.3-include-did-you-mean-report.md) |

Lanes A, B and C (items 0.1-0.5, 1.1-1.6, 2.1-2.5) landed before the report convention; their record is the plan's
"As landed" bullets, the section 11 answers and the PR descriptions (#3, #8, #10, #15-#17). The frames, logs and
scratch files the reports cite stay in `run/iris-plan/` and are not in the repository.
