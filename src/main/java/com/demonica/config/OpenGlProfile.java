package com.demonica.config;

import java.util.Locale;

/**
 * The OpenGL context Demonica starts on ({@code advanced.opengl_profile}). {@link #COMPATIBILITY} is Cleanroom's own
 * context, created from {@code forge_early.cfg}; {@link #CORE} is the 3.3+ core context Demonica requests itself.
 *
 * <p>Top-level and free of GLSM imports, because the display mixin resolves it before the GL context exists, without
 * loading {@link DemonicaOptions}.
 */
public enum OpenGlProfile {
    /** Compatibility on Windows and Linux; core on macOS, whose compatibility profile stops at OpenGL 2.1. */
    AUTO,
    COMPATIBILITY,
    CORE;

    /** The lower-case name, as logs and the translation keys spell it. */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "sodium.options.opengl_profile." + this.id();
    }

    /** Reads a profile name in any case; a missing or unknown name is {@link #AUTO}. */
    public static OpenGlProfile parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        for (OpenGlProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(value.trim())) {
                return profile;
            }
        }
        return AUTO;
    }

    /** The profile to request: {@link #AUTO} becomes the platform's, anything else stays. */
    public OpenGlProfile forPlatform(boolean macos) {
        if (this != AUTO) {
            return this;
        }
        return macos ? CORE : COMPATIBILITY;
    }
}
