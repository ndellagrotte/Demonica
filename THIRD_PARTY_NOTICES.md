# Third-Party Notices

Demonica is assembled from other projects' source. This page lists each one,
where it lives in this repository, its terms, and whether it ships in a
published jar. Where a source file carries its own copyright or license header,
that header governs the file. This is an inventory, not legal advice.

**The license of the combined work is still open.** Angelica's code is
LGPL-3.0, and Actinium's root license, which covers its changes, is GPL-3.0.
Demonica publishes nothing beyond `mavenLocal` until that is decided (see
[`docs/PROVENANCE.md`](docs/PROVENANCE.md#open-license)).

## Source

| Component | Upstream | Paths in Demonica | Terms | Shipped |
|---|---|---|---|---|
| Angelica, including GLSM | https://github.com/GTNewHorizons/Angelica at `9fd02900ef` (tag `angelica-baseline/9fd02900ef`) | the repository's origin; GLSM in `glsm/`, `src/lwjglCommon/`, `src/lwjgl3/`; `com.gtnewhorizons.angelica.*` support classes | LGPL-3.0 ([`LICENSE`](LICENSE)), plus file-level notices | yes |
| Iris | https://github.com/IrisShaders/Iris, via Angelica's backport | `src/main/java/{net/coderbot, kroppeb, net/irisshaders}`, `src/main/resources/assets/iris/**` | LGPL-3.0 | yes |
| Actinium | https://github.com/DHJComical/Actinium at `fee5de38` (tag `actinium-syncline/fee5de38`) | its 1.12.2 adaptation of all of the above, `assets/actinium/**`, the ported tests; the Gradle build is derived from Actinium's | GPL-3.0 ([`vendor/ACTINIUM-LICENSE.txt`](vendor/ACTINIUM-LICENSE.txt)); Actinium's own inventory is [`vendor/ACTINIUM-THIRD_PARTY_NOTICES.md`](vendor/ACTINIUM-THIRD_PARTY_NOTICES.md) | yes |
| ShadersMod | karyonix, sonic ether, id_miner, daxnitro | the notice in `src/main/resources/LICENSE` (relicensed under LGPL by the GTNH developers) | see that file | yes |
| Mesa | https://gitlab.freedesktop.org/mesa/mesa | `glsm/.../glsm/DisplayListIDAllocator.java` (port of `util_idalloc`, Copyright 2017 Valve Corporation); the fixed-function shader generators in `glsm/.../glsm/ffp/` are inspired by Mesa | MIT, as in the file headers | yes |
| LWJGL utility code | https://github.com/LWJGL/lwjgl3 | `glsm/.../glsm/GLDebug.java` | BSD-style LWJGL license, as in the file header | yes |
| GTNHLib (Actinium's 1.12.2 fork) | https://github.com/GTNewHorizons/GTNHLib | `vendor/GTNHLib/` | LGPL-3.0, plus file-level notices (the `bytebuf/` sources carry LWJGL's) | **no** (compile-only) |
| celeritas-common (Actinium's fork of Celeritas) | https://git.taumc.org/embeddedt/celeritas, derived from Embeddium and Sodium | `vendor/celeritas-common/` | mixed provenance: follow the file-level notices. `grondag/bitraster` is Apache-2.0 (its `LICENSE`); the Canvas-derived `occlusion/geometry` sources carry LGPL-3.0 headers | **no** (compile-only) |

The vendored trees are byte-identical to Actinium at the syncline. See
[`vendor/README.md`](vendor/README.md).

## Runtime dependencies (named in the published POMs)

| Artifact | License |
|---|---|
| `org.taumc:glsl-transformation-lib` | not stated in its POM or jar; Angelica's credits list it as LGPL-3.0 (https://github.com/TauMC/glsl-transformation-lib) |
| `org.antlr:antlr4-runtime` | BSD-3-Clause |
| `org.anarres:jcpp` | Apache-2.0 |

## Compile-only dependencies

Minecraft 1.12.2 and Cleanroom, LWJGL 3.4.1, lwjglx, joml, fastutil, gson, ASM,
log4j, Lombok, JetBrains annotations and Distant Horizons are resolved at build
time and are never bundled. Each is governed by its own license. See
`build.gradle` for the exact coordinates.
