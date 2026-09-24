package me.flashyreese.mods.reeses_sodium_options.client.gui.option;

import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action.OptionResetAction;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action.OptionUndoAction;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RSO's undo and reset buttons on Celeritas's option model, which knows no applied or default value of its own. */
class RsoOptionTest {
    @Test
    void undoReturnsToTheAppliedValue() {
        Storage storage = new Storage(3);
        RsoOption option = new RsoOption(slider(storage, "undo"));

        option.modifyValue(8);
        assertTrue(OptionUndoAction.canUndo(option));
        OptionUndoAction.undoChanges(option);

        assertEquals(3, option.getPendingValue());
        assertFalse(option.hasChanged());
        assertFalse(OptionUndoAction.canUndo(option));
    }

    @Test
    void aValueSetBackToTheAppliedOneIsNoChange() {
        RsoOption option = new RsoOption(slider(new Storage(3), "same"));

        option.modifyValue(8);
        option.modifyValue(3);

        assertFalse(option.hasChanged());
        assertFalse(OptionUndoAction.canUndo(option));
    }

    @Test
    void resetGoesToTheDeclaredDefault() {
        Storage storage = new Storage(3);
        OptionImpl<Storage, Integer> slider = OptionDefaults.declare(slider(storage, "declared"), 5);
        RsoOption option = new RsoOption(slider);

        assertTrue(OptionResetAction.canReset(option));
        OptionResetAction.resetToDefault(option);
        assertEquals(5, option.getPendingValue());
        assertTrue(option.hasChanged());

        slider.applyChanges();
        assertEquals(5, storage.value);
        assertFalse(OptionResetAction.canReset(option));
    }

    @Test
    void withoutADeclaredDefaultResetUndoes() {
        RsoOption option = new RsoOption(slider(new Storage(3), "undeclared"));

        assertFalse(OptionResetAction.canReset(option), "nothing to reset while the applied value is shown");
        option.modifyValue(9);
        assertTrue(OptionResetAction.canReset(option));
        OptionResetAction.resetToDefault(option);

        assertEquals(3, option.getPendingValue());
        assertFalse(option.hasChanged());
    }

    private static OptionImpl<Storage, Integer> slider(Storage storage, String name) {
        return OptionImpl.createBuilder(int.class, storage)
                .setId(OptionIdentifier.create("rsooptiontest", name, int.class))
                .setName(TextComponent.literal(name))
                .setTooltip(TextComponent.literal(name))
                .setControl(option -> new SliderControl(option, 0, 10, 1, ControlValueFormatter.number()))
                .setBinding((data, value) -> data.value = value, data -> data.value)
                .build();
    }

    private static final class Storage implements OptionStorage<Storage> {
        private int value;

        private Storage(int value) {
            this.value = value;
        }

        @Override
        public Storage getData() {
            return this;
        }
    }
}
