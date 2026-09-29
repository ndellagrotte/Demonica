# S09: GLSM subtractions and utilities

Step 9 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `02f161d6` (S1 to S8 and the
orchestrator's S7b done; exit point A reached), on 2026-09-29. Times are UTC (the test XML timestamps).

## Status

**Done.** Every "Done when" item holds.

| Done when | Evidence |
|---|---|
| `ShaderTransformPostProcessor`, `postTransformProcessor`, `ShaderType`, `parseFullQuiet`, `parsePreQuiet` are gone | The brief's grep over `glsm/src shader/src src` (`--include=*.java`) prints `0`. `git grep` over the whole tree outside `docs/` finds none of the five names either. The 0.5.0 jar built afterwards holds no `glsm/shader/ShaderType` or `ShaderTransformPostProcessor` class (count 0) and still holds `UniformType` (count 1) |
| Build green | `./gradlew build verifyModuleBoundaries` (`run/s9-build.out`): `BUILD SUCCESSFUL in 11s`; `:test`, `:verifyDistributedJar`, `:verifyModuleBoundaries`, `:verifyRunClasspath`, `:check` and `:build` all ran. The full `:test` in it: `classes 136 tests 1065 skipped 4 failures 0 errors 0` (test XML, timestamps 04:29) |
| `VertexShaderGeneratorTest` and the GLSM tests green | `./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.*' --rerun` (`run/s9-glsm-after.out`): `BUILD SUCCESSFUL in 3s`; test XML `classes 35 tests 193 skipped 0 failures 0 errors 0`, of which `VertexShaderGeneratorTest tests=8 skipped=0 failures=0 errors=0` (04:29:01). Before the change (`run/s9-glsm-before.out`): the same 35 classes and 193 tests, 0 failures |
| `verifyModuleBoundaries` passes | In the build above (`> Task :verifyModuleBoundaries`, then `BUILD SUCCESSFUL`); `DependencyDirectionTest tests=1 failures=0` in the same `:test` |
| No `glsm.shader.Spirv*`/`GlslVulkanPreprocess` reference | `grep -rn "glsm\.shader\.Spirv\|GlslVulkanPreprocess" glsm/src shader/src src` prints nothing (exit 1) |
| Report | This page |

## Commits

| Commit | Subject |
|---|---|
| `21eb3764` | glsl-transformer: S9 GLSM subtractions and VertexShaderGeneratorTest on ShaderAst |
| the commit that adds this page | glsl-transformer: S9 report and status |

## What changed

Deleted:
- `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/hooks/ShaderTransformPostProcessor.java` (13 lines): an
  interface nothing implemented or called; its default method took TauMC's `GLSLParser.Translation_unitContext`.
- `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/shader/ShaderType.java` (26 lines): its only importer was
  `ShaderTransformPostProcessor`. `glsm/shader/` now holds only `UniformType.java`.

Changed:
- `glsm/.../glsm/hooks/GLSMHooks.java`: the field `public static ShaderTransformPostProcessor postTransformProcessor;`
  (line 25) is gone; nothing assigned or read it.
- `glsm/.../glsm/GlslTransformUtils.java` (169 lines, 7,409 bytes, now 153 lines, 6,741 bytes): `parseFullQuiet` and
  `parsePreQuiet` are gone, and with them the imports of `CharStreams`, `CommonTokenStream`, `GLSLLexer`, `GLSLParser`
  and `GLSLPreParser`. The class imports no TauMC class any more; its last library dependency is ANTLR's
  `ParseTree`/`TerminalNode`, for `getFormattedShader(ParseTree, String)`, which is now `@Deprecated` with a javadoc
  that names its remaining users and Step 11. The regex helpers (`TEXTURE_RENAMES`, `replaceTexture`,
  `renameParseBreakingTextureFunctions`, `renameReservedWords`, `restoreReservedWords`) are unchanged.
- `src/test/java/com/gtnewhorizons/angelica/glsm/ffp/VertexShaderGeneratorTest.java` (200 lines, 9,844 bytes, now
  190 lines, 10,573 bytes): the three tests that used TauMC as their oracle now use `ShaderAst.parse`,
  `ShaderAst.findQualifiers(StorageType.IN/UNIFORM)`, a node query and `GlslTokens`; the file imports nothing from
  `org.taumc` or `org.antlr` (count 0). The five other tests assert on the generator's own text with `String.contains`
  and are unchanged: that text is the generator's output, not a transform's. `VertexShaderGenerator` itself is
  unchanged (`git diff 02f161d6 -- glsm/.../ffp/VertexShaderGenerator.java` is empty).

Not changed: `THIRD_PARTY_NOTICES.md` (its `## Compile-only dependencies` section has no shaderc or spvc line since
`df08c785`; `git grep -i 'lwjgl-shaderc\|spvc\|spirv'` outside `docs/` finds only `RenderBackend.specializeShader`'s
message about SPIR-V binaries, which is the GL 4.6 entry point and unrelated), `glsm/build.gradle` (no SPIR-V lines
left), `GLStateManager.java` (5432 and 5434 still call `renameReservedWords` and `CompatShaderTransformer.transform`,
7162 `generatePassthroughVertexShader`; none of them used what was deleted).

### `VertexShaderGeneratorTest`: old oracles against new

The old test at `02f161d6` asserted these facts through TauMC (the `taumc` column), and the new one asserts them as
the `ShaderAst` column says. Every old fact has a new assertion that is at least as strict.

| Test | Old assertion (TauMC) | New assertion |
|---|---|---|
| `extendedUnitAttributesAreHomogeneousVec4` (unit 3 with a per-vertex texcoord) | A1. `ShaderParser.parseShader(shader).full()`: TauMC recovers from syntax errors silently, so this checked only that no exception was thrown | `ShaderAst.parse(shader)`, which throws `SyntaxException` on any syntax error (stricter) |
| | A2. `findQualifiers(GLSLLexer.IN).get("a_TexCoord3")` exists (a `NullPointerException` otherwise) and its `type_specifier_nonarray` text is `vec4` | `findQualifiers(StorageType.IN).get("a_TexCoord3")` not null (with the shader as the message), `typeName()` is `vec4`, and neither the type nor the declarator carries an array specifier (added) |
| `primaryTextureAttributeAcceptsCompleteHomogeneousCoordinates` (unit 0 with a per-vertex texcoord) | B1. `IN` `a_TexCoord0` has type `vec4` | the same, through `findQualifiers(StorageType.IN)` (not null, `vec4`, no array) |
| | B2. exactly one `assignment_expression` with an assignment operator whose left side's text is `v_TexCoord0` | exactly one `BinaryExpression` of an assignment type (`=`, `*=`, `+=` and the eight others) whose left side's tokens are exactly `[v_TexCoord0]` |
| | B3. that assignment's right side's text is `a_TexCoord0` | its right side's tokens are exactly `[a_TexCoord0]`; also `GlslTokens.contains(shader, "v_TexCoord0 = a_TexCoord0 ;")` (added) |
| | B4. reprint through `GlslTransformUtils.getFormattedShader(tree, "#version 330 core\n")` and parse again with TauMC: again only "no exception" | `ast.print("#version 330 core\n")` and `ShaderAst.parse` of the result, which fails on a syntax error; the reparsed program still declares `in vec4 a_TexCoord0`, still has exactly one assignment to `v_TexCoord0`, and its text contains `v_TexCoord0 = a_TexCoord0 ;` (added) |
| `eyeLinearTexGenFeedsEveryPlaneFromSingleConstructor` (eye-linear texgen on S, T and R) | C1 to C3. `findQualifiers(GLSLLexer.UNIFORM)`: `u_TexGenEyePlaneS`, `T` and `R` exist with type `vec4` | `findQualifiers(StorageType.UNIFORM)`: each not null, `vec4`, no array |
| | C4. no assignment with an assignment operator whose left side's text starts with `texGenCoord.` | `assertFalse(ast.hasAssignment("texGenCoord."))`: `ShaderAst.hasAssignment` is exactly "any assignment of the eleven kinds whose left side's text without whitespace starts with the name" (TauMC `hasAssigment`'s rule, proven by S4's parity tests) |
| | C5. exactly one `single_declaration` whose first declarator is `texGenCoord` with an initializer | exactly one `DeclarationMember` named `texGenCoord` with an initializer, over every declarator of every declaration (`root.nodeIndex`), not only first declarators (stricter) |
| | C6. the initializer's text starts with `vec4(` | the initializer's first two tokens are `vec4`, `(` |
| | C7 to C9. the initializer's text contains `dot(eyePos,u_TexGenEyePlaneS)`, `...T)`, `...R)` | `GlslTokens.contains(initializer, "dot ( eyePos , u_TexGenEyePlaneS )")`, and the same for T and R |

The oracles can fail: five runs against deliberately broken generator output. Each changed only
`VertexShaderGenerator.java` in the working tree, from a copy saved in the session's scratch directory; the copy was
put back after each run (`cmp` silent) and nothing of it was committed.

| Run | Mutation of the generator | Result (test XML) |
|---|---|---|
| 1 (`run/s9-vsg-mutation1.out`) | the unit 2/3 attributes declared `vec2`; the `a_TexCoord0` declaration dropped; the `u_TexGenEyePlaneS` declaration dropped | `8 tests completed, 4 failed`: `extendedUnitAttributesAreHomogeneousVec4` `expected: <vec4> but was: <vec2>`; `primaryTexture...` `a_TexCoord0 not declared in ... expected: not <null>`; `eyeLinearTexGen...` `u_TexGenEyePlaneS not declared in ... expected: not <null>`; and the unchanged string test `units2And3WithPerVertexTexcoordsUseDedicatedAttributes` |
| 2 (`...-mutation2.out`) | `v_TexCoord0 *= 1.0;` after the unit 0 assignment; `texGenCoord.x += 0.0;` after the texgen constructor; the constructor spelled `dvec4(` | `8 tests completed, 2 failed`: `primaryTexture...` `expected: <1> but was: <2>`; `eyeLinearTexGen...` `expected: <false> but was: <true>` (the component write; it fails before the constructor check) |
| 3 (`...-mutation3.out`) | the unit 0 assignment's right side `a_TexCoord0.xyzw`; the constructor spelled `dvec4(` | `2 failed`: `expected: <[a_TexCoord0]> but was: <[a_TexCoord0, ., xyzw]>`; `expected: <[vec4, (]> but was: <[dvec4, (]>` |
| 4 (`...-mutation4.out`) | each eye-linear component written `dot(u_TexGenEyePlaneX, eyePos)` | `1 failed`: `eyeLinearTexGen...` `expected: <true> but was: <false>` (the `dot ( eyePos , u_TexGenEyePlaneS )` check) |
| 5 (`...-mutation5.out`) | the `;` after the unit 0 assignment dropped | `1 failed`: `primaryTexture...` threw `ShaderAst$SyntaxException: line 27:0 missing ';' at '}'` (TauMC would have recovered) |

## Commands run and their outcomes

All test runs pass `--rerun`; counts are read from `build/test-results/test/*.xml` afterwards.

| Command | Outcome |
|---|---|
| `./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.*' --rerun` before any change (`run/s9-glsm-before.out`) | `BUILD SUCCESSFUL in 4s`; `classes 35 tests 193 skipped 0 failures 0 errors 0` |
| `./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGeneratorTest' --rerun` after the rewrite (`run/s9-vsg-1.out`) | `BUILD SUCCESSFUL in 4s` (`:glsm:compileJava` and `:shader:compileJava` ran, with javac's note that `CompatShaderTransformer` and some `shader` files use a deprecated API: the now-deprecated `getFormattedShader`); `VertexShaderGeneratorTest tests=8 skipped=0 failures=0 errors=0`. The stale `ShaderType.class` and `ShaderTransformPostProcessor.class` were gone from `glsm/build/classes` |
| The five mutation runs (table above) | `BUILD FAILED in 2s` each, with the failures listed |
| Verify 1: `grep -rn "SpirvShaderTranslator\|GlslVulkanPreprocess\|SpirvCompiler\|ShaderTransformPostProcessor\|postTransformProcessor\|parseFullQuiet\|parsePreQuiet" --include=*.java glsm/src shader/src src \| wc -l` | `0` |
| `grep -rn "glsm\.shader\.Spirv\|GlslVulkanPreprocess" glsm/src shader/src src` | no output, exit 1 |
| Verify 2: `./gradlew :test --tests 'com.gtnewhorizons.angelica.glsm.*' --rerun` (`run/s9-glsm-after.out`) | `BUILD SUCCESSFUL in 3s`; `classes 35 tests 193 skipped 0 failures 0 errors 0` |
| Verify 3: `./gradlew build verifyModuleBoundaries` (`run/s9-build.out`) | `BUILD SUCCESSFUL in 11s`, no `FAILED` or `error:` line, 0 `warning: [` lines; tasks `:test`, `:verifyDistributedJar`, `:verifyModuleBoundaries`, `:verifyRunClasspath`, `:check`, `:build` ran. Full `:test`: `classes 136 tests 1065 skipped 4 failures 0 errors 0`, `DependencyDirectionTest tests=1 failures=0`; the mini-corpus replay in it: `replay: engine=douira corpus=.../src/test/resources/transform-corpus cases=32 identical=19 (byte-identical 0) accepted=9 failing=0 unsupported=4 recorded=0 filtered=0` (as at S8) |
| `unzip -l build/libs/Demonica-0.5.0-SNAPSHOT.jar` | `glsm/shader/ShaderType` or `ShaderTransformPostProcessor`: 0 entries; `glsm/shader/UniformType`: 1 (the `0.4.0-SNAPSHOT` jars next to it are older leftovers and still have both) |

`./gradlew build` ran once; no `check` run beyond it. No dev run: the step changes no code a running game reaches
(the deleted field was never assigned, the deleted methods were never called).

## Measurements

| File | Before (`02f161d6`) | After |
|---|---|---|
| `GlslTransformUtils.java` | 169 lines, 7,409 bytes; imports: 3 TauMC (`GLSLLexer`, `GLSLParser`, `GLSLPreParser`), 4 ANTLR | 153 lines, 6,741 bytes; imports: 0 TauMC, 2 ANTLR (`ParseTree`, `TerminalNode`, for the deprecated serializer) |
| `VertexShaderGeneratorTest.java` | 200 lines, 9,844 bytes; imports: 5 TauMC, 1 ANTLR | 190 lines, 10,573 bytes; 0 TauMC, 0 ANTLR |
| `glsm/.../shader/` | `ShaderType.java`, `UniformType.java` | `UniformType.java` |
| GLSM tests | 35 classes, 193 tests | 35 classes, 193 tests |
| Full `:test` | 1,065 tests, 4 skipped (S8) | 1,065 tests, 4 skipped |

## Residual diffs

None: no transform output changed. The mini-corpus replay in the full `:test` gives the same summary as at S8
(`cases=32 identical=19 accepted=9 failing=0 unsupported=4`).

| Case | Stage | Classification | Action |
|---|---|---|---|
| (none) | | | |

## Remaining callers of `getFormattedShader` (for Step 11)

`GlslTransformUtils.getFormattedShader(ParseTree, String)`, now deprecated, measured with
`grep -rn "GlslTransformUtils\.getFormattedShader\|GlslTransformUtils#getFormattedShader" --include=*.java glsm/src shader/src src`
after the commit:

Main code:
- `glsm/.../glsm/CompatShaderTransformer.java:273` (Step 10 moves it to `ShaderAst.print`).
- `shader/.../pipeline/transform/ShaderTransformer.java:131`, `:136`, `:224`, `:243` (the old engine).
- `shader/.../pipeline/transform/AdaptiveShadowBoundsTransformer.java:142` (old).

Tests:
- `src/test/.../transform/CompatibilityTransformerTest.java:173`, `AdaptiveShadowBoundsTransformerTest.java:238` (the
  TauMC copies).
- `src/test/.../transform/AstShaderTransformerTest.java:158` (its `taumc(...)` helper).
- `src/test/.../transform/ShaderAstCorpusDifferential.java:345`.
- `src/test/.../transform/ShaderAstParityTest.java:232`, `:374`, `:1546`, `:1608`, `:1675`, and the javadoc at `:45`.
- A javadoc mention only: `glsm/.../transformer/ShaderAst.java:745`.

TauMC's own `ShaderPrinter.getFormattedShader` (not this method) is also still called at
`shader/.../transform/CompatibilityTransformer.java:125` (old), `src/test/.../transform/CeleritasTransformerTest.java:108`
and `ShaderAstParityTest.java:1361`. `VertexShaderGeneratorTest` no longer calls either.

## Deviations from the brief

- Test runs use `--rerun` (the orchestrator's rule); the brief's Verify 2 has none.
- The brief's goal says `GlslTransformUtils` becomes "regex-only"; it keeps the deprecated `getFormattedShader` (as Do 3
  says) and therefore its two ANTLR tree imports until Step 11.
- `VertexShaderGeneratorTest` gained assertions the old test did not have (no array specifier; the reparsed program's
  declaration and assignment; `GlslTokens.contains` on the assignment). Its five string-based tests are unchanged.
- The orchestrator's extra checks (the old-against-new oracle table, the mutation runs, the caller list) are in this
  report.

## Open questions

1. `UniformType` (`glsm/shader/`) is imported only by `PerFrameUniformBlock`; after this step the `glsm.shader` package
   holds that one enum. Move it next to its user (`glsm.hooks`) some day, or leave the package? Not in this plan's
   scope; left alone.
2. S8's open questions stand unchanged.

## Notes for the next step

- Handoff: `UniformType` survived (imported by `glsm/.../hooks/PerFrameUniformBlock.java:3`); `ShaderType` was deleted
  (its only importer was `ShaderTransformPostProcessor`). No notice line was removed in this step: the shaderc and
  spvc lines went at `df08c785`, before the plan started.
- `GlslTransformUtils.getFormattedShader` is `@Deprecated`, so javac prints "uses or overrides a deprecated API" notes
  for `CompatShaderTransformer` and for `shader` files. They are notes, not `warning: [` lines, and the build has no
  `-Werror`. Step 10 removes the `CompatShaderTransformer` caller; Step 11 deletes the method with the old engine.
- After Step 10 moves `CompatShaderTransformer`, GLSM main code's only TauMC import will be gone: `GlslTransformUtils`
  has none now, and `ShaderAst` mentions `org.taumc.glsl.Transformer` only in its javadoc.
- `VertexShaderGeneratorTest` shows the pattern for reading generated or transformed GLSL on glsl-transformer without
  TauMC: `ShaderAst.parse`, `findQualifiers(StorageType.X)` (`typeName()`, `arraySpecifierText()`, `member()`), an
  `ASTVoidVisitor` over `ast.tree` for assignments (`BinaryExpression` with an assignment `ExpressionType`),
  `ast.root.nodeIndex.getStream(DeclarationMember.class)` for declarators, `ShaderAst.text(node)` plus `GlslTokens`
  for text. `glsm/CompatShaderTransformerTest` (Step 10) can follow it.
- Test classes that still import `org.taumc.glsl` (`grep -rln 'org\.taumc\.glsl' src/test/java`, eight now):
  `glsm/CompatShaderTransformerTest`, `celeritas/vertices/TerrainVertexFormatScanParityTest`, and in
  `pipeline/transform/`: `AdaptiveShadowBoundsTransformerTest`, `AstShaderTransformerTest`, `CeleritasTransformerTest`,
  `CompatibilityTransformerTest`, `ShaderAstCorpusDifferential`, `ShaderAstParityTest`.
