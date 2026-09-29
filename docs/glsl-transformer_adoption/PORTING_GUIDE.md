# Porting Iris transformer changes

How to take a change from upstream Iris's GLSL transform code into Demonica, and how to check it. Written in Step 12
of [the adoption plan](ADOPTION_PLAN.md) (2026-09-29), after the demonstrated port of Iris 26.1's
`CompatibilityTransformer` code; the [Step 12 report](reports/S12-payoff.md) has its numbers. Iris 26.1 is the local
copy at `/home/nick/IdeaProjects/schmaloogium-clean/reference-src/Iris-26.1/common/src/main/java/net/irisshaders/iris/pipeline/transform/`
(mod 1.11.2, Minecraft 26.1.2); both code bases use glsl-transformer 3.0.0-pre3.

## Where things are

Demonica's transform code is `shader/src/main/java/net/coderbot/iris/pipeline/transform/`, laid out as Iris's
`pipeline/transform/`. `ShaderAst` is the exception: it lives in `glsm/` under the same package, because GLSM's
`CompatShaderTransformer` uses it too.

| Iris 26.1 | Demonica | Notes |
|---|---|---|
| `transform/TransformPatcher` | `transform/TransformPatcher` (facade, cache) and `transform/ShaderTransformer` (the per-stage sequence) | Iris does both in one class; Demonica's sequence adds the version negotiation and the regex passes (below) |
| `transformer/X.java` | `transformer/X.java` | Same simple names where both have the class: `CommonTransformer`, `CompatibilityTransformer`, `CompositeDepthTransformer`, `DHGenericTransformer`, `DHTerrainTransformer`, `EntityPatcher`, `TextureTransformer` |
| `transformer/VanillaTransformer` | `transformer/AttributeTransformer` | Patch `VANILLA` is Demonica's `ATTRIBUTES` (gbuffers programs) |
| `transformer/SodiumTransformer` | `transformer/CeleritasTransformer` | Patch `SODIUM` is Demonica's `CELERITAS_TERRAIN` |
| `parameter/VanillaParameters`, `parameter/SodiumParameters` | `parameter/AttributeParameters`, `parameter/CeleritasTerrainParameters` | `Parameters` has no `name` in Demonica (Iris: the program name) |
| `Patch.{VANILLA, SODIUM, COMPOSITE, COMPUTE, DH_TERRAIN, DH_GENERIC}` | `Patch.{ATTRIBUTES, CELERITAS_TERRAIN, COMPOSITE, COMPUTE, DH_TERRAIN, DH_GENERIC}` | |
| `PatchShaderType`, `ShaderType` | the same, including both tessellation stages | |
| `transformer/CompositeTransformer`, the `*CoreTransformer`s, `LayoutTransformer`, `CompatibilityTransformer.transformFragmentCore`, `ShaderPrinter`, `parameter/GeometryInfoParameters` | none | Iris's core-profile pack path and layout handling; not ported |

What Demonica has that Iris does not:

