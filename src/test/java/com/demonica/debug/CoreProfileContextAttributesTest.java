package com.demonica.debug;

import net.minecraftforge.common.ForgeEarlyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreProfileContextAttributesTest {
    private int originalMajor;
    private int originalMinor;
    private boolean originalCompatProfile;
    private boolean originalDebugContext;

    @BeforeEach
    void saveForgeEarlyConfig() {
        originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
        originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
        originalCompatProfile = ForgeEarlyConfig.OPENGL_COMPAT_PROFILE;
        originalDebugContext = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
    }

    @AfterEach
    void restoreForgeEarlyConfig() {
        ForgeEarlyConfig.OPENGL_VERSION_MAJOR = originalMajor;
        ForgeEarlyConfig.OPENGL_VERSION_MINOR = originalMinor;
        ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = originalCompatProfile;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = originalDebugContext;
    }

    @Test
    void appliesForgeEarlyCoreProfileForCleanroomWindowCreation() {
        CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, 1, true);

        assertEquals(4, ForgeEarlyConfig.OPENGL_VERSION_MAJOR);
        assertEquals(1, ForgeEarlyConfig.OPENGL_VERSION_MINOR);
        assertFalse(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE);
        assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
    }

    @Test
    void restoresTheOriginalProfileAfterCoreContextCreation() {
        ForgeEarlyConfig.OPENGL_VERSION_MAJOR = 4;
        ForgeEarlyConfig.OPENGL_VERSION_MINOR = 6;
        ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = true;
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;

        CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, 1, true);
        assertFalse(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE);

        CoreProfileContextAttributes.restoreForgeEarlyProfile(4, 6, true, false);

        assertEquals(4, ForgeEarlyConfig.OPENGL_VERSION_MAJOR);
        assertEquals(6, ForgeEarlyConfig.OPENGL_VERSION_MINOR);
        assertTrue(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE,
            "The compatibility profile must be restored so Cleanroom can start without Demonica");
        assertFalse(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
    }

    @Test
    void keepsTheUsersOwnCoreProfileChoiceOnRestore() {
        // OPENGL_COMPAT_PROFILE is user-editable in Forge's config GUI; restore it, don't force it on.
        CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, 6, false);

        CoreProfileContextAttributes.restoreForgeEarlyProfile(3, 3, false, true);

        assertEquals(3, ForgeEarlyConfig.OPENGL_VERSION_MAJOR);
        assertEquals(3, ForgeEarlyConfig.OPENGL_VERSION_MINOR);
        assertFalse(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE);
        assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
    }

    @Test
    void raisesTheDebugContextFlagOnlyWhenItWasOff() {
        ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
        assertFalse(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(false));
        assertFalse(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);

        assertTrue(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(true));
        assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);

        // Already on (the user's own forge_early.cfg setting): not Demonica's to lower afterwards.
        assertFalse(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(true));
        CoreProfileContextAttributes.restoreForgeEarlyDebugContext(false);
        assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
    }
}
