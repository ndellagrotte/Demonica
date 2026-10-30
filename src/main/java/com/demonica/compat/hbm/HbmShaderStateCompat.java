package com.demonica.compat.hbm;

import net.coderbot.iris.apiimpl.IrisApiV0Impl;

/**
 * Answers HBM's "are shaders active" probes with Demonica's shader-pipeline state.
 *
 * <p>HBM's renderers choose between an OptiFine-shaders path and a no-shaders path through
 * {@code com.hbm.util.ShaderHelper}, which binds {@code net.optifine.shaders.Shaders} by name and
 * resolves to a no-op (never active) unless the OptiFine class exists. Demonica implements the
 * shader pipeline itself and ships no OptiFine class, so every such probe is negative here and the
 * renderers take their "no shaders" branch while a shader pack is drawing the world.</p>
 *
 * <p>The probe is answered per call site rather than by patching {@code ShaderHelper} itself:
 * some of HBM's call sites follow a positive answer with OptiFine-only calls
 * ({@code com.hbm.render.chunk.SectionGeometry} pushes the block entity through
 * {@code OptifineHooks}, whose method handles are null without OptiFine), so a global answer
 * would crash chunk meshing. Only the renderers whose shader branch is plain GL are redirected.</p>
 */
public final class HbmShaderStateCompat {
    private HbmShaderStateCompat() {
    }

    /** Returns whether a shader pack is rendering the world right now. */
    public static boolean isShaderPackActive() {
        return IrisApiV0Impl.INSTANCE.isShaderPackInUse();
    }
}
