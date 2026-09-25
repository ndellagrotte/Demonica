# Adopting glsl-transformer

Date: 2026-09-25. Tree: `dev` at `8e19cb75` (0.3.0-SNAPSHOT). Status: **Draft, not started.**

The file inventory (Appendix A) and the byte counts in the briefs were measured at `11b6c618` earlier the same day; none of the transform, GLSM or test files it lists changed between the two commits (the commits in between removed Reese's Sodium Options and Actinium's performance features). The build-file and notice line numbers were checked at `8e19cb75`.

This page is the migration plan from TauMC's `org.taumc:glsl-transformation-lib` to douira's `io.github.douira:glsl-transformer`, the library upstream Iris is written against. It is written as twelve briefs, one per agent session. Each brief assumes a fresh agent with a 1M-token context window, names exactly what to read, and is sized so that a session that fully implements the step ends under about 40% of that window (400K tokens). Section 5 explains how the budgets were estimated.

Background: the session transcript `chatlog_library_swap.md` (kept locally next to this page, not committed) records the research that led here (why Angelica left glsl-transformer in Oct 2024, the license position, the size of the change). [`../SCOPE_RESEARCH.md`](../SCOPE_RESEARCH.md) is the wider survey of the repository; this plan overlaps it only where it deletes the SPIR-V path (its item F).

## Decisions this plan is built on

Made by the maintainer on 2026-09-25.

| Decision | Choice |
|---|---|
| License | Demonica's own code is relicensed to AGPL-3.0. Code ported from Angelica and Iris stays LGPL-3.0, code ported from Actinium and the GTNHLib port in S8TNLib stays GPL-3.0; GPLv3 §13 and AGPLv3 §13 permit the combination. The change is made in Step 1, before the library enters the jar. |
| Strategy | Adapter first. One class, `ShaderAst`, re-implements the nineteen verbs Demonica calls on TauMC's `Transformer` on top of glsl-transformer's AST, and is proven equal to TauMC verb by verb while both libraries sit on the classpath. Transformer classes then port mechanically. Upstream Iris idioms may be used next to the verbs wherever a file is being brought closer to Iris. |
| Scope | Full removal. The Iris pipeline and GLSM's `CompatShaderTransformer` both move; TauMC's library and its ANTLR parser leave the jar at the end. The unreachable SPIR-V/GLES translation path (`SpirvShaderTranslator`, `GlslVulkanPreprocess`, `SpirvCompiler`) is deleted, not ported. |

What this document is not: it is not a license opinion (the reasoning is in the chatlog and in the decision record Step 1 writes), it is not the trimming plan in SCOPE_RESEARCH, and nothing in it changes what a shader pack sees. A pack that renders today must render identically at the end; the parity harness in section 3.5 is how that is checked.

## 1. Why, and the end state

Why (from the chatlog): Iris's transform code is written against glsl-transformer, so improvements there port directly instead of being translated across two parse-tree APIs. glsl-transformer is a documented AST with identifier and node indexes, matchers and templates, where glsl-transformation-lib is a thin, undocumented layer over an ANTLR parse tree with no local sources. The Java constraint that kept Angelica on TauMC's library (glsl-transformer 3.x needs Java 21) does not apply to Demonica, which compiles with `--release 21`. The accepted costs: the AGPL license, and the transform layer no longer being shared with Angelica, Actinium and Celeritas, which all use TauMC's library. The rewrite is bounded: 431 lines across 19 main files reference the library's types, plus 125 lines across 5 tests (Appendix A).

End state, checked in Step 11:

- `shader/src/main/java/net/coderbot/iris/pipeline/transform/` holds `TransformPatcher` (facade and cache, unchanged API), `ShaderTransformer` (the orchestrator, now on glsl-transformer), `Patch`, `PatchShaderType`, `parameter/` (unchanged) and `transformer/` (the ported transformer classes and `ShaderAst`). This mirrors Iris 26.1's `transform/{TransformPatcher, parameter/, transformer/}` layout.
- `glsm/`: `CompatShaderTransformer` runs on `ShaderAst`; `GlslTransformUtils` keeps only its regex helpers; the `shader/` package's SPIR-V path, `ShaderTransformPostProcessor` and `GLSMHooks.postTransformProcessor` are gone, and so are the `lwjgl-shaderc`/`lwjgl-spvc` compile-only dependencies.
- Build: `contain('io.github.douira:glsl-transformer:3.0.0-pre3') { transitive = false }` and one `antlr4-runtime`; no `org.taumc:glsl-transformation-lib` anywhere.
- License and notices: AGPL-3.0 project license, GPL and LGPL texts still shipped for the ported parts, glsl-transformer listed as a contained AGPL library.
- Tests: no test uses TauMC as an oracle; a corpus replay test and a committed mini-corpus guard the transform output.

## 2. Reference material

Everything an agent needs exists locally or at a stable URL. Read by file, never by tree.

| Material | Where | Role | Size |
|---|---|---|---|
| glsl-transformer 3.0.0-pre3 | Maven Central `io.github.douira:glsl-transformer:3.0.0-pre3` (published 2025-08-23; Gradle metadata: JVM 21; POM: AGPL-3.0, depends on `antlr4-runtime:4.13.1` and the full `antlr4` tool, compile scope) | The target. The sources jar (`-sources.jar`, 577 KB) and javadoc jar exist next to it; Step 1 unpacks the sources under `run/lib-src/glsl-transformer/` | ~4,000 lines of API classes worth reading; read by class |
| Iris 26.1 (mod 1.11.2, MC 26.1.2) | `/home/nick/IdeaProjects/schmaloogium-clean/reference-src/Iris-26.1/common/src/main/java/net/irisshaders/iris/pipeline/transform/` | The 3.0.0-pre3 idioms as Iris uses them: `TransformPatcher` (431 lines, `EnumASTTransformer` setup), `transformer/CommonTransformer` (501), `transformer/CompatibilityTransformer` (751), `transformer/SodiumTransformer` (362), the DH transformers, `parameter/` | 25 files, 4,256 lines, 175 KB |
| Angelica's last glsl-transformer state | Demonica's own git history: `git show d96ac280:src/main/java/net/coderbot/iris/pipeline/transform/<File>.java`. `d96ac280` (2024-10-27) is the parent of PR #680 `1df1ec35`, "Conversion to glsl-transformation-lib". `git diff d96ac280 1df1ec35 -- src/main/java/net/coderbot/iris/pipeline/transform/` is the reverse of the Iris side of this migration | Semantics of the Iris-side transformers before the switch (glsl-transformer 1.0.0 API, older package names) | 10 files, 46 KB: AttributeTransformer 111 lines, CompatibilityTransformer 544, CompositeDepthTransformer 30, CompositeTransformer 30, SodiumTerrainTransformer 112, TransformPatcher 226, parameters |
| Oculus 1.12.2 | `/home/nick/IdeaProjects/schmaloogium-clean/reference-src/Oculus-1.12-1.12.2/src/main/java/net/coderbot/iris/pipeline/transform/` | The same code on glsl-transformer 1.0.1; a second reading where Angelica's differs | 10 files, 1,202 lines |
| TauMC glsl-transformation-lib sources | `https://github.com/TauMC/glsl-transformation-lib`, `src/main/java/org/taumc/glsl/` (`Transformer.java` 30 KB, `Util.java` 10.6 KB, `ShaderPrinter.java` 15.5 KB, and about 30 listener classes of 0.5 to 3.4 KB). The pinned build is `0.2.0-32.g7dd88a4-GTNH`; try the commit `7dd88a4` first, then `main` | The semantics of every verb `ShaderAst` must reproduce. There is no local sources jar; the cached jar has classes only | ~75 KB |
| glsl-transformer documentation | `docs/overview.md` and `docs/AST-examples.md` in the IrisShaders/glsl-transformer repository (`main`); README license section | Principles: use `Root` indexes, use `Matcher`, mutate the AST, use `Template`, avoid creating new roots, node constructors need a root session | 10 KB |
| GLSM's `CompatShaderTransformer` | `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/CompatShaderTransformer.java` | No glsl-transformer version ever existed (Angelica wrote it after the switch, extracted to its `glsm` module on 2026-03-20). Ported by semantics through `ShaderAst` | 1,214 lines, 55 KB |
| This session's inventories | Appendix A and B of this page | Which file calls which verb, how often; test oracles; build wiring | |

### API drift, 1.0.x (Angelica `d96ac280`, Oculus) to 3.0.0-pre3 (Iris 26.1)

Derived from the imports of both code bases; verify each row against the unpacked sources before relying on it.

| 1.0.x | 3.0.0-pre3 |
|---|---|
| `ast.node.basic.ASTNode` | `ast.node.abstract_node.ASTNode` |
| `job_parameter.JobParameters` | `ast.transform.JobParameters` |
| `cst.token_filter.{TokenFilter, TokenChannel, ChannelFilter}` | `token_filter.{TokenFilter, TokenChannel, ChannelFilter}` |
| `cst.core.SemanticException` | `parser.ParsingException`, `ast.transform.TransformationException` |
| `Root.indexBuildSession(tree, ...)` | `setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT)` on the transformer; `ast.query.RootSupplier` |
| `transformer.setParseTokenFilter(...)` | `transformer.setTokenFilter(...)` |
| `tree.prependMain(...)` / `appendMain(...)` | `tree.prependMainFunctionBody(t, ...)` / `appendMainFunctionBody(t, ...)` |
| `root.identifierIndex.rename(a, b)` | `root.rename(a, b)` |
| `new AutoHintedMatcher<>(source, ASTParser::parseExpression)`-style constructors | `new AutoHintedMatcher<>(source, ParseShape.EXPRESSION)`; `parser.ParseShape` |
| `Root.indexNodes(root, () -> ...)` | `t.parseExpression(root, ...)`, `Template.withExternalDeclaration(...)`, `Template.getInstanceFor(root, ...)` |
| (no equivalent) | New in Iris 26.1's imports: `ast.node.VersionStatement`, `ast.query.match.HintedMatcher`, `ast.query.RootSupplier`, `ast.node.type.qualifier.LayoutQualifier`, `NamedLayoutQualifierPart`, `BuiltinFixedTypeSpecifier`, `StructMember`, `StructDeclarator`, `ArraySpecifier`, `ArrayAccessExpression` |

Unchanged names: `ast.query.Root`, `ast.node.TranslationUnit`, `ast.transform.{ASTParser, ASTInjectionPoint, Template, EnumASTTransformer}`, `ast.query.match.{Matcher, AutoHintedMatcher}`, `ast.print.PrintType`, `util.{Type, LRUCache}`.

License facts, for the notices: glsl-transformer's POM says LGPL-3.0 up to 1.0.1 and AGPL-3.0 from 2.0.0 on (2.0.2 and 3.0.0-pre3 checked); the repository's LICENSE file is AGPL-3.0 at every tag from v1.0.1 up. The README says: "`glsl-transformer` is licensed under the GNU Affero General Public License v3.0. Software that uses this library must itself be licensed as AGPLv3. However, there are two special cases: Certain projects can receive a specific additional noncommercial permission [...]. You can obtain a commercial license [...]. Please contact the author (douira) in these cases." Iris ships it with a one-line `LICENSE-DEPENDENCIES` warning. Angelica's old build line carried the comment `// glsl-transformer Noncommercial License 1.0.0`; that grant was Angelica's and does not transfer.

## 3. Architecture of the migration

### 3.1 The pipeline today

`TransformPatcher` (`shader/.../pipeline/transform/TransformPatcher.java`, 281 lines) is the facade every caller uses (`Iris`, `DeferredWorldRenderingPipeline`, `CompositeRenderer`, `FinalPassRenderer`, `ShadowCompositeRenderer`, the DH programs). It builds a `Parameters` object per patch kind, keeps a 400-entry LRU cache keyed on sources plus parameters plus the adaptive-shadow-bounds flags, and calls `ShaderTransformer.transform` or `transformCompute`. It imports nothing from TauMC's library.

`ShaderTransformer` (496 lines) is the orchestrator, and the only file that knows the whole sequence. Per stage: find `#version`; hoist the version if the source uses features that need a higher one (`VERSION_REQUIREMENTS`, driven by `RenderSystem` capabilities); raise to the stage minimum (330, tessellation 400); negotiate against `RenderSystem.getMaxGlslVersion()`; regex pre-passes (`GlslTransformUtils.replaceTexture`, `renameReservedWords`, `CompatShaderTransformer.fixupQualifiers`, and for composite fragments the cloud regex patches in `CompatibilityTransformer`); `ShaderParser.parseShader` and `new Transformer(parsed.full())`; `doTransform` (dispatch on `Patch`: ATTRIBUTES, CELERITAS_TERRAIN, COMPOSITE, COMPUTE, DH_TERRAIN, DH_GENERIC, then always `TextureTransformer` and `CompatibilityTransformer.transformEach`); the `#extension` lines are recovered from the pre-parser tree. After all stages, `CompatibilityTransformer.transformGrouped` fixes in/out mismatches across stages. Then each tree is printed by `GlslTransformUtils.getFormattedShader` (a token-spaced serializer: one space after every token, a newline after `;`, `{` and `}`) under a header of `#version N core` plus the extensions, and `restoreReservedWords` undoes the pre-pass renames. CELERITAS_TERRAIN vertex shaders get Celeritas's `chunk_vertex.glsl` header appended as text after printing. Every patch kind starts with `CommonTransformer.transform`, which calls `AdaptiveShadowBoundsTransformer`.

