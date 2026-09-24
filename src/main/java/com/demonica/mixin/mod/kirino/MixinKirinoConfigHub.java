package com.demonica.mixin.mod.kirino;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Headless co-existence bridge for Kirino Engine's {@code KirinoConfigHub}.
 *
 * <p>Kirino Engine (shipped with Cleanroom Loader, mod ids
 * {@code kirino_engine}/{@code kirino_ecs}/{@code kirino_gl}) has two mutually
 * exclusive render runs: in Graphics mode it replaces
 * {@code EntityRenderer#renderWorld} entirely (its own {@code EntityRenderer$renderWorld}),
 * which starves every Demonica render hook that anchors to vanilla
 * {@code renderWorldPass} (the Iris pipeline, Celeritas's terrain, entity batching, ...).
 * Its {@code enableRenderDelegate} toggle is initialised to {@code true}; Cleanroom's own
 * {@code KirinoCommonCore#onKirinoOneTimeConfig} turns it off (0.5.17 to 0.6.12), but any other
 * listener of that one-time config event can turn it back on, and there is no user facing
 * configuration for it.
 *
 * <p>Demonica therefore pins the toggle at its read points: wherever Kirino asks
 * {@code isEnableRenderDelegate()}, this mixin always answers {@code false}, which makes
 * Kirino run in Headless mode ("path 1"): the engine and its ECS/analysis runtime are
 * initialised ({@code isEnable()} is untouched, so no GL resources are allocated and
 * vanilla {@code renderWorld} keeps running), while Demonica stays the single owner of
 * the rendering pipeline.
 *
 * <p>Registered early by {@code MixinEarly} ({@code mixins.demonica.kirino.json}) and applied only
 * when Kirino's class is transformed ({@code KirinoMixinConfigPlugin}); the target class is referenced
 * by string, so no compile-time dependency on Kirino exists.
 */
@Mixin(targets = "com.cleanroommc.kirino.config.KirinoConfigHub", remap = false)
public abstract class MixinKirinoConfigHub {

    /**
     * @author Demonica
     * @reason Force Kirino into Headless mode so vanilla {@code renderWorld} (and therefore
     * Demonica's render pipeline) stays in charge; {@code KIRINO_CONFIG_HUB.enable} remains
     * untouched so Kirino's ECS/analysis runtime still initialises.
     */
    @Overwrite
    public boolean isEnableRenderDelegate() {
        return false;
    }
}
