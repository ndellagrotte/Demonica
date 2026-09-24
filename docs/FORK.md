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
  `net.irisshaders`, `kroppeb`, `com.gtnewhorizons.angelica`,
  `me.flashyreese`. Asset namespaces (`iris`, `angelica`, `actinium`,
  `reeses-sodium-options`) stay.
- **Actinium's code enters by porting, not syncing.** Commits that port code
  from Actinium carry `Ported-From: actinium@4a19c959`, only so that `git log
  --grep=Ported-From` can trace where the code came from. Once ported, the
  code is Demonica's to change.
- **Celeritas is patched only through the quarantine.** Demonica may patch
  Celeritas internals only in `mixins.demonica.celeritas.json`. Each patch gets
  a ledger entry and an upstream PR draft
  ([`celeritas/LEDGER.md`](celeritas/LEDGER.md)), is guarded by the jar pin and
  the anchor audit, and fails soft.

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

GTNHLib is not in this repository. The mod jar merges the jar of
[S8TNLib](https://github.com/ndellagrotte/S8TNLib), which ports GTNHLib to
1.12.2 on Cleanroom and keeps the 60 of its files that Demonica reaches.
Those files are byte-identical in S8TNLib's `v0.1.0` and `v0.1.1`, and in
`GTNHLib/` at `61fa479d` (S8TNLib's tag `demonica-syncline/61fa479d`), which
is what `GTNHLib/` held when it was removed here. The 30 files that nothing in
Demonica reached are not in S8TNLib.

- `gradle.properties` pins the release and the commit its jar was built
  from, and `verifyS8tnlibPin` checks that commit in the jar's manifest.
- The jar is merged into the mod jar, and never goes on the dev client's
  class path: `verifyRunClasspath` checks that, and
  [`celeritas/SPIKE.md`](celeritas/SPIKE.md) ("Dev class loading") says why.
- A change to GTNHLib is made in S8TNLib, released there, and then pinned
  here. S8TNLib's `docs/HOST_CONTRACT.md` lists what Demonica supplies to
  it.

## History

The sync era is documented in [`PROVENANCE.md`](PROVENANCE.md) and its
manifests. `scripts/provenance_audit.py` and `scripts/test_port_scan.py` still
work against the tags `angelica-baseline/9fd02900ef`,
`actinium-syncline/fee5de38` and `actinium-fork-point/4a19c959`. They describe
how Demonica got here. They are not a rule for new changes.

## License

Actinium's root project is GPL-3.0, so porting it makes the Demonica jar a
GPL-3.0 combined work. On 2026-09-24 the maintainer settled on GPL-3.0 for
Demonica as a whole ([`LICENSE`](../LICENSE)). Angelica's LGPL-3.0 license file
stays as [`LICENSE-LGPL-3.0.txt`](../LICENSE-LGPL-3.0.txt): LGPL-3.0 is GPL-3.0
plus additional permissions, which section 7 of GPL-3.0 lets a redistributor
remove, and file headers still govern their files. The mod jar carries both
texts and [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md), whose Notices
section reproduces the MIT and BSD notices.
