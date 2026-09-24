# Vendored compile-time dependencies

This directory is being emptied. It will be deleted when Demonica becomes a mod
(the plan's Phase 3).

| Source | Path | Status |
|---|---|---|
| `actinium@4a19c959:GTNHLib/src/main/java` | `vendor/GTNHLib/src/main/java` (90 files) | Moves to `GTNHLib/`. Demonica owns it from the fork point on (`docs/FORK.md`). GTNHLib: LGPL-3.0, plus file-level notices (LWJGL-derived `bytebuf/` code carries the LWJGL BSD-style notice) |
| `actinium@4a19c959:celeritas-common/src/main/java` | removed | Replaced by upstream Celeritas, pinned in `docs/celeritas/PIN.md`. Actinium-only seam types now live in `com.demonica.celeritas.api` |
| `actinium@fee5de38:LICENSE` | `vendor/ACTINIUM-LICENSE.txt` | Moves to `third-party/actinium/`. Actinium's root license (GPL-3.0) |
| `actinium@fee5de38:THIRD_PARTY_NOTICES.md` | `vendor/ACTINIUM-THIRD_PARTY_NOTICES.md` | Moves to `third-party/actinium/`. Actinium's inventory of the upstream projects it embeds |
