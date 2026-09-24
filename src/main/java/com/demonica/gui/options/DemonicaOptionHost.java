package com.demonica.gui.options;

import me.flashyreese.mods.reeses_sodium_options.client.gui.option.FmlRsoModMetadataResolver;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoModMetadataResolver;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoModOptions;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gui.options.CommonOptionPages;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.impl.gui.SodiumGameOptionPages;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The options behind one Reese's Sodium Options screen: Celeritas's own pages, as its video settings screen builds
 * them, and the pages that {@link OptionGUIConstructionEvent} listeners add (Demonica's among them, see
 * {@link com.demonica.gui.DemonicaOptionPages}), grouped by mod into {@link RsoModOptions}. It applies and undoes
 * pending changes and runs their side effects.
 *
 * <p>The pages are built anew for every screen, as Celeritas does: option ranges and availability that depend on the
 * window, the GL context or the active shader pack are current each time.
 */
public final class DemonicaOptionHost {
    private static final Logger LOGGER = LogManager.getLogger("Demonica-OptionHost");

    private final DemonicaApplyActions applyActions;
    private final List<RsoModOptions> modOptions;

    DemonicaOptionHost(DemonicaApplyActions applyActions, RsoModMetadataResolver metadataResolver, List<OptionPage> pages) {
        this.applyActions = applyActions;
        this.modOptions = group(pages, metadataResolver);
    }

    /** Collects the pages for a new screen. */
    public static DemonicaOptionHost collect() {
        Minecraft client = Minecraft.getMinecraft();
        List<OptionPage> pages = OptionGUIConstructionBridge.collect(celeritasPages(), OptionGUIConstructionEvent.BUS::post);
        return new DemonicaOptionHost(new DemonicaApplyActionsImpl(client),
                FmlRsoModMetadataResolver.forClient(client.getResourceManager()), pages);
    }

    /** The pages of Celeritas's own video settings screen ({@code CeleritasVideoOptionsScreen}), in its order. */
    static List<OptionPage> celeritasPages() {
        return List.of(
                SodiumGameOptionPages.general(),
                SodiumGameOptionPages.quality(),
                CommonOptionPages.performance(CeleritasVintage.options()),
                SodiumGameOptionPages.advanced());
    }

    private static List<RsoModOptions> group(List<OptionPage> pages, RsoModMetadataResolver metadataResolver) {
        Map<String, List<OptionPage>> byMod = new LinkedHashMap<>();
        for (OptionPage page : pages) {
            byMod.computeIfAbsent(page.getId().getModId(), ignored -> new ArrayList<>()).add(page);
        }
        List<RsoModOptions> result = new ArrayList<>();
        for (Map.Entry<String, List<OptionPage>> entry : byMod.entrySet()) {
            result.add(RsoModOptions.create(entry.getKey(), metadataResolver.resolve(entry.getKey()), entry.getValue()));
        }
        LOGGER.debug("Collected {} option page groups: {}", result.size(),
                result.stream().map(RsoModOptions::configId).toList());
        return List.copyOf(result);
    }

    public List<RsoModOptions> modOptions() {
        return this.modOptions;
    }

    /** Applies all pending changes, saves each changed storage once and runs the flags' side effects once. */
    public void applyChanges() {
        Set<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        Set<OptionStorage<?>> dirtyStorages = new HashSet<>();
        for (OptionPage page : this.allPages()) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    option.applyChanges();
                    flags.addAll(option.getFlags());
                    dirtyStorages.add(option.getStorage());
                }
            }
        }
        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save(flags);
        }
        this.applyFlagSideEffects(flags);
    }

    /** Discards all pending changes (back to the applied values). */
    public void undoChanges() {
        for (OptionPage page : this.allPages()) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    option.reset();
                }
            }
        }
    }

    public boolean hasPendingChanges() {
        for (OptionPage page : this.allPages()) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<OptionPage> allPages() {
        List<OptionPage> pages = new ArrayList<>();
        for (RsoModOptions options : this.modOptions) {
            options.unwrapPages(pages);
        }
        return pages;
    }

    private void applyFlagSideEffects(Set<OptionFlag> flags) {
        if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            this.applyActions.reloadRenderer();
        } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
            this.applyActions.updateRenderer();
        }
        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            this.applyActions.reloadAssets();
        }
        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            this.applyActions.showRestartRequired();
        }
    }
}
