# Third-Party Notices

Demonica is assembled from other projects' source. This page lists each one,
where it lives in this repository, its terms, and whether it ships in the mod
jar. Where a source file carries its own copyright or license header, that
header governs the file. This is an inventory, not legal advice.

**Demonica is AGPL-3.0.** On 2026-09-25 the maintainer decided to relicense
Demonica's own code, the files written for Demonica and Demonica's changes to
ported files, under AGPL-3.0 ([`LICENSE`](LICENSE)), before glsl-transformer,
an AGPL-3.0 library, entered the jar; the change was made on 2026-09-28
([decision record](docs/glsl-transformer_adoption/DECISION.md)). Releases up to
0.4.0 were GPL-3.0. Ported code keeps its license: Angelica's and Iris's code is
LGPL-3.0 (its license file is [`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt)),
and the code ported from Actinium's root project is GPL-3.0
([`LICENSE-GPL-3.0.txt`](LICENSE-GPL-3.0.txt)). The parts combine in one jar
because section 13 of GPL-3.0 and section 13 of AGPL-3.0 each permit combining
a work under one with a work under the other: each part stays under its own
license, and AGPL-3.0's network-interaction requirement applies to the
combination. The LGPL parts join as GPL-3.0 code: LGPL-3.0 is GPL-3.0 plus
additional permissions, which section 7 of GPL-3.0 lets a redistributor remove.
File headers still govern their files, and all three license texts stay in the
repository and in the jars ([`docs/FORK.md`](docs/FORK.md#license)).

## Source

| Component | Upstream | Paths in Demonica | Terms | In the mod jar |
|---|---|---|---|---|
| Angelica, including GLSM | https://github.com/GTNewHorizons/Angelica at `9fd02900ef` (tag `angelica-baseline/9fd02900ef`) | the repository's origin; GLSM in `glsm/`, `src/lwjglCommon/`, `src/lwjgl3/`; `com.gtnewhorizons.angelica.*` in `shader/` and in the root project, as Actinium adapted it; `src/main/resources/assets/angelica/**` | LGPL-3.0 ([`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt), Angelica's license file), plus file-level notices | yes |
| Iris | https://github.com/IrisShaders/Iris, via Angelica's backport | `shader/src/main/java/{net/coderbot, kroppeb, net/irisshaders}`, `src/main/java/net/irisshaders/iris/compat/`, `src/main/resources/assets/iris/**`. Some files are based on Sodium's, as their headers say | LGPL-3.0 | yes |
| Actinium | https://github.com/DHJComical/Actinium, synced until `fee5de38` (tag `actinium-syncline/fee5de38`) and forked at `4a19c959` (tag `actinium-fork-point/4a19c959`, [`docs/FORK.md`](docs/FORK.md)) | its 1.12.2 adaptation of all of the above; its root project, ported to the root `src/main/java` (`com.dhj.actinium` became `com.demonica`); `src/main/resources/assets/actinium/**`; the ported tests. The Gradle build is derived from Actinium's | GPL-3.0 ([`third-party/actinium/LICENSE`](third-party/actinium/LICENSE)). Actinium's own inventory, which also covers the renderer Demonica does not carry, is [`third-party/actinium/THIRD_PARTY_NOTICES.md`](third-party/actinium/THIRD_PARTY_NOTICES.md) | yes |
| GTNHLib, through S8TNLib | https://github.com/GTNewHorizons/GTNHLib, ported to 1.12.2 by Actinium and carried by S8TNLib up to 0.1.1 | `shader/src/main/java/com/demonica/compat/Mods.java`, derived from its `compat/Mods`. The rest of GTNHLib is S8TNLib's, installed separately (below) | GPL-3.0, as S8TNLib distributes it: GTNHLib's files are LGPL-3.0, and whether LGPL-3.0 or GPL-3.0 covers Actinium's changes is unstated | yes |
| ShadersMod | karyonix, sonic ether, id_miner, daxnitro | the notice at the top of [`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt) (relicensed under LGPL by the GTNH developers) | see that notice | yes |
| Mesa | https://gitlab.freedesktop.org/mesa/mesa | `glsm/.../glsm/DisplayListIDAllocator.java` (port of `util_idalloc`, Copyright 2017 Valve Corporation); the fixed-function shader generators in `glsm/.../glsm/ffp/` are inspired by Mesa | MIT, as in the file headers | yes |
| LWJGL utility code | https://github.com/LWJGL/lwjgl3 | `glsm/.../glsm/GLDebug.java` | BSD-style LWJGL license, as in the file header | yes |

## Installed separately

Demonica runs on Celeritas and S8TNLib and contains neither:
`verifyDistributedJar` fails the build if the mod jar holds a Celeritas class, a
renamed copy of one, JOML, Celeritas's assets, or S8TNLib's classes.

| Mod | Upstream | Terms | Relationship |
|---|---|---|---|
| Celeritas, the 1.12.2 mod `celeritas` | https://git.taumc.org/embeddedt/celeritas at `06999aab`, as built by kappa-maintainer/Celeritas-auto-build ([`docs/celeritas/PIN.md`](docs/celeritas/PIN.md)) | LGPL-3.0 (the `COPYING.LESSER` in its jar); the JOML it relocates is MIT | Required. Demonica compiles against the pinned jar and patches its classes at runtime through `mixins.demonica.celeritas.json` ([`docs/celeritas/LEDGER.md`](docs/celeritas/LEDGER.md)) |
| S8TNLib, the 1.12.2 mod `s8tnlib` | https://github.com/ndellagrotte/S8TNLib, the release `gradle.properties` pins by SHA-256 ([`docs/FORK.md`](docs/FORK.md#gtnhlib)): GTNHLib, ported to 1.12.2 by Actinium | GPL-3.0; its jar carries its license and notices, including ASM's for `asm/ClassConstantPoolParser` | Required. Demonica compiles against the pinned jar, and its GLSM redirector and mixins use GTNHLib's classes at runtime |

## Contained libraries

Packaged unmodified as nested jars in the mod jar, and listed in its manifest's
`ContainedDeps`:

| Artifact | License |
|---|---|
| `org.taumc:glsl-transformation-lib:0.2.0-32.g7dd88a4-GTNH` | not stated in its POM or jar; Angelica's credits list it as LGPL-3.0 (https://github.com/TauMC/glsl-transformation-lib) |
| `io.github.douira:glsl-transformer:3.0.0-pre3` | AGPL-3.0 (https://github.com/IrisShaders/glsl-transformer). The jar carries no license file; the AGPL-3.0 text ships as `LICENSE`. It contains a subset of Apache Commons Collections 4 (`org.apache.commons.collections4`, Apache-2.0), and its parser is generated from a grammar that extends GraphicsFuzz's (Apache-2.0); see [Notices](#glsl-transformer-agpl-30) |
| `org.antlr:antlr4-runtime:4.13.2` | BSD-3-Clause |
| `org.anarres:jcpp:1.4.14` | Apache-2.0 |

## Compile-only dependencies

These are resolved at build time, never bundled, and each is governed by its
own license:
- Minecraft 1.12.2 and Cleanroom, with the libraries they bring (log4j among
  them);
- LWJGL 3.4.1, lwjglx, JOML, fastutil, Gson, ASM, Lombok and the JetBrains
  annotations;
- the mods Demonica has compatibility code for: Distant Horizons, Botania,
  CoFH Core, Extra Utilities 2, iChunUtil, CreativeCore, LittleTiles,
  GregTech CEu, CodeChickenLib, Obscure Tooltips, OldResearchReborn, Scannable,
  VoxelMap, Fluidlogged API, RevoUI, the Component Model Hider, StellarCore and
  Gnetum (whose classes the HUD-cache transformers and their tests read);
- what only the tests use: JUnit, Guava and JourneyMap.

See `build.gradle` for the exact coordinates.

## In the jars

The mod jar, the diagnostics jar and the sources jar carry four files from the repository root:
`LICENSE` (the AGPL-3.0 text: Demonica's license and glsl-transformer's),
`LICENSE-GPL-3.0.txt` (the GPL-3.0 text, for the code ported from Actinium),
`LICENSE-LGPL-3.0.txt` (Angelica's license file: the ShadersMod notice, then
the LGPL-3.0 text) and this page, whose [Notices](#notices) reproduce
glsl-transformer's license statement and the MIT and BSD notices that binary
copies must carry: Mesa's and LWJGL's. `verifyDistributedJar` and
`verifyDiagnosticsJar` fail the build if any of the four files is missing from
their jar. This page's links do not resolve inside the jar; the notices below
are complete on their own.

## Notices

### glsl-transformer (AGPL-3.0)

`io.github.douira:glsl-transformer:3.0.0-pre3`, nested in the mod jar as
`glsl-transformer-3.0.0-pre3.jar`. Its repository,
https://github.com/IrisShaders/glsl-transformer (tag `v3.0.0-pre3`, retrieved
2026-09-28), states no copyright line: its `LICENSE` is the unmodified AGPL-3.0
text, the same file as Demonica's `LICENSE`, and douira's own Java sources
carry no headers (the bundled Commons Collections subset carries the ASF
Apache-2.0 header). Its README names the author: "`glsl-transformer` is
developed and maintained by [douira](https://github.com/douira)." The
README's license section, quoted from the same tag:

```text
`glsl-transformer` is licensed under the [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.en.html).

Software that uses this library must itself be licensed as AGPLv3. However, there are two special cases:

- Certain projects can receive a specific additional noncommercial permission that allows them to use this software without significantly reducing the requirements of the AGPLv3 for other unintended purposes.
- You can obtain a commercial license, which is entirely separate from the publicly granted AGPLv3 license. This license includes warranty and support.

Please contact the author (douira) in these cases.

In addition to the terms of the AGPLv3, contributors to this project must agree to license their work in such a way that these additional licenses may be granted.
```

Demonica uses it under the AGPL-3.0, with no additional permission. Two parts
of the library come from elsewhere, as its README and sources say:

- Its parser grammar, `GLSLParser.g4`, "extends the graphicsfuzz grammar" and
  keeps its header: "Copyright 2018 The GraphicsFuzz Project Authors",
  licensed under the Apache License, Version 2.0.
- "This project includes parts of Apache Commons Collections in its respective
  package. Not all source files have been included since only those related to
  `Trie` are needed. Apache Commons Collections is licensed under the
  [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)." Those
  classes are in the jar under `org/apache/commons/collections4/`; their
  license file is only in the library's sources jar.

### Mesa (MIT)

The header of `glsm/src/main/java/com/gtnewhorizons/angelica/glsm/DisplayListIDAllocator.java`,
a port of Mesa's `util_idalloc`:

```text
Copyright 2017 Valve Corporation
All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sub license, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice (including the
next paragraph) shall be included in all copies or substantial portions
of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS AND/OR ITS SUPPLIERS BE LIABLE FOR
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Original author: Samuel Pitoiset <samuel.pitoiset@gmail.com>
```

### LWJGL (BSD 3-Clause)

`glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GLDebug.java` carries
"Copyright LWJGL. All rights reserved. License terms:
https://www.lwjgl.org/license". That license, as
published in LWJGL's repository (`LICENSE.md` at commit `022178269d`,
retrieved 2026-09-24):

```text
Copyright (c) 2012-present Lightweight Java Game Library
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

- Neither the name Lightweight Java Game Library nor the names of
  its contributors may be used to endorse or promote products derived
  from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
