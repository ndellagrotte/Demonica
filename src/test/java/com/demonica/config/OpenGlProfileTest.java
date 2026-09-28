package com.demonica.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenGlProfileTest {
    @Test
    void parsesAnyCaseAndDefaultsToAuto() {
        assertEquals(OpenGlProfile.CORE, OpenGlProfile.parse("core"));
        assertEquals(OpenGlProfile.CORE, OpenGlProfile.parse("CORE"));
        assertEquals(OpenGlProfile.COMPATIBILITY, OpenGlProfile.parse(" Compatibility "));
        assertEquals(OpenGlProfile.AUTO, OpenGlProfile.parse("auto"));
        assertEquals(OpenGlProfile.AUTO, OpenGlProfile.parse(null));
        assertEquals(OpenGlProfile.AUTO, OpenGlProfile.parse(" "));
        assertEquals(OpenGlProfile.AUTO, OpenGlProfile.parse("legacy"));
    }

    @Test
    void autoIsCoreOnMacOsAndCompatibilityElsewhere() {
        assertEquals(OpenGlProfile.CORE, OpenGlProfile.AUTO.forPlatform(true));
        assertEquals(OpenGlProfile.COMPATIBILITY, OpenGlProfile.AUTO.forPlatform(false));
    }

    @Test
    void explicitProfilesIgnoreThePlatform() {
        for (boolean macos : new boolean[] {true, false}) {
            assertEquals(OpenGlProfile.CORE, OpenGlProfile.CORE.forPlatform(macos));
            assertEquals(OpenGlProfile.COMPATIBILITY, OpenGlProfile.COMPATIBILITY.forPlatform(macos));
        }
    }

    @Test
    void translationKeysUseTheLowerCaseName() {
        assertEquals("sodium.options.opengl_profile.compatibility", OpenGlProfile.COMPATIBILITY.translationKey());
    }
}
