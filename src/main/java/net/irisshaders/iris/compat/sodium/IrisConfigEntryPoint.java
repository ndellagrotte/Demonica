package net.irisshaders.iris.compat.sodium;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Exposes the supported Iris settings through Celeritas's option model.
 * The entrypoint deliberately binds only the legacy Iris shadow distance
 * value; shader pack selection is Celeritas's own "Shader Packs" tab, which
 * opens Iris's screen through {@code IrisApi}.
 */
public final class IrisConfigEntryPoint {
    private static final Logger LOGGER = LogManager.getLogger("Demonica-Iris-Config");
    private static final String MOD_ID = "iris";
    private static final int SHADOW_DISTANCE_MIN = 0;
    private static final int SHADOW_DISTANCE_MAX = 256;

    private final Consumer<Integer> shadowSaver;
    private final Supplier<Integer> shadowLoader;

    /** Creates the production entrypoint backed by Iris' legacy settings. */
    public IrisConfigEntryPoint() {
        this(value -> {
            IrisVideoSettings.shadowDistance = value;
            saveIrisConfig();
        }, () -> IrisVideoSettings.shadowDistance);
    }

    /** Allows direct logic tests to supply persistence without client bootstrapping. */
    public IrisConfigEntryPoint(Consumer<Integer> shadowSaver, Supplier<Integer> shadowLoader) {
        this.shadowSaver = Objects.requireNonNull(shadowSaver, "Iris shadow saver must not be null");
        this.shadowLoader = Objects.requireNonNull(shadowLoader, "Iris shadow loader must not be null");
    }

    /** Builds the Iris option page (shadow distance slider). */
    public OptionPage createPage() {
        OptionImpl<Object, Integer> shadow = OptionImpl.createBuilder(int.class, new IrisStorage())
                .setId(OptionIdentifier.create(MOD_ID, "shadow_distance", int.class))
                .setName(TextComponent.translatable("options.iris.shadowDistance"))
                .setTooltip(TextComponent.translatable(IrisVideoSettings.isShadowDistanceSliderEnabled()
                        ? "options.iris.shadowDistance.enabled"
                        : "options.iris.shadowDistance.disabled"))
                .setControl(option -> new SliderControl(option,
                        SHADOW_DISTANCE_MIN, SHADOW_DISTANCE_MAX, 1, ControlValueFormatter.number()))
                .setBinding((ignored, value) -> this.shadowSaver.accept(value), ignored -> this.shadowLoader.get())
                .setEnabledPredicate(() -> Iris.enabled)
                .build();

        OptionGroup group = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "shadow"))
                .add(shadow)
                .build();

        return new OptionPage(
                OptionIdentifier.create(MOD_ID, "video_settings"),
                TextComponent.translatable("options.iris.title"),
                List.of(group));
    }

    private static void saveIrisConfig() {
        try {
            Iris.getIrisConfig().save();
        } catch (IOException exception) {
            LOGGER.error("Failed to save Iris configuration after applying options", exception);
        }
    }

    /** Placeholder storage: Iris values read/write IrisVideoSettings; save persists to disk. */
    private static final class IrisStorage implements OptionStorage<Object> {
        @Override
        public Object getData() {
            return this;
        }

        @Override
        public void save() {
            saveIrisConfig();
        }
    }
}
