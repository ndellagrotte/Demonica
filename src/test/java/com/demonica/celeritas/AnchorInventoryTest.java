package com.demonica.celeritas;

import com.demonica.celeritas.guard.Anchor;
import com.demonica.celeritas.guard.AnchorAudit;
import com.demonica.celeritas.guard.AnchorFile;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.guard.QuarantineAnchors;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Demonica's quarantined patches bind to in the pinned Celeritas jar, and the shape of upstream's own mixins that
 * the patches are layered on. The anchors are extracted from the compiled quarantine mixins by the extractor the build
 * uses for the mod's anchor list (com.demonica.celeritas.guard.AnchorExtractor), and checked by the audit
 * QuarantineGuard runs in the game. When the pin moves, this test says exactly which anchor or upstream mixin changed.
 */
class AnchorInventoryTest {
    static final String FOG_SERVICE = "org/taumc/celeritas/impl/render/terrain/fog/GLStateManagerFogService";
    static final String OPTION_PAGES = "org/taumc/celeritas/impl/gui/SodiumGameOptionPages";
    static final String PAGE = "()Lorg/taumc/celeritas/api/options/structure/OptionPage;";
    static final String OPTION_ID = "Lorg/taumc/celeritas/api/options/OptionIdentifier;";
    static final String STANDARD_GROUP = "Lorg/taumc/celeritas/api/options/structure/StandardOptions$Group;";
    static final String STANDARD_OPTION = "Lorg/taumc/celeritas/api/options/structure/StandardOptions$Option;";

    /**
     * Not patches: the video settings that DemonicaOptionPages extends and OptionsScreens replaces. Video Settings builds
     * Celeritas's screen, and its pages hold the groups and options Demonica's settings are placed by. A change here
     * misplaces Demonica's settings rather than breaking a patch, so the build checks it and the game does not.
     */
    static final List<Anchor> OPTION_SCREEN_CONTEXT = List.of(
        context("org/taumc/celeritas/mixin/features/options/MixinGuiOptions",
            "open(Lnet/minecraft/client/gui/GuiButton;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
            "Lorg/taumc/celeritas/impl/gui/CeleritasVideoOptionsScreen;<init>(Lnet/minecraft/client/gui/GuiScreen;)V", Anchor.Kind.INVOKE),
        context(OPTION_PAGES, "general" + PAGE, STANDARD_GROUP + "WINDOW:" + OPTION_ID, Anchor.Kind.ACCESS),
        context(OPTION_PAGES, "general" + PAGE, STANDARD_OPTION + "FULLSCREEN:" + OPTION_ID, Anchor.Kind.ACCESS),
        context(OPTION_PAGES, "general" + PAGE, STANDARD_OPTION + "MAX_FRAMERATE:" + OPTION_ID, Anchor.Kind.ACCESS),
        context(OPTION_PAGES, "quality" + PAGE, STANDARD_GROUP + "SORTING:" + OPTION_ID, Anchor.Kind.ACCESS),
        context(OPTION_PAGES, "advanced" + PAGE, STANDARD_GROUP + "CPU_SAVING:" + OPTION_ID, Anchor.Kind.ACCESS)
    );

    private static Anchor context(String owner, String method, String target, Anchor.Kind kind) {
        return new Anchor("com.demonica.gui.DemonicaOptionPages", PatchGroup.OPTIONS, "options", kind, owner, method, target);
    }

    @Test
    void everyPatchAnchorHoldsInThePinnedJar() {
        List<AnchorAudit.Failure> failures = audit(QuarantineAnchors.extracted());
        assertTrue(failures.isEmpty(), "anchors missing from " + CeleritasJar.get().file().getName() + ":\n  "
            + failures.stream().map(Object::toString).collect(Collectors.joining("\n  ")));
    }

    @Test
    void everyQuarantineMixinHasAnchors() {
        Set<String> anchored = QuarantineAnchors.extracted().stream().map(Anchor::mixin).collect(Collectors.toSet());
        List<String> without = QuarantineAnchors.mixins().stream().filter(mixin -> !anchored.contains(mixin)).toList();
        assertTrue(without.isEmpty(), "quarantine mixins without anchors: " + without);
    }