Sources reach the transformer already preprocessed: `ShaderPack.java:326` runs `JcppProcessor.glslPreprocessSource` on every program. The dumped output of the last dev run (138 files under `run/client/patched_shaders/`) contains no directive other than `#version` and, in the four terrain vertex shaders, the Celeritas header's own `#ifdef` block. Step 2 re-checks this on recorded inputs.

GLSM's path is separate: `GLStateManager.glShaderSource` (line 5551) runs `GlslTransformUtils.renameReservedWords` and, when the FFP shader manager is enabled, `CompatShaderTransformer.transform(src, isFragment)` on every non-Iris shader a mod submits. That class strips GLES precision guards, evaluates the preprocessor with its own hand-written evaluator (lines 283-981, no library), renames before parsing, parses, fails fast on syntax errors, applies about 74 verb calls, serializes, and falls back to a version fix-up on any exception. Iris output always starts with `#version N core`, N ≥ 330, so it is not transformed twice.

### 3.2 Engine switch

`TransformPatcher` gets an engine selector, read once from the system property `demonica.glsl.engine` (`taumc` or `douira`; default `taumc` until Step 8 flips it, removed in Step 11), logged at first use. Its two private entry points route to `ShaderTransformer` (old) or `AstShaderTransformer` (new). `clearCache()` clears both engines' session state. `CompatShaderTransformer` reads the same property for its own parse/transform/print block from Step 10 on. Nothing else in the code base sees the switch.

Until a patch kind is ported, the new engine throws `UnsupportedOperationException("glsl-transformer engine: <kind> not ported yet")`. The replay test (3.5) filters by kind, so partial engines are testable. A dev run on the new engine before Step 6 therefore fails to load any pack (gbuffers programs are ATTRIBUTES); end-to-end runs start in Step 6.

### 3.3 Package layout during and after

During the migration:

```
shader/src/main/java/net/coderbot/iris/pipeline/transform/
  TransformPatcher.java            facade; gains the switch and the recorder hook
  ShaderTransformer.java           old engine (TauMC), untouched except for the extraction in 3.4
  AstShaderTransformer.java        new engine (glsl-transformer), grows kind by kind
  VersionNegotiation.java          engine-neutral: hoisting, stage minimum, negotiation (extracted in Step 5)
  <old transformer classes>.java   TauMC; deleted in Step 11
  parameter/                       unchanged
  transformer/                     new: ShaderAst.java and the ported transformer classes
  corpus/                          new: TransformCorpusRecorder.java (main code, inert without the property)
```

After Step 11, `AstShaderTransformer` is renamed to `ShaderTransformer`, the old classes are gone, and the layout matches Iris 26.1 (`TransformPatcher`, `parameter/`, `transformer/`), which is the point: a future Iris change lands in the file with the same name.

The new classes keep the old simple names inside `transformer/` (`CommonTransformer`, `CompatibilityTransformer`, ...). While both exist, greps must include the directory; the briefs say "old = `transform/*.java`, new = `transform/transformer/*.java`".

### 3.4 `ShaderAst`, the adapter

`transform/transformer/ShaderAst.java` wraps one parsed program: it holds the `ASTParser` (`t`), the `TranslationUnit` (`tree`) and the `Root` (`root`), and exposes the nineteen verbs under their TauMC names so that a transformer class ports by changing its parameter type and nothing else. Two deliberate signature changes: `findType` returns glsl-transformer's `Type` (or a small sealed result covering sampler kinds) instead of a `GLSLLexer` token integer, and the three-argument `replaceExpression(source, replacement, GLSLParser::function_definition)` that `AdaptiveShadowBoundsTransformer` uses becomes `replaceFunctionDefinition(name, newSource)`. `mutateTree` disappears; callers get `tree`/`root` directly. Appendix B maps each verb to its TauMC implementing class and to the glsl-transformer building block that implements it.

Parsing follows Iris 26.1's `TransformPatcher`: an `EnumASTTransformer<Parameters, PatchShaderType>` (or an `ASTParser` with a `Root` from `RootSupplier.PREFIX_UNORDERED_ED_EXACT`, which is what makes prefix queries such as `prefixQueryFlat("iris_")` available), the lexer's `version` set from the `#version` directive, and a `ChannelFilter` on `TokenChannel.PREPROCESSOR`. Iris's filter throws on any unparsed directive; Demonica's must instead drop the directive and log it, because that is what the old engine did (the pre-parser tree was used only to recover `#extension` lines). `#version` and `#extension` are grammar statements in glsl-transformer, not preprocessor tokens; the orchestrator strips them from the tree and emits its own header exactly as today.

Printing uses `PrintType.INDENTED`. The format differs from the token-spaced serializer, so no test may compare raw strings; the `GlslTokens` helper (3.5) is the comparison tool everywhere.

Thread safety: `TransformPatcherCacheTest` drives eight threads through a cache miss at once, and the cache code comments that "another transform may have completed while this caller was parsing GLSL". glsl-transformer's transformer objects are stateful. The new orchestrator therefore either creates its transformer per call or guards one instance with a lock; Step 5 measures both with the `transformMs` log line and keeps the faster one that passes the test.

### 3.5 Parity harness

The migration is verified by replaying real inputs through both engines, not by reading diffs of Java.

**Recorder.** `transform/corpus/TransformCorpusRecorder` is main code that does nothing unless `demonica.glsl.corpus=<dir>` is set. `TransformPatcher` calls it on every cache miss with the inputs, the `Parameters`, the GLSL capability (`RenderSystem.getMaxGlslVersion()`, `supportsSSBO()`, `supportsImageLoadStore()`), the adaptive-shadow-bounds flags the cache key already reads, the engine name, the output map and the elapsed time. `CompatShaderTransformer.transform` calls it with `(source, isFragment, output)`. One directory per call: `<dir>/<domain>/<seq>-<patch>-<hash8>/` with `case.properties` (flat keys; the texture map as `textureMap.N=name|type|stage=replacement`), `in.<stage>.glsl` and `out.<engine>.<stage>.glsl`. Stages are `vertex, geometry, tess_control, tess_eval, fragment, compute`; the compat domain uses `in.glsl`/`out.<engine>.glsl` and `isFragment`.

**Replayer.** `src/test/java/net/coderbot/iris/pipeline/transform/TransformCorpusReplayTest.java` is skipped unless a corpus directory is configured. Because the test JVM is forked, configuration travels as Gradle properties that the root `test {}` block forwards as system properties: `-PglslCorpusDir=<abs>`, `-PglslReplayEngine=taumc|douira`, `-PglslReplayPatches=COMPOSITE,COMPUTE,...`, `-PglslReplayRecord=true`. For each case it sets the capability through `RenderSystem.initializeGlslCapabilityForTesting` (extending that hook with the two boolean flags if it lacks them), rebuilds the `Parameters`, calls the selected engine's `transform`/`transformCompute` **directly** (not through the cache), and compares each stage with the recorded `out.taumc.<stage>.glsl` using `GlslTokens`. Diffs go to `build/reports/transform-replay/<case>.<stage>.diff`. `src/test/resources/transform-replay/accepted.txt` lists tolerated differences as `<case glob> | <stage> | <reason>`; an entry without a reason fails the test. The summary prints cases, identical, accepted, failing and unsupported (kind not ported). With `taumc` against `taumc` outputs the result must be 100% identical; that run is the determinism check.

**`GlslTokens`.** A test helper: strip `//` and `/* */` comments, keep each preprocessor line as one token with collapsed whitespace, split the rest into identifiers, numbers (including `1.0e-3f`), multi-character operators and single punctuation; `text()` joins tokens with one space and breaks lines after `;`, `{` and `}`; `diff(a, b)` is a line-based LCS diff of those texts; `contains(tokens, "color = iris_FrontColor ;")` replaces the raw `contains` assertions in the tests.

