package com.demonica.gui;

import com.demonica.runtime.DemonicaRuntime;
import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfigEntryPoint;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionDefaults;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.OptionGroupConstructionEvent;
import org.taumc.celeritas.api.OptionPageConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.api.options.structure.StandardOptions;
import org.taumc.celeritas.impl.gui.SodiumGameOptionPages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemonicaOptionPagesTest {
    @Test
    void fullscreenModeReplacesTheFullscreenToggleAndTheLoadingLimitFollowsMaxFramerate() {
        List<Option<?>> window = new ArrayList<>(List.of(
                placeholder(StandardOptions.Option.GUI_SCALE),
                placeholder(StandardOptions.Option.FULLSCREEN),
                placeholder(StandardOptions.Option.VSYNC),
                placeholder(StandardOptions.Option.MAX_FRAMERATE)));

        DemonicaOptionPages.onGroup(new OptionGroupConstructionEvent(StandardOptions.Group.WINDOW, window));

        assertEquals(List.of("minecraft:gui_scale", "demonica:fullscreen_mode", "minecraft:vsync", "minecraft:max_frame_rate",
                "demonica:loading_screen_framerate_limit"), ids(window));
    }

    @Test
    void optionsWhoseNeighboursAreMissingGoToTheEnd() {
        List<Option<?>> window = new ArrayList<>(List.of(placeholder(StandardOptions.Option.VSYNC)));

        DemonicaOptionPages.onGroup(new OptionGroupConstructionEvent(StandardOptions.Group.WINDOW, window));

        assertEquals(List.of("minecraft:vsync", "demonica:fullscreen_mode", "demonica:loading_screen_framerate_limit"), ids(window));
    }

    @Test
    void dynamicFovFollowsVignette() {
        List<Option<?>> details = new ArrayList<>(List.of(
                placeholder(StandardOptions.Option.CLOUDS),
                placeholder(StandardOptions.Option.VIGNETTE),
                placeholder(StandardOptions.Option.BIOME_BLEND)));

        DemonicaOptionPages.onGroup(new OptionGroupConstructionEvent(StandardOptions.Group.DETAILS, details));

        assertEquals(List.of("minecraft:clouds", "minecraft:vignette", "demonica:dynamic_fov", "minecraft:biome_blend"),
                ids(details));
    }

    @Test
    void otherGroupsAreLeftAlone() {
        List<Option<?>> graphics = new ArrayList<>(List.of(placeholder(StandardOptions.Option.GRAPHICS_MODE)));

        DemonicaOptionPages.onGroup(new OptionGroupConstructionEvent(StandardOptions.Group.GRAPHICS, graphics));

        assertEquals(List.of("minecraft:graphics_mode"), ids(graphics));
    }

    /** Celeritas's own quality page, built from the pinned jar: its fast renderer toggle becomes Demonica's (S13). */
    @Test
    void theFastBlockRendererToggleOnCeleritasQualityPageIsDemonicas() {
        DemonicaOptionPages.register();

        OptionGroup sorting = group(SodiumGameOptionPages.quality(), StandardOptions.Group.SORTING);

        List<String> ids = ids(sorting.getOptions());
        assertTrue(ids.contains("demonica:fast_block_renderer"), ids.toString());
        assertFalse(ids.contains("celeritas:fast_block_renderer"), ids.toString());
        Option<?> toggle = sorting.getOptions().get(ids.indexOf("demonica:fast_block_renderer"));
        assertEquals(Boolean.FALSE, OptionDefaults.get(toggle), "off by default, like upstream's production setting (S13)");
    }

    /** Celeritas's own advanced page, built from the pinned jar, with Demonica's fast paths and its two groups. */
    @Test
    void celeritasAdvancedPageCarriesDemonicasFastPaths() {
        DemonicaOptionPages.register();

        OptionPage advanced = SodiumGameOptionPages.advanced();

        assertEquals(List.of("celeritas:cpu_render_ahead_limit", "demonica:streaming_upload_strategy"),
                ids(group(advanced, StandardOptions.Group.CPU_SAVING).getOptions()));
        assertEquals(List.of("celeritas:cpu_saving", "demonica:direct_memory", "demonica:framebuffer_errors"),
                advanced.getGroups().stream().map(g -> g.getId().toString()).toList());
    }

    @Test
    void pagesAreAddedOnceAndTheDebugPageOnlyWhenEnabled() {
        boolean debugTab = DemonicaRuntime.options().enableDebugTab;
        try {
            // Stands in for RSO's own page, which needs FML's config directory to build.
            List<OptionPage> pages = new ArrayList<>(List.of(emptyPage(ReeseSodiumOptionsConfigEntryPoint.PAGE_ID)));

            DemonicaRuntime.options().enableDebugTab = false;
            DemonicaOptionPages.onGui(new OptionGUIConstructionEvent(pages));
            assertEquals(List.of("reeses-sodium-options:rso_options", "iris:video_settings", "iris:shader_pack_selection"), pageIds(pages));

            DemonicaRuntime.options().enableDebugTab = true;
            DemonicaOptionPages.onGui(new OptionGUIConstructionEvent(pages));
            DemonicaOptionPages.onGui(new OptionGUIConstructionEvent(pages));
            assertEquals(List.of("reeses-sodium-options:rso_options", "iris:video_settings", "iris:shader_pack_selection",
                    "demonica:debug"), pageIds(pages));
        } finally {
            DemonicaRuntime.options().enableDebugTab = debugTab;
        }
    }

    /** Every name, tooltip and value label of Demonica's options has an English, Russian and Chinese translation. */
    @Test
    void everyTranslationKeyIsInDemonicasLangFiles() throws IOException {
        List<Option<?>> options = new ArrayList<>();
        for (OptionIdentifier<Void> group : List.of(StandardOptions.Group.WINDOW, StandardOptions.Group.DETAILS,
                StandardOptions.Group.SORTING, StandardOptions.Group.CPU_SAVING)) {
            List<Option<?>> added = new ArrayList<>();
            DemonicaOptionPages.onGroup(new OptionGroupConstructionEvent(group, added));
            options.addAll(added);
        }
        OptionPageConstructionEvent advanced = new OptionPageConstructionEvent(StandardOptions.Pages.ADVANCED, TextComponent.literal("advanced"));
        DemonicaOptionPages.onPage(advanced);
        advanced.getAdditionalGroups().forEach(g -> options.addAll(g.getOptions()));
        OptionPage debug = DemonicaOptionPages.debug();
        options.addAll(debug.getOptions());

        Set<String> keys = new TreeSet<>(keys(debug.getName()));
        for (Option<?> option : options) {
            keys.addAll(keys(option.getName()));
            keys.addAll(keys(option.getTooltip()));
            if (option.getControl() instanceof CyclingControl<?> cycling) {
                for (TextComponent name : cycling.getNames()) {
                    keys.addAll(keys(name));
                }
            }
        }
        assertTrue(keys.size() > 40, "found only " + keys);

        for (String locale : List.of("en_us", "ru_ru", "zh_cn")) {
            Set<String> defined = langKeys("/assets/actinium/lang/" + locale + ".lang");
            Set<String> missing = keys.stream().filter(key -> !defined.contains(key)).collect(Collectors.toCollection(TreeSet::new));
            assertTrue(missing.isEmpty(), locale + " lacks " + missing);
        }
    }

    private static List<String> keys(TextComponent component) {
        if (component instanceof TextComponent.Translatable translatable) {
            return translatable.keys();
        }
        return List.of();
    }

    private static Set<String> langKeys(String resource) throws IOException {
        try (InputStream in = DemonicaOptionPagesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.contains("=") && !line.startsWith("#"))
                    .map(line -> line.substring(0, line.indexOf('=')))
                    .collect(Collectors.toSet());
        }
    }

    private static OptionGroup group(OptionPage page, OptionIdentifier<Void> id) {
        return page.getGroups().stream().filter(g -> id.equals(g.getId())).findFirst()
                .orElseThrow(() -> new AssertionError(page.getId() + " has no group " + id));
    }

    private static List<String> ids(List<Option<?>> options) {
        return options.stream().map(option -> String.valueOf(option.getId())).toList();
    }

    private static List<String> pageIds(List<OptionPage> pages) {
        return pages.stream().map(page -> page.getId().toString()).toList();
    }

    private static OptionPage emptyPage(OptionIdentifier<Void> id) {
        return new OptionPage(id, TextComponent.literal(id.toString()), List.of());
    }

    /** A tick box standing in for one of Celeritas's options, by id. */
    private static Option<Boolean> placeholder(OptionIdentifier<Void> id) {
        return OptionImpl.createBuilder(boolean.class, new Storage())
                .setId(id.<Boolean>cast())
                .setName(TextComponent.literal(id.toString()))
                .setTooltip(TextComponent.literal(id.toString()))
                .setControl(TickBoxControl::new)
                .setBinding((data, value) -> data.value = value, data -> data.value)
                .build();
    }

    private static final class Storage implements OptionStorage<Storage> {
        private boolean value;

        @Override
        public Storage getData() {
            return this;
        }
    }
}
