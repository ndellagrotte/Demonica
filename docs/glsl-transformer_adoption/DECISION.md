# Decision record: adopting glsl-transformer, and AGPL-3.0

Decided by the maintainer on 2026-09-25. Carried out on 2026-09-28, in Step 1 of
[the adoption plan](ADOPTION_PLAN.md), on the branch `feat/glsl-transformer`
before the library could reach a jar anyone distributes. This is a record of
the reasoning, not legal advice.

Done on 2026-09-29: Step 11 (commit `06895328`) removed TauMC's
glsl-transformation-lib, so the migration this record decided is complete on
the branch (exit point B); Step 12 is optional.

## The three decisions

| Decision | Choice |
|---|---|
| License | Demonica's own code is relicensed to AGPL-3.0. Code ported from Angelica and Iris stays LGPL-3.0; code ported from Actinium, and the GTNHLib port in S8TNLib, stay GPL-3.0. Section 13 of GPL-3.0 and section 13 of AGPL-3.0 permit the combination. |
| Strategy | Adapter first. One class, `ShaderAst`, re-implements the nineteen verbs Demonica calls on TauMC's `Transformer` on top of glsl-transformer's AST, and is proven equal to TauMC verb by verb while both libraries are on the classpath. The transformer classes then port mechanically. Upstream Iris idioms may be used next to the verbs wherever a file is being brought closer to Iris. |
| Scope | Full removal. The Iris pipeline and GLSM's `CompatShaderTransformer` both move to glsl-transformer; TauMC's library and its ANTLR parser leave the jar at the end. The unreachable SPIR-V/GLES path was deleted on `dev` at `df08c785` and is not ported. |

## Why glsl-transformer

Upstream Iris's transform code is written against
`io.github.douira:glsl-transformer` (3.0.0-pre3 in Iris 26.1), so its fixes port
directly instead of being translated between two parse-tree APIs.
glsl-transformer is a documented AST with identifier and node indexes, matchers
and templates. TauMC's `org.taumc:glsl-transformation-lib` is a thin,
undocumented layer over an ANTLR parse tree, with no sources jar.

