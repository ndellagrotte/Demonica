# S10: `CompatShaderTransformer` on `ShaderAst`

Step 10 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `f61cbab6` (S1 to S9 and the
orchestrator's S7b done; exit point A reached), on 2026-09-29. Times are local (the dev-run log stamps).

## Status

**Done.** Every "Done when" item holds.

| Done when | Evidence |
|---|---|
| `CompatShaderTransformerTest` green on both engines | `./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.CompatShaderTransformerTest' --tests 'com.gtnewhorizons.angelica.glsm.GlslTransformEngineTest' -PglslEngine=<e> --rerun`: douira `BUILD SUCCESSFUL in 2s`, test XML `CompatShaderTransformerTest tests="21" skipped="0" failures="0" errors="0"`, log `[CompatShaderTransformer] GLSL transform engine: douira`; taumc the same counts, log `... engine: taumc` (`run/s10-tests-5-{douira,taumc}.out`). After the last edit (a javadoc line) the default engine again: `tests="21" ... failures="0"` (`run/s10-cst-final.out`) |
| Compat replay clean | Every compat case of every recorded corpus on `douira` (`-PglslReplayPatches=COMPAT`): 121 cases, all identical, `accepted=0 failing=0 unsupported=0` (table under "Commands run"). The brief's command on `run/transform-corpus/compat`: `cases=24 identical=24 ... failing=0 unsupported=0`. Mini-corpus (every `:test`): `cases=39 identical=30 accepted=9 failing=0 unsupported=0`, `COMPAT {IDENTICAL=11}` |
| Compat dev run shows no fallback warnings the TauMC run did not also show | `run/s10-compat.out` (compat script, default engine, `-PwithCompatMods`, `angelica.dumpShaders=true`): the brief's grep counts `2`, both unrelated (`Could not load mappings from classpath, falling back to checking packages`, oshi's `falling back to sysfs`); S8's and S2's TauMC compat runs show the same two lines and count `2`. `AST transformation failed` 0, compile failures 0. The same holds for the three compat+BSL runs (table under "Measurements") |
| Report | This page |

The orchestrator's additions: the engine is resolved through one helper that `TransformPatcher` also uses (done, not
duplicated); the GLSM tests (`com.gtnewhorizons.angelica.glsm.*`) are green on both engines (`classes=36 tests=198
skipped=0 failures=0 errors=0` each, `run/s10-glsm-{douira,taumc}.out`); a second compat run loads BSL (done, on both
engines, frames compared).

## Commits

| Commit | Subject |
|---|---|
| `65b3efed` | glsl-transformer: S10 CompatShaderTransformer on ShaderAst |
| the commit that adds this page | glsl-transformer: S10 report and status |

## What changed

Added:
- `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GlslTransformEngine.java`: the enum `TAUMC`/`DOUIRA` with
  `PROPERTY` (`demonica.glsl.engine`), `DEFAULT` (`DOUIRA`), `byId`, `resolve(value, unknownValue)` and
  `fromSystemProperty(unknownValue)`. The value is trimmed and lower-cased; null gives the default; an unknown value
  (the empty string included) is handed to the caller's callback, which logs it, and gives the default. It sits in
  `glsm` because `shader` depends on `glsm`, never the reverse (`verifyModuleBoundaries` and `DependencyDirectionTest`
  pass).
- `src/test/java/com/gtnewhorizons/angelica/glsm/GlslTransformEngineTest.java` (4 tests): the default is `douira`;
  resolution; the unknown-value fallback; `TransformPatcher.engine()` and `CompatShaderTransformer.engine()` are the same
  object in one JVM.
- `src/test/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAstLegacyPrintTest.java` (3 tests): the
  sub-130 print (below).
