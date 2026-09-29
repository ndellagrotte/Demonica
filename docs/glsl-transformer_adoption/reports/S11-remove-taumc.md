# S11: remove TauMC, final layout, release checks (exit point B)

Step 11 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `f00339e3` (S1 to S10 and the
orchestrator's S7b done), on 2026-09-29. Dev-client and Prism log stamps are local time (UTC-4); test XML stamps are UTC.

## Status

**Done. Exit point B is reached:** TauMC's `org.taumc:glsl-transformation-lib`, the old engine and the engine switch are
gone; the jar contains glsl-transformer, ANTLR's runtime and jcpp only; the release-shaped jar loaded glsl-transformer
through Cleanroom's contained deps and rendered BSL in the Prism instance with no TauMC jar on the class path.

| Done when | Evidence |
|---|---|
| `grep -rn "org.taumc.glsl"` over sources, build files and scripts returns nothing | The brief's command, `grep -rn "org.taumc.glsl" --include=*.java --include=*.gradle --include=*.py . \| grep -v '^./.reference' \| grep -v '^./run' \| wc -l`, prints `0` |
| `check` green | `./gradlew check` twice: `run/s11-check-1.out` `BUILD SUCCESSFUL in 15s` (`:test` executed, and `verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath`, `verifyS8tnlibPin` ran); `run/s11-check-2.out` on the committed tree `BUILD SUCCESSFUL in 6s` (the same verify tasks; `:test UP-TO-DATE`). Because `check` can reuse `:test`, `./gradlew :test --rerun` separately (`run/s11-full-test-2.out`): `BUILD SUCCESSFUL in 4s`, from `build/test-results/test/*.xml`: `classes=134 tests=1049 skipped=2 failures=0 errors=0`, test timestamps 05:40:11 to 05:40:14 UTC |
| The jar lists exactly `glsl-transformer-3.0.0-pre3.jar`, `antlr4-runtime-<v>.jar` and `jcpp-1.4.14.jar` as contained deps | `build/libs/Demonica-0.5.0-SNAPSHOT.jar` (SHA-256 `f66d1bd25434ead78a22bc7f5069d63c46ab05f8780c00731e1c319b26729e37`), manifest with its continuation line joined: `ContainedDeps: glsl-transformer-3.0.0-pre3.jar antlr4-runtime-4.13.1.jar jcpp-1.4.14.jar`, `NonModDeps: true`; `unzip -l` lists those three nested jars and no `org/taumc/` entry (0) |
| The sweep, the rejection run and the Prism smoke test pass with baseline-level screenshots | Sweep (`scripts/glsl-corpus/sweep.txt`, twice): all six pack loads `(loaded: true)`, 0 compile failures, frames within the noise of S8's (Measurements, table 3). Rejection run (`gate-rejected.txt`, `pinsOnly=true`): the same rejection, 18 "Not applying" lines as S8, `ShadersUnavailableScreen is open`, no transform ran; its frames differ from S8's only by the spawn point (table 3). Prism: BSL `(loaded: true)`, frames 0.13 % and 0.02 % of pixels from S8's smoke frames |
| The classpath grep shows no old library | Dev (the brief's cp4 run, `run/s11-cp4.out`): `grep -c 'glsl-transformation-lib' run/client/logs/latest.log` `0`, `grep -c 'glsl-transformer-3.0.0-pre3' run/client/logs/latest.log` `0`; the dev `latest.log` never lists the Gradle class path, `debug.log` does: `glsl-transformation-lib` 0, `glsl-transformer-3.0.0-pre3` 6 (`Examining for coremod candidacy glsl-transformer-3.0.0-pre3.jar`, the Gradle cache path). Prism: `glsl-transformation-lib` 0 in `latest.log`, `debug.log` and the parent JVM's `debug-1.log.gz`; `glsl-transformer-3.0.0-pre3` 9 in `debug.log`, 5 in `debug-1.log.gz` (Commands run, "Prism") |
| Report | This page |

## Commits

| Commit | Subject |
|---|---|
| `d5493289` | glsl-transformer: S11 STATUS corrections for S10 |
| `0a3a108a` | glsl-transformer: S11 freeze TauMC's answers as test snapshots |
| `06895328` | glsl-transformer: S11 remove TauMC's library and the old engine |
| the commit that adds this page | glsl-transformer: S11 report and status |

`d5493289` names S10's commits in STATUS (`65b3efed`, `f00339e3`) and corrects the engine-switch bullet:
`TransformPatcher`'s cache key did not include the engine, `CompatShaderTransformer`'s (S10) did.

## What changed

### Final inventory

Deleted, main code (`shader/.../pipeline/transform/`, the TauMC engine): `ShaderTransformer` (old),
`AdaptiveShadowBoundsTransformer`, `AttributeTransformer`, `CeleritasTransformer`, `CommonTransformer`,
`CompatibilityTransformer`, `CompositeDepthTransformer`, `ComputeTransformer`, `CoreTransformHelper`,
`DHGenericTransformer`, `DHTerrainTransformer`, `EntityPatcher`, `TextureTransformer`; in GLSM
`glsm/.../glsm/GlslTransformEngine.java` and `GlslTransformUtils.getFormattedShader` (with its two ANTLR tree imports).
Main code over all: 27 files, 302 insertions, 2,019 deletions (`git diff --shortstat f00339e3 06895328 -- glsm/src/main
shader/src/main src/main`).

Renamed: `transform/AstShaderTransformer` to `transform/ShaderTransformer` (the old class's path; git shows a delete and a
modify); its perf-debug line is now `[ShaderTransformer] <kind> timing ...`. Tests: `ShaderAstParityTest` to
`ShaderAstSnapshotTest`, `AstShaderTransformerTest` to `ShaderTransformerTest`,
`celeritas/vertices/TerrainVertexFormatScanParityTest` to `TerrainVertexFormatScanTest`.

Layout after the step, `shader/src/main/java/net/coderbot/iris/pipeline/transform/`: `TransformPatcher`,
`ShaderTransformer`, `Patch`, `PatchShaderType`, `VersionNegotiation` (kept, as the brief says), `CompatibilityPatches`
(the three regex pack patches, library-free, moved there in S5), `corpus/`, `parameter/`, `transformer/` (the ported
classes; `ShaderAst` is in `glsm` under the same package). This matches Iris 26.1's `transform/{TransformPatcher,
parameter/, transformer/}` apart from those two extra classes and `corpus/`.

Changed, main code:
- `glsm/.../CompatShaderTransformer.java`: the engine holder, `engine()`, the three-argument `transform`, the `Verbs`
  interface and its `TauMcVerbs`/`AstVerbs` adapters and `parseTauMc` are gone; `transformInternal` parses with
  `ShaderAst.parse(source, targetVersion)` and prints with `ast.print(header, targetVersion)`; the four helpers take
  `ShaderAst`. Every verb call and its order is unchanged. The cache key is `(source, isFragment)`. The corpus records
  `TransformCorpus.ENGINE`.
- `glsm/.../debug/TransformCorpus.java`: `ENGINE = "douira"`, the engine name every recording carries.
- `shader/.../TransformPatcher.java`: the engine holder, `ENGINE_PROPERTY`, `engine()` and the two switches are gone; it
  calls `ShaderTransformer` directly and records `TransformCorpus.ENGINE`.
- Javadoc only: `ShaderTransformer` (history of the class), `VersionNegotiation`, `CompatibilityPatches`, `ShaderAst`
  (no `org.taumc.glsl` name left; links to the snapshot test), `transformer/AdaptiveShadowBoundsTransformer`; the
  `transformer/` classes import `ShaderTransformer` for `applyIntelHd4000Workaround`/`addIfNotExists`.

Build, notices, scripts:
- `glsm/build.gradle`: no `glsl-transformation-lib`, `antlr4-runtime:4.13.1`, and no GTNH Nexus repository (it served
  only that library there; the root build keeps its own). `build.gradle`: the `contain` entry for TauMC is gone,
  `antlr4-runtime:4.13.1`; the test block no longer forwards `-PglslEngine` or `-PglslReplayEngine`.
  `verifyDistributedJar`'s forbidden `org/taumc/` rule stays (it also guards against Celeritas classes), and its
  contained-jar expectation follows the `contain` configuration, so it needed no edit.
- `THIRD_PARTY_NOTICES.md`: the glsl-transformation-lib row is gone; the ANTLR row says 4.13.1 (BSD-3-Clause).
  `README.MD` credits keep a "formerly used" line: "embeddedt and Ferri_Arnus for glsl-transformation-lib, which
  Demonica's shader transforms used up to 0.4.0" (the maintainer may drop it). `mcmod.info` credits drop "GLSL
  Transformation Library". `third-party/actinium/THIRD_PARTY_NOTICES.md` (Actinium's own notice, which lists the
  library as Actinium's dependency) is left as it is: it describes Actinium.
- `scripts/test_port_scan.py`: `org.taumc.glsl.` replaced by `io.github.douira.glsl_transformer.` in the list of
  libraries on Demonica's test class path (see Deviations for its exit status).
- `scripts/glsl-corpus/capture.sh`: records only on glsl-transformer, into `run/transform-corpus-douira/<name>/` (log
  `run/corpus-<name>-douira.out`), refuses `GLSL_ENGINE` other than `douira`, and refuses a `GLSL_CORPUS_ROOT` named
  `transform-corpus`, `transform-corpus-dh`, `-taumc` or `-douira`: the TauMC reference corpora can no longer be
  re-recorded, and a plain `capture.sh bsl` would otherwise have deleted `run/transform-corpus/bsl`.
  `timing.sh <pack> <tag>` takes no engine (log `run/timing-<pack>-douira-<tag>.out`). `transform-times.py` reads
  `[ShaderTransformer]` and `[AstShaderTransformer]` timing lines and reports `engine=douira` for logs without an engine
  line. `sweep.txt`: comment.

### The frozen oracles (Do 1 and the orchestrator's note)

Commit `0a3a108a`, while TauMC was still on the class path: `ShaderAstParityTest` and `AstShaderTransformerTest` route
every TauMC answer through a snapshot helper that records it (`-PtaumcSnapshotRecord=true`, forwarded as
`demonica.taumc.snapshots.record`, removed again in `06895328`) or compares the live answer with the file. Record run
(`run/s11-record.out`): `BUILD SUCCESSFUL`, 421 tests, 0 failures. Check run against the files read from the class path
(`run/s11-record-check.out`): `BUILD SUCCESSFUL in 3s`, `AstShaderTransformerTest 20`, `ShaderAstParityTest 400 (1
skipped)`, 0 failures. `changingParity` and `transformGroupedWrittenOnShaderAst` also asserted in both runs that TauMC's
unchanged program equals ShaderAst's for every such case, so the frozen tests can check "the verb changed the program"
against ShaderAst's unchanged print.

The files: `src/test/resources/shader-ast-parity/` (45 files, one per test method, 409 entries: TauMC's printed
programs, query answers as `name = answer` lines, and `!throws <class>: <message>` where TauMC threw) and
`src/test/resources/transform-engine-taumc/` (19 files, 58 whole-engine outputs keyed by method, patch label and a
SHA-256 prefix of the inputs; a recorded throw is rethrown as the same exception type and message). Reader:
`src/test/.../transform/TauMcSnapshots.java`. After the switch, a run that printed every key read showed all 467
entries read and none missing (`run/s11-snapshot-used.out`, with a temporary print that is not committed).

| Deleted or converted test | Where its coverage went |
|---|---|
| `ShaderAstParityTest` (400 tests; 1 the corpus mode) | `ShaderAstSnapshotTest`, 399 tests, the same cases against the frozen TauMC answers |
| `ShaderAstParityTest.corpusDifferential` + `ShaderAstCorpusDifferential` (corpus-only) | Deleted; its last run (`run/s11-last-oracle-corpus.out`, `run/transform-corpus`): `shader-ast-parity: corpus=... inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=28`. End-to-end coverage of the same inputs stays with `TransformCorpusReplayTest` on the pack corpora |
| `AstShaderTransformerTest` (20, compared with the TauMC engine live) | `ShaderTransformerTest`, 20 tests against the 58 frozen engine outputs and one frozen verb sequence |
| `TerrainVertexFormatScanParityTest` (45 + corpus mode) | `TerrainVertexFormatScanTest`, 45 tests, TauMC's counts written into the 22 agreeing rows (`new int[] {...}`, from a one-off dump before the removal), the 22 lexer-error rows and `remainingDifferences` unchanged; the corpus mode's last run: `tvfr-parity: corpus=.../run/transform-corpus vertexOutputs=387 differing=0` |
| `transform/AdaptiveShadowBoundsTransformerTest` (9, TauMC copy) | `transform/transformer/AdaptiveShadowBoundsTransformerTest` (14, S7's twin) |
| `transform/CompatibilityTransformerTest` (6, TauMC copy) | `transform/transformer/CompatibilityTransformerTest` (6, S8's twin) |
| `transform/CeleritasTransformerTest` (3, TauMC copy) | `transform/transformer/CeleritasTransformerTest` (3, S8's twin) |
| `glsm/GlslTransformEngineTest` (4) | Deleted with the switch it tested |

Full `:test`: S10's 1,073 tests (4 skipped) become 1,049 (2 skipped): -1 (parity test to snapshot test, its corpus
mode), -1 (the scan's corpus mode), -18 (the three TauMC copies), -4 (the engine test) = -24; the two skipped tests that
went are the two corpus modes. `CompatibilityTransformerCaveSkyholeTest` and
`GlslCorpusParseSurveyTest` now call `CompatibilityPatches` and `VersionNegotiation` directly. `accepted.txt` names
`ShaderTransformerTest` where it named `AstShaderTransformerTest`; no entry changed.

## Commands run and their outcomes

Every Gradle invocation one at a time; test counts from `build/test-results/test/*.xml`; dev clients one at a time,
default `demonica.openglProfile`; memory notes read first. Another project's Gradle build (a Gradle 9.6.1 daemon and
workers, not this repository's) ran during the dev runs; the load average was 1.5 to 4.9 when sampled.

Brief's Verify:
```
$ grep -rn "org.taumc.glsl" --include=*.java --include=*.gradle --include=*.py . | grep -v '^./.reference' | grep -v '^./run' | wc -l
0
$ ./gradlew check 2>&1 | grep -E 'Tests run|FAILED|error:|BUILD|verify' | tail -30     (run/s11-check-1.out)
> Task :verifyCeleritasPin / :verifyDiagnosticsJar / :verifyDiagnosticsRemap / :verifyDistributedJar /
  :verifyModuleBoundaries / :verifyRunClasspath / :verifyS8tnlibPin
BUILD SUCCESSFUL in 15s
$ unzip -p build/libs/Demonica-*.jar META-INF/MANIFEST.MF | grep ContainedDeps
ContainedDeps: glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar antlr4        <- the glob also matches the stale
                                                                                    Demonica-0.4.0-SNAPSHOT.jar of 2026-09-27
$ unzip -p build/libs/Demonica-0.5.0-SNAPSHOT.jar META-INF/MANIFEST.MF | grep -A1 ContainedDeps
ContainedDeps: glsl-transformer-3.0.0-pre3.jar antlr4-runtime-4.13.1.jar
  jcpp-1.4.14.jar
$ ls run/client/mods/1.12.2/ | grep -i glsl
(nothing; grep exit 1)
$ ./gradlew runClient -PdevScript=@scripts/cp4.txt > run/s11-cp4.out 2>&1; grep -c 'glsl-transformation-lib' run/client/logs/latest.log; grep -c 'glsl-transformer-3.0.0-pre3' run/client/logs/latest.log
0
0            (debug.log of the same run: 0 and 6; BUILD SUCCESSFUL in 1m 5s, five pack switches loaded: true)
```

Replays (the recorded TauMC outputs stay the reference; `-PglslReplayThreads=8`; `run/s11-replay-<corpus>.out`, each
`BUILD SUCCESSFUL`, each `replay: accepted entries in scope=0 used=0 stale=0`):
```
run/transform-corpus          cases=424 identical=424 (byte-identical 0) accepted=0 failing=0 unsupported=0   concurrent cases=387 differing=0
run/transform-corpus-dh       cases=140 identical=140 (byte-identical 0) accepted=0 failing=0 unsupported=0   concurrent cases=118 differing=0
run/transform-corpus-s7-taumc cases=175 identical=175 (byte-identical 0) accepted=0 failing=0 unsupported=0   concurrent cases=170 differing=0
mini-corpus (every :test)     cases=39 identical=30 (byte-identical 0) accepted=9 failing=0 unsupported=0, accepted entries in scope=9 used=9 stale=0
```
S8 had `unsupported=37`, `22` and `5` for the compat cases; they replay on glsl-transformer since S10 and are identical.

ANTLR (`./gradlew dependencyInsight --dependency antlr4-runtime --configuration contain`, the same for
`:glsm:runtimeClasspath` and the root `runtimeClasspath`): `org.antlr:antlr4-runtime:4.13.1` in all three.
`run/lib-src/glsl-transformer/io/github/douira/glsl_transformer/GLSLParser.java:17` and `GLSLLexer.java:17`:
`RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION)`; glsl-transformer's POM names
`antlr4-runtime` 4.13.1.

`scripts/test_port_scan.py` (`run/s11-test-port-scan.out`): exit 1 after the edit, exit 0 before
(`run/s11-test-port-scan-before.out`); see Deviations.

Dev clients (every one `BUILD SUCCESSFUL`, exited through its script; 19 "injectors found their targets" lines in each
accepted-Celeritas run; 0 `Shader compilation failed`, `Failed to compile`, `ShaderAst$SyntaxException`,
`ClassNotFoundException`, `NoClassDefFoundError`, `LinkageError`):

| Run | Log | What it shows |
|---|---|---|
| sweep (`-PdevProps=demonica.glsmPerfDebug=true`), twice | `run/s11-sweep.out` (1m 19s), `run/s11-sweep-2.out` | three packs switched mid-game, then each again from the cache; 6 of 6 loads `(loaded: true)` in each; frames `run/engine-screenshots/s11-sweep{,-2}/`; copies of the first run's `latest.log`/`debug.log` in `run/s11-sweep-{latest,debug}.log` |
| `timing.sh <pack> s11` and `s11b` for bsl, complementary, vanilla | `run/timing-<pack>-douira-s11{,b}.out` | first loads, S2-comparable frames `run/engine-screenshots/s11{,b}-douira/` |
| `gate-rejected.txt`, `-PdevProps=demonica.celeritas.pinsOnly=true` | `run/s11-gate.out` (30 s) | `[DemonicaQuarantine]: celeritas-forge-mc12.2-2.4.0-autobuild.06999aab.jar is not the Celeritas build this Demonica was made for ...`; 18 `Not applying` lines (S8: 18); `GLStateManagerFogServiceMixin ... 7 of 7 injectors`; `Dev screen: ShadersUnavailableScreen is open`; `Dev shader pack: not switched to BSL_v10.1.8.zip, Iris is off`; no `ShaderTransformer`/`TransformPatcher` line: no transform runs on that path |
| brief's `cp4.txt` | `run/s11-cp4.out` | the classpath greps above; copies `run/s11-cp4-{latest,debug}.log` |

Stale extracted copies (Do 5): `run/client/mods/1.12.2/glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar` was moved
to `run/s11-stale-mods/` before the first dev run. Before, S10's last dev run showed `FML has found a non-mod file
glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar in your mods directory. It will now be injected into your classpath.`
in `latest.log`. `run/client/mods/1.12.2/antlr4-runtime-4.13.2.jar` (2026-09-23) was left: the dev class path now
examines both `antlr4-runtime-4.13.2.jar` (that file) and `antlr4-runtime-4.13.1.jar` (Gradle), `run/s11-cp4-debug.log`
lines 39 and 69 (Open questions).

**Prism** (production-shaped smoke test; memory note followed; the instance is approved for smoke tests of the mod jar):
- Before: `relauncher.json`, `demonica-options.json`, `shaders.properties` and `forge_early.cfg` copied to
  `minecraft/smoke-backup-glsl-transformer-s11/orig/`, with listings of `mods/`, `mods/1.12.2/`, `saves/` and the
  SHA-256 of the jars; there was no `options.txt`. The instance's `Demonica-0.4.0.jar` lists `ContainedDeps:
  glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar antlr4-runtime-4.13.2.jar jcpp-1.4.14.jar`, so Cleanroom
  re-extracts the TauMC jar the next time 0.4.0 launches: removing it is safe for the 0.4.0 jars.
- Set-up: `Demonica-0.4.0.jar` and `Demonica-diagnostics-0.4.0.jar` moved aside; this build's
  `Demonica-0.5.0-SNAPSHOT.jar` (`f66d1bd2...`) and `Demonica-diagnostics-0.5.0-SNAPSHOT.jar` (`cd84e863...`) copied
  in; `mods/1.12.2/glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar` (`875f9214...`) moved to the backup's
  `stale/`; `scripts/smoke-glsl-s11.txt` (S8's smoke script with the world `gts11` and frames `gt-s11-*`: `pack off`, a
  new world, a frame, BSL, a frame, `pack off`, `exit`); `relauncher.json` args `-XX:+UseCompactObjectHeaders
  -Ddemonica.glsmPerfDebug=true -Ddemonica.dev.script=@<instance>/scripts/smoke-glsl-s11.txt`; `options.txt` with only
  `pauseOnLostFocus:false`.
- Launch: `flatpak run org.prismlauncher.PrismLauncher --launch prod-smoke-test`, 01:34:35 to 01:35:17, `exit=0` (Prism
  exited by itself).
- Parent (Forge) JVM, `logs/debug-1.log.gz`: `[01:34:44] [main/DEBUG] [FML]: Extracting ContainedDep
  glsl-transformer-3.0.0-pre3.jar from .../mods/Demonica-0.5.0-SNAPSHOT.jar ...`, `Extracted ContainedDep
  glsl-transformer-3.0.0-pre3.jar ...`, the same for `antlr4-runtime-4.13.1.jar`. The extracted files' SHA-256 equal the
  Gradle cache's: `0d55650f...` (glsl-transformer) and `54665d28...` (ANTLR 4.13.1).
- Cleanroom JVM, `logs/debug.log`: `Found existing ContainDep extracted to .../mods/1.12.2/glsl-transformer-3.0.0-pre3.jar,
  skipping extraction`, `Adding glsl-transformer-3.0.0-pre3.jar to the mod list`, `Adding antlr4-runtime-4.13.1.jar to
  the mod list`, and also `Adding antlr4-runtime-4.13.2.jar to the mod list` (0.4.0's extracted copy).
  `glsl-transformation-lib`: 0 lines in `latest.log`, `debug.log` and `debug-1.log.gz`. (S8's smoke run, whose logs
  rotated to `debug-2.log.gz`, shows `Found existing ContainDep extracted to .../glsl-transformation-lib-...jar` and
  `Adding glsl-transformation-lib-...jar to the mod list` at 23:58:43: the stale copy was on S8's class path, harmless
  then because the mod jar still contained TauMC.)
- `logs/latest.log`: `DemonicaQuarantine: 01-celeritas-forge-mc12.2-2.4.0-dev.jar is the Celeritas build this Demonica
  was made for (upstream 06999aab, SHA-256 4dd4b35d)`; `[01:35:10] Dev shader pack: BSL_v10.1.8.zip (loaded: true)`;
  `Dev stats: 60 fps; terrain C: 344/7056 D: 12 A: 4; shadow sections 504`; `Dev script finished; shutting down`. 0
  `ClassNotFoundException`, `NoClassDefFoundError`, `LinkageError`, `Shader compilation failed`, `Failed to compile`,
  `ShaderAst$SyntaxException` in `latest.log` and `debug.log`; the one error line is the known `CleanMix: Invalid REFMAP
  JSON` (memory note). 19 "injectors found their targets" lines. 107 `[ShaderTransformer] ... timing` lines, no engine
  line (the switch is gone). The BSL frame (`gt-s11-1-bsl.png`, 854x480) shows BSL with its clouds and terrain shadows.
- Afterwards: both jars, the script (and its used copy), the used and after-run `relauncher.json`, `options.txt`,
  `demonica-options.json` and `shaders.properties`, the logs (`latest.log`, `debug.log`, `debug-1.log.gz`,
  `cleanmix.log`), both frames, the world `saves/gts11`, the extracted `glsl-transformer-3.0.0-pre3.jar` and
  `antlr4-runtime-4.13.1.jar` (with `sha256.txt`) and the stale TauMC jar are in
  `minecraft/smoke-backup-glsl-transformer-s11/`. The instance is back on `Demonica-0.4.0.jar` (`a9f6f478...`) and
  `Demonica-diagnostics-0.4.0.jar` (`ca8ce836...`); `relauncher.json`, `demonica-options.json` and
  `shaders.properties` are the originals again (`cmp`), `forge_early.cfg` was not changed (`cmp`), there is no
  `options.txt`, `mods/` and `saves/` list as before, and `mods/1.12.2/` holds what it held before except the TauMC jar.
  The run had added `"opengl_profile": "AUTO"` to `demonica-options.json` and rewritten the date in
  `shaders.properties`; both were restored.

## Measurements

### Table 1: first pack load, transform time per pack

`[ShaderTransformCache] ... miss transformMs=` per transform, the pack's corpus script (`timing.sh`),
`-Ddemonica.glsmPerfDebug=true`, pooled over the runs of a row. Earlier rows from the S8 report.

| Pack | Engine, runs | Transforms | Median ms | p90 ms | Sum ms per run |
|---|---|---|---|---|---|
| BSL | TauMC, S2 (recorder) | 170 | 21.4 | 55.4 | 4,720.2 |
| BSL | TauMC, S8 | 170 | 19.2 | 59.0 | 4,658.2 |
| BSL | glsl-transformer, S8 | 170 | 17.7 | 53.5 | 4,032.1 |
| BSL | **glsl-transformer, S11 x2** | 170 | **23.9** | 54.7 | 4,759.7 (4,241.7; 5,277.7) |
| Complementary | TauMC, S2 (recorder) | 115 | 77.8 | 230.6 | 13,082.5 |
| Complementary | TauMC, S8 | 115 | 73.8 | 174.6 | 9,917.1 |
| Complementary | glsl-transformer, S8 | 115 | 76.5 | 143.4 | 9,722.8 |
| Complementary | **glsl-transformer, S11 x2** | 115 | **84.6** | 165.0 | 10,250.6 (10,521.5; 9,979.6) |
| I Like Vanilla | TauMC, S2 (recorder) | 100 | 41.5 | 68.3 | 5,190.2 |
| I Like Vanilla | TauMC, S8 x2 | 100 | 41.6 | 66.5 | 5,125.6 |
| I Like Vanilla | glsl-transformer, S8 x2 | 100 | 46.3 | 69.7 | 5,218.8 |
| I Like Vanilla | **glsl-transformer, S11 x2** | 100 | **43.4** | 73.6 | 5,078.6 (5,033.0; 5,124.2) |

Against the S2 baseline: BSL median 1.12x, sum 1.01x; Complementary 1.09x and 0.78x; I Like Vanilla 1.05x and 0.98x.
The single runs spread by up to 33 % in the median (BSL 20.6 against 27.5 ms), with another project's build on the
machine; no run approaches "about twice as slow". Where the time goes (`[ShaderTransformer] ... timing` sums, first
run): BSL parse 365.7, AST build 250.8, lock wait 1,577.1, lock held 338.0 ms, `locks=10645 contended=2418`;
Complementary 1,031.1 / 676.2 / 1,988.6 / 728.0; I Like Vanilla 626.4 / 347.1 / 1,521.9 / 393.1.

### Table 2: the sweep, first load and cached reload

`scripts/glsl-corpus/sweep.txt`, `transform-times.py --phases`, S11's two runs against S8's one per engine.

| Phase | S11 run 1: transforms / hits, median / p90 / sum ms | S11 run 2 | S8 glsl-transformer | S8 TauMC |
|---|---|---|---|---|
| warm-up | 2 / 0, 11.3 / 15.5 / 22.6 | 2 / 0, 8.2 / 10.6 / 16.4 | 36.6 / 60.5 / 73.1 | 8.6 / 10.8 / 17.2 |
| first BSL | 104 / 3, 36.1 / 68.5 / 3,704.1 | 104 / 3, 32.8 / 60.8 / 3,304.2 | 27.2 / 61.1 / 3,158.5 | 25.7 / 61.8 / 3,155.9 |
| first Complementary | 113 / 2, 56.6 / 158.5 / 8,432.9 | 113 / 2, 51.9 / 121.9 / 7,387.9 | 53.8 / 128.7 / 7,213.2 | 53.4 / 144.9 / 8,466.8 |
| first I Like Vanilla | 98 / 3, 72.3 / 123.5 / 7,598.5 | 98 / 3, 37.1 / 52.2 / 3,731.3 | 28.5 / 40.0 / 2,891.1 | 29.5 / 42.9 / 3,098.9 |
| cached BSL | 0 / 108 | 0 / 108 | 0 / 108 | 0 / 108 |
| cached Complementary | 0 / 116 | 0 / 116 | 0 / 116 | 0 / 116 |
| cached I Like Vanilla | 0 / 110 | 0 / 110 | 0 / 110 | 0 / 110 |

A cached reload transforms nothing (every program is a cache hit), as in S8. The first run's I Like Vanilla phase
(72.3 ms median) did not repeat in the second (37.1 ms); both ran while the other project's build used the machine.
The warm-up (two trivial programs) is back at TauMC's level (8 to 11 ms against S8's 36.6 ms); the old engine's
classes are no longer loaded next to glsl-transformer's, which may be why, but no run separates the causes.

Prism smoke run (854x480, 60 fps cap): 106 transforms, median 30.2, p90 55.0, sum 3,339.4 ms (S8: 28.8 / 55.6 /
3,490.1).

### Table 3: frames

numpy over ffmpeg's rgb24 decode; "pixels" is the share of pixels with a channel differing by more than 16/255
(`run/s11-imgdiff-{sweep,timing,gate}.txt`). Baseline = `run/baseline-screenshots/` (S2, TauMC).

| Frame | S11 against | Pixels | Note |
|---|---|---|---|
| BSL no pack / pack / Nether / overworld (timing run 1) | S2 baseline | 0.01 % / 0.11 % / 2.32 % / 0.45 % | S8 new: 0.00 / 0.03 / 2.39 / 0.50 %; S8 TauMC Nether 2.60 % |
| BSL pack (timing run 2) | S2 baseline | 0.06 % | |
| Complementary no pack / pack (run 1), pack (run 2) | S2 baseline | 0.02 % / 0.08 % / 0.01 % | |
| I Like Vanilla no pack / pack (run 1), pack (run 2) | S2 baseline | 0.01 % / **0.41 %** / **0.27 %** | S8 new 1.75 %, S8 TauMC 0.42 %, TauMC floor 0.97 % (S6) |
| I Like Vanilla pack (run 1) | S8's TauMC frame / S8's new frame | 0.03 % / 1.56 % | |
| sweep 0 to 7 (run 1) | S8 sweep new / TauMC | nopack 0.01/0.01, BSL 0.07/0.09, Compl. 0.06/0.01, ILV 0.93/0.79, off 0.01/0.01, cached BSL 0.04/0.04, cached Compl. 0.02/0.01, cached ILV 0.25/0.59 % | S8 new against S8 TauMC ILV: 0.21 % |
| sweep BSL / Compl. / ILV (run 2) | S8 sweep new | 0.08 / 0.03 / 0.27 % (ILV against S8 TauMC 0.23 %) | |
| gate 0 / 1 / 2 | S8 gate frames | 37.48 / 5.59 / 37.60 % | camera offset, not rendering: the chat lines in the frames read `Teleported Developer to -160.5, 99.83, 221.5` (S11) against `-164.5, 100.83, 218.5` (S8); the script teleports relative to a spawn point that moved. Same scene, same screen text. Iris is off on this path |
| Prism no pack / BSL | S8 smoke frames | 0.02 % / 0.13 % | |

**S8's I Like Vanilla edge jitter did not repeat:** both S11 pack frames are 0.41 % and 0.27 % from the S2 baseline,
below the TauMC floor, and the first is 0.03 % from S8's TauMC frame. S8's new-engine frame is 1.56 % from S11's and
1.75 % from the baseline, so S8's frame was the outlier, as S8 concluded.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| 424 pack cases, 140 DH-corpus cases, 175 cases of S7's TauMC BSL corpus (compat included) | all | identical (tokens) | none |
| mini-corpus: 30 cases | all | identical | none |
| mini-corpus: the nine `accepted.txt` entries | as listed | reviewed in S8; unchanged | kept |
| gate frames | frame | camera offset from the spawn point | none |

## Deviations from the brief

1. Do 1 names only `ShaderAstParityTest`; the orchestrator's note also asked to freeze `TerrainVertexFormatScanParityTest`
   and every other oracle test. `AstShaderTransformerTest` compared with the live TauMC engine in 19 of its 20 tests,
   so its TauMC outputs were frozen too (`transform-engine-taumc/`), and it was renamed with the class it tests. The
   two corpus modes (`corpusDifferential`, `corpusVertexOutputs`) could not be frozen (the corpora are local and not
   committed); their last results are recorded above.
2. The snapshot format is one text file per test method with `==== <key>` entries, not one file per case; the record
   mode was a temporary Gradle property in the freeze commit.
3. `scripts/test_port_scan.py`: the brief says remove the `org.taumc.glsl.` entry; done, and glsl-transformer's package
   added, since tests on Demonica's class path import it. The script (historical, not part of any build task) now
   exits 1 with seven `MISMATCH: audit scope covers but scan skips` lines: Actinium's TauMC-based tests
   (`CompatShaderTransformerTest`, `VertexShaderGeneratorTest`, the three TauMC transformer tests) and the two
   `betterportals_render_portal` resources are no longer portable verbatim, while `provenance_audit.py`'s tests scope
   still lists them. Before the edit it exited 0. Open question 3.
4. The brief's `unzip -p build/libs/Demonica-*.jar ...` matches every jar in `build/libs/`, including a stale
   `Demonica-0.4.0-SNAPSHOT.jar` (2026-09-27), whose manifest names TauMC; the evidence above reads the 0.5.0 jar by
   name. The stale jar was left in `build/libs/`.
5. The brief's classpath grep reads `run/client/logs/latest.log`; a dev run lists the Gradle class path only in
   `debug.log`, so both counts are 0 in `latest.log` and the evidence is `debug.log` (and the Prism logs, which show
   the contained-deps extraction).
6. The sweep is `scripts/glsl-corpus/sweep.txt` (S8's comparable cp4 shape, the orchestrator's choice); the brief's
   literal `cp4.txt` run was made too, for its Verify line. Two sweeps and two timing rounds instead of one, because
   the machine was shared with another project's build.
7. Extra: the ANTLR runtime is pinned (the orchestrator's instruction, the brief's recommendation); `capture.sh`,
   `timing.sh` and `transform-times.py` changed with the engine (not listed in the brief); the stale TauMC jars were
   moved to backups (`run/s11-stale-mods/`, the instance's `smoke-backup-glsl-transformer-s11/stale/`) rather than
   deleted outright.
8. Test runs use `--rerun`; the commit trailer names Claude Opus 5.5 (the orchestrator's rules).

## Open questions

1. A stale `antlr4-runtime-4.13.2.jar` stays in `mods/1.12.2/` next to 4.13.1 wherever 0.4.0 (or an older build) ran:
   the Prism smoke run added both to the mod list, and the dev client examines both. Same major.minor, so ANTLR prints
   no version warning and nothing failed, but which runtime wins depends on class-path order. Players upgrading from
   0.4.0 will have the same pair, plus `glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar`, which FML keeps injecting
   (unused, harmless). Mention in the 0.5.0 release notes that `mods/1.12.2/glsl-transformation-lib-*.jar` and
   `antlr4-runtime-4.13.2.jar` can be deleted? Delete `run/client/mods/1.12.2/antlr4-runtime-4.13.2.jar`?
2. `README.MD` keeps a "formerly used" credit for glsl-transformation-lib (the brief leaves it to the maintainer).
3. `scripts/test_port_scan.py` exits 1 now (Deviations 3). Keep TauMC's package in its library list as a historical
   fact of the fork-time class path, update `provenance_audit.py`'s tests scope, or leave it?
4. `CompatibilityPatches` stays in `transform/` (library-free, next to `VersionNegotiation`); Iris 26.1 keeps its pack
   patches in `transformer/CompatibilityTransformer`. Move it there for the layout, in Step 12?
5. `docs/SCOPE_RESEARCH.md` item F (the SPIR-V path) was done at `df08c785`; with this step the TauMC library that path
   and the old engine used is gone too, so item F is done as far as this plan reaches. That page was not edited.
6. Follow-ups for the maintainer: the release version bump (0.5.0; the first release without TauMC and under
   AGPL-3.0), the GitHub repository's license metadata (AGPL-3.0), the release notes' jar list (glsl-transformer,
   ANTLR 4.13.1, jcpp) and the S8TNLib line the memory notes require, and S8's, S9's and S10's open questions, which
   stand.

## Notes for the next step

- One engine: `transform/ShaderTransformer` (glsl-transformer). `TransformPatcher` calls it directly; GLSM's
  `CompatShaderTransformer` calls `ShaderAst` directly. No `demonica.glsl.engine`, `-PglslEngine` or
  `-PglslReplayEngine`.
- Replay: `./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=<abs> [-PglslReplayPatches=...]
  [-PglslReplayThreads=N] [-PglslReplayRecord=true]`, then `grep -E 'replay|BUILD'`. The reference is always the
  recorded `out.taumc.*`; record mode writes `out.douira.*` (into an explicit `-PglslCorpusDir` only). Every recorded
  corpus replays identically today (Commands run).
- Step 12's replays: the brief's commands (mini-corpus, `run/transform-corpus`, the transform tests) work as they are;
  drop `-PglslReplayEngine` from any older command.
- TauMC's answers are frozen and cannot be re-recorded: `ShaderAstSnapshotTest`/`ShaderTransformerTest` read
  `src/test/resources/shader-ast-parity/` and `transform-engine-taumc/` through `TauMcSnapshots` (keyed by test method
  and case name, or by a hash of the inputs). A behaviour change that makes a case differ from TauMC (Step 12's port,
  the reserved-word experiment) must change the test's assertion into a deliberate deviation, not edit the snapshot.
- `capture.sh <pack>` records the current engine into `run/transform-corpus-douira/<pack>/`; `timing.sh <pack> <tag>`.
- Stale extracted jars: FML injects every jar under `mods/1.12.2/`; check `debug.log` (dev) or the Prism logs for what
  was on the class path.
- The Prism instance `prod-smoke-test` is on the 0.4.0 jars; S11's files are in its
  `minecraft/smoke-backup-glsl-transformer-s11/`.
