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
| Commit | `d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4` |
| Built by | [kappa-maintainer/Celeritas-auto-build](https://github.com/kappa-maintainer/Celeritas-auto-build), release `commit-d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4-20261014T021746` |
| Maven | `org.embeddedt:celeritas-forge-mc12.2:2.5.0-autobuild.d94ec1e5` on `https://maven.outlands.top/releases` |
| SHA-256, Maven jar | `7c159117fe23e4545bf499a708276ccd624905bba97063bd5c01b90d7b00afdb` |
| SHA-256, release asset `01-celeritas-forge-mc12.2-2.5.0-dev.jar` | `7d380743016c3c12a473a48ef15fba87812f66acf6ac19cd6251ab3055ba4471` |
| Version string inside the jar | `2.5.0-dev` (the same for every upstream dev build of 2.5.0) |
| Bundled as | the release asset, byte for byte, nested in the mod jar as `zz-demonica-bundled-celeritas-d94ec1e5-20261014T021746.jar` |

`gradle.properties` holds the same values (`celeritas_sha`, `celeritas_version`,
`celeritas_sha256`) and three more for the bundled copy:

| Key | Value |
|---|---|
| `celeritas_release` | `commit-d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4-20261014T021746`, the auto-build release tag |
| `celeritas_release_asset` | `01-celeritas-forge-mc12.2-2.5.0-dev.jar`, the asset bundled |
| `celeritas_asset_sha256` | `7d380743016c3c12a473a48ef15fba87812f66acf6ac19cd6251ab3055ba4471`, that asset's hash (also listed in `celeritas_sha256`) |

**Two hashes, one build.** The auto-build publishes the release assets and the
Maven artifact from two Gradle invocations with different version strings. The
two jars have the same 710 files, byte for byte, except `META-INF/MANIFEST.MF`,
whose `Class-Path` line names the version (`common-2.5.0-dev-…` against
`common-2.5.0-autobuild.d94ec1e5-…`). FML ignores that line, and no jar it names
exists. Demonica bundles the release asset, so the build holds it to its exact
hash (`celeritas_asset_sha256`). The Maven hash stays accepted for the compile
classpath, and because a player may still have installed either jar.

**Provenance, checked on 2026-10-14 (and on 2026-09-29 for the previous pin).**
- At `d94ec1e5`: the release asset and the Maven jar were compared entry for entry
  (710 each, the manifest alone differing). Against the `9b661b70` asset, the ten
  class files that differ or are new (`ChunkBufferBuilder`,
  `BlockRendererDispatcherMixin`, `VintageBlockRenderer`, `ChunkBuilderMeshingTask`,
  `VintageChunkBuildContext`, `TextureAtlasSpriteMixin`, `SpriteTransparencyLevel`,
  `SectionLattice`, the two bitraster rasterizers) are exactly the classes the six
  upstream commits between the pins touch (`git.taumc.org` compare
  `9b661b70...d94ec1e5`): no class changed that no commit explains.
- At `9b661b70`: the Maven `-sources.jar`'s Java files matched `forge122/src/main/java`
  of a clone of upstream at that commit file for file. (At `06999aab`, the release's
  sources jar was also checked and was identical to the Maven one.)
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
  no mixins: its plugin, `CeleritasVintageMixinPlugin`, returns all 28 classes of
  `org.taumc.celeritas.mixin` (since `26ab2175`, `core.terrain.BlockRendererDispatcherMixin`
  among them: it wraps the `BlockModelRenderer.renderModel` call in vanilla's
  `BlockRendererDispatcher.renderBlock`, which is how the fast block renderer is
  reached), and none can be switched off individually.
- `META-INF/celeritas_at.cfg` (SRG names) is its access transformer.
  `META-INF/celeritas.accesswidener` is a Fabric leftover that Forge ignores.
- No `META-INF/services`: since 2.5.0 upstream loads no service by `ServiceLoader`.
  The fog service is the singleton `GLStateManagerFogService.INSTANCE`, which
  `VintageRenderSectionManager.getFogService()` returns.

## Bundled copy

The release asset is nested unmodified at the root of `Demonica-<version>.jar`
under the name `zz-demonica-bundled-celeritas-<sha8>-<timestamp>.jar` (the commit's
first eight characters and the release's timestamp) and listed in the manifest's
`ContainedDeps`, like the three contained libraries. Next to it sits a sidecar,
`<that name>.meta`: the asset's own main manifest attributes plus
`Maven-Artifact: com.demonica:zz-demonica-bundled-celeritas:<sha8>-<timestamp>`
(generated from the asset by `generateBundledCeleritasMeta`). A `ContainedDeps`
jar without a coordinate is extracted into `mods/1.12.2/`, where it is a normal
candidate on every launch; one with a coordinate Cleanroom's `LibraryManager`
extracts to its in-memory repository instead,
`mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/<sha8>-<timestamp>/<name>`
(the artifact's file name is `<artifact>-<version>.jar`, so the file keeps the
bundled name), copies the sidecar next to it as `<name>.meta`, and adds the
artifact to its `MEMORY` mod list. That list is rebuilt from scratch on every
launch from the jars in `mods` and `mods/1.12.2`, never saved, and its artifacts
are appended to the candidates after those jars. Nothing ever lists
`mods/memory_repo` itself, so the extracted copy is a candidate only on a launch
that read Demonica's jar: disable or remove `Demonica-<version>.jar` and the copy
is inert. On a launch with Demonica it is a normal candidate that provides mod id
`celeritas`, so `required-after:celeritas` holds. The manifest also records
`Demonica-Celeritas-Jar`, `Demonica-Celeritas-Artifact`,
`Demonica-Celeritas-SHA256` and `Demonica-Celeritas-Release`.

The bundled copy is there so that players need to install nothing else. It does
not override a Celeritas the player installed: an installed Celeritas takes
precedence, and the bundled copy steps aside.

- **The sidecar stands in for the manifest.** Cleanroom's mod discoverer reads
  `<file>.meta` in place of a jar's own manifest when it exists, so the sidecar
  carries the asset's attributes (`FMLCorePlugin`, `FMLCorePluginContainsFMLMod`,
  `ForceLoadAsMod`, `FMLAT`, …) and nothing else but the coordinate;
  `verifyDistributedJar` checks that. The asset itself stays byte for byte the
  release.
- **The name encodes the pin.** Cleanroom never overwrites an extracted file and
  never deletes an old one, so a fixed name would keep the first Celeritas it ever
  extracted. A new pin is a new version folder and extracts afresh; the hook
  deletes the older pins' folders.
- **The name starts with `zz-`.** Demonica 0.8.2 and earlier extracted the copy
  to `mods/1.12.2`, where it is a sorted candidate. Cleanroom sorts candidates by
  lower-cased file name and adds each coremod to the class path in that order;
  the first jar added wins class resolution. `zz-` puts such a copy after
  `01-celeritas-…`, `celeritas-…`, `demonica-…` and nearly every other jar, so
  its own place in that order never puts it ahead of an installed Celeritas. The
  copy in the repository is appended after every jar of the mods folders, so it
  never wins against an installed jar on its own.
- **`NonModDeps`.** Demonica's manifest sets it for the three libraries: Cleanroom
  then adds `mods/1.12.2/<each ContainedDeps name>` to the class path at
  Demonica's own place in the order. For the bundled Celeritas that URL is
  dangling (the file is in the repository) and harmless. `MixinEarly` adds the
  repository copy to the class path itself, at the same moment, when the hook
  decided to use it, so class resolution is as it was when `NonModDeps` resolved
  the file. A copy left in `mods/1.12.2` by an earlier Demonica *does* resolve
  there, which is why the hook deletes such copies.
- **Which jars are which.** Every candidate that contains Celeritas's marker class
  and declares mod id `celeritas` (or Celeritas's coremod) is a Celeritas jar,
  whatever its name. The *bundled* copy is the jar named by
  `Demonica-Celeritas-Jar`: of several files with that name, the one in the
  repository, else the one in `mods/1.12.2`, else any. The others are *stale* if
  they are in `mods/1.12.2` (the leftover of the old extraction) and *duplicates*
  otherwise (the player's files, left out of the list and never deleted). A
  *stale* copy is also a jar in a folder named `1.12.2` whose name starts with
  `zz-demonica-bundled-celeritas-` but is not the bundled name, and that no
  candidate's `ContainedDeps` names: the leftover of an earlier Demonica pin.
  Every other Celeritas jar is *installed*: the player put it there. That
  includes a prefixed copy in `mods` and the bundled copy of a second Demonica
  jar.
- **The hook.** The manifest's `Bansoukou` attribute names
  `com.demonica.loading.bundle.BundledCeleritasHook`. Cleanroom calls its
  `bansoukou(List)` after extraction and before any coremod is scanned, with the
  live list of candidate jars.
  - With no installed Celeritas, on a client, the bundled copy is used.
  - With one or more installed, the bundled copy is turned off: it is removed
    from the list. Its file stays where it is, inert, and is used again, with no
    other step and no second extraction, once the installed jar is removed. The
    installed jars stay on the list and on disk; the hook never touches them.
  - Stale copies are removed from the list and deleted from `mods/1.12.2`
    whenever another Celeritas remains (the bundled copy or an installed one).
    When nothing else is left they are kept, so that some Celeritas still loads.
    Older pins' version folders in the repository (jar and sidecar) are deleted
    whenever the bundled copy is in its own; nothing lists them, so nothing holds
    them open.
  - If another jar carries `Bansoukou` too, the hook calls it afterwards, so
    Demonica never disables another mod's hook. The hook has no dependencies on
    Minecraft, Forge or the rest of Demonica, and never throws, since an
    exception there aborts the launch.
- **Reconciliation.** `MixinEarly.injectData` makes `CoreModManager`'s ignored and
  reparseable lists agree with the hook's decision before Forge identifies mods,
  so two `celeritas` mods never reach `Loader.identifyDuplicates` (which would
  crash with `DuplicateModsFoundException`). When the bundled copy is turned off,
  its name joins the ignored list, so the installed jar is the identified mod.
- **When the hook did not run.** Then `MixinEarly` scans the mods folders and the
  bundled copy's version folder itself. If an installed Celeritas is there, it
  reconciles the same way: the bundled copy is ignored and the installed jar is
  the identified mod, with no duplicate crash. Stale copies are deleted, except
  a leftover with the bundled name in `mods/1.12.2`, which `NonModDeps` has put
  on the class path and the Celeritas classes may be loading from: that one is
  deleted when the game exits (Linux deletes an open file at once, and the
  loader then fails; on Windows the open file may stay, and the warning below
  names it). The repository copy was listed after every installed jar, so the installed
  jar's classes load, whatever its name. Only a leftover of the old extraction
  in `mods/1.12.2` can still win: `NonModDeps` added it at Demonica's place, so
  the installed jar's classes load only if its lower-cased name sorts before
  Demonica's own jar (`01-celeritas-…` and `celeritas-…` do, before
  `demonica-…`). Otherwise the log warns, on a client, that this launch loaded
  the bundled Celeritas classes from that leftover although the installed jar is
  there, because another mod's `Bansoukou` hook ran first and that jar sorts
  after Demonica's; the "Using the installed …" report is then not logged, since
  it would say the opposite of what loaded. Either way, `QuarantineGuard` judges
  the jar whose classes loaded, and the leftover is gone on the next launch.
  Without an installed Celeritas, the bundled copy is used as on the hook's
  path; it reaches the class path when the fallback decides, or at the latest
  when Cleanroom reaches its coremod.
- **Dedicated servers.** Celeritas is client-only, so on a server Demonica stays
  inactive, the bundled copy is dropped from the candidates (its file is kept)
  and an installed Celeritas is left alone. Stale copies in `mods/1.12.2` and
  older pins' folders are still deleted there.
- **In the log.** The logger `DemonicaBundle` reports the outcome in one line at
  INFO: the bundled copy in use ("Using the Celeritas bundled with Demonica,
  …"), or the installed jar used in its place ("Using the installed
  mods/<jar> instead of the Celeritas bundled with Demonica; remove it from the
  mods folder to use the bundled build."); and the stale copies and older pins
  removed. If an installed jar is used and the bundled copy was not there to
  begin with, the line says so instead of advising removal, since removing the
  installed jar would leave no Celeritas ("Using the installed mods/<jar>; the
  Celeritas bundled with Demonica, <bundled name> (Celeritas-auto-build
  <release>), is not in mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas
  (it was not extracted, or -Dfml.skipContainedDeps names it)."). If the
  bundled copy was not extracted (`-Dfml.skipContainedDeps`, a failed
  extraction) and nothing is installed, the hook leaves the candidates alone and
  `MixinEarly` reports that Celeritas is missing.
- **Installed jar, other build.** Shaders need the pinned build. An installed
  Celeritas of any other build still takes precedence; `QuarantineGuard` then
  turns shaders off, and the reason, in the log and on the shader pack screen,
  names the jar and says to remove it (the bundled copy is used on the next
  start) or to install the pinned build (below, "At runtime"). When the bundled
  copy was skipped or not extracted, the reason says that instead, and never
  advises removing what may be the only Celeritas.
- **Caveat: only one `Bansoukou` hook runs.** Cleanroom honours only the first
  candidate, in name order, whose manifest has the attribute. A mod that sorts
  before `demonica-…` and has one keeps Demonica's hook from running, unless that
  mod chains to it. Then the fallback above prevents the crash, with its
  class-race caveat for a leftover in `mods/1.12.2`. A stale copy cannot win
  that race on its own name, which sorts after Demonica's jar. Because the
  attribute also makes Cleanroom add Demonica's jar to the class path early, its
  merged GTNHLib classes come before other jars' copies.
- **Caveat: `-Dforge.enable_auto_mod_movement=true`.** With that (off by
  default, and marked for removal in Forge) Cleanroom records extracted
  artifacts in `mods/mod_list.json` and lists them on every launch, Demonica's
  jar or not. Not supported.
- **Uninstalling Demonica** leaves
  `mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/` behind, which
  nothing loads without Demonica's jar; delete it to free the space. Demonica
  0.8.2 and earlier left `mods/1.12.2/zz-demonica-bundled-celeritas-*.jar`
  instead, which loads as a standalone Celeritas: a Demonica from 0.8.3 on
  deletes it on its first launch, or delete it by hand.

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
- The Maven jar is used once, through `remapCeleritasForDev`, which renames it
  from SRG to MCP names the way Unimined's mixin-aware remap did: tiny-remapper
  with its Mixin extension renames the classes' member references, the mixins'
  `@Shadow`/`@Overwrite` members and the target strings of their injectors in
  place, and the access transformer's names. It writes
  `build/celeritas-dev/celeritas-forge-mc12.2-<version>-dev.jar`, byte for byte
  the same from the same input. That jar is everything the build uses: the
  compile classpath (`modCompileOnly`), the tests, and the dev client
  (`devRuntimeMods`, through `-Dcrl.dev.extrapath`). Neither the raw jar nor a
  `deobf(...)` copy can run there: Cleanroom renames the SRG classes as it loads
  them, but its CleanMix remapper does not rename an injector's target in a mixin
  without a refmap (`MinecraftMixin`'s `func_71407_l()V`), and `deobf()` renames
  no mixin annotation at all.
- `verifyCeleritasPin` (part of `check`) hashes the resolved Maven jar and fails
  unless it is one of `celeritas_sha256`. `verifyBundledCeleritasPin` (part of
  `check`, and a dependency of `jar`) hashes the release asset and fails unless it
  is exactly `celeritas_asset_sha256`, so a replaced asset is never packaged.
- `generateCeleritasPin` writes `META-INF/demonica/celeritas-pin`: the upstream
  commit, the version and the accepted SHA-256s, plus `dev_sha256`, the hash of
  `remapCeleritasForDev`'s jar, which the dev client runs and the tests read. The
  task fails unless the Maven jar that remap starts from hashes to one of
  `celeritas_sha256`. The guard reads the pin at runtime (below).
- The `jar` task drops `dev_sha256`, and `verifyDistributedJar` (part of `check`)
  fails unless the distributed jar's pin names exactly the upstream commit, the
  version and the SHA-256s of `gradle.properties`, with no other key. It also
  requires the nested Celeritas jar to hash to `celeritas_asset_sha256`, the root
  `*.jar` entries to be exactly the `ContainedDeps`, and the four manifest
  attributes above, and the jar's `THIRD_PARTY_NOTICES.md` to name
  `celeritas_sha` in full. It still forbids any top-level Celeritas class or asset,
  a renamed copy or JOML: the jar carries Celeritas only as the nested asset.
- `QuarantineGuardTest` checks that the pin on the test classpath matches
  `gradle.properties`, and that its `dev_sha256` is the remapped jar the tests
  read.
  `QuarantinePriorityTest` reads upstream's mixin priorities and `@Overwrite`s from
  the pinned jar, and fails if forge122 overrides what S2 patches or S3 adds.
- `GlsmRedirectLinkageTest` runs GLSM's redirector over every class in the jar and
  checks that each rewritten call exists in `GLStateManager`.
  `celeritasJarIsTheDevRemap` checks that the jar on the test classpath is the
  mixin-aware remap: `RenderGlobalMixin` has `renderBlockLayer`, not
  `func_174977_a`, and `MinecraftMixin`'s injectors target `runTick()V`, not
  `func_71407_l()V`. `QuarantinePriorityTest` has a canary for the same: upstream's
  `@Overwrite`s include `RenderGlobal#renderBlockLayer`, so its `@Overwrite` check
  compares MCP names with MCP names. Upstream's `RenderItemMixin` keeps one SRG
  name, `func_175032_a`, which has no MCP name in the mappings (Unimined's remap
  left it too).

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
  `mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/<version>/<name>`
  so that it is extracted again.

Details in [`LEDGER.md`](LEDGER.md#the-guard).

The dev client runs `remapCeleritasForDev`'s MCP remap of the pinned Maven jar
(`devRuntimeMods`), whose hash no pin matches. The guard accepts `dev_sha256`, that
jar's hash, only in a deobfuscated environment;
`-PdevProps=demonica.celeritas.pinsOnly=true` turns that off.

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
   - alone: the bundled copy is extracted to
     `mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/<version>/`
     with its `.meta`, nothing new appears in `mods/1.12.2` but the three
     libraries, the bundled copy is used, the guard accepts it, shaders work;
   - with `Demonica-<version>.jar` disabled (renamed `.jar.disabled`): no
     `celeritas` mod, no `CeleritasLoadingPlugin`, nothing named
     `zz-demonica-bundled-celeritas` in the log;
   - next to an installed copy of the new pin: the installed jar is used, the
     repository copy is left out and stays on disk, there is no
     `DuplicateModsFoundException`, shaders work;
   - next to an installed Celeritas of another build: the installed jar is used,
     shaders are off, and the reason names that jar;
   - after removing the installed jar: the bundled copy is used on the next
     start, without another extraction;
   - with a stale `mods/1.12.2/zz-demonica-bundled-celeritas-<other pin>.jar`:
     it is deleted, with and without an installed Celeritas;
   - with a leftover `mods/1.12.2/zz-demonica-bundled-celeritas-<this pin>.jar`
     (an update from 0.8.2 or earlier): it is deleted, the repository copy is
     used, no duplicate crash;
   - with an older pin's folder in the repository: it is deleted;
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
