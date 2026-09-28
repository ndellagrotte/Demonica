# S01: build wiring, license, spike

Step 1 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, created from `dev` at `2ed28444`
on 2026-09-28.

## Status

**Done.** Every item under "Done when" holds; the evidence is below.

## Commits

| Commit | Subject |
|---|---|
| `4104a798` | glsl-transformer: S1 relicense to AGPL-3.0 and contain glsl-transformer 3.0.0-pre3 |
| `f7232573` | glsl-transformer: S1 engine switch, stub engine and parser spike |
| `114feb14` | glsl-transformer: S1 report and status |
| (the commit after `114feb14`) | glsl-transformer: S1 fix the unknown-engine warning and the notice's header claim ([Verification follow-up](#verification-follow-up)) |

## What changed

Added:
- `shader/src/main/java/net/coderbot/iris/pipeline/transform/AstShaderTransformer.java`: the new engine's stub. Same two
  static signatures as `ShaderTransformer` (`transform(vertex, geometry, tessControl, tessEval, fragment, P)`,
  `transformCompute(compute, P)`), both return `null` for all-null input and otherwise throw
  `UnsupportedOperationException("glsl-transformer engine: <PATCH> not ported yet")`; `clearSessionState()` is empty.
- `src/test/java/net/coderbot/iris/pipeline/transform/GlslTransformerSpikeTest.java`: the reference parser
  configuration, three tests.
- `LICENSE-GPL-3.0.txt`: the former `LICENSE` (GPL-3.0), moved with `git mv`.
- `docs/glsl-transformer_adoption/DECISION.md`, `STATUS.md`, `reports/S01-build-wiring.md`.

Changed:
- `LICENSE`: now the AGPL-3.0 text, fetched from https://www.gnu.org/licenses/agpl-3.0.txt (661 lines, 34,523 bytes,
  SHA-256 `0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0`; byte-identical to glsl-transformer's own
  `LICENSE` at `v3.0.0-pre3` and at `main`).
- `glsm/build.gradle`: `implementation('io.github.douira:glsl-transformer:3.0.0-pre3') { transitive = false }` next to
  TauMC's line.
- `build.gradle`: the same coordinate in `contain`, `transitive = false`; the five license-file lists
  (`diagnosticsJar`, `sourcesJar`, `jar`, `verifyDistributedJar`'s required entries, `verifyDiagnosticsJar`'s allowed
  and required entries) now name `LICENSE`, `LICENSE-GPL-3.0.txt`, `LICENSE-LGPL-3.0.txt`, `THIRD_PARTY_NOTICES.md`.
- `shader/.../pipeline/transform/TransformPatcher.java`: the engine switch (below).
- `THIRD_PARTY_NOTICES.md`: the bold paragraph (Demonica is AGPL-3.0; how the LGPL-3.0 and GPL-3.0 parts combine under
  section 13 of GPL-3.0 and of AGPL-3.0); a contained-libraries row for glsl-transformer; `## In the jars` names four
  files; a new `### glsl-transformer (AGPL-3.0)` notice.
- `README.MD`: the License section (AGPL-3.0, four files, releases up to 0.4.0 were GPL-3.0) and a credit line
  "douira for glsl-transformer" under the glsl-transformation-lib credit, which stays until Step 11.
- `docs/FORK.md` and `docs/PROVENANCE.md`: their `## License` sections, plus PROVENANCE's historical bullet on
  `src/main/resources/LICENSE`, which listed the three files and now points at the change.
- `src/main/resources/mcmod.info`: credits gain "glsl-transformer (douira)".

Files touched by the license change (the handoff list): `LICENSE`, `LICENSE-GPL-3.0.txt`, `build.gradle`,
`THIRD_PARTY_NOTICES.md`, `README.MD`, `docs/FORK.md`, `docs/PROVENANCE.md`, `src/main/resources/mcmod.info`,
`docs/glsl-transformer_adoption/DECISION.md`. Left alone on purpose: `third-party/actinium/THIRD_PARTY_NOTICES.md:3`
("The root `LICENSE` applies to ...") is Actinium's own inventory and means Actinium's root.

Outside git (`run/` is ignored by `.gitignore:6`): `run/lib-src/glsl-transformer-sources.jar` and its unpacked tree
`run/lib-src/glsl-transformer/` (257 `.java` files); the dev-run scripts `run/client/scripts/s1.txt` and `s1-base.txt`;
the outputs `run/s1-*.out`; screenshots `run/client/screenshots/s1-{taumc,douira,base}-*.png`. The save
`run/client/saves/s1` was deleted after the runs.

### The engine switch

- Property `demonica.glsl.engine`, values `taumc` (default) and `douira`, trimmed and lower-cased. An unknown value logs
  `[TransformPatcher] Unknown GLSL transform engine '<value>' in demonica.glsl.engine; using taumc` (WARN), with
  `<value>` trimmed and lower-cased. (Until the verification follow-up the code passed `{}` arguments to
  `IrisLogging.warn(Object...)` and logged `[Ljava.lang.Object;@...` instead; see that section.)
