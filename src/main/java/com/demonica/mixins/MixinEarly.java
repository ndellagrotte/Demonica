package com.demonica.mixins;

import com.demonica.DemonicaIrisBridge;
import com.demonica.loading.Environment;
import com.demonica.loading.bundle.BundledCeleritasHook;
import com.demonica.loading.fml.transformers.GnetumHudCachingCompatTransformer;
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
import java.util.Map;

/**
 * Demonica's coremod. It registers the GL redirector, the class transformers in {@code loading.fml.transformers} and
 * the early mixin configs, but only on a client where upstream Celeritas is present and Actinium is not: without
 * Celeritas, FML's missing-mods screen must be what the player sees, and with Actinium, the {@code @Mod} reports the
 * conflict ({@link com.demonica.Demonica}). It never loads a Celeritas class: Celeritas's jar may reach the class
 * loader after this coremod is constructed.
 *
 * <p>Celeritas comes from Demonica's own jar unless the player installed one, which takes precedence
 * ({@link BundledCeleritasHook}); {@link #injectData} makes sure FML identifies only one Celeritas mod even when that
 * hook did not run.
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
                LOGGER.error("The Celeritas bundled with Demonica, {}, was not extracted to {}, and no other Celeritas "
                    + "is installed; Demonica stays inactive. Check that -Dfml.skipContainedDeps does not name it, or delete "
                    + "that file if it is there but damaged, so that it is extracted again.", bundled,
                    BundledCeleritasHook.extractedPath(bundled));
            } else {
                LOGGER.error("Celeritas is not installed. Demonica needs it; Demonica stays inactive until it is added.");
            }
        }
        ACTIVE = client && celeritas && !actinium;
        if (ACTIVE) {
            // The bundled copy sits in mods/memory_repo, which the loader puts on the class path only when it reaches the
            // copy's own coremod, last of all candidates. This coremod is loaded at Demonica's own place in the order, where
            // NonModDeps put the copy when it was extracted to mods/1.12.2; adding it here keeps class resolution as it was.
            addBundledCeleritasToTheClassPath(BundledCeleritasHook.decision());
        }
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
     * {@link CoreModManager#getReparseableCoremods()}. The bundled copy's hook normally sorts the Celeritas jars out on
     * the loader's list of candidates, and these lists are made to agree with it. When another mod's {@code Bansoukou}
     * hook sorted first, the hook never ran at all, and these lists are all that is left: the same rules are applied to
     * the mods folders ({@link BundledCeleritasHook#decideWithoutHook}). A Celeritas the player installed takes
     * precedence either way, and the bundled copy is then the one ignored.
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
        boolean hookRan = decision != null;
        if (!hookRan) {
            // Deletes stale copies too: the hook deletes them itself, and without it they would stay.
            decision = BundledCeleritasHook.decideWithoutHook(jars, bundled, FMLLaunchHandler.side().isClient());
            if (ACTIVE) {
                addBundledCeleritasToTheClassPath(decision);
            }
        }
        List<String> changed = BundledCeleritasHook.reconcile(CoreModManager.getReparseableCoremods(),
            CoreModManager.getIgnoredMods(), present, bundled, decision.useBundled(), decision.droppedStale());
        if (hookRan) {
            if (!changed.isEmpty()) {
                // The hook already reported what it decided.
                LOGGER.debug("Ignoring {} so that FML identifies only one Celeritas mod", changed);
            }
            return;
        }
        String demonica = BundledCeleritasHook.demonicaJarName();
        // When the bundled copy won the class path, the report's "Using the installed ..." is the opposite of what
        // loaded; warnAboutTheClassPath says what did.
        boolean bundledWon = bundledWonOverTheInstalledJar(jars, bundled, decision, demonica);
        if (changed.isEmpty()) {
            if (bundledWon) {
                LOGGER.info("Demonica's Celeritas hook did not run (another mod's Bansoukou hook ran first, or it failed).");
            } else {
                LOGGER.info("Demonica's Celeritas hook did not run (another mod's Bansoukou hook ran first, or it failed); {}",
                    decision.report());
            }
        } else if (bundledWon) {
            LOGGER.warn("Demonica's Celeritas hook did not run (another mod's Bansoukou hook ran first, or it failed). "
                + "Ignoring {} so that FML identifies only one Celeritas mod.", changed);
        } else {
            LOGGER.warn("Demonica's Celeritas hook did not run (another mod's Bansoukou hook ran first, or it failed); {} "
                + "Ignoring {} so that FML identifies only one Celeritas mod.", decision.report(), changed);
        }
        warnAboutTheClassPath(jars, bundled, decision, demonica, bundledWon);
    }

    /**
     * Says which Celeritas classes this launch loaded when the hook did not run, if they are not the decided ones. The
     * loader adds jars to the class path in lower-cased file name order, and the first jar added wins a class. The
     * bundled copy in the repository is listed after every jar of the mods folders, so it never wins against an
     * installed jar; but a copy with its name left in {@code mods/1.12.2} by Demonica 0.8.2 or earlier is added by
     * Demonica's {@code NonModDeps} at the place of Demonica's own jar, so an installed Celeritas wins against that copy
     * only if its name sorts before Demonica's, and a stale copy whose name sorts before Demonica's wins over the bundled
     * one. Nothing can change that any more; the version gate judges whichever jar loaded, and the leftover is deleted
     * for the next launch.
     */
    private static void warnAboutTheClassPath(List<File> jars, String bundled, BundledCeleritasHook.Decision decision,
                                              @Nullable String demonica, boolean bundledWon) {
        if (!FMLLaunchHandler.side().isClient()) {
            // Celeritas is client-only: on a dedicated server no copy loads, so none won a race.
            return;
        }
        if (demonica == null || jars.stream().noneMatch(jar -> jar.getName().equals(bundled))) {
            // Without Demonica's own jar there is nothing to compare with, and without the bundled copy it never raced.
            return;
        }
        if (!decision.externals().isEmpty()) {
            if (bundledWon) {
                String external = String.join(" and ", decision.externals());
                LOGGER.warn("This launch loaded the bundled Celeritas classes from a copy left in mods/1.12.2 although {} is "
                    + "installed, because another mod's Bansoukou hook ran first and {} sorts after {}. That copy is deleted "
                    + "when the game exits (delete it by hand if it is still there); restart the game to use the installed jar.",
                    external, external, demonica);
            }
        } else if (decision.useBundled()) {
            List<String> first = BundledCeleritasHook.sortedBefore(decision.droppedStale(), demonica);
            if (!first.isEmpty()) {
                LOGGER.warn("{} sorted before {}: this launch's Celeritas classes came from a copy an earlier Demonica "
                    + "extracted. That copy was deleted (or is deleted when the game exits); restart the game to use the "
                    + "bundled Celeritas.", first, demonica);
            }
        }
    }

    /**
     * Whether, with the hook not run, the bundled copy's classes loaded although an installed Celeritas was chosen: on a
     * client, with a copy of the bundled name left in {@code mods/1.12.2} (the one {@code NonModDeps} resolves; it is
     * among the stale copies dropped), Demonica's own jar known, and every installed jar sorting after it
     * ({@link #warnAboutTheClassPath}).
     */
    private static boolean bundledWonOverTheInstalledJar(List<File> jars, String bundled, BundledCeleritasHook.Decision decision,
                                                         @Nullable String demonica) {
        return FMLLaunchHandler.side().isClient() && demonica != null && !decision.externals().isEmpty()
            && decision.droppedStale().contains(bundled)
            && BundledCeleritasHook.sortedBefore(decision.externals(), demonica).isEmpty();
    }

    /**
     * Adds the bundled copy to the launch class path when it is the Celeritas this launch uses. Cleanroom adds it again
     * when it reaches the copy's own coremod, which the class loader ignores as a duplicate.
     */
    private static void addBundledCeleritasToTheClassPath(@Nullable BundledCeleritasHook.Decision decision) {
        if (decision == null || !decision.useBundled() || decision.bundledFile() == null || Launch.classLoader == null) {
            return;
        }
        try {
            Launch.classLoader.addURL(decision.bundledFile().toURI().toURL());
        } catch (Exception e) {
            LOGGER.warn("Could not add {} to the class path now; Cleanroom adds it when it reaches its coremod",
                decision.bundledFile(), e);
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
