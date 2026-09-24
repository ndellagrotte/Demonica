package com.demonica.mixins;

import com.demonica.DemonicaIrisBridge;
import com.demonica.loading.Environment;
import com.demonica.loading.fml.transformers.MacDisplayForwardCompatTransformer;
import net.coderbot.iris.debug.IrisDebugOptions;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demonica's coremod. It registers the GL redirector and the early mixin configs, but only when upstream Celeritas is
 * installed and Actinium is not: without Celeritas, FML's missing-mods screen must be what the player sees, and with
 * Actinium, the {@code @Mod} reports the conflict ({@link com.demonica.Demonica}). It never loads a Celeritas class.
 */
@IFMLLoadingPlugin.Name("Demonica")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class MixinEarly implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");
    private static final String LATE_TWEAKER = "com.gtnewhorizons.angelica.loading.fml.tweakers.AngelicaLateTweaker";

    private static final List<String> MIXIN_CONFIGS = List.of(
        "mixins.demonica.core.json",
        // Iris's hooks into vanilla rendering.
        "mixins.demonica.iris.json",
        // The quarantine: every patch against Celeritas's own classes (docs/celeritas/LEDGER.md).
        "mixins.demonica.celeritas.json"
    );

    /** Whether this environment can run Demonica at all; decided once, before anything is registered. */
    public static final boolean ACTIVE;

    static {
        boolean celeritas = Environment.isCeleritasPresent();
        boolean actinium = Environment.isActiniumPresent();
        if (actinium) {
            LOGGER.error("Actinium is installed. Demonica replaces it and cannot run next to it; Demonica stays inactive. "
                + "Remove one of the two mods.");
        } else if (!celeritas) {
            LOGGER.error("Celeritas is not installed. Demonica needs it; Demonica stays inactive until it is added.");
        }
        ACTIVE = celeritas && !actinium;

        // Iris.enabled is a static final read from this bridge, so it must be in place before Iris is initialized.
        IrisDebugOptions.setBridge(new DemonicaIrisBridge());

        // CeleritasExtra uses Java 11 nesting features in its mixins.
        MixinEnvironment.setCompatibilityLevel(MixinEnvironment.CompatibilityLevel.JAVA_11);
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        if (!ACTIVE) {
            return new String[0];
        }
        return new String[] {
            MacDisplayForwardCompatTransformer.class.getName(),
            "com.gtnewhorizons.angelica.loading.fml.transformers.EarlyRedirectorTransformer"
        };
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void injectData(Map<String, Object> data) {
        if (!ACTIVE) {
            return;
        }
        Object value = Launch.blackboard.get("TweakClasses");
        List<String> tweaks = value instanceof List<?> ? (List<String>) value : null;
        if (tweaks == null) {
            tweaks = new ArrayList<>();
            Launch.blackboard.put("TweakClasses", tweaks);
        }
        if (!tweaks.contains(LATE_TWEAKER)) {
            tweaks.add(LATE_TWEAKER);
        }
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return getEarlyMixinConfigs();
    }

    public static List<String> getEarlyMixinConfigs() {
        return ACTIVE ? MIXIN_CONFIGS : List.of();
    }
}