- `public static TransformPatcher.Engine engine()`; `public enum Engine { TAUMC("taumc"), DOUIRA("douira") }` with a
  public `id`. It is resolved in a holder class, so it happens once per JVM, thread-safely, on first access.
- Logged once at first use, at INFO, on whichever thread transforms first:
  `[TransformPatcher] GLSL transform engine: taumc (demonica.glsl.engine)`. In the dev runs that is a
  `Shader-Transform-0` thread during Iris's startup warm-up, before the script's first step completes.
- `transform`/`transformCompute` route through a `switch (engine())` after the cache lookup; the cache itself is
  shared. `clearCache()` calls `ShaderTransformer.clearSessionState()` and `AstShaderTransformer.clearSessionState()`
  without resolving the engine.

## Commands run and their outcomes

Dependency resolution (`run/s1-deps.out`):
```
$ ./gradlew :glsm:dependencies --configuration compileClasspath 2>&1 | grep -E 'douira|antlr|taumc'
+--- org.taumc:glsl-transformation-lib:0.2.0-32.g7dd88a4-GTNH
+--- io.github.douira:glsl-transformer:3.0.0-pre3
\--- org.antlr:antlr4-runtime:4.13.2
```
No `org.antlr:antlr4` (the tool) and no other transitive module.

Spike and transform tests (`run/s1-tests.out`, then `run/s1-tests-head.out` at `f7232573`):
```
$ ./gradlew :test --tests '*GlslTransformerSpikeTest' --tests 'net.coderbot.iris.pipeline.transform.*'
BUILD SUCCESSFUL in 2s
```
Per class, from `build/test-results/test/*.xml`: AdaptiveShadowBoundsTransformerTest 9, CeleritasTransformerTest 3,
CompatibilityTransformerCaveSkyholeTest 1, CompatibilityTransformerTest 6, GlslTransformerSpikeTest 3,
TransformPatcherCacheTest 5, TransformPatcherTest 3: 30 tests, 0 failures, 0 errors, 0 skipped. The rerun at the head
commit (after the dev baseline runs had switched the tree) restored `:test` from Gradle's build cache, that is, the
same inputs as the executed run.

The spike's first run failed one assertion: the printer wrote `0.0f` where the test expected `0.0` (see Measurements);
the test now asserts the library's actual output and says why.

