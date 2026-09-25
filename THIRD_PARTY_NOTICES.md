# Third-Party Notices

Demonica is assembled from other projects' source. This page lists each one,
where it lives in this repository, its terms, and whether it ships in the mod
jar. Where a source file carries its own copyright or license header, that
header governs the file. This is an inventory, not legal advice.

**Demonica is GPL-3.0.** Angelica's code is LGPL-3.0 (its license file is
[`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt)), and the code ported from
Actinium's root project is GPL-3.0, so the jar that combines them is GPL-3.0 as
a whole. On 2026-09-24 the maintainer settled on GPL-3.0 for Demonica
([`LICENSE`](LICENSE)). That holds under either reading of the LGPL parts:
LGPL-3.0 is GPL-3.0 plus additional permissions, which section 7 of GPL-3.0
lets a redistributor remove. File headers still govern their files, and the
LGPL-3.0 text stays in the repository and in the jar
([`docs/FORK.md`](docs/FORK.md#license)).

## Source

| Component | Upstream | Paths in Demonica | Terms | In the mod jar |
|---|---|---|---|---|
| Angelica, including GLSM | https://github.com/GTNewHorizons/Angelica at `9fd02900ef` (tag `angelica-baseline/9fd02900ef`) | the repository's origin; GLSM in `glsm/`, `src/lwjglCommon/`, `src/lwjgl3/`; `com.gtnewhorizons.angelica.*` in `shader/` and in the root project, as Actinium adapted it (the batching font renderer among them); `src/main/resources/assets/angelica/**` | LGPL-3.0 ([`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt), Angelica's license file), plus file-level notices | yes |
| Iris | https://github.com/IrisShaders/Iris, via Angelica's backport | `shader/src/main/java/{net/coderbot, kroppeb, net/irisshaders}`, `src/main/java/net/irisshaders/iris/compat/`, `src/main/resources/assets/iris/**`. Some files are based on Sodium's, as their headers say | LGPL-3.0 | yes |
| Actinium | https://github.com/DHJComical/Actinium, synced until `fee5de38` (tag `actinium-syncline/fee5de38`) and forked at `4a19c959` (tag `actinium-fork-point/4a19c959`, [`docs/FORK.md`](docs/FORK.md)) | its 1.12.2 adaptation of all of the above; its root project, ported to the root `src/main/java` (`com.dhj.actinium` became `com.demonica`); `src/main/resources/assets/actinium/**`; the ported tests. The Gradle build is derived from Actinium's | GPL-3.0 ([`third-party/actinium/LICENSE`](third-party/actinium/LICENSE)). Actinium's own inventory, which also covers the renderer Demonica does not carry, is [`third-party/actinium/THIRD_PARTY_NOTICES.md`](third-party/actinium/THIRD_PARTY_NOTICES.md) | yes |
| GTNHLib, through S8TNLib | https://github.com/GTNewHorizons/GTNHLib, ported to 1.12.2 by Actinium. S8TNLib (https://github.com/ndellagrotte/S8TNLib) publishes the 60 files Demonica uses as `com.s8tnlib:s8tnlib`, at the release `gradle.properties` pins | `shader/src/main/java/com/demonica/compat/Mods.java` and `src/main/java/com/demonica/render/font/IFontParameters.java`, derived from its `compat/Mods` and `util/font/IFontParameters`; otherwise none: the build merges S8TNLib's jar into the mod jar | GPL-3.0, as S8TNLib distributes it: GTNHLib's files are LGPL-3.0, and whether LGPL-3.0 or GPL-3.0 covers Actinium's changes is unstated. Plus file-level notices: the `bytebuf/` sources carry LWJGL's, `asm/ClassConstantPoolParser` carries ASM's | yes |
| Reese's Sodium Options | https://github.com/FlashyReese/reeses-sodium-options, via Actinium's 1.12.2 port | `src/main/java/me/flashyreese/**`, `src/main/java/com/demonica/gui/rso/compat/`, `src/main/resources/assets/reeses-sodium-options/**` | MIT ([`third-party/reeses-sodium-options/LICENSE.md`](third-party/reeses-sodium-options/LICENSE.md)) | yes |
| ShadersMod | karyonix, sonic ether, id_miner, daxnitro | the notice at the top of [`LICENSE-LGPL-3.0.txt`](LICENSE-LGPL-3.0.txt) (relicensed under LGPL by the GTNH developers) | see that notice | yes |
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

## In the mod jar

The mod jar and the sources jar carry three files from the repository root:
`LICENSE` (the GPL-3.0 text), `LICENSE-LGPL-3.0.txt` (Angelica's license file:
the ShadersMod notice, then the LGPL-3.0 text) and this page, whose
[Notices](#notices) reproduce the MIT and BSD notices that binary copies must
carry: Reese's Sodium Options', Mesa's, LWJGL's and ASM's. `verifyDistributedJar`
fails the build if any of the three files is missing. This page's links do not
resolve inside the jar; the notices below are complete on their own.

## Notices

### Reese's Sodium Options (MIT)

From [`third-party/reeses-sodium-options/LICENSE.md`](third-party/reeses-sodium-options/LICENSE.md):

```text
The MIT License (MIT)

Copyright (c) 2021 FlashyReese

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

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

`glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GLDebug.java` and the nine
`bytebuf/` files that S8TNLib's jar brings carry "Copyright LWJGL. All rights
reserved. License terms: https://www.lwjgl.org/license". That license, as
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

### ASM (BSD 3-Clause)

The header of S8TNLib's `asm/ClassConstantPoolParser.java`, which is derived
from ASM's `ClassReader`:

```text
ASM: a very small and fast Java bytecode manipulation framework Copyright (c)
2000-2011 INRIA, France Telecom All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met: 1.
Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer. 2. Redistributions in binary
form must reproduce the above copyright notice, this list of conditions and
the following disclaimer in the documentation and/or other materials provided
with the distribution. 3. Neither the name of the copyright holders nor the
names of its contributors may be used to endorse or promote products derived
from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
