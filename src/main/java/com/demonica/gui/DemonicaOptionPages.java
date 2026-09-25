package com.demonica.gui;

import com.demonica.config.DemonicaOptions;
import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.gui.options.DemonicaOptionsStorage;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebugHooks;
import net.irisshaders.iris.compat.sodium.IrisConfigEntryPoint;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.OptionGroupConstructionEvent;
import org.taumc.celeritas.api.OptionPageConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.CyclingControl;
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
 * Demonica's settings in Celeritas's video settings screen. They are added through Celeritas's construction events and
 * land where Actinium had them:
 * <ul>
 *   <li>Quality, Sorting group: the fast block renderer toggle is Demonica's setting, which the meshing patch S13
 *   reads, in place of Celeritas's toggle of its static flag, which S13 overrides.</li>
 *   <li>Advanced: GLSM's upload strategy joins the CPU Saving group; direct memory access and ignoring framebuffer
 *   errors follow as groups of their own.</li>
 *   <li>Pages: Iris's (shadow distance) and Demonica's Debug page while {@code enable_debug_tab} is set. The shader
 *   pack screen is Celeritas's own "Shader Packs" tab, which finds Iris through {@code IrisApi}.</li>
 * </ul>
 * Celeritas builds its pages anew for every screen, so the events come once for each Video Settings click. Each
 * listener only edits the list of its own event, and adds a page only when no page with that id is there yet.
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
        if (StandardOptions.Group.SORTING.equals(id)) {
            replaceOrAdd(options, CELERITAS_FAST_BLOCK_RENDERER, fastBlockRenderer());
        } else if (StandardOptions.Group.CPU_SAVING.equals(id)) {
            options.add(streamingUploadStrategy());
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
        OptionPage iris = new IrisConfigEntryPoint().createPage();
        addIfAbsent(pages, iris.getId(), () -> iris);
        if (DemonicaRuntime.options().enableDebugTab) {
            addIfAbsent(pages, DEBUG_PAGE, DemonicaOptionPages::debug);
        }
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
        return OptionImpl.createBuilder(DemonicaOptions.StreamingUploadStrategy.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, "streaming_upload_strategy", DemonicaOptions.StreamingUploadStrategy.class))
            .setName(TextComponent.translatable("sodium.options.streaming_upload_strategy.name"))
            .setTooltip(TextComponent.translatable("sodium.options.streaming_upload_strategy.tooltip"))
            .setControl(option -> new CyclingControl<>(option, DemonicaOptions.StreamingUploadStrategy.class, names))
            .setBinding((o, v) -> o.advanced.streamingUploadStrategy = v, o -> o.advanced.streamingUploadStrategy)
            // GLSM picks its uploader once, when the GL context is created.
            .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
            .build();
    }

    private static Option<Boolean> debugTickBox(String path, OptionImpact impact, Function<DemonicaOptions, Boolean> getter,
                                                BiConsumer<DemonicaOptions, Boolean> setter, OptionFlag... flags) {
        return tickBox(path, "sodium.options.actinium." + path, impact, getter, setter, flags);
    }

    /** A tick box over one of Demonica's settings, named by {@code <key>.name} and {@code <key>.tooltip}. */
    private static Option<Boolean> tickBox(String path, String key, OptionImpact impact, Function<DemonicaOptions, Boolean> getter,
                                           BiConsumer<DemonicaOptions, Boolean> setter, OptionFlag... flags) {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
            .setId(OptionIdentifier.create(DemonicaRuntime.MODID, path, boolean.class))
            .setName(TextComponent.translatable(key + ".name"))
            .setTooltip(TextComponent.translatable(key + ".tooltip"))
            .setControl(TickBoxControl::new)
            .setImpact(impact)
            .setBinding(setter, getter)
            .setFlags(flags)
            .build();
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