Full build (`run/s1-build.out`, the step's one `check` run):
```
$ ./gradlew build 2>&1 | grep -E 'FAILED|error:|BUILD' | tail -20
BUILD SUCCESSFUL in 13s
```
124 test classes, 549 tests, 0 failures, 0 errors, 1 skipped (all written by this build, 17:33:27 to 17:33:28 UTC).
`check` ran `verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`,
`verifyModuleBoundaries`, `verifyRunClasspath` and `verifyS8tnlibPin`.

Jar contents (the brief's glob `build/libs/Demonica-*.jar` matches six jars there, and `unzip` reads extra arguments as
member names, so the path is explicit):
```
$ unzip -l build/libs/Demonica-0.5.0-SNAPSHOT.jar | grep -E 'glsl|antlr|LICENSE|NOTICES'
    34523  1980-02-01 00:00   LICENSE
    35149  1980-02-01 00:00   LICENSE-GPL-3.0.txt
     7931  1980-02-01 00:00   LICENSE-LGPL-3.0.txt
    13059  1980-02-01 00:00   THIRD_PARTY_NOTICES.md
   326307  1980-02-01 00:00   antlr4-runtime-4.13.2.jar
     2633  1980-02-01 00:00   assets/actinium/shaders/include/chunk_vertex.glsl
   312561  1980-02-01 00:00   glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar
   722670  1980-02-01 00:00   glsl-transformer-3.0.0-pre3.jar
ContainedDeps: glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar glsl-transformer-3.0.0-pre3.jar antlr4-runtime-4.13.2.jar jcpp-1.4.14.jar
```
The diagnostics jar and the sources jar carry the same four files.

```
$ ./gradlew verifyDistributedJar verifyDiagnosticsJar --rerun-tasks 2>&1 | tail -15
> Task :verifyDistributedJar
> Task :verifyDiagnosticsJar
BUILD SUCCESSFUL in 16s
```

Dev run, default engine (`run/s1-taumc.out`, 29 s):
```
$ ./gradlew runClient -PdevScript=@scripts/s1.txt > run/s1-taumc.out 2>&1
[Shader-Transform-0/INFO] [Demonica]: [TransformPatcher] GLSL transform engine: taumc (demonica.glsl.engine)
[DemonicaDevHarness]: Dev shader pack: BSL_v10.1.8.zip (loaded: true)
[DemonicaDevHarness]: Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224
[DemonicaDevHarness]: Dev screenshot saved: screenshots/s1-1-bsl.png
[DemonicaDevHarness]: Dev step: exit
```
All 24 steps logged; the engine line appears once; 19 "n of n injectors found their targets" lines; the
`[DemonicaQuarantine]` gate line (`celeritas-forge-mc12.2-2.4.0-autobuild.06999aab.jar is the build this workspace was
built against (dev remap SHA-256 0c2b9b77, ...)`); no `Shader compilation failed`. The only `Exception` lines are the
known ones: six "Mixin config does not reside in a jar file" (six in each of the four earlier runs checked, from
2026-09-27; one per mixin config, the diagnostics one included) and
the narrator's "No null terminator found".

Dev run, douira engine (`run/s1-douira.out`):
```
$ ./gradlew runClient -PdevScript=@scripts/s1.txt -PdevProps=demonica.glsl.engine=douira > run/s1-douira.out 2>&1
[Shader-Transform-0/INFO] [Demonica]: [TransformPatcher] GLSL transform engine: douira (demonica.glsl.engine)
[Client thread/WARN] [Demonica]: Warmup failed
java.util.concurrent.ExecutionException: java.lang.UnsupportedOperationException: glsl-transformer engine: COMPOSITE not ported yet
[Client thread/ERROR] [Demonica]: Failed to create shader rendering pipeline, disabling shaders!
java.lang.RuntimeException: Shader transformation failed for 'deferred' in stage 'deferred' (pass 0)
Caused by: java.lang.UnsupportedOperationException: glsl-transformer engine: COMPOSITE not ported yet
[CHAT] Failed to create the shader rendering pipeline, shaders disabled! Reason: glsl-transformer engine: COMPOSITE not ported yet
[DemonicaDevHarness]: Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections -1
[DemonicaDevHarness]: Dev step: exit
```
No crash: every step ran to `exit`, and the harness reports `BSL_v10.1.8.zip (loaded: true)` although the pipeline
was not created (it reports the selection). The warm-up failure is Iris's startup transform warm-up
(`Iris$ShaderTransformExecutor.warmup`, a composite program); it logs WARN and nothing else happens.

Baseline on `dev` (not in the brief; see Deviations). `git switch --detach dev`, the same script as
`run/client/scripts/s1-base.txt` with its own shot names and an absolute teleport, then `git switch feat/glsl-transformer`.
The first attempt used the relative teleport and spawned 3 blocks away (`-167.5, 99.83, 221.5` against
`-164.5, 100.83, 218.5`: the vanilla spawn fuzz), so its frames did not line up; the second run (`run/s1-base-dev.out`)
teleported to `-164.5 100.82940159667969 218.5 225 15`:
```
[DemonicaDevHarness]: Dev shader pack: BSL_v10.1.8.zip (loaded: true)
[DemonicaDevHarness]: Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224
[DemonicaDevHarness]: Dev step: exit
```

## Measurements

Library artifacts, checked against Maven Central's `.sha1` files:

| File | Bytes | SHA-256 | SHA-1 (= Central's) |
|---|---|---|---|
| `glsl-transformer-3.0.0-pre3.jar` (Gradle cache; the copy nested in the mod jar has the same hash) | 722,670 | `0d55650fb8e58dad25b714d447a1ed5a713a6b6120a06610ce216c9b26e852d7` | `83979e47f9e147037f1f13e63000cbdea3a66e94` |
| `glsl-transformer-3.0.0-pre3-sources.jar` (`run/lib-src/glsl-transformer-sources.jar`) | 576,985 | `82274b7fa24db657176a71e12181f49e93863be50b578e903bc0e0451812a817` | `90b8bb09ae5299f791db2d8a810958ee31f8567b` |

The binary jar has no license file; its manifest is only `Manifest-Version: 1.0`. Besides 404 entries under
`io/github/douira/glsl_transformer`, it holds 42 entries under `org/apache/commons/collections4` (the `Trie` subset of
Apache Commons Collections, Apache-2.0; their `LICENSE.txt` is only in the sources jar). Its Gradle metadata says
`"org.gradle.jvm.version": 21`.

Jar sizes (`remapJar` output; "before" rebuilt on the branch at `2ed28444` before any change):

| Jar | Before | After | Change |
|---|---|---|---|
| `build/libs/Demonica-0.5.0-SNAPSHOT.jar` | 2,987,741 B, 1,437 entries | 3,644,337 B, 1,442 entries | +656,596 B (+22.0 %) |
| `build/libs/Demonica-diagnostics-0.5.0-SNAPSHOT.jar` | 162,404 B (up to date at `2ed28444`) | 175,507 B | +13,103 B (the GPL text and the longer notices) |
| `build/libs/Demonica-0.5.0-SNAPSHOT-sources.jar` | not measured at `2ed28444` | 1,338,008 B | |

The nested `glsl-transformer-3.0.0-pre3.jar` is 722,670 B, stored as 639,692 B compressed. The five new mod-jar entries
are that jar, `LICENSE-GPL-3.0.txt`, `AstShaderTransformer.class`, `TransformPatcher$Engine.class` and
`TransformPatcher$EngineHolder.class`.

What the spike shows (the printed output is in `run/s1-spike.out`):
- `#version 120` parses when the lexer's version is set from the directive. A probe outside the repository (a
  scratch `java` run against the same jars, not committed) parsed a `#version 120` shader that uses `sample` as an
  identifier with the lexer version unset (the default is `GLSL46`), `GLSL12`, `GLSL33` and `GLSL46`: it fails with
  `ParseCancellationException: line 4:41 no viable alternative at input 'float sample'` at the default and at `GLSL46`,
  and parses at `GLSL12` and `GLSL33`. `texture` as an identifier parsed at every version.
- The preprocessor filter sees `#define A 1` as three tokens (`PP_ENTER_MODE` "#define", `PP_CONTENT`, `PP_EOL`);
  `PP_EMPTY` and `NR_LINE` are also on that channel. `#version` and `#extension` are grammar: the filter dropped
  nothing in test (b). `setParsingCacheStrategy` replaces the parser, so it must come before `setTokenFilter` (Iris's
  order).
- `PrintType.INDENTED` indents with tabs, keeps the `#version` line, and prints `#extension GL_ARB_shader_image_load_store: enable`
  (no space before the colon).
- The printer reprints every float literal as `Double.toString(value) + "f"` (`ASTPrinter`, the `FLOAT32` case):
  `0.0` becomes `0.0f`, `1e-3` becomes `0.001f`, `vec4(1.0)` becomes `vec4(1.0f)`. TauMC keeps the source text.
- A syntax error (`fragColor = vec4(1.0) vec4(0.0);` on line 4) raised ANTLR's own
  `org.antlr.v4.runtime.misc.ParseCancellationException: line 4:26 missing ';' at 'vec4'`, not `ParsingException`.
  From the source (`EnhancedParser.handleParseCancellationException`): only an `InputMismatchException` becomes a
  `ParsingException`, whose message has no line ("Unexpected token 'x'", "Missing semicolon or comma!",
  "Unexpected end of file!") and whose cause carries ANTLR's `line L:C ...` message; every other recognition error is
  thrown as the `ParseCancellationException` itself. `ParsingException.extractParseCancellationException(e)` handles
  both; the test uses it.
- `#extension X : require` is `ExtensionDirective.ExtensionBehavior.DEBUG` (its token is `NR_REQUIRE`); `ENABLE`,
  `WARN`, `DISABLE` are named as expected.

Frames (1200 x 720, decoded with ffmpeg to rgb24; "pixels" = share with any channel differing by more than 16/255):

| Comparison | Mean abs | Pixels | Reading |
|---|---|---|---|
| branch taumc BSL vs `dev` BSL (same absolute camera) | 0.85 | 0.55 % (sky band 0.04 %, terrain bands 0.62-1.00 %), max 81 | same render; waving plants |
| branch taumc no-pack vs `dev` no-pack | 1.29 | 2.92 % | noise floor of this camera (vanilla clouds and plants) |
| douira "BSL" vs douira no-pack | 1.29 | 3.3 % | shaders off, as intended |
| branch taumc BSL vs its no-pack | 33.81 | 97.0 % | BSL is on |

The frames are `s1-taumc-1-bsl.png`, `s1-base-1-bsl.png`, `s1-douira-1-bsl.png` and their `-0-nopack` pairs in
`run/client/screenshots/`.

## Residual diffs

No corpus or replay exists yet (Step 2), so there are no replay cases. What the step did compare:

| Case | Stage | Classification | Action |
|---|---|---|---|
| s1 BSL frame, branch (taumc) against `dev` | final image | noise: 0.55 % of pixels, all in the terrain bands | none |
| Spike: `0.0` printed as `0.0f`, `1e-3` as `0.001f` | print | library behaviour, not a bug | `GlslTokens` (Step 2 or 3) must normalize float literals, or every replay stage will differ |
| Spike: `#extension X : enable` printed as `X: enable` | print | whitespace | none; any tokenizer absorbs it |

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1": the orchestrator's
   rule (the trailer names the model that did the work).
2. The glsl-transformer notice has no copyright line, because the repository has none: at the tag `v3.0.0-pre3` and on
   `main` (`9d26f0f6`), `README.md` has no copyright line, `LICENSE` is the unmodified AGPL-3.0 text, and none of
   douira's own Java sources has a header. (Corrected in the verification follow-up: the sources jar's 257 `.java`
   files are douira's 233 under `io/` with no header, 21 under `org/apache/commons/collections4/` that carry the ASF
   Apache-2.0 header, and 3 ANTLR-generated files with only a "Generated from" line.) The only "Copyright" in the
   sources is `GLSLParser.g4`'s "Copyright 2018 The GraphicsFuzz Project Authors" (Apache-2.0). The notice therefore quotes the README's authorship
   sentence (README line 11, raw URL `https://raw.githubusercontent.com/IrisShaders/glsl-transformer/v3.0.0-pre3/README.md`):
   "`glsl-transformer` is developed and maintained by [douira](https://github.com/douira)." and says that no
   copyright line exists. The quoted license paragraph is README lines 15-24 at the same tag, checked byte-for-byte.
