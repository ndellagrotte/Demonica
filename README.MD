# Demonica

Demonica brings shader packs to Minecraft 1.12.2 on Cleanroom. It is the
[Iris](https://github.com/IrisShaders/Iris) shader pipeline, the GLSM GL state
manager and a compatibility layer, running on top of the separately installed
[Celeritas](https://git.taumc.org/embeddedt/celeritas) renderer. It takes over
the role of [Actinium](https://github.com/DHJComical/Actinium), from which it
forked at `4a19c959` ([`docs/FORK.md`](docs/FORK.md)); unlike Actinium, it
carries no copy of the terrain renderer.

**Requires:** Cleanroom (0.6.12 and 0.6.13 are tested) and Celeritas's 1.12.2
mod, the build pinned in [`docs/celeritas/PIN.md`](docs/celeritas/PIN.md):
upstream commit `06999aab`, as published by the Celeritas auto-build (the release
asset `01-celeritas-forge-mc12.2-2.4.0-dev.jar` or the Maven jar; both are
accepted).
Demonica does not run next to Actinium, and does nothing without Celeritas, so
that the game can tell you it is missing.

## What it does

- Shader packs draw Celeritas's terrain, with terrain shadows and the block IDs
  packs use for waving plants and water.
- GLSM, Angelica's GL state manager, to which vanilla's and mods' GL calls are
  redirected.
- Iris's and Demonica's settings in Celeritas's Video Settings, next to its
  Shader Packs tab.
- Actinium's end portal and debug tools, and its compatibility with other mods.

Demonica patches Celeritas's own classes in one place only, and each patch is
recorded with a draft of the upstream change that would make it unnecessary
([`docs/celeritas/LEDGER.md`](docs/celeritas/LEDGER.md)). If a different
Celeritas build is installed, Demonica checks what its patches bind to before
applying any of them, and turns off what no longer fits: at worst shaders are
off, and the log and the shader pack screen say why.

## Installing

Download `Demonica-<version>.jar` from the
[releases](https://github.com/ndellagrotte/Demonica/releases) and put it in
`mods/`, next to the Celeritas jar named above and
[S8TNLib](https://github.com/ndellagrotte/S8TNLib/releases), which brings
GTNHLib's classes: `s8tnlib-<version>.jar`, at the version Demonica's release
notes name (0.3.0 or later). Without either mod, Forge lists what is missing.

## Building

JDK 25 (compiling with `--release 21`). The first build sets up the Minecraft
1.12.2/Cleanroom workspace through Unimined, downloads the pinned Celeritas
from `maven.outlands.top` and the pinned S8TNLib from its GitHub release.
`verifyCeleritasPin` and `verifyS8tnlibPin` check both jars' SHA-256.

```sh
./gradlew build      # compile, test, and check the pins, the patches' anchors and the mod jar
./gradlew runClient  # dev client with Celeritas, S8TNLib and Demonica
```

To try an S8TNLib build that has no release yet, pass its `build/libs` with
`-Ps8tnlibDir=<dir>`; the S8TNLib pin is then only reported.

The mod jar is `build/libs/Demonica-<version>.jar`, remapped to SRG names for a
normal install. `runClient` takes:

| Property | Effect |
|---|---|
| `-PdevScript=@scripts/<name>.txt` | drive the client from a script in `run/client/scripts` (steps in `com.demonica.dev.DevHarness`) |
| `-PdevProps=key=value,...` | pass system properties, for example `demonica.guard.drill=SHADOW` to rehearse a patch group failing |
| `-PwithCompatMods` | also load the mods Demonica has compatibility code for |
| `-PwithoutCeleritas` | leave Celeritas out, to see what a player without it sees |
| `-PmixinVerbose`, `-PmixinExport` | Mixin's verbose log; the transformed classes in `run/client/.mixin.out` |

## License

Demonica is distributed under GPL-3.0 ([`LICENSE`](LICENSE)). Angelica's and
Iris's code is LGPL-3.0 ([`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt),
Angelica's license file), which may be conveyed under GPL-3.0; the code ported
from Actinium is GPL-3.0. S8TNLib, installed separately, is GPL-3.0 too. The
MIT- and BSD-licensed parts keep their notices. [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
has the details, and the mod jar carries all three files.

## Credits

- The [Iris](https://github.com/IrisShaders/Iris) team (coderbot, IMS212,
  Justsnoopy30, FoundationGames), and Asek3 for [Oculus](https://github.com/Asek3/Oculus)
- The Angelica contributors, and mitchej123 in particular for GLSM
- DHJComical and embeddedt for Actinium and its 1.12.2 Iris/Celeritas adaptation
- embeddedt for Celeritas; JellySquid and the CaffeineMC team for Sodium
- The GTNewHorizons team for GTNHLib; embeddedt and Ferri_Arnus for
  [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib)
- The original ShadersMod authors: karyonix, sonic ether, id_miner and daxnitro
- [Mesa](https://gitlab.freedesktop.org/mesa/mesa), for the display-list ID
  allocator port and the fixed-function shader generation it inspired
