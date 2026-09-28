package com.demonica.debug;

import net.minecraftforge.common.ForgeEarlyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreProfileContextAttributesTest {
    @Test
    void configuresTheOriginalContextInRequiredOrder() {
        RecordingContext attributes = new RecordingContext();

        RecordingContext configured = CoreProfileContextAttributes.configure(
            attributes,
            value -> value.record("core"),
            value -> value.record("forward"),
            value -> value.record("debug")
        );

        assertSame(attributes, configured);
        assertEquals(List.of("core", "forward", "debug"), attributes.operations());
    }

    @Test
    void appliesForgeEarlyCoreProfileForLwjglxx() {
        int originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
        int originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
        boolean originalCompatProfile = ForgeEarlyConfig.OPENGL_COMPAT_PROFILE;
        boolean originalDebugContext = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
        String originalForwardCompat = System.getProperty(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY);

        try {
            CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, 1, true);

            assertEquals(4, ForgeEarlyConfig.OPENGL_VERSION_MAJOR);
            assertEquals(1, ForgeEarlyConfig.OPENGL_VERSION_MINOR);
            assertFalse(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE);
            assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
            assertTrue(Boolean.getBoolean(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY),
                "the macOS forward-compatible hint is on while a core context is requested");
        } finally {
            ForgeEarlyConfig.OPENGL_VERSION_MAJOR = originalMajor;
            ForgeEarlyConfig.OPENGL_VERSION_MINOR = originalMinor;
            ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = originalCompatProfile;
            ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = originalDebugContext;
            restoreProperty(originalForwardCompat);
        }
    }

    @Test
    void restoresCompatProfileForLwjglxxAfterCoreContextCreation() {
        int originalMajor = ForgeEarlyConfig.OPENGL_VERSION_MAJOR;
        int originalMinor = ForgeEarlyConfig.OPENGL_VERSION_MINOR;
        boolean originalCompatProfile = ForgeEarlyConfig.OPENGL_COMPAT_PROFILE;
        boolean originalDebugContext = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;
        String originalForwardCompat = System.getProperty(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY);

        try {
            CoreProfileContextAttributes.applyForgeEarlyCoreProfile(4, 6, true);
            assertFalse(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE);

            CoreProfileContextAttributes.restoreForgeEarlyCompatProfile(4, 6, false);

            assertEquals(4, ForgeEarlyConfig.OPENGL_VERSION_MAJOR);
            assertEquals(6, ForgeEarlyConfig.OPENGL_VERSION_MINOR);
            assertTrue(ForgeEarlyConfig.OPENGL_COMPAT_PROFILE,
                "The compatibility profile must be restored so Cleanroom can start without Demonica");
            assertFalse(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
            assertNull(System.getProperty(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY),
                "the forward-compatible hint must not reach a later compatibility request");
        } finally {
            ForgeEarlyConfig.OPENGL_VERSION_MAJOR = originalMajor;
            ForgeEarlyConfig.OPENGL_VERSION_MINOR = originalMinor;
            ForgeEarlyConfig.OPENGL_COMPAT_PROFILE = originalCompatProfile;
            ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = originalDebugContext;
            restoreProperty(originalForwardCompat);
        }
    }

    @Test
    void raisesTheDebugContextFlagOnlyWhenItWasOff() {
        boolean originalDebugContext = ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT;

        try {
            ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = false;
            assertFalse(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(false));
            assertFalse(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);

            assertTrue(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(true));
            assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);

            // Already on (the user's own forge_early.cfg setting): not Demonica's to lower afterwards.
            assertFalse(CoreProfileContextAttributes.raiseForgeEarlyDebugContext(true));
            CoreProfileContextAttributes.restoreForgeEarlyDebugContext(false);
            assertTrue(ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT);
        } finally {
            ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT = originalDebugContext;
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY);
        } else {
            System.setProperty(CoreProfileContextAttributes.FORWARD_COMPAT_PROPERTY, value);
        }
    }

    private static final class RecordingContext {
        private final List<String> operations = new ArrayList<>();

        private void record(String operation) {
            operations.add(operation);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }
}
