# Demonica

Demonica brings shader packs to Minecraft 1.12.2 on Cleanroom. It is the
[Iris](https://github.com/IrisShaders/Iris) shader pipeline, the GLSM GL state
manager and a compatibility layer, running on top of the separately installed
[Celeritas](https://git.taumc.org/embeddedt/celeritas) renderer. It takes over
the role of [Actinium](https://github.com/DHJComical/Actinium), from which it
forked at `4a19c959` ([`docs/FORK.md`](docs/FORK.md)); unlike Actinium, it
carries no copy of the terrain renderer.

**Requires:** Cleanroom 0.6.12 and Celeritas's 1.12.2 mod, the exact build
pinned in [`docs/celeritas/PIN.md`](docs/celeritas/PIN.md). Demonica does not
run next to Actinium.

## Status

Under construction on `feat/celeritas-shader-mod`: the mod loads on Celeritas
and initializes GLSM and Iris; shader pack rendering is being brought back
patch by patch ([`docs/celeritas/`](docs/celeritas/)).

## Building

JDK 25 (compiling with `--release 21`). The first build sets up the Minecraft
1.12.2/Cleanroom workspace through Unimined and downloads the pinned Celeritas.

```sh
./gradlew build      # compile, tests, and checks on the pin and the mod jar
./gradlew runClient  # dev client with Celeritas and Demonica
```

The mod jar is `build/libs/Demonica-<version>.jar`. `runClient` accepts
`-PdevScript=@path/to/script` to drive the client from a script
(`com.demonica.dev.DevHarness`).

## License

Demonica combines Angelica's LGPL-3.0 code with code ported from Actinium,
which is GPL-3.0, so the combined work is GPL-3.0. Nothing is published until
the maintainer decides how to release it. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Credits

- The [Iris](https://github.com/IrisShaders/Iris) team (coderbot, IMS212,
  Justsnoopy30, FoundationGames), and Asek3 for [Oculus](https://github.com/Asek3/Oculus)
- The Angelica contributors, and mitchej123 in particular for GLSM
- DHJComical and embeddedt for Actinium and its 1.12.2 Iris/Celeritas adaptation
- embeddedt for Celeritas; JellySquid and the CaffeineMC team for Sodium
- The GTNewHorizons team for GTNHLib; embeddedt and Ferri_Arnus for
  [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib)
- FlashyReese for [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)
- The original ShadersMod authors: karyonix, sonic ether, id_miner and daxnitro
- [Mesa](https://gitlab.freedesktop.org/mesa/mesa), for the display-list ID
  allocator port and the fixed-function shader generation it inspired
