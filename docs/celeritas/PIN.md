# The Celeritas pin

Demonica runs on upstream Celeritas's 1.12.2 mod (`forge122`, mod id `celeritas`),
installed separately. It compiles against one exact build of that mod and
checks for it at runtime. This page records which build that is and how to move
it.

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

`gradle.properties` holds the same values (`celeritas_sha`, `celeritas_version`,
`celeritas_sha256`).

**Two hashes, one build.** The auto-build publishes the release assets and the
Maven artifact from two Gradle invocations with different version strings. The
two jars have the same 708 files, byte for byte, except `META-INF/MANIFEST.MF`,
whose `Class-Path` line names the version (`common-2.5.0-dev-…` against
`common-2.5.0-autobuild.9b661b70-…`). FML ignores that line, and no jar it names
exists. Users are likely to install the release asset, so both hashes are
accepted.

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
- `generateCeleritasPin` writes `META-INF/demonica/celeritas-pin`: the upstream
  commit, the version and the accepted SHA-256s, plus `dev_sha256`, the hash of
  the remapped jar the workspace compiles, tests and runs against (the task fails
  if the compile and runtime classpaths hold different ones). The guard reads it
  at runtime (below).
- The `jar` task drops `dev_sha256`, and `verifyDistributedJar` (part of `check`)
  fails unless the distributed jar's pin names exactly the upstream commit, the
  version and the SHA-256s of `gradle.properties`, with no other key.
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
on the shader pack screen, and only the fog patch (S15) applies. Details in
[`LEDGER.md`](LEDGER.md#the-guard).

The development workspace runs Unimined's remap of the pin, which no pin matches;
the guard accepts it by `dev_sha256`, only in a deobfuscated environment.
`-PdevProps=demonica.celeritas.pinsOnly=true` makes a dev client reject it.

## Moving the pin

1. Pick an upstream commit and its auto-build release. Download the release's
   `01-…-dev.jar` and the Maven jar; hash both.
2. Update `celeritas_sha`, `celeritas_version` and `celeritas_sha256` in
   `gradle.properties`, and the table above.
3. Run `./gradlew build`. A Celeritas class or member that Demonica's code uses
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
   release asset before accepting the new pin.
