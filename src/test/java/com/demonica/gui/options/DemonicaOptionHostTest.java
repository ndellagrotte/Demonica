package com.demonica.gui.options;

import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoModMetadata;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoModOptions;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemonicaOptionHostTest {
    @Test
    void groupsPagesByModInTheirOrder() {
        Storage storage = new Storage();
        DemonicaOptionHost host = host(new Actions(), List.of(
                page("hosta", "one", tickBox(storage, "hosta", "a")),
                page("hostb", "two", tickBox(storage, "hostb", "b")),
                page("hosta", "three", tickBox(storage, "hosta", "c"))));

        assertEquals(List.of("hosta", "hostb"), host.modOptions().stream().map(RsoModOptions::configId).toList());
        assertEquals(2, host.modOptions().getFirst().pages().size());
    }

    @Test
    void appliesChangesSavesEachStorageOnceAndRunsEachSideEffectOnce() {
        Storage first = new Storage();
        Storage second = new Storage();
        Actions actions = new Actions();
        Option<Boolean> a = tickBox(first, "hostc", "a", OptionFlag.REQUIRES_RENDERER_RELOAD);
        Option<Boolean> b = tickBox(first, "hostc", "b", OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_GAME_RESTART);
        Option<Integer> c = slider(second, "hostc", "c");
        Option<Boolean> untouched = tickBox(second, "hostc", "d", OptionFlag.REQUIRES_ASSET_RELOAD);
        DemonicaOptionHost host = host(actions, List.of(page("hostc", "page", a, b, c, untouched)));

        a.setValue(false);
        b.setValue(false);
        c.setValue(7);
        assertTrue(host.hasPendingChanges());
        host.applyChanges();

        assertFalse(host.hasPendingChanges());
        assertFalse(first.values.contains("a") || first.values.contains("b"));
        assertEquals(7, second.number);
        assertEquals(1, first.saves);
        assertEquals(1, second.saves);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_GAME_RESTART), second.savedWith,
                "every saved storage sees all flags of the transaction");
        assertEquals(List.of("reloadRenderer", "showRestartRequired"), actions.calls);
    }

    @Test
    void undoReturnsEveryOptionToItsAppliedValue() {
        Storage storage = new Storage();
        Option<Boolean> a = tickBox(storage, "hostd", "a");
        Option<Integer> b = slider(storage, "hostd", "b");
        DemonicaOptionHost host = host(new Actions(), List.of(page("hostd", "page", a, b)));

        a.setValue(false);
        b.setValue(9);
        host.undoChanges();

        assertFalse(host.hasPendingChanges());
        assertTrue(a.getValue());
        assertEquals(3, b.getValue());
        assertEquals(0, storage.saves);
    }

    private static DemonicaOptionHost host(Actions actions, List<OptionPage> pages) {
        return new DemonicaOptionHost(actions, configId -> new RsoModMetadata(configId, "", null, false), pages);
    }

    private static OptionPage page(String namespace, String path, Option<?>... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder();
        for (Option<?> option : options) {
            group.add(option);
        }
        return new OptionPage(OptionIdentifier.create(namespace, path), TextComponent.literal(path), List.of(group.build()));
    }

    private static Option<Boolean> tickBox(Storage storage, String namespace, String name, OptionFlag... flags) {
        storage.values.add(name);
        return OptionImpl.createBuilder(boolean.class, storage)
                .setId(OptionIdentifier.create(namespace, name, boolean.class))
                .setName(TextComponent.literal(name))
                .setTooltip(TextComponent.literal(name))
                .setControl(TickBoxControl::new)
                .setBinding((data, value) -> {
                    if (value) {
                        data.values.add(name);
                    } else {
                        data.values.remove(name);
                    }
                }, data -> data.values.contains(name))
                .setFlags(flags)
                .build();
    }

    private static Option<Integer> slider(Storage storage, String namespace, String name) {
        return OptionImpl.createBuilder(int.class, storage)
                .setId(OptionIdentifier.create(namespace, name, int.class))
                .setName(TextComponent.literal(name))
                .setTooltip(TextComponent.literal(name))
                .setControl(option -> new SliderControl(option, 0, 10, 1, ControlValueFormatter.number()))
                .setBinding((data, value) -> data.number = value, data -> data.number)
                .build();
    }

    private static final class Storage implements OptionStorage<Storage> {
        private final List<String> values = new ArrayList<>();
        private int number = 3;
        private int saves;
        private Set<OptionFlag> savedWith;

        @Override
        public Storage getData() {
            return this;
        }

        @Override
        public void save(Set<OptionFlag> flags) {
            this.saves++;
            this.savedWith = Set.copyOf(flags);
        }
    }

    private static final class Actions implements DemonicaApplyActions {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void reloadRenderer() {
            this.calls.add("reloadRenderer");
        }

        @Override
        public void updateRenderer() {
            this.calls.add("updateRenderer");
        }

        @Override
        public void reloadAssets() {
            this.calls.add("reloadAssets");
        }

        @Override
        public void showRestartRequired() {
            this.calls.add("showRestartRequired");
        }
    }
}
