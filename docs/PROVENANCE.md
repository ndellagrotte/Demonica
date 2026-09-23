# Demonica Provenance

Demonica is a fork of [Angelica](https://github.com/GTNewHorizons/Angelica)
at commit `9fd02900ef5b322254cb15e1a24d62ade8de97cf` (tag:
`angelica-baseline/9fd02900ef`), carrying the 1.12.2/Cleanroom Iris
adaptation developed in Actinium, re-absorbed as a first-class project
instead of an untracked vendored tree.

## Code classes

Every file under the audited paths (`net/coderbot`, `kroppeb`,
`net/irisshaders`) is exactly one of:

| class | meaning | how to verify |
|---|---|---|
| **verbatim** | byte-identical to Angelica at the baseline tag | `git diff angelica-baseline/9fd02900ef -- <path>` is empty |
| **adapted** | version-ported or modified for 1.12.2/Celeritas | differs from baseline; arrives only via `Ported-From:` commits |
| **new** | written downstream (Actinium or Demonica) | absent from baseline; arrives via `Ported-From:` or feature commits |

The audit tool classifies mechanically by git blob SHA:

```sh
scripts/provenance_audit.py --write docs/PROVENANCE.manifest.md
```

Re-run it after every sync and commit the manifest next to the sync.

## Conventions

- Sync commits carry trailers: `Ported-From: actinium@<sha>` /
  `Upstream-From: angelica@<sha>`. Query with `git log --grep=Ported-From`.
- **Never rename upstream packages** (`net.coderbot.*`, `kroppeb.*`,
  `com.gtnewhorizons.angelica.*`). Blob identity against upstream is the
  provenance oracle; renaming destroys it.
- Demonica-original code goes under `com.demonica.*`.
- Syncs land on `sync/*` branches; `dev` merges only buildable states;
  `main` tags blessed sync points.

## Current state (sync/actinium-fee5de38)

- Java tree over audited paths is blob-identical to
  `actinium@fee5de3834352050fc453bde1d93030c6d434983`
  (`shader/src/main/java` → `src/main/java` path mapping applied).
- Manifest at sync time: 297 verbatim / 132 adapted / 50 new / 16 dropped
  (`docs/provenance/actinium-fee5de38/iris.md`).
- Celeritas bridge included; Celeritas is a compileOnly dependency.

## Deliberately deferred

- **Iris mixins** (`mixins.actinium.iris.json` + 37
  `features.iris.*` classes + `core.terrain`/`core.vertex` +
  `IrisMixinConfigPlugin`): these import 16 Actinium root-project classes
  (`com.dhj.actinium.render.*`, `config.ActiniumRuntimeOptions`, per-mod
  compat). They are the engine's attachment points to the host mod and
  belong to the build-port phase, where each helper is ported, stubbed, or
  its mixin skipped deliberately — not silently.
- **Build port** to 1.12.2/Cleanroom (Unimined-based, modeled on
  Actinium's root build; current tree still builds as a 1.7.10 GTNH mod).
