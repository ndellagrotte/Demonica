package net.coderbot.iris.shaderpack;

import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.shaderpack.include.AbsolutePackPath;
import net.coderbot.iris.shaderpack.include.IncludeGraph;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The required-feature check of {@link ShaderPack}'s constructor (docs/IRIS_PORTING_PLAN.md, section 11 item 7): a
 * pack whose {@code iris.features.required} names an unsupported feature leaves the constructor with
 * {@link MissingFeaturesException} instead of disabling shaders from inside it. The whole constructor is not built
 * here because its feature-flag loop reads GL capabilities (GLSM's {@code RenderSystem}); the check it calls is tested.
 */
class ShaderPackMissingFeaturesTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void initializeLaunchEnvironment() {
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("fml.deobfuscatedEnvironment", false);
    }

    @Test
    void unknownRequiredFlagThrowsWithTheFlag() throws IOException {
        ShaderProperties properties = parse("iris.features.required = ENTITY_TRANSLUCENT BOGUS_FLAG\n");

        MissingFeaturesException e = assertThrows(MissingFeaturesException.class,
            () -> ShaderPack.checkRequiredFeatures(properties.getRequiredFeatureFlags()));

        assertEquals(List.of(FeatureFlags.UNKNOWN), e.getInvalidFlags());
        assertEquals("Unknown", e.getFlagList());
        assertTrue(e.getMessage().contains("Unknown"), e.getMessage());
    }

    @Test
    void usableRequiredFlagsPass() throws IOException {
        // Software-true flags whose hardware check is a constant, so no GL capability is read.
        ShaderProperties properties = parse("iris.features.required = ENTITY_TRANSLUCENT FADE_VARIABLE TEXTURE_FILTERING\n");

        assertDoesNotThrow(() -> ShaderPack.checkRequiredFeatures(properties.getRequiredFeatureFlags()));
    }

    @Test
    void noRequiredFlagsPass() throws IOException {
        ShaderProperties properties = parse("");

        assertDoesNotThrow(() -> ShaderPack.checkRequiredFeatures(properties.getRequiredFeatureFlags()));
    }

    @Test
    void isAnIllegalStateExceptionSoOlderCatchSitesStillSeeIt() {
        assertTrue(IllegalStateException.class.isAssignableFrom(MissingFeaturesException.class));
    }

    private ShaderProperties parse(String source) throws IOException {
        Path entry = tempDir.resolve("entry.glsl");
        Files.writeString(entry, "void main() {}\n");
        IncludeGraph graph = new IncludeGraph(tempDir,
            com.google.common.collect.ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/entry.glsl")));
        ShaderPackOptions options = new ShaderPackOptions(graph, Map.of());
        return new ShaderProperties(source, options, List.of(), () -> true);
    }
}
