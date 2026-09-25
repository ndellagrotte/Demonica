package com.demonica.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

/**
 * Demonica's own settings, saved to {@code config/demonica-options.json}. Renderer settings belong to Celeritas and
 * are read from {@code CeleritasVintage.options()}; Actinium kept both in one forked {@code SodiumGameOptions}, so on
 * the first start without a Demonica file the Demonica-owned values are taken over once from
 * {@code config/actinium-options.json}.
 *
 * <p>It depends on no Celeritas class, so reading a setting never loads one; the options screen adapts it to
 * Celeritas's option API.
 */
public final class DemonicaOptions {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");

    public static final String FILE_NAME = "demonica-options.json";
    static final String ACTINIUM_FILE_NAME = "actinium-options.json";

    /**
     * Actinium settings that are not taken over. Actinium turned its own fast block renderer on by default; Demonica's
     * switch picks Celeritas's, which is a different renderer and starts off, like upstream's production setting
     * (docs/celeritas/LEDGER.md, S13).
     */
    private static final Set<String> ACTINIUM_KEYS_NOT_MIGRATED = Set.of("use_fast_block_renderer");

    /** Actinium's names for the keys that Demonica renamed; everything else keeps its name. */
    private static final Map<String, String> ACTINIUM_KEY_RENAMES = Map.of(
        "enable_actinium_gl_debug", "enable_gl_debug",
        "enable_actinium_perf_debug", "enable_perf_debug",
        "enable_actinium_gpu_perf_debug", "enable_gpu_perf_debug"
    );

    /**
     * Whether the video options screen shows the DEBUG page. Off by default and only reachable through the config
     * file, so the page cannot be re-enabled from the GUI it hides.
     */
    public boolean enableDebugTab = false;

    public final QualitySettings quality = new QualitySettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final DebugSettings debug = new DebugSettings();
    public final WindowSettings window = new WindowSettings();

    private boolean readOnly;
    private Path configPath;

    public static class QualitySettings {
        // Vanilla widens the field of view while sprinting or flying and narrows it while drawing a bow. That factor
        // reaches the projection only when getFOVModifier is called with useFOVSetting=true, so turning this off
        // drops the dynamic factor and keeps vanilla's underwater and death-camera scaling.
        public boolean dynamicFov = true;

        // Biome colour position noise (Actinium issue #56). Actinium applied it in its forked biome colour cache;
        // upstream Celeritas has no equivalent yet (docs/celeritas/LEDGER.md), so these are kept but not applied.
        public boolean useBiomeColorNoise = true;
        public float biomeColorNoiseGrassIntensity = 0.08F;
        public float biomeColorNoiseFoliageIntensity = 0.08F;
        public float biomeColorNoiseWaterIntensity = 0.08F;
    }

    public static class PerformanceSettings {
        public int loadingScreenFramerateLimit = 60;
        // Celeritas's fast block renderer. Upstream turns it on only in dev; Demonica sets it explicitly
        // (docs/celeritas/LEDGER.md, S13), off by default like upstream's production setting. Blocks that mods render
        // through vanilla's dispatcher stay on the vanilla path either way (FastBlockRendererCompat).
        public boolean useFastBlockRenderer = false;
    }

    public static class AdvancedSettings {
        public boolean allowDirectMemoryAccess = true;
        public boolean useModelRendererBatching = true;
        public boolean useModelRendererDisplayLists = true;
        public StreamingUploadStrategy streamingUploadStrategy = StreamingUploadStrategy.MAP_BUFFER_RANGE;
    }

    public static class DebugSettings {
        public boolean enableProductionDiagnostics = true;
        public boolean ignoreFramebufferErrors = false;
        public boolean enableGlDebug = false;
        public boolean enableLwjglDebug = false;
        public boolean enablePbrDebug = false;
        public boolean enableCloudControlDebug = false;
        public boolean enablePerfDebug = false;
        public boolean enableGpuPerfDebug = false;
        public boolean enableFrameGlErrorCheck = false;
        public boolean enablePostRenderGlErrorCheck = false;
        public boolean enableRedirectorDebug = false;
        public boolean enableRedirectorLogSpam = false;
        public boolean enableRedirectorClassDump = false;
    }

    public static class WindowSettings {
        public String fullscreenMode;
        public String lastFullscreenMode;
    }

