# Demonica

Demonica brings shader packs to Minecraft 1.12.2 on Cleanroom. It is the
[Iris](https://github.com/IrisShaders/Iris) shader pipeline, the GLSM GL state
manager and a compatibility layer, running on top of the separately installed
[Celeritas](https://git.taumc.org/embeddedt/celeritas) renderer. It takes over
the role of [Actinium](https://github.com/DHJComical/Actinium), from which it
forked at `4a19c959` ([`docs/FORK.md`](docs/FORK.md)); unlike Actinium, it
carries no copy of the terrain renderer.

**Requires:** Cleanroom (0.6.12 and 0.6.13 are tested), and two mods:

- Celeritas's 1.12.2 mod, the build pinned in
  [`docs/celeritas/PIN.md`](docs/celeritas/PIN.md): upstream commit
  `9b661b70`, as published by the Celeritas auto-build (the release asset
  `01-celeritas-forge-mc12.2-2.5.0-dev.jar` or the Maven jar; both are
  accepted).
- [S8TNLib](https://github.com/ndellagrotte/S8TNLib) 0.3.0 or later, which
  brings GTNHLib's classes.

Demonica does not run next to Actinium, and does nothing without Celeritas or
S8TNLib, so that the game can tell you which is missing.

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
any of them, Demonica hashes the installed Celeritas jar. On any build but the
pinned one, only the fog patch applies and shaders are off, and the log and the
shader pack screen say why
([`docs/celeritas/PIN.md`](docs/celeritas/PIN.md#at-runtime)).

## Installing

Download `Demonica-<version>.jar` from the
[releases](https://github.com/ndellagrotte/Demonica/releases) and put it in
`mods/`, next to the Celeritas jar named above and `s8tnlib-<version>.jar`
from [S8TNLib's releases](https://github.com/ndellagrotte/S8TNLib/releases),
0.3.0 or later. Without either mod, Forge lists what is missing.

Demonica runs on Cleanroom's own OpenGL context. `advanced.opengl_profile`
(Video Settings → Advanced → OpenGL Profile) selects a core context instead,
which macOS gets automatically.

## Building

JDK 25 (compiling with `--release 21`). The first build sets up the Minecraft
1.12.2/Cleanroom workspace through Unimined, downloads the pinned Celeritas
from `maven.outlands.top` and the pinned S8TNLib from its GitHub release.
`verifyCeleritasPin` and `verifyS8tnlibPin` check both jars' SHA-256.

```sh
./gradlew build      # compile, test, and check the pins and the mod jar
./gradlew runClient  # dev client with Celeritas, S8TNLib and Demonica
```

To try an S8TNLib build that has no release yet, pass its `build/libs` with
`-Ps8tnlibDir=<dir>`; the S8TNLib pin is then only reported. The build looks
for the version in `s8tnlib_version`, so a snapshot also needs
`-Ps8tnlib_version=<version>`, such as `0.4.0-SNAPSHOT`.

The mod jar is `build/libs/Demonica-<version>.jar`, remapped to SRG names for a
normal install, and the diagnostics jar is `build/libs/Demonica-diagnostics-<version>.jar`.
`runClient` takes:

| Property | Effect |
|---|---|
| `-PdevScript=@scripts/<name>.txt` | drive the client from a script in `run/client/scripts` (steps in `com.demonica.diagnostics.dev.DevHarness`, part of the diagnostics jar) |
| `-PdevProps=key=value,...` | pass system properties, for example `demonica.celeritas.pinsOnly=true` to see what a Celeritas that is not the pin gets, or `demonica.openglProfile=core` to start on a core context |
| `-PwithCompatMods` | also load the mods Demonica has compatibility code for |
| `-PwithHudCache=stellarcore` or `=gnetum` | also load StellarCore (with JourneyMap) or Gnetum, whose HUD caches Demonica bridges to GLSM |
| `-PwithoutCeleritas` | leave Celeritas out, to see what a player without it sees |
| `-PwithoutS8tnlib` | leave S8TNLib out, likewise |
| `-PwithoutDiagnostics` | leave the diagnostics jar out, as a player without it runs |
| `-PmixinVerbose`, `-PmixinExport` | Mixin's verbose log; the transformed classes in `run/client/.mixin.out` |

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
library is AGPL-3.0. S8TNLib, installed separately, is GPL-3.0. The MIT- and
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
