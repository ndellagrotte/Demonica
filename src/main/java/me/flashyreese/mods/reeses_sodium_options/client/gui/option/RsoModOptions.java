package me.flashyreese.mods.reeses_sodium_options.client.gui.option;

import com.demonica.gui.rso.compat.Component;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import me.flashyreese.mods.reeses_sodium_options.client.gui.theme.DemonicaTheme;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.List;
import java.util.Objects;

/**
 * The mod-options aggregation model: groups a list of Celeritas
 * {@link OptionPage}s by modId and exposes the configId/name/version/icon/
 * theme/pages view the RSO tab rail consumes. Name, version and icon come
 * from {@link RsoModMetadata} (resolved by the host from FML mod metadata or
 * the embedded-component fallback table); no Sodium config model is involved.
 */
public final class RsoModOptions {
    private final String configId;
    private final String name;
    private final String version;
    private final ResourceLocation icon;
    private final boolean iconMonochrome;
    private final DemonicaTheme theme;
    private final List<RsoPage> pages;

    private RsoModOptions(String configId, String name, String version,
                          ResourceLocation icon, boolean iconMonochrome,
                          DemonicaTheme theme, List<RsoPage> pages) {
        this.configId = configId;
        this.name = name;
        this.version = version;
        this.icon = icon;
        this.iconMonochrome = iconMonochrome;
        this.theme = theme;
        this.pages = List.copyOf(pages);
    }

    /** Builds the RSO view from Celeritas pages and resolved mod metadata. */
    public static RsoModOptions create(String configId, RsoModMetadata metadata, List<OptionPage> pages) {
        Objects.requireNonNull(configId, "configId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("Cannot build options view without pages for '" + configId + "'");
        }
        List<RsoPage> rsoPages = pages.stream().map(RsoPage::new).toList();
        return new RsoModOptions(configId, metadata.name(), metadata.version(), metadata.icon(),
                metadata.iconMonochrome(), DemonicaTheme.defaultFor(configId), rsoPages);
    }

    /** Expands back to the underlying Celeritas pages (for apply/undo walks). */
    public void unwrapPages(List<OptionPage> target) {
        for (RsoPage page : this.pages) {
            target.add(page.delegate);
        }
    }

    public String configId() {
        return this.configId;
    }

    public String name() {
        return this.name;
    }

    public String version() {
        return this.version;
    }

    public ResourceLocation icon() {
        return this.icon;
    }

    public boolean iconMonochrome() {
        return this.iconMonochrome;
    }

    public DemonicaTheme theme() {
        return this.theme;
    }

    public List<RsoPage> pages() {
        return this.pages;
    }

    /** RSO view of a page: wraps a Celeritas OptionPage, converting text to 1.12.2 ITextComponent. */
    public static final class RsoPage {
        private final OptionPage delegate;

        RsoPage(OptionPage delegate) {
            this.delegate = delegate;
        }

        public ITextComponent name() {
            return convertText(this.delegate.getName());
        }

        public List<RsoOptionGroup> groups() {
            return this.delegate.getGroups().stream()
                    .map(RsoOptionGroup::new)
                    .toList();
        }

        /** Returns the underlying Celeritas page (for tab building and apply/undo walks). */
        public OptionPage unwrap() {
            return this.delegate;
        }
    }

    /** RSO view of an option group: wraps a Celeritas OptionGroup. */
    public static final class RsoOptionGroup {
        private final OptionGroup delegate;

        RsoOptionGroup(OptionGroup delegate) {
            this.delegate = delegate;
        }

        public List<Option<?>> options() {
            return this.delegate.getOptions();
        }
    }

    private static ITextComponent convertText(TextComponent component) {
        return Component.fromEmbeddium(component).unwrap();
    }
}
