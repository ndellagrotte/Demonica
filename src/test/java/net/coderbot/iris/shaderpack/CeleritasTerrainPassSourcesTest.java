package net.coderbot.iris.shaderpack;

import net.coderbot.iris.celeritas.CeleritasTerrainPipeline;
import net.coderbot.iris.celeritas.IrisTerrainPass;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan item 2.2: each Celeritas terrain pass takes its own program through the fallback chain
 * ({@link CeleritasTerrainPipeline#resolveSources}), and passes that resolve to one source share one transform
 * ({@link CeleritasTerrainPipeline#submitTransforms}). Uses {@link ProgramFallbackResolverTest}'s fixture pack and
 * loader.
 */
class CeleritasTerrainPassSourcesTest {
    @TempDir
    Path tempDir;

    @Test
    void passesUseUpstreamProgramIds() {
        Map<IrisTerrainPass, ProgramId> expected = new EnumMap<>(IrisTerrainPass.class);
        expected.put(IrisTerrainPass.SHADOW, ProgramId.ShadowSolid);
        expected.put(IrisTerrainPass.SHADOW_CUTOUT, ProgramId.ShadowCutout);
        expected.put(IrisTerrainPass.SHADOW_TRANSLUCENT, ProgramId.ShadowWater);
        expected.put(IrisTerrainPass.GBUFFER_SOLID, ProgramId.TerrainSolid);
        expected.put(IrisTerrainPass.GBUFFER_CUTOUT, ProgramId.TerrainCutout);
        expected.put(IrisTerrainPass.GBUFFER_TRANSLUCENT, ProgramId.Water);

        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            assertEquals(expected.get(pass), pass.getProgramId(), pass.name());
        }
    }

    @Test
    void fixturePackResolvesEachPassToItsFileOrItsFallback() throws Exception {
        // The fixture ships shadow_solid and gbuffers_terrain_solid but no cutout or shadow_water files.
        Map<IrisTerrainPass, String> expected = new EnumMap<>(IrisTerrainPass.class);
        expected.put(IrisTerrainPass.SHADOW, "shadow_solid");
        expected.put(IrisTerrainPass.SHADOW_CUTOUT, "shadow");
        expected.put(IrisTerrainPass.SHADOW_TRANSLUCENT, "shadow");
        expected.put(IrisTerrainPass.GBUFFER_SOLID, "gbuffers_terrain_solid");
        expected.put(IrisTerrainPass.GBUFFER_CUTOUT, "gbuffers_terrain");
        expected.put(IrisTerrainPass.GBUFFER_TRANSLUCENT, "gbuffers_water");

        Map<IrisTerrainPass, Optional<ProgramSource>> sources = resolve(ProgramFallbackResolverTest.fixtureRoot());

        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            assertEquals(Optional.of(expected.get(pass)), sources.get(pass).map(ProgramSource::getName), pass.name());
        }
    }

    @Test
    void packShippingEverySplitFileTransformsEachPassOnItsOwn() throws Exception {
        for (String name : new String[] {
            "shadow", "shadow_solid", "shadow_cutout", "shadow_water",
            "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water"
        }) {
            ProgramFallbackResolverTest.writeProgram(tempDir, name);
        }

        Map<IrisTerrainPass, Optional<ProgramSource>> sources = resolve(tempDir);
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            assertEquals(Optional.of(pass.getProgramId().getSourceName()), sources.get(pass).map(ProgramSource::getName),
                pass.name());
        }

        List<String> submitted = new ArrayList<>();
        Map<IrisTerrainPass, Object> futures = submit(sources, submitted);

        assertEquals(List.of("gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water",
            "shadow_solid", "shadow_cutout", "shadow_water"), submitted);
        assertEquals(EnumSet.allOf(IrisTerrainPass.class), futures.keySet());
        assertNotSame(futures.get(IrisTerrainPass.GBUFFER_SOLID), futures.get(IrisTerrainPass.GBUFFER_CUTOUT));
        assertNotSame(futures.get(IrisTerrainPass.SHADOW), futures.get(IrisTerrainPass.SHADOW_CUTOUT));
    }

    @Test
    void packWithoutSplitFilesTransformsEachSourceOnceInTheOldOrder() throws Exception {
        // The shape of BSL, Complementary and I Like Vanilla: the four sources the pipeline transformed before 2.2.
        for (String name : new String[] {"shadow", "shadow_water", "gbuffers_terrain", "gbuffers_water"}) {
            ProgramFallbackResolverTest.writeProgram(tempDir, name);
        }

        List<String> submitted = new ArrayList<>();
        Map<IrisTerrainPass, Object> futures = submit(resolve(tempDir), submitted);

        assertEquals(List.of("gbuffers_terrain", "gbuffers_water", "shadow", "shadow_water"), submitted);
        assertSame(futures.get(IrisTerrainPass.GBUFFER_SOLID), futures.get(IrisTerrainPass.GBUFFER_CUTOUT));
        assertSame(futures.get(IrisTerrainPass.SHADOW), futures.get(IrisTerrainPass.SHADOW_CUTOUT));
        assertNotSame(futures.get(IrisTerrainPass.GBUFFER_SOLID), futures.get(IrisTerrainPass.GBUFFER_TRANSLUCENT));
        assertNotSame(futures.get(IrisTerrainPass.SHADOW), futures.get(IrisTerrainPass.SHADOW_TRANSLUCENT));
    }

    @Test
    void fallbackSharedByEveryPassIsTransformedOnce() throws Exception {
        // No water programs: translucent falls back to the terrain and shadow sources too. No shadow at all: the shadow
        // passes get nothing.
        ProgramFallbackResolverTest.writeProgram(tempDir, "gbuffers_textured");

        List<String> submitted = new ArrayList<>();
        Map<IrisTerrainPass, Object> futures = submit(resolve(tempDir), submitted);

        assertEquals(List.of("gbuffers_textured"), submitted);
        assertEquals(EnumSet.of(IrisTerrainPass.GBUFFER_SOLID, IrisTerrainPass.GBUFFER_CUTOUT,
            IrisTerrainPass.GBUFFER_TRANSLUCENT), futures.keySet());
        assertSame(futures.get(IrisTerrainPass.GBUFFER_SOLID), futures.get(IrisTerrainPass.GBUFFER_TRANSLUCENT));
    }

    private static Map<IrisTerrainPass, Optional<ProgramSource>> resolve(Path root) throws Exception {
        return CeleritasTerrainPipeline.resolveSources(
            new ProgramFallbackResolver(ProgramFallbackResolverTest.loadProgramSet(root)));
    }

    private static Map<IrisTerrainPass, Object> submit(Map<IrisTerrainPass, Optional<ProgramSource>> sources,
                                                       List<String> submitted) {
        return CeleritasTerrainPipeline.submitTransforms(sources, source -> {
            submitted.add(source.getName());
            return new Object();
        });
    }
}
