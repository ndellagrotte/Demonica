# Distant Horizons: accept Demonica as the Iris provider

Draft of an issue for Distant Horizons, not sent. Whether and when to send it is the
maintainer's decision.

## Problem

Distant Horizons 3.3.0 for 1.12.2 (Cleanroom) binds its Iris integration only when a mod
with id `actinium` is loaded. `CleanroomMain.initializeModCompat` calls
`tryCreateModCompatAccessor("actinium", IIrisAccessor.class, ...)`, so the
`IIrisAccessor` (the shader-pack LOD programs and the deferred translucent LOD pass) is
never created for any other mod that provides the Iris API.

Demonica is Actinium's successor: a shader mod on upstream Celeritas, with the same Iris
API (`net.irisshaders.iris.api.v0.IrisApi` and the `net.coderbot.iris` classes DH's
accessor uses). It runs under mod id `demonica`, and refuses to start next to Actinium.
With DH installed, shader packs get no LOD passes.

## Request

Accept either mod id, for example with the `String[]` overload that
`tryCreateModCompatAccessor` already has:

```java
tryCreateModCompatAccessor(new String[] { "actinium", "demonica" }, IIrisAccessor.class, IrisAccessor::new);
```

## Until then

Demonica ships a mixin, loaded only with Distant Horizons
(`mixins.demonica.distanthorizons.json`), that redirects the `tryCreateModCompatAccessor`
calls in `initializeModCompat` (`MixinCleanroomMain`). It binds the `IIrisAccessor` for
`demonica` exactly once: on DH 3.3.0 the lone `"actinium"` call is made as `"demonica"`; on a
DH that names `"actinium"` and then `"demonica"` (the shape of the upstream change), the first
call binds as `demonica` and the second is skipped; on a DH that names only `"demonica"` the
call passes through. The guard matters because DH's `DependencyInjector.bind` throws
`IllegalStateException("The dependency [IIrisAccessor] has already been bound.")` on a second
bind, so rewriting `"actinium"` alone would stop the game on a DH that already accepts
`demonica`. The redirect is optional (`require = 0`), so a DH release that registers its
accessors elsewhere loads normally.
