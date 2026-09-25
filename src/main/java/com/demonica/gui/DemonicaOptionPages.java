package com.demonica.gui;

import com.demonica.config.DemonicaOptions;
import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.gui.options.DemonicaOptionsStorage;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebugHooks;
import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfigEntryPoint;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionDefaults;
import net.irisshaders.iris.compat.sodium.IrisConfigEntryPoint;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.OptionGroupConstructionEvent;
import org.taumc.celeritas.api.OptionPageConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.StandardOptions;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Demonica's settings in the video settings screens, Reese's Sodium Options and Celeritas's own. They are added through
 * Celeritas's construction events and land where Actinium had them:
 * <ul>
 *   <li>General, Window group: Fullscreen Mode (with borderless) replaces vanilla's fullscreen toggle, and the
 *   loading-screen frame limit follows Max Framerate.</li>
 *   <li>Quality, Sorting group: the fast block renderer toggle is Demonica's setting, which the meshing patch S13
 *   reads, in place of Celeritas's toggle of its static flag, which S13 overrides.</li>
 *   <li>Advanced: GLSM's upload strategy and the draw fast paths join the CPU Saving group; direct memory access and
 *   ignoring framebuffer errors follow as groups of their own.</li>
 *   <li>Pages: Iris's (shadow distance, shader packs), Demonica's Debug page while {@code enable_debug_tab} is set,
 *   and RSO's own settings.</li>
 * </ul>
 * Celeritas builds its pages anew for every screen, so the events come often: twice for each Video Settings click, once
 * for Celeritas's screen and once for the RSO screen that replaces it. Each listener only edits the list of its own
 * event, and adds a page only when no page with that id is there yet.
 */
public final class DemonicaOptionPages {
    public static final OptionIdentifier<Void> DEBUG_PAGE = OptionIdentifier.create(DemonicaRuntime.MODID, "debug");
    // Celeritas interns identifiers by name and rejects one name used with two types, so no group shares a name with
    // an option.
    static final OptionIdentifier<Void> DIRECT_MEMORY_GROUP = OptionIdentifier.create(DemonicaRuntime.MODID, "direct_memory");
    static final OptionIdentifier<Void> FRAMEBUFFER_ERRORS_GROUP = OptionIdentifier.create(DemonicaRuntime.MODID, "framebuffer_errors");
    static final OptionIdentifier<Void> DEBUG_GROUP = OptionIdentifier.create(DemonicaRuntime.MODID, "diagnostics");
    // Matched by name: creating the identifier here would fail if upstream ever gave it another type.
    static final String CELERITAS_FAST_BLOCK_RENDERER = "celeritas:fast_block_renderer";

    private static final DemonicaOptionsStorage STORAGE = new DemonicaOptionsStorage();
    // The values a new options file starts with, declared as the options' defaults for RSO's reset button.
    private static final DemonicaOptions DEFAULTS = new DemonicaOptions();

    private static boolean registered;

    private DemonicaOptionPages() {
    }

