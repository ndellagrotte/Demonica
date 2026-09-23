# Demonica

Demonica is the [Iris](https://github.com/IrisShaders/Iris) shader pipeline and
GLSM, the GL state manager, packaged as libraries for Minecraft 1.12.2 on
Cleanroom. It is a fork of [Angelica](https://github.com/GTNewHorizons/Angelica)
that carries the 1.12.2 adaptation developed in
[Actinium](https://github.com/DHJComical/Actinium). Every synced file is
byte-identical to the Actinium syncline and audited
([`docs/PROVENANCE.md`](docs/PROVENANCE.md)).

**It is not a mod.** Demonica has no mixins, no mod metadata and no terrain
renderer. A host mod (Actinium is the reference) wires it into the game;
[`docs/HOST_CONTRACT.md`](docs/HOST_CONTRACT.md) lists what the host must supply.

## Modules

| Gradle project | Artifact | Contents |
|---|---|---|
| `:` | `com.demonica:demonica` | the Iris tree (`net.coderbot`, `kroppeb`, `net.irisshaders`) and its resources |
| `:glsm` | `com.demonica:demonica-glsm` | GLSM and the LWJGL 3 render backend |
| `:GTNHLib`, `:celeritas-common` | never published | Actinium's forks, vendored compile-only ([`vendor/README.md`](vendor/README.md)) |

Both jars are MCP-named and are not remapped. Syncline:
`actinium@fee5de3834352050fc453bde1d93030c6d434983`.

## Building

JDK 25 (compiling with `--release 21`). The first build sets up the Minecraft
1.12.2/Cleanroom workspace through Unimined.

```sh
./gradlew build                # compile, tests, module-boundary and published-jar checks
./gradlew publishToMavenLocal  # the only publishing target for now
```

## License

The license of the combined work is undecided. Angelica's code is LGPL-3.0
([`LICENSE`](LICENSE)), and Actinium, whose changes Demonica carries, is
GPL-3.0. Until that is settled, nothing is published beyond `mavenLocal`. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for every upstream and its terms.

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
