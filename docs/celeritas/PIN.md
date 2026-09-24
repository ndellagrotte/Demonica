# The Celeritas pin

Demonica runs on upstream Celeritas's 1.12.2 mod (`forge122`, mod id `celeritas`),
installed separately. It compiles against one exact build of that mod and
checks for it at runtime. This page records which build that is and how to move
it.

## Current pin

| | |
|---|---|
| Upstream | https://git.taumc.org/embeddedt/celeritas, branch `stonecutter` |
| Commit | `06999aabc2ea41a772ea3d0888c88a9e72d09f02` (also Actinium's last sync base) |
| Built by | [kappa-maintainer/Celeritas-auto-build](https://github.com/kappa-maintainer/Celeritas-auto-build), release `commit-06999aabc2ea41a772ea3d0888c88a9e72d09f02-20260922T020003` |
| Maven | `org.embeddedt:celeritas-forge-mc12.2:2.4.0-autobuild.06999aab` on `https://maven.outlands.top/releases` |
| SHA-256, Maven jar | `1579bd31efdcc8832b29540173a455fcfb0ad2566c2f10b2f01fc20b679e8c92` |
| SHA-256, release asset `01-celeritas-forge-mc12.2-2.4.0-dev.jar` | `4dd4b35dbcb42a29fcf43b1e751eb1be24869ce1d28b1e463e1a7384cedf11f2` |
| Version string inside the jar | `2.4.0-dev` (the same for every upstream dev build) |

`gradle.properties` holds the same values (`celeritas_sha`, `celeritas_version`,
`celeritas_sha256`).

**Two hashes, one build.** The auto-build publishes the release assets and the
Maven artifact from two Gradle invocations with different version strings. The
two jars have the same 711 entries, byte for byte, except `META-INF/MANIFEST.MF`,
whose `Class-Path` line names the version (`common-2.4.0-dev-…` against
`common-2.4.0-autobuild.06999aab-…`). FML ignores that line, and no jar it names
exists. Users are likely to install the release asset, so both hashes are
accepted.

**Provenance, checked on 2026-09-23.**
- The release's sources jar (`03-…-sources.jar`) and the Maven `-sources.jar` are
  identical, and their Java files match `forge122/src/main/java` of a fresh clone
  of upstream at `06999aab` exactly.
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
- `META-INF/services` registers upstream's `GLStateManagerFogService`, a render
  visuals service and a chunk shader texture service.

## In Demonica's build

- `build.gradle` resolves the jar from an `exclusiveContent` repository that
  serves only this module.
- `modCompileOnly` and `modRuntimeOnly` are remapped from SRG to MCP by Unimined,
  with base-mixin and MixinExtras remapping (`mixinRemap`) and
  `catchAWNamespaceAssertion()` for the leftover access widener. The remapped jar:
  - gains a fallback refmap, `mixins.celeritas-refmap.json` (20 mixin classes,
    SRG → MCP), and the config points at it;
  - has `celeritas_at.cfg` in MCP names;
  - keeps every other entry.
- `verifyCeleritasPin` (part of `check`) hashes the resolved Maven jar and fails
  unless it is one of `celeritas_sha256`.
- `generateCeleritasAnchors` extracts every anchor of the quarantine's patches
  from the compiled mixins into `META-INF/demonica/celeritas-anchors`, together
  with the upstream commit, the version and the accepted SHA-256s. The guard
  reads it at runtime (below).
- `AnchorInventoryTest` checks those anchors against the pinned jar (in its dev
  remap), upstream's mixin priorities and `@Overwrite`s, and a snapshot of
  upstream's whole mixin inventory
  (`src/test/resources/com/demonica/celeritas/upstream-mixin-inventory.txt`).
  `verifyProductionAnchors` (part of `check`) checks the anchors the distributed
  jar carries against the SRG-named Maven jar, through the jar's refmap, as the
  guard would in a real install.
- `GlsmRedirectLinkageTest` runs GLSM's redirector over every class in the jar and
  checks that each rewritten call exists in `GLStateManager`.

## At runtime

`QuarantineGuard` hashes the Celeritas jar the game loads (from the class path,
or the mods folder) before Mixin applies the quarantine. On a pinned SHA-256 every
patch applies. On any other build it checks every anchor against that jar's
classes and turns off the patch groups whose anchors moved: at worst shaders are
off, with the reason in the log and on the shader pack screen. The levels and
what each group costs are in [`LEDGER.md`](LEDGER.md#the-guard).

The development workspace runs Unimined's remap of the pin, whose hash never
matches, so every dev run checks the anchors and logs whether they all hold.

## Moving the pin

1. Pick an upstream commit and its auto-build release. Download the release's
   `01-…-dev.jar` and the Maven jar; hash both.
2. Update `celeritas_sha`, `celeritas_version` and `celeritas_sha256` in
   `gradle.properties`, and the table above.
3. Run `./gradlew build`. Expect `AnchorInventoryTest` to report every anchor and
   upstream mixin that moved; its snapshot mismatch writes the new inventory to
   `run/test/upstream-mixin-inventory.actual.txt`. Before changing anything, a dev
   run on the new jar shows what the guard would do for a player who installed it.
4. For each change, re-derive the affected quarantine patch and its `@Patch`
   declaration, update its ledger entry (`LEDGER.md`), then update the snapshot.
5. Repeat the spike's two runs ([`SPIKE.md`](SPIKE.md) sections 1 and 2), the
   current checkpoint's matrix, the guard's drills
   ([`LEDGER.md`](LEDGER.md#the-guard), "Rehearsing it") and the production-shaped
   smoke test (`LEDGER.md`, after Checkpoint 10) before accepting the new pin.