- Seven hand-written compat cases in the mini-corpus, `src/test/resources/transform-corpus/compat-*`, with their
  `out.taumc.glsl` recorded by the replay's record mode on `taumc` in a scratch copy and copied in (the S7b way):
  `compat-lighting-fragment` (`gl_LightSource`, `gl_FrontMaterial`, `gl_FrontLightModelProduct.sceneColor`, `gl_Fog`,
  `gl_FogFragCoord`, `gl_FragData[0]`/`[1]`, `gl_TexCoord[0]`/`[2]`), `compat-matrices-vertex`
  (`gl_ModelViewProjectionMatrix`, `gl_TextureMatrix[0]`, `[1]` and `[unit]`, `gl_NormalMatrix`, the inverse matrices,
  `gl_Normal`, `gl_MultiTexCoord0/1`, `gl_FrontColor`), `compat-shadow-fragment` (all six `renameAndWrapShadow` names
  with swizzles, `texture2DLod`/`Proj`, `texture3D`, the pre-parse renames of `textureCube` and `texture2DRect`, a
  sampler named `texture`, `sample` and `new` as identifiers, an `#extension` in the preamble),
  `compat-shadow-nested` (the nested-shadow quirk, below), `compat-gles-conditionals-fragment` (no `#version`, a
  `GL_ES` precision guard, a `#if` that selects GLSL tokens and is evaluated before the parse, a function-like macro,
  `main(void)`), `compat-400-compatibility-fragment` (`sampler` as an identifier, renamed from 400 on) and
  `compat-texture-variable-vertex` (`texture` → `gtexture`, `ftransform`). The mini-corpus now has 39 cases, 11 of them
  compat.
- `scripts/glsl-corpus/compat-bsl.txt`: `compat.txt`'s world and camera, a no-pack frame, then BSL, a pack frame and
  the creative inventory under the pack.

Changed:
- `glsm/.../glsm/CompatShaderTransformer.java`:
  - `engine()`: resolved once per JVM through `GlslTransformEngine.fromSystemProperty` in a holder class and logged
    once, `[CompatShaderTransformer] GLSL transform engine: <id> (demonica.glsl.engine)`; an unknown value logs
    `[CompatShaderTransformer] Unknown GLSL transform engine '<value>' in demonica.glsl.engine; using douira`.
  - `transform(source, isFragment)` delegates to the new public `transform(source, isFragment, engine)`, which the
    replay uses to pick the engine per call. The cache key includes the engine. The corpus recorder gets the engine's
    id instead of the constant `"taumc"`.
  - `transformInternal`: the block from `ShaderParser.parseShader` to the `mutateTree` serialization is replaced. The
    verb calls are written once, against a private interface `Verbs` (the 12 TauMC verbs this class calls plus
    `print(header)`), with two adapters: `TauMcVerbs` (TauMC's `Transformer`; its `print` is the old
    `mutateTree` + `GlslTransformUtils.getFormattedShader`) and `AstVerbs` (`ShaderAst`; `print(header, version)`).
    `parseTauMc` keeps TauMC's syntax-error check word for word; the douira branch is
    `ShaderAst.parse(source, targetVersion)`. Every verb call and its order is unchanged (`injectVariable` x23,
    `rename` x14 including `MATRIX_RENAMES`, `replaceExpression` x7, `injectFunction` x6, `renameAndWrapShadow` x6,
    `renameArray` x2, `prependMain` x2, `containsCall` x2, `hasVariable` x2, `appendMain`,
    `renameFunctionCall(TEXTURE_RENAMES)`); only the four helper methods' parameter type changed. Everything before the
    parse (precision guards, `separatePreprocessorPreamble` with its evaluator, `main(void)`, `replaceTexture`,
    `renameParseBreakingTextureFunctions`, `renameReservedWords`) and after the print (`restoreReservedWords`,
    `fixupQualifiers`, the dump) is untouched. The existing `catch (Exception e)` turns a `ShaderAst.SyntaxException`
    into the version fix-up, so the fail-fast behaviour stays (test `syntacticallyInvalidShaderFallsBackToVersionFixup`
    on both engines; the douira log shows `ShaderAst$SyntaxException: line 3:0 missing ';' at '}'`).
  - The class javadoc names the switch.
