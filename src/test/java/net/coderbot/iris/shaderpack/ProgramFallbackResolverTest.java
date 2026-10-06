package net.coderbot.iris.shaderpack;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.shaderpack.include.AbsolutePackPath;
import net.coderbot.iris.shaderpack.include.IncludeGraph;
import net.coderbot.iris.shaderpack.include.IncludeProcessor;
import net.coderbot.iris.shaderpack.include.ShaderPackSourceNames;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan item 2.1: {@link ProgramSet} reads every {@link ProgramId}, and {@link ProgramFallbackResolver} resolves each one
 * to its own file when the pack ships it and along the fallback chain otherwise. The fixture pack in
 * {@code src/test/resources/program-fallback-pack} ships a mix: some of the ids ProgramSet did not read before 2.1
 * ({@code shadow_solid}, {@code gbuffers_terrain_solid}, {@code gbuffers_terrain_cutout_mip}, {@code gbuffers_item}),
 * some of the six ids 2.1 added ({@code shadow_entities}, {@code shadow_block}, {@code gbuffers_particles}), and leaves
 * out others so their fallbacks are exercised.
 */
class ProgramFallbackResolverTest {
    // The source name each id resolves to in the fixture pack; null means nothing resolves.
    private static final Map<ProgramId, String> FIXTURE_EXPECTED = expectedFixtureResolutions();

    @TempDir
    Path tempDir;

    @Test
    void everyIdHasAFixtureExpectation() {
        assertEquals(EnumSet.allOf(ProgramId.class), EnumSet.copyOf(FIXTURE_EXPECTED.keySet()));
    }

    @Test
    void fixturePackResolvesEveryIdToItsFileOrItsFallback() throws Exception {
        ProgramSet programSet = loadProgramSet(fixtureRoot());
        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programSet);