    /** Registers the listeners, once. Called when Demonica is constructed. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        OptionGroupConstructionEvent.BUS.addListener(DemonicaOptionPages::onGroup);
        OptionPageConstructionEvent.BUS.addListener(DemonicaOptionPages::onPage);
        OptionGUIConstructionEvent.BUS.addListener(DemonicaOptionPages::onGui);
    }

    static void onGroup(OptionGroupConstructionEvent event) {
        List<Option<?>> options = event.getOptions();
        OptionIdentifier<Void> id = event.getId();
        if (StandardOptions.Group.WINDOW.equals(id)) {
            replaceOrAdd(options, StandardOptions.Option.FULLSCREEN.toString(), fullscreenMode());
            insertAfterOrAdd(options, StandardOptions.Option.MAX_FRAMERATE.toString(), loadingScreenFramerateLimit());
        } else if (StandardOptions.Group.DETAILS.equals(id)) {
            insertAfterOrAdd(options, StandardOptions.Option.VIGNETTE.toString(), dynamicFov());
        } else if (StandardOptions.Group.SORTING.equals(id)) {
            replaceOrAdd(options, CELERITAS_FAST_BLOCK_RENDERER, fastBlockRenderer());
        } else if (StandardOptions.Group.CPU_SAVING.equals(id)) {
            options.add(streamingUploadStrategy());
            options.add(tickBox("model_renderer_batching", "sodium.options.actinium.model_renderer_batching", OptionImpact.MEDIUM,
                o -> o.advanced.useModelRendererBatching, (o, v) -> o.advanced.useModelRendererBatching = v));
            options.add(tickBox("model_renderer_display_lists", "sodium.options.model_renderer_display_lists", OptionImpact.MEDIUM,
                o -> o.advanced.useModelRendererDisplayLists, (o, v) -> o.advanced.useModelRendererDisplayLists = v));
        }
    }

    static void onPage(OptionPageConstructionEvent event) {
        if (StandardOptions.Pages.ADVANCED.equals(event.getId())) {
            event.addGroup(group(DIRECT_MEMORY_GROUP,
                tickBox("allow_direct_memory_access", "sodium.options.allow_direct_memory_access", OptionImpact.HIGH,
                    o -> o.advanced.allowDirectMemoryAccess, (o, v) -> o.advanced.allowDirectMemoryAccess = v)));
            event.addGroup(group(FRAMEBUFFER_ERRORS_GROUP,
                tickBox("ignore_framebuffer_errors", "sodium.options.actinium.ignore_framebuffer_errors", OptionImpact.MEDIUM,
                    o -> o.debug.ignoreFramebufferErrors, (o, v) -> o.debug.ignoreFramebufferErrors = v)));
        }
    }

    static void onGui(OptionGUIConstructionEvent event) {
        List<OptionPage> pages = event.getPages();
        for (OptionPage page : new IrisConfigEntryPoint().createPages()) {
            addIfAbsent(pages, page.getId(), () -> page);
        }
        if (DemonicaRuntime.options().enableDebugTab) {
            addIfAbsent(pages, DEBUG_PAGE, DemonicaOptionPages::debug);
        }
        addIfAbsent(pages, ReeseSodiumOptionsConfigEntryPoint.PAGE_ID, ReeseSodiumOptionsConfigEntryPoint::createOptionsPage);
    }

    static OptionPage debug() {
        return new OptionPage(DEBUG_PAGE, TextComponent.translatable("sodium.options.pages.debug"), List.of(group(DEBUG_GROUP,
            debugTickBox("production_diagnostics", OptionImpact.LOW,
                o -> o.debug.enableProductionDiagnostics, (o, v) -> o.debug.enableProductionDiagnostics = v),
            debugTickBox("gl_debug", OptionImpact.HIGH, o -> o.debug.enableGlDebug, (o, v) -> o.debug.enableGlDebug = v),
            debugTickBox("lwjgl_debug", OptionImpact.HIGH, o -> o.debug.enableLwjglDebug, (o, v) -> o.debug.enableLwjglDebug = v,
                OptionFlag.REQUIRES_GAME_RESTART),
            debugTickBox("pbr_debug", OptionImpact.HIGH, o -> o.debug.enablePbrDebug, (o, v) -> o.debug.enablePbrDebug = v),
            debugTickBox("cloud_control_debug", OptionImpact.MEDIUM,
                o -> o.debug.enableCloudControlDebug, (o, v) -> o.debug.enableCloudControlDebug = v),
            debugTickBox("perf_debug", OptionImpact.MEDIUM, o -> o.debug.enablePerfDebug, (o, v) -> {
                o.debug.enablePerfDebug = v;
                GLSMPerfDebugHooks.setConfiguredEnabled(DemonicaRuntimeOptions.resolvePerfDebugEnabled(v));
            }),
            debugTickBox("gpu_perf_debug", OptionImpact.HIGH, o -> o.debug.enableGpuPerfDebug, (o, v) -> o.debug.enableGpuPerfDebug = v),
            debugTickBox("frame_gl_error_check", OptionImpact.MEDIUM,
                o -> o.debug.enableFrameGlErrorCheck, (o, v) -> o.debug.enableFrameGlErrorCheck = v),
            debugTickBox("post_render_gl_error_check", OptionImpact.MEDIUM,
                o -> o.debug.enablePostRenderGlErrorCheck, (o, v) -> o.debug.enablePostRenderGlErrorCheck = v),
            debugTickBox("redirector_debug", OptionImpact.MEDIUM,
                o -> o.debug.enableRedirectorDebug, (o, v) -> o.debug.enableRedirectorDebug = v, OptionFlag.REQUIRES_GAME_RESTART),
            debugTickBox("redirector_log_spam", OptionImpact.HIGH,
                o -> o.debug.enableRedirectorLogSpam, (o, v) -> o.debug.enableRedirectorLogSpam = v, OptionFlag.REQUIRES_GAME_RESTART),
            debugTickBox("redirector_class_dump", OptionImpact.HIGH,
                o -> o.debug.enableRedirectorClassDump, (o, v) -> o.debug.enableRedirectorClassDump = v, OptionFlag.REQUIRES_GAME_RESTART))));
    }

    private static Option<FullscreenMode> fullscreenMode() {
        return OptionDefaults.declare(OptionImpl.createBuilder(FullscreenMode.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, "fullscreen_mode", FullscreenMode.class))
            .setName(TextComponent.translatable("celeritas.options.fullscreen_mode.name"))
            .setTooltip(TextComponent.translatable("celeritas.options.fullscreen_mode.tooltip"))
            .setControl(option -> new CyclingControl<>(option, FullscreenMode.class))
            .setBinding((o, mode) -> DemonicaWindowModeController.applyMode(Minecraft.getMinecraft(), o, mode),
                DemonicaWindowModeController::resolveConfiguredMode)
            .build(), FullscreenMode.OFF);
    }

    private static Option<Integer> loadingScreenFramerateLimit() {
        Function<DemonicaOptions, Integer> getter = o -> o.performance.loadingScreenFramerateLimit;
        return OptionDefaults.declare(OptionImpl.createBuilder(int.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, "loading_screen_framerate_limit", int.class))
            .setName(TextComponent.translatable("options.actinium.loadingScreenFramerateLimit"))
            .setTooltip(TextComponent.translatable("options.actinium.loadingScreenFramerateLimit.tooltip"))
            .setControl(option -> new SliderControl(option, 30, 240, 10, ControlValueFormatter.fpsLimit()))
            .setBinding((o, v) -> o.performance.loadingScreenFramerateLimit = v, getter)
            .build(), getter.apply(DEFAULTS));
    }

    /** Vanilla's sprint, flight and bow-draw widening of the field of view (MixinEntityRendererDynamicFov). */
    private static Option<Boolean> dynamicFov() {
        return tickBox("dynamic_fov", "sodium.options.dynamic_fov", OptionImpact.LOW,
            o -> o.quality.dynamicFov, (o, v) -> o.quality.dynamicFov = v);
    }