- `glsm/.../transformer/ShaderAst.java`: `print(String header, int version)`. From 130 on it is `print(header)`;
  below, a private `ASTPrinter` subclass (`UnsuffixedFloatPrinter`, `PrintType.INDENTED`'s token processor) prints
  `FLOAT32` literals as `Double.toString(value)` without the `f`. `print(header)`'s directive removal moved into a
  private `removeHeaderDirectives()`.
- `shader/.../transform/TransformPatcher.java`: the nested `Engine` enum is gone; `engine()` returns
  `GlslTransformEngine`, resolved through the same helper; `ENGINE_PROPERTY` is `GlslTransformEngine.PROPERTY`. The log
  lines are unchanged. No other main code named `TransformPatcher.Engine`.
- `src/test/.../glsm/CompatShaderTransformerTest.java`: no TauMC import. `countTokens(..., PRECISION)` is
  `GlslTokens.of(t).count("precision")`; the directive-token counts are `directives(t, "ifdef")` etc. (the
  `GlslTokens` preprocessor tokens, comments stripped, matched by `^#\s*(\w+)`); `MACRO_ESC_NEWLINE` is
  `lineContinuations(t)` (backslash before a line break); the "0 syntax errors" pairs are `assertParses(t)`
  (`ShaderAst.parse` does not throw); `hasIdentifier` is `GlslTokens.contains`. The exact-string assertions on the
  fallback paths stay. New test `preprocessorDirectivesInTheParsedTextAreDroppedNotFatal`: every directive kind in the
  parsed text (13 lines: `#define`, `#undef`, `#if`, `#elif`, `#else`, `#endif`, `#ifdef`, `#ifndef`, `#line`,
  `#pragma`, the empty `#`) is dropped and listed, never thrown on. 21 tests (20 before).
- `src/test/.../transform/TransformCorpusReplayTest.java`: compat cases are no longer unsupported on `douira`; they
  run through `CompatShaderTransformer.transform(input, isFragment, GlslTransformEngine.byId(engine))`.
- `glsm/.../glsm/GlslTransformUtils.java`: the deprecation note names `CompatShaderTransformer` "on the `taumc`
  engine".

Outside git: `run/s10-*.out` (every command below); `run/transform-corpus-s10-douira/compat/` (24 cases recorded in
game on the new engine); frames `run/engine-screenshots/s10-compat/`, `s10-compat-bsl-douira/`,
`s10-compat-bsl-douira2/`, `s10-compat-bsl-taumc/`; dumps `run/s10-compat-shaders-s8-taumc/` (S8's dump, saved before
the run), `run/s10-compat-shaders-douira/` (also copied back to `run/client/compat_shaders/`),
`run/s10-compat-bsl-shaders-{douira,douira2,taumc}/`.

## Commands run and their outcomes

Every test run used `--rerun`; counts are from `build/test-results/test/*.xml`.

Brief's Verify 1 and 2 (with the GLSM engine test), both green, see Status. The first run on douira, before the test
port, already passed the old TauMC-oracle test (`tests="20" failures="0"`, `run/s10-cst-douira-1.out`), and on taumc
(`run/s10-cst-taumc-1.out`).

Unknown engine value, `-PglslEngine=bogus` (`run/s10-tests-bogus.out`): `GlslTransformEngineTest tests="4"
failures="0"`; the log has both
`[TransformPatcher] Unknown GLSL transform engine 'bogus' in demonica.glsl.engine; using douira` and
`[CompatShaderTransformer] Unknown GLSL transform engine 'bogus' in demonica.glsl.engine; using douira`.

Brief's Verify 3 and every other recorded corpus, `-PglslReplayEngine=douira -PglslReplayPatches=COMPAT`
(`run/s10-replay-<corpus>.out`; all `BUILD SUCCESSFUL`; each line `accepted entries in scope=0 used=0 stale=0`):

| Corpus | Summary line |
|---|---|
| `run/transform-corpus/compat` (brief's command, no filter) | `cases=24 identical=24 (byte-identical 0) accepted=0 failing=0 unsupported=0 recorded=0 filtered=0` (22 COMPAT, 1 ATTRIBUTES, 1 COMPOSITE) |
| `run/transform-corpus` | `cases=37 identical=37 ... failing=0 unsupported=0 ... filtered=387` |
| `run/transform-corpus-douira` | `cases=15 identical=15 ... filtered=385` |
| `run/transform-corpus-dh` | `cases=22 identical=22 ... filtered=118` |
| `run/transform-corpus-dh-douira` | `cases=22 identical=22 ... filtered=118` |
| `run/transform-corpus-s7-douira`, `-s7-douira2`, `-s7-taumc`, `-s7b-douira` | `cases=5 identical=5 ... filtered=170` each |
| `run/transform-corpus-s7b-capturetest` | `cases=5 identical=5 ... filtered=2` |

That is 121 compat cases, but only 22 distinct inputs (`hash=` values across all of them). Determinism check on
`taumc` (`run/s10-replay-transform-corpus-taumc.out`): `cases=37 identical=37 (byte-identical 37)`.

Mini-corpus (`run/s10-replay-mini-{douira,taumc}.out`): douira `cases=39 identical=30 (byte-identical 0) accepted=9
failing=0 unsupported=0`, `COMPAT {IDENTICAL=11}`, `accepted entries in scope=9 used=9 stale=0`; taumc `cases=39
identical=39 (byte-identical 39)`. Before the seven new cases (`run/s10-replay-mini.out`): `cases=32 identical=23
accepted=9 unsupported=0` (S8/S9: `identical=19 ... unsupported=4`; the four were the compat cases).

The seven new cases alone (`run/s10-replay-newcases.out`): `cases=7 identical=7 ... failing=0`. The `gles-conditionals`
case first had a continued `#define`, which the evaluated-conditionals path rejects ("unterminated directive
continuation", fallback on TauMC too); it was rewritten to one line so that it reaches the parse.

Compiling both engines' outputs, `glslangValidator` 16.4.0 (`-S frag|vert`), on the 33 distinct compat inputs (the 22
recorded, the 4 old and 7 new mini-corpus cases; douira outputs from a record-mode replay into a scratch copy,
`run/s10-record-douira-validate.out`): 32 compile on both engines; `compat-shadow-nested` fails on both with the same
error (`'scalar swizzle' : not supported for this version`), the known quirk.

GLSM tests: see Status. Transform tests (Appendix C set plus `GlslTransformEngineTest`, `run/s10-transform-douira.out`):
`classes=22 tests=538 skipped=2 failures=0 errors=0`. Full `:test` (`run/s10-full-test.out`): `BUILD SUCCESSFUL`,
`classes=138 tests=1073 skipped=4 failures=0 errors=0` (S9: 1,065; +4 engine, +3 legacy print, +1 directive test).
`./gradlew verifyModuleBoundaries` (`run/s10-boundaries.out`): `BUILD SUCCESSFUL`; `DependencyDirectionTest tests="1"
failures="0"` (`run/s10-depdir.out`). No `./gradlew check` was run.

Dev runs (memory gotchas read first; one Gradle invocation at a time; each client exited through the script's `exit`):

| Run | Command | Log |
|---|---|---|
| compat, default engine | `./gradlew runClient -PwithCompatMods -PdevScript=@<abs>/scripts/glsl-corpus/compat.txt -PdevProps=angelica.dumpShaders=true,demonica.glsl.corpus=<abs>/run/transform-corpus-s10-douira/compat` | `run/s10-compat.out`, `BUILD SUCCESSFUL in 30s` |
| compat + BSL, default engine | `... -PdevScript=@<abs>/scripts/glsl-corpus/compat-bsl.txt -PdevProps=angelica.dumpShaders=true` | `run/s10-compat-bsl.out` (43 s) and a repeat `run/s10-compat-bsl-2.out` (37 s) |
| compat + BSL, TauMC | the same with `,demonica.glsl.engine=taumc` | `run/s10-compat-bsl-taumc.out` (49 s) |

Every default-engine run logs `[CompatShaderTransformer] GLSL transform engine: douira (demonica.glsl.engine)` once
and `[TransformPatcher] GLSL transform engine: douira (demonica.glsl.engine)` once; the TauMC run logs `taumc` for
both. BSL loaded in all three BSL runs (`Dev shader pack: BSL_v10.1.8.zip (loaded: true)`).

The brief's Verify 4 grep, `grep -cE 'falling back|FFP .* compilation failed|Shader compilation log'`, and three more
counts:

| Log | Verify-4 grep | `AST transformation failed` | `compilation failed`/`Failed to compile`/`Failed to link` | `[ShaderAst] Dropped` | `Exception` lines |
|---|---|---|---|---|---|
| `run/s10-compat.out` | 2 | 0 | 0 | 0 | 54 |
| `run/s10-compat-bsl.out` | 2 | 0 | 0 | 0 | 54 |
| `run/s10-compat-bsl-2.out` | 2 | 0 | 0 | 0 | 54 |
| `run/s10-compat-bsl-taumc.out` | 2 | 0 | 0 | 0 | 54 |
| S8 `run/s8-compat.out`, `run/s8-compat-dump.out`, S2 `run/corpus-compat.out` (TauMC) | 2 each | | | | |

The two Verify-4 lines are the Mixin mappings line and oshi's sysfs line in every run. The 54 `Exception` lines are
Extra Utilities 2 and ArchitectureCraft model/blockstate errors and iChunUtil's offline resource fetch, identical in
number on both engines.

## Measurements

**The in-game compat corpus against TauMC.** The default-engine compat run recorded 24 cases (22 COMPAT, `outcome=ok`
each); their 24 input hashes are exactly S2's 24. Recording TauMC outputs for them in a scratch copy and replaying
douira: `cases=22 identical=22 ... failing=0` (`run/s10-replay-devcorpus-douira.out`). The in-game `out.douira.glsl`
files are byte-identical to a douira replay of the same inputs (22 of 22).

**Dumps.** Compat run: 116 files, as S2's and S8's; the 58 originals are byte-identical to S8's (same order); 32
untransformed outputs are byte-identical, the 26 transformed ones differ in formatting only (they are the 22 distinct
inputs above, some submitted twice). Callers: Distant Horizons 26, Botania 13, `OpenGlHelper` 13, Celeritas 6.
`glslangValidator` on the 58 transformed dumps: 57 compile; `33_transformed.frag.glsl` fails, a Distant Horizons shader
that is not transformed (`Transformed: false`, the version fix-up path) and fails the same way from S8's TauMC dump
(`'dFdxFine' : no matching overloaded function found`); the driver compiled it (no compile-failure line in the log).
Compat + BSL runs: 428 files each (214 programs: Iris 138, DH 26, Celeritas 24, Botania 13, `OpenGlHelper` 13); 26
transformed on each engine, all 26 compile under `glslangValidator` on douira (both runs) and on TauMC. The 138 Iris
programs pass through GLSM untransformed (`#version 330 core` or later), as the plan's 3.1 says.

**Frames.** numpy over ffmpeg's rgb24 decode, 1200x720; "pixels" is the share of pixels with a channel differing by
more than 16/255.

| Frame | Comparison | Pixels | Mean | Cause |
|---|---|---|---|---|
| compat world | S10 against S2 baseline / against S8 | 0.44 % / 0.48 % | 0.13 / 0.14 | cloud edges only (mask looked at); S8 against S2 was 0.05 % |
| compat inventory | S10 against S2 / against S8 | 1.26 % / 1.26 % | 1.24 / 1.24 | an "Iron Ore" tooltip in S2's and S8's frames that is absent in S10's (the mouse cursor's position over the window) |
| compat + BSL, no pack | douira (repeat) against taumc | 0.23 % | 0.07 | |
| compat + BSL, BSL | douira (repeat) against taumc | 0.88 % | 0.87 | waving grass edges only (mask looked at) |
| compat + BSL, inventory | douira (repeat) against taumc | 0.04 % | 0.14 | |
| compat + BSL, BSL / inventory | douira (first run) against taumc | 80.46 % / 32.07 % | 41.96 / 8.43 | the camera looks at the ground in the first douira run's pack and inventory frames (and a tooltip shows): the view moved between the no-pack frame (0.13 %) and the pack frame. The repeat run did not reproduce it; not an engine effect |
| compat + BSL, no pack | douira against S2 compat world | 0.22 % | 0.07 | |

**Transform time.** The recorder's `transformMs` for the 22 shared inputs, one session each (S2 TauMC against this
run): sum 164.8 against 153.7 ms, median 1.87 against 1.91 ms. The first transform of each session includes class
loading.

## Residual diffs

| Case | Stage | Classification | Action |
|---|---|---|---|
| none | | Every compat case of every corpus, and the 11 mini-corpus compat cases, replays identically on douira | none |
| `compat-shadow-nested` (mini-corpus) | compat | Identical on both engines, but neither output compiles (`vec4(texture(outer, vec3(texture(inner, p).r)))`: `.r` on a `float`) | Kept for parity, as the orchestrator asked; pins the S4 quirk so that a change to it shows in the replay (S4 open question 2) |

## Deviations from the brief

- Do 1 says "behind `TransformPatcher.engine()`"; `glsm` cannot see `shader` (S1), so both classes resolve the engine
  through the new `glsm` helper `GlslTransformEngine` (the orchestrator's note). `TransformPatcher.Engine` is gone;
  `TransformPatcher.engine()` returns `GlslTransformEngine`.
- The verb calls are not duplicated into a second method: they are written once against a private `Verbs` interface
  with a TauMC and a `ShaderAst` adapter, so both engines provably run the same sequence. Step 11 deletes the TauMC
  adapter and can then inline `ShaderAst` for `Verbs`.
- `CompatShaderTransformer.transform(source, isFragment, engine)` is new public API, so the replay can run compat cases
  on the engine it replays rather than the JVM's.
- `ShaderAst.print(header, version)` (the float-suffix guard) was added although no current path prints below 330 (see
  Notes); it is covered by its own test.
- Do 4's command passes `demonica.glsl.engine=douira`; the runs above leave it unset (douira is the default since S8,
  as the orchestrator noted) and the logs show `douira`. The compat run also recorded a corpus
  (`demonica.glsl.corpus`), which the brief's command does not. Do 4's "one mod screen" screenshot was not taken: the
  compat script shoots the world and the inventory, and the orchestrator's second run (BSL with the compat mods) took
  its place.
- Test runs use `--rerun` (the orchestrator's rule); the brief's Verify commands have none.
- Seven compat cases were added to the committed mini-corpus (not asked for): the recorded corpora hold only 22
  distinct compat inputs and none uses `gl_LightSource`, `gl_FrontMaterial`, `gl_FrontLightModelProduct`,
  `gl_TextureMatrix[i]`, most of the shadow family or the reserved-word renames.

## Open questions

1. `compat-shadow-nested` keeps TauMC's nested-shadow output, which does not compile. Wrap inner calls too once TauMC is
   gone (S4 open question 2)? The mini-corpus case would then need an `accepted.txt` entry or a re-recorded snapshot.
2. The first douira compat+BSL run's pack and inventory frames show the camera turned to the ground (and an item
   tooltip), which neither the TauMC run nor the repeat douira run showed; S10's plain compat inventory frame lacks the
   tooltip S2's and S8's frames have. Both look like the host's mouse cursor reaching the client window. Should the
   harness park or ignore the cursor during scripted runs?
3. S8's and S9's open questions stand.

## Notes for the next step

- Engine: `GlslTransformEngine` (`glsm`, package `com.gtnewhorizons.angelica.glsm`) is the one switch.
  `TransformPatcher.engine()` and `CompatShaderTransformer.engine()` resolve it once each and log it once each. Step 11
  deletes the enum (or keeps only its `DOUIRA` meaning), `TransformPatcher.engine()`, `CompatShaderTransformer.engine()`
  and the three-argument `transform`, the replay's engine choice for compat, and `GlslTransformEngineTest`.
- `CompatShaderTransformer`'s remaining TauMC use is `parseTauMc` and the `TauMcVerbs` record (imports
  `org.taumc.glsl.ShaderParser`, `org.taumc.glsl.Transformer`) and `GlslTransformUtils.getFormattedShader` inside it.
  Removing them leaves `Verbs` with one implementation: replace `Verbs` by `ShaderAst` in the four helpers and the
  switch by `ShaderAst.parse(source, targetVersion)`, and call `ast.print(header, targetVersion)` directly.