        for (ProgramId id : ProgramId.values()) {
            assertEquals(Optional.ofNullable(FIXTURE_EXPECTED.get(id)), resolver.resolve(id).map(ProgramSource::getName),
                id.name());
        }
    }

    @Test
    void fixturePackGetIsPresentExactlyForShippedFiles() throws Exception {
        Path root = fixtureRoot();
        ProgramSet programSet = loadProgramSet(root);

        for (ProgramId id : ProgramId.values()) {
            boolean shipped = Files.exists(root.resolve(id.getSourceName() + ".vsh"))
                && Files.exists(root.resolve(id.getSourceName() + ".fsh"));
            Optional<String> expected;

            if (id == ProgramId.DamagedBlock && !shipped) {
                // ProgramSet's ShadersMod fallback: gbuffers_terrain with DRAWBUFFERS overridden to 0.
                expected = Optional.of("gbuffers_terrain");
            } else if (id == ProgramId.ShadowWater && !shipped) {
                // ProgramSet.get(ShadowWater) answers with shadow when shadow_water is absent.
                expected = Optional.of("shadow");
            } else {
                expected = shipped ? Optional.of(id.getSourceName()) : Optional.empty();
            }

            assertEquals(expected, programSet.get(id).map(ProgramSource::getName), id.name());
        }
    }

    @Test
    void packShippingEveryProgramResolvesEachIdToItsOwnFile() throws Exception {
        for (ProgramId id : ProgramId.values()) {
            writeProgram(tempDir, id.getSourceName());
        }

        ProgramSet programSet = loadProgramSet(tempDir);
        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programSet);

        for (ProgramId id : ProgramId.values()) {
            assertEquals(Optional.of(id.getSourceName()), programSet.get(id).map(ProgramSource::getName), id.name());
            assertEquals(Optional.of(id.getSourceName()), resolver.resolve(id).map(ProgramSource::getName), id.name());
        }
    }

    @Test
    void shadowProgramsAreReadWithBlendingOff() throws Exception {
        for (ProgramId id : ProgramId.values()) {
            writeProgram(tempDir, id.getSourceName());
        }

        ProgramSet programSet = loadProgramSet(tempDir);

        for (ProgramId id : new ProgramId[] {
            ProgramId.Shadow, ProgramId.ShadowSolid, ProgramId.ShadowCutout, ProgramId.ShadowWater,
            ProgramId.ShadowEntities, ProgramId.ShadowLightning, ProgramId.ShadowBlock
        }) {
            Optional<BlendModeOverride> blend = programSet.get(id).orElseThrow().getDirectives().getBlendModeOverride();
            assertTrue(blend.isPresent(), id.name());
            assertSame(BlendModeOverride.OFF, blend.get(), id.name());
        }
    }

    private static Map<ProgramId, String> expectedFixtureResolutions() {
        Map<ProgramId, String> expected = new EnumMap<>(ProgramId.class);

        expected.put(ProgramId.Shadow, "shadow");
        expected.put(ProgramId.ShadowSolid, "shadow_solid");
        expected.put(ProgramId.ShadowCutout, "shadow");
        expected.put(ProgramId.ShadowWater, "shadow");
        expected.put(ProgramId.ShadowEntities, "shadow_entities");
        expected.put(ProgramId.ShadowLightning, "shadow_entities");
        expected.put(ProgramId.ShadowBlock, "shadow_block");

        expected.put(ProgramId.Basic, "gbuffers_basic");
        expected.put(ProgramId.Line, "gbuffers_basic");
        expected.put(ProgramId.Textured, "gbuffers_textured");
        expected.put(ProgramId.TexturedLit, "gbuffers_textured");
        expected.put(ProgramId.SkyBasic, "gbuffers_basic");
        expected.put(ProgramId.SkyTextured, "gbuffers_textured");
        expected.put(ProgramId.Clouds, "gbuffers_textured");

        expected.put(ProgramId.Terrain, "gbuffers_terrain");
        expected.put(ProgramId.TerrainSolid, "gbuffers_terrain_solid");
        expected.put(ProgramId.TerrainCutoutMip, "gbuffers_terrain_cutout_mip");
        expected.put(ProgramId.TerrainCutout, "gbuffers_terrain");
        expected.put(ProgramId.DamagedBlock, "gbuffers_terrain");

        expected.put(ProgramId.Block, "gbuffers_terrain");
        expected.put(ProgramId.BlockTrans, "gbuffers_terrain");
        expected.put(ProgramId.BeaconBeam, "gbuffers_textured");
        expected.put(ProgramId.Item, "gbuffers_item");

        expected.put(ProgramId.Entities, "gbuffers_entities");
        expected.put(ProgramId.EntitiesTrans, "gbuffers_entities");
        expected.put(ProgramId.Lightning, "gbuffers_entities");
        expected.put(ProgramId.Particles, "gbuffers_particles");
        expected.put(ProgramId.ParticlesTrans, "gbuffers_particles");
        expected.put(ProgramId.EntitiesGlowing, "gbuffers_entities");
        expected.put(ProgramId.ArmorGlint, "gbuffers_textured");
        expected.put(ProgramId.SpiderEyes, "gbuffers_textured");

        expected.put(ProgramId.Hand, "gbuffers_textured");
        expected.put(ProgramId.Weather, "gbuffers_textured");
        expected.put(ProgramId.Water, "gbuffers_water");
        expected.put(ProgramId.HandWater, "gbuffers_hand_water");
        expected.put(ProgramId.DhTerrain, "dh_terrain");
        expected.put(ProgramId.DhWater, "dh_terrain");
        expected.put(ProgramId.DhGeneric, "dh_terrain");
        expected.put(ProgramId.DhShadow, null);

        expected.put(ProgramId.Final, "final");

        return expected;
    }

    static Path fixtureRoot() throws URISyntaxException {
        return Path.of(ProgramFallbackResolverTest.class.getResource("/program-fallback-pack").toURI());
    }

    static void writeProgram(Path root, String name) throws IOException {
        Files.writeString(root.resolve(name + ".vsh"), "#version 120\n\nvoid main() {}\n");
        Files.writeString(root.resolve(name + ".fsh"), "#version 120\n\nvoid main() {}\n");
    }

    /**
     * Loads the pack's base ProgramSet the way ShaderPack's constructor does (source discovery over
     * ShaderPackSourceNames.POTENTIAL_STARTS, include graph, include processor), without the preprocessor and
     * properties, which a source without directives does not need.
     */
    static ProgramSet loadProgramSet(Path root) throws Exception {
        ImmutableList.Builder<AbsolutePackPath> starts = ImmutableList.builder();
        ShaderPackSourceNames.findPresentSources(starts, root, AbsolutePackPath.fromAbsolutePath("/"),
            ShaderPackSourceNames.POTENTIAL_STARTS);

        IncludeGraph graph = new IncludeGraph(root, starts.build());
        assertTrue(graph.getFailures().isEmpty(), graph.getFailures().toString());
        IncludeProcessor includeProcessor = new IncludeProcessor(graph);

        Function<AbsolutePackPath, String> sourceProvider = path -> {
            ImmutableList<String> lines = includeProcessor.getIncludedFile(path);
            return lines == null ? null : String.join("\n", lines) + "\n";
        };

        return new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"), sourceProvider, ShaderProperties.empty(),
            packWithoutFeatures());
    }

    /**
     * ProgramSet asks its pack only hasFeature (for tessellation stages). ShaderPack's constructor needs a GL context
     * (FeatureFlags.isUsable queries the GL capabilities), so the test allocates one without running it and gives it an
     * empty feature set.
     */
    private static ShaderPack packWithoutFeatures() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ShaderPack pack = (ShaderPack) unsafe.allocateInstance(ShaderPack.class);

        Field activeFeatures = ShaderPack.class.getDeclaredField("activeFeatures");
        activeFeatures.setAccessible(true);
        activeFeatures.set(pack, new HashSet<FeatureFlags>());
        assertFalse(pack.hasFeature(FeatureFlags.TESSELLATION_SHADERS));

        return pack;
    }
}
