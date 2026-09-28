# S03: `ShaderAst`, the core verbs

Step 3 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `6eeea2da` (S1 and S2 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds.

| Done when | Evidence |
|---|---|
| Parity green for the twelve verbs | `ShaderAstParityTest`: 129 tests, 0 failures, 0 errors, 1 skipped (the corpus mode, which needs `-PglslCorpusDir`), on the tree committed as `80fda189`. The corpus mode on `run/transform-corpus` (811 inputs, 221 distinct): `unexplained=0`; on the mini-corpus (27 inputs, 24 distinct): `unexplained=0`. After the [verification follow-up](#verification-follow-up): 141 tests, 0 failures, 1 skipped; corpus mode `unexplained=0` on both |
| Every verb has a javadoc line stating its semantics | `ShaderAst`: each public verb's javadoc starts "TauMC `<verb>`: ..." and names what it matches, what it skips and its deviations |
| Report | this page |

## Commits

| Commit | Subject |
|---|---|
| `3e1f5fa9` | glsl-transformer: S3 ShaderAst core verbs, parity test and corpus differential |
| `80fda189` | glsl-transformer: S3 query parity over every identifier of every fixture |
| `2ab0db35` | glsl-transformer: S3 report and status (also a javadoc-only fix: `ShaderAst.findType`'s javadoc now starts "TauMC `findType`:" like the other verbs') |
| `3f9f926f` | glsl-transformer: S3 fix replaceExpression matching by exact tree equality (the [verification follow-up](#verification-follow-up)) |
| the commit that records `3f9f926f` here and in `STATUS.md` | glsl-transformer: S3 fix status |

## What changed

Added:
- `glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAst.java` (815 lines). The package is the
  plan's; the project is `glsm`, not `shader` (see Deviations 2).
- `src/test/java/net/coderbot/iris/pipeline/transform/ShaderAstParityTest.java`: fixtures, the per-verb parity tests,
  the deviation tests, life-cycle tests, and the corpus mode's entry point `corpusDifferential`.
- `src/test/java/net/coderbot/iris/pipeline/transform/ShaderAstCorpusDifferential.java`: the corpus mode (not a test
  class itself).

Changed:
- `GlslTokens` (S2's helper, reused): two more canonical forms, both printer normalizations the corpus exposed. A
  square matrix type is one token (`mat2x2` is `mat2`, `dmat4x4` is `dmat4`), because glsl-transformer parses both
  into one `Type` and prints the short name. Directly nested grouping parentheses are one pair (`((a + b))` is
  `(a + b)`), because glsl-transformer's `ASTBuilder.visitGroupingExpression` keeps one `GroupingExpression` for any
  nesting; the parentheses of a call, a constructor, an array-indexed call or a control statement are not groupings,
  so `f((a, b))` stays distinct from `f(a, b)`. `GlslTokensTest` has a test for each (10 tests now).
- `GlslCorpusParseSurveyTest`: `prepare` and `restoreHoisting` are package-private (the corpus mode prepares Iris
  inputs with them).

Outside git (`run/` is ignored): `run/lib-src/taumc/` (the TauMC sources below and `SHA256SUMS`),
`run/lib-src/taumc-main/` (8 classes from `main`, fetched only to compare), the outputs `run/s3-*.out`, and the corpus
mode's diffs under `build/reports/shader-ast-parity/`.

### `ShaderAst` API

- Fields: `public final ASTParser t`, `public final TranslationUnit tree`, `public final Root root`.
- Factory: `static ShaderAst parse(String source, int version)` (lexer at GLSL `version`; `IllegalArgumentException`
  if glsl-transformer has no `Version` for it: it knows 110, 120, 130, 140, 150, 330, 400 to 460) and
  `static ShaderAst parse(String source)` (lexer from the `#version` directive, the library default GLSL 460 if there
  is none). The parser comes from one package-private factory, `static ASTParser newParser(DirectiveFilter)`, called
  once per `parse`: a plain `ASTParser` (no `EnumASTTransformer`, no `JobParameters`), parsing cache `NONE`, then the
  filter. Root: `ShaderAst.ROOT_SUPPLIER = RootSupplier.PREFIX_UNORDERED_ED_EXACT`.
- `SyntaxException` (public nested, `RuntimeException`): both ways glsl-transformer reports a syntax error become it,
  with the original as the cause; the message starts with ANTLR's `line L:C`, `line()` and `column()` give them.
- `droppedDirectives()`: what the parse dropped, as `line N: #define` (filtered directives) or the `#pragma` text; also
  logged at WARN by the `ShaderAst` log4j logger.
- `print(String header)`: `header + "\n" + ` the body printed with `PrintType.INDENTED`, after removing the version
  statement and every `ExtensionDirective` from `tree` (mirrors `GlslTransformUtils.getFormattedShader(tree, header)`);
  `printBody()` is `print("")`.
- `public static final ReentrantLock BUILD_LOCK`: see Notes (thread safety).
- `findType` returns `ShaderAst.DeclaredType` (sealed: `Numeric(util.Type type)`, `Fixed(BuiltinFixedTypeSpecifier.BuiltinType type)`;
  `keyword()`, `is(Type)`, `is(BuiltinType)`), or `null` where TauMC returned 0.

### The verbs

The [verification follow-up](#verification-follow-up) at the end of this page changed `replaceExpression`'s matching
(no more `Matcher`) and `removeVariable`'s `for`-initializer case, and named further deviations; where it differs from
the rows below, it wins.

TauMC's semantics are those of `Transformer` at `7dd88a4`, which implements each verb over cached parse-tree rule
contexts; the listener classes the brief lists (`Renamer`, `ReplaceExpression`, `HasVariable`, ...) back the older
`Util` API, which Demonica does not call. In TauMC's grammar a call `f(x)` is a postfix expression whose callee is a
`variable_identifier`, a member selection `.x` is a `variable_identifier`, parameters are `parameter_declarator`, and
`texture2D`/`texture3D` are keywords.

| Verb | TauMC behaviour | Adapter implementation | Parity | Deviations |
|---|---|---|---|---|
| `injectVariable` | Inserts before an anchor: first external declaration that is a function definition or holds a `storage_qualifier`; an injected qualified declaration becomes the anchor, an injected function both anchors; no anchor, nothing inserted | Same anchor rule over `tree.getChildren()` (storage qualifiers found by walking each declaration down), `t.parseExternalDeclaration(root, code)` inserted at the anchor's index | 9 fixture cases; corpus 221/221 (as a 6-step injection sequence) | A removed anchor is recomputed (TauMC: `IndexOutOfBoundsException`) |
| `injectFunction` | Inserts before the first function definition; an injected function becomes both anchors | Same, over `tree.getChildren()` | 6 fixture cases; corpus in the same sequence | No function in the program: appended at the end (TauMC: `IndexOutOfBoundsException`, test `deviationInjectFunctionWithoutAnyFunction`) |
| `rename(String,String)`, `rename(Map)` | Renames `typeless_declaration`, `variable_identifier` and `function_prototype` identifiers, all at once | `identifierIndex` entries whose parent is `DeclarationMember`, `ReferenceExpression`, `FunctionCallExpression`, `MemberAccessExpression` or `FunctionPrototype`; `Identifier.setName` after collecting | 12 fixture cases (struct fields, swizzles, parameters, blocks, types, layout names, a swap); corpus 221/221 single and 221/221 map (one name of each of 11 kinds) | None. Like TauMC it leaves parameter declarations alone while renaming their uses |
| `replaceExpression(String,String)` | Binary pass, then postfix pass, over contexts whose text equals the pattern's | Identifier pattern: `ReferenceExpression`s of the name, plus call names if the replacement is an identifier. Other patterns: `Matcher` on every ancestor of the pattern's class of each occurrence of its longest identifier (walked to the top, not only the nearest), pattern parsed into a separate root | 14 fixture cases; corpus identifier 218/218, array access 199/199, member access 211/211, call 192/192, product 45 identical + 142 explained, sum 75 identical + 112 explained | Literals match by value; the printer parenthesizes a replacement where needed; a replacement in postfix position is kept whole; a binary pattern does not also replace its first operand everywhere; a call of the name is left alone when the replacement is not an identifier (tests `deviationReplacementKeepsItsPrecedence`, `deviationLiteralsMatchByValue`, `deviationBinaryPatternAlsoReplacesItsFirstOperandInTauMC`) |
| `prependMain`, `appendMain` | Parses a statement and adds it first/last in every `main` definition; no `main`, nothing | `main` definitions via the identifier index, `getBody().getStatements().add(...)` | 7 fixture cases; corpus 221/221 | Empty `main` body gets the statement (TauMC: `NullPointerException`, test `deviationEmptyMainGetsTheStatement`) |
| `removeVariable` | Scans declarators in document order; the first shared one is removed alone and stops the scan; a sole one is remembered, the last wins, and its whole declaration goes | Same scan over `DeclarationMember`s sorted into document order; `detachAndDelete` of the member, or of the `DeclarationExternalDeclaration`/`DeclarationStatement` | 11 fixture cases; corpus 531/531 (sole global, shared, sole local) | Removing an initialized or sized first declarator (`float a = 1.0, b;`) gives `float b;`, where TauMC kept `a`'s initializer under `b`'s name (test `deviationRemovingAnInitializedFirstDeclarator`) |
| `findType` | First `single_declaration` (document order) declaring the name; the type keyword's token, 0 if none; a struct type is skipped | Same over `TypeAndInitDeclaration` members; `DeclaredType.Numeric`/`Fixed` or null | 4 named-list cases + 8 all-identifier cases; corpus 4,988/4,988 queries | Returns a `DeclaredType`, not a token (planned) |
| `containsCall` | Any `variable_identifier` with the name: references, call names, member selections | `identifierIndex` entries whose parent is `ReferenceExpression`, `FunctionCallExpression` or `MemberAccessExpression` | 4 + 8 cases; corpus 6,377/6,377 | `texture2D`/`texture3D` are identifiers here, keywords in TauMC (test `deviationTexture2DIsAnIdentifier`); no Demonica caller asks about them |
| `hasVariable` | A `typeless_declaration` or `function_prototype` with the name | Parent `DeclarationMember` or `FunctionPrototype` | 3 + 8 cases; corpus 6,397/6,397 | None |
| `renameFunctionCall(String,String)`, `(Map)` | `function_prototype` names, every `variable_identifier`, and the `texture2D`/`texture3D` keyword tokens | `rename`'s targets without `DeclarationMember` | 6 fixture cases (`TEXTURE_RENAMES` twice); corpus 216/216 single, 221/221 map | None visible: glsl-transformer's `texture2D` calls are identifiers, so the keyword special case is not needed |
| `renameArray` | Every `name[i]` with a bare base: `Integer.parseInt` of the index text (throws otherwise), token becomes `newName + i`, `[i]` dropped | `ArrayAccessExpression` with a `ReferenceExpression` base; an `int` literal index, else `NumberFormatException`; replaced by a parsed identifier | 6 fixture cases + the throwing case; corpus 335/335, and 84/84 non-literal cases where both threw `NumberFormatException` | Octal `07` gives `newName7` (TauMC `newName07`); a negative index throws (TauMC wrote `newName-1`) |

## Commands run and their outcomes

TauMC sources, fetched by raw URL at the ref the brief names first:
```
$ for f in Transformer ShaderParser Util ...; do curl -s -o run/lib-src/taumc/$f.java -w '%{http_code}' https://raw.githubusercontent.com/TauMC/glsl-transformation-lib/7dd88a4/src/main/java/org/taumc/glsl/$f.java; done
```
All 18 classes the brief lists answered 200 from `7dd88a4` (65,092 bytes together); `main` was not needed. The GitHub
API resolves `7dd88a4` to `7dd88a47492c67b579ee133842915d65f0b0b4b4` (2026-03-30, "Two fixes for sues: Ensure proper
const removal, multi pass dead function removal"); `main` is `db6e8cbd205de370fa9691d2d37fedc82cf3c229` (2026-06-14).
Also fetched from `7dd88a4`, because `Transformer` depends on them: `TransformerCollector`, `TransformerRemover`,
`src/main/antlr/GLSLParser.g4`, `src/main/antlr/GLSLLexer.g4` (22 files, 95,139 bytes; SHA-256 of each in
`run/lib-src/taumc/SHA256SUMS`, `Transformer.java` `b768d68d85cb0935cd1999ca262ebff6e9d7d0af03f08765343a30d3735c4492`).

Source against the pinned jar (`glsl-transformation-lib-0.2.0-32.g7dd88a4-GTNH.jar` in the Gradle cache): of 8 classes
fetched from both refs, `Transformer` and `Util` differ; in `Transformer` the only difference is `prependMain` and
`appendMain`, which on `main` call `Util.injectStatement`. `javap -c -p` of the jar's `Transformer.prependMain` shows
`Statement_listContext` and `java/util/List.add:(ILjava/lang/Object;)V` then `scanNode`, the `7dd88a4` code, and the
jar's `Util` has no `injectStatement` (`javap -c -p ... org.taumc.glsl.Util | grep -c injectStatement` = 0). The test
`deviationEmptyMainGetsTheStatement` also sees the `7dd88a4` behaviour (TauMC throws `NullPointerException` on an empty
`main`).

The brief's Verify command, on the tree committed as `80fda189` (`run/s3-verify.out`; `--rerun` so that the tests
executed):
```
$ ./gradlew :test --tests '*ShaderAstParityTest' --tests '*GlslTransformerSpikeTest' --rerun 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
BUILD SUCCESSFUL in 2s
```
Gradle prints no "Tests run" line; from `build/test-results/test/`: ShaderAstParityTest 129 tests, 0 failures,
0 errors, 1 skipped; GlslTransformerSpikeTest 3 tests, 0 failures (19:47:39 UTC).

Corpus mode, the pack corpora (`run/s3-corpus-packs-final.out`, at `3e1f5fa9`'s `ShaderAst`):
```
$ ./gradlew :test --tests '*ShaderAstParityTest.corpusDifferential' -PglslCorpusDir=$PWD/run/transform-corpus
shader-ast-parity: corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=17
shader-ast-parity:   baseline {IDENTICAL=221}
shader-ast-parity:   injectVariable+injectFunction {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   prependMain+appendMain {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   removeVariable {IDENTICAL=531} changedTheProgram=531
shader-ast-parity:   rename {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   rename(Map) {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   renameArray {IDENTICAL=335} changedTheProgram=335
shader-ast-parity:   renameArray(non-literal) {BOTH_THREW=84} changedTheProgram=0
shader-ast-parity:   renameFunctionCall {IDENTICAL=216} changedTheProgram=216
shader-ast-parity:   renameFunctionCall(Map) {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   replaceExpression(ArrayAccess) {IDENTICAL=199} changedTheProgram=199
shader-ast-parity:   replaceExpression(Call) {IDENTICAL=192} changedTheProgram=192
shader-ast-parity:   replaceExpression(MemberAccess) {IDENTICAL=211} changedTheProgram=211
shader-ast-parity:   replaceExpression(Product) {IDENTICAL=45, DIFFERENT=142} changedTheProgram=187
shader-ast-parity:   replaceExpression(Sum) {IDENTICAL=75, DIFFERENT=112} changedTheProgram=187
shader-ast-parity:   replaceExpression(identifier) {IDENTICAL=218} changedTheProgram=218
shader-ast-parity:   query containsCall same=6377 different=0
shader-ast-parity:   query findType same=4988 different=0
shader-ast-parity:   query hasVariable same=6397 different=0
shader-ast-parity:   explained 142x replaceExpression(Product): TauMC's postfix pass cut the binary pattern to its first operand and replaced that operand everywhere
shader-ast-parity:   explained 112x replaceExpression(Sum): TauMC's postfix pass cut the binary pattern to its first operand and replaced that operand everywhere
BUILD SUCCESSFUL in 19s
```
The mini-corpus (`run/s3-corpus-mini-final.out`): `inputs=27 distinct=24 parseFailures=0 skipped=0 unexplained=0`,
baseline 24/24 identical, every mutating verb identical on every input where it applied (removeVariable 37, renameArray
9, replaceExpression identifier 23, array access 9, call 7, member access 15), queries containsCall 277, findType 132,
hasVariable 286 with 0 different; product 6 identical + 1 explained, sum 1 identical + 2 explained.

How the mode works (the orchestrator's addition; `ShaderAstCorpusDifferential` javadoc): every Iris stage input is
prepared as `ShaderTransformer` hands it to TauMC (`GlslCorpusParseSurveyTest.prepare`), every compat input as
`CompatShaderTransformer.transformInternal` does before its parse (its private steps called by reflection), identical
texts once. Per input: the unmodified print on both libraries (the baseline); `findType`, `hasVariable` and
`containsCall` for up to 6 names of each of 11 kinds (global and local declarators, parameters, functions, called
names, references, member selections, struct members, type names, block names, layout names) plus an absent name; and
each mutating verb on a fresh parse on both sides with arguments taken from the input. "changedTheProgram" counts the
applications whose TauMC output differs from the baseline, so a verb that silently did nothing on both sides would show
there. A difference counts as explained only when the adapter followed by an emulation of the known TauMC quirk prints
exactly TauMC's output. It runs on 4 threads.

Other checks:
- Appendix C transform tests (`run/s3-transform-tests.out`): `BUILD SUCCESSFUL in 2s`; 14 classes, 179 tests, 0
  failures, 0 errors, 3 skipped (the three corpus-gated tests), at the time ShaderAstParityTest had 105 tests.
- Full build, the step's one `check` run (`run/s3-build.out`, before `80fda189` added 24 query tests):
  `./gradlew build` gave `BUILD SUCCESSFUL in 9s`; `:test` executed: 128 classes, 666 tests, 0 failures, 0 errors,
  4 skipped (S2's 559 plus ShaderAstParityTest's 105 and GlslTokensTest's 2). `verifyCeleritasPin`,
  `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`,
  `verifyRunClasspath` and `verifyS8tnlibPin` ran. The mod jar holds
  `net/coderbot/iris/pipeline/transform/transformer/ShaderAst*.class` (9 entries).
- Determinism replay after the `GlslTokens` change (`run/s3-replay-packs.out`, `run/s3-replay-mini.out`): `taumc`
  against `taumc` 424/424 and 16/16 byte-identical, as in S2.

## Measurements

- Corpus mode: 17 s for the pack corpora (221 distinct inputs; 3,465 verb applications plus 221 baselines, and
  17,762 queries, each on both libraries), under 1 s for the mini-corpus.
- The 811 recorded stage inputs are 221 distinct texts already as recorded (a SHA-256 count over
  `run/transform-corpus/*/*/in.*glsl`); S2's 424 cases are distinct by their parameters and both stages, and many share
  a stage text.
- Library facts found while implementing, each checked in the sources and by a test or a probe:
  - `TranslationUnitFilterCachingParser.parse` calls itself for a translation unit, so
    `ParsingCacheStrategy.ALL_EXCLUDING_TRANSLATION_UNIT` overflows the stack (test
    `parsingCacheStrategiesThatHideOrBreakTheFilter`).
  - With `TWO_TIER` (the spike's and Iris's choice), the second parse of the same text is served from the cache and the
    filter sees no tokens (same test). `ShaderAst` uses `NONE`: a translation unit is parsed at most once per
    `TransformPatcher` cache miss anyway, so the cache saved nothing, and snippet ASTs are still cached by the
    parser's own AST cache (`ASTCacheStrategy.ALL_EXCLUDING_TRANSLATION_UNIT`, the default), which is independent of
    the parsing cache.
  - After a failed fast (SLL) pass, `EnhancedParser.parse` resets the lexer and parses again (LL) without resetting the
    token source. Whether the filter then sees tokens twice was not observed; `ShaderAst`'s filter keys its records by
    the token's offset, so a second pass cannot record a directive twice.
  - Syntax errors: whatever prediction or ANTLR's one-token recovery reports ("no viable alternative", "missing ';'",
    "extraneous input") arrives as ANTLR's `ParseCancellationException`; only an `InputMismatchException` that the
    recovery cannot fix becomes glsl-transformer's `ParsingException` (the test's example is `#version foo`: `line 1:9 mismatched input 'foo'
    expecting {...} (Unexpected token 'foo')`). Both become `SyntaxException` (test
    `syntaxErrorsBecomeOneExceptionWithTheLine`).
  - Every AST node takes its root from `Root.activeBuildRoots`, a static unsynchronized `ArrayDeque`
    (`ASTNode` field initializer `Root.getActiveBuildRoot()`; `ASTBuilder.build` and `cloneInto` push through
    `Root.indexNodes`). See Notes (thread safety).
  - `VariableDeclaration(TypeQualifier, Stream<Identifier>)` stores the qualifier without `setup(...)`, so the
    `TypeQualifier` of `layout(local_size_x = 8) in;` or `invariant gl_Position;` has no parent (jshell probe against
    the jar; the mini-corpus `compute` case exposed it).
  - Printer: `FLOAT32` literals are always `Double.toString(value) + "f"` (`ASTPrinter.visitLiteralExpression`, the
    `FLOAT32` case); `OutputOptions` has only `printInfo`, `headerSuffix` and `printCustomDirectives`, so there is **no
    switch** for the suffix. `mat2x2` prints as `mat2`; `((x))` prints as `(x)`; the printer adds parentheses where an
    expression's parent binds tighter (`ASTPrinter.enterExpression`), so a replaced subexpression keeps its meaning.
  - `Matcher(String, ParseShape)` parses with the static `ASTParser._getInternalInstance()`, which is not thread-safe;
    `ShaderAst` builds its patterns with its own parser into a separate root and passes the node to `Matcher`.

## Residual diffs

No replay of an engine runs in this step (the new engine is not ported until Step 5). What the parity work found:

| Case | Stage | Classification | Action |
|---|---|---|---|
| 31 of 221 distinct pack inputs, unmodified print (first corpus run) | all stages | printer normalization: `((x))` printed as `(x)` (the diff read was `s * ( ( s - 2.0 * p ) )` in `bsl/00045-ATTRIBUTES-37425c65` fragment) | `GlslTokens` canonicalizes directly nested groupings; the next run's baseline was 221/221 identical, so all 31 were this |
| fixture `FRAGMENT_330`, `uniform mat2x2 rotation;` | fragment | printer normalization: `mat2x2` printed as `mat2` | `GlslTokens` canonicalizes square matrix names |
| 254 pack and 3 mini-corpus `replaceExpression` applications with a product or sum pattern | all | TauMC bug: its postfix pass cut the pattern to its first operand and replaced that operand everywhere | not reproduced; explained by emulation in the corpus mode; test `deviationBinaryPatternAlsoReplacesItsFirstOperandInTauMC`; no Demonica pattern is binary |
| 84 pack `renameArray` applications on arrays with a non-literal index | all | both throw `NumberFormatException` | none (parity) |
| mini-corpus `compute`, injection sequence | compute | ShaderAst bug, fixed: the `layout(...) in;` anchor was missed because of the parentless `TypeQualifier` | anchor detection walks down; fixture "a compute layout declaration is a qualified declaration" |
| mini-corpus `compat-betterportals-*`, fed raw | vertex, fragment | input artefact: TauMC's lexer hides whole `#if` blocks, glsl-transformer keeps every branch; production preprocesses first | compat inputs are now prepared as GLSM does; identical after |

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, not "Claude Fable 5.1" (the orchestrator's
   rule).
2. `ShaderAst` is in the `glsm` project (`glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/`), in
   the plan's package. `glsm` cannot see `shader` (`shader` has `api project(':glsm')`), and Step 10's
   `CompatShaderTransformer` (in `glsm`) must call it. `verifyModuleBoundaries` only rejects a class defined twice, and
   `glsm` already holds a non-GLSM package (`net.minecraftforge.eventbus`). The Iris transformers ported in Steps 5 to 7
   go in the same package under `shader/`, so they need no import.
3. Parsing cache `NONE` instead of the spike's `TWO_TIER`, for the reasons under Measurements; the orchestrator asked
   for the choice and the reason.
4. `BUILD_LOCK` is not in the brief: every `ShaderAst` method that builds nodes holds a JVM-wide reentrant lock.
5. `#pragma` (a grammar node in glsl-transformer) is removed from the tree at parse and recorded with the filtered
   directives, because TauMC's full parse never saw it.
6. `print(header)` returns `header + "\n" + body` (the `getFormattedShader` contract) and removes the version statement
   and extension directives from `tree`, as the brief says; it is therefore not repeatable with a different result.
7. `GlslTokens` (S2's) gained two canonical forms and `GlslCorpusParseSurveyTest`'s two helpers became
   package-private; the brief said to reuse `GlslTokens`, which this does.
8. The corpus mode (orchestrator's addition) lives in `ShaderAstCorpusDifferential`, prepares compat inputs through
   `CompatShaderTransformer`'s private pre-parse steps by reflection, and explains a difference only by emulation of a
   named TauMC quirk.
9. Beyond the brief's five to ten inputs per verb: the query verbs also run over every identifier of each of the eight
   fixtures (`queriesOverEveryIdentifier`, 24 tests), and `ShaderAstParityTest` has life-cycle tests (syntax errors,
   directives on repeated parses, the parsing-cache facts, lexer version, concurrent use).

## Open questions

1. Placement: keep `ShaderAst` in `glsm` under `net.coderbot.iris.pipeline.transform.transformer` (split package across
   two projects of one jar), or move it to a `com.gtnewhorizons.angelica.glsm.*` package? Nothing depends on the
   package yet beyond the tests.
2. Step 10 float suffix hazard: glsl-transformer prints every float as `1.0f`, which GLSL 1.10 rejects (suffixes came
   in 1.30), and there is no printer switch. Today the transformed compat output never goes below 330:
   `CompatShaderTransformer.transformInternal` writes `#version <targetVersion> core` with
   `targetVersion = max(declared, backend minimum)` (330 without a backend), the only production backend
   (`Lwjgl3GLRenderBackend.getMinGLSLVersion()`) returns 330, and all 37 recorded compat cases carry
   `minGlslVersion=330`. So the hazard is real only for a backend with a minimum below 130 or a path that prints a
   legacy shader through glsl-transformer. If one appears, the fix is a subclass of `ASTPrinter` (public,
   `ASTPrinter(TokenProcessor)`) that overrides `visitLiteralExpression` for `FLOAT32`, printed through
   `ASTPrinter.printAST(PrintType.INDENTED.getTokenProcessor(), tree)`. Step 10 should confirm.
3. The deviations are all where TauMC threw or wrote wrong GLSL. Should any be reproduced for byte-level parity
   instead? None changes a recorded output in the corpora.

## Notes for the next step

- Where: `glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAst.java`
  (`grep -n "public" ...` for the API). Construct only through `ShaderAst.parse`.
- `findType` results: numeric types are `glsl_transformer.util.Type`: `float` `FLOAT32`, `vec2`/`vec3`/`vec4`
  `F32VEC2`/`F32VEC3`/`F32VEC4`, `int` `INT32`, `ivec2`..`ivec4` `I32VEC2`..`I32VEC4`, `uint` `UINT32`,
  `uvec2`..`uvec4` `U32VEC2`..`U32VEC4`, `bool` `BOOL`, `mat4` (and `mat4x4`) `F32MAT4X4`. Samplers are
  `BuiltinFixedTypeSpecifier.BuiltinType`: `SAMPLER1D`/`2D`/`3D`, `ISAMPLER*`, `USAMPLER*`, `SAMPLER2DRECT`,
  `ISAMPLER2DRECT`, `USAMPLER2DRECT`, `SAMPLER2DSHADOW`, ... (same names as TauMC's tokens). Port
  `TextureTransformer.isTypeValid` and the `mc_Entity`/`mc_midTexCoord` switches with `DeclaredType.is(...)` or a
  `switch` on the records; `null` is TauMC's 0.
- **Thread safety (Step 5).** glsl-transformer 3.0.0-pre3 is unsafe for concurrent node construction even with one
  parser per thread: the active build root is a static `ArrayDeque`. `ShaderAst` holds `ShaderAst.BUILD_LOCK` around
  every build (parse, and every verb that parses a snippet), so separate instances on separate threads are safe
  (`concurrentUseOnSeparateInstancesIsSafe`: 8 threads, 64 runs, outputs equal). Code that builds nodes itself through
  `t`, `tree` or `root` (Iris idioms such as `parseAndInjectNode`, `new LiteralExpression(...)`) must hold the lock.
  Holding it around a whole stage transform is simplest; it serializes the new engine's parsing across the
  `Shader-Transform-*` threads, which Step 5's `transformMs` comparison should measure.
- **Step 4, `findQualifiers`.** Appendix B's plan ("`nodeIndex.get(StorageQualifier.class)` ... up to the
  `DeclarationExternalDeclaration`") breaks on `VariableDeclaration`s: their `TypeQualifier` has no parent, so the walk
  up ends at the qualifier. Walk down from the external declarations instead (as `ShaderAst.holdsStorageQualifier`
  does), or treat a parentless `TypeQualifier` specially (as `isAttached` does).
- **Step 5 replay.** Two printer normalizations are now in `GlslTokens` (`mat2x2`, `((x))`); without the second, 31 of
  the 221 distinct prepared pack inputs already differed before any verb ran. Floats and integers were already
  canonical (S2).
- TauMC anchors: `ShaderAst` reproduces TauMC's declaration order exactly (the corpus sequence of six injections is
  identical on all 221 inputs), so the replay should not need "declaration order" entries for injections. The order
  `findType` and `removeVariable` scan in is another matter: see the verification follow-up.
- The lexer version set by `parse` stays on the parser for the verbs' snippets. The orchestrator's `#version` for the
  new engine (S5) should be the effective version, as `GlslCorpusParseSurveyTest.prepare` writes it.
- `replaceExpression` patterns in Demonica are names, array and member accesses and calls; all four shapes are
  identical on the corpus. The three-argument form (`AdaptiveShadowBoundsTransformer`) is Step 4's
  `replaceFunctionDefinition`.
- Corpus mode: `./gradlew :test --tests '*ShaderAstParityTest.corpusDifferential' -PglslCorpusDir=<abs>`, then
  `grep shader-ast-parity`; diffs in `build/reports/shader-ast-parity/` (25 kept per verb), summary in `summary.txt`.
  Step 4 can add its verbs to `ShaderAstCorpusDifferential.applications`.
- TauMC sources for Step 4: `run/lib-src/taumc/` has `Transformer.java` (whose `removeUnusedFunctions`,
  `removeConstAssignment`, `findQualifiers`, `hasAssigment`, `initialize`, `renameAndWrapShadow` Step 4 needs) and
  `TransformerCollector`/`TransformerRemover`; fetch the listener classes Step 4 lists from `7dd88a4` the same way.

## Verification follow-up

An independent verification (outputs `run/s3v-*.out`) found one blocking issue and made eight remarks. Fixed on
2026-09-28 on `feat/glsl-transformer` in `3f9f926f` (code, tests, this section); the next commit records it in
`STATUS.md`. Status stays **done**.

### Blocking: `replaceExpression` call patterns matched calls whose argument list is a prefix

**Confirmed, and wider than reported.** glsl-transformer 3.0.0-pre3's `Matcher.matches` (`ast/query/match/Matcher.java`
lines 185-195) walks the candidate and compares it item by item with the pattern's pre-order items (node classes and
data) but never checks that every pattern item was consumed, so a candidate whose items are a prefix of the pattern's
matches. The verifier's proposed fix, requiring a match in both directions, is not enough: the item sequence has no
list boundaries, so two calls with the same identifiers nested differently have equal sequences. The probe
`run/s3f-matcher-probe.out` (jshell against the test classpath, `new Matcher<>(x).matches(y)` and the reverse, each
side parsed with `ASTParser.parseExpression` into its own root):
```
f(g(a), b) vs f(g(a, b)): fwd=true bwd=true
f(a, b) vs f(a): fwd=true bwd=false
vec4(worldpos, 0.0) vs vec4(worldpos): fwd=true bwd=false
f(g(a), h(b)) vs f(g(a, h(b))): fwd=true bwd=true
f(a, b) vs f(a, b): fwd=true bwd=true
```

**Fix.** `ShaderAst.replaceExpression` no longer uses `Matcher`. A private `structure(ASTNode)` records the same items
the `Matcher` compares (the node class of every node, the data the visitor reports: identifier names, literal types,
values and integer formats, type and qualifier enums) plus an end marker after each node's children, and a candidate
matches when its structure list equals the pattern's. That is exact tree equality, with literals still equal by
value (`deviationLiteralsMatchByValue` unchanged). The javadoc now says so, and says occurrences are collected before
anything is replaced.

**Tests.** Five parity cases in `ShaderAstParityTest.replaceExpression`, on two new fixtures:
- `OVERLOADS_330` (overloads `f(float)`, `f(float, float)`, `g(float)`, `g(float, float)`; calls
  `vec4(f(a), f(a, b), f(b), 0.0)` and `vec4(f(g(a), b), f(g(a, b)), 0.0, 0.0)`): pattern `f(a, b)` (the verifier's
  repro 1), `f(a)`, `f(g(a, b))` and `f(g(a), b)`;
- `CELERITAS_VEC4_330` (`uniform vec4 worldpos; ... gl_Position = vec4(worldpos);`): CeleritasTransformer's pattern
  `vec4(worldpos, 0.0)` with its replacement (the verifier's repro 2).

Before the fix (`run/s3f-before-fix.out`), four of the five failed, as the probe predicts (`f(a)` passes: a longer
candidate never matched):
```
ShaderAstParityTest > replaceExpression() > a call pattern with more arguments than a call of the same overloaded name FAILED
ShaderAstParityTest > replaceExpression() > Celeritas's constructor pattern on a one-argument constructor FAILED
ShaderAstParityTest > replaceExpression() > a nested call pattern against a call whose argument sits one level up FAILED
ShaderAstParityTest > replaceExpression() > a nested call pattern against a call whose argument sits one level down FAILED
141 tests completed, 5 failed, 1 skipped
```
(the fifth failure is the new `deviationRemovingAForInitializerKeepsTheLoop`, below). The diffs were the ones reported,
for example `- o = vec4 ( f ( a ) , iris_z , f ( b ) , 0.0 ) ;` against `+ o = vec4 ( iris_z , iris_z , f ( b ) , 0.0 ) ;`
and `- gl_Position = vec4 ( worldpos ) ;` against
`+ gl_Position = iris_ProjectionMatrix * gbufferModelView * vec4 ( worldpos , 1.0 ) ;`; the nested cases replaced both
`f(g(a), b)` and `f(g(a, b))`.

**Corpus mode.** Its call pattern was the lexicographically first small call of each program, which never exposed a
prefix. `ShaderAstCorpusDifferential` now also applies that call with one more argument
(`f(x, y, iris_parityExtra)`), which occurs nowhere, so both sides must leave the program alone. Before the fix
(`run/s3f-corpus-packs-before-fix.out`):
```
shader-ast-parity: corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=25 seconds=18
shader-ast-parity:   replaceExpression(CallWithAnExtraArgument) {IDENTICAL=167, DIFFERENT=25} changedTheProgram=0
```
After it, below.

### Remarks

| # | Remark | Resolution |
|---|---|---|
| 1 | `removeVariable` of a variable declared only in a `for` initializer deleted the whole loop (`target.getParent().getParent()` is the `ForLoopStatement`) | **Fixed.** When the `TypeAndInitDeclaration`'s parent is a `ForLoopStatement`, only the declaration is detached (`setInitDeclaration(null)` through the self-replacer), giving `for (; i < 4; i++)`. TauMC writes `for ( i < 4 ; i ++ )`, not GLSL; either way `i` is undeclared. Named in the javadoc; test `deviationRemovingAForInitializerKeepsTheLoop` (it failed before the fix: the loop was gone) |
| 2 | The order `findType` and `removeVariable` treat as TauMC's is right only before a mutation | **Documented, not emulated.** Confirmed in `Transformer.java`/`TransformerCollector.java`: TauMC's rule-context cache is a `LinkedHashSet` per rule; `injectVariable`, `injectFunction`, `prependMain`, `appendMain` and `replaceExpression` (`replaceNode`) call `scanNode`, which appends what they added. Emulating it would mean tracking an insertion epoch for every node the verbs add, for a case no current caller reaches (below). Javadoc of both verbs corrected; test `deviationDeclarationOrderAfterAnInjection` asserts the verifier's repro (TauMC `findType` = `VEC2`, `ShaderAst` = `FLOAT32`; `removeVariable` removes the injected uniform in TauMC and the local in `ShaderAst`) |
| 3 | TauMC's `replaceExpression` by-text cache is not updated by `rename`, `renameFunctionCall`, `renameArray` | **Named deviation.** Confirmed in the source: `cachedContextsByText` is built at the first `replaceExpression` of a rule and only `TransformerCollector`/`TransformerRemover` update it; the renames call `setText` on tokens. So TauMC misses a renamed node under its new name and still finds it under its old one. Test `deviationReplaceExpressionSeesRenamedIdentifiers` asserts both (`y = b + a` against `y = b + d`; and `rename(c, e)` then `replaceExpression(c, d)`: TauMC `y = b + d`, `ShaderAst` `y = b + e`). Named in the javadoc |
| 4 | Unnamed divergences where TauMC writes broken or wrong GLSL | **Named** in the javadoc and each asserted by a test: ternary replacement truncated by TauMC's binary pass (`deviationTernaryReplacementIsKeptWhole`: `x > u > 0.0` against `x > ( u > 0.0 ? 1.0 : 2.0 )`); non-postfix replacement under unary minus (`deviationReplacementKeepsItsPrecedence` now also has `y = -x;`: `- a` against `- ( a + b )`); self-referential replacement (`deviationSelfReferentialReplacementAppliesOnce`: `f ( f ( f ( f ( v ) ) ) )` against `f ( f ( f ( v ) ) )`); `renameArray` index `+1` (`deviationRenameArrayWithAUnaryPlusIndexThrows`: TauMC records 1 and writes `a + 1`, `ShaderAst` throws `NumberFormatException`); struct name in `S[2](...)` and `arr.length()` under `rename` (`deviationRenameLeavesTypeNamesAndLength`) |
| 5 | `rename("texture2D", ...)`: javadoc and comment mention it, the test asserted only `containsCall` | **Fixed.** `deviationTexture2DIsAnIdentifier` also asserts that `rename("texture2D", "texture")` leaves TauMC's `texture2D ( texture , texcoord )` and renames it in `ShaderAst`; `rename`'s javadoc names it |
| 6 | `BUILD_LOCK` is held around the whole `parseTranslationUnit`, lexing and ANTLR parsing included | **Unchanged, carried to Step 5.** The lock has to cover the AST build, and glsl-transformer's `ASTParser.parseTranslationUnit` does the parse and the build in one call; splitting them means driving `EnhancedParser` and `ASTBuilder` directly. Step 5 measures `transformMs` first (Notes, thread safety) |
| 7 | Other claims reproduced; mini-corpus replay and Appendix C run not re-run by the verifier | Appendix C re-run below; the `taumc` replay is not affected (no change to `GlslTokens` or the replayer) and was not re-run |
| 8 | Verifier outputs in `run/s3v-*.out`; scratch test removed | Nothing to do |

None of the remarks' cases is reachable from Demonica's current arguments, with one production shape to know about:
`CELERITAS_TERRAIN` runs `patchMultiTexCoord3` (`rename("gl_MultiTexCoord3", "mc_midTexCoord")`, then
`injectVariable("attribute vec4 mc_midTexCoord;")`) and then `replaceMidTexCoord` (`findType`, `removeVariable`,
`replaceExpression` of `mc_midTexCoord`), after `CeleritasTransformer` has already called `replaceExpression`. It
runs only when the vertex shader declares `gl_MultiTexCoord3` (`hasVariable`), and then remarks 2 and 3 both apply:
TauMC's `findType` sees the renamed declaration first, its `removeVariable` removes the injected one, and its last
`replaceExpression` misses the renamed references. No recorded corpus vertex input declares `gl_MultiTexCoord3`
(verifier's check).

### Commands run and their outcomes

- `./gradlew :test --tests '*ShaderAstParityTest' --tests '*GlslTransformerSpikeTest' --rerun` (the brief's Verify,
  `run/s3f-verify.out`): `BUILD SUCCESSFUL in 3s`; from `build/test-results/test/`: ShaderAstParityTest 141 tests,
  0 failures, 0 errors, 1 skipped (the corpus mode); GlslTransformerSpikeTest 3 tests, 0 failures (20:20 UTC).
  ShaderAstParityTest has 12 more tests than at `80fda189`: 5 parity cases and 7 deviation tests.
- Corpus mode, pack corpora (`run/s3f-corpus-packs.out`):
  ```
  shader-ast-parity: corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=18
  shader-ast-parity:   replaceExpression(Call) {IDENTICAL=192} changedTheProgram=192
  shader-ast-parity:   replaceExpression(CallWithAnExtraArgument) {IDENTICAL=192} changedTheProgram=0
  ```
  Every other line is as at `80fda189` (baseline 221/221, removeVariable 531/531, product 45 identical + 142 explained,
  sum 75 + 112, queries containsCall 6,377, findType 4,988, hasVariable 6,397 with 0 different).
- Corpus mode, mini-corpus (`run/s3f-corpus-mini.out`): `inputs=27 distinct=24 parseFailures=0 skipped=0
  unexplained=0`; `replaceExpression(CallWithAnExtraArgument) {IDENTICAL=7} changedTheProgram=0`, the rest as before.
- Appendix C transform tests with `--rerun` (`run/s3f-transform-tests.out`): `BUILD SUCCESSFUL in 2s`; 14 classes,
  215 tests, 0 failures, 0 errors, 3 skipped (the corpus-gated ones).
- Full build, this follow-up's one `check` run (`run/s3f-build.out`): `./gradlew build` gave `BUILD SUCCESSFUL in 8s`;
  `:test` executed: 128 classes, 702 tests, 0 failures, 0 errors, 4 skipped. `verifyCeleritasPin`,
  `verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`,
  `verifyRunClasspath` and `verifyS8tnlibPin` ran. The mod jar holds 10 `ShaderAst*.class` entries (one more
  anonymous visitor). It ran before two javadoc-only edits (`ShaderAst`, `ShaderAstCorpusDifferential`); the Verify
  command after them recompiled both and passed again (`run/s3f-verify-final.out`: 141 tests, 0 failures, 1 skipped;
  spike 3 tests, 0 failures).
- Skipped: the `taumc` determinism replay (nothing it reads changed) and any dev run (no engine is wired yet).

### Notes for the next step (additions)

- `replaceExpression` matching is exact tree equality now. Do not use glsl-transformer's `Matcher` or
  `AutoHintedMatcher` for patterns without wildcards in Steps 4 to 10 (Iris's `replaceExpressionMatches` idiom uses
  it): it has the prefix and nesting holes above. `matches` never checks that the pattern was consumed, with or
  without wildcards, so a wildcard pattern needs the same care (compare structures, or check the match some other
  way).
- Step 5's replay: a `CELERITAS_TERRAIN` vertex shader that declares `gl_MultiTexCoord3` can differ (remarks 2 and
  3; not observed, no recorded input has one); such a diff is TauMC's cache order or its stale by-text cache, not a
  port error.
