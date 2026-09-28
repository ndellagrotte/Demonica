package com.demonica.debug;

import com.demonica.config.OpenGlProfile;

/**
 * The OpenGL context this run started on, recorded once by the display mixin for the diagnostics.
 *
 * @param configured the profile from the options or {@code -Ddemonica.openglProfile}
 * @param requested the profile requested, with {@code AUTO} resolved for the platform
 * @param actualProfile what {@code GL_CONTEXT_PROFILE_MASK} reports: {@code core}, {@code compatibility} or
 *                      {@code unknown}
 * @param version the parsed {@code GL_VERSION}
 * @param versionString {@code GL_VERSION} as the driver reported it
 */
public record OpenGlContextReport(
    OpenGlProfile configured,
    OpenGlProfile requested,
    String actualProfile,
    OpenGlVersion version,
    String versionString
) {
    private static volatile OpenGlContextReport current;

    public static void record(OpenGlContextReport report) {
        current = report;
    }

    /** The context this run started on, or {@code null} before the display exists. */
    public static OpenGlContextReport current() {
        return current;
    }

    public String describe() {
        return "configured=" + this.configured.id()
            + " requested=" + this.requested.id()
            + " actual=" + this.actualProfile
            + " " + this.version.major() + "." + this.version.minor()
            + " (" + this.versionString + ")";
    }
}