    /** The list in the mod's resources is the build's extraction of these classes, for the pin in gradle.properties. */
    @Test
    void theModCarriesTheseAnchorsAndThePin() throws IOException {
        AnchorFile.Contents generated = QuarantineAnchors.generated();
        assertEquals(QuarantineAnchors.extracted(), generated.anchors(),
            "META-INF/demonica/celeritas-anchors is stale; run ./gradlew generateCeleritasAnchors");
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(System.getProperty("demonica.projectRoot", "."), "gradle.properties"))) {
            properties.load(in);
        }
        assertEquals(properties.getProperty("celeritas_sha"), generated.upstreamCommit());
        assertEquals(properties.getProperty("celeritas_version"), generated.version());
        assertEquals(Set.of(properties.getProperty("celeritas_sha256").split(",")), generated.pins());
    }

    @Test
    void theOptionScreenContextHolds() {
        List<AnchorAudit.Failure> failures = audit(OPTION_SCREEN_CONTEXT);
        assertTrue(failures.isEmpty(), "the video settings Demonica extends changed:\n  "
            + failures.stream().map(Object::toString).collect(Collectors.joining("\n  ")));
    }

    private static List<AnchorAudit.Failure> audit(List<Anchor> anchors) {
        CeleritasJar jar = CeleritasJar.get();
        // The dev remap has no refmap: the annotations' names are already the jar's.
        return new AnchorAudit(name -> jar.contains(name) ? jar.bytes(name) : null, Map.of()).audit(anchors);
    }

    /** What upstream's mixins @Overwrite, in MCP names. A quarantine patch must never overwrite these again. */
    static final Set<String> UPSTREAM_OVERWRITES = Set.of(
        "net/minecraft/client/renderer/RenderGlobal#getDebugInfoRenders()Ljava/lang/String;",
        "net/minecraft/client/renderer/RenderGlobal#getRenderedChunks()I",
        "net/minecraft/client/renderer/RenderGlobal#hasNoChunkUpdates()Z",
        "net/minecraft/client/renderer/RenderGlobal#markBlocksForUpdate(IIIIIIZ)V",
        "net/minecraft/client/renderer/RenderGlobal#renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        "net/minecraft/client/renderer/RenderGlobal#setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
        "net/minecraft/client/renderer/culling/Frustum#isBoxInFrustum(DDDDDD)Z",
        "net/minecraft/client/renderer/texture/TextureUtil#blendColors(IIIIZ)I",
        "net/minecraft/util/EnumFacing#getFacingFromVector(FFF)Lnet/minecraft/util/EnumFacing;",
        "net/minecraft/world/biome/BiomeColorHelper#getColorAtPos(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/world/biome/BiomeColorHelper$ColorResolver;)I"
    );

    /** Upstream's non-default mixin priorities by target. Every other upstream mixin runs at the default, 1000. */
    static final Map<String, Integer> UPSTREAM_PRIORITIES = Map.of(
        "net/minecraft/client/renderer/RenderGlobal", 1000,
        "net/minecraft/world/biome/BiomeColorHelper", 1200,
        "net/minecraft/client/renderer/texture/TextureUtil", 900
    );

    @Test
    void upstreamOverwritesAreAsRecorded() throws IOException {
        Set<String> overwrites = new TreeSet<>();
        for (UpstreamMixinInventory.MixinClass mixin : UpstreamMixinInventory.read(CeleritasJar.get())) {
            for (String target : mixin.targets()) {
                mixin.overwrites().forEach(o -> overwrites.add(target + "#" + o));
            }
        }
        assertEquals(new TreeSet<>(UPSTREAM_OVERWRITES), overwrites, "upstream's set of @Overwrite members changed");
    }

    @Test
    void upstreamPrioritiesAreAsRecorded() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (UpstreamMixinInventory.MixinClass mixin : UpstreamMixinInventory.read(CeleritasJar.get())) {
            for (String target : mixin.targets()) {
                int expected = UPSTREAM_PRIORITIES.getOrDefault(target, UpstreamMixinInventory.DEFAULT_PRIORITY);
                if (mixin.priority() != expected) {
                    wrong.add(mixin.name() + " -> " + target + ": priority " + mixin.priority() + ", expected " + expected);
                }
            }
        }
        assertTrue(wrong.isEmpty(), "upstream mixin priorities changed:\n  " + String.join("\n  ", wrong));
    }

    /**
     * The full inventory of upstream's 27 mixins (targets, priorities, overwrites, injection points), compared with
     * the checked-in snapshot. A new pin that changes any of it fails here with the new inventory written next to
     * the build output, so the change can be reviewed and the snapshot updated deliberately.
     */
    @Test
    void upstreamMixinInventoryMatchesSnapshot() throws IOException {
        List<UpstreamMixinInventory.MixinClass> mixins = UpstreamMixinInventory.read(CeleritasJar.get());
        assertEquals(27, mixins.size(), "upstream's mixin count changed");

        List<String> actual = UpstreamMixinInventory.render(mixins);
        List<String> expected;
        try (InputStream in = AnchorInventoryTest.class.getResourceAsStream("upstream-mixin-inventory.txt")) {
            expected = in == null ? List.of() : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).collect(Collectors.toList());
        }
        if (!actual.equals(expected)) {
            Path out = Path.of("upstream-mixin-inventory.actual.txt").toAbsolutePath();
            Files.write(out, actual, StandardCharsets.UTF_8);
            assertEquals(String.join("\n", expected), String.join("\n", actual),
                "upstream mixin inventory differs from src/test/resources/com/demonica/celeritas/upstream-mixin-inventory.txt; actual written to " + out);
        }
    }

    @Test
    void upstreamFogServiceIsTheOnlyRegisteredOne() throws IOException {
        try (JarFile file = new JarFile(CeleritasJar.get().file())) {
            var entry = file.getEntry("META-INF/services/org.embeddedt.embeddium.impl.render.chunk.fog.FogService");
            assertTrue(entry != null, "Celeritas no longer registers a FogService");
            String services = new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8).trim();
            // S15 patches this class in place, so it must stay the one ServiceLoader.findFirst() picks.
            assertEquals(FOG_SERVICE.replace('/', '.'), services);
        }
    }
}
