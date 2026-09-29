# Demonica forks from Actinium

Demonica was synced from [Actinium](https://github.com/DHJComical/Actinium)
until `actinium@4a19c95952cb9710211d29bec3440e752b6a2d03` (tag
`actinium-fork-point/4a19c959`). At that commit, every synced scope matched
Actinium file for file. `provenance_audit.py --expect-identical` found 484
Iris-tree, 223 GLSM, 90 GTNHLib, 314 celeritas-common, 8 resource and 74 test
files verbatim.

From the fork point on:
- **Demonica owns its code.** Changes land here directly, not in Actinium
  first. Actinium is no longer synced.
- **Demonica becomes the mod.** It takes over Actinium's role as a shader mod
  for 1.12.2 on Cleanroom. Instead of vendoring a renderer, it runs on the
  separately installed upstream Celeritas mod (mod id `celeritas`), pinned in
  [`celeritas/PIN.md`](celeritas/PIN.md).
- **Packages may be renamed.** Actinium's own `com.dhj.actinium` code becomes
  `com.demonica`. Upstream-derived packages keep their names: `net.coderbot`,
  `net.irisshaders`, `kroppeb`, `com.gtnewhorizons.angelica`. Asset namespaces
  (`iris`, `angelica`, `actinium`) stay.
- **Actinium's code enters by porting, not syncing.** Commits that port code
  from Actinium carry `Ported-From: actinium@4a19c959`, only so that `git log
  --grep=Ported-From` can trace where the code came from. Once ported, the
  code is Demonica's to change.
- **Celeritas is patched only through the quarantine.** Demonica may patch
  Celeritas internals only in `mixins.demonica.celeritas.json`. Each patch gets
  a ledger entry and an upstream PR draft
  ([`celeritas/LEDGER.md`](celeritas/LEDGER.md)), is guarded by the jar pin, and
  fails soft.

## Identity

| | Actinium | Demonica |
|---|---|---|
| Mod id | `actinium` | `demonica` |
| Root package | `com.dhj.actinium` | `com.demonica` |
| Options file | `config/actinium-options.json` | `config/demonica-options.json`, migrated once from `actinium-options.json` |
| Terrain renderer | vendored Celeritas fork (`dhj.embeddedt.*`) | upstream Celeritas, installed separately |

Demonica refuses to start alongside Actinium and says why. Both mods patch the
same classes, and Actinium carries a second renderer.

## GTNHLib

GTNHLib is not in this repository. The mod jar merges the GTNHLib classes of
[S8TNLib](https://github.com/ndellagrotte/S8TNLib)'s jar, which ports GTNHLib
to 1.12.2 on Cleanroom and keeps the files Demonica reaches.
Up to S8TNLib's `v0.1.1`, its 60 files were byte-identical to `GTNHLib/` at
`61fa479d` (S8TNLib's tag `demonica-syncline/61fa479d`), which is what
`GTNHLib/` held when it was removed here. From `v0.2.0` on, S8TNLib changes
on its own: 0.2.0 fixed the `PointerBuffer` path of `bytebuf`, dropped its
Java 8 branches, freed a thread's capture buffers once the thread ends, and
no longer carried the two moved types. 0.3.0 drops `bytebuf` for LWJGL's own
`org.lwjgl.system` (Cleanroom 0.6.12 ships LWJGL 3.4.1), which Demonica now
imports directly, and makes the jar a mod, so 47 files remain. From
Demonica 0.3.0 to 0.5.0 players installed S8TNLib as a separate mod, pinned
by its jar's SHA-256; since 0.6.0 its GTNHLib classes are merged into
Demonica's jar again, as they were up to 0.2.0.

- **The latest release, unpinned.** Each build asks GitHub's API for
  S8TNLib's latest release (`releases/latest`, which skips drafts and
  prereleases; `GITHUB_TOKEN`, when set, authenticates the call) and
  downloads `s8tnlib-<version>.jar` and its sources jar from the release's
  assets. The answer is cached in `.gradle/demonica/s8tnlib-latest`, which
  `--offline` builds and builds that cannot reach GitHub use. So a build
  takes a new S8TNLib release as soon as it is published, and two builds of
  one commit can differ: the mod jar's manifest records `S8TNLib-Version`
  and `S8TNLib-Commit` (the commit S8TNLib's jar was built from), and
  `verifyDistributedJar` requires both. `-Ps8tnlibDir=<dir>` takes the jar
  from a local directory instead, such as S8TNLib's `build/libs`, with the
  version of the `s8tnlib-<version>.jar` there (`-Ps8tnlibVersion=<version>`
  chooses one when there are several).
- **Merging.** The merge takes Unimined's MCP remap of the release jar (it
  is `modCompileOnly`), so the classes join `:glsm`'s and `:shader`'s in
  `mergeEmbeddedLibraryClasses`, dev runs see them in MCP names, and
  `remapJar` maps them to SRG with the rest. It leaves out what makes
  S8TNLib a mod of its own: `com/s8tnlib/**` (its `@Mod` and no-op
  coremod), its `mcmod.info`, manifest, `LICENSE` and notices. Demonica's
  coremod needs GTNHLib's classes while coremods load (`GLSMRedirector`,
  `MixinTessellator`, `AngelicaLateTweaker`), which being in Demonica's own
  jar guarantees.
- **A leftover S8TNLib jar.** A player coming from 0.3.0 to 0.5.0 may still
  have `s8tnlib-<version>.jar` in `mods/`. It is harmless while its GTNHLib
  classes match Demonica's, and wrong once they differ, so `MixinEarly` logs
  a warning naming it (`Environment.staleS8tnlibJar`, which looks for
  S8TNLib's `@Mod` class).
- **Dev runs** load the merged classes through `-Dcrl.dev.extrapath` with
  the rest of the mod classes. S8TNLib's jar is on neither the dev client's
  class path nor the extrapath: `verifyRunClasspath` checks that, and
  [`celeritas/SPIKE.md`](celeritas/SPIKE.md) ("Dev class loading") says why.
- A change to GTNHLib is made in S8TNLib and released there; the next
  Demonica build picks it up. S8TNLib's `docs/HOST_CONTRACT.md` lists what
  Demonica supplies to it.
- Two of the 60 served only Demonica, and are Demonica's own since the pin
  moved to 0.2.0, with only their package lines changed: `compat/Mods` is
  `com.demonica.compat.Mods` in `:shader`, and `util/font/IFontParameters`
  was `com.demonica.render.font.IFontParameters` until the batching font
  renderer that implemented it was dropped in 0.3.0.

## History

The sync era is documented in [`PROVENANCE.md`](PROVENANCE.md) and its
manifests. `scripts/provenance_audit.py` and `scripts/test_port_scan.py` still
work against the tags `angelica-baseline/9fd02900ef`,
`actinium-syncline/fee5de38` and `actinium-fork-point/4a19c959`. They describe
how Demonica got here. They are not a rule for new changes.

## License

Actinium's root project is GPL-3.0, so porting it makes the Demonica jar a
GPL-3.0 combined work. On 2026-09-24 the maintainer settled on GPL-3.0 for
Demonica as a whole (the text is now
[`LICENSE-GPL-3.0.txt`](../LICENSE-GPL-3.0.txt)). On 2026-09-28, before
glsl-transformer (AGPL-3.0) entered the jar, Demonica's own code was relicensed
to AGPL-3.0 ([`LICENSE`](../LICENSE),
[decision record](glsl-transformer_adoption/DECISION.md)); the ported code keeps
GPL-3.0 or LGPL-3.0, and section 13 of GPL-3.0 and of AGPL-3.0 lets the parts
combine. Angelica's LGPL-3.0 license file
stays as [`LICENSE-LGPL-3.0.txt`](../LICENSE-LGPL-3.0.txt): LGPL-3.0 is GPL-3.0
plus additional permissions, which section 7 of GPL-3.0 lets a redistributor
remove, and file headers still govern their files. The mod jar carries all
three texts and [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md), whose Notices
section reproduces glsl-transformer's license statement and the MIT and BSD
notices.