    private static Option<Boolean> fastBlockRenderer() {
        return tickBox("fast_block_renderer", "celeritas.options.fast_block_renderer", OptionImpact.MEDIUM,
            o -> o.performance.useFastBlockRenderer, (o, v) -> o.performance.useFastBlockRenderer = v,
            OptionFlag.REQUIRES_RENDERER_RELOAD);
    }

    private static Option<DemonicaOptions.StreamingUploadStrategy> streamingUploadStrategy() {
        DemonicaOptions.StreamingUploadStrategy[] strategies = DemonicaOptions.StreamingUploadStrategy.values();
        TextComponent[] names = new TextComponent[strategies.length];
        for (int i = 0; i < strategies.length; i++) {
            names[i] = TextComponent.translatable(strategies[i].translationKey());
        }
        Function<DemonicaOptions, DemonicaOptions.StreamingUploadStrategy> getter = o -> o.advanced.streamingUploadStrategy;
        return OptionDefaults.declare(OptionImpl.createBuilder(DemonicaOptions.StreamingUploadStrategy.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, "streaming_upload_strategy", DemonicaOptions.StreamingUploadStrategy.class))
            .setName(TextComponent.translatable("sodium.options.streaming_upload_strategy.name"))
            .setTooltip(TextComponent.translatable("sodium.options.streaming_upload_strategy.tooltip"))
            .setControl(option -> new CyclingControl<>(option, DemonicaOptions.StreamingUploadStrategy.class, names))
            .setBinding((o, v) -> o.advanced.streamingUploadStrategy = v, getter)
            // GLSM picks its uploader once, when the GL context is created.
            .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
            .build(), getter.apply(DEFAULTS));
    }

    private static Option<Boolean> debugTickBox(String path, OptionImpact impact, Function<DemonicaOptions, Boolean> getter,
                                                BiConsumer<DemonicaOptions, Boolean> setter, OptionFlag... flags) {
        return tickBox(path, "sodium.options.actinium." + path, impact, getter, setter, flags);
    }

    /** A tick box over one of Demonica's settings, named by {@code <key>.name} and {@code <key>.tooltip}. */
    private static Option<Boolean> tickBox(String path, String key, OptionImpact impact, Function<DemonicaOptions, Boolean> getter,
                                           BiConsumer<DemonicaOptions, Boolean> setter, OptionFlag... flags) {
        return OptionDefaults.declare(OptionImpl.createBuilder(boolean.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, path, boolean.class))
            .setName(TextComponent.translatable(key + ".name"))
            .setTooltip(TextComponent.translatable(key + ".tooltip"))
            .setControl(TickBoxControl::new)
            .setImpact(impact)
            .setBinding(setter, getter)
            .setFlags(flags)
            .build(), getter.apply(DEFAULTS));
    }

    private static OptionGroup group(OptionIdentifier<Void> id, Option<?>... options) {
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id);
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }

    /** Puts {@code option} where the option {@code id} is, or at the end when the group has no such option. */
    static void replaceOrAdd(List<Option<?>> options, String id, Option<?> option) {
        int index = indexOf(options, id);
        if (index >= 0) {
            options.set(index, option);
        } else {
            options.add(option);
        }
    }

    /** Puts {@code option} after the option {@code id}, or at the end when the group has no such option. */
    static void insertAfterOrAdd(List<Option<?>> options, String id, Option<?> option) {
        int index = indexOf(options, id);
        options.add(index >= 0 ? index + 1 : options.size(), option);
    }

    private static int indexOf(List<Option<?>> options, String id) {
        for (int i = 0; i < options.size(); i++) {
            if (id.equals(String.valueOf(options.get(i).getId()))) {
                return i;
            }
        }
        return -1;
    }

    private static void addIfAbsent(List<OptionPage> pages, OptionIdentifier<Void> id, Supplier<OptionPage> page) {
        for (OptionPage existing : pages) {
            if (existing != null && id.equals(existing.getId())) {
                return;
            }
        }
        pages.add(page.get());
    }
}