- `transformer/AdaptiveShadowBoundsTransformer` (the adaptive shadow bounds rewrite, called from `CommonTransformer`),
  `transformer/CoreTransformHelper` (Demonica's core-profile helpers), `transformer/ComputeTransformer`.
- `transform/VersionNegotiation`: version hoisting, stage minimums (330, tessellation 400) and negotiation against
  `RenderSystem.getMaxGlslVersion()`.
- The regex passes around the parse, in `ShaderTransformer`: `GlslTransformUtils.replaceTexture`,
  `renameReservedWords`, `CompatShaderTransformer.fixupQualifiers`, the three pack patches of
  `transform/CompatibilityPatches` (Iris keeps its pack patches in `CompatibilityTransformer`), and
  `restoreReservedWords` after the print. Step 12 found `replaceTexture` and `renameReservedWords` load-bearing (the
  report's "reserved-word question"): keep them.
- The Celeritas header (`ShaderTransformer.computeCeleritasHeader()`), text printed between the `#extension` lines and
  the body of `CELERITAS_TERRAIN` vertex shaders, never parsed.
- `transform/corpus/` (the recorder) and the header handling of `ShaderAst` (`#version` and `#extension` lines are
  taken out and printed by Demonica, not by glsl-transformer).

## Two ways to write a transformation

**`ShaderAst` verbs.** The nineteen methods under TauMC's names (`injectVariable`, `rename`, `replaceExpression`,
`prependMain`, `findQualifiers`, ...; Appendix B of the plan). They take the build lock themselves and keep TauMC's
order rules: injection anchors, the "cache order" in which later verbs see earlier additions, `HashMap` iteration where
TauMC had it. Use them when changing a Demonica transformer that is already written with them, and whenever the
output must stay as it was (every verb is pinned by `ShaderAstSnapshotTest` against TauMC's frozen answers).

**glsl-transformer idioms**, as Iris writes them: `ast.t` (the `ASTParser`), `ast.tree` (the `TranslationUnit`),
`ast.root` (the `Root`, with a prefix identifier index), `Template`, `Matcher`, `root.identifierIndex`,
`root.nodeIndex`, `tree.injectNode`, `tree.prependMainFunctionBody`. Use them when taking Iris code: the code then
stays Iris's, and the next upstream change to it is a diff against the same text. Rules:

1. **Hold the build lock.** glsl-transformer 3.0.0-pre3 builds nodes on a static stack, so node construction is not
   thread-safe (`ShaderAst.BUILD_LOCK`). Run the code inside `ast.build(() -> ...)`, which takes the lock and sets the
   shared parser's lexer to the program's version. Static `Template`s and `Matcher`s are built at class
   initialization: put them in a nested class that only locked code touches (`CompatibilityTransformer.Upstream`),
   so the class initializes inside the lock.
2. **Iterate in document order when the output depends on the order.** `root.nodeIndex.get(X.class)` is a `HashSet`
   of nodes in identity-hash order (ShaderAst uses `RootSupplier.PREFIX_UNORDERED_ED_EXACT`, as Iris does): code that
   injects while iterating it produces a different order from run to run. Iterate `tree.getChildren()` instead (the
   port's `inDocumentOrder`). The replay's concurrent pass (`-PglslReplayThreads=8`, `differing=0`) and a test that
   transforms the same input many times catch this.
3. **Nodes built directly are not verb additions.** `ShaderAst` records what its own verbs add; a verb that runs after
   idiom code does not see idiom-built nodes in TauMC's order. Run idiom code after the verbs of the same stage, as
   `transformEach` does, or check the verb's output.
4. **Keep the Iris text.** Copy the method with its comments and tabs; mark every Demonica change with a
   `// Demonica:` comment that says why. The demonstrated port has three adaptations (the lock, the missing program
   name, a partial `transformEach`) and two fixes to Iris (an unsigned zero, the document order).
5. **Check what the library gives you.** glsl-transformer's `LiteralExpression.getDefaultValue` returns an `int` 0 for
   unsigned types, so Iris writes `id = 0;` for a `uint`, which does not compile before GLSL 4.00; Demonica's copy
   writes `0u`. Compile the output (below) instead of trusting the upstream code.
6. Demonica's `Iris.logger` has no `warn(String, Object...)`; Iris code that logs through a log4j `LogManager` logger
   can keep it (the `shader` module has log4j).

## Verifying a port

1. **Write the cases first.** For each behaviour the port changes, a hand-written mini-corpus case under
   `src/test/resources/transform-corpus/<case>/`: `case.properties` copied from a similar case (`composite-330` for a
   COMPOSITE vertex and fragment pair; set `stages`, `files.in`, `engine=douira` and a comment line saying what the
   case is for), and `in.<stage>.glsl` files. Record the output of the engine *before* the port into a scratch copy
   and copy the `out.douira.*` files in:

   ```
   ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/run/<scratch> -PglslReplayRecord=true
   ```

   TauMC is gone, so a new case has no `out.taumc.*`; the replay compares it with `out.douira.*` instead (Step 12).
   Never write or edit an `out.taumc.*` file.
2. **Port**, then replay the mini-corpus (every `:test` does; `--rerun`, counts from `build/test-results/test/*.xml`).
   Each changed stage writes `build/reports/transform-replay/<case>.<stage>.diff`. Add one
   `src/test/resources/transform-replay/accepted.txt` entry per changed case and stage whose reason names the upstream
   change; an entry that tolerates no difference fails the run as `STALE`.
3. **Replay the pack corpora**, which must stay identical unless the change is meant to touch them (then list every
   changed case in the report):

   ```
   ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/run/transform-corpus -PglslReplayThreads=8
   ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/run/transform-corpus-dh -PglslReplayThreads=8
   ./gradlew :test --tests '*TransformCorpusReplayTest' --rerun -PglslCorpusDir=$PWD/run/transform-corpus-s7-taumc -PglslReplayThreads=8
   ```

   and read `grep -E 'replay: (engine|accepted|concurrent)|BUILD'`. The corpora are local (`run/` is gitignored) and
   their TauMC outputs cannot be re-recorded; `capture.sh` refuses to write into them.
4. **Compile the outputs** before and after with `glslangValidator` (installed at `/usr/bin/glslangValidator`): each
   stage alone (`.vert`, `.tesc`, `.tese`, `.geom`, `.frag`), then linked (`glslangValidator -l`). Record the outputs
   with record mode into a scratch copy of the cases. glslang does not report a stage input that the previous stage
   never writes; read those by eye.
5. **Tests.** Unit tests for the new behaviour next to the class (`transformer/CompatibilityTransformerTest`). Where a
   frozen-TauMC test (`ShaderAstSnapshotTest`, `ShaderTransformerTest`) now fails, turn its assertion into a named
   deviation that says what differs and why; never edit a snapshot under `src/test/resources/shader-ast-parity/` or
   `transform-engine-taumc/`, and never rename a test method (its name is the snapshot key).
6. **In game.** A behaviour change needs the screenshot sweep (`scripts/glsl-corpus/sweep.txt`) compared with the last
   one before release; read `docs/glsl-transformer_adoption/reports/S12-payoff.md` for the commands.

## Minecraft code

Iris 26.1's transform package imports nothing from `net.minecraft.*` or `com.mojang.*` (grep: 0 imports under
`pipeline/transform/`), and neither do Demonica's transformer classes: a transformer port is GLSL work only, and the
Cleanroom backporting concerns (registries, NBT, renderers) do not arise. They arise when a port touches Iris's
pipeline code outside `transform/`, which imports `com.mojang.blaze3d.opengl.GlStateManager` (14 files),
`net.minecraft.client.Minecraft` (9), `com.mojang.blaze3d.vertex.VertexFormat` (8), `net.minecraft.resources.Identifier`
(5), `com.mojang.blaze3d.systems.RenderSystem` (5) and more. The front door for those is the `cleanroom` MCP tool
`find_equivalent` with `from: "modern-minecraft"`. Tried on Step 12 on classes from that list:

| Query | Result |
|---|---|
| `net.minecraft.resources.Identifier.fromNamespaceAndPath` (Iris's `CustomTextureManager.java:141`) | 0 equivalents |
| `Identifier` | 0 equivalents |
| `ResourceLocation` | 0 equivalents |
| `RenderSystem` | 1, "pattern-change": the modern `PoseStack`/`RenderSystem` pipeline "becomes direct GlStateManager/Tessellator calls" in 1.12.2 (topic block-entity-renderer, validated against Cleanroom 0.6.3-alpha) |

So the tool answers for patterns it has in its corpus and says so when it has nothing; follow a miss with its
`search_cleanroom_api`, `resolve_symbol` or `search_mappings` tools. Two Demonica-specific points it cannot know:
Iris's `RenderSystem` and `GlStateManager` calls usually map to Demonica's own GLSM classes
(`com.gtnewhorizons.angelica.glsm.RenderSystem`, `GLStateManager`), not to Minecraft's 1.12.2 `GlStateManager`; and
`Identifier` is 1.12.2's `ResourceLocation`, a rename the tool did not find.