    public enum StreamingUploadStrategy {
        BUFFER_DATA(StreamingUploader.UploadStrategy.BUFFER_DATA, "sodium.options.streaming_upload_strategy.buffer_data"),
        BUFFER_SUB_DATA(StreamingUploader.UploadStrategy.BUFFER_SUB_DATA, "sodium.options.streaming_upload_strategy.buffer_sub_data"),
        MAP_BUFFER_RANGE(StreamingUploader.UploadStrategy.MAP_BUFFER_RANGE, "sodium.options.streaming_upload_strategy.map_buffer_range");

        private final StreamingUploader.UploadStrategy glsmStrategy;
        private final String translationKey;

        StreamingUploadStrategy(StreamingUploader.UploadStrategy glsmStrategy, String translationKey) {
            this.glsmStrategy = glsmStrategy;
            this.translationKey = translationKey;
        }

        public StreamingUploader.UploadStrategy glsmStrategy() {
            return this.glsmStrategy;
        }

        public String translationKey() {
            return this.translationKey;
        }
    }

    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting()
        .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.TRANSIENT)
        .create();

    public static DemonicaOptions defaults() {
        DemonicaOptions options = new DemonicaOptions();
        options.configPath = configDir().resolve(FILE_NAME);
        return options;
    }

    public static DemonicaOptions load() {
        return load(configDir());
    }

    static DemonicaOptions load(Path dir) {
        Path path = dir.resolve(FILE_NAME);
        DemonicaOptions options;
        boolean save = true;

        if (Files.exists(path)) {
            options = read(path);
            if (options == null) {
                // Keep the unreadable file for the user to fix; run on defaults without overwriting it.
                options = new DemonicaOptions();
                save = false;
            }
        } else {
            options = migrateFromActinium(dir.resolve(ACTINIUM_FILE_NAME));
        }

        options.configPath = path;
        if (options.advanced.streamingUploadStrategy == null) {
            options.advanced.streamingUploadStrategy = StreamingUploadStrategy.MAP_BUFFER_RANGE;
        }

        if (save) {
            try {
                options.writeChanges();
            } catch (IOException e) {
                throw new RuntimeException("Couldn't write " + path, e);
            }
        }
        return options;
    }

    private static DemonicaOptions read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            DemonicaOptions options = GSON.fromJson(reader, DemonicaOptions.class);
            return options != null ? options : new DemonicaOptions();
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Could not parse {}; using default settings", path, e);
            return null;
        }
    }

    /**
     * Actinium's options file holds Demonica's settings next to Celeritas's. Its Demonica-owned keys are taken
     * over, renamed where Demonica renamed them; the file itself is left alone.
     */
    static DemonicaOptions migrateFromActinium(Path actiniumPath) {
        if (!Files.exists(actiniumPath)) {
            return new DemonicaOptions();
        }
        try (Reader reader = Files.newBufferedReader(actiniumPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                return new DemonicaOptions();
            }
            renameKeys(root.getAsJsonObject());
            DemonicaOptions options = GSON.fromJson(root, DemonicaOptions.class);
            LOGGER.info("Took over Demonica's settings from {}", actiniumPath);
            return options != null ? options : new DemonicaOptions();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {} for migration; using default settings", actiniumPath, e);
            return new DemonicaOptions();
        }
    }

    private static void renameKeys(JsonObject object) {
        for (String key : object.keySet().toArray(new String[0])) {
            JsonElement value = object.get(key);
            if (value.isJsonObject()) {
                renameKeys(value.getAsJsonObject());
            }
            if (ACTINIUM_KEYS_NOT_MIGRATED.contains(key)) {
                object.remove(key);
                continue;
            }
            String renamed = ACTINIUM_KEY_RENAMES.get(key);
            if (renamed != null) {
                object.remove(key);
                object.add(renamed, value);
            }
        }
    }

    private static Path configDir() {
        return Paths.get("config");
    }

    public void save() {
        try {
            this.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }
        LOGGER.info("Flushed changes to Demonica configuration");
    }

    public void writeChanges() throws IOException {
        if (this.readOnly) {
            throw new IllegalStateException("Config file is read-only");
        }
        Path dir = this.configPath.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        // Write next to the destination, then move over it atomically.
        Path tempPath = this.configPath.resolveSibling(this.configPath.getFileName() + ".tmp");
        Files.writeString(tempPath, GSON.toJson(this));
        Files.move(tempPath, this.configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public String getFileName() {
        return this.configPath.getFileName().toString();
    }
}
