package com.demonica.compat.hbm;

import com.demonica.mixin.mod.hbm.GeneralConfigAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps HBM's instanced particles off while a shader pack renders the world.
 *
 * <p>With {@code instancedParticles} on, HBM draws its explosion smoke, rocket flames, contrails,
 * radiation fog and RBMK effects through {@code InstancedParticleRenderer}: its own GLSL program
 * ({@code hbm:shaders/lit_particles}) run from Forge's {@code RenderWorldLastEvent}, which samples
 * the lightmap from texture unit 1. Under a shader pack that event fires after the pipeline has
 * finished the frame, outside any gbuffers program and with the pipeline's own textures on the
 * units, so those particles come out black. HBM's config calls the option out as one that "will
 * break with shaders", and its mushroom cloud already takes the non-instanced path when OptiFine
 * reports a pack; the other spawn sites only read the flag.</p>
 *
 * <p>So the flag is driven from here: HBM's configured value is captured when it loads its
 * config, and the live field is set to {@code configured && !shaderPackActive} at the start of
 * every client tick, before any effect packet of that tick spawns particles and before the
 * renderer reads it. The non-instanced particles are ordinary {@code ParticleManager} particles,
 * which the pipeline's particle program lights like any other. Turning the pack off restores the
 * configured value; a config reload recaptures it.</p>
 */
public final class HbmInstancedParticlesCompat {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");
    private static Boolean configured;
    private static boolean lastApplied;

    private HbmInstancedParticlesCompat() {
    }

    /** Records the value HBM just read from its config file, then applies the shader rule to it. */
    public static void onConfigLoaded(boolean value) {
        configured = value;
        lastApplied = value;
        sync();
    }

    /** Sets HBM's live flag from the configured value and the current shader-pack state. */
    public static void sync() {
        if (configured == null) {
            return;
        }
        boolean wanted = configured && !HbmShaderStateCompat.isShaderPackActive();
        if (GeneralConfigAccessor.demonica$getInstancedParticles() != wanted) {
            GeneralConfigAccessor.demonica$setInstancedParticles(wanted);
        }
        if (wanted != lastApplied) {
            lastApplied = wanted;
            LOGGER.info("HBM instanced particles {} (configured {}, shader pack {})",
                wanted ? "on" : "off", configured, wanted == configured ? "off" : "on");
        }
    }
}