- GLSM main code imports TauMC only in `CompatShaderTransformer` (the adapter above); `ShaderAst` names
  `org.taumc.glsl.Transformer` in its javadoc only. `glsm/CompatShaderTransformerTest`
  no longer imports TauMC; `grep -rln 'org\.taumc\.glsl' src/test/java` now lists seven classes
  (`celeritas/vertices/TerrainVertexFormatScanParityTest` and six in `pipeline/transform/`, per S9's list).
- Float suffixes: the compat output version is `max(declared, RENDER_BACKEND.getMinGLSLVersion())`, or 330 without a
  backend; the only production backend (`Lwjgl3GLRenderBackend`) returns 330 and every recorded case has
  `minGlslVersion=330`, so nothing prints below 130 today. `ShaderAst.print(header, version)` prints suffix-free floats
  below 130 should that change. `glslangValidator` 16.4.0 accepts `1.0f` under `#version 120`, so it cannot show the
  hazard; the GLSL 1.10/1.20 specifications have no suffix.
- The parse's lexer version is the compat output's target version (`ShaderAst.parse(source, targetVersion)`): the body
  has no `#version` line, and `renameReservedWords` renamed for that version.
- The mod shader's preamble stays separated by `separatePreprocessorPreamble`; the parsed body holds no directive
  (strict mode moves or rejects them, the evaluated mode drops dead branches). `ShaderAst.extensionDirectives()` is not
  used here; the preamble text goes into the header as before.
- The mini-corpus has 39 cases (11 compat); `:test` replays them on the default engine: `identical=30 accepted=9
  failing=0 unsupported=0`. `accepted.txt` still has nine entries, none for compat.
- `scripts/glsl-corpus/compat-bsl.txt` loads BSL with the compat mods; run it with `-PwithCompatMods` (and
  `angelica.dumpShaders=true` for dumps). It leaves `run/client/saves/corpus`; delete it afterwards (done here).
