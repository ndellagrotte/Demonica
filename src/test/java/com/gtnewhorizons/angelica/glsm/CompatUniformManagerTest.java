package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatUniformManagerTest {

    @Test
    void findsLightUniformWhenTheFirstStructMemberIsOptimizedOut() {
        int[] locations = absentLocations();
        locations[CompatUniformManager.LOC_LIGHT_BASE + CompatUniformManager.LF_POSITION] = 12;

        assertTrue(CompatUniformManager.hasUniformLocation(
            locations,
            CompatUniformManager.LOC_LIGHT_BASE,
            2 * CompatUniformManager.LIGHT_FIELDS
        ));
    }

    @Test
    void findsMaterialUniformWhenEmissionIsOptimizedOut() {
        int[] locations = absentLocations();
        locations[CompatUniformManager.LOC_MAT_BASE + CompatUniformManager.MF_SHININESS] = 27;

        assertTrue(CompatUniformManager.hasUniformLocation(
            locations,
            CompatUniformManager.LOC_MAT_BASE,
            CompatUniformManager.MAT_FIELDS
        ));
    }

    @Test
    void rejectsUniformRangesWithoutActiveLocations() {
        int[] locations = absentLocations();

        assertFalse(CompatUniformManager.hasUniformLocation(
            locations,
            CompatUniformManager.LOC_LIGHT_BASE,
            2 * CompatUniformManager.LIGHT_FIELDS
        ));
        assertFalse(CompatUniformManager.hasUniformLocation(
            locations,
            CompatUniformManager.LOC_MAT_BASE,
            CompatUniformManager.MAT_FIELDS
        ));
    }

    @Test
    void looksUpTheIrisInverseMatrices() {
        assertEquals("iris_ModelViewMatrixInverse", CompatUniformManager.uniformName(CompatUniformManager.LOC_IRIS_MODELVIEW_INVERSE));
        assertEquals("iris_ProjectionMatrixInverse", CompatUniformManager.uniformName(CompatUniformManager.LOC_IRIS_PROJECTION_INVERSE));
        assertEquals("actinium_ModelViewMatrixInverse", CompatUniformManager.uniformName(CompatUniformManager.LOC_MODELVIEW_INVERSE));
        assertEquals("actinium_ProjectionMatrixInverse", CompatUniformManager.uniformName(CompatUniformManager.LOC_PROJECTION_INVERSE));
    }

    @Test
    void everyLocationHasItsOwnUniformName() {
        // The light and material blocks follow the single slots; an overlap would overwrite a name and leave a slot unnamed.
        Set<String> names = new HashSet<>();
        for (int location = 0; location < CompatUniformManager.LOC_COUNT; location++) {
            String name = CompatUniformManager.uniformName(location);
            assertNotNull(name, "location " + location);
            assertTrue(names.add(name), "duplicate name " + name);
        }
        assertEquals("actinium_LightSource[0].ambient", CompatUniformManager.uniformName(CompatUniformManager.LOC_LIGHT_BASE));
        assertEquals("actinium_FrontMaterial.shininess",
            CompatUniformManager.uniformName(CompatUniformManager.LOC_COUNT - 1));
    }

    private static int[] absentLocations() {
        int[] locations = new int[CompatUniformManager.LOC_COUNT];
        Arrays.fill(locations, -1);
        return locations;
    }
}
