# Demonica Provenance

> **Historical.** This page describes the sync era, which ended at the fork point
> `actinium@4a19c959` (tag `actinium-fork-point/4a19c959`). Demonica now owns its
> code and is no longer synced from Actinium; see [`FORK.md`](FORK.md). The rules
> below (verbatim syncs, no renames, the vendor policy) no longer apply to new
> changes.

Demonica is a fork of [Angelica](https://github.com/GTNewHorizons/Angelica) at
commit `9fd02900ef5b322254cb15e1a24d62ade8de97cf` (tag `angelica-baseline/9fd02900ef`).
It carries the 1.12.2/Cleanroom Iris adaptation developed in
[Actinium](https://github.com/DHJComical/Actinium), absorbed as a first-class,
audited project instead of an untracked vendored tree.

Current syncline: `actinium@fee5de3834352050fc453bde1d93030c6d434983`
(tag `actinium-syncline/fee5de38`). Every synced file is byte-identical to it.

## Demonica is an engine library

Demonica publishes the Iris shader pipeline and GLSM as MCP-named library jars
for 1.12.2/Cleanroom. It is not a mod: it has no mixins, no mod metadata and no
terrain renderer.

This was decided during the fee5de38 sync. On 1.12.2, Iris cannot run without
Actinium's Celeritas terrain renderer. Its init hooks, main-pass terrain
(`@Overwrite renderBlockLayer`) and shadow terrain drawing
(`WorldRendererCompatBridge`) all live in Actinium's vintage mixins and renderer.
Porting `mixins.actinium.iris.json` would pull in 73 host classes (~8.5k lines)
and still not run. So the mixins and the host glue stay in the host mod, and
`HOST_CONTRACT.md` listed everything a host had to supply (removed after the fork; see the tag `actinium-fork-point/4a19c959`).

## Path map

| Actinium @ fee5de38 | Demonica | Gradle project → artifact | Files |
|---|---|---|---|
| `shader/src/main/java` | `src/main/java` | `:` → `com.demonica:demonica` | 484 |
| `glsm/src/main/java`, `src/lwjglCommon/java`, `src/lwjgl3/java` | same paths | `:glsm` → `com.demonica:demonica-glsm` | 222 |
| `src/main/resources/META-INF/services/…glsm.backend.RenderBackend` | `glsm/src/main/resources/META-INF/services/…` | `:glsm` | 1 |
| `src/main/resources/{assets/iris/**, assets/angelica/shaders/centerDepth.*, assets/actinium/shaders/include/chunk_vertex.glsl}` | same paths | `:` | 8 |
| `GTNHLib/src/main/java` | `vendor/GTNHLib/src/main/java` | `:GTNHLib` (never published) | 90 |
| `celeritas-common/src/main/java` | `vendor/celeritas-common/src/main/java` | `:celeritas-common` (never published) | 314 |
| `src/test/java/{net/coderbot, com/gtnewhorizons/angelica/glsm, net/minecraft}`, `src/test/resources/compat_shaders` | same paths | `:` tests | 74 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | same paths | — | 4 |

Demonica's own files: the Gradle build (derived from Actinium's, see the header
of `build.gradle`), `META-INF/demonica_at.cfg` (19 lines of `actinium_at.cfg`),
`scripts/`, `docs/` and `vendor/README.md`.

## Code classes

The audit tool classifies every file in a scope by git blob SHA against a baseline:

| class | meaning |
|---|---|
| **verbatim** | byte-identical to the baseline |
| **adapted** | in both trees, content differs; arrives only through `Ported-From:` commits |
| **new** | only in the compared tree |
| **dropped** | only in the baseline |

## Scopes and audits

`scripts/provenance_audit.py` knows where each scope lives in three layouts:
`angelica` (the baseline, iris and glsm only), `actinium` and `demonica`. Files
are keyed relative to their source root, so the same file matches across
prefixes.

| scope | Demonica roots | files |
|---|---|---|
| `iris` | `src/main/java` | 484 |
| `glsm` | `glsm/src/main/java`, `src/lwjglCommon/java`, `src/lwjgl3/java`, the RenderBackend service file | 223 |
| `gtnhlib` | `vendor/GTNHLib/src/main/java` | 90 |
| `celeritas-common` | `vendor/celeritas-common/src/main/java` | 314 |
| `resources` | `src/main/resources/{assets/iris, assets/angelica/shaders/centerDepth.*, assets/actinium/shaders/include/chunk_vertex.glsl}` | 8 |
| `tests` | `src/test/java`, `src/test/resources` | 74 |

```sh
ACT=fee5de3834352050fc453bde1d93030c6d434983

# Demonica is exactly the syncline (every scope 100% verbatim):
scripts/provenance_audit.py --scope all --a-ref $ACT --a-layout actinium --expect-identical

# Same, against staged files, before committing a sync:
scripts/provenance_audit.py --scope all --a-ref $ACT --a-layout actinium --b-ref INDEX --expect-identical

# What Actinium changed relative to the Angelica baseline:
scripts/provenance_audit.py --scope iris --b-ref $ACT --b-layout actinium
scripts/provenance_audit.py --scope glsm --b-ref $ACT --b-layout actinium

# Which Actinium tests can be ported verbatim, and why the others cannot:
scripts/test_port_scan.py
```

Manifests for the fee5de38 syncline live in
[`provenance/actinium-fee5de38/`](provenance/actinium-fee5de38/):

| manifest | compares | result |
|---|---|---|
| [`iris.md`](provenance/actinium-fee5de38/iris.md) | baseline → Actinium, `iris` | 298 verbatim / 135 adapted / 51 new / 16 dropped |
| [`glsm.md`](provenance/actinium-fee5de38/glsm.md) | baseline → Actinium, `glsm` | 61 / 79 / 83 / 2 |
| [`exactness.md`](provenance/actinium-fee5de38/exactness.md) | Actinium → Demonica, all scopes | 484, 223, 90, 314, 8, 74 verbatim; nothing else |
| [`tests.md`](provenance/actinium-fee5de38/tests.md) | test-port scan | 72 + 2 ported, 83 skipped with reasons |

## Conventions

- Sync commits carry trailers: `Ported-From: actinium@<sha>`, `Baseline:
  angelica-baseline/9fd02900ef` where the baseline is diffed, and
  `Upstream-From: angelica@<sha>` for Angelica cherry-picks. Query with
  `git log --grep=Ported-From`.
- Stage, audit against `INDEX` with `--expect-identical`, then commit. Nothing
  is committed until the audit shows it is exact.
- **Never rename upstream packages** (`net.coderbot.*`, `kroppeb.*`,
  `com.gtnewhorizons.angelica.*`, `com.mitchej123.*`). Blob identity with
  upstream is how provenance is checked, and renaming destroys it.
- Demonica-original code goes under `com.demonica.*`.
- Syncs land on `sync/*` branches. `dev` only takes buildable states. A green
  sync is merged to `dev` and tagged `actinium-syncline/<sha>`, and `main` tags
  blessed sync points.

## Vendor policy

> After the sync era, GTNHLib moved from `vendor/GTNHLib` to the `:GTNHLib`
> project (`3d0db995`), and then out of this repository: the mod jar merges
> S8TNLib's jar ([`FORK.md`](FORK.md#gtnhlib)). The path map and the
> `gtnhlib` scope above describe the sync era.

`vendor/` holds Actinium's GTNHLib and celeritas-common forks, which Demonica
compiles against:

- **Verbatim.** Nothing there is edited in Demonica. Fixes go to Actinium and
  arrive with the next sync.
- **compileOnly, never shipped.** The host supplies both at runtime.
  `verifyPublishedJars` fails the build if their classes, or a POM or module
  dependency on them, reach a published artifact.
- **Temporary.** They will be replaced by upstream artifacts once Celeritas is
  un-vendored.

See [`vendor/README.md`](../vendor/README.md).

## Tests

Ported tests are verbatim or skipped with a recorded reason
([`tests.md`](provenance/actinium-fee5de38/tests.md)). A failing ported test is
never edited. It is reproduced in Actinium at the syncline, and if the failure
is environmental, it is excluded with the reason recorded.

## Out of scope

- **The Iris mixins** (`mixins.actinium.iris.json`, `features.iris.*`,
  `core.terrain`, `core.vertex`, `IrisMixinConfigPlugin`) are owned by the host,
  by decision. See `HOST_CONTRACT.md` at the tag `actinium-fork-point/4a19c959`.
- **Actinium's 33 root `com.gtnewhorizons.angelica.*` classes**: the redirector
  tweaker and transformers, `IrisGLSMBridge`, the font renderer and the mixin
  interfaces. They are host glue, and 5 of them import `com.dhj.actinium`.
- **The skipped tests**, listed in `tests.md`.
- **`src/main/resources/LICENSE`.** Actinium replaced Angelica's ShadersMod
  relicensing notice with its GPL-3.0 text. Demonica kept Angelica's file
  until the license was settled (below), so it was not part of the
  `resources` scope. Decided with 0.1.0: the file is gone, and the jar carries
  the root `LICENSE`, `LICENSE-LGPL-3.0.txt` and `THIRD_PARTY_NOTICES.md`
  (see [License](#license)).
- **The next sync**: Actinium `d3fb124c` and `653c862b` (the MC_VERSION
  rewrite and per-buffer blend), followed by the seven Angelica cherry-picks.

## License

Actinium's root `LICENSE` is GPL-3.0, while Demonica's was Angelica's LGPL-3.0.
Demonica carries Actinium-authored changes from this sync and the previous one.
Before anything was published beyond `mavenLocal`, that needed a decision:
either GPL-3.0 for the combined work, or permission to relicense.

Decided on 2026-09-24, with the 0.1.0 release: GPL-3.0 for the combined work.
The root `LICENSE` is the GPL-3.0 text, Angelica's file is
`LICENSE-LGPL-3.0.txt`, and the mod jar carries both with
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md). See
[`FORK.md`](FORK.md#license).