**Corpora.** Pack-derived recordings are third-party pack code and stay local under `run/transform-corpus/<pack>/` (`run/` is gitignored); the capture scripts under `scripts/glsl-corpus/` regenerate them. A hand-written mini-corpus with one case per patch kind, plus compat cases, lives in `src/test/resources/transform-corpus/` with its `out.taumc.*` files committed (generated by the replayer's record mode while TauMC is still present), so the snapshot survives Step 11.

**Baselines.** Step 2 also keeps the three packs' screenshots and the `[ShaderTransformCache] ... transformMs=` totals from `-Ddemonica.glsmPerfDebug=true` runs; Step 8 and Step 11 compare against them.

### 3.6 What stays as it is

The regex passes around parsing (`replaceTexture`, `renameReservedWords`/`restoreReservedWords`, `fixupQualifiers`, the three cloud patches) exist to keep TauMC's grammar happy or to fix packs. The new engine keeps all of them for parity. Whether glsl-transformer's version-aware lexer makes the reserved-word passes unnecessary is a Step 12 question, answered by replaying the corpus without them.

`Parameters` and its subclasses, `Patch`, `PatchShaderType`, the cache, and every caller of `TransformPatcher` are untouched. The Celeritas header append stays text. `CompatShaderTransformer`'s preprocessor evaluator, `fixupQualifiers`, `generatePassthroughVertexShader` and `needsTransformation` are library-free and stay.

### 3.7 Exit points

- **A, after Step 8.** Iris programs run on glsl-transformer; GLSM still uses TauMC; both libraries ship as nested jars. The jar is coherent and releasable. Steps 9 to 11 can wait.
- **B, after Step 11.** TauMC is gone.

Rolling back before A is switching the property default; rolling back after A means reverting the branch.

## 4. Rules for every step

These apply to each brief in section 6 and are not repeated there.

**Branch and commits.** Work on `feat/glsl-transformer`, created from `dev` by Step 1 (`git switch -c feat/glsl-transformer dev`); later steps `git switch` to it. Commit at least once per step, message `glsl-transformer: S<N> <what>`, ending with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Never push, never merge into `dev`, never tag; the maintainer does that. Never edit anything under `.reference/` or `run/` except the corpus, scripts and lib-src directories this plan names.

**Reports and status.** Before starting, read `docs/glsl-transformer_adoption/STATUS.md` and the reports of the steps you depend on (`docs/glsl-transformer_adoption/reports/S<NN>-<slug>.md`), not the git log. When done, write your own report with these headings: Status (done, partial, blocked), Commits, What changed (files added, changed, deleted), Commands run and their outcomes (quote the summary lines), Measurements, Residual diffs (a table: case, stage, classification, action), Deviations from the brief, Open questions, Notes for the next step. Then update the row in `STATUS.md` (step, status, commit, date, report link). A report is the only handoff; the next agent does not see your transcript.

**Faithful outcomes.** If a check fails, the report says so with the output. A skipped check is listed as skipped. "Done" means every item under "Done when" holds.

**Context budget.** The 40% target is met by reading less, not by working less:

- Read only the files the brief lists, in the ranges it gives. Use `sed -n 'A,Bp'` for ranges and `grep -n` to locate; never `cat` a file over 1,000 lines. `GLStateManager.java` (8,354 lines) is only ever grepped.
- Library sources by class, never by jar or tree. Start with `grep -n "public" <Class>.java` and read the methods you need.
- Gradle output through a filter: `2>&1 | grep -E 'Tests run|FAILED|error:|warning: \[|BUILD|> Task .*FAILED' | tail -40`, or `| tail -60`. A raw Unimined build prints thousands of lines.
- Dev-run output redirected to `run/<name>.out`, then grepped (`Dev step`, `Dev shader pack`, `ShaderTransformCache`, `Shader compilation failed`, `Exception`, `CompatShaderTransformer`). Never read `run/client/logs/latest.log` whole.
- At most two `./gradlew check` runs per step; use the filtered `:test --tests` commands from Appendix C for iteration.
- Keep a running scratch file of findings so that a compaction does not lose them.
- If the budget is at 300K and the step is not done, stop, write a partial report, and let the next session continue.

**Dev-run harness gotchas** (from the ledger and the maintainer's notes): scripts are passed as `-PdevScript=@<path>` relative to `run/client` (absolute paths work); the harness's `pack <file>` choice persists across runs, so every script starts with `pack off`; `pack` does nothing while Iris is off; a failed `expect-screen` stops the script before its `exit` and leaves the client open, so run the client in the background with a timeout and kill it if steps stop logging; `-PdevProps=k=v,k2=v2` splits only at a comma followed by `key=`; `patched_shaders/` is wiped when the next pipeline starts, so one pack per run if you want its dump; `run/client/options.txt` is a hand-written 12-line file that any settings save rewrites; GL debug logs are noisy; every dev run audits the Celeritas anchors and prints "All 316 anchors ... hold" (expected). Screenshot diffs: no PIL or ImageMagick on this machine; decode with `ffmpeg -f rawvideo -pix_fmt rgb24` into numpy; two frames of a still camera differ only in waving plants and water.

**Definition of done for a port.** Compiles under `--release 21`; the step's filtered tests are green on both engines where both apply; the replay for the step's patch kinds has no unexplained diff; the report is written; the commit exists.

## 5. Step map

```
S1 build wiring, license, spike
 ├── S2 corpus recorder + replayer + baselines        (needs S1's switch; desktop for dev runs)
 └── S3 ShaderAst core verbs ── S4 ShaderAst structural verbs
                                   │
     S2 + S4 ──────────────────────┴── S5 orchestrator + COMPOSITE/COMPUTE
                                        └── S6 ATTRIBUTES + CELERITAS_TERRAIN   (first end-to-end run)
                                             └── S7 DH + AdaptiveShadowBounds
                                                  └── S8 flip default, port tests, full run   = exit point A
                                                       └── S9 GLSM subtractions + utilities
                                                            └── S10 CompatShaderTransformer
                                                                 └── S11 remove TauMC, release checks   = exit point B
                                                                      └── S12 optional payoff
```

S2 and S3/S4 are independent and can run as two sessions in parallel on the same branch if they commit disjoint files (S2: `TransformPatcher`, `corpus/`, tests, scripts; S3/S4: `transformer/ShaderAst`, parity tests). Whoever finishes first owns `GlslTokens`; the other reuses it.

### Budget table

Estimates use 3.5 bytes per token for Java and Markdown, count a filtered build or test cycle as about 5K tokens and a dev run's grepped output as about 8K, and add 60% for tool-call framing, reasoning and writing. The target is under 400K per session; every step is planned to land between a quarter and a half of that so that a bad afternoon of debugging still fits.

| Step | Source to read | Read tokens | Cycles | Estimated session total |
|---|---|---|---|---|
| S1 | 60 KB (build files by range, notices, `TransformPatcher`, chatlog) | 17K | 6 builds | ~100K |
| S2 | 45 KB (`TransformPatcher`, parameters, capability hooks, harness, scripts) | 13K | 4 tests + 5 dev runs | ~120K |
| S3 | 230 KB (TauMC sources 75 KB, glsl-transformer classes ~120 KB read selectively, Iris `CommonTransformer` + `TransformPatcher` 40 KB) | 65K | 8 tests | ~200K |
| S4 | 120 KB (TauMC listeners, Iris `CompatibilityTransformer`, Demonica's `CompatibilityTransformer` and `AdaptiveShadowBoundsTransformer`) | 35K | 8 tests | ~160K |
| S5 | 100 KB (`ShaderTransformer` whole, seven small transformers, `ShaderAst` API, Iris `TransformPatcher` setup lines) | 30K | 8 tests | ~150K |
| S6 | 50 KB | 15K | 6 tests + 3 dev runs | ~130K |
| S7 | 55 KB | 16K | 6 tests | ~130K |
| S8 | 60 KB (six tests, `accepted.txt`, scripts) | 17K | 6 tests + 4 dev runs + 1 full `:test` | ~130K |
| S9 | 40 KB | 12K | 5 builds | ~90K |
| S10 | 90 KB (`CompatShaderTransformer` outside its preprocessor, its test, compat corpus) | 26K | 6 tests + 1 dev run | ~130K |
| S11 | 60 KB | 17K | 2 `check` + 4 runs | ~130K |
| S12 | 70 KB | 20K | 4 tests | ~100K |

## 6. Steps

Each step has the same nine parts: Goal, Preconditions, Read, Do, Done when, Verify, Budget, Handoff, Risks. File sizes are bytes on `dev` at `11b6c618`.

### Step 1. Build wiring, license, spike

**Goal.** glsl-transformer 3.0.0-pre3 compiles, ships as a nested jar and runs on Demonica's toolchain; the license change and the notices are done before the library is in a build anyone could distribute; the engine switch exists with a stub new engine; a spike test records the parser configuration the later steps reuse.

**Preconditions.** None. Create the branch.

**Read.**
- `build.gradle` (35.7 KB), ranges only: 23-35 (java, `contain`), 140-170 (subprojects), 200-210 (repositories), 325-345 (`contain` calls), 420-445 (tests), 485-530 (`sourcesJar`, `jar`), 705-800 (`verifyDistributedJar`).
- `glsm/build.gradle` (1.2 KB).
- `THIRD_PARTY_NOTICES.md` (12.9 KB), `README.MD` lines 70-100, `docs/FORK.md` lines 70-90, `docs/PROVENANCE.md` lines 155-175, `src/main/resources/mcmod.info`.
- `shader/src/main/java/net/coderbot/iris/pipeline/transform/TransformPatcher.java` (13.4 KB).
- `docs/glsl-transformer_adoption/chatlog_library_swap.md` (11.6 KB), for the license reasoning to summarize in the decision record.
- The Cleanroom template's `build.gradle` (via the `cleanroom` MCP tool `get_project_template`) if you want to see the convention Demonica's `contain` follows; not required.

**Do.**
1. Dependency. In `glsm/build.gradle`, next to the TauMC line, add `implementation('io.github.douira:glsl-transformer:3.0.0-pre3') { transitive = false }`. In `build.gradle`'s `contain` block add the same coordinate with `transitive = false`. Keep TauMC and keep `org.antlr:antlr4-runtime:4.13.2` as the single runtime (glsl-transformer's parser was generated by 4.13.1; same major.minor, so ANTLR's runtime check does not warn). `transitive = false` also keeps out the full `antlr4` tool that the POM declares in compile scope. Confirm resolution with `./gradlew :glsm:dependencies --configuration compileClasspath 2>&1 | grep -E 'douira|antlr'`.
2. Sources for later steps. `curl -sL` the sources jar from `https://repo1.maven.org/maven2/io/github/douira/glsl-transformer/3.0.0-pre3/glsl-transformer-3.0.0-pre3-sources.jar` into `run/lib-src/` and unpack it to `run/lib-src/glsl-transformer/` (gitignored). Do not read it now. Record its SHA-256 and the jar's SHA-256 in the report.
3. License. `git mv LICENSE LICENSE-GPL-3.0.txt`; write the AGPL-3.0 text (`https://www.gnu.org/licenses/agpl-3.0.txt`, curl to file, do not read it) as `LICENSE`. Update the file lists in `jar {}` (line 504), `sourcesJar {}` (line 491) and `verifyDistributedJar`'s required entries (lines 733-735) to the four files: `LICENSE`, `LICENSE-GPL-3.0.txt`, `LICENSE-LGPL-3.0.txt`, `THIRD_PARTY_NOTICES.md`. Check with `grep -rn "LICENSE" build.gradle README.MD docs/*.md` that no other place lists the files.
4. Notices. `THIRD_PARTY_NOTICES.md`: rewrite the bold paragraph (lines 8-16) to say Demonica is AGPL-3.0 and how the LGPL-3.0 and GPL-3.0 parts combine (GPLv3 §13, AGPLv3 §13); add a row to the contained-libraries table: `io.github.douira:glsl-transformer:3.0.0-pre3` | AGPL-3.0 (`https://github.com/IrisShaders/glsl-transformer`) with a note that the jar carries no license file and the text ships as `LICENSE`; add a `### glsl-transformer (AGPL-3.0)` subsection under Notices with the copyright line from the repository and the README's license paragraph quoted; update `## In the mod jar` to four files. `README.MD`: the license section (lines 78-85) and credits (add douira and glsl-transformer next to the glsl-transformation-lib credit, which stays until Step 11). the `## License` sections at `docs/FORK.md:79` and `docs/PROVENANCE.md:164` (a sentence each). `mcmod.info:8`: add "glsl-transformer (douira)" to the credits string.
5. Decision record. `docs/glsl-transformer_adoption/DECISION.md`: date; the three decisions from the header of this plan; the options considered for the license (keep GPL-3.0 under §13; relicense; ask for a noncommercial grant) and why relicensing was chosen; what the relicensing covers (Demonica's own code: `com.demonica.*` and Demonica's modifications to ported files) and what it does not (files whose headers name another license: check with `grep -rln "Copyright\|Licensed under\|SPDX" src shader glsm --include=*.java | head`); the obligations that follow (source availability for the distributed jar; the AGPL §13 network clause, which does not apply to a client mod); a note that the author's README condition is now met without a grant.
6. Engine switch. In `TransformPatcher`: `enum Engine { TAUMC, DOUIRA }`, resolved once from `System.getProperty("demonica.glsl.engine", "taumc")` (unknown values log a warning and fall back to `taumc`), logged at first use; `transform`/`transformCompute` route to `ShaderTransformer` or to the new `AstShaderTransformer` (same package, same two static method signatures, both throwing `UnsupportedOperationException` with the patch kind for now); `clearCache()` calls `ShaderTransformer.clearSessionState()` and the new engine's equivalent. Also add a `static Engine engine()` accessor for the replay test and, later, `CompatShaderTransformer`.
7. Spike test. `src/test/java/net/coderbot/iris/pipeline/transform/GlslTransformerSpikeTest.java`, three tests: (a) a `#version 120` vertex shader with `attribute`, `varying`, `gl_TexCoord[0]`, `ftransform()` and `gl_MultiTexCoord0`, parsed with an `EnumASTTransformer` configured as Iris 26.1 does (`setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT)`, lexer version from the directive, a `ChannelFilter` on `TokenChannel.PREPROCESSOR` that drops and counts), then `root.rename("gl_TexCoord", "iris_TexCoord")`, `tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ModelViewMatrix;")`, `tree.prependMainFunctionBody(t, "iris_FogFragCoord = 0.0;")`, printed with `PrintType.INDENTED`; assert on tokens (write the minimal tokenizer inline; Step 2 or 3 will replace it with `GlslTokens`). (b) `#version 330 core` plus `#extension GL_ARB_shader_image_load_store : enable`: the extension statement is a node and survives printing. (c) a syntax error raises the library's exception with a line number. Keep the test: it is the reference configuration.
8. Build and jar. `./gradlew build` (filtered), then `unzip -l build/libs/Demonica-*.jar | grep -E 'glsl|antlr|LICENSE|NOTICES'` shows both GLSL jars, the ANTLR runtime and the four license files. `./gradlew verifyDistributedJar` passes (it derives the contained-jar list from `contain`).
9. Dev run. A short script (`pack off`, world, `pack BSL_v10.1.8.zip`, `shot s1-bsl`, `exit`) on the default engine renders BSL as before; the same with `-PdevProps=demonica.glsl.engine=douira` logs the engine choice and fails pack loading with the `UnsupportedOperationException` message in the log and no crash (Iris logs the error and turns shaders off).
10. Report and STATUS.

**Done when.** Both jars nested and the four license files present; `verifyDistributedJar` green; spike test green; the transform tests still green (`./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*'`); the two dev runs behave as described; notices, README, FORK, PROVENANCE, mcmod.info and DECISION.md updated; committed.

**Verify.**
```
./gradlew :glsm:dependencies --configuration compileClasspath 2>&1 | grep -E 'douira|antlr|taumc'
./gradlew :test --tests '*GlslTransformerSpikeTest' --tests 'net.coderbot.iris.pipeline.transform.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew build 2>&1 | grep -E 'FAILED|error:|BUILD' | tail -20
unzip -l build/libs/Demonica-*.jar | grep -E 'glsl|antlr|LICENSE|NOTICES'
./gradlew verifyDistributedJar 2>&1 | tail -15
./gradlew runClient -PdevScript=@scripts/s1.txt > run/s1-taumc.out 2>&1; grep -E 'Dev step|Dev shader pack|Shader compilation failed|engine' run/s1-taumc.out | head
./gradlew runClient -PdevScript=@scripts/s1.txt -PdevProps=demonica.glsl.engine=douira > run/s1-douira.out 2>&1; grep -E 'engine|UnsupportedOperation|Dev shader pack|Exception' run/s1-douira.out | head
```

**Budget.** ~100K. Reads about 17K; six filtered builds; two dev runs read by grep only. Do not read the AGPL text or the sources jar into the context.

**Handoff.** Report: resolved coordinates and both SHA-256 values; the path of the unpacked sources; the property name and the log line format of the engine switch; anything the spike showed about `#version 120` parsing; the list of files touched by the license change.

**Risks.** Maven Central rate limiting (a 429 disables the repository for the build; retry later, do not add mirrors); forgetting `transitive = false` pulls in the ANTLR tool (about 16 MB with its dependencies); a fourth license file missed in one of the three lists fails `verifyDistributedJar`; `readme` claims of "three files" in other docs.

### Step 2. Corpus recorder, replayer, baseline capture

**Goal.** A recorded corpus of real transform calls for the three packs and for mod compat shaders, a replay test that runs either engine on it and diffs token streams against the TauMC output, a committed mini-corpus, and the performance and screenshot baselines.

**Preconditions.** Step 1 merged on the branch (switch exists). A desktop session, because dev runs open a window.

**Read.**
- `TransformPatcher.java` (13.4 KB); `parameter/*.java` (6 files, 6.6 KB); `net/coderbot/iris/gbuffer_overrides/matching/InputAvailability.java`; `TextureStage`, `TextureType`, `helpers/Tri` by grep for their fields.
- `AdaptiveShadowBoundsStats`: grep for `isInstrumentationEnabled`, `getActiveBinding`, `shaderVersionMarker`.
- `glsm/.../RenderSystem.java` lines 340-420 and 570-600 (`supportsImageLoadStore`, `supportsSSBO`, `getMaxGlslVersion`, `initializeGlslCapabilityForTesting`).
- `glsm/.../CompatShaderTransformer.java` lines 60-200 (`transform`, `needsTransformation`, `dumpShader`).
- `src/main/java/com/demonica/dev/DevHarness.java` lines 40-90 (the step list) and `run/client/scripts/cp4.txt`, `cp5.txt`, `guard-core.txt` for script shapes.
- `build.gradle` lines 534-557 (the `test {}` block) to add the property forwarding.

**Do.**
1. Recorder (3.5): `transform/corpus/TransformCorpusRecorder.java`, inert without `demonica.glsl.corpus`. Hook it into `TransformPatcher`'s cache-miss paths (graphics and compute), recording `Parameters` before the call (its `type` field is mutated during the transform), and into `CompatShaderTransformer.transform`. Serialize `Parameters` by kind: `AttributeParameters` (`hasGeometry`, `inputs.texture/lightmap/color`), `TextureStageParameters` (stage, texture map), `ComputeParameters` (stage, texture map), `DHParameters` (texture map), `CeleritasTerrainParameters` (nothing). Write `transformMs`.
2. `GlslTokens` test helper and the replay test as specified in 3.5, with the four Gradle properties forwarded in the root `test {}` block (`systemProperty 'demonica.glsl.corpus.dir', project.findProperty('glslCorpusDir') ?: ''`, and likewise for engine, patches, record). Extend `RenderSystem.initializeGlslCapabilityForTesting` with the two boolean flags if it lacks them. If the adaptive-shadow-bounds flags have no test hook, skip cases recorded with instrumentation on and count them as unsupported.
3. Determinism: run the replayer with `taumc` against the `taumc` outputs. It must be 100% identical. If it is not, find the nondeterminism (hash-ordered collections in the old engine) and report it; do not fix the old engine unless the fix is a one-line ordering change.
4. Mini-corpus: `src/test/resources/transform-corpus/` with hand-written cases: one COMPOSITE (vertex + fragment, `#version 120`, uses `gl_FragColor`, `texture2D`, `gl_TexCoord[0]`), one COMPOSITE at `#version 330 core`, one COMPUTE, one ATTRIBUTES (with `mc_Entity`, `mc_midTexCoord`, `gl_MultiTexCoord3`), one CELERITAS_TERRAIN (vertex + fragment), one DH_TERRAIN, one DH_GENERIC, one with an unused helper function and a const parameter (for `transformEach`), one with a vertex/fragment in/out mismatch (for `transformGrouped`), one fragment with a `texture2DShadow2x2`-style PCF helper (for `AdaptiveShadowBounds`), two compat cases (a `#version 120` mod shader with `gl_ModelViewMatrix`, `varying`, `shadow2D`; the BetterPortals fixtures already in `src/test/resources/compat_shaders/`). Generate their `out.taumc.*` with the record mode and commit them.
5. Capture scripts under `scripts/glsl-corpus/`: `bsl.txt`, `complementary.txt`, `vanilla.txt` (shape of `cp4.txt`: `pack off`, `world`, `wait`, `chunks`, `shot`, `pack <zip>`, `wait 20`, `chunks 1200`, `wait 100`, `shot`, `stats`, `exit`; add the Nether round trip from `cp5.txt` to the BSL script if it is a plain portal walk), and `compat.txt` (opens the inventory and any screens the compat mods draw). Run each pack once: `./gradlew runClient -PdevScript=@<abs>/scripts/glsl-corpus/bsl.txt -PdevProps=demonica.glsl.corpus=<abs>/run/transform-corpus/bsl,demonica.glsmPerfDebug=true > run/corpus-bsl.out 2>&1`. Compat: add `-PwithCompatMods` and `angelica.dumpShaders=true`; the corpus dir is `run/transform-corpus/compat`.
6. Baselines: copy `run/client/screenshots/corpus-*.png` to `run/baseline-screenshots/`; sum `transformMs` per pack from the `.out` files (`grep -o 'transformMs=[0-9.]*'`); count cases per pack and per patch kind.
7. Surveys on the recorded inputs: directives (`grep -lE '^\s*#\s*(if|ifdef|ifndef|define|undef|elif|else|endif|include|line|pragma)' run/transform-corpus/*/*/in.*`, report counts by directive; expected none); parse-ability with glsl-transformer (a throwaway test that runs the spike's parser configuration over every `in.*` and lists failures with the exception message; this is the GLSL-120 survey the risk register asks for). Both go in the report.
8. Report and STATUS.

**Done when.** Recorder and replayer committed with the Gradle wiring; `taumc`-against-`taumc` 100% identical on all corpora; the mini-corpus with outputs committed; the four scripts committed; the pack corpora and baselines exist under `run/`; the two surveys are in the report with counts.

**Verify.**
```
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=taumc 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=taumc 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
find run/transform-corpus -name 'case.properties' | wc -l; grep -h '^patch=' run/transform-corpus/*/*/case.properties | sort | uniq -c
grep -o 'transformMs=[0-9.]*' run/corpus-bsl.out | cut -d= -f2 | paste -sd+ | python3 -c 'import sys; print(eval(sys.stdin.read()))'
```

**Budget.** ~120K. The dev runs are the cost; read them only through grep. Do not open the recorded `.glsl` files except one or two to check the format.

**Handoff.** Report: corpus counts (per pack, per kind, per stage), the determinism result, the directive survey, the parse survey (every failing input with the message and a guess at the construct), `transformMs` totals per pack, screenshot paths, which compat mods submitted shaders.

**Risks.** No DH cases (Distant Horizons needs LOD data; documented, the mini-corpus covers DH); compat corpus may be small (report which mods drew); the harness gotchas in section 4; nondeterministic old-engine output (report, then let the accepted list handle it).

### Step 3. `ShaderAst`, the core verbs

**Goal.** `transform/transformer/ShaderAst.java` with the parse and print lifecycle and the twelve verbs that touch identifiers and expressions, each proven to match TauMC's behaviour on shared inputs by a parity test that runs both libraries.

**Preconditions.** Step 1 (library on the classpath, sources unpacked). Independent of Step 2; if Step 2 has not landed, write `GlslTokens` here and note it.

**Read.**
- TauMC sources, fetched raw by URL into `run/lib-src/taumc/` (try `https://raw.githubusercontent.com/TauMC/glsl-transformation-lib/7dd88a4/src/main/java/org/taumc/glsl/<File>.java`, then `main`): `Transformer.java` (30 KB), `ShaderParser.java`, `Util.java` (10.6 KB), `VariableInjector`, `FunctionInjector`, `InjectorPoint`, `Renamer`, `ExpressionRenamer`, `ReplaceExpression`, `PrependFunction`, `AppendFunction`, `RemoveVariable`, `TypeFinder`, `HasVariable`, `IdentifierCollector`, `FunctionCollector`, `ArrayExpressionRewriteListener`, `FastTreeWalker` (each 0.5 to 3.4 KB). About 55 KB.
- glsl-transformer sources under `run/lib-src/glsl-transformer/io/github/douira/glsl_transformer/`, signatures first (`grep -n "public"`), bodies as needed: `ast/transform/ASTTransformer.java`, `EnumASTTransformer.java`, `ASTParser.java`, `ASTInjectionPoint.java`, `Template.java`; `ast/query/Root.java`, `RootSupplier.java`, `index/IdentifierIndex.java`, `index/NodeIndex.java`, `index/ExternalDeclarationIndex.java`; `ast/node/TranslationUnit.java`, `abstract_node/ASTNode.java`; `ast/query/match/Matcher.java`, `AutoHintedMatcher.java`; `ast/print/PrintType.java`; `parser/ParseShape.java`; `util/Type.java`; `token_filter/ChannelFilter.java`; the node classes for declarations and expressions by name as they come up (`DeclarationExternalDeclaration`, `TypeAndInitDeclaration`, `DeclarationMember`, `FunctionDefinition`, `FunctionPrototype`, `ReferenceExpression`, `FunctionCallExpression`, `ArrayAccessExpression`, `BuiltinNumericTypeSpecifier`, `BuiltinFixedTypeSpecifier`).
- Iris 26.1 `transformer/CommonTransformer.java` (22 KB) for the idioms (`root.rename`, `replaceReferenceExpressions`, `replaceExpressionMatches` with `AutoHintedMatcher`, `parseAndInjectNode`, `Template`, `addIfNotExists`) and `TransformPatcher.java` lines around `new EnumASTTransformer`, `setRootSupplier`, `parseTranslationUnit`, `setTokenFilter`, `setPrintType`.
- Demonica's call sites, by grep, to see argument shapes: `grep -n 'transformer\.\|root\.\|prevTransformer\.\|currentTransformer\.' shader/src/main/java/net/coderbot/iris/pipeline/transform/*.java glsm/src/main/java/com/gtnewhorizons/angelica/glsm/CompatShaderTransformer.java`. About 430 lines.
- The spike test from Step 1.

**Do.**
1. Lifecycle. `static ShaderAst parse(String source, int version)` builds the parser as the spike does (one shared, lock-guarded `EnumASTTransformer`/`ASTParser`, or one per call; Step 5 decides, so keep the construction behind one factory method) and returns the wrapper; `String print(String header)` prints the tree with `PrintType.INDENTED` after removing the `VersionStatement` and any extension statements (the orchestrator emits the header, as today), and `String printBody()` for tests. Expose `t`, `tree`, `root`.
2. Verbs, in this order: `injectVariable`, `injectFunction`, `rename(String,String)`, `rename(Map)`, `replaceExpression(String,String)`, `prependMain`, `appendMain`, `removeVariable`, `findType`, `containsCall`, `hasVariable`, `renameFunctionCall(String,String)`, `renameFunctionCall(Map)`, `renameArray(String,String,Set<Integer>)`. For each, first write down TauMC's behaviour from its source (where it inserts, what it matches, what it skips: declarations versus references, call position versus not, struct fields, nested calls, identifiers inside strings of macros), then implement it with the building blocks in Appendix B, then the parity cases. Ordering: TauMC's `injectVariable` prepends (the dumped output shows the last injected declaration first); glsl-transformer's `BEFORE_DECLARATIONS` also inserts at the front. Match the old order where it costs nothing; where it would cost real code, keep glsl-transformer's order and let the replay accept "declaration order" diffs with that reason.
3. `ShaderAstParityTest` (`src/test/java/net/coderbot/iris/pipeline/transform/`): for each verb, five to ten inputs (GLSL 120 style and 330 style; identifiers that are both a function name and a variable; multi-declarator declarations `out float a, b;`; arrays; nested calls; the identifier inside a `#define`-free but comment-bearing source), run TauMC (`new Transformer(ShaderParser.parseShader(src).full())`, the verb, `GlslTransformUtils.getFormattedShader(tree, "")` via `mutateTree`) and `ShaderAst` (the verb, `printBody()`), and compare with `GlslTokens`. Name deliberate deviations in the test with a comment and in the report.
4. Report: a table (verb, TauMC behaviour in one line, adapter implementation in one line, parity status, deviations).

**Done when.** Parity green for the twelve verbs; every verb has a javadoc line stating its semantics; report.

**Verify.**
```
./gradlew :test --tests '*ShaderAstParityTest' --tests '*GlslTransformerSpikeTest' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
```

**Budget.** ~200K, the heaviest step. Read library classes by method. Do not read Iris's `CompatibilityTransformer` or Demonica's transformer classes here; they belong to Steps 4 and 5.

**Handoff.** Report as above plus: the factory method's shape, the `Type` values `findType` returns for the vector, integer and sampler kinds, and any glsl-transformer behaviour that surprised you (root sessions, index types, printing of injected nodes).

**Risks.** glsl-transformer node constructors require a root session (`Root` throws otherwise): create nodes through `t.parseXxx(root, ...)` or `Template`. The identifier index must be the prefix variant if any later step needs `prefixQueryFlat`. TauMC's `rename` may or may not touch declarations, struct fields or function names; read `Renamer` before deciding.

### Step 4. `ShaderAst`, the structural verbs

**Goal.** The remaining seven verbs and the declaration and function queries that `CompatibilityTransformer.transformGrouped` and `AdaptiveShadowBoundsTransformer` need.

**Preconditions.** Step 3.

**Read.**
- TauMC: `FunctionCallWrapper` (renameAndWrapShadow), `FunctionRemover`, `TransformerRemover`, `TransformerCollector`, `FunctionCollector` (removeUnusedFunctions), `ConstAssignmentRemover`, `ConstParameterFinder` (removeConstAssignment), `QualifierFinder` (findQualifiers), `AssigmentChecker` (hasAssigment), `StorageCollector`, and `Transformer.initialize` / `Transformer.removeUnusedFunctions` / `removeConstAssignment` in `Transformer.java`. About 20 KB.
- Iris 26.1 `transformer/CompatibilityTransformer.java` (32 KB): `transformEach` (const-parameter removal, unused-function removal, empty-declaration removal) and `transformGrouped` (`DeclarationMatcher`, out/in matching, `Template` for the added out declaration, `getInitializer`).
- Demonica: `CompatibilityTransformer.java` lines 130-253 (12.1 KB file) and `AdaptiveShadowBoundsTransformer.java` (10 KB) to see what queries they need (function prototype name, parameter types and names, body text, `hasVariable`, `injectVariable`, replacement of a whole function definition by source).

**Do.**
1. Verbs: `renameAndWrapShadow(String from, String to)` (rename the call and wrap it as TauMC's `FunctionCallWrapper` does; check whether it wraps in `vec4(...)` or something else, and whether it touches non-call uses); `removeUnusedFunctions()` (TauMC's algorithm may be single-pass; Iris's is per definition; parity decides); `removeConstAssignment()` (Iris's `transformEach` const logic as the model); `Map<String, Declaration> findQualifiers(StorageType)` returning a record `(name, typeText, arraySpecifierText, node)` in source order; `hasAssignment(String)`; `initialize(Declaration, String name)` (what TauMC injects, where; Iris's `getInitializer(root, name, type)` is the model); `replaceFunctionDefinition(String name, String newSource)`.
2. Queries: `List<FunctionInfo> functions()` with name, parameters (type text, name), the `FunctionDefinition` node and `String source(FunctionDefinition)`; `String text(ASTNode)` through the printer; `boolean isDeclaredGlobal(String)` if `hasVariable`'s TauMC semantics turn out to be "declared" rather than "occurs".
3. Parity cases: multi-declarator `out float mat, recolor;`, `flat out float isMoon;` (the existing `CompatibilityTransformerTest` expects the type text `flatoutfloat`, i.e. qualifiers included), arrays `out vec3 v[2];`, `in` declarations with `gl_` names (skipped by `transformGrouped`), functions with `const` parameters initializing `const` locals, an unused helper and a helper used only by another unused helper, `shadow2D`/`shadow2DProj` calls inside expressions, a PCF helper `float texture2DShadow2x2(sampler2DShadow s, vec3 p)`.
4. Report table as in Step 3.

**Done when.** Parity green for all nineteen verbs and the queries; report.

**Verify.** Same command as Step 3.

**Budget.** ~160K.

**Handoff.** The verb table completed; a note on what `findQualifiers` returns as `typeText` (with or without qualifiers) because `transformGrouped` and a test depend on it; the `FunctionInfo` shape.

**Risks.** `removeUnusedFunctions` fixpoint versus single pass changes output on packs with chains of unused helpers; parity on the corpus (Step 5) will show which TauMC does. `initialize` must produce the same zero values as TauMC (`vec4(0.0)`, `0`, `false`).

### Step 5. Orchestrator and the COMPOSITE and COMPUTE path

**Goal.** `AstShaderTransformer` runs the full sequence of 3.1 on glsl-transformer, and COMPOSITE and COMPUTE programs replay clean against the corpus.

**Preconditions.** Steps 2 and 4.

**Read.**
- `ShaderTransformer.java` (22.8 KB), whole file.
- `CommonTransformer.java` (4.6 KB), `CompatibilityTransformer.java` (12.1 KB), `CompositeDepthTransformer.java` (0.9 KB), `ComputeTransformer.java` (0.4 KB), `TextureTransformer.java` (1.8 KB), `EntityPatcher.java` (1.0 KB), `CoreTransformHelper.java` (3.1 KB), `parameter/*.java` (6.6 KB).
- `ShaderAst` public API (`grep -n "public" transformer/ShaderAst.java`) and the Step 3 and 4 reports.
- `GlslTransformUtils.java` (7.4 KB): the regex helpers and `TEXTURE_RENAMES`.
- Iris 26.1 `TransformPatcher.java`: only the lines around `new EnumASTTransformer`, `setRootSupplier`, `ParsingCacheStrategy`, `parseTranslationUnit`, `setTokenFilter`, `setPrintType`, and its `transformer.transform(inputs)` call.
- `Iris.java` line 656 (`ShaderTransformer.init()`).

**Do.**
1. Extract the engine-neutral parts of `ShaderTransformer` into `transform/VersionNegotiation.java`: `VERSION_REQUIREMENTS`, `init` (keep `ShaderTransformer.init()` delegating so `Iris.java:656` is untouched), `getRequiredVersion`, `getStageMinimumVersion`, `negotiateVersion`, `NegotiationResult`, the version regex. Both engines use it. The old engine's tests still pass.
2. `AstShaderTransformer.transform(...)` and `transformCompute(...)`: the same per-stage sequence as the old engine up to the parse (version, hoisting with the Celeritas-header and adaptive-shadow-bounds scan quirks, stage minimum, negotiation, the regex pre-passes in the same order), then collect the `#extension` lines from the input by regex (the old engine took them from the pre-parser tree), `ShaderAst.parse`, `doTransform` (COMPOSITE and COMPUTE now; the other kinds throw), then across stages `CompatibilityTransformer.transformGrouped`, then `print(header)` with the Celeritas-header rule left as a hook for Step 6, then `restoreReservedWords`. Log the same `[Load #n] Transformed shader for <kind> in <time>` line.
3. Thread safety: implement the factory as one instance behind a `ReentrantLock` and, alternatively, one instance per call; run the corpus replay with `-Ddemonica.glsmPerfDebug`-equivalent timing in the test and `TransformPatcherCacheTest` under `-PglslEngine=douira` (add that property forwarding too); keep the faster one that passes, note the numbers.
4. Port into `transformer/`: `CommonTransformer` (its `AdaptiveShadowBoundsTransformer.transform(root, type)` call becomes a `TODO(S7)` no-op with a comment; the report lists the corpus cases this affects, and those cases go into `accepted.txt` with the reason `S7 pending`), `CompatibilityTransformer` (`transformEach` and `transformGrouped` on `ShaderAst`; the regex patch methods are shared: keep them in the old class and call them, or move them to a small `CompatibilityPatches` helper that both engines use), `CompositeDepthTransformer`, `ComputeTransformer`, `TextureTransformer` (the sampler-kind switch on `findType`'s result), `EntityPatcher`, `CoreTransformHelper`, and `applyIntelHd4000Workaround`.
5. Replay `-PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE` on the pack corpora and the mini-corpus. Classify every diff: semantic (fix), formatting (should not occur after `GlslTokens`; if it does, fix the tokenizer), ordering (accept with reason), S7-pending (accept with reason). Repeat until no unexplained diff remains.
6. Report and STATUS.

**Done when.** Replay for COMPOSITE and COMPUTE has zero unexplained diffs on all corpora; the old-engine transform tests still green; `TransformPatcherCacheTest` green on both engines; report.

**Verify.**
```
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformPatcherCacheTest' -PglslEngine=douira 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
ls build/reports/transform-replay | head; wc -l src/test/resources/transform-replay/accepted.txt
```

**Budget.** ~150K.

**Handoff.** The lock-versus-per-call decision with numbers; the `accepted.txt` entries added and why; how extensions are collected; which old-engine methods the new engine still calls (regex helpers) and where they live.

**Risks.** The old engine drops unknown directives silently; the new filter must do the same and log. `#extension` ordering in the header. The `[Load #n]` log line and `Iris.getShaderPackLoadId()` coupling. Composite fragment programs are the bulk of any pack, so this replay is the biggest signal in the whole migration.

### Step 6. ATTRIBUTES and CELERITAS_TERRAIN

**Goal.** The two patch kinds every gbuffers, shadow and terrain program uses, and the first end-to-end run of a pack on the new engine.

**Preconditions.** Step 5. Desktop session.

**Read.**
- `AttributeTransformer.java` (3.2 KB), `CeleritasTransformer.java` (4.2 KB), `ShaderTransformer.java` lines 377-493 (`applyIntelHd4000Workaround`, `patchMultiTexCoord3`, `replaceMidTexCoord`, `replaceMCEntity`, `addIfNotExists*`, `computeCeleritasHeader`), `parameter/AttributeParameters.java`, `parameter/CeleritasTerrainParameters.java`.
- `net/coderbot/iris/celeritas/vertices/TerrainVertexFormatRequirements.java` (5.0 KB) and its test (3.4 KB); `CeleritasTerrainPipeline.java` around line 170 (the caller).
- Iris 26.1 `transformer/SodiumTransformer.java` (14.8 KB), optional, for its `replaceMidTexCoord` and `replaceMCEntity` shapes.
- The Step 5 report; `accepted.txt`.

**Do.**
1. Port `AttributeTransformer` and `CeleritasTransformer` into `transformer/`, plus the four helpers from `ShaderTransformer` into the new orchestrator (or into `transformer/VertexAttributeHelpers`). `findType`'s `Type` result replaces the `GLSLLexer.VEC4`-style switch cases; keep the same injected source strings. `AstShaderTransformer.doTransform` gains both kinds; the Celeritas header append after printing is text, unchanged.
2. `TerrainVertexFormatRequirements.analyze`: replace the ANTLR lexer scan with a library-free identifier scan (strip comments and string literals, skip preprocessor lines, match `\b[A-Za-z_]\w*\b`), preserving what happens on lexer errors today (read `LexerErrorCounter`'s use first). Its test is the specification and must stay green unchanged.
3. Replay `-PglslReplayPatches=ATTRIBUTES,CELERITAS_TERRAIN` on the pack corpora and the mini-corpus; classify and fix as in Step 5.
4. First end-to-end runs: the Step 2 scripts with `-PdevProps=demonica.glsl.engine=douira`, one pack each. Check the log for `Shader compilation failed`, `Failed to compile`, `UnsupportedOperation` and `Exception` (none expected beyond the harmless ones listed in the maintainer's notes), and compare the screenshots with the Step 2 baselines (ffmpeg to raw RGB, numpy mean absolute difference; report the number per pack).
5. Report and STATUS.

**Done when.** Replay clean for the four kinds ported so far; `TerrainVertexFormatRequirementsTest` green; the three packs load and render on the new engine with screenshot differences at the level of moving foliage and water; report.

**Verify.**
```
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE,ATTRIBUTES,CELERITAS_TERRAIN 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TerrainVertexFormatRequirementsTest' --tests 'net.coderbot.iris.pipeline.transform.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew runClient -PdevScript=@<abs>/scripts/glsl-corpus/bsl.txt -PdevProps=demonica.glsl.engine=douira > run/s6-bsl.out 2>&1; grep -cE 'Shader compilation failed|Failed to compile|UnsupportedOperation' run/s6-bsl.out; grep -E 'Dev shader pack|Dev stats' run/s6-bsl.out
```

**Budget.** ~130K.

**Handoff.** Screenshot difference numbers per pack; any program that compiled on TauMC and not on the new engine (with the driver log line); the `Type` mapping used for `mc_Entity` and `mc_midTexCoord`.

**Risks.** `mc_Entity` unpacking source strings contain `>>` and `&`; make sure the printer emits them unchanged. The Celeritas header is appended after the version line; its `#ifdef` block must stay outside the parsed tree. Terrain shaders are where `gl_MultiTexCoord3`/`mc_midTexCoord` interplay lives; a mistake shows as broken block textures, visible in the screenshot diff.

### Step 7. DH transformers and `AdaptiveShadowBoundsTransformer`

**Goal.** The last two patch kinds and the Actinium-specific shadow-bounds instrumentation, with their tests on the new engine.

**Preconditions.** Step 6.

**Read.**
- `DHGenericTransformer.java` (6.3 KB), `DHTerrainTransformer.java` (5.7 KB), `parameter/DHParameters.java`.
- `AdaptiveShadowBoundsTransformer.java` (10 KB) and `AdaptiveShadowBoundsTransformerTest.java` (13.1 KB); `AdaptiveShadowBoundsStats` by grep for the members the transformer uses.
- Iris 26.1 `transformer/DHGenericTransformer.java` and `DHTerrainTransformer.java` (14.4 KB together), optional.
- The Step 4 report (the `FunctionInfo` and `replaceFunctionDefinition` shapes).

**Do.**
1. Port both DH transformers (identical call sets: `replaceExpression` x9, `rename` x6, `injectFunction` x4, and the vertex `injectVertInit`) and add the kinds to `doTransform`.
2. Port `AdaptiveShadowBoundsTransformer` onto `ShaderAst.functions()`, `hasVariable`, `injectVariable` and `replaceFunctionDefinition`; the candidate detection (PCF helper signatures, `shadowMapResolution` presence, existing guard) and the patched-source generation are string logic and stay. Remove the `TODO(S7)` no-op in the new `CommonTransformer` and the `S7 pending` entries from `accepted.txt`.
3. Tests: rewrite `AdaptiveShadowBoundsTransformerTest`'s assertions so that they do not depend on either library: re-parse the output with `ShaderAst.parse` (must succeed), then assert with `GlslTokens` and `ShaderAst.functions()` (one guard `if` in the helper, three `atomicAdd` calls, `binding = 7`, the negative cases). Run it against the new engine's transformer. The old test stays until Step 11 and keeps testing the old engine.
4. Replay all six kinds on the mini-corpus and the pack corpora (DH cases exist only in the mini-corpus unless Step 2 found some).
5. Report and STATUS.

**Done when.** Replay clean for all kinds; both AdaptiveShadowBounds tests green; report.

**Verify.**
```
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayEngine=douira 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus -PglslReplayEngine=douira 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*AdaptiveShadowBounds*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
```

**Budget.** ~130K.

**Handoff.** Whether any DH case came from a real pack; the shape of the guard the transformer injects, as printed by the new engine (for Step 8's review).

**Risks.** `replaceFunctionDefinition` must keep the function's position (Step 4's contract); the instrumentation path depends on `AdaptiveShadowBoundsStats` flags that the replayer may not be able to set (documented in Step 2). A DH world is not available in the harness; the dev check is the mini-corpus only.

### Step 8. Flip the default engine, port the Iris-side tests, full run

**Goal.** The new engine is the default, every Iris-side test is library-neutral or uses `ShaderAst`, and the packs render as before. This is exit point A.

**Preconditions.** Step 7. Desktop session.

**Read.**
- `TransformPatcherTest.java` (4.3 KB), `TransformPatcherCacheTest.java` (7.6 KB), `CompatibilityTransformerTest.java` (7.8 KB), `CompatibilityTransformerCaveSkyholeTest.java` (1.0 KB), `CeleritasTransformerTest.java` (6.4 KB), `AdaptiveShadowBoundsTransformerTest.java` (13.1 KB, already handled in Step 7), `TerrainVertexFormatRequirementsTest.java` (3.4 KB).
- `src/test/resources/transform-replay/accepted.txt`; the Step 2 report (baselines); `run/client/scripts/cp4.txt`, `guard-core.txt`.

**Do.**
1. Default `demonica.glsl.engine` becomes `douira`. The old engine stays selectable.
2. Port the tests: `TransformPatcherTest` (assertions through `GlslTokens.contains` instead of `contains`/`replaceAll`); `CompatibilityTransformerTest` (the "0 syntax errors" oracle becomes "`ShaderAst.parse` succeeds", the `findQualifiers`/`transformGrouped` assertions use the new `CompatibilityTransformer` and `ShaderAst.findQualifiers`); `CeleritasTransformerTest` (the listener becomes `ShaderAst` queries or token assertions; `ShaderPrinter.getFormattedShader` becomes `printBody()`); `CompatibilityTransformerCaveSkyholeTest` and `TransformPatcherCacheTest` are library-independent and stay. `TerrainVertexFormatRequirementsTest` is unchanged since Step 6. Delete nothing yet; Step 11 removes the old-engine copies.
3. Review every line of `accepted.txt`: each must still be true and carry a reason a reader can check; unexplained entries are bugs to fix now.
4. Full replay on all corpora and all kinds with the default engine; the summary goes in the report.
5. Dev runs: a `cp4`-shaped sweep of the three packs (screenshot each, diff against baseline), `guard-core.txt` once (the guard's shader path with the new engine), and the compat-mod run from Step 2 with the new engine (GLSM is still on TauMC, so this only checks co-existence). Extract `transformMs` totals per pack and tabulate against the Step 2 baseline.
6. One full `./gradlew :test`.
7. Report and STATUS; mark exit point A reached.

**Done when.** Default flipped; all transform tests green on the default engine; replay clean; the sweep renders all three packs with baseline-level screenshot differences; the guard drill passes; full `:test` green; timing table in the report.

**Verify.**
```
./gradlew :test 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew runClient -PdevScript=@scripts/cp4.txt -PdevProps=demonica.glsmPerfDebug=true > run/s8-cp4.out 2>&1; grep -E 'Dev shader pack|Dev stats|Shader compilation failed|Failed to compile' run/s8-cp4.out; grep -o 'transformMs=[0-9.]*' run/s8-cp4.out | cut -d= -f2 | paste -sd+ | python3 -c 'import sys; print(eval(sys.stdin.read()))'
./gradlew runClient -PdevScript=@scripts/guard-core.txt -PdevProps=demonica.guard.drill=CORE > run/s8-guard.out 2>&1; grep -E 'anchors|drill|Dev shader pack' run/s8-guard.out | head
```

**Budget.** ~130K.

**Handoff.** The timing table (per pack, old versus new, first load and cached reload); the screenshot numbers; the final `accepted.txt` with reasons; anything that should be reported to the maintainer before Step 9 (a pack feature that differs, a slower transform).

**Risks.** A slower first pack load: the cache hides reloads but not first loads; if the new engine is more than about twice as slow, report it before continuing (the lock-versus-per-call choice from Step 5 and the `ParsingCacheStrategy` are the knobs). String assertions in tests that encoded the token-spaced printer.

### Step 9. GLSM subtractions and utilities

**Goal.** The unreachable SPIR-V/GLES path and the unused post-transform hook are gone, `GlslTransformUtils` is regex-only, and the fixed-function generator test no longer uses TauMC as its oracle.

**Preconditions.** Step 8 (exit point A). No desktop needed.

**Read.**
- `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GlslTransformUtils.java` (7.4 KB).
- `glsm/.../glsm/shader/`: the first 60 lines of each of `SpirvShaderTranslator.java`, `GlslVulkanPreprocess.java`, `SpirvCompiler.java`, `UniformType.java`, `ShaderType.java`; then `grep -rn "glsm.shader\." glsm/src shader/src src --include=*.java` for every importer (`PerFrameUniformBlock` and `CompatShaderTransformer` import from this package).
- `glsm/.../hooks/ShaderTransformPostProcessor.java` (13 lines) and `GLSMHooks.java` line 25.
- `glsm/build.gradle` lines 25-31.
- `src/test/java/com/gtnewhorizons/angelica/glsm/ffp/VertexShaderGeneratorTest.java` (9.8 KB).
- `GLStateManager.java` lines 5545-5600 and 7280-7300 only.
- `CompatShaderTransformer.java` lines 100-130 (the javadoc that mentions the SPIR-V path).
- `THIRD_PARTY_NOTICES.md` `## Compile-only dependencies` section; `docs/SCOPE_RESEARCH.md` item F (for the wording, no edit).

**Do.**
1. Delete `SpirvShaderTranslator`, `GlslVulkanPreprocess`, `SpirvCompiler`. Keep `UniformType` and `ShaderType` if anything outside the deleted files imports them; delete them otherwise. Remove the `lwjgl-shaderc` and `lwjgl-spvc` compile-only lines from `glsm/build.gradle` and their mention in the notices. Fix the javadoc in `CompatShaderTransformer`.
2. Delete `ShaderTransformPostProcessor` and the `postTransformProcessor` field (nothing assigns or calls it).
3. `GlslTransformUtils`: delete `parseFullQuiet` and `parsePreQuiet` (their only callers were deleted in 1). `getFormattedShader(ParseTree, String)` is still used by the old engine and `AdaptiveShadowBoundsTransformer` (old); mark it `@Deprecated` with a comment pointing at Step 11 and leave it.
4. `VertexShaderGeneratorTest`: replace the TauMC oracles (`findQualifiers(GLSLLexer.IN/UNIFORM)`, the two listener classes, the reprint through `getFormattedShader`) with `ShaderAst.parse` (must succeed), `ShaderAst.findQualifiers(StorageType.IN/UNIFORM)` and `GlslTokens` assertions. The generator itself is untouched.
5. `./gradlew build` and the module-boundary and dependency-direction checks (`verifyModuleBoundaries` is part of `check`; `DependencyDirectionTest` is a test).
6. Report and STATUS.

**Done when.** The five files are gone, the build is green, `VertexShaderGeneratorTest` and the GLSM tests are green, `verifyModuleBoundaries` passes, no `glsm.shader.Spirv*`/`GlslVulkanPreprocess` reference remains (`grep -rn` returns nothing), report.

**Verify.**
```
grep -rn "SpirvShaderTranslator\|GlslVulkanPreprocess\|SpirvCompiler\|ShaderTransformPostProcessor\|postTransformProcessor\|parseFullQuiet\|parsePreQuiet" --include=*.java glsm/src shader/src src | wc -l
./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew build verifyModuleBoundaries 2>&1 | grep -E 'FAILED|error:|BUILD' | tail -20
```

**Budget.** ~90K.

**Handoff.** Which of `UniformType`/`ShaderType` survived and why; the notice lines removed.

**Risks.** `PerFrameUniformBlock` imports from `glsm/shader`; do not delete what it uses. `verifyDistributedJar` has no rule about these classes, but `DependencyDirectionTest` fails if a glsm class references the root project.

### Step 10. `CompatShaderTransformer` on `ShaderAst`

**Goal.** GLSM's compat transformer runs on glsl-transformer behind the same switch, its test no longer uses TauMC token types as oracles, and the compat corpus replays clean.

**Preconditions.** Steps 4 and 9. Desktop for one run.

**Read.**
- `CompatShaderTransformer.java` (54.9 KB): lines 1-282 and 982-1214 fully; lines 283-981 (the preprocessor evaluator) only through `grep -n "transformer\.\|Transformer\|parse" ` to confirm they touch no library.
- `CompatShaderTransformerTest.java` (20.9 KB).
- `ShaderAst` API; the Step 3 and 4 reports; `GlslTransformUtils.TEXTURE_RENAMES`.
- Compat corpus cases under `run/transform-corpus/compat/` (count and one example) and the two committed compat cases.

**Do.**
1. Behind `TransformPatcher.engine()`, replace the block from `ShaderParser.parseShader` to the serialization (lines about 175-270) with `ShaderAst.parse` (a parse failure throws the library's exception, which the existing `catch` turns into the version fix-up fallback, preserving the fail-fast behaviour), the same verb calls one to one (`injectVariable` x23, `rename` x14 including the `MATRIX_RENAMES` map, `replaceExpression` x7, `injectFunction` x6, `renameAndWrapShadow` x6, `renameArray` x2, `prependMain` x2, `containsCall` x2, `hasVariable` x2, `appendMain`, `renameFunctionCall(TEXTURE_RENAMES)`), and `print(header)` in place of the `mutateTree` serialization. Everything before the parse (precision guards, the evaluator, the pre-parse renames including `renameParseBreakingTextureFunctions`) and after it (`fixupQualifiers`, `restoreReservedWords`, the dump) stays.
2. Port the test's oracles: token-type counts (`GLSLLexer.PRECISION`, the directive tokens, `MACRO_ESC_NEWLINE`, `IDENTIFIER`) become `GlslTokens` counts and line-based checks on the output text (count `precision` tokens, count `#ifdef`/`#else`/`#endif` lines, count backslash-newline continuations); the "0 syntax errors" oracles become `ShaderAst.parse` succeeding; `hasIdentifier` becomes `GlslTokens.contains`; the exact-string assertions (`"#version 330 core\n" + source`) are for the untouched fallback paths and stay. Run the test on both engines (`-PglslEngine`).
3. Replay the compat corpus (`-PglslReplayPatches=COMPAT` or a `domain` filter) with the new engine; classify and fix.
4. Dev run: the compat script with `-PwithCompatMods -PdevProps=angelica.dumpShaders=true,demonica.glsl.engine=douira`; check the log for `CompatShaderTransformer` warnings (`AST transformation failed, falling back`), FFP compile failures (`FFP ... shader compilation failed`), and that `run/client/compat_shaders/*_transformed*` compiled (no `Shader compilation log` lines with errors). Screenshot the inventory and one mod screen; compare with the Step 2 compat screenshots.
5. Report and STATUS.

**Done when.** `CompatShaderTransformerTest` green on both engines; compat replay clean; the compat dev run shows no fallback warnings that the TauMC run did not also show; report.

**Verify.**
```
./gradlew :test --tests '*CompatShaderTransformerTest' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*CompatShaderTransformerTest' -PglslEngine=taumc 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus/compat -PglslReplayEngine=douira 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew runClient -PwithCompatMods -PdevScript=@<abs>/scripts/glsl-corpus/compat.txt -PdevProps=angelica.dumpShaders=true,demonica.glsl.engine=douira > run/s10-compat.out 2>&1; grep -cE 'falling back|FFP .* compilation failed|Shader compilation log' run/s10-compat.out
```

**Budget.** ~130K. The preprocessor region is 700 lines you do not need to read.

**Handoff.** Which compat mods submitted shaders and whether each transformed identically; any verb whose compat usage exposed a semantic gap in `ShaderAst` (fix it in `ShaderAst`, note it).

**Risks.** `renameAndWrapShadow` is used six times here and twice in the Iris tree; Step 4's parity fixtures must cover the compat shapes (`shadow2D(s, p).r`, `shadow2DProj`). Mod shaders arrive with raw directives; the evaluator handles them before the parse, and the new parser's channel filter must not throw on any survivor.

### Step 11. Remove TauMC, final layout, release checks

**Goal.** TauMC's library and every class written against it are gone, the layout matches Iris, the notices are final, and the jar passes every check plus a production-shaped smoke test. Exit point B.

**Preconditions.** Step 10. Desktop and the Prism instance `prod-smoke-test`.

**Read.**
- `build.gradle` ranges 325-345 and 705-800; `glsm/build.gradle`; `THIRD_PARTY_NOTICES.md`; `README.MD` credits; `mcmod.info`; `scripts/test_port_scan.py` line 55.
- `grep -rln "org.taumc.glsl\|ShaderTransformer\.\|GlslTransformUtils.getFormattedShader\|demonica.glsl.engine\|TransformPatcher.engine" --include=*.java --include=*.gradle --include=*.py --include=*.md . | grep -v '^./.reference' | grep -v '^./run'`.
- `STATUS.md` and the reports of Steps 5 to 10 (for the list of old classes and tests).

**Do.**
1. Freeze the parity fixtures: run `ShaderAstParityTest` once more in a mode that writes TauMC's outputs into `src/test/resources/shader-ast-parity/` and convert the test into `ShaderAstSnapshotTest` (adapter output compared with the committed snapshots). Then delete `ShaderAstParityTest`.
2. Delete the old engine: `ShaderTransformer` (old), the old transformer classes in `transform/` (`AdaptiveShadowBoundsTransformer`, `AttributeTransformer`, `CeleritasTransformer`, `CommonTransformer`, `CompatibilityTransformer` after moving its regex patch methods if Step 5 left them there, `CompositeDepthTransformer`, `ComputeTransformer`, `CoreTransformHelper`, `DHGenericTransformer`, `DHTerrainTransformer`, `EntityPatcher`, `TextureTransformer`), the old-engine tests that Steps 7 and 8 duplicated, the switch in `TransformPatcher` and `CompatShaderTransformer`, `GlslTransformUtils.getFormattedShader`, the `taumc` branch of the replayer. Rename `AstShaderTransformer` to `ShaderTransformer`. Keep `VersionNegotiation`.
3. Build: remove `org.taumc:glsl-transformation-lib` from `glsm/build.gradle` and from `contain`. Decide the ANTLR runtime: pin `antlr4-runtime:4.13.1` to match the library's generated parser (recommended), or keep 4.13.2; either way one coordinate in both files, and record the reason. Keep `verifyDistributedJar`'s forbidden `org/taumc/` rule (it also guards against Celeritas classes). Remove the `org.taumc.glsl.` entry from `scripts/test_port_scan.py`.
4. Notices: remove the glsl-transformation-lib row from `THIRD_PARTY_NOTICES.md` and its credits in `README.MD` and `mcmod.info` (a one-line "formerly used" acknowledgement in the README credits is reasonable; the maintainer decides); confirm the ANTLR row's version.
5. Stale extracted copies: Cleanroom's mod discoverer extracts a coremod's contained jars and puts them on the launch classloader (`CleanroomModDiscoverer.addContainedDepsToClasspath`), so old copies survive on disk. Delete `run/client/mods/1.12.2/glsl-transformation-lib-*.jar` and the same file in the Prism instance `prod-smoke-test` (and in any other instance the smoke test uses). Then a dev run must show `glsl-transformer-3.0.0-pre3.jar` and no `glsl-transformation-lib` in the classpath lines of `run/client/logs/latest.log` (grep both names).
6. Checks: `./gradlew check` (tests, `verifyDistributedJar`, `verifyModuleBoundaries`, `verifyRunClasspath`, `verifyCeleritasPin`, `verifyS8tnlibPin`, `verifyProductionAnchors`); the `cp4` sweep and one guard drill on the dev client; the full replay on the pack corpora (the recorded TauMC outputs are still the reference); the production smoke test through Prism per the maintainer's procedure (launch the instance with `flatpak --launch`, never a hand-built java command; copy the jar into the instance; props in `relauncher.json`).
7. Final timing table (first load and cached reload per pack) against the Step 2 baseline.
8. `docs`: update `STATUS.md` (done, exit point B), `DECISION.md` (done, date), and add a paragraph to this page's header ("Completed on <date>, commit <sha>"). `docs/SCOPE_RESEARCH.md` item F is now partly done; mention it in the report, do not edit that page.
9. Report and STATUS.

**Done when.** `grep -rn "org.taumc.glsl"` over the sources, build files and scripts returns nothing; `check` green; the jar lists exactly `glsl-transformer-3.0.0-pre3.jar`, `antlr4-runtime-<v>.jar` and `jcpp-1.4.14.jar` as contained deps; the sweep, the drill and the Prism smoke test pass with baseline-level screenshots; the classpath grep shows no old library; report.

**Verify.**
```
grep -rn "org.taumc.glsl" --include=*.java --include=*.gradle --include=*.py . | grep -v '^./.reference' | grep -v '^./run' | wc -l
./gradlew check 2>&1 | grep -E 'Tests run|FAILED|error:|BUILD|verify' | tail -30
unzip -p build/libs/Demonica-*.jar META-INF/MANIFEST.MF | grep ContainedDeps
ls run/client/mods/1.12.2/ | grep -i glsl
./gradlew runClient -PdevScript=@scripts/cp4.txt > run/s11-cp4.out 2>&1; grep -c 'glsl-transformation-lib' run/client/logs/latest.log; grep -c 'glsl-transformer-3.0.0-pre3' run/client/logs/latest.log
```

**Budget.** ~130K.

**Handoff.** The final inventory (files deleted, files renamed), the ANTLR decision, the smoke-test evidence (screenshot paths, log excerpts), the timing table, and a list of follow-ups for the maintainer (release version bump, GitHub license metadata, the `docs/SCOPE_RESEARCH.md` note).

**Risks.** A reference to an old class hidden in a mixin config, a string constant or reflection (grep for the simple names too, including in `src/main/resources`); the stale jar masking such a reference in a production-shaped run (that is why step 5 comes before step 6); `verifyRunClasspath` and `verifyDistributedJar` expectations changed by the contain list.

### Step 12. Optional payoff

**Goal.** Prove the reason for the migration: take one upstream Iris transformer change without translation, and write down how to do it next time. Also answer the reserved-word question from 3.6.

**Preconditions.** Step 11.

**Read.**
- Iris 26.1 `transformer/CompatibilityTransformer.java` (32 KB) and Demonica's `transformer/CompatibilityTransformer.java`; Iris 26.1 `Patch.java`, `PatchShaderType.java`, `parameter/Parameters.java`.
- `docs/` house style (this page, `celeritas/patches/README.md`).

**Do.**
1. `docs/glsl-transformer_adoption/PORTING_GUIDE.md`: the file map (Iris 26.1 `transformer/X.java` to Demonica `transformer/X.java`; Iris `VANILLA`/`SODIUM` to Demonica `ATTRIBUTES`/`CELERITAS_TERRAIN`; Iris `VanillaParameters`/`SodiumParameters` to `AttributeParameters`/`CeleritasTerrainParameters`; what Demonica has that Iris does not: `AdaptiveShadowBoundsTransformer`, `CoreTransformHelper`, the regex passes, the Celeritas header); the two ways to write a transformation (`ShaderAst` verbs, direct `Root`/`TranslationUnit` idioms) and when to use which; the replay and mini-corpus workflow for verifying a port; a note that transformer classes are Minecraft-agnostic (Iris 26.1's transform package imports no `net.minecraft.*`), so the Cleanroom backporting concerns (registries, NBT, renderers) apply only if a port touches pipeline code outside `transform/`, where the `cleanroom` tool `find_equivalent(from: "modern-minecraft")` is the front door.
2. One demonstrated port: Iris 26.1's `transformEach` empty-declaration removal (Demonica's old code carried it commented out as impossible with TauMC's library) and its `transformGrouped` handling of array specifiers and geometry stages, brought into Demonica's `CompatibilityTransformer` with a mini-corpus case each and a replay run.
3. The reserved-word question: replay the pack corpora with `replaceTexture`/`renameReservedWords`/`restoreReservedWords` disabled in the new engine (a temporary flag) and report parse failures and diffs. If none, propose their removal in the report; do not remove them in this step.
4. Report and STATUS.

**Done when.** The guide exists; the demonstrated port replays clean and has tests; the reserved-word report is written.

**Verify.**
```
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/src/test/resources/transform-corpus 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20
./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
```

**Budget.** ~100K.

**Handoff.** The guide; the replay summary for the demonstrated port; the reserved-word report with the list of inputs that fail to parse without the passes (empty or not).

**Risks.** None to the shipped jar; the demonstrated port is a behaviour change and needs a screenshot sweep before release.

## 7. Risk register

| Risk | Where it shows | Mitigation |
|---|---|---|
| GLSL 120 constructs that glsl-transformer's own glslang corpus fails on | Step 2's parse survey over recorded inputs; Step 6's first pack run | Survey before porting; a regex pre-pass or a rewrite of the construct in `ShaderAst.parse` if a real pack hits one |
| Directives surviving preprocessing (`#define`, `#if`, `#line`) | Step 2's directive scan | The old engine dropped them; the new channel filter drops and logs them; if a real pack depends on one, run jcpp again before the parse |
| `#extension` lines lost or reordered in the header | Replay diffs on the first line | Collect them by regex from the input, emit them in source order as today |
| Printer format breaking string assertions | Every test that used `contains` on token-spaced output | `GlslTokens` everywhere; no raw string comparison of transformed output |
| Declaration-order differences between engines | Replay diffs on injected declarations | Match the old order where cheap; otherwise accept with the reason "declaration order" |
| Nondeterministic old-engine output | Step 2's `taumc` self-replay | Report; accept per case; do not chase it in the old engine |
| Slower first pack load | Step 5 and Step 8 timing tables | Per-call versus locked transformer, `ParsingCacheStrategy`; the 400-entry cache hides reloads; report if more than about twice as slow |
| Thread safety of the new engine | `TransformPatcherCacheTest` on the new engine | Lock or per-call instance, decided in Step 5 |
| ANTLR runtime 4.13.2 with a parser generated by 4.13.1 | Warnings on stderr at first parse | Same major.minor, no warning expected; Step 1 checks the spike's output; Step 11 may pin 4.13.1 |
| The `antlr4` tool arriving transitively (about 16 MB) | Jar size, `verifyDistributedJar` | `transitive = false` on both declarations |
| AGPL obligations | Distribution | Source is public; the AGPL §13 network clause does not apply to a client mod; notices and license files shipped from Step 1 |
| Mod shaders with raw directives (GLSM path) | Step 10 compat replay | The hand-written evaluator runs before the parse and stays |
| `renameAndWrapShadow`, `renameArray`, `findQualifiers` semantics guessed wrong | Parity tests in Steps 3 and 4, compat replay in Step 10 | Read TauMC's implementing class before implementing; fixtures for the compat shapes |
| Stale extracted copy of the old library under `mods/1.12.2/` masking an incomplete removal | Step 11's production-shaped runs | Delete the extracted jars in `run/client/mods/1.12.2/` and the Prism instances before the final runs; grep the classpath log |
| A reference to an old class outside Java (mixin config, resource, script) | Step 11's grep | Grep simple names in `src/main/resources`, `scripts/`, `docs/` too |
| An agent reading too much | Every step | Section 4's budget rules; stop at 300K with a partial report |

## Appendix A. File inventory on `dev` at `11b6c618` (unchanged at `8e19cb75`)

Main code that imports `org.taumc.glsl` or `org.antlr`. "API lines" counts lines that call the library (imports, `Transformer` verbs, parser and lexer types).

| File | Lines | Bytes | API lines | What it uses |
|---|---|---|---|---|
| `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/CompatShaderTransformer.java` | 1,214 | 54,896 | 74 | `parseShader`, syntax-error counts, `injectVariable` x23, `rename` x14 (one `Map`), `replaceExpression` x7, `injectFunction` x6, `renameAndWrapShadow` x6, `renameArray` x2, `prependMain` x2, `containsCall` x2, `hasVariable` x2, `appendMain`, `renameFunctionCall(Map)`, `mutateTree`. Lines 283-981 are a hand-written preprocessor with no library use |
| `glsm/.../glsm/GlslTransformUtils.java` | 169 | 7,409 | 18 | Lexer and parser construction (`parseFullQuiet`, `parsePreQuiet`), the token-spaced serializer `getFormattedShader`; the rest is regex |
| `glsm/.../glsm/hooks/ShaderTransformPostProcessor.java` | 13 | 496 | 2 | A type reference in an interface nothing implements or calls (`GLSMHooks.postTransformProcessor` is never assigned) |
| `glsm/.../glsm/shader/GlslVulkanPreprocess.java` | 306 | 15,048 | 36 | Listener plus rule accessors; called only by `SpirvShaderTranslator` |
| `glsm/.../glsm/shader/SpirvShaderTranslator.java` | 418 | 20,979 | 40 | Two listeners; no callers (shaderc/spvc natives are compile-only and not shipped) |
| `shader/.../iris/celeritas/vertices/TerrainVertexFormatRequirements.java` | 133 | 4,973 | 10 | Lexer only: `getAllTokens`, `IDENTIFIER`; called from `CeleritasTerrainPipeline:170` |
| `shader/.../pipeline/transform/AdaptiveShadowBoundsTransformer.java` | 232 | 10,080 | 15 | `hasVariable`, `mutateTree` with a listener over function definitions, the 3-argument `replaceExpression`, `injectVariable`, rule accessors, `getFormattedShader` |
| `.../transform/AttributeTransformer.java` | 75 | 3,150 | 20 | `rename` x8, `injectVariable` x7, `hasVariable` x2, `injectFunction`, `renameFunctionCall`, `replaceExpression` |
| `.../transform/CeleritasTransformer.java` | 91 | 4,157 | 14 | `injectVariable` x3, `injectFunction` x3, `replaceExpression` x3, `prependMain`, `hasVariable`, `removeVariable`, `rename(Map)` |
| `.../transform/CommonTransformer.java` | 103 | 4,609 | 36 | `injectVariable` x12, `rename` x8, `containsCall` x3, `hasVariable` x3, `appendMain` x2, `prependMain` x2, `injectFunction` x2, `renameAndWrapShadow` x2, `renameArray`, `renameFunctionCall(TEXTURE_RENAMES)`, `replaceExpression`; calls `AdaptiveShadowBoundsTransformer` |
| `.../transform/CompatibilityTransformer.java` | 253 | 12,129 | 21 | `replaceExpression`, `removeUnusedFunctions`, `removeConstAssignment`, `findQualifiers(OUT/IN)`, `containsCall`, `injectVariable`, `hasAssigment` x2, `initialize` x2, `ShaderPrinter.getFormattedShader`, rule navigation; three regex patch methods with no library use |
| `.../transform/CompositeDepthTransformer.java` | 24 | 853 | 4 | `findType`, `injectVariable`, `replaceExpression` |
| `.../transform/ComputeTransformer.java` | 11 | 361 | 1 | Passes the `Transformer` to `CommonTransformer` |
| `.../transform/CoreTransformHelper.java` | 59 | 3,106 | 17 | `injectVariable` x9, `rename` x3 (one `Map`), `replaceExpression` x2, `renameFunctionCall`, `injectFunction` |
| `.../transform/DHGenericTransformer.java` | 114 | 6,257 | 24 | `replaceExpression` x9, `rename` x6, `injectFunction` x4, `containsCall`, `hasVariable`, `injectVariable`, `prependMain` |
| `.../transform/DHTerrainTransformer.java` | 106 | 5,666 | 24 | The same set as DHGeneric |
| `.../transform/EntityPatcher.java` | 23 | 972 | 3 | `containsCall`, `hasVariable`, `injectVariable` |
| `.../transform/ShaderTransformer.java` | 496 | 22,762 | 56 | `parseShader` x2, `full()`/`pre()`, `new Transformer` x2, `mutateTree` x2, `injectFunction` x13, `injectVariable` x5, `hasVariable` x4, `findType` x2, `removeVariable` x2, `replaceExpression` x2, `rename`, `renameFunctionCall`; switches on `GLSLLexer` type constants. Line 487 is Celeritas's own `ShaderParser`, not this library |
| `.../transform/TextureTransformer.java` | 51 | 1,809 | 16 | `findType`, `rename`; compares against the sampler token constants |

Files that depend on the library without importing it: `TransformPatcher.java` (281 lines, 13,365 bytes) calls `ShaderTransformer`; `GLStateManager.java` (8,354 lines) at 5554, 5556 and 7287; `GLSMHooks.java:25`; `CeleritasTerrainPipeline.java:170`. `parameter/` (6 files, 218 lines) is library-free.

Tests (all under `src/test/java/`):

| File | Lines | Bytes | API lines | Oracle it uses |
|---|---|---|---|---|
| `com/gtnewhorizons/angelica/glsm/CompatShaderTransformerTest.java` | 473 | 20,855 | 26 | Raw lexer token loop; `PRECISION`, `IFDEF/ELSE/ENDIF/DEFINE/VERSION_DIRECTIVE`, `MACRO_ESC_NEWLINE`, `IDENTIFIER`; pre-parser and parser syntax-error counts; 15 calls of `transform` |
| `com/gtnewhorizons/angelica/glsm/ffp/VertexShaderGeneratorTest.java` | 200 | 9,844 | 23 | `new Transformer`, `findQualifiers(IN/UNIFORM)`, `mutateTree` with two listeners, `getFormattedShader` |
| `net/coderbot/iris/pipeline/transform/AdaptiveShadowBoundsTransformerTest.java` | 303 | 13,097 | 38 | `new Transformer`, `mutateTree`, syntax-error counts, four listeners over statements and expressions, `getFormattedShader` |
| `net/coderbot/iris/pipeline/transform/CeleritasTransformerTest.java` | 166 | 6,359 | 20 | `new Transformer`, `mutateTree`, `ShaderPrinter.getFormattedShader`, a listener counting declarations and identifiers |
| `net/coderbot/iris/pipeline/transform/CompatibilityTransformerTest.java` | 191 | 7,762 | 18 | Syntax-error counts x5, `new Transformer`, `findQualifiers(OUT)`, `mutateTree`, `getFormattedShader` |

Library-independent tests that exercise the pipeline: `TransformPatcherTest` (110 lines; string assertions on token-spaced output), `TransformPatcherCacheTest` (175; cache semantics, eight concurrent misses), `CompatibilityTransformerCaveSkyholeTest` (30; regex), `TerrainVertexFormatRequirementsTest` (63). Fixtures: `src/test/resources/compat_shaders/betterportals_render_portal.{vsh,fsh}`; everything else is inline. There is no shader-pack corpus and no golden file today.

Build wiring: `glsm/build.gradle:25-28` (`implementation`, `transitive = false`, `antlr4-runtime:4.13.2`); `build.gradle:333-339` (`contain`, resolved from the GTNH Nexus for the library and Maven Central for ANTLR and jcpp); `build.gradle:34-35` (`implementation.extendsFrom(contain)`), `build.gradle:154` (subprojects compile against the root classpath); `build.gradle:494-517` (`jar {}`: nested jars at 500, license files at 504, `ContainedDeps` and `NonModDeps` at 516-517); `build.gradle:706-797` (`verifyDistributedJar`; license files required at 733-735); `scripts/test_port_scan.py:55`. Notices: `THIRD_PARTY_NOTICES.md:47-49`; `README.MD:94-95`; `mcmod.info:8`; `third-party/actinium/THIRD_PARTY_NOTICES.md:21` (points at a GTNewHorizons fork URL, Demonica's own notice at TauMC's).

## Appendix B. Verb map

Approximate use counts are from grep over the main code. The building blocks are the glsl-transformer idioms as Iris 26.1 uses them; Steps 3 and 4 confirm each against TauMC's implementing class.

| TauMC verb | TauMC implementing class | Uses | glsl-transformer building block |
|---|---|---|---|
| `injectVariable(String)` | `VariableInjector`, `InjectorPoint` | ~65 | `tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, decl)` |
| `injectFunction(String)` | `FunctionInjector`, `InjectorPoint` | ~34 | `tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS, decl)` |
| `rename(String, String)`, `rename(Map)` | `Renamer` | ~47 | `root.rename(from, to)` |
| `replaceExpression(String, String)` | `ReplaceExpression`, `ExpressionRenamer` | ~36 | `root.replaceReferenceExpressions(t, name, replacement)` when the source is an identifier; otherwise `root.replaceExpressionMatches(t, new AutoHintedMatcher<>(expr, ParseShape.EXPRESSION), replacement)` |
| `replaceExpression(String, String, GLSLParser::function_definition)` | `ReplaceExpression` | 1 | `replaceFunctionDefinition(name, source)`: `t.parseExternalDeclaration(root, source)` and `FunctionDefinition.replaceByAndDelete` |
| `prependMain(String)`, `appendMain(String)` | `PrependFunction`, `AppendFunction` | 7, 3 | `tree.prependMainFunctionBody(t, stmt)`, `tree.appendMainFunctionBody(t, stmt)` |
| `removeVariable(String)` | `RemoveVariable` | 3 | `identifierIndex` to the `DeclarationMember`, then `detachAndDelete` of the member or of its `DeclarationExternalDeclaration` |
| `findType(String)` | `TypeFinder` | 4 | The declaration's `TypeSpecifier`: `BuiltinNumericTypeSpecifier.type` (`Type`) or `BuiltinFixedTypeSpecifier` for samplers |
| `containsCall(String)` | `IdentifierCollector`, `Util` | 9 | `identifierIndex.getStream(fn).anyMatch(id -> id.getParent() instanceof FunctionCallExpression)` |
| `hasVariable(String)` | `HasVariable` | 16 | `identifierIndex.has(name)` or a declaration query; read `HasVariable` first |
| `renameFunctionCall(String, String)`, `(Map)` | `Renamer` variant | 5 | Identifiers whose parent is a `FunctionCallExpression`, `setName` |
| `renameArray(String, String, Set<Integer>)` | `ArrayExpressionRewriteListener` | 3 | `ArrayAccessExpression` matcher with a literal index (Iris `CommonTransformer`'s `glFragDataI` pattern) |
| `renameAndWrapShadow(String, String)` | `FunctionCallWrapper` | 8 | Rename the call and wrap the `FunctionCallExpression` in a constructor call built from a `Template` |
| `removeUnusedFunctions()` | `FunctionRemover`, `TransformerRemover`, `FunctionCollector` | 1 | Iris `CompatibilityTransformer.transformEach`'s unused-function block (`nodeIndex.get(FunctionDefinition.class)`, `identifierIndex.getStream(name).count()`) |
| `removeConstAssignment()` | `ConstAssignmentRemover`, `ConstParameterFinder` | 1 | Iris `transformEach`'s const-parameter block |
| `findQualifiers(int token)` | `QualifierFinder` | 2 | `nodeIndex.get(StorageQualifier.class)` filtered by `StorageType`, up to the `DeclarationExternalDeclaration`, its members |
| `hasAssigment(String)` | `AssigmentChecker` | 2 | `identifierIndex.getStream(name)` with an assignment ancestor on the left-hand side |
| `initialize(declaration, String)` | `Transformer.initialize` | 2 | `tree.prependMainFunctionBody(t, name + " = " + zero + ";")` (Iris `getInitializer`) |
| `mutateTree(Consumer)` | `Transformer` | 4 plus tests | Not needed; `tree` and `root` are exposed |
| `ShaderParser.parseShader`, `ParsedShader.full()/pre()/parser()/preParser()` | `ShaderParser` | 3 plus tests | `ShaderAst.parse`; a parse failure is an exception |
| `ShaderPrinter.getFormattedShader`, `GlslTransformUtils.getFormattedShader` | `ShaderPrinter`, `GlslTransformUtils` | 2 plus tests | `ShaderAst.print(header)` / `printBody()` with `PrintType.INDENTED` |

Never used by Demonica: `injectAtEnd`, `renameArray(Map, Set)`, `removeFunction`, `findConstParameter`, `removeConstAssignment(Map)`, `makeOutDeclaration`, `rewriteStructArrays`, `collectStorage`, the public fields `variable` and `function`, `ShaderParser.parseSnippet`.

## Appendix C. Commands

```
# transform-related tests only (all tests live in the root project; :shader and :glsm have none)
./gradlew :test --tests 'net.coderbot.iris.pipeline.transform.*' \
  --tests 'com.gtnewhorizons.angelica.glsm.CompatShaderTransformerTest' \
  --tests 'com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGeneratorTest' \
  --tests 'net.coderbot.iris.celeritas.vertices.TerrainVertexFormatRequirementsTest' \
  2>&1 | grep -E 'Tests run|FAILED|error:|BUILD' | tail -30

# corpus replay (properties are forwarded to the forked test JVM by the root test {} block, added in Step 2)
./gradlew :test --tests '*TransformCorpusReplayTest' -PglslCorpusDir=$PWD/run/transform-corpus \
  -PglslReplayEngine=douira -PglslReplayPatches=COMPOSITE,COMPUTE 2>&1 | grep -E 'replay|Tests run|FAILED|BUILD' | tail -20

# a dev run, read only through grep
./gradlew runClient -PdevScript=@scripts/cp4.txt -PdevProps=demonica.glsl.engine=douira,demonica.glsmPerfDebug=true \
  > run/s6-cp4.out 2>&1
grep -E 'Dev step|Dev shader pack|Dev stats|ShaderTransformCache|Shader compilation failed|Failed to compile|Exception' run/s6-cp4.out | head -80

# what the jar contains
unzip -l build/libs/Demonica-*.jar | grep -E 'glsl|antlr|jcpp|LICENSE|NOTICES'
unzip -p build/libs/Demonica-*.jar META-INF/MANIFEST.MF | grep -E 'ContainedDeps|NonModDeps'

# Angelica's pre-switch code, from Demonica's own history
git show d96ac280:src/main/java/net/coderbot/iris/pipeline/transform/CompatibilityTransformer.java | sed -n '1,120p'
git diff --stat d96ac280 1df1ec35 -- src/main/java/net/coderbot/iris/pipeline/transform/

# library sources (Step 1 unpacks glsl-transformer; Step 3 fetches TauMC by file)
curl -sL https://repo1.maven.org/maven2/io/github/douira/glsl-transformer/3.0.0-pre3/glsl-transformer-3.0.0-pre3-sources.jar \
  -o run/lib-src/glsl-transformer-sources.jar && mkdir -p run/lib-src/glsl-transformer \
  && unzip -qo run/lib-src/glsl-transformer-sources.jar -d run/lib-src/glsl-transformer
curl -sL https://raw.githubusercontent.com/TauMC/glsl-transformation-lib/main/src/main/java/org/taumc/glsl/Transformer.java \
  -o run/lib-src/taumc/Transformer.java
grep -n "public" run/lib-src/glsl-transformer/io/github/douira/glsl_transformer/ast/query/Root.java | head -60

# screenshot difference without PIL or ImageMagick
ffmpeg -v error -i a.png -f rawvideo -pix_fmt rgb24 a.rgb && ffmpeg -v error -i b.png -f rawvideo -pix_fmt rgb24 b.rgb
python3 -c "import numpy as n; a=n.fromfile('a.rgb',n.uint8); b=n.fromfile('b.rgb',n.uint8); print(abs(a.astype(int)-b.astype(int)).mean())"
```
