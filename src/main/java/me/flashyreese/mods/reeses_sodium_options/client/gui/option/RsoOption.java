package me.flashyreese.mods.reeses_sodium_options.client.gui.option;

import com.demonica.gui.rso.compat.Component;
import com.demonica.celeritas.options.OptionControls;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.taumc.celeritas.api.options.structure.Option;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.Objects;

/**
 * Row-level view over a Celeritas {@link Option}: exposes the upstream
 * pending/applied semantics and control-type dispatch helpers. Rows depend
 * only on this class and {@link RsoModOptions}; no Sodium config model is
 * involved.
 *
 * <p>Celeritas's options know their pending value and whether it differs from
 * the applied one ({@link Option#hasChanged()}); undo is {@link Option#reset()}.
 * Declared defaults come from {@link OptionDefaults}, and the slider and
 * cycling details from the quarantine patch O1 ({@link OptionControls}).
 */
public final class RsoOption {
    private final Option<?> delegate;

    public RsoOption(Option<?> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Option must not be null");
    }

    /** Returns the underlying Celeritas option (for control type checks). */
    public Option<?> unwrap() {
        return this.delegate;
    }

    /** Returns the stable option id (from the Celeritas OptionIdentifier). */
    public String rso$getId() {
        OptionIdentifier<?> id = this.delegate.getId();
        return id == null ? "" : id.toString();
    }

    public ITextComponent getName() {
        return convertText(this.delegate.getName());
    }

    public ITextComponent getTooltip() {
        return convertText(this.delegate.getTooltip());
    }

    public boolean isEnabled() {
        return this.delegate.isAvailable();
    }

    public boolean hasChanged() {
        return this.delegate.hasChanged();
    }

    /** Returns the pending value the user has not applied yet. */
    public Object getPendingValue() {
        return this.delegate.getValue();
    }

    /** Replaces the current edit value with a new pending value. */
    public void modifyValue(Object value) {
        this.setValue(value);
    }

    @SuppressWarnings("unchecked")
    private void setValue(Object value) {
        ((Option<Object>) this.delegate).setValue(value);
    }

    /** Discards pending changes, returning to the applied baseline. */
    public void undo() {
        this.delegate.reset();
    }

    /** Whether the pending value differs from the default: the declared one, else the applied value. */
    public boolean differsFromDefault() {
        Object declared = OptionDefaults.get(this.delegate);
        return declared != null ? !Objects.equals(this.getPendingValue(), declared) : this.hasChanged();
    }

    /** Restores the declared default value, or the applied value when the option declares no default. */
    public void resetToDefault() {
        Object declared = OptionDefaults.get(this.delegate);
        if (declared != null) {
            this.setValue(declared);
        } else {
            this.undo();
        }
    }

    /** Returns the performance impact label (may be null). */
    public String getImpactName() {
        OptionImpact impact = this.delegate.getImpact();
        return impact == null ? null : impact.name();
    }

    /** Control-type dispatch: whether this is a boolean tick-box. */
    public boolean isTickBox() {
        return this.delegate.getControl() instanceof TickBoxControl;
    }

    /** Control-type dispatch: whether this is an integer slider. */
    public boolean isSlider() {
        return this.delegate.getControl() instanceof SliderControl;
    }

    /** Control-type dispatch: whether this is a cycling control (enum/discrete values). */
    public boolean isCycling() {
        return this.delegate.getControl() instanceof CyclingControl;
    }

    /** Control-type dispatch: whether this opens a separate screen. */
    public boolean isExternalButton() {
        return this.delegate.getControl() instanceof ExternalButtonControl;
    }

    /** Returns the slider lower bound (meaningful only when isSlider). */
    public int sliderMin() {
        return OptionControls.sliderMin((SliderControl) this.delegate.getControl());
    }

    /** Returns the slider upper bound (meaningful only when isSlider). */
    public int sliderMax() {
        return OptionControls.sliderMax((SliderControl) this.delegate.getControl());
    }

    /** Returns the slider step (meaningful only when isSlider). */
    public int sliderInterval() {
        return OptionControls.sliderInterval((SliderControl) this.delegate.getControl());
    }

    /** Returns the formatted slider value text (meaningful only when isSlider). */
    public ITextComponent formatSliderValue(Object value) {
        SliderControl control =
                (SliderControl) this.delegate.getControl();
        return convertText(OptionControls.sliderFormatter(control).format((Integer) value));
    }

    /** Returns the cycling control label for a value (meaningful only when isCycling). */
    public ITextComponent getElementName(Object value) {
        CyclingControl<Object> control =
                (CyclingControl<Object>) this.delegate.getControl();
        Object[] allowed = OptionControls.allowedValues(control);
        TextComponent[] names = control.getNames();
        for (int i = 0; i < allowed.length; i++) {
            if (Objects.equals(allowed[i], value)) {
                return convertText(names[i]);
            }
        }
        return new TextComponentString(value.toString());
    }

    /** Returns whether the cycling control accepts a value (meaningful only when isCycling). */
    public boolean isValueAllowed(Object value) {
        CyclingControl<Object> control =
                (CyclingControl<Object>) this.delegate.getControl();
        for (Object allowed : OptionControls.allowedValues(control)) {
            if (Objects.equals(allowed, value)) {
                return true;
            }
        }
        return false;
    }

    /** Returns all values of the cycling control (meaningful only when isCycling). */
    public Object[] getAllowedValues() {
        CyclingControl<Object> control =
                (CyclingControl<Object>) this.delegate.getControl();
        return OptionControls.allowedValues(control);
    }

    /** Returns the external button's screen consumer (meaningful only when isExternalButton). */
    public java.util.function.Consumer<net.minecraft.client.gui.GuiScreen> getCurrentScreenConsumer() {
        return ((ExternalButtonControl) this.delegate.getControl())
                .getScreenConsumer();
    }

    /** Whether the control should be hidden while the option is disabled (always false here). */
    public boolean shouldHideControl() {
        return false;
    }

    private static ITextComponent convertText(TextComponent component) {
        return Component.fromEmbeddium(component).unwrap();
    }
}
