package com.demonica.debug;

import com.demonica.config.OpenGlProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads the options needed before the display exists, and so before the normal runtime configuration is available:
 * the OpenGL profile and the LWJGL debug context.
 */
public final class DemonicaStartupConfig {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaStartupConfig");
    private static final Path CONFIG_PATH = Paths.get("config", "demonica-options.json");
    private static final Path LEGACY_CONFIG_PATH = Paths.get("config", "actinium-options.json");
    static final String OPENGL_PROFILE_PROPERTY = "demonica.openglProfile";
    private static final Snapshot SNAPSHOT = loadSnapshot();
    private static final boolean LWJGL_DEBUG = resolveLwjglDebug(System.getProperty("demonica.lwjglDebug"), SNAPSHOT.enableLwjglDebug);
    private static final OpenGlProfile OPENGL_PROFILE = resolveOpenGlProfile(System.getProperty(OPENGL_PROFILE_PROPERTY), SNAPSHOT.openGlProfile);

    private DemonicaStartupConfig() {
    }

    /**
     * Returns whether startup must request an OpenGL debug context and install its driver callback.
     * This is intentionally separate from the runtime GL state checkpoint option.
     */
    public static boolean enableLwjglDebug() {
        return LWJGL_DEBUG;
    }

    /** The configured OpenGL profile, before {@link OpenGlProfile#forPlatform} resolves {@code AUTO}. */
    public static OpenGlProfile openGlProfile() {
        return OPENGL_PROFILE;
    }

    static boolean resolveLwjglDebug(String override, boolean configured) {
        return override != null ? Boolean.parseBoolean(override) : configured;
    }

    /** {@code -Ddemonica.openglProfile} beats the file; an unknown override is {@code AUTO}, with a warning. */
    static OpenGlProfile resolveOpenGlProfile(String override, String configured) {
        if (override == null) {
            return OpenGlProfile.parse(configured);
        }
        OpenGlProfile profile = OpenGlProfile.parse(override);
        if (profile == OpenGlProfile.AUTO && !OpenGlProfile.AUTO.name().equalsIgnoreCase(override.trim())) {
            LOGGER.warn("Unknown -D{}={}; using auto (expected auto, compatibility or core)", OPENGL_PROFILE_PROPERTY, override);
        }
        return profile;
    }

    private static Snapshot loadSnapshot() {
        return readSnapshot(Files.isRegularFile(CONFIG_PATH) ? CONFIG_PATH : LEGACY_CONFIG_PATH);
    }

    static Snapshot readSnapshot(Path path) {
        if (!Files.isRegularFile(path)) {
            return Snapshot.DEFAULT;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return Snapshot.DEFAULT;
            }

            JsonObject debug = getObject(root.getAsJsonObject(), "debug");
            JsonObject advanced = getObject(root.getAsJsonObject(), "advanced");
            return new Snapshot(
                debug != null && getBoolean(debug, "enable_lwjgl_debug"),
                advanced != null ? getString(advanced, "opengl_profile") : null
            );
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read startup options from {}", path, e);
            return Snapshot.DEFAULT;
        }
    }

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    record Snapshot(boolean enableLwjglDebug, String openGlProfile) {
        static final Snapshot DEFAULT = new Snapshot(false, null);
    }
}