Angelica left glsl-transformer in October 2024 (GTNewHorizons/Angelica#680,
"Conversion to glsl-transformation-lib"). The PR gives no reason. The evidence
points to the license: Angelica, an LGPL-3.0 mod, used glsl-transformer 1.0.0
under a project-specific grant (its build line carried the comment
`// glsl-transformer Noncommercial License 1.0.0`), and the replacement was
written as an LGPL-3.0 library by embeddedt and ferriarnus, with Angelica's
maintainer contributing its Java 8 fixes. A second likely factor: later
glsl-transformer versions need a newer Java than the Java 8 Angelica targeted.
Neither constraint applies to Demonica, which compiles for release 21
(glsl-transformer 3.0.0-pre3's Gradle metadata asks for JVM 21).

The accepted costs: the AGPL-3.0 license, and the transform layer no longer
being shared with Angelica, Actinium and Celeritas, which use TauMC's library.
Demonica stopped syncing Actinium at the fork point (`4a19c959`), so the second
cost falls mostly on shader-transform patches from Angelica.

## The license options

glsl-transformer has been AGPL-3.0 since 2.0.0. Checked on 2026-09-28: its POM
names LGPL-3.0 at 1.0.1 and AGPL-3.0 at 2.0.0, 2.0.2 and 3.0.0-pre3, and its
repository's `LICENSE` is the AGPL-3.0 text at the tags v1.0.1, v2.0.0, v2.0.2
and v3.0.0-pre3. Its README adds a condition of the author's: "Software that uses this
library must itself be licensed as AGPLv3", with two exceptions granted on
request, a noncommercial permission for certain projects and a commercial
license.

1. **Keep GPL-3.0 and rely on section 13.** GPL-3.0 section 13 lets a covered
   work be combined with an AGPL-3.0 work into one combined work and conveyed;
   the GPL-3.0 part stays GPL-3.0 and AGPL-3.0's network-interaction
   requirement applies to the combination. The license texts alone would
   allow this. It does not meet the README's condition, so it would leave
   Demonica in open disagreement with the author's stated terms.
2. **Relicense Demonica's own code to AGPL-3.0.** Meets the README's condition
   without asking anyone, and the maintainer, the only author of Demonica's own
   code, can make the change alone. The ported code cannot be relicensed and
   does not need to be: section 13 of both licenses covers the combination.
3. **Ask douira for a noncommercial grant**, as Angelica had. It depends on the
   author's answer and timing, would bind Demonica to terms negotiated for it
   alone, and would not pass to forks. Angelica's grant was Angelica's and does
   not transfer.

**Chosen: option 2.** The maintainer does not mind AGPL-3.0, it needs no one
else's permission, and it is the reading of the library's terms that leaves
nothing to argue about. The combination argument of option 1 still carries the
ported GPL-3.0 and LGPL-3.0 code, so option 2 costs nothing for those parts.

## What the relicensing covers

It covers code whose copyright is Demonica's: the files written for Demonica,
and Demonica's changes to ported files. It does not cover code ported from
elsewhere, which keeps its license:

- Angelica, including GLSM, and Iris (through Angelica's backport): LGPL-3.0.
- Actinium's root project, ported into `com.demonica` (Actinium's
  `com.dhj.actinium`): GPL-3.0. The package name is therefore not a test of
  authorship; [`../PROVENANCE.md`](../PROVENANCE.md), its manifests and the
  tag `actinium-fork-point/4a19c959` show which files came from Actinium.
- `com.demonica.compat.Mods`, derived from GTNHLib's `compat/Mods`: GPL-3.0.
- Files whose headers name another license or another copyright holder. At
  2026-09-28, `grep -rln "Copyright\|Licensed under\|SPDX" src shader glsm --include=*.java`
  finds two: `glsm/.../glsm/DisplayListIDAllocator.java` ("Copyright 2017 Valve
  Corporation", MIT, from Mesa) and `glsm/.../glsm/GLDebug.java` ("Copyright
  LWJGL. All rights reserved.", LWJGL's BSD license). A wider search for
  license names finds seven more, all naming LGPL for code from Sodium or
  Canvas: `shader/.../iris/gl/shader/{ProgramCreator, GlShader, ShaderType}.java`,
  `shader/.../iris/shaderpack/preprocessor/{JcppProcessor, PropertiesPreprocessor}.java`,
  `shader/.../iris/vertices/NormI8.java` and `glsm/.../glsm/shader/ShaderType.java`.
- The contained libraries (glsl-transformer AGPL-3.0, ANTLR's runtime
  BSD-3-Clause, jcpp Apache-2.0; glsl-transformation-lib until Step 11) and the separately installed
  mods (Celeritas LGPL-3.0, S8TNLib GPL-3.0), as
  [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md) lists them.

Releases up to 0.4.0 were distributed under GPL-3.0 and stay so.

## Files

The repository and every jar carry four files: `LICENSE` (AGPL-3.0, fetched
from https://www.gnu.org/licenses/agpl-3.0.txt; byte-identical to
glsl-transformer's own `LICENSE`), `LICENSE-GPL-3.0.txt` (the former
`LICENSE`, moved with `git mv`), `LICENSE-LGPL-3.0.txt` (unchanged) and
`THIRD_PARTY_NOTICES.md`, whose notices now include glsl-transformer's license
statement. `verifyDistributedJar` and `verifyDiagnosticsJar` require all four.

## Obligations that follow

- **Source for the distributed jar.** Whoever conveys the jar must offer its
  Corresponding Source under AGPL-3.0 (and GPL-3.0/LGPL-3.0 for those parts), as
  before: the public repository at the released commit and the sources jar
  published with each release (v0.4.0 carries `Demonica-0.4.0-sources.jar`). The source of the contained libraries is their
  published sources (glsl-transformer's sources jar on Maven Central, its
  repository at the tag `v3.0.0-pre3`).
- **The network clause.** AGPL-3.0 section 13 requires offering the source to
  users who interact with a modified version over a network. Demonica is a
  client-side mod that serves nothing over a network, so the clause has no
  practical effect; a modified build offered as a network service would carry it.
- **Notices.** The four files above must stay in every jar; the build checks it.

## The author's condition

glsl-transformer's README asks that "Software that uses this library must
itself be licensed as AGPLv3". With Demonica's own code under AGPL-3.0, that
condition is met without a grant, and Demonica uses the library under the
public AGPL-3.0 license alone.
