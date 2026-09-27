package com.demonica.diagnostics.dev;

import com.demonica.celeritas.terrain.CeleritasWorldRendererCompat;
import com.demonica.diagnostics.mixin.GuiScreenAccessor;
import com.demonica.gui.DemonicaGuiFactory;
import net.coderbot.iris.Iris;
import net.coderbot.iris.config.IrisConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.embeddedt.embeddium.impl.gui.frame.AbstractFrame;
import org.embeddedt.embeddium.impl.gui.framework.Interactable;
import org.embeddedt.embeddium.impl.gui.framework.InteractableContainer;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.widgets.FlatButtonWidget;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.embeddedt.embeddium.impl.util.Dim2i;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;
import org.taumc.celeritas.impl.gui.VintageInteractionContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Drives a dev client through a scripted session so rendering can be checked without input tools: load a
 * world from a fixed seed, run commands, wait for terrain, take screenshots and exit. It does nothing unless
 * {@code -Ddemonica.dev.script} is set, either to the steps themselves (separated by {@code ;}) or to
 * {@code @path} of a file with one step per line. It is part of the diagnostics jar, which installs it at init
 * (DemonicaDiagnosticsMod), so a production install with that jar runs scripts too.
 *
 * <p>Steps:
 * <ul>
 *   <li>{@code world <folder> <seed>}: from the main menu, recreate the creative world {@code folder} from {@code seed}</li>
 *   <li>{@code wait <ticks>}: wait for client ticks</li>
 *   <li>{@code cmd <command>}: send a chat command as the player</li>
 *   <li>{@code chunks <maxTicks>}: wait until no terrain is left to build, or for at most {@code maxTicks}</li>
 *   <li>{@code shot <name> [hud]}: save {@code screenshots/<name>.png} after the next frame, with the HUD hidden
 *   unless {@code hud} is given</li>
 *   <li>{@code debug on|off}: show or hide the F3 debug screen</li>
 *   <li>{@code mine <ticks>}: break the block in view for {@code ticks} ticks, as holding the attack button does</li>
 *   <li>{@code third <0|1|2>}: set the camera mode</li>
 *   <li>{@code glide <dx> <dz> <ticks>}: move the player by {@code dx, dz} blocks every tick (fast flight)</li>
 *   <li>{@code screen options|video|modconfig|shaderpacks|inventory|close}: open vanilla's Options screen, open Video
 *   Settings (Celeritas's screen), open Demonica's Config screen from the mod list, click the "Shader Packs" tab of the
 *   open Video Settings as a click does (from any other screen, open the screen that tab opens, through the same
 *   {@code ShaderModBridge} call), open the player's inventory (the creative one in creative mode), or close the current
 *   screen</li>
 *   <li>{@code expect-screen <class>}: fail unless the open screen's class has this simple name</li>
 *   <li>{@code press <buttonId>}: press a button of the current vanilla screen, as a click does (Options' Video
 *   Settings button is 101)</li>
 *   <li>{@code reload}: reload resources (F3+T)</li>
 *   <li>{@code pack <file>|off}: select the shader pack {@code shaderpacks/<file>} (the name may contain spaces), or
 *   turn shaders off, and reload Iris the way its shader toggle key does. Celeritas's renderer is not reloaded here;
 *   it follows on the next frame, as after the toggle key. Does nothing while Iris is off.</li>
 *   <li>{@code stats}: log the frame rate, Celeritas's chunk counts and the sections the last shadow pass drew</li>
 *   <li>{@code log <text>}: write a marker to the log</li>
 *   <li>{@code exit}: shut the client down</li>
 * </ul>
 * Other features register further steps through {@link #registerStep}.
 */
public final class DevHarness {
    public static final String SCRIPT_PROPERTY = "demonica.dev.script";
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDevHarness");
    // The title Celeritas gives its "Shader Packs" tab.
    private static final String SHADER_PACKS_TAB = "options.iris.shaderPackSelection";
    private static final Map<String, BiFunction<DevHarness, String[], Boolean>> EXTRA_STEPS = new ConcurrentHashMap<>();

    private static boolean installed;

    private final Deque<String> steps;
    private String current;
    private int waited;
    private boolean worldLaunched;
    private boolean shotPending;
    private String shotName;
    private boolean savedHideGui;

    private DevHarness(List<String> steps) {
        this.steps = new ArrayDeque<>(steps);
    }

    /** Registers the harness if a script is configured. Safe to call more than once. */
    public static synchronized void install() {
        String script = System.getProperty(SCRIPT_PROPERTY);
        if (script == null || script.isBlank() || installed) {
            return;
        }
        List<String> steps;
        try {
            steps = parse(script);
        } catch (IOException e) {
            LOGGER.error("Cannot read dev script {}", script, e);
            return;
        }
        installed = true;
        LOGGER.info("Dev harness armed with {} steps", steps.size());
        MinecraftForge.EVENT_BUS.register(new DevHarness(steps));
    }

    /**
     * Adds a step. The handler is called once per client tick while the step is current and returns true when the
     * step is done.
     */
    public static void registerStep(String name, BiFunction<DevHarness, String[], Boolean> handler) {
        EXTRA_STEPS.put(name.toLowerCase(Locale.ROOT), handler);
    }

    static List<String> parse(String script) throws IOException {
        List<String> lines;
        if (script.startsWith("@")) {
            lines = Files.readAllLines(Paths.get(script.substring(1)), StandardCharsets.UTF_8);
        } else {
            lines = Arrays.asList(script.split(";"));
        }
        List<String> steps = new ArrayList<>();
        for (String line : lines) {
            String step = line.trim();
            if (!step.isEmpty() && !step.startsWith("#")) {
                steps.add(step);
            }
        }
        return steps;
    }

    /** Ticks spent on the current step so far. */
    public int ticksInStep() {
        return this.waited;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shotPending) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            while (true) {
                if (this.current == null) {
                    this.current = this.steps.poll();
                    this.waited = 0;
                    if (this.current == null) {
                        return;
                    }
                    LOGGER.info("Dev step: {}", this.current);
                }
                if (!this.run(mc, this.current.split("\\s+"))) {
                    this.waited++;
                    return;
                }
                this.current = null;
                if (this.shotPending) {
                    // Nothing may change before the next frame is saved.
                    return;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Dev step '{}' failed; stopping the script", this.current, e);
            this.steps.clear();
            this.current = null;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !this.shotPending) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.gameDir, this.shotName + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        LOGGER.info("Dev screenshot saved: screenshots/{}.png", this.shotName);
        mc.gameSettings.hideGUI = this.savedHideGui;
        this.shotPending = false;
    }

    private boolean run(Minecraft mc, String[] args) {
        String name = args[0].toLowerCase(Locale.ROOT);
        switch (name) {
            case "world" -> {
                if (this.worldLaunched) {
                    boolean joined = mc.world != null && mc.player != null && mc.currentScreen == null;
                    this.worldLaunched = !joined;
                    return joined;
                }
                if (mc.currentScreen instanceof GuiMainMenu) {
                    String folder = args[1];
                    long seed = Long.parseLong(args[2]);
                    mc.getSaveLoader().deleteWorldDirectory(folder);
                    WorldSettings settings = new WorldSettings(seed, GameType.CREATIVE, true, false, WorldType.DEFAULT).enableCommands();
                    mc.launchIntegratedServer(folder, folder, settings);
                    this.worldLaunched = true;
                }
                return false;
            }
            case "wait" -> {
                return this.waited >= Integer.parseInt(args[1]);
            }
            case "cmd" -> {
                String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (mc.player == null) {
                    return false;
                }
                mc.player.sendChatMessage(command);
                return true;
            }
            case "chunks" -> {
                boolean done = mc.renderGlobal != null && mc.renderGlobal.hasNoChunkUpdates() && this.waited > 20;
                if (!done && this.waited >= Integer.parseInt(args[1])) {
                    LOGGER.warn("Terrain still building after {} ticks; continuing", this.waited);
                    return true;
                }
                return done;
            }
            case "shot" -> {
                this.shotName = args[1];
                this.savedHideGui = mc.gameSettings.hideGUI;
                mc.gameSettings.hideGUI = !(args.length > 2 && args[2].equalsIgnoreCase("hud"));
                this.shotPending = true;
                return true;
            }
            case "mine" -> {
                // Survival block breaking of the block in view, one hit per tick, as holding the attack button does.
                RayTraceResult hit = mc.objectMouseOver;
                if (mc.player == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
                    return true;
                }
                if (this.waited == 0) {
                    mc.playerController.clickBlock(hit.getBlockPos(), hit.sideHit);
                } else {
                    mc.playerController.onPlayerDamageBlock(hit.getBlockPos(), hit.sideHit);
                }
                mc.player.swingArm(EnumHand.MAIN_HAND);
                return this.waited + 1 >= Integer.parseInt(args[1]);
            }
            case "debug" -> {
                mc.gameSettings.showDebugInfo = args[1].equalsIgnoreCase("on");
                return true;
            }
            case "third" -> {
                mc.gameSettings.thirdPersonView = Integer.parseInt(args[1]);
                return true;
            }
            case "glide" -> {
                if (mc.player == null) {
                    return false;
                }
                double dx = Double.parseDouble(args[1]);
                double dz = Double.parseDouble(args[2]);
                mc.player.capabilities.isFlying = true;
                mc.player.sendChatMessage(String.format(Locale.ROOT, "/tp @p ~%.2f ~ ~%.2f", dx, dz));
                return this.waited + 1 >= Integer.parseInt(args[3]);
            }
            case "screen" -> {
                switch (args[1]) {
                    case "options" -> mc.displayGuiScreen(new GuiOptions(mc.currentScreen, mc.gameSettings));
                    case "video" -> mc.displayGuiScreen(new CeleritasVideoOptionsScreen(mc.currentScreen));
                    case "modconfig" -> mc.displayGuiScreen(new DemonicaGuiFactory().createConfigGui(mc.currentScreen));
                    case "shaderpacks" -> {
                        if (mc.currentScreen instanceof CeleritasVideoOptionsScreen videoSettings) {
                            clickShaderPacksTab(mc, videoSettings);
                        } else {
                            Object screen = ShaderModBridge.openShaderScreen(mc.currentScreen);
                            if (!(screen instanceof GuiScreen guiScreen)) {
                                throw new IllegalStateException("Celeritas's shader pack tab opened no screen: " + screen);
                            }
                            mc.displayGuiScreen(guiScreen);
                        }
                    }
                    case "inventory" -> mc.displayGuiScreen(mc.playerController.isInCreativeMode()
                        ? new GuiContainerCreative(mc.player) : new GuiInventory(mc.player));
                    case "close" -> mc.displayGuiScreen(null);
                    default -> throw new IllegalArgumentException("Unknown screen: " + args[1]);
                }
                return true;
            }
            case "expect-screen" -> {
                String open = mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "none";
                if (!open.equals(args[1])) {
                    throw new IllegalStateException("Expected the " + args[1] + " screen, found " + open);
                }
                LOGGER.info("Dev screen: {} is open", open);
                return true;
            }
            case "press" -> {
                press(mc.currentScreen, Integer.parseInt(args[1]));
                return true;
            }
            case "reload" -> {
                mc.refreshResources();
                return true;
            }
            case "pack" -> {
                String pack = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (!Iris.enabled) {
                    LOGGER.info("Dev shader pack: not switched to {}, Iris is off", pack);
                    return true;
                }
                boolean enable = !pack.equalsIgnoreCase("off");
                IrisConfig config = Iris.getIrisConfig();
                if (enable) {
                    config.setShaderPackName(pack);
                }
                config.setShadersEnabled(enable);
                try {
                    // Iris.reload() reads the config back from disk.
                    config.save();
                    Iris.reload();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                LOGGER.info("Dev shader pack: {} (loaded: {})", Iris.getCurrentPackName(), Iris.getCurrentPack().isPresent());
                return true;
            }
            case "stats" -> {
                String terrain = mc.world != null ? mc.renderGlobal.getDebugInfoRenders() : "no world";
                LOGGER.info("Dev stats: {} fps; terrain {}; shadow sections {}", Minecraft.getDebugFPS(), terrain,
                    CeleritasWorldRendererCompat.shadowSections());
                return true;
            }
            case "log" -> {
                LOGGER.info("Dev marker: {}", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                return true;
            }
            case "exit" -> {
                LOGGER.info("Dev script finished; shutting down");
                mc.shutdown();
                return true;
            }
            default -> {
                BiFunction<DevHarness, String[], Boolean> handler = EXTRA_STEPS.get(name);
                if (handler == null) {
                    throw new IllegalArgumentException("Unknown dev step: " + name);
                }
                return handler.apply(this, args);
            }
        }
    }

    // GuiScreen.actionPerformed is how a click reaches a button's screen, and what mods inject into.
    private static void press(GuiScreen screen, int buttonId) {
        GuiScreenAccessor access = (GuiScreenAccessor) screen;
        GuiButton button = access.demonica$getButtonList().stream().filter(b -> b.id == buttonId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(screen.getClass().getSimpleName() + " has no button " + buttonId));
        try {
            access.demonica$actionPerformed(button);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot press button " + buttonId + " of " + screen, e);
        }
    }

    // Celeritas's Video Settings hands a click to its root frame, which passes it down to the widget under the cursor,
    // so a click on the root frame at the tab runs the tab's own action. The fields read here are Celeritas's own
    // names, which neither the dev remap nor production changes.
    private static void clickShaderPacksTab(Minecraft mc, CeleritasVideoOptionsScreen screen) {
        CeleritasVideoOptionsController controller =
            (CeleritasVideoOptionsController) readField(CeleritasVideoOptionsScreen.class, "controller", screen);
        AbstractFrame root = controller.getFrame();
        if (root == null) {
            throw new IllegalStateException("Video Settings has no frame");
        }
        FlatButtonWidget tab = findTab(root, SHADER_PACKS_TAB).orElseThrow(() -> new IllegalStateException(
            "Video Settings has no Shader Packs tab: Celeritas did not find Iris's API"));
        Dim2i dim = (Dim2i) readField(FlatButtonWidget.class, "dim", tab);
        int x = dim.getCenterX();
        int y = dim.getCenterY();
        LOGGER.info("Dev click: Celeritas's Shader Packs tab at ({}, {})", x, y);
        if (!root.mouseClicked(VintageInteractionContext.INSTANCE, x, y, 0)) {
            throw new IllegalStateException("A click at (" + x + ", " + y + ") reached no widget: is the Shader Packs "
                + "tab scrolled out of view?");
        }
        if (mc.currentScreen == screen) {
            throw new IllegalStateException("The Shader Packs tab opened no screen; see the log for ShaderModBridge's error");
        }
    }

    private static Optional<FlatButtonWidget> findTab(Interactable widget, String titleKey) {
        if (widget instanceof FlatButtonWidget button) {
            return button.getLabel() instanceof TextComponent.Translatable title && title.keys().contains(titleKey)
                ? Optional.of(button) : Optional.empty();
        }
        if (widget instanceof InteractableContainer container) {
            return container.interactableChildren().map(child -> findTab(child, titleKey))
                .flatMap(Optional::stream).findFirst();
        }
        return Optional.empty();
    }

    private static Object readField(Class<?> owner, String name, Object instance) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Celeritas's " + owner.getSimpleName() + "." + name, e);
        }
    }

    /** Where screenshots land, for steps that want to report them. */
    public static Path screenshotDir() {
        return Minecraft.getMinecraft().gameDir.toPath().resolve("screenshots");
    }
}
