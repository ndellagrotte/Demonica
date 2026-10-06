package net.coderbot.iris.celeritas;

import com.demonica.celeritas.api.shader.PassSemantics;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

public enum IrisTerrainPass {
    // Each pass's program is its upstream id (I pipeline/programs/SodiumPrograms.java, Pass), resolved through the
    // fallback chain: shadow_solid and shadow_cutout fall back to shadow, gbuffers_terrain_solid and
    // gbuffers_terrain_cutout to gbuffers_terrain.
    // Demonica: TerrainCutoutMip (OptiFine's 1.12.2-era name, which upstream dropped) gets no pass of its own; the
    // cutout pass draws Celeritas's cutout-mipped material (toTerrainPass) with TerrainCutout, as upstream's one
    // cutout pass does.
    SHADOW("shadow", ProgramId.ShadowSolid),
    SHADOW_CUTOUT("shadow", ProgramId.ShadowCutout),
    SHADOW_TRANSLUCENT("shadow_water", ProgramId.ShadowWater),
    GBUFFER_SOLID("gbuffers_terrain", ProgramId.TerrainSolid),
    GBUFFER_CUTOUT("gbuffers_terrain_cutout", ProgramId.TerrainCutout),
    GBUFFER_TRANSLUCENT("gbuffers_water", ProgramId.Water);

    public static final IrisTerrainPass[] VALUES = values();

    private final String name;
    private final ProgramId programId;

    IrisTerrainPass(String name, ProgramId programId) {
        this.name = name;
        this.programId = programId;
    }

    public String getName() {
        return name;
    }

    /** The program this pass draws with and takes its blend default from (upstream {@code Pass.getOriginalId()}). */
    public ProgramId getProgramId() {
        return programId;
    }

    public boolean isShadow() {
        return this == SHADOW || this == SHADOW_CUTOUT || this == SHADOW_TRANSLUCENT;
    }

    public TerrainRenderPass toTerrainPass(RenderPassConfiguration<?> config) {
        return switch (this) {
            case SHADOW, GBUFFER_SOLID -> config.defaultSolidMaterial().pass;
            case SHADOW_CUTOUT, GBUFFER_CUTOUT -> config.defaultCutoutMippedMaterial().pass;
            case SHADOW_TRANSLUCENT, GBUFFER_TRANSLUCENT -> config.defaultTranslucentMaterial().pass;
        };
    }

    public static IrisTerrainPass fromTerrainPass(TerrainRenderPass pass, boolean isShadow) {
        return switch (PassSemantics.semantic(pass)) {
            case WATER, TRANSLUCENT -> isShadow ? SHADOW_TRANSLUCENT : GBUFFER_TRANSLUCENT;
            case CUTOUT -> isShadow ? SHADOW_CUTOUT : GBUFFER_CUTOUT;
            case SOLID -> isShadow ? SHADOW : GBUFFER_SOLID;
        };
    }
}
