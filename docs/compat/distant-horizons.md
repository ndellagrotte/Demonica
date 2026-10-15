# Distant Horizons: accept Demonica as the Iris provider

Draft of an issue for Distant Horizons, not sent. Whether and when to send it is the
maintainer's decision.

## Problem

Distant Horizons for 1.12.2 binds its Iris integration only when a mod with id `actinium`
is loaded. Its mod class's `initializeModCompat` (`cleanroom.CleanroomMain` in 3.3.0,
`forge112.ForgeMain` in 3.3.3) calls
`tryCreateModCompatAccessor("actinium", IIrisAccessor.class, ...)`, so the
`IIrisAccessor` (the shader-pack LOD programs and the deferred translucent LOD pass) is
never created for any other mod that provides the Iris API. DH's `main` branch adds a
`"demonica"` call (b095799d, 2026-10-12), not yet in a release.

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
(`mixins.demonica.distanthorizons.json`), on DH's shared superclass `AbstractModInitializer`
(`MixinAbstractModInitializer`): it injects at the head of both `tryCreateModCompatAccessor`
overloads (`String` and `String[]`) and binds the `IIrisAccessor` for `demonica` exactly once.
On DH 3.3.0 to 3.3.3 the lone `"actinium"` call is made as `"demonica"`; on a DH that names
`"actinium"` and then `"demonica"` (the shape of the upstream change), the first call binds as
`demonica` and the second is skipped; on a DH that names only `"demonica"`, or passes an array,
it binds as `demonica`. The guard matters because DH's `DependencyInjector.bind` throws
`IllegalStateException("The dependency [IIrisAccessor] has already been bound.")` on a second
bind. When it rewrites a call, Demonica logs
`Distant Horizons asked for its Iris accessor as [actinium]; binding it as [demonica]`.

The mixin used to redirect the call inside `CleanroomMain.initializeModCompat` (0.7.1 to
0.8.0). DH 3.3.3 renamed that class to `forge112.ForgeMain`, so the redirect found no target
and stopped applying without an error; DH logged `Skipping mod compatibility accessor for:
[actinium]` and shader packs got no LODs. The superclass keeps its name across the rename, and
the injectors are required, so a DH that drops these methods fails loudly instead.

## Cleanroom 0.7

2026-10-14: DH 3.3.0 (`maven.modrinth:uCdwusMi:Sa0ttGJr`) calls GLFW, which Cleanroom 0.7 does
not ship: the client reaches the main menu, then crashes on a world's first frame with
`NoClassDefFoundError: org/lwjgl/glfw/GLFW` (`GLProxy.runningOnRenderThread`, from
`ClientApi.renderLods`), so `-PwithCompatMods` stopped loading it.

2026-10-15: DH 3.3.3 (`curse.maven:distant-horizons-508933:9004743`, the version Demonica now
compiles against) runs on Cleanroom 0.7.4-alpha. In a Prism clone of `cleanroom-0.7.4-alpha`
with BSL, the 0.8.0 release jar reproduced the bug (`Skipping mod compatibility accessor for:
[actinium]`, LODs drawn unshaded, and DH's `GlVanillaFadeRenderer` logging a
`Framebuffer cannot be cast to IFramebufferDepthTexture` error every frame); this fix bound the
accessor as `demonica`, and the LODs were drawn through the pack with no DH errors. DH is not
back in `devRuntimeMods` yet: that dev run is untested.
