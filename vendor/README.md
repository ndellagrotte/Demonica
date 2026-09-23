# Vendored compile-time dependencies

The Iris tree and GLSM compile against Actinium's forks of GTNHLib and
celeritas-common. Neither fork has a published artifact that Demonica can use:
the published GTNHLib is a 1.7.10 mod, and the GTNH celeritas-common jar uses
the `org.embeddedt` package and lacks 10 of the classes the tree imports.
Both are therefore vendored here, verbatim, from the Actinium syncline.

| Source | Path | Files | License |
|---|---|---|---|
| `actinium@fee5de38:GTNHLib/src/main/java` | `vendor/GTNHLib/src/main/java` | 90 | GTNHLib: LGPL-3.0, plus file-level notices (LWJGL-derived `bytebuf/` code carries the LWJGL BSD-style notice) |
| `actinium@fee5de38:celeritas-common/src/main/java` | `vendor/celeritas-common/src/main/java` | 314 (313 Java + `grondag/bitraster/LICENSE`) | Celeritas/Embeddium/Sodium-derived, mixed provenance: file-level notices take precedence. `grondag/bitraster` is Apache-2.0 (its `LICENSE`); the Canvas-derived `occlusion/geometry` sources carry LGPL-3.0 headers |
| `actinium@fee5de38:LICENSE` | `vendor/ACTINIUM-LICENSE.txt` | 1 | Actinium's root license (GPL-3.0), which covers Actinium's own changes to these trees |
| `actinium@fee5de38:THIRD_PARTY_NOTICES.md` | `vendor/ACTINIUM-THIRD_PARTY_NOTICES.md` | 1 | Actinium's inventory of the upstream projects it embeds |

`fee5de38` is `fee5de3834352050fc453bde1d93030c6d434983`.

## Policy

- **Verbatim.** Every file is byte-identical to the Actinium syncline. Nothing
  here is edited in Demonica: fixes go to Actinium and arrive with the next sync.
- **compileOnly, never shipped.** The `:GTNHLib` and `:celeritas-common` Gradle
  projects are compile-time dependencies of Demonica and are never published.
  The host mod supplies both at runtime (see `docs/HOST_CONTRACT.md`).
  `verifyPublishedJars` fails the build if any of their classes or a POM
  dependency on them leaks into a published artifact.
- **Temporary.** Once Celeritas is un-vendored upstream, these trees are
  replaced by the upstream artifacts.

## Audit

```sh
ACT=fee5de3834352050fc453bde1d93030c6d434983
scripts/provenance_audit.py --scope gtnhlib --a-ref $ACT --a-layout actinium --expect-identical
scripts/provenance_audit.py --scope celeritas-common --a-ref $ACT --a-layout actinium --expect-identical
```
