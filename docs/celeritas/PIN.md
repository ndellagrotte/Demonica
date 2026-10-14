# The Celeritas pin

Demonica runs on upstream Celeritas's 1.12.2 mod (`forge122`, mod id `celeritas`),
and carries that mod inside its own jar, unmodified, so players install only
Demonica. A Celeritas the player installed takes precedence over the bundled
copy. Demonica compiles against one exact build of the mod and checks for it at
runtime. This page records which build that is, how it is bundled and how to
move it.

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
| Bundled as | the release asset, byte for byte, nested in the mod jar as `zz-demonica-bundled-celeritas-9b661b70-20260926T020851.jar` |

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
under the name `zz-demonica-bundled-celeritas-<sha8>-<timestamp>.jar` (the commit's
first eight characters and the release's timestamp) and listed in the manifest's
`ContainedDeps`, like the three contained libraries. Cleanroom extracts every
`ContainedDeps` jar into `mods/1.12.2/` before any mod code runs, where it is a
normal candidate that provides mod id `celeritas`, so `required-after:celeritas`
holds. The manifest also records `Demonica-Celeritas-Jar`,
`Demonica-Celeritas-SHA256` and `Demonica-Celeritas-Release`.

The bundled copy is there so that players need to install nothing else. It does
not override a Celeritas the player installed: an installed Celeritas takes
precedence, and the bundled copy steps aside.

- **The name encodes the pin.** Cleanroom never overwrites an extracted file and
  never deletes an old one, so a fixed name would keep the first Celeritas it ever
  extracted. A new pin is a new name and extracts afresh.
- **The name starts with `zz-`.** Cleanroom sorts candidates by lower-cased file
  name and adds each coremod to the class path in that order; the first jar added
  wins class resolution. `zz-` puts the bundled copy after `01-celeritas-…`,
  `celeritas-…`, `demonica-…` and nearly every other jar, so its own place in
  that order never puts it ahead of an installed Celeritas. (`NonModDeps` still
  adds it at Demonica's own place; the next points say how that is handled.)
- **Which jars are which.** Every candidate that contains Celeritas's marker class
  and declares mod id `celeritas` (or Celeritas's coremod) is a Celeritas jar,
  whatever its name. The *bundled* copy is the jar named by
  `Demonica-Celeritas-Jar`. A *stale* copy is a jar in a folder named `1.12.2`
  whose name starts with `zz-demonica-bundled-celeritas-` but is not the bundled
  name, and that no candidate's `ContainedDeps` names: the leftover of an earlier
  Demonica pin. Every other Celeritas jar is *installed*: the player put it there.
  That includes a prefixed copy outside `mods/1.12.2` and the bundled copy of a
  second Demonica jar, but not a file with the bundled name itself: wherever it
  is, that is the bundled copy. Of several files with that name the one in
  `mods/1.12.2` is kept; the others are left out of the list and never deleted.
- **The hook.** The manifest's `Bansoukou` attribute names
  `com.demonica.loading.bundle.BundledCeleritasHook`. Cleanroom calls its
  `bansoukou(List)` after extraction and before any coremod is scanned, with the
  live list of candidate jars.
  - With no installed Celeritas, on a client, the bundled copy is used.
  - With one or more installed, the bundled copy is turned off: it is removed
    from the list and its extracted file, `mods/1.12.2/<bundled name>`, is
    deleted. Deleting is needed because `NonModDeps` adds every `ContainedDeps`
    name to the class path at Demonica's place in the order; left on disk, the
    bundled copy could win the class race against an installed jar that sorts
    after Demonica's. Cleanroom extracts it again on the next start on which it
    is missing, so removing the installed jar brings the bundled copy back with
    no other step. The installed jars stay on the list and on disk; the hook
    never touches them.
  - Stale copies are removed from the list and deleted from `mods/1.12.2`
    whenever another Celeritas remains (the bundled copy or an installed one).
    When nothing else is left they are kept, so that some Celeritas still loads.
  - If another jar carries `Bansoukou` too, the hook calls it afterwards, so
    Demonica never disables another mod's hook. The hook has no dependencies on
    Minecraft, Forge or the rest of Demonica, and never throws, since an
    exception there aborts the launch.
- **Reconciliation.** `MixinEarly.injectData` makes `CoreModManager`'s ignored and
  reparseable lists agree with the hook's decision before Forge identifies mods,
  so two `celeritas` mods never reach `Loader.identifyDuplicates` (which would
  crash with `DuplicateModsFoundException`). When the bundled copy is turned off,
  its name joins the ignored list, so the installed jar is the identified mod.
- **When the hook did not run.** Then `MixinEarly` scans the mods folders itself.
  If an installed Celeritas is there, it reconciles the same way: the bundled copy
  is ignored and the installed jar is the identified mod, with no duplicate
  crash. Stale copies are deleted. The bundled file was not deleted in time,
  though, and `NonModDeps` has added it to the class path at Demonica's place, so
  the installed jar's classes load only if its lower-cased name sorts before
  Demonica's own jar (`01-celeritas-…` and `celeritas-…` do, before
  `demonica-…`). Otherwise the log warns, on a client, that this launch loaded
  the bundled Celeritas classes although the installed jar is there, because
  another mod's `Bansoukou` hook ran first and that jar sorts after Demonica's;
  the "Using the installed …" report is then not logged, since it would say the
  opposite of what loaded. Either way,
  `QuarantineGuard` judges the jar whose classes loaded. Without an installed
  Celeritas, the bundled copy is used as on the hook's path.
- **Dedicated servers.** Celeritas is client-only, so on a server Demonica stays
  inactive, the bundled copy is dropped from the candidates (its file is kept)
  and an installed Celeritas is left alone. Stale copies in `mods/1.12.2` are
  still deleted there.
- **In the log.** The logger `DemonicaBundle` reports the outcome in one line at
  INFO: the bundled copy in use ("Using the Celeritas bundled with Demonica,
  …"), or the installed jar used in its place ("Using the installed
  mods/<jar> instead of the Celeritas bundled with Demonica; remove it from the
  mods folder to use the bundled build."); and the stale copies removed. If an
  installed jar is used and the bundled copy was not there to begin with, the
  line says so instead of advising removal, since removing the installed jar
  would leave no Celeritas ("Using the installed mods/<jar>; the Celeritas
  bundled with Demonica, <bundled name> (Celeritas-auto-build <release>), is not
  in mods/1.12.2 (it was not extracted, or -Dfml.skipContainedDeps names it).").
  If the
  bundled copy was not extracted (`-Dfml.skipContainedDeps`, a failed
  extraction) and nothing is installed, the hook leaves the candidates alone and
  `MixinEarly` reports that Celeritas is missing.
- **Installed jar, other build.** Shaders need the pinned build. An installed
  Celeritas of any other build still takes precedence; `QuarantineGuard` then
  turns shaders off, and the reason, in the log and on the shader pack screen,
  names the jar and says to remove it (the bundled copy returns on the next
  start) or to install the pinned build (below, "At runtime"). When the bundled
  copy was skipped or not extracted, the reason says that instead, and never
  advises removing what may be the only Celeritas.
- **Caveat: only one `Bansoukou` hook runs.** Cleanroom honours only the first
  candidate, in name order, whose manifest has the attribute. A mod that sorts
  before `demonica-…` and has one keeps Demonica's hook from running, unless that
  mod chains to it. Then the fallback above prevents the crash, with its
  class-race caveat. A stale copy cannot win that race on its own name, which
  sorts after Demonica's jar, where the bundled copy is already on the class
  path. Because the attribute also makes Cleanroom add Demonica's jar to the
  class path early, its merged GTNHLib classes come before other jars' copies.
- **Uninstalling Demonica** leaves `mods/1.12.2/zz-demonica-bundled-celeritas-*.jar`
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
on the shader pack screen, and only the fog patch (S15) applies. The reason
says which jar it was and what to do about it:

- an installed Celeritas of another build: it is installed in the mods folder
  and takes precedence over the Celeritas bundled with Demonica; remove it to use
  the bundled build of the pinned upstream commit, or install that build;
- no installed Celeritas, and the bundled copy was not extracted: check
  `-Dfml.skipContainedDeps`;
- the bundled copy itself, with the wrong hash: it is damaged; delete
  `mods/1.12.2/<name>` so that it is extracted again.

Details in [`LEDGER.md`](LEDGER.md#the-guard).

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
   built Demonica jar before accepting the new pin:
   - alone: the bundled copy is used, the guard accepts it, shaders work;
   - next to an installed copy of the new pin: the installed jar is used, the
     extracted `mods/1.12.2/zz-demonica-bundled-celeritas-…jar` is deleted, there
     is no `DuplicateModsFoundException`, shaders work;
   - next to an installed Celeritas of another build: the installed jar is used,
     shaders are off, and the reason names that jar;
   - after removing the installed jar: the bundled copy is extracted again on the
     next start and used;
   - with a stale `mods/1.12.2/zz-demonica-bundled-celeritas-<other pin>.jar`:
     it is deleted, with and without an installed Celeritas;
   - with another mod's `Bansoukou` hook sorting first (the fallback) and an
     installed Celeritas named before and after `demonica-…`: no crash, the
     installed jar is the identified mod, and the log says which jar's classes
     loaded;
   - on a dedicated server: Demonica stays inactive.

If the auto-build prunes the release, the build can no longer download the asset:
keep a copy and pass `-PceleritasAssetDir=<dir>` until the pin moves. The folder
must hold a file named exactly the value of `celeritas_release_asset` (now
`01-celeritas-forge-mc12.2-2.5.0-dev.jar`) whose SHA-256 is
`celeritas_asset_sha256`. The copy in Gradle's cache has the release tag
appended to its name and must be renamed; `build/tmp/celeritasPinCandidate/`
already holds it under the right name. If the auto-build changes its tag
format, relax the `celeritas_release` pattern in `build.gradle`.
