# Third-Party Notices

Demonica is assembled from other projects' source. This page lists each one,
where it lives in this repository, its terms, and whether it ships in the mod
jar. Where a source file carries its own copyright or license header, that
header governs the file. This is an inventory, not legal advice.

**The mod jar is a GPL-3.0 combined work.** Angelica's code is LGPL-3.0 (the
repository's [`LICENSE`](LICENSE)), and the code ported from Actinium's root
project is GPL-3.0, so the jar that combines them is GPL-3.0 as a whole.
Nothing is published until the maintainer decides how to release it (see
[`docs/FORK.md`](docs/FORK.md#license)).

## Source

| Component | Upstream | Paths in Demonica | Terms | In the mod jar |
|---|---|---|---|---|
| Angelica, including GLSM | https://github.com/GTNewHorizons/Angelica at `9fd02900ef` (tag `angelica-baseline/9fd02900ef`) | the repository's origin; GLSM in `glsm/`, `src/lwjglCommon/`, `src/lwjgl3/`; `com.gtnewhorizons.angelica.*` in `shader/` and in the root project, as Actinium adapted it (the batching font renderer among them); `src/main/resources/assets/angelica/**` | LGPL-3.0 ([`LICENSE`](LICENSE)), plus file-level notices | yes |
| Iris | https://github.com/IrisShaders/Iris, via Angelica's backport | `shader/src/main/java/{net/coderbot, kroppeb, net/irisshaders}`, `src/main/java/net/irisshaders/iris/compat/`, `src/main/resources/assets/iris/**`. Some files are based on Sodium's, as their headers say | LGPL-3.0 | yes |
| Actinium | https://github.com/DHJComical/Actinium, synced until `fee5de38` (tag `actinium-syncline/fee5de38`) and forked at `4a19c959` (tag `actinium-fork-point/4a19c959`, [`docs/FORK.md`](docs/FORK.md)) | its 1.12.2 adaptation of all of the above; its root project, ported to the root `src/main/java` (`com.dhj.actinium` became `com.demonica`); `src/main/resources/assets/actinium/**`; the ported tests. The Gradle build is derived from Actinium's | GPL-3.0 ([`third-party/actinium/LICENSE`](third-party/actinium/LICENSE)). Actinium's own inventory, which also covers the renderer Demonica does not carry, is [`third-party/actinium/THIRD_PARTY_NOTICES.md`](third-party/actinium/THIRD_PARTY_NOTICES.md) | yes |
| GTNHLib, through S8TNLib | https://github.com/GTNewHorizons/GTNHLib, ported to 1.12.2 by Actinium. S8TNLib (https://github.com/ndellagrotte/S8TNLib) publishes the 60 files Demonica uses as `com.s8tnlib:s8tnlib`, at the release `gradle.properties` pins | none: the build merges S8TNLib's jar into the mod jar | GPL-3.0, as S8TNLib distributes it: GTNHLib's files are LGPL-3.0, and whether LGPL-3.0 or GPL-3.0 covers Actinium's changes is unstated. Plus file-level notices: the `bytebuf/` sources carry LWJGL's, `asm/ClassConstantPoolParser` carries ASM's | yes |
| Reese's Sodium Options | https://github.com/FlashyReese/reeses-sodium-options, via Actinium's 1.12.2 port | `src/main/java/me/flashyreese/**`, `src/main/java/com/demonica/gui/rso/compat/`, `src/main/resources/assets/reeses-sodium-options/**` | MIT ([`third-party/reeses-sodium-options/LICENSE.md`](third-party/reeses-sodium-options/LICENSE.md)) | yes |
| ShadersMod | karyonix, sonic ether, id_miner, daxnitro | the notice in `src/main/resources/LICENSE` (relicensed under LGPL by the GTNH developers) | see that file | yes |
| Mesa | https://gitlab.freedesktop.org/mesa/mesa | `glsm/.../glsm/DisplayListIDAllocator.java` (port of `util_idalloc`, Copyright 2017 Valve Corporation); the fixed-function shader generators in `glsm/.../glsm/ffp/` are inspired by Mesa | MIT, as in the file headers | yes |
| LWJGL utility code | https://github.com/LWJGL/lwjgl3 | `glsm/.../glsm/GLDebug.java`; 9 `bytebuf/` files in S8TNLib's jar | BSD-style LWJGL license, as in the file headers | yes |

## Installed separately

Demonica runs on Celeritas and never contains it: `verifyDistributedJar` fails
the build if the mod jar holds a Celeritas class, a renamed copy of one, JOML or
Celeritas's assets.

| Mod | Upstream | Terms | Relationship |
|---|---|---|---|
| Celeritas, the 1.12.2 mod `celeritas` | https://git.taumc.org/embeddedt/celeritas at `06999aab`, as built by kappa-maintainer/Celeritas-auto-build ([`docs/celeritas/PIN.md`](docs/celeritas/PIN.md)) | LGPL-3.0 (the `COPYING.LESSER` in its jar); the JOML it relocates is MIT | Required. Demonica compiles against the pinned jar and patches its classes at runtime through `mixins.demonica.celeritas.json` ([`docs/celeritas/LEDGER.md`](docs/celeritas/LEDGER.md)) |

## Contained libraries

Packaged unmodified as nested jars in the mod jar, and listed in its manifest's
`ContainedDeps`:

| Artifact | License |
|---|---|
| `org.taumc:glsl-transformation-lib:0.2.0-32.g7dd88a4-GTNH` | not stated in its POM or jar; Angelica's credits list it as LGPL-3.0 (https://github.com/TauMC/glsl-transformation-lib) |
| `org.antlr:antlr4-runtime:4.13.2` | BSD-3-Clause |
| `org.anarres:jcpp:1.4.14` | Apache-2.0 |

## Compile-only dependencies

These are resolved at build time, never bundled, and each is governed by its
own license:
- Minecraft 1.12.2 and Cleanroom, with the libraries they bring (log4j among
  them);
- LWJGL 3.4.1, lwjglx, JOML, fastutil, Gson, ASM, Lombok and the JetBrains
  annotations;
- the mods Demonica has compatibility code for: Distant Horizons,
  NeverEnoughAnimation, Botania, CoFH Core, Extra Utilities 2, Gibbed,
  iChunUtil, CreativeCore, LittleTiles, GregTech CEu, CodeChickenLib, Obscure
  Tooltips, OldResearchReborn, Scannable, VoxelMap, Fluidlogged API,
  NeoFontRender, RevoUI and the Component Model Hider;
- what only the tests use: JUnit, Guava, Gnetum, StellarCore and JourneyMap.

See `build.gradle` for the exact coordinates.

## Before a release

The mod jar carries one license file, `LICENSE`: the ShadersMod notice and the
LGPL-3.0 text. A release also needs the text of the license the maintainer
chooses, and the notices that the MIT and BSD terms above ask binary copies to
carry: Reese's Sodium Options', Mesa's, LWJGL's and ASM's.
