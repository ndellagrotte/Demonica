# Demonica

Demonica brings shader packs to Minecraft 1.12.2 on Cleanroom. It is the
[Iris](https://github.com/IrisShaders/Iris) shader pipeline, the GLSM GL state
manager and a compatibility layer, running on top of the
[Celeritas](https://git.taumc.org/embeddedt/celeritas) renderer, which its jar carries. It takes over
the role of [Actinium](https://github.com/DHJComical/Actinium), from which it
forked at `4a19c959` ([`docs/FORK.md`](docs/FORK.md)); unlike Actinium, it
carries no copy of the terrain renderer.

**Requires:** Cleanroom 0.7.4 or later (its SDL 3 window; Java 25). Cleanroom
0.6.x is no longer supported. Celeritas's 1.12.2 mod
comes with Demonica: its jar bundles the build pinned in
[`docs/celeritas/PIN.md`](docs/celeritas/PIN.md), upstream commit `d94ec1e5` as
published by the Celeritas auto-build (the release asset
`01-celeritas-forge-mc12.2-2.5.0-dev.jar`), unmodified. A Celeritas you install
yourself takes precedence over the bundled one; shaders need the pinned build
(below). GTNHLib's classes, from
[S8TNLib](https://github.com/ndellagrotte/S8TNLib), are part of Demonica's jar too.

Demonica does not run next to Actinium. It is client-only: on a dedicated server
it stays inactive.

## What it does

- Shader packs draw Celeritas's terrain, with terrain shadows and the block IDs
  packs use for waving plants and water.
- GLSM, Angelica's GL state manager, to which vanilla's and mods' GL calls are
  redirected.
- Iris's and Demonica's settings in Celeritas's Video Settings, next to its
  Shader Packs tab.
- Actinium's end portal and its compatibility with other mods; its debug tools
  are a separate, optional jar ([below](#the-diagnostics-jar)).

Demonica patches Celeritas's own classes in one place only, and each patch is
recorded with a draft of the upstream change that would make it unnecessary
([`docs/celeritas/LEDGER.md`](docs/celeritas/LEDGER.md)). Before applying
any of them, Demonica hashes the Celeritas jar the game loads. On any build but the
pinned one, only the fog patch applies and shaders are off, and the log and the
shader pack screen say why
([`docs/celeritas/PIN.md`](docs/celeritas/PIN.md#at-runtime)).

## Installing

Download `Demonica-<version>.jar` from the
[releases](https://github.com/ndellagrotte/Demonica/releases) and put it in
`mods/`. That is the only jar to install. On start Demonica extracts the bundled
Celeritas to `mods/1.12.2/zz-demonica-bundled-celeritas-<commit>-<timestamp>.jar`
and deletes the copies of earlier Demonica versions there.

A Celeritas jar you installed yourself takes precedence: Demonica uses it, leaves
its file alone, deletes the extracted bundled copy and logs which jar it uses
(unless another mod's Bansoukou hook runs first; see
[`docs/celeritas/PIN.md`](docs/celeritas/PIN.md)).
Shaders need the pinned build, so with any other build of Celeritas installed
they are off, and the log and the shader pack screen name that jar. Remove it
from `mods/` to use the bundled build (it is extracted again on the next start),
or install the pinned build. Demonica 0.3.0 to 0.5.0 needed
`s8tnlib-<version>.jar` beside them; it can go now (Demonica logs a warning while
it is there).

**To uninstall Demonica, also delete `mods/1.12.2/zz-demonica-bundled-celeritas-*.jar`.**
Otherwise it keeps loading as a standalone Celeritas.

Demonica runs on Cleanroom's own OpenGL context. `advanced.opengl_profile`
(Video Settings → Advanced → OpenGL Profile) selects a core context instead,
which macOS gets automatically.

## Building

JDK 25. The build uses [CleanroomGradle](https://github.com/CleanroomMC/CleanroomGradle)'s
userdev workspace (`cleanroom.userdev(cleanroom_version)`, Cleanroom 0.7.4 with
MCP names) and CleanroomMC's Conventions plugin; the Unimined setup of
Demonica 0.7 and earlier is gone. The first build downloads that workspace and the pinned Celeritas
from `maven.outlands.top`, which `verifyCeleritasPin` checks by SHA-256, and the
same build's release asset from the Celeritas auto-build's GitHub releases, which
`verifyBundledCeleritasPin` checks against its exact hash and the jar bundles. GTNHLib
comes from the latest release of S8TNLib: every build asks GitHub's API which
release that is (`GITHUB_TOKEN`, when set, authenticates the call), downloads
its jar and merges its classes into the mod jar. The jar's manifest records
`S8TNLib-Version` and `S8TNLib-Commit`. Offline, or when GitHub cannot be
reached, the build uses the last answer, cached in
`.gradle/demonica/s8tnlib-latest`.

```sh
./gradlew build             # compile, test, and check the pin and the jars
./gradlew -q printVersion   # the version this checkout builds
./gradlew runClient         # dev client with Celeritas and Demonica
```

To try an S8TNLib build that has no release yet, pass its `build/libs` with
`-Ps8tnlibDir=<dir>`; the version is that of the `s8tnlib-<version>.jar` there
(`-Ps8tnlibVersion=<version>` chooses one when there are several). Likewise,
`-PceleritasAssetDir=<dir>` takes the Celeritas release asset from a local folder
instead of GitHub, for a pruned release or an offline build. The file there must be
named exactly the value of `celeritas_release_asset` and hash to
`celeritas_asset_sha256`: the copy in Gradle's cache has the release tag appended
and must be renamed, while `build/tmp/celeritasPinCandidate/` already has the right
name. `./gradlew celeritasPinCandidate -PceleritasCandidate=<tag|latest>` prints the
`gradle.properties` lines for a new pin ([`docs/celeritas/PIN.md`](docs/celeritas/PIN.md#moving-the-pin)).

`build/libs` holds what is distributed:

| Jar | What it is |
|---|---|
| `build/libs/Demonica-<version>.jar` | the mod, `reobfJar`'s SRG rename of the dev jar, for a normal install |
| `build/libs/Demonica-diagnostics-<version>.jar` | the diagnostics jar (SRG, from `:diagnostics:reobfJar`) |
| `build/libs/Demonica-<version>-sources.jar` | the sources, the merged GLSM, Iris and GTNHLib sources included |
| `build/devlibs/*-mcp.jar` | the MCP-named jars `jar` writes, for dev use only |

### Versions and branches

The version comes from git tags (Conventions' Versioning), not from
`gradle.properties`; `./gradlew -q printVersion` prints it. A release is a tag
`vX.Y.Z` (a suffix such as `-beta` sets the release type) on `main`, the release
branch; pushing the tag runs the release workflow, which attaches the jars of
`build/libs` to a GitHub release. Between tags `main` builds the next patch
version, `<X.Y.Z+1>-dev.N`. `develop/0.8` is the next minor version and builds
`0.8.0-dev.N`; feature branches are cut from `develop/0.8` and build
`0.8.0-<branch>.N` (`feat/x` becomes `feat-x`). N counts the commits since the
last tag. A commit that is not pushed adds `.local`, modified or untracked files
add `.dirty`, and CI adds `+run.<run number>`. Versioning needs the tags: clone
with full history.

### The dev client

`./gradlew runClient` starts the client in `run/client` (1280x720, 6 GB heap).
Cleanroom loads the mods from `-Dcrl.dev.extrapath`, never from the JVM's class
path: Demonica's own classes (with the merged GLSM, Iris and GTNHLib classes),
the diagnostics' classes, and the raw jars of the `devRuntimeMods`
configuration, which Cleanroom remaps from SRG to MCP names as it loads them
([`docs/celeritas/SPIKE.md`](docs/celeritas/SPIKE.md#dev-class-loading)).
`runClient` takes:

| Property | Effect |
|---|---|
| `-PdevScript=@scripts/<name>.txt` | drive the client from a script in `run/client/scripts` (steps in `com.demonica.diagnostics.dev.DevHarness`, part of the diagnostics jar) |
| `-PdevProps=key=value,...` | pass system properties, for example `demonica.celeritas.pinsOnly=true` to see what a Celeritas that is not the pin gets, or `demonica.openglProfile=core` to start on a core context |
| `-PwithCompatMods` | also load the mods Demonica has compatibility code for (Chunk Animator among them: its coremod's MCP name tables are unpacked into `run/mcp` first) |
| `-PwithHudCache=stellarcore` or `=gnetum` | also load StellarCore (with JourneyMap) or Gnetum, whose HUD caches Demonica bridges to GLSM |
| `-PwithModernSplash` | also load Modern Splash, a coremod that replaces FML's loading screen |
| `-PwithoutCeleritas` | leave Celeritas out, to see what a player without it sees |
| `-PwithoutDiagnostics` | leave the diagnostics jar out, as a player without it runs |
| `-PmixinVerbose`, `-PmixinExport` | Mixin's verbose log; the transformed classes in `run/client/.mixin.out` |

A mod reaches the build in one of two ways, both in `build.gradle`'s
`dependencies`:

- **To run it in the dev client**, add its normal (SRG-named) jar to
  `devRuntimeMods`, for example
  `devRuntimeMods 'curse.maven:<slug>-<project id>:<file id>'`. Never pass it
  through `deobf(...)` there: the dev client needs the jar as players have it,
  with its mixin refmaps and SRG names, which Cleanroom remaps at load time.
- **To compile against it**, add `modCompileOnly deobf('<coordinate>')`.
  `deobf(...)` renames the jar's classes and members to MCP names for the compile
  and test classpaths only; a jar that is already MCP-named (CodeChickenLib's
  `-dev` jar) goes in without it.

## The diagnostics jar

Demonica's GL debugging tools are a separate mod,
`Demonica-diagnostics-<version>.jar`, which every build makes next to the mod
jar. The mod jar calls them through no-op stand-ins, so without
the diagnostics jar they cost nothing and log nothing. It carries:

- the GL debug logs and timing tables of the Iris pipeline (world pass, game
  loop, composite and final passes, framebuffer output), the GL state probe
  around Celeritas's terrain draw, and GLSM's draw logs;
- the PBR texture and shader regression logs;
- the GL flight recorder, a crash-safe record of the last GL commands and
  stages;
- the scripted dev harness that `-PdevScript` drives.

Dev runs load it; `-PwithoutDiagnostics` runs what a player without it has.
For a bug report, put it in `mods/` next to `Demonica-<version>.jar` of the
same version (it does nothing next to another version), then turn on what is
asked for:

| Switch | Effect |
|---|---|
| Video Settings → Debug → GL State Debug (`debug.enable_gl_debug`), or `-Ddemonica.glDebug=true` | GL state checkpoints, pipeline and framebuffer logs, the GL state probe, GLSM's draw logs, the regression logs |
| Render Timing Debug, GPU Timing Debug, or `-Ddemonica.perfDebug=true`, `-Ddemonica.gpuPerfDebug=true` | the timing tables, with GPU timer queries |
| PBR Texture Debug, or `-Ddemonica.pbrDebug=true` | PBR loading logs and an export of each PBR atlas to `pbr_debug/atlas` |
| Cloud Control Debug, or `-Ddemonica.cloudControlDebug=true` | the cloud-control pixels Bliss's cloud passes read |
| `-Ddemonica.glFlightRecorder=crash` | record to `diagnostics/gl-flight-*.bin` |
| `-Ddemonica.glsm.verboseDrawLogs=true`, `-Ddemonica.debug.textureUnitLogs=true`, `-Ddemonica.debug.portalRenderLogs=true` | further GLSM draw, texture-unit and end-portal logs |

The Debug page appears with `"enable_debug_tab": true` in
`config/demonica-options.json`; its GL debug switches show only while the
diagnostics jar is installed. A flight recording is read with
`java -cp Demonica-diagnostics-<version>.jar com.demonica.diagnostics.flight.GlFlightRecordingDecoder <file>`,
which prints it as JSON.

## License

Demonica is distributed under AGPL-3.0 ([`LICENSE`](LICENSE)); releases up to
0.4.0 were GPL-3.0. Code ported from other projects keeps its license:
Angelica's and Iris's is LGPL-3.0 ([`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt),
Angelica's license file), and Actinium's is GPL-3.0
([`LICENSE-GPL-3.0.txt`](LICENSE-GPL-3.0.txt)); section 13 of GPL-3.0 and of
AGPL-3.0 lets these parts be combined with Demonica's AGPL-3.0 code in one work. The contained glsl-transformer
library is AGPL-3.0. GTNHLib, from S8TNLib, is GPL-3.0 and part of the mod jar. The MIT- and
BSD-licensed parts keep their notices. [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
has the details, and the mod jar and the diagnostics jar carry all four files.
The reasons are in [the decision record](docs/glsl-transformer_adoption/DECISION.md).

## Credits

- The [Iris](https://github.com/IrisShaders/Iris) team (coderbot, IMS212,
  Justsnoopy30, FoundationGames), and Asek3 for [Oculus](https://github.com/Asek3/Oculus)
- The Angelica contributors, and mitchej123 in particular for GLSM
- DHJComical and embeddedt for Actinium and its 1.12.2 Iris/Celeritas adaptation
- embeddedt for Celeritas; JellySquid and the CaffeineMC team for Sodium
- The GTNewHorizons team for GTNHLib; embeddedt and Ferri_Arnus for
  [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib), which Demonica's shader
  transforms used up to 0.4.0
- douira for [glsl-transformer](https://github.com/IrisShaders/glsl-transformer)
- The original ShadersMod authors: karyonix, sonic ether, id_miner and daxnitro
- [Mesa](https://gitlab.freedesktop.org/mesa/mesa), for the display-list ID
  allocator port and the fixed-function shader generation it inspired
