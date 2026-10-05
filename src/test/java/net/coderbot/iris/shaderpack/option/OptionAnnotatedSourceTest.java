package net.coderbot.iris.shaderpack.option;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionAnnotatedSourceTest {
    private static Set<String> constStringOptionNames(String source) {
        return new OptionAnnotatedSource(source).getStringOptions().values().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsVoxelDistanceAndHigherShadowColorBufferOptions() {
        String source = String.join("\n",
                "const float voxelDistance = 128.0; // [64.0 128.0]",
                "const bool shadowcolor5Nearest = false;",
                "const bool shadowColor7MinMagNearest = false;",
                "const int shadowcolor5MinMagNearest = 1; // [0 1]",
                "const int shadowHardwareFiltering7 = 1; // [0 1]",
                "const int notARealOption = 1; // [0 1]");

        OptionAnnotatedSource parsed = new OptionAnnotatedSource(source);
        Set<String> names = constStringOptionNames(source);

        assertTrue(names.contains("voxelDistance"), names.toString());
        assertTrue(names.contains("shadowcolor5MinMagNearest"), names.toString());
        assertTrue(names.contains("shadowHardwareFiltering7"), names.toString());
        assertTrue(parsed.getBooleanOptions().values().stream()
                .anyMatch(o -> o.getName().equals("shadowcolor5Nearest")));
        assertTrue(parsed.getBooleanOptions().values().stream()
                .anyMatch(o -> o.getName().equals("shadowColor7MinMagNearest")));
        assertEquals(1, parsed.getDiagnostics().size(), parsed.getDiagnostics().toString());
        assertTrue(!names.contains("notARealOption"));
    }
}