3. The notice and the contained-libraries row also name the two Apache-2.0 parts inside the library (the Commons
   Collections subset and the GraphicsFuzz-derived grammar). The brief asked only for the AGPL row and the README
   paragraph.
4. DECISION.md scopes the relicensing by authorship, not by package. The brief says "`com.demonica.*` and Demonica's
   modifications", but `com.demonica` holds Actinium's root project renamed from `com.dhj.actinium`, which stays
   GPL-3.0 (THIRD_PARTY_NOTICES.md says so), and `com.demonica.compat.Mods` derives from GTNHLib. The header grep the
   brief gives finds two files (Mesa's and LWJGL's); a wider search for license names finds seven more naming LGPL
   (Sodium, Canvas). All are listed.
5. Spike test (a) adds `#define SPIKE_UNUSED 1` (to see the filter drop and count a directive) and
   `const float SPIKE_EPSILON = 1e-3;` (to record literal printing) to the source the brief lists. The first
   version of the test expected `0.0`, failed, and now asserts `0.0f` with a comment that says why.
6. `s1.txt` has fixed shot names, so the douira run overwrote the taumc run's `s1-0-nopack.png`/`s1-1-bsl.png`. They
   were restored losslessly from the rgb24 decode made before the second run (re-encoded to PNG and checked
   byte-identical as raw pixels) as `s1-taumc-*.png`; the douira frames were renamed `s1-douira-*.png`. The script
   also has a `stats` step, a `wait 20` after the first `pack off`, and a closing `pack off` so no pack stays selected.
