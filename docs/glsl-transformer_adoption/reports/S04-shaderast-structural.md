# S04: `ShaderAst`, the structural verbs

Step 4 of [the adoption plan](../ADOPTION_PLAN.md). Branch `feat/glsl-transformer`, from `fc042ac6` (S1 to S3 done) on
2026-09-28.

## Status

**Done.** Every item under "Done when" holds, and the four S3 leftovers the orchestrator listed are closed.

| Done when | Evidence |
|---|---|
| Parity green for all nineteen verbs and the queries | `ShaderAstParityTest`: 375 tests, 0 failures, 0 errors, 1 skipped (the corpus mode, which needs `-PglslCorpusDir`), on the tree committed as `53bc8702`. Corpus mode on `run/transform-corpus` (811 inputs, 221 distinct, 107 stage groups): `unexplained=0`; on the mini-corpus (27 inputs, 24 distinct, 10 groups): `unexplained=0`. Every Step 4 verb and query is identical on every input where it applied; the only differences are S3's 254 + 3 explained binary-pattern cases |
| Report | this page, with the complete verb table below |

S3 leftovers (orchestrator's list):

| # | Leftover | Resolution |
|---|---|---|
| 1 | `injectVariable` anchor diverged from TauMC after `injectFunction` inserted a qualified declaration | **Matched.** `ShaderAst` now rebuilds TauMC's rule-context cache order and fixes the anchor with TauMC's rule (below). The verifier's repro is a parity test; it fails against S3's rule with the reported `iris_r; iris_q; main`. S03 report corrected with a dated line |
| 2 | `removeVariable` of an unbraced `if`/`for` body left a null statement; `printBody()` threw `NullPointerException` | **Fixed and named.** The declaration statement is replaced by an empty statement (`if (c) ;`). TauMC's output (`if (c) y = 2.0;`: the next statement became the body) is asserted in `deviationRemovingADeclarationThatIsAnUnbracedBody`, which throws the reported `NullPointerException` against S3's code |
| 3 | `containsCall("length")` with only `arr.length()`: TauMC true, `ShaderAst` false | **Named.** `containsCall`'s javadoc; test `deviationContainsCallDoesNotSeeLength` |
| 4 | S03's `ShaderAst.java` line count was stale (815) | **Corrected** in S03 with a dated note: 815 at `3e1f5fa9` to `2ab0db35`, 886 from `3f9f926f` (and at `fc042ac6`) |

## Commits

| Commit | Subject |
|---|---|
| `53bc8702` | glsl-transformer: S4 ShaderAst structural verbs, TauMC cache order, parity tests |
| the commit that adds this page | glsl-transformer: S4 report (this page and the S03 corrections) |
| the next one | glsl-transformer: S4 status (the S4 row of `STATUS.md`, with both hashes, and its facts) |

## What changed

Changed (no files added or deleted in git):
- `glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAst.java` (886 to 1,467 lines): the seven
  verbs, the queries, the cache-order machinery, the S3 fixes; javadoc for every new verb and for the changed ones.
- `src/test/java/net/coderbot/iris/pipeline/transform/ShaderAstParityTest.java` (968 to 1,730 lines; 141 to 375 tests):
  seven Step 4 fixtures, parity tests per verb and query, `transformGrouped` written on `ShaderAst`, the S3 leftover
  tests; S3's `deviationDeclarationOrderAfterAnInjection` became four parity cases.
- `src/test/java/net/coderbot/iris/pipeline/transform/ShaderAstCorpusDifferential.java` (745 to 911 lines): the new
  verbs as applications, the new queries, and a grouped pass over the stages of each Iris case.
- `docs/glsl-transformer_adoption/reports/S03-shaderast-core.md`: three dated corrections (line count, the anchor
  claim, remark 2 now emulated). `STATUS.md`: the S4 row and facts.

Outside git: `run/lib-src/taumc/` gained the nine TauMC classes below (their SHA-256 appended to `SHA256SUMS`), the
outputs `run/s4-*.out`, and the corpus mode's reports under `build/reports/shader-ast-parity/`.

### New `ShaderAst` API

```java
public void renameAndWrapShadow(String oldName, String newName)
public void removeUnusedFunctions()
public void removeConstAssignment()
public Map<String, QualifiedDeclaration> findQualifiers(StorageQualifier.StorageType type)
public boolean hasAssignment(String name)
public void initialize(QualifiedDeclaration declaration, String name)
public int replaceFunctionDefinition(String name, String newSource)
public void replaceFunctionDefinition(FunctionDefinition definition, String newSource)
public List<FunctionInfo> functions()
public static String source(FunctionDefinition definition)
public static String text(ASTNode node)
public boolean isDeclaredGlobal(String name)

public record QualifiedDeclaration(String name, String typeText, String typeName, String arraySpecifierText,
                                   TypeAndInitDeclaration declaration, DeclarationMember member)
public record FunctionInfo(String name, String returnType, List<Parameter> parameters, FunctionDefinition node)
    // + public String bodyText()
public record Parameter(String type, String name, FunctionParameter node)
```

### TauMC's cache order

Where a TauMC verb takes "the first" or "the last" of something, it reads its rule-context cache: a `LinkedHashSet` per
grammar rule, filled in document order by the constructor's walk, to which `scanNode` appends every subtree a verb adds
(`injectVariable`, `injectFunction`, `prependMain`, `appendMain`, `replaceNode` for both `replaceExpression` forms and
`renameAndWrapShadow`; `initialize` goes through `prependMain`). Renames change tokens in place and do not move
anything. `ShaderAst` now records each subtree its verbs add with a sequence number (`added(...)`), and
`inTauMCOrder(type, filter)` walks the tree down from the root, tags each node with the sequence number of the nearest
addition around it (0 for the parsed program), and sorts stably by it. `renameArray` hands its replacement the
addition number of the access it replaces, since TauMC renamed that token in place. Users of the order:

- the variable anchor (leftover 1): the first storage qualifier (or `layout(...) in;`) in that order, if its external
  declaration comes before the first function definition in that order, else that function;
- the function anchor: the first function definition in that order;
- `findType` and `removeVariable` (S3 remark 2, see Deviations 6);
- `findQualifiers` (its key order, below) and `removeConstAssignment` (its single pass over identifiers).

### The verbs

TauMC's semantics are those of `Transformer` at `7dd88a4`. The listener classes the brief names
(`FunctionCallWrapper`, `FunctionRemover`, `ConstAssignmentRemover`, `ConstParameterFinder`, `QualifierFinder`,
`AssigmentChecker`, `StorageCollector`) back the older `Util` API (`grep` finds them only in `Util.java`), which
Demonica does not call; `Transformer`'s own methods implement the verbs over the cache, as S3 found for the core verbs.
`initialize` uses `BuiltinFunction`'s table.

| Verb | TauMC behaviour | Adapter implementation | Parity | Deviations |
|---|---|---|---|---|
| `injectVariable` | Inserts before the variable anchor; fixes it at the first call from the first cached storage qualifier (if before the first cached function) or the first function; injected qualified declarations and functions move it | S3's, with the anchor fixed by TauMC's rule over `inTauMCOrder` (S4) | S3's 9 cases + 4 anchor cases (S4); corpus 221/221 six-injection sequence and 221/221 `injectFunction(qualified)+injectVariable` | A removed anchor is fixed again (TauMC: `IndexOutOfBoundsException`) |
| `injectFunction` | Before the first cached function; injected functions move both anchors | S3's, first function in TauMC order | S3's 6 cases; corpus as above | No function: appended (TauMC throws) |
| `rename`, `rename(Map)` | S3 | S3 | S3: 12 cases, corpus 221/221 twice | S3's |
| `replaceExpression(String,String)` | S3 | S3, replacements recorded as additions | S3: 19 cases; corpus as S3 | S3's |
| `prependMain`, `appendMain` | S3 | S3, statements recorded as additions | S3: 7 cases; corpus 221/221 | S3's |
| `removeVariable` | S3's scan, over the cache order | S3's scan over `inTauMCOrder` (S4); an unbraced body becomes an empty statement (S4) | S3's 11 cases + 3 order cases; corpus 531/531 and 208/208 `injectVariable+removeVariable(localName)` | S3's, plus the unbraced body (TauMC changed the program's meaning) |
| `findType` | S3, first in cache order | S3 over `inTauMCOrder` (S4) | S3's + 1 order case; corpus 4,988/4,988 and 208/208 after an injection | Returns a `DeclaredType` (planned) |
| `containsCall` | S3 | S3 | S3; corpus 6,377/6,377 | S3's `texture2D`; `arr.length()` (S4, named) |
| `hasVariable` | S3 | S3 | S3; corpus 6,397/6,397 | None |
| `renameFunctionCall(String,String)`, `(Map)` | S3 | S3 | S3; corpus 216/216, 221/221 | S3's |
| `renameArray` | S3 | S3; the replacement keeps the access's addition number | S3; corpus 335/335, 84 both threw | S3's |
| `renameAndWrapShadow` | Every postfix call with arguments whose text starts with `oldName(` is replaced by a parse of `vec4(<text>)` (cached list, so a call inside an already wrapped call is not reached); then `renameFunctionCall(oldName, newName)` | Calls of `oldName` with arguments in document order; each still attached is replaced by a parse of `vec4(<printed call>)`, recorded as an addition; then `renameFunctionCall` | 8 cases (7 changing): CommonTransformer's 2 and CompatShaderTransformer's 6 renames over `shadow2D(s, p).r`, `shadow2DProj(s, p).r`, a call in a larger expression and as a function argument, a nested call, a whole `vec4`, a user function of the name, added calls; corpus 221/221 twice (26 changed) | None |
| `removeUnusedFunctions` | Loop: remove every prototype name (definitions and declarations) that no `variable_identifier` uses, except `main`, until a pass removes nothing | Loop over file-scope definitions and declarations, `containsCall(name)` as the use test, until a pass removes nothing | 18 cases (2 changing: a chain, prototypes, a local named like a function, overloads, mutual recursion); corpus 221/221 (159 changed) | TauMC looped forever on an unused local prototype; `arr.length()` is no use of a function `length` |
| `removeConstAssignment` | Collect parameters whose first qualifier is `const` per function name; one pass over cached identifiers: a name in the list, inside a function of that name, inside a `single_declaration` (type or first declarator) adds that declarator and removes the whole type qualifier if it starts with `const` | Same, over `inTauMCOrder` identifiers; `singleDeclarationOf` stands for the rule; `TypeQualifier.detachAndDelete()` | 13 cases (4 changing: `in const` not collected, chains, a second declarator missed, a member name, overloads, `const highp`, prototypes, `for` initializers, array sizes, forward uses); corpus 221/221 alone and after `removeUnusedFunctions` (0 changed on packs, 1 on the mini-corpus) | Unnamed `const` parameters and declarations without declarators are skipped (TauMC: `NullPointerException`) |
| `findQualifiers` | `HashMap` name to `single_declaration` for every cached storage qualifier of the token type inside a `single_declaration`, first declarator then the others | Same walk over `inTauMCOrder` `StorageQualifier`s under a `TypeAndInitDeclaration`'s type; filled into a `HashMap` in that order, frozen into an unmodifiable `LinkedHashMap` (TauMC's iteration order) | 113 cases: 16 sources x 7 storage types (key order, type text, `getText`, keyword, type array) + collisions after injections; corpus 1,547/1,547 | Qualified declarations without declarators are skipped (TauMC NPE); `mat2x2` is `mat2` in `typeName` |
| `hasAssignment` (TauMC `hasAssigment`) | Any `assignment_expression` whose left side's text starts with the name | Any assignment-type `BinaryExpression` whose left side's compact text starts with the name | 16 cases (every identifier of 15 sources, plus prefixes); corpus 6,397/6,397 | None |
| `initialize` | `prependMain(name + " = " + zero + ";")` for a keyword type, from `BuiltinFunction` | Same zero values for `BuiltinNumericTypeSpecifier`s; nothing for others | 15 cases (14 changing) over every type TauMC knows, a second declarator, a type array; corpus 155/155 (IN) and 91/91 (OUT) | `double` types get `0.0lf`, where TauMC wrote broken GLSL (`deviationInitializeDoubles`); opaque types do nothing (TauMC NPE) |
| `replaceFunctionDefinition` (TauMC `replaceExpression(src, new, function_definition)`) | Replaces every function definition whose text equals `src`'s with a parse of `new` | Replaces every definition named `name` with the new definition's parameter types; recorded as an addition; anchors move to the replacement | 5 cases (all changing): both overloads of `texture2DShadow2x2`, `SampleFilteredShadow` with the instrumented guard and the stats buffer, anchors before and after; corpus 184/184 | Selection by name and signature instead of text (the same definition in a valid program) |
| `functions()`, `source`, `text` | (parse-tree reads in `AdaptiveShadowBoundsTransformer`) | `FunctionInfo` per definition | 15 cases: name, return type, parameters, body tokens and the answers to AdaptiveShadowBounds' text searches; corpus 221/221 | Types are printed with spaces (`highp float`) where TauMC's `getText()` had none |
| `isDeclaredGlobal` | (no TauMC verb; a `typeless_declaration` outside every function) | A declarator of a file-scope `TypeAndInitDeclaration` | 15 cases (every identifier); corpus 6,397/6,397 | None |

`findQualifiers`' `typeText` includes every qualifier, printed on one line with spaces: `flat out float`,
`layout(location = 0) out vec4`, `out float` for both `mat` and `recolor` of `out float mat, recolor;`. TauMC's
`fully_specified_type().getText()` is it without spaces (`flatoutfloat`, what `CompatibilityTransformerTest` expects).
`typeName` is the type without qualifiers or array (`float`, `vec3`, a struct name, an inline struct's text without
whitespace); `arraySpecifierText` is the array on the type (`[2]` for `out vec3[2] w;`), null for `out vec3 v[2];`,
whose array is on `member()`, as TauMC's `type_specifier().array_specifier()` was.

## Commands run and their outcomes

TauMC sources, fetched by raw URL at `7dd88a4` into `run/lib-src/taumc/` (S3 had fetched `Transformer`,
`TransformerCollector`, `TransformerRemover`, `FunctionCollector` and the grammar):
```
$ for f in FunctionCallWrapper FunctionRemover FunctionCollector ConstAssignmentRemover ConstParameterFinder QualifierFinder AssigmentChecker StorageCollector BuiltinFunction ShaderPrinter; do ... curl -s -o $f.java -w '%{http_code}' https://raw.githubusercontent.com/TauMC/glsl-transformation-lib/7dd88a4/src/main/java/org/taumc/glsl/$f.java; done
FunctionCallWrapper 200 1173
FunctionRemover 200 949
have FunctionCollector
ConstAssignmentRemover 200 2086
ConstParameterFinder 200 1713
QualifierFinder 200 1654
AssigmentChecker 200 794
StorageCollector 200 651
BuiltinFunction 200 2716
ShaderPrinter 200 15514
```
27,250 bytes; SHA-256 appended to `run/lib-src/taumc/SHA256SUMS` (for example `BuiltinFunction.java` `68f0ee660f00bc62...`).

Probes of the pinned TauMC jar (jshell, classpath from the Gradle cache; scripts in the session scratchpad), before any
code was written:
- `renameAndWrapShadow("shadow2D", "texture")` on a shader with `shadow2D(s, vec3(shadow2D(t, p).r))`:
  `vec4(texture(s, vec3(texture(t, p).r)))`, the inner call renamed but not wrapped; `shadow2D(s, p).r` became
  `vec4(texture(s, p)).r`.
- `findQualifiers(OUT)` key order `mat, c, v, recolor, w, isMoon` (a `HashMap`); `out vec3 v[2]` has no type array,
  `out vec3[2] w` has one; `ShaderPrinter` prints the types `flat out float`, `layout(location = 0) out vec4`.
- `removeConstAssignment`: `in const float c` is not collected; `const float e = 3.0, h = d;` untouched (second
  declarator); `const highp float y = x + 1.0;` became `float y`.
- `initialize` on every type: `0.0f`, `vec3(0.0f)`, `0`, `ivec2(0)`, `0u`, `uvec4(0u)`, `false`, `bvec2(false)`,
  `mat3(0.0f)`, `mat2x2(0.0f)`; `double` printed `line 1:9 mismatched input 'd'` and gave `d_o = 0.0`; `dvec2`
  `no viable alternative at input 'dvec2(0.0d'` and gave `dv_o =;`.
- `hasAssigment("color")` true when only `colorOut` is assigned; `colorO` true; `o` false.

The brief's Verify command, on the tree committed as `53bc8702` (`run/s4-verify.out`, `--rerun`):
```
$ ./gradlew :test --tests '*ShaderAstParityTest' --tests '*GlslTransformerSpikeTest' --rerun 2>&1 | grep -E 'Tests run|FAILED|BUILD' | tail -20
BUILD SUCCESSFUL in 2s
```
Gradle prints no "Tests run" line; from `build/test-results/test/`: ShaderAstParityTest 375 tests, 0 failures,
0 errors, 1 skipped; GlslTransformerSpikeTest 3 tests, 0 failures (21:15:27 UTC).

The leftover tests against S3's behaviour (`run/s4-s3-behaviour.out`): S3's anchor rule, document-order scan and
unbraced-body removal were put back into `ShaderAst` temporarily, the test class run, and the fixed file restored (a
`cmp` against the saved copy matched):
```
ShaderAstParityTest > deviationRemovingADeclarationThatIsAnUnbracedBody() FAILED
ShaderAstParityTest > declarationOrderAfterAnInjection() > findType finds the local, which TauMC scanned before the injected uniform FAILED
ShaderAstParityTest > declarationOrderAfterAnInjection() > removeVariable removes the injected uniform, the last TauMC scanned FAILED
ShaderAstParityTest > declarationOrderAfterAnInjection() > a declaration prepended to main is scanned after the program's own FAILED
ShaderAstParityTest > declarationOrderAfterAnInjection() > two injections are scanned in the order they were made FAILED
ShaderAstParityTest > replaceFunctionDefinition() > the first function replaced before the first injection: TauMC's anchor moves on FAILED
ShaderAstParityTest > injectionAnchorFollowsTauMCsCacheOrder() > a qualified declaration injected as a function does not anchor before the program's own qualifier FAILED
375 tests completed, 7 failed, 1 skipped
```
The unbraced-body failure was `java.lang.NullPointerException: Cannot invoke "...ASTNode.accept(...)" because "node" is
null`; the anchor failure's diff was `- uniform float iris_q ; uniform float iris_r ; + uniform float iris_q ;`, S3's
`iris_r; iris_q; main` against TauMC's `iris_q; iris_r; main`.

Corpus mode, the pack corpora (`run/s4-corpus-packs-final.out`; `run/s4-corpus-packs-2.out` before the last
test-only edits printed the same lines):
```
$ ./gradlew :test --tests '*ShaderAstParityTest.corpusDifferential' -PglslCorpusDir=$PWD/run/transform-corpus
shader-ast-parity: corpus=/home/nick/IdeaProjects/Demonica/run/transform-corpus inputs=811 distinct=221 parseFailures=0 skipped=0 unexplained=0 seconds=26
shader-ast-parity:   baseline {IDENTICAL=221}
shader-ast-parity:   initialize(IN) {IDENTICAL=155} changedTheProgram=155
shader-ast-parity:   initialize(OUT) {IDENTICAL=91} changedTheProgram=91
shader-ast-parity:   injectFunction(qualified)+injectVariable {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   injectVariable+injectFunction {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   injectVariable+removeVariable(localName) {IDENTICAL=208} changedTheProgram=0
shader-ast-parity:   prependMain+appendMain {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   removeConstAssignment {IDENTICAL=221} changedTheProgram=0
shader-ast-parity:   removeUnusedFunctions {IDENTICAL=221} changedTheProgram=159
shader-ast-parity:   removeUnusedFunctions+removeConstAssignment {IDENTICAL=221} changedTheProgram=159
shader-ast-parity:   removeVariable {IDENTICAL=531} changedTheProgram=531
shader-ast-parity:   rename {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   rename(Map) {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   renameAndWrapShadow(Common) {IDENTICAL=221} changedTheProgram=26
shader-ast-parity:   renameAndWrapShadow(Compat) {IDENTICAL=221} changedTheProgram=26
shader-ast-parity:   renameArray {IDENTICAL=335} changedTheProgram=335
shader-ast-parity:   renameArray(non-literal) {BOTH_THREW=84} changedTheProgram=0
shader-ast-parity:   renameFunctionCall {IDENTICAL=216} changedTheProgram=216
shader-ast-parity:   renameFunctionCall(Map) {IDENTICAL=221} changedTheProgram=221
shader-ast-parity:   replaceExpression(ArrayAccess) {IDENTICAL=199} changedTheProgram=199
shader-ast-parity:   replaceExpression(Call) {IDENTICAL=192} changedTheProgram=192
shader-ast-parity:   replaceExpression(CallWithAnExtraArgument) {IDENTICAL=192} changedTheProgram=0
shader-ast-parity:   replaceExpression(MemberAccess) {IDENTICAL=211} changedTheProgram=211
shader-ast-parity:   replaceExpression(Product) {IDENTICAL=45, DIFFERENT=142} changedTheProgram=187
shader-ast-parity:   replaceExpression(Sum) {IDENTICAL=75, DIFFERENT=112} changedTheProgram=187
shader-ast-parity:   replaceExpression(identifier) {IDENTICAL=218} changedTheProgram=218
shader-ast-parity:   replaceFunctionDefinition {IDENTICAL=184} changedTheProgram=184
shader-ast-parity:   transformGrouped {IDENTICAL=107} changedTheProgram=0
shader-ast-parity:   query containsCall same=6377 different=0
shader-ast-parity:   query findQualifiers same=1547 different=0
shader-ast-parity:   query findType same=4988 different=0
shader-ast-parity:   query findType(afterInjectVariable) same=208 different=0
shader-ast-parity:   query functions same=221 different=0
shader-ast-parity:   query hasAssignment same=6397 different=0
shader-ast-parity:   query hasVariable same=6397 different=0
shader-ast-parity:   query isDeclaredGlobal same=6397 different=0
shader-ast-parity:   explained 142x replaceExpression(Product): TauMC's postfix pass cut the binary pattern to its first operand and replaced that operand everywhere
shader-ast-parity:   explained 112x replaceExpression(Sum): TauMC's postfix pass cut the binary pattern to its first operand and replaced that operand everywhere
BUILD SUCCESSFUL in 28s
```
Every S3 line is as in S3's final run. `injectVariable+removeVariable(localName)` injects `uniform float <x>;` where `x`
is a sole local and removes `x`: TauMC removes the injected uniform again (the last it scans), so `changedTheProgram=0`
is the expected outcome; S3's document order would have removed the local.

Corpus mode, the mini-corpus (`run/s4-corpus-mini-final.out`): `inputs=27 distinct=24 parseFailures=0 skipped=0
unexplained=0 seconds=0`; every verb identical where it applied: `initialize(IN)` 14 (14 changed), `initialize(OUT)` 11
(11), `injectFunction(qualified)+injectVariable` 24, `injectVariable+removeVariable(localName)` 14,
`removeConstAssignment` 24 (1 changed), `removeUnusedFunctions` 24 (1), `renameAndWrapShadow(Common)` and `(Compat)`
24 (1 each), `replaceFunctionDefinition` 3 (3), `transformGrouped` 10 (2 changed); queries `findQualifiers` 168,
`findType(afterInjectVariable)` 14, `functions` 24, `hasAssignment` 286, `isDeclaredGlobal` 286, 0 different; S3's
lines unchanged (product 6 + 1 explained, sum 1 + 2 explained).

Appendix C transform tests with `--rerun` (`run/s4-transform-tests.out`): `BUILD SUCCESSFUL in 2s`; 14 classes, 449
tests, 0 failures, 0 errors, 3 skipped (the corpus-gated `TransformCorpusReplayTest`, `GlslCorpusParseSurveyTest` and
`ShaderAstParityTest.corpusDifferential`).

Full build, the step's one `check` run (`run/s4-build.out`): `./gradlew build` gave `BUILD SUCCESSFUL in 9s`; `:test`
executed: 128 classes, 936 tests, 0 failures, 0 errors, 4 skipped (21:16:04 to 21:16:06 UTC). `verifyCeleritasPin`,
`verifyDiagnosticsJar`, `verifyDiagnosticsRemap`, `verifyDistributedJar`, `verifyModuleBoundaries`,
`verifyRunClasspath` and `verifyS8tnlibPin` ran. `build/libs/Demonica-0.5.0-SNAPSHOT.jar` holds 17
`net/coderbot/iris/pipeline/transform/transformer/ShaderAst*.class` entries (S3: 10).

Skipped: the `taumc` determinism replay (the replayer, `GlslTokens` and both engines are unchanged, so nothing it reads
changed) and any dev run (no engine uses `ShaderAst` yet).

## Measurements

- Tests: `ShaderAstParityTest` 141 to 375 (234 new, of which 4 replace S3's one order deviation test); the full suite
  702 to 936.
- Corpus mode: 26 s for the pack corpora (S3: 17 to 18 s; the grouped pass, the new applications and queries added the
  rest), under 1 s for the mini-corpus. 107 distinct stage groups in the packs (cases with two or more of vertex,
  geometry, fragment), 10 in the mini-corpus.
- Queries per corpus run: `findQualifiers` 1,547 (221 inputs x 7 storage types), `hasAssignment` and
  `isDeclaredGlobal` 6,397 each, `functions` 221, `findType` after an injection 208.
- `ShaderAst.java` 1,467 lines (886 before).
- Library facts found while implementing:
  - `ASTPrinter.print(PrintType.COMPACT, node)` prints any node on one line with spaces between tokens and a trailing
    space: `layout(location = 0) out vec4`, `{ return x; } `; `ShaderAst.text` trims it.
  - All eleven assignment operators are `BinaryExpression` subclasses; `Expression.ExpressionType` names them
    (`ASSIGNMENT`, `ADDITION_ASSIGNMENT`, ...).
  - A declaration that is the unbraced body of an `if` or loop is a `DeclarationStatement` held in a field of its
    parent (`SelectionStatement.ifTrue`, a loop's body), not in a list; `detachAndDelete` sets that field to null, and
    the printer throws on it. `CompoundStatement` is the only list parent of statements.
  - `t.parseStatement(root, ";")` gives an `EmptyStatement`.
  - glsl-transformer's lexer reads `0.0lf` as a `FLOAT64CONSTANT`; TauMC's reads `0.0d` as `0.0` followed by an error.
  - `Type` has a compact name for `bool`, the 32-bit `int`/`uint`/`float` families, `double` and its vectors and
    matrices, and `f16mat2` to `f16mat4`; `initialize` gives the last group nothing (TauMC's lexer does not know them).

## Residual diffs

No engine replay runs in this step. What the parity work found:

| Case | Stage | Classification | Action |
|---|---|---|---|
| first `findQualifiers` run, `rotation` (FRAGMENT_330), `m22` (INITIALIZE_400) | n/a | printer normalization: `mat2x2` is `mat2` (known from S3) | the test canonicalizes TauMC's `getText` column; `typeName`'s javadoc names it |
| first `findQualifiers` run, `uniform struct Light { vec3 p; } light;` | n/a | ShaderAst difference: `typeName` of an inline struct printed with spaces, TauMC's `getText()` without | fixed: `typeName` of a struct specifier is its text without whitespace |
| first pack run, `functions` 154 of 221 | all | test artefact: body text without whitespace cannot be compared token-wise (`return1.0f` against `return1.0`; `2.0E-4f` against `0.0002`) | the test compares the body tokens (`GlslTokens`) and the answers to AdaptiveShadowBounds' text searches instead; 221/221 after |
| first pack run, `injectVariable+removeVariable(localName)`, `bsl/00012-COMPOSITE-a982496e` fragment | fragment | known S3 deviation: removing an initialized first declarator of a shared declaration (`vec3 bloom = ..., temp = ...;`), TauMC wrote `vec3 temp = vec3(0.0) = vec3(0.0);` | the application now picks a sole local (its purpose is the order); 208/208 after |
| `deviationInitializeDoubles`, first run | n/a | my assumption of TauMC's printed output was wrong (it prints `dv_out = dvec2 ( 0.0 d dv_out = dvec2 ( 0.0 d ) ;`, an error-recovery tree) | the test asserts `d_out = 0.0 d` and that glsl-transformer cannot parse TauMC's output |
| `removeUnusedFunctions` "after an injected unused function" on FRAGMENT_330 | n/a | test artefact: removing the injected function restores the input, so the "TauMC changed the program" check failed | runs on UNUSED_330 instead |
| 254 pack and 3 mini-corpus `replaceExpression` product and sum applications | all | S3's explained TauMC bug | none (as S3) |
| packs: `transformGrouped` 107/107 and `removeConstAssignment` 221/221 identical, but TauMC changed none | all | coverage: the packs' raw stages pair their ins and outs, and no pack input has a `const` parameter that initializes a declaration | covered by the fixtures (grouped: 4 cases, 3 changing; const: 13 cases) and the mini-corpus (grouped 2, const 1 changed) |

## Deviations from the brief

1. Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the orchestrator's rule).
2. `findQualifiers` iterates in TauMC's order, not "in source order": TauMC returned a `HashMap`, and
   `transformGrouped` injects the missing `out` declarations (and prepends their initializers) in its iteration
   order, so source order would reorder injected declarations against TauMC's. The map is filled like TauMC's (same
   keys, inserted in TauMC's cache order, which decides collisions) and frozen into an unmodifiable `LinkedHashMap`.
   The key-order parity case "after injections" has 14 declared names and 5 injected ones: three buckets of the
   32-bucket map hold two keys each (`o5`/`iris_FrontColor`, `o13`/`qa`, `o12`/`pa`), so document order would iterate
   differently from TauMC's (checked with a `HashMap` probe in jshell), and `o3` is declared twice, so the value TauMC's
   order sees last (the injected `vec4`) is the one reported.
3. The record is `QualifiedDeclaration(name, typeText, typeName, arraySpecifierText, declaration, member)`, not
   `(name, typeText, arraySpecifierText, node)`: `typeName` is what `transformGrouped` compares between stages
   (`type_specifier_nonarray` text), and the node is split into the declaration (shared by its declarators) and the
   declarator (which holds a name-side array).
4. `replaceFunctionDefinition(String name, String newSource)` selects by name and the new source's parameter types, so
   an overload is replaced alone (TauMC matched the old text), returns the number replaced, and has a second form that
   takes the `FunctionDefinition`; replacing an anchor moves the anchor to the replacement.
5. `FunctionInfo` also carries `returnType` and `bodyText()` (AdaptiveShadowBounds reads both), and `Parameter` its
   node; `source` and `text` are static. `isDeclaredGlobal` exists because S3 found `hasVariable` means "declared".
6. Beyond leftover 1: the cache order is also used by `findType` and `removeVariable`, which closes S3's remark 2
   (documented there as not emulated). The same machinery made it a one-line change, and it removes a divergence on a
   production path (a `CELERITAS_TERRAIN` vertex shader that declares `gl_MultiTexCoord3`; S3 remark 3, the stale
   by-text cache of `replaceExpression`, still applies there). S3's `deviationDeclarationOrderAfterAnInjection`
   became the parity cases `declarationOrderAfterAnInjection`.
7. Beyond the brief's fixtures: `transformGrouped` written on `ShaderAst` (`ShaderAstParityTest.transformGrouped`)
   against TauMC's `CompatibilityTransformer.transformGrouped`, in the unit tests and as a corpus pass over stage
   groups; `changingParity` cases that also check TauMC's verb changed the program; the corpus mode's new
   applications and queries (the orchestrator asked for the verbs in `applications`).
8. `initialize` writes `0.0lf` for `double` types, where TauMC wrote broken GLSL (named, tested).

## Open questions

1. `findQualifiers`' order is TauMC's `HashMap` order so that Step 5's `transformGrouped` port injects in TauMC's order.
   After Step 11 there is no oracle; should the order then become source order (a readable, stable injection order,
   one replay-accepted "declaration order" diff per grouped program that injects two or more outputs), or stay?
2. TauMC's `renameAndWrapShadow` leaves a nested call unwrapped (`shadow2D(s, vec3(shadow2D(t, p).r))` becomes
   `vec4(texture(s, vec3(texture(t, p).r)))`, where `.r` selects from a `float` and does not compile). `ShaderAst`
   reproduces it; no recorded pack has the shape. Wrap inner calls too after Step 8?
3. `removeConstAssignment` drops the whole type qualifier (`const highp float` becomes `float`, losing `highp`), as
   TauMC does. Reproduced; Iris removes only the `const`. Keep TauMC's form?

## Notes for the next step

- Where: `glsm/src/main/java/net/coderbot/iris/pipeline/transform/transformer/ShaderAst.java`; `grep -n "public" ...`
  for the API above. All nineteen verbs exist; `mutateTree` does not (use `tree`, `root`, the queries).
- **Step 5, `transformGrouped`:** `ShaderAstParityTest.transformGrouped(Map<PatchShaderType, ShaderAst>)` is TauMC's
  method line for line on `ShaderAst` and matches it on every fixture and every recorded stage group; lift it. The
  `out` declaration is `inDec.get(in).typeText() + " " + in + ";"` with `replaceFirst("\\bin\\b", "out")`; the type
  comparison is `typeName().equals(...)`; the array check is `outDec.get(in).arraySpecifierText() != null`. Iterate
  `findQualifiers(...)`'s map as returned; copying it into another map loses TauMC's order. `Parameters` is unused by
  TauMC's method (the tests pass null).
- **Step 5, `transformEach`:** `removeUnusedFunctions()` then `removeConstAssignment()`, as TauMC; the sildur
  `replaceExpression` pattern (`fract(worldpos.y + 0.001)`) is a call pattern, which S3 tested ("a literal inside a
  call pattern").
- **Step 7, AdaptiveShadowBounds:** `functions()` gives `name`, `returnType` (`float`; with qualifiers `highp float`,
  where TauMC's `getText()` gave `highpfloat`), `parameters` (`type` without qualifiers or name-side array, `name`,
  null for an unnamed one) and `bodyText()` (TauMC's `getText()` form: no whitespace; literals print as `1.0f`, which
  none of the transformer's searches contain; `ShaderAstParityTest.boundsNeedles` checks their answers). Build the
  patched source from `ShaderAst.source(function.node())` (inserting after the first `{` works as before) and call
  `replaceFunctionDefinition(function.name(), patched)`; it returns 1.
- **Step 10:** `renameAndWrapShadow` is tested with CompatShaderTransformer's six calls on the shapes packs use.
- Cache order: nodes that code builds through `t`/`tree`/`root` itself (Iris idioms) are not recorded as additions, so
  the order-dependent verbs treat them as part of the parsed program. That matters only where TauMC parity of those
  verbs after such an edit is wanted.
- The corpus mode now takes about 26 s on the pack corpora; `grep shader-ast-parity`, diffs in
  `build/reports/shader-ast-parity/`.
- `BUILD_LOCK` (S3) still applies to every build; the new verbs that parse (`renameAndWrapShadow`, `initialize`,
  `replaceFunctionDefinition`, `removeVariable`'s empty statement) hold it.
