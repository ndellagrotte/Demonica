# The Celeritas pin

Demonica runs on upstream Celeritas's 1.12.2 mod (`forge122`, mod id `celeritas`),
and carries that mod inside its own jar, unmodified, so players install only
Demonica. The bundled copy overrides a Celeritas the player installed. Demonica
compiles against one exact build of the mod and checks for it at runtime. This
page records which build that is, how it is bundled and how to move it.

## Current pin

| | |
|---|---|
| Upstream | https://git.taumc.org/embeddedt/celeritas, branch `stonecutter` |
| Commit | `9b661b70ecaf84c39052e8fbc51c875ba37e739d` |
| Built by | [kappa-maintainer/Celeritas-auto-build](https://github.com/kappa-maintainer/Celeritas-auto-build), release `commit-9b661b70ecaf84c39052e8fbc51c875ba37e739d-20260926T020851` |
| Maven | `org.embeddedt:celeritas-forge-mc12.2:2.5.0-autobuild.9b661b70` on `https://maven.outlands.top/releases` |
| SHA-256, Maven jar | `5bf123a6da6190f6e061cd3fd069a2bddcbf6ebf57894381b6c888c06a94f982` |
| SHA-256, release asset `01-celeritas-forge-mc12.2-2.5.0-dev.jar` | `80f07935d86d364fd9b80297f4bc87d47f3a49209f589091b554a7332b56a909` |
| Version string inside the jar | `2.5.0-dev` (the same for every upstream dev build of 2.5.0) |
| Bundled as | the release asset, byte for byte, nested in the mod jar as `0-demonica-bundled-celeritas-9b661b70-20260926T020851.jar` |

`gradle.properties` holds the same values (`celeritas_sha`, `celeritas_version`,
`celeritas_sha256`) and three more for the bundled copy:

| Key | Value |
|---|---|
| `celeritas_release` | `commit-9b661b70ecaf84c39052e8fbc51c875ba37e739d-20260926T020851`, the auto-build release tag |
| `celeritas_release_asset` | `01-celeritas-forge-mc12.2-2.5.0-dev.jar`, the asset bundled |
| `celeritas_asset_sha256` | `80f07935d86d364fd9b80297f4bc87d47f3a49209f589091b554a7332b56a909`, that asset's hash (also listed in `celeritas_sha256`) |

**Two hashes, one build.** The auto-build publishes the release assets and the
Maven artifact from two Gradle invocations with different version strings. The
two jars have the same 708 files, byte for byte, except `META-INF/MANIFEST.MF`,
whose `Class-Path` line names the version (`common-2.5.0-dev-…` against
`common-2.5.0-autobuild.9b661b70-…`). FML ignores that line, and no jar it names
exists. Demonica bundles the release asset, so the build holds it to its exact
hash (`celeritas_asset_sha256`). The Maven hash stays accepted for the compile
classpath, and because a player may still have installed either jar.

**Provenance, checked on 2026-09-29.**
- The Maven `-sources.jar`'s Java files match `forge122/src/main/java` of a clone
  of upstream at `9b661b70` file for file. (At the previous pin, `06999aab`, the
  release's sources jar was also checked and was identical to the Maven one.)
- The auto-build's workflow (`.github/workflows/build.yaml` in that repository)
  clones upstream at `HEAD`, checks the commit, and runs
  `./gradlew -Pceleritas_target_versions=1.12.2 :forge122:packageJar`: an
  unmodified upstream build.
- The builds are not tagged upstream, and the auto-build's README says so:
  *"Not based on tag so highly risky"*. The pin makes that acceptable: Demonica
  never floats to a newer build.

## What the jar looks like

- SRG-named, with **no refmap**: upstream's remapper rewrites the mixin
  annotations to SRG names directly.
- It shades upstream `common`, downgraded to Java 8 by jvmdowngrader (records
  become stubs), and relocates JOML to `org.embeddedt.embeddium.impl.shadow.joml`.
- The coremod `org.taumc.celeritas.core.CeleritasLoadingPlugin` is a MixinBooter
  `IEarlyMixinLoader` that registers only `mixins.celeritas.json`. That config lists
  no mixins: its plugin, `CeleritasVintageMixinPlugin`, returns all 27 classes of
  `org.taumc.celeritas.mixin`, and none can be switched off individually.
- `META-INF/celeritas_at.cfg` (SRG names) is its access transformer.
  `META-INF/celeritas.accesswidener` is a Fabric leftover that Forge ignores.
- No `META-INF/services`: since 2.5.0 upstream loads no service by `ServiceLoader`.
  The fog service is the singleton `GLStateManagerFogService.INSTANCE`, which
  `VintageRenderSectionManager.getFogService()` returns.

## Bundled copy

The release asset is nested unmodified at the root of `Demonica-<version>.jar`
under the name `0-demonica-bundled-celeritas-<sha8>-<timestamp>.jar` (the commit's
first eight characters and the release's timestamp) and listed in the manifest's
`ContainedDeps`, like the three contained libraries. Cleanroom extracts every
`ContainedDeps` jar into `mods/1.12.2/` before any mod code runs, where it is a
normal candidate that provides mod id `celeritas`, so `required-after:celeritas`
holds. The manifest also records `Demonica-Celeritas-Jar`,
`Demonica-Celeritas-SHA256` and `Demonica-Celeritas-Release`.

- **The name encodes the pin.** Cleanroom never overwrites an extracted file and
  never deletes an old one, so a fixed name would keep the first Celeritas it ever
  extracted. A new pin is a new name and extracts afresh.
- **The name starts with `0-`.** Cleanroom sorts candidates by lower-cased file
  name, and `-` (0x2D) sorts before `1`, so the bundled copy comes before
  `01-celeritas-…`, `celeritas-…` and `demonica-…`. Wherever an installed
  Celeritas reaches the class path next to it, the bundled one is first and wins
  class resolution. Among bundled copies the order is that of their hex `<sha8>`,
  which says nothing about which is newer, so a stale copy from an earlier pin
  can sort first; removing stale copies, not the name, keeps them out.
- **The hook.** The manifest's `Bansoukou` attribute names
  `com.demonica.loading.bundle.BundledCeleritasHook`. Cleanroom calls its
  `bansoukou(List)` after extraction and before any coremod is scanned, with the
  live list of candidate jars. The hook removes every other Celeritas jar from
  that list: an installed copy (whatever its file name or build, found by its
  contents rather than its name) and stale bundled copies of earlier Demonica
  pins. It deletes the stale files from `mods/1.12.2` and never touches the
  installed one, which stays on disk. If another jar carries `Bansoukou` too, the
  hook calls it afterwards, so Demonica never disables another mod's hook. The
  hook has no dependencies on Minecraft, Forge or the rest of Demonica, and
  never throws, since an exception there aborts the launch.
- **Reconciliation.** `MixinEarly.injectData` makes `CoreModManager`'s ignored and
  reparseable lists agree with the hook's decision before Forge identifies mods,
  so two `celeritas` mods never reach `Loader.identifyDuplicates` (which would
  crash with `DuplicateModsFoundException`). When the hook did not run, it scans
  the mods folders itself and logs a warning naming the jars it ignores.
- **Dedicated servers.** Celeritas is client-only, so on a server Demonica stays
  inactive, the bundled copy is dropped from the candidates and an installed
  Celeritas is left alone. Stale bundled copies of earlier pins in
  `mods/1.12.2` are still deleted there.
- **In the log.** The logger `DemonicaBundle` reports the outcome in one line: the
  bundled copy in use, at INFO; at WARN, the installed Celeritas jar it ignores
  ("Ignored the installed …: Demonica brings its own Celeritas, and that jar can
  be removed."); the number of stale copies deleted. If the bundled copy
  was not extracted (`-Dfml.skipContainedDeps`, a failed extraction), the hook
  leaves the candidates alone and `QuarantineGuard` judges whatever Celeritas
  loaded; shaders are off, with the reason, unless it is the pin. A bundled copy
  that fails the guard's hash is reported as damaged, with the file to delete.
- **Caveat: only one `Bansoukou` hook runs.** Cleanroom honours only the first
  candidate, in name order, whose manifest has the attribute. A mod that sorts
  before `demonica-…` and has one keeps Demonica's hook from running, unless that
  mod chains to it. Then reconciliation prevents the crash, and the `0-` name still
  wins the class race against an installed Celeritas. It does not win against a
  stale bundled copy of an earlier pin that sorts first: that launch loads
  Celeritas from the stale copy, which the guard then rejects, so reconciliation
  deletes stale copies for the next launch and logs a warning to restart.
  Because the attribute also makes Cleanroom add Demonica's jar to the class
  path early, its merged GTNHLib classes come before other jars' copies.
- **Uninstalling Demonica** leaves `mods/1.12.2/0-demonica-bundled-celeritas-*.jar`
  behind, which then loads as a standalone Celeritas. Delete it too.

## In Demonica's build

- `build.gradle` resolves the jar from an `exclusiveContent` repository that
  serves only this module, and the release asset from another that serves only the
  auto-build's GitHub releases (`-PceleritasAssetDir=<dir>` serves it from a local
  folder instead). The asset goes into its own `bundledCeleritas` configuration,
  which extends nothing, so the SRG jar stays off the compile and run class paths.
- A configuration-time check rejects an inconsistent set of keys: a release tag
  that does not match `commit-<40 hex>-<timestamp>`, a tag whose commit is not
  `celeritas_sha`, a `celeritas_version` that does not end in `.<sha8>`, an asset
  that is not a `.jar`, an asset hash missing from `celeritas_sha256`. When the
  only tasks requested are `celeritasPinCandidate` or Gradle's help tasks, it
  warns instead, so the helper still runs halfway through a move.
- `modCompileOnly` and `modRuntimeOnly` are remapped from SRG to MCP by Unimined,
  with base-mixin and MixinExtras remapping (`mixinRemap`) and
  `catchAWNamespaceAssertion()` for the leftover access widener. The remapped jar:
  - gains a fallback refmap, `mixins.celeritas-refmap.json` (20 mixin classes,
    SRG → MCP), and the config points at it;
  - has `celeritas_at.cfg` in MCP names;
  - keeps every other entry.
- `verifyCeleritasPin` (part of `check`) hashes the resolved Maven jar and fails
  unless it is one of `celeritas_sha256`. `verifyBundledCeleritasPin` (part of
  `check`, and a dependency of `jar`) hashes the release asset and fails unless it
  is exactly `celeritas_asset_sha256`, so a replaced asset is never packaged.
- `generateCeleritasPin` writes `META-INF/demonica/celeritas-pin`: the upstream
  commit, the version and the accepted SHA-256s, plus `dev_sha256`, the hash of
  the remapped jar the workspace compiles, tests and runs against (the task fails
  if the compile and runtime classpaths hold different ones). The guard reads it
  at runtime (below).
- The `jar` task drops `dev_sha256`, and `verifyDistributedJar` (part of `check`)
  fails unless the distributed jar's pin names exactly the upstream commit, the
  version and the SHA-256s of `gradle.properties`, with no other key. It also
  requires the nested Celeritas jar to hash to `celeritas_asset_sha256`, the root
  `*.jar` entries to be exactly the `ContainedDeps`, and the four manifest
  attributes above, and the jar's `THIRD_PARTY_NOTICES.md` to name
  `celeritas_sha` in full. It still forbids any top-level Celeritas class or asset,
  a renamed copy or JOML: the jar carries Celeritas only as the nested asset.
- `QuarantineGuardTest` checks that the pin on the test classpath matches
  `gradle.properties`, and that its `dev_sha256` is the jar the tests read.
  `QuarantinePriorityTest` reads upstream's mixin priorities and `@Overwrite`s from
  the pinned jar, and fails if forge122 overrides what S2 patches or S3 adds.
- `GlsmRedirectLinkageTest` runs GLSM's redirector over every class in the jar and
  checks that each rewritten call exists in `GLStateManager`.

## At runtime

`QuarantineGuard` hashes the Celeritas jar the game loads (from the class path,
or the mods folder) before Mixin applies the quarantine. On a pinned SHA-256 every
patch applies. On any other build shaders are off, with the reason in the log and
on the shader pack screen, and only the fog patch (S15) applies. When the loaded
jar is not the bundled one, the reason says it was loaded instead of the Celeritas
bundled with Demonica and that it can be removed from the mods folder. Details in
[`LEDGER.md`](LEDGER.md#the-guard).

The development workspace runs Unimined's remap of the pin, which no pin matches;
the guard accepts it by `dev_sha256`, only in a deobfuscated environment.
`-PdevProps=demonica.celeritas.pinsOnly=true` makes a dev client reject it.

## Moving the pin

1. Pick an upstream commit and its auto-build release. Run
   `./gradlew celeritasPinCandidate -PceleritasCandidate=<tag|latest>`: it asks
   GitHub for that release (or the latest), downloads the `01-celeritas-forge-mc12.2-*.jar`
   asset and the matching Maven jar into `build/tmp/celeritasPinCandidate/`, hashes
   both and prints the six property lines and the table rows below. It edits
   nothing. Hashing the two jars by hand works as well.
2. Edit the six keys in `gradle.properties` together: `celeritas_sha`,
   `celeritas_version`, `celeritas_release`, `celeritas_release_asset`,
   `celeritas_asset_sha256` and `celeritas_sha256` (which lists both hashes). Update
   the tables above and the Celeritas row of `THIRD_PARTY_NOTICES.md`, which
   names the full commit (`verifyDistributedJar` fails if it does not). The
   configuration-time check fails the build with "Celeritas pin is inconsistent"
   if the keys disagree. `celeritasPinCandidate` (and Gradle's `help`, `tasks` and
   `properties`) still runs while they do, with the same message as a warning,
   so the helper can be re-run halfway through the move.
3. Run `./gradlew verifyCeleritasPin verifyBundledCeleritasPin build`. A Celeritas
   class or member that Demonica's code uses
   and that was renamed or removed fails to compile (an injector's target string
   does not; step 4 catches those); `QuarantinePriorityTest` reports upstream priorities or overwrites
   that now clash. Re-derive each affected patch and update its ledger entry
   (`LEDGER.md`). Check by hand the calls the S1, S2 and S13 mixins' Javadoc names.
4. Run the dev client and load a world. The injection audit is fatal in dev: a
   quarantine injector that found no target stops the client with a crash report
   that names it. A clean run with all the "n of n injectors found their targets"
   lines means every patch found its target. Repeat the current checkpoint's
   matrix.
5. Run the production-shaped smoke test (`LEDGER.md`, after Checkpoint 10) with the
   built Demonica jar, alone and next to an installed Celeritas, before accepting
   the new pin.

If the auto-build prunes the release, the build can no longer download the asset:
keep a copy and pass `-PceleritasAssetDir=<dir>` until the pin moves. The folder
must hold a file named exactly the value of `celeritas_release_asset` (now
`01-celeritas-forge-mc12.2-2.5.0-dev.jar`) whose SHA-256 is
`celeritas_asset_sha256`. The copy in Gradle's cache has the release tag
appended to its name and must be renamed; `build/tmp/celeritasPinCandidate/`
already holds it under the right name. If the auto-build changes its tag
format, relax the `celeritas_release` pattern in `build.gradle`.
