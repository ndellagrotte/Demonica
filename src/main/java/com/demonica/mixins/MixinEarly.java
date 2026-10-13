package com.demonica.mixins;

import com.demonica.DemonicaIrisBridge;
import com.demonica.loading.Environment;
import com.demonica.loading.bundle.BundledCeleritasHook;
import com.demonica.loading.fml.transformers.GnetumHudCachingCompatTransformer;
import com.demonica.loading.fml.transformers.MacDisplayForwardCompatTransformer;
import com.demonica.loading.fml.transformers.StellarCoreHudCachingCompatTransformer;
import net.coderbot.iris.debug.IrisDebugOptions;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Demonica's coremod. It registers the GL redirector, the class transformers in {@code loading.fml.transformers} and
 * the early mixin configs, but only on a client where upstream Celeritas is present and Actinium is not: without
 * Celeritas, FML's missing-mods screen must be what the player sees, and with Actinium, the {@code @Mod} reports the
 * conflict ({@link com.demonica.Demonica}). It never loads a Celeritas class: Celeritas's jar may reach the class
 * loader after this coremod is constructed.
 *
 * <p>Celeritas normally comes from Demonica's own jar ({@link BundledCeleritasHook}); {@link #injectData} makes sure
 * FML identifies only one Celeritas mod even when that hook did not run.
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
        "mixins.demonica.celeritas.json",
        // Kirino Engine (part of Cleanroom) loads its config hub before late configs are queued; the config's plugin
        // applies the pin only when that class is transformed.
        "mixins.demonica.kirino.json",
        // HBM's coremod loads TileEntityRendererDispatcher before late configs are queued; inert without HBM.
        "mixins.demonica.hbm.early.json"
    );

    /** Whether this environment can run Demonica at all; decided once, before anything is registered. */
    public static final boolean ACTIVE;

    static {
        // Demonica and Celeritas render; a dedicated server has nothing for them to do, and no client classes to patch.
        boolean client = FMLLaunchHandler.side().isClient();
        boolean celeritas = Environment.isCeleritasPresent();
        boolean actinium = Environment.isActiniumPresent();
        if (!client) {
            LOGGER.info("Dedicated server: Demonica is client-only and stays inactive.");
        } else if (actinium) {
            LOGGER.error("Actinium is installed. Demonica replaces it and cannot run next to it; Demonica stays inactive. "
                + "Remove one of the two mods.");
        } else if (!celeritas) {
            String bundled = BundledCeleritasHook.bundledJarName();
            if (bundled != null) {
                LOGGER.error("The Celeritas bundled with Demonica, {}, was not extracted to mods/1.12.2, and no other Celeritas "
                    + "is installed; Demonica stays inactive. Check that -Dfml.skipContainedDeps does not name it, or delete "
                    + "mods/1.12.2/{} if it is there but damaged, so that it is extracted again.", bundled, bundled);
            } else {
                LOGGER.error("Celeritas is not installed. Demonica needs it; Demonica stays inactive until it is added.");
            }
        }
        ACTIVE = client && celeritas && !actinium;
        File staleS8tnlib = Environment.staleS8tnlibJar();
        if (staleS8tnlib != null) {
            // Harmless while its GTNHLib classes are the ones Demonica carries, wrong once they differ.
            LOGGER.warn("{} can be removed from the mods folder: Demonica carries S8TNLib's GTNHLib itself again, and "
                + "that jar only adds a second copy of the same classes.", staleS8tnlib.getName());
        }

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
            // StellarCore's and Gnetum's HUD caches, bridged to GLSM; each touches only its one target class.
            StellarCoreHudCachingCompatTransformer.class.getName(),
            GnetumHudCachingCompatTransformer.class.getName(),
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
        // Before the ACTIVE check: two Celeritas mods crash the launch whether Demonica runs or not.
        try {
            reconcileCeleritas();
        } catch (Throwable t) {
            LOGGER.error("Could not check FML's list of Celeritas mods against the Celeritas bundled with Demonica", t);
        }
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

    /**
     * Makes FML identify exactly one Celeritas mod. FML calls this after every coremod is discovered and before mods
     * are identified, and identifies a jar unless its name is on {@link CoreModManager#getIgnoredMods()} and not on
     * {@link CoreModManager#getReparseableCoremods()}. The bundled copy's hook normally keeps every other Celeritas off
     * the loader's list of candidates, and these lists are made to agree with it; when another mod's
     * {@code Bansoukou} hook sorted first, the hook never ran at all, and these lists are all that is left. Its
     * decision is used when there is one; otherwise the bundled copy is used on a client whenever it was extracted.
     * Its name sorts before any jar a player installs, so it also won the class path, but not necessarily over a copy
     * an earlier Demonica extracted: those names differ only in a hash. Without the hook, those copies are deleted
     * here, so that the next launch is right.
     */
    private static void reconcileCeleritas() {
        String bundled = BundledCeleritasHook.bundledJarName();
        if (bundled == null) {
            // A development workspace, or a build without a nested Celeritas: whatever is installed is what loads.
            return;
        }
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        List<File> jars = Environment.celeritasJarsInModsFolders();
        List<String> present = jars.stream().map(File::getName).toList();
        boolean useBundled = decision != null ? decision.useBundled() : FMLLaunchHandler.side().isClient() && present.contains(bundled);
        List<String> changed = BundledCeleritasHook.reconcile(CoreModManager.getReparseableCoremods(),
            CoreModManager.getIgnoredMods(), present, bundled, useBundled);
        if (decision == null) {
            // The hook deletes stale copies itself; without it they would stay, and win the class path again next time.
            removeStaleCopies(jars, bundled, useBundled);
        }
        if (changed.isEmpty()) {
            return;
        }
        if (decision == null && useBundled) {
            LOGGER.warn("Demonica's Celeritas hook did not run (another mod's Bansoukou hook ran first, or it failed); ignoring "
                + "{} so FML does not load two Celeritas mods. Remove them from the mods folder.", changed);
        } else {
            // The hook already reported these, or the bundled copy is the one left out (a dedicated server, which needs
            // no advice).
            LOGGER.debug("Ignoring {} so that FML identifies only one Celeritas mod", changed);
        }
    }

    /**
     * Deletes the copies of Celeritas an earlier Demonica extracted, when the hook did not run. The loader orders the
     * class path by lower-cased file name, so a stale copy whose name sorts before the bundled one already supplied
     * this launch's Celeritas classes; that cannot be undone, and the player has to restart.
     */
    private static void removeStaleCopies(List<File> jars, String bundled, boolean useBundled) {
        List<String> removed = BundledCeleritasHook.deleteStaleCopies(jars, bundled);
        if (removed.isEmpty()) {
            return;
        }
        String bundledKey = bundled.toLowerCase(Locale.ROOT);
        List<String> first = removed.stream().filter(name -> name.toLowerCase(Locale.ROOT).compareTo(bundledKey) < 0).toList();
        if (useBundled && !first.isEmpty()) {
            LOGGER.warn("Demonica's Celeritas hook did not run, and {} sorted before the Celeritas bundled with Demonica, {}: "
                + "this launch's Celeritas classes came from a copy an earlier Demonica extracted. That copy was deleted (or "
                + "is deleted when the game exits); restart the game to use the bundled Celeritas.", first, bundled);
        } else {
            LOGGER.info("Removed {} copies of Celeritas an earlier Demonica extracted: {}", removed.size(), removed);
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
