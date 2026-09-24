package com.demonica.gui.options;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OptionGUIConstructionBridgeTest {
    @Test
    void keepsThePagesAddedBeforeAListenerFailed() {
        OptionPage builtIn = page("bridgetest", "general");

        List<OptionPage> pages = OptionGUIConstructionBridge.collect(List.of(builtIn), event -> {
            event.addPage(page("bridgeaddon", "first"));
            throw new IllegalStateException("listener failed");
        });

        assertEquals(List.of("bridgetest:general", "bridgeaddon:first"), ids(pages));
    }

    @Test
    void dropsPagesRsoCannotShow() {
        OptionPage builtIn = page("bridgetest", "quality");

        List<OptionPage> pages = OptionGUIConstructionBridge.collect(List.of(builtIn), event -> {
            event.getPages().add(null);
            event.addPage(new OptionPage(OptionIdentifier.EMPTY, TextComponent.literal("no id"), List.of()));
            event.addPage(page("bridgetest", "quality"));
            event.addPage(page("bridgeaddon", "extra"));
        });

        assertEquals(List.of("bridgetest:quality", "bridgeaddon:extra"), ids(pages));
        assertSame(builtIn, pages.getFirst(), "the first page with an id wins");
    }

    @Test
    void honoursListenersThatReorderOrRemovePages() {
        OptionPage first = page("bridgetest", "one");
        OptionPage second = page("bridgetest", "two");

        List<OptionPage> pages = OptionGUIConstructionBridge.collect(List.of(first, second), event -> {
            event.getPages().remove(first);
            event.getPages().add(0, page("bridgeaddon", "front"));
        });

        assertEquals(List.of("bridgeaddon:front", "bridgetest:two"), ids(pages));
    }

    private static List<String> ids(List<OptionPage> pages) {
        return pages.stream().map(page -> page.getId().toString()).toList();
    }

    private static OptionPage page(String namespace, String path) {
        var option = OptionImpl.createBuilder(boolean.class, new MutableStorage())
                .setId(OptionIdentifier.create(namespace, path + "_enabled", boolean.class))
                .setName(TextComponent.literal("Enabled"))
                .setTooltip(TextComponent.literal("Enabled tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((data, value) -> data.value = value, data -> data.value)
                .build();
        return new OptionPage(OptionIdentifier.create(namespace, path), TextComponent.literal(path),
                List.of(OptionGroup.createBuilder().add(option).build()));
    }

    private static final class MutableStorage implements OptionStorage<MutableStorage> {
        private boolean value = true;

        @Override
        public MutableStorage getData() {
            return this;
        }
    }
}
