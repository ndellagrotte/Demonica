# GLSM passthrough plan: item reports

The per-item reports of [GLSM_PASSTHROUGH_PLAN.md](../../GLSM_PASSTHROUGH_PLAN.md). Each report records the commit it
was taken on, the commands, and what its verify step found. Absolute paths under the user's home are written
`<repo>/`.

| Item | Report |
|---|---|
| A.0 Baseline capture (§4 "Baseline first") | [A.0-baseline.md](A.0-baseline.md) |

The frames, logs, timing output and triage notes the reports cite live in the gitignored
`run/glsm-passthrough/baseline/` (with the capture helpers in `run/glsm-passthrough/`) and are not in the repository.
The A.0 report records the commit and the sha256 of every baseline frame, log and input script, so a later item can
check with `sha256sum -c SHA256SUMS` that it compares against the same reference. The run directories' SHA256SUMS cover
frames, `latest.log` and `client.out` or the timing log, and, appended on 2026-10-22, the crash reports, the perf
summaries and the StellarCore config copies (listed in the report's appendix A.3). Each V-mods scene was captured twice
(`mods-<scene>` and `mods-<scene>-B`); the three whose two runs started at different spawn points (ocscreen, itemgui,
hudcache-stellarcore) were retaken (`-C`, `-D`) until a run matched one of them. A later item compares a mods scene with
its two runs of record, after checking that its run started at the same spawn point (A.0 report, sections 2 and 5.5).
