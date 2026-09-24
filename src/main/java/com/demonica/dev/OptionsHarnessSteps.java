package com.demonica.dev;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.Tab;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoModOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The dev harness's {@code rso} step, for checking the video settings (Checkpoint 7). It works on the Reese's Sodium
 * Options screen that is open:
 * <ul>
 *   <li>{@code rso expect-screen rso|celeritas}: fail unless RSO's or Celeritas's video settings screen is open</li>
 *   <li>{@code rso tab <page>}: select the tab of a page, for example {@code celeritas:quality}</li>
 *   <li>{@code rso set <option> <value>}: give an option a pending value: {@code true}/{@code false}, a number, or
 *   the name of an enum constant</li>
 *   <li>{@code rso apply}: apply the pending changes, as the Apply button does</li>
 *   <li>{@code rso expect <option> <value>}: fail unless the option's value is {@code value}</li>
 *   <li>{@code rso dump}: log every option's id and value</li>
 *   <li>{@code rso enable|disable}: set and save RSO's own "enabled" setting, which the next Video Settings reads</li>
 * </ul>
 */
public final class OptionsHarnessSteps {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDevHarness");

    private OptionsHarnessSteps() {
    }

    public static void register() {
        DevHarness.registerStep("rso", (harness, args) -> run(args));
    }

    private static boolean run(String[] args) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        switch (args[1]) {
            case "expect-screen" -> {
                boolean matches = switch (args[2]) {
                    case "rso" -> current instanceof SodiumVideoOptionsScreen;
                    case "celeritas" -> current instanceof CeleritasVideoOptionsScreen;
                    default -> throw new IllegalArgumentException("Unknown screen: " + args[2]);
                };
                if (!matches) {
                    throw new IllegalStateException("Expected the " + args[2] + " screen, found " + name(current));
                }
                LOGGER.info("Dev options: {} is open", name(current));
            }
            case "tab" -> {
                var tabFrame = rso(current).rso$getTabFrame();
                Tab<?> tab = tabFrame.getTabs().stream()
                    .filter(candidate -> candidate.getPage().getId().toString().equals(args[2]))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No tab for page " + args[2]));
                tabFrame.setTab(Optional.of(tab));
                LOGGER.info("Dev options: selected tab {}", args[2]);
            }
            case "set" -> {
                @SuppressWarnings("unchecked")
                Option<Object> option = (Option<Object>) option(rso(current), args[2]);
                Object value = parse(option.getValue(), args[3]);
                option.setValue(value);
                LOGGER.info("Dev options: {} = {} (pending, changed: {})", args[2], value, option.hasChanged());
            }
            case "apply" -> {
                rso(current).rso$getHost().applyChanges();
                LOGGER.info("Dev options: applied");
            }
            case "expect" -> {
                Option<?> option = option(rso(current), args[2]);
                Object expected = parse(option.getValue(), args[3]);
                if (!Objects.equals(option.getValue(), expected) || option.hasChanged()) {
                    throw new IllegalStateException(args[2] + " is " + option.getValue() + (option.hasChanged() ? " (pending)" : "")
                        + ", expected " + expected);
                }
                LOGGER.info("Dev options: {} is {} as expected", args[2], expected);
            }
            case "dump" -> {
                for (Option<?> option : options(rso(current))) {
                    LOGGER.info("Dev options: {} = {}", option.getId(), option.getValue());
                }
            }
            case "enable", "disable" -> {
                ReeseSodiumOptionsConfig.config().setEnabled(args[1].equals("enable"));
                ReeseSodiumOptionsConfig.writeConfig();
                LOGGER.info("Dev options: Reese's Sodium Options {}d", args[1]);
            }
            default -> throw new IllegalArgumentException("Unknown rso action: " + args[1]);
        }
        return true;
    }

    private static SodiumVideoOptionsScreen rso(GuiScreen current) {
        if (current instanceof SodiumVideoOptionsScreen screen) {
            return screen;
        }
        throw new IllegalStateException("Reese's Sodium Options is not open: " + name(current));
    }

    private static List<Option<?>> options(SodiumVideoOptionsScreen screen) {
        List<OptionPage> pages = new ArrayList<>();
        for (RsoModOptions mod : screen.rso$getHost().modOptions()) {
            mod.unwrapPages(pages);
        }
        List<Option<?>> options = new ArrayList<>();
        pages.forEach(page -> options.addAll(page.getOptions()));
        return options;
    }

    private static Option<?> option(SodiumVideoOptionsScreen screen, String id) {
        return options(screen).stream()
            .filter(option -> option.getId() != null && option.getId().toString().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No option " + id));
    }

    /** Reads {@code text} as a value of the same type as {@code current}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parse(Object current, String text) {
        if (current instanceof Boolean) {
            return Boolean.parseBoolean(text);
        }
        if (current instanceof Integer) {
            return Integer.parseInt(text);
        }
        if (current instanceof Enum<?> constant) {
            return Enum.valueOf((Class) constant.getDeclaringClass(), text);
        }
        throw new IllegalArgumentException("Cannot set a " + current.getClass().getName() + " from '" + text + "'");
    }

    private static String name(GuiScreen screen) {
        return screen == null ? "no screen" : screen.getClass().getSimpleName();
    }
}
