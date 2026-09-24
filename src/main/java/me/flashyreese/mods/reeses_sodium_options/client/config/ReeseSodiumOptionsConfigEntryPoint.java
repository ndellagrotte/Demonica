package me.flashyreese.mods.reeses_sodium_options.client.config;

import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionDefaults;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builds the page of Reese's Sodium Options' own configuration (enabled, appearance, behavior) as a Celeritas
 * {@link OptionPage}, without the Sodium config model. Each option reads and writes the live
 * {@link ReeseSodiumOptionsConfig#config()} and declares the value of a fresh configuration as its default. The
 * support group (donation buttons) is left out.
 */
public final class ReeseSodiumOptionsConfigEntryPoint {
    private static final String MOD_ID = "reeses-sodium-options";
    public static final OptionIdentifier<Void> PAGE_ID = OptionIdentifier.create(MOD_ID, "rso_options");

    private ReeseSodiumOptionsConfigEntryPoint() {
    }

    /** Builds the RSO config page. */
    public static OptionPage createOptionsPage() {
        List<OptionGroup> groups = List.of(
                createGeneralOptions(),
                createAppearanceOptions(),
                createBehaviorOptions());
        return new OptionPage(
                PAGE_ID,
                TextComponent.translatable("rso.options.page"),
                groups);
    }

    private static OptionGroup createGeneralOptions() {
        return OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "general"))
                .add(booleanOption("enabled",
                        ReeseSodiumOptionsConfig.ConfigData::isEnabled,
                        ReeseSodiumOptionsConfig.ConfigData::setEnabled))
                .build();
    }

    private static OptionGroup createAppearanceOptions() {
        return OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "appearance"))
                .add(booleanOption("tab_header_icons",
                        ReeseSodiumOptionsConfig.ConfigData::isTabHeaderIcons,
                        ReeseSodiumOptionsConfig.ConfigData::setTabHeaderIcons))
                .add(booleanOption("tab_header_version_labels",
                        ReeseSodiumOptionsConfig.ConfigData::isTabHeaderVersionLabels,
                        ReeseSodiumOptionsConfig.ConfigData::setTabHeaderVersionLabels))
                .add(enumOption("tab_header_collapse_mode", ReeseSodiumOptionsConfig.TabHeaderCollapseMode.class,
                        ReeseSodiumOptionsConfig.ConfigData::getTabHeaderCollapseMode,
                        ReeseSodiumOptionsConfig.ConfigData::setTabHeaderCollapseMode))
                .add(booleanOption("tab_headers",
                        ReeseSodiumOptionsConfig.ConfigData::isTabHeaders,
                        ReeseSodiumOptionsConfig.ConfigData::setTabHeaders))
                .add(booleanOption("collapse_single_page_groups",
                        ReeseSodiumOptionsConfig.ConfigData::isCollapseSinglePageGroups,
                        ReeseSodiumOptionsConfig.ConfigData::setCollapseSinglePageGroups))
                .add(booleanOption("collapsible_groups",
                        ReeseSodiumOptionsConfig.ConfigData::isCollapsibleGroups,
                        ReeseSodiumOptionsConfig.ConfigData::setCollapsibleGroups))
                .add(intOption("tooltip_delay",
                        ReeseSodiumOptionsConfig.ConfigData::getTooltipDelayMs,
                        ReeseSodiumOptionsConfig.ConfigData::setTooltipDelayMs,
                        ReeseSodiumOptionsConfig.MIN_TOOLTIP_DELAY_MS,
                        ReeseSodiumOptionsConfig.MAX_TOOLTIP_DELAY_MS,
                        100,
                        value -> TextComponent.translatable("rso.options.value.milliseconds", value)))
                .add(booleanOption("tooltip_option_ids",
                        ReeseSodiumOptionsConfig.ConfigData::isTooltipOptionIds,
                        ReeseSodiumOptionsConfig.ConfigData::setTooltipOptionIds))
                .add(booleanOption("color_themes",
                        ReeseSodiumOptionsConfig.ConfigData::isColorThemes,
                        ReeseSodiumOptionsConfig.ConfigData::setColorThemes))
                .add(booleanOption("themed_headers_and_labels",
                        ReeseSodiumOptionsConfig.ConfigData::isThemedHeadersAndLabels,
                        ReeseSodiumOptionsConfig.ConfigData::setThemedHeadersAndLabels))
                .add(booleanOption("themed_tooltip_borders",
                        ReeseSodiumOptionsConfig.ConfigData::isThemedTooltipBorders,
                        ReeseSodiumOptionsConfig.ConfigData::setThemedTooltipBorders))
                .add(booleanOption("reduced_motion",
                        ReeseSodiumOptionsConfig.ConfigData::isReducedMotion,
                        ReeseSodiumOptionsConfig.ConfigData::setReducedMotion))
                .build();
    }

    private static OptionGroup createBehaviorOptions() {
        return OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "behavior"))
                .add(booleanOption("reverse_cycling_controls",
                        ReeseSodiumOptionsConfig.ConfigData::isReverseCyclingControls,
                        ReeseSodiumOptionsConfig.ConfigData::setReverseCyclingControls))
                .add(booleanOption("shift_scroll_slider_adjustments",
                        ReeseSodiumOptionsConfig.ConfigData::isShiftScrollSliderAdjustments,
                        ReeseSodiumOptionsConfig.ConfigData::setShiftScrollSliderAdjustments))
                .add(intOption("search_result_limit",
                        ReeseSodiumOptionsConfig.ConfigData::getSearchResultLimit,
                        ReeseSodiumOptionsConfig.ConfigData::setSearchResultLimit,
                        ReeseSodiumOptionsConfig.MIN_SEARCH_RESULT_LIMIT,
                        ReeseSodiumOptionsConfig.MAX_SEARCH_RESULT_LIMIT,
                        1,
                        value -> TextComponent.translatable("rso.options.value.results", value)))
                .add(booleanOption("hide_non_matching_options",
                        ReeseSodiumOptionsConfig.ConfigData::isHideNonMatchingOptions,
                        ReeseSodiumOptionsConfig.ConfigData::setHideNonMatchingOptions))
                .add(booleanOption("hide_non_matching_tabs",
                        ReeseSodiumOptionsConfig.ConfigData::isHideNonMatchingTabs,
                        ReeseSodiumOptionsConfig.ConfigData::setHideNonMatchingTabs))
                .add(enumOption("disabled_option_visibility", ReeseSodiumOptionsConfig.DisabledOptionVisibility.class,
                        ReeseSodiumOptionsConfig.ConfigData::getDisabledOptionVisibility,
                        ReeseSodiumOptionsConfig.ConfigData::setDisabledOptionVisibility))
                .add(enumOption("focus_border_mode", ReeseSodiumOptionsConfig.FocusBorderMode.class,
                        ReeseSodiumOptionsConfig.ConfigData::getFocusBorderMode,
                        ReeseSodiumOptionsConfig.ConfigData::setFocusBorderMode))
                .add(booleanOption("controller_guides",
                        ReeseSodiumOptionsConfig.ConfigData::isControllerGuides,
                        ReeseSodiumOptionsConfig.ConfigData::setControllerGuides))
                .add(booleanOption("reset_button_overlay",
                        ReeseSodiumOptionsConfig.ConfigData::isResetButtonOverlay,
                        ReeseSodiumOptionsConfig.ConfigData::setResetButtonOverlay))
                .add(booleanOption("undo_button_overlay",
                        ReeseSodiumOptionsConfig.ConfigData::isUndoButtonOverlay,
                        ReeseSodiumOptionsConfig.ConfigData::setUndoButtonOverlay))
                .add(booleanOption("always_show_action_buttons",
                        ReeseSodiumOptionsConfig.ConfigData::isAlwaysShowActionButtons,
                        ReeseSodiumOptionsConfig.ConfigData::setAlwaysShowActionButtons))
                .build();
    }

    private static OptionImpl<Object, Boolean> booleanOption(String name,
                                                             Function<ReeseSodiumOptionsConfig.ConfigData, Boolean> getter,
                                                             BiConsumer<ReeseSodiumOptionsConfig.ConfigData, Boolean> setter) {
        RsoStorage storage = new RsoStorage();
        return OptionDefaults.declare(OptionImpl.createBuilder(boolean.class, storage)
                .setId(OptionIdentifier.create(MOD_ID, name, boolean.class))
                .setName(TextComponent.translatable("rso.options." + name + ".name"))
                .setTooltip(TextComponent.translatable("rso.options." + name + ".tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((ignored, value) -> setter.accept(ReeseSodiumOptionsConfig.config(), value),
                        ignored -> getter.apply(ReeseSodiumOptionsConfig.config()))
                .build(), getter.apply(Defaults.VALUES));
    }

    private static OptionImpl<Object, Integer> intOption(String name,
                                                         Function<ReeseSodiumOptionsConfig.ConfigData, Integer> getter,
                                                         BiConsumer<ReeseSodiumOptionsConfig.ConfigData, Integer> setter,
                                                         int min, int max, int step,
                                                         ControlValueFormatter formatter) {
        RsoStorage storage = new RsoStorage();
        return OptionDefaults.declare(OptionImpl.createBuilder(int.class, storage)
                .setId(OptionIdentifier.create(MOD_ID, name, int.class))
                .setName(TextComponent.translatable("rso.options." + name + ".name"))
                .setTooltip(TextComponent.translatable("rso.options." + name + ".tooltip"))
                .setControl(option -> new SliderControl(option, min, max, step, formatter))
                .setBinding((ignored, value) -> setter.accept(ReeseSodiumOptionsConfig.config(), value),
                        ignored -> getter.apply(ReeseSodiumOptionsConfig.config()))
                .build(), getter.apply(Defaults.VALUES));
    }

    private static <E extends Enum<E>> OptionImpl<Object, E> enumOption(String name,
                                                                         Class<E> enumClass,
                                                                         Function<ReeseSodiumOptionsConfig.ConfigData, E> getter,
                                                                         BiConsumer<ReeseSodiumOptionsConfig.ConfigData, E> setter) {
        RsoStorage storage = new RsoStorage();
        E[] constants = enumClass.getEnumConstants();
        TextComponent[] names = new TextComponent[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = TextComponent.translatable("rso.options." + name + ".value." + enumId(constants[i]));
        }
        return OptionDefaults.declare(OptionImpl.createBuilder(enumClass, storage)
                .setId(OptionIdentifier.create(MOD_ID, name, enumClass))
                .setName(TextComponent.translatable("rso.options." + name + ".name"))
                .setTooltip(TextComponent.translatable("rso.options." + name + ".tooltip"))
                .setControl(option -> new CyclingControl<>(option, enumClass, names))
                .setBinding((ignored, value) -> setter.accept(ReeseSodiumOptionsConfig.config(), value),
                        ignored -> getter.apply(ReeseSodiumOptionsConfig.config()))
                .build(), getter.apply(Defaults.VALUES));
    }

    private static String enumId(Object value) {
        if (value instanceof ReeseSodiumOptionsConfig.TabHeaderCollapseMode mode) {
            return mode.id();
        }
        if (value instanceof ReeseSodiumOptionsConfig.DisabledOptionVisibility visibility) {
            return visibility.id();
        }
        if (value instanceof ReeseSodiumOptionsConfig.FocusBorderMode mode) {
            return mode.id();
        }
        return value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    // A fresh configuration, made when the page is first built: making one loads ReeseSodiumOptionsConfig, which reads
    // its file from FML's config directory.
    private static final class Defaults {
        static final ReeseSodiumOptionsConfig.ConfigData VALUES = new ReeseSodiumOptionsConfig.ConfigData();
    }

    /** Placeholder storage: RSO config reads/writes the static ConfigData; the storage only persists on save. */
    private static final class RsoStorage implements OptionStorage<Object> {
        @Override
        public Object getData() {
            return this;
        }

        @Override
        public void save() {
            ReeseSodiumOptionsConfig.writeConfig();
        }

        @Override
        public void save(Set<OptionFlag> flags) {
            this.save();
        }
    }
}