7. An extra baseline run of the same script on `dev`, to back "renders BSL as before" with a same-day frame: the only
   older frame from this camera (`cp4-1-bsl.png`, 2026-09-23) predates terrain shadows. It meant detaching the working
   tree onto `dev` twice (no commits there) and a new script `s1-base.txt` with an absolute teleport. The first
   baseline's `run/s1-base-dev.out` was overwritten by the second.
8. The verify tasks were run with `--rerun-tasks`, so they executed instead of being reported up to date after
   `build`; that also re-ran the tasks they depend on.
9. `engine()` and `Engine` are public rather than package-private. The replay test is in the same package, but
   nothing in `glsm` can call `TransformPatcher` anyway (see Notes).
10. The brief's Verify line `unzip -l build/libs/Demonica-*.jar` was run with the explicit path
    `build/libs/Demonica-0.5.0-SNAPSHOT.jar`: the glob matches six jars there.

## Open questions

1. Apache-2.0 texts. glsl-transformer's jar carries Apache Commons Collections classes without a license file, and
   Apache-2.0 section 4(a) asks a redistributor to give recipients a copy of the license. `jcpp-1.4.14.jar` (Apache-2.0)
   and `antlr4-runtime-4.13.2.jar` (BSD-3-Clause) already ship without their texts, so this is an existing gap that the
   new library widens. Should the jars carry `LICENSE-APACHE-2.0.txt` (a fifth file in the five lists)? Not done here.
   Added by the verification follow-up: the 21 Commons Collections sources' ASF headers point to "the NOTICE file
   distributed with this work", and Apache-2.0 section 4(d) asks a redistributor to keep a NOTICE file's attributions.
   Neither glsl-transformer's jars (the sources jar has only `org/apache/commons/collections4/LICENSE.txt`) nor
   Demonica's notices reproduce Commons Collections' NOTICE. If the Apache-2.0 text is added, add that attribution to
   the glsl-transformer notice too (fetched from Commons Collections, not written from memory).
2. Is the README's authorship sentence enough attribution for glsl-transformer, or should douira be asked for a
   preferred copyright line?
3. In this camera, BSL's water (left of the frame) renders grey-white on both `dev` and the branch, where
   `cp4-1-bsl.png` from 2026-09-23 shows it blue. It is not caused by this step (the `dev` frame has it too). It may be a
   sky reflection at this angle, or come from the terrain-shadow work or the compatibility-profile default; not
   investigated.
4. The README now says "releases up to 0.4.0 were GPL-3.0". Confirm the wording when 0.5.0 is cut.
5. (Verification follow-up.) `DECISION.md` and `THIRD_PARTY_NOTICES.md` put "Demonica's changes to ported files" under
   AGPL-3.0, as the brief prescribed. Under GPL-3.0 section 5(c) a modified GPL-3.0 file is licensed as a whole under
   GPL-3.0, so the maintainer may prefer "also offered under AGPL-3.0" for those changes, or limiting the relicensing
   to new files. Neither document claims that a ported file changes license. Left for the maintainer.
6. (Verification follow-up.) `IrisLogging` has `warn(String)`, `warn(String, Throwable)` and `warn(Object...)` but no
   `warn(String, Object...)`, so every `Iris.logger.warn("...{}...", args)` call binds to `warn(Object...)` and logs
   `[Ljava.lang.Object;@...`. A one-line grep for `Iris.logger.warn("...{}` outside tests finds 23 such calls
   (`NbtConditionalIdMap:207`, `Iris:727`, `RenderTargets:347/375`, `ShaderProperties:326/883`, `TagEntry:42/43`,
   `ShaderPack:149/179/429/438/710`, `PropertiesTokenizer:92/190/194/198/202/279/280`,
   `PropertiesPreprocessor:47/55/80`; the verifier counted 19 by its own search). Adding
   `warn(String, Object...)` to `IrisLogging` would fix all of them (Java prefers it to `warn(Object...)`, and
   `warn(String, Throwable)` still wins for a lone Throwable), but it predates this step and reaches beyond it, so S1
   fixed only its own call. A separate change on `dev`?

## Notes for the next step

- Coordinates: `io.github.douira:glsl-transformer:3.0.0-pre3` (jar SHA-256 `0d55650f...52d7`, sources SHA-256
  `82274b7f...a817`), `org.antlr:antlr4-runtime:4.13.2` as the only ANTLR. glsm has it as `implementation`; `shader` and
  the root tests see it through the root compile classpath (`contain` extends `implementation`), as they see TauMC.
- Sources: `run/lib-src/glsl-transformer/` (`io/github/douira/glsl_transformer/...`, plus `GLSLLexer.g4` and
  `GLSLParser.g4` at the top). Read by class: start with `grep -n "public"`.
- Engine switch: property `demonica.glsl.engine`; log line `[TransformPatcher] GLSL transform engine: <id> (demonica.glsl.engine)`;
  `TransformPatcher.engine()`. The engine is fixed per JVM (holder class), so a test cannot flip it: the replay test
  (Step 2) should call `ShaderTransformer`/`AstShaderTransformer` directly, as section 3.5 already says.
- `glsm` cannot reach `TransformPatcher` (`shader` has `api project(':glsm')`, not the reverse). Step 10's
  `CompatShaderTransformer` must read the property itself or through a small holder in `glsm`.
- The reference configuration is `GlslTransformerSpikeTest.newTransformer`: `EnumASTTransformer<JobParameters, PatchShaderType>`,
  `setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT)`, `setParsingCacheStrategy(ParsingCacheStrategy.TWO_TIER)`
  before `setTokenFilter(...)`, `parseTranslationUnit(Root, String)` overridden to set `getLexer().version` from the
  `#version` regex, `setPrintType(PrintType.INDENTED)`, and the transformation run inside `root.indexBuildSession(...)`.
  The filter records `PP_ENTER_MODE`/`PP_EMPTY`/`NR_LINE` tokens; directives otherwise produce three tokens each.
- Demonica's `Parameters` does not implement glsl-transformer's `JobParameters` (Iris's does). The spike passes
  `JobParameters.EMPTY`. Step 5 decides whether `Parameters` implements it (it already has `equals`/`hashCode`).
- `GlslTokens` (whoever writes it, Step 2 or 3) must canonicalize float literals: parse the number, drop the
  `f`/`F`/`lf`/`hf` suffix, compare values. Otherwise every stage will differ between engines.
- Lexer version and keywords: identifiers that became keywords at a later GLSL version (`sample` at 400) fail when the
  lexer version is higher than the shader needs. The old pipeline hoists and raises the version (to 330, 400 for
  tessellation) and runs `renameReservedWords` before parsing; the new engine must keep that order and should set the
  lexer version from the directive the parse actually sees.
- Syntax errors: catch both `ParsingException` and `ParseCancellationException`; get the line from
  `ParsingException.extractParseCancellationException(e).getMessage()` (`line L:C ...`).
- On the douira engine the first failure a dev run shows is COMPOSITE (Iris's startup warm-up, then the `deferred`
  pass), not ATTRIBUTES as section 3.2 expects; the effect is the same.
- Dev-run harness: shot names are fixed by the script, so rename frames between runs. For frame comparisons across
  runs, teleport absolutely; seed 1234567 with `world s1` spawned at `-164.5, 100.83, 218.5` twice and at
  `-167.5, 99.83, 221.5` once (spawn fuzz). One Gradle daemon (6 GB) was already running when this step started and
  served every invocation.
- (Verification follow-up.) `TransformPatcher.CacheKey` does not include the engine. That is correct while the engine
  is fixed per JVM; if a later step lets it vary inside one JVM, the key must include it.
- (Verification follow-up.) `Iris.logger` is an `IrisLogging`, whose `warn` has no `(String, Object...)` overload:
  `Iris.logger.warn("... {}", x)` logs `[Ljava.lang.Object;@...`. Concatenate warnings, or use `info`, `error` or
  `debug`, which have the overload.

## Verification follow-up

An independent verification of `114feb14` found one blocking issue and made ten remarks. This section lists each and
what was done (2026-09-28).

### Blocking: the unknown-engine warning logged `[Ljava.lang.Object;@...`

**Confirmed and fixed.** `TransformPatcher.EngineHolder.resolveEngine` called
`Iris.logger.warn("[TransformPatcher] Unknown GLSL transform engine '{}' in {}; using {}", value, ENGINE_PROPERTY, Engine.TAUMC.id)`.
`IrisLogging` has only `warn(String)`, `warn(String, Throwable)` and `warn(Object...)`, so the call bound to
`warn(Object...)`, which hands the array to log4j's `warn(Object)`. The report's "Engine switch" line described a
message the code never emitted. The fix is the minimal local one: the warning is now one concatenated string, with a
comment that says why. `IrisLogging` is unchanged (see Open questions 6). The fallback to `taumc` was already right.

The verifier's repro, before the fix (its `run/verify-s1-bogus.out` and the test XML):
`[Test worker/WARN]: [Ljava.lang.Object;@2787de58`, then `GLSL transform engine: taumc (demonica.glsl.engine)`.

After the fix. The init script (in the session's scratchpad, not committed) is the verifier's:
`allprojects { tasks.withType(Test).configureEach { t -> def v = t.project.findProperty('verifyEngine'); if (v != null) t.systemProperty 'demonica.glsl.engine', v.toString() } }`.
```
$ ./gradlew --init-script <it> -PverifyEngine=Bogus :test --tests 'net.coderbot.iris.pipeline.transform.TransformPatcherTest' --rerun > run/s1-fix-bogus.out 2>&1
BUILD SUCCESSFUL in 4s
$ grep ... build/test-results/test/TEST-net.coderbot.iris.pipeline.transform.TransformPatcherTest.xml
tests="3" skipped="0" failures="0" errors="0" timestamp="2026-09-28T17:56:34.608Z"
[13:56:34] [Test worker/WARN]: [TransformPatcher] Unknown GLSL transform engine 'bogus' in demonica.glsl.engine; using taumc
[13:56:34] [Test worker/INFO]: [TransformPatcher] GLSL transform engine: taumc (demonica.glsl.engine)
```
The value is shown trimmed and lower-cased (`Bogus` became `bogus`); the report's "Engine switch" line now says so.

Checks re-run on the fixed tree:
- Transform tests (`run/s1-fix-tests.out`):
  `./gradlew :test --tests '*GlslTransformerSpikeTest' --tests 'net.coderbot.iris.pipeline.transform.*' --rerun`,
  `BUILD SUCCESSFUL in 2s`; the seven classes ran at 17:56:44 UTC with the same counts as before (9, 3, 1, 6, 3, 5, 3):
  30 tests, 0 failures, 0 errors, 0 skipped.
- Full build (`run/s1-fix-build.out`, the step's second `check` run): `./gradlew build`, `BUILD SUCCESSFUL in 9s`;
  `:test` executed (not from cache): 124 classes, 549 tests, 1 skipped, 0 failures, 0 errors (17:57:10 to 17:57:12
  UTC). `verifyCeleritasPin`, `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`,
  `verifyModuleBoundaries`, `verifyRunClasspath` and `verifyS8tnlibPin` ran.
- Dev run with an unknown value (`run/s1-fix-bogus-dev.out`, not in the brief): `./gradlew runClient
  -PdevScript=@scripts/s1.txt -PdevProps=demonica.glsl.engine=Bogus`, `BUILD SUCCESSFUL in 28s`:
  ```
  [Shader-Transform-0/WARN] [Demonica]: [TransformPatcher] Unknown GLSL transform engine 'bogus' in demonica.glsl.engine; using taumc
  [Shader-Transform-0/INFO] [Demonica]: [TransformPatcher] GLSL transform engine: taumc (demonica.glsl.engine)
  [DemonicaDevHarness]: Dev shader pack: BSL_v10.1.8.zip (loaded: true)
  [DemonicaDevHarness]: Dev stats: 120 fps; terrain C: 166/3600 D: 8 A: 4; shadow sections 224
  [DemonicaDevHarness]: Dev step: exit
  ```
  24 steps, 19 "n of n injectors found their targets" lines, the `[DemonicaQuarantine]` gate line, no
  `Shader compilation failed`, no `[Ljava.lang.Object;@`; the only `Exception` lines are the six "Mixin config does not
  reside in a jar file" and the narrator's "No null terminator found". Frames renamed to
  `run/client/screenshots/s1-bogus-{0-nopack,1-bsl}.png`; the save `run/client/saves/s1` was deleted afterwards. Same
  measure as the Measurements table:

  | Comparison | Mean abs | Pixels | Max |
  |---|---|---|---|
  | unknown-value BSL vs `s1-taumc-1-bsl.png` | 0.56 | 0.11 % | 129 |
  | unknown-value no-pack vs `s1-taumc-0-nopack.png` | 0.01 | 0.01 % | 94 |
  | unknown-value BSL vs its no-pack | 33.82 | 97.00 % | 209 |

  The fallback renders BSL, and this fresh frame agrees with the restored `s1-taumc-*` frames (see remark 8).
- Jars (`run/s1-fix-verify.out`): after the notice edit below (its final text, reflowed),
  `./gradlew verifyDistributedJar verifyDiagnosticsJar sourcesJar` gave `BUILD SUCCESSFUL in 10s` (`jar`, `remapJar`,
  `diagnosticsJar`, `sourcesJar` and both verify tasks executed). The mod, diagnostics and sources jars each carry
  `LICENSE` 34,523 B, `LICENSE-GPL-3.0.txt` 35,149 B, `LICENSE-LGPL-3.0.txt` 7,931 B and `THIRD_PARTY_NOTICES.md`
  13,143 B (was 13,059 B), and the three notices hash the same as the working-tree file. The mod jar is now
  3,644,508 B (3,644,337 B at `f7232573`), still 1,442 entries; its nested jars and `ContainedDeps` are unchanged. (An
  earlier `sourcesJar`-only run, `run/s1-fix-sourcesjar.out`, preceded the reflow.)

### Remarks

| # | Remark | Resolution |
|---|---|---|
| 1 | The notice ("its Java sources carry no headers") and Deviation 2 ("no Java source in the sources jar has a header") are wrong: the Commons Collections sources carry ASF headers. | **Fixed.** Counted in `run/lib-src/glsl-transformer/`: 257 `.java` files; douira's 233 under `io/` have no `Copyright`, `Licensed under` or `SPDX` line; all 21 under `org/apache/commons/collections4/` carry "Licensed to the Apache Software Foundation (ASF) ..."; 3 ANTLR-generated files (`gen/`, `.antlr/`) have only a "Generated from ..." line. The notice now says "douira's own Java sources carry no headers (the bundled Commons Collections subset carries the ASF Apache-2.0 header)"; Deviation 2 is corrected in place. The central claim stands: no copyright line exists for douira's code. |
| 2 | The ASF headers point to a NOTICE file that neither the library's jars nor Demonica's notices reproduce (Apache-2.0 section 4(d)). | **Not done; added to Open questions 1**, with the fifth-file question it belongs to. |
| 3 | The `IrisLogging` defect predates the step and affects about twenty other calls; `warn(String, Object...)` would fix all of them. | **Not done; Open questions 6**, with the call list. S1 took the minimal local fix, as the verifier suggested. |
| 4 | "Demonica's changes to ported files" under AGPL-3.0 may read better as "also offered under AGPL-3.0" (GPL-3.0 section 5(c)). | **Not changed; Open questions 5.** The wording is the brief's, and a license statement is the maintainer's call. |
| 5 | DECISION.md's Strategy row leaves out "Upstream Iris idioms may be used next to the verbs wherever a file is being brought closer to Iris." | **Fixed.** The sentence is added to the row, verbatim from the plan's header table. |
| 6 | `CacheKey` does not include the engine; correct while the engine is fixed per JVM. | **Recorded** in Notes for the next step. No code change. |
| 7 | The full suite in `./gradlew build` came from Gradle's cache; a fresh `--rerun` gave the same result. | **Agreed.** The follow-up build above executed `:test` (549 tests, 1 skipped, 0 failures). |
| 8 | The `s1-taumc-*.png` frames are re-encodings of an rgb24 decode; their provenance rests on the report. | **Agreed**, and the unknown-value dev run adds a freshly written taumc-engine frame from the same script: 0.11 % of pixels differ from `s1-taumc-1-bsl.png` (0.01 % for the no-pack pair). |
| 9 | Spike (c) raises `ParseCancellationException`, not `ParsingException`; later steps must catch both. | **Already covered** by Measurements and Notes for the next step. No change. |
| 10 | Scratch output of the verification: `run/verify-s1-*.out`, init script in the verifier's scratchpad; no tracked file changed. | **Noted.** This follow-up's own output is `run/s1-fix-{bogus,tests,build,bogus-dev,verify,sourcesjar}.out`; the frames are `run/client/screenshots/s1-bogus-*.png`. |

Files changed by the follow-up: `shader/src/main/java/net/coderbot/iris/pipeline/transform/TransformPatcher.java`,
`THIRD_PARTY_NOTICES.md`, `docs/glsl-transformer_adoption/DECISION.md`, this report and `STATUS.md`. Status stays
**done**: every item under "Done when" still holds on the fixed tree.
