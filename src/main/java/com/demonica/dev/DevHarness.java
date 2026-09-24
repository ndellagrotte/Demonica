package com.demonica.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Drives a dev client through a scripted session so rendering can be checked without input tools: load a
 * world from a fixed seed, run commands, wait for terrain, take screenshots and exit. It does nothing unless
 * {@code -Ddemonica.dev.script} is set, either to the steps themselves (separated by {@code ;}) or to
 * {@code @path} of a file with one step per line.
 *
 * <p>Steps:
 * <ul>
 *   <li>{@code world <folder> <seed>}: from the main menu, recreate the creative world {@code folder} from {@code seed}</li>
 *   <li>{@code wait <ticks>}: wait for client ticks</li>
 *   <li>{@code cmd <command>}: send a chat command as the player</li>
 *   <li>{@code chunks <maxTicks>}: wait until no terrain is left to build, or for at most {@code maxTicks}</li>
 *   <li>{@code shot <name>}: save {@code screenshots/<name>.png} after the next frame, with the HUD hidden</li>
 *   <li>{@code third <0|1|2>}: set the camera mode</li>
 *   <li>{@code glide <dx> <dz> <ticks>}: move the player by {@code dx, dz} blocks every tick (fast flight)</li>
 *   <li>{@code reload}: reload resources (F3+T)</li>
 *   <li>{@code log <text>}: write a marker to the log</li>
 *   <li>{@code exit}: shut the client down</li>
 * </ul>
 * Other features register further steps through {@link #registerStep}.
 */
public final class DevHarness {
    public static final String SCRIPT_PROPERTY = "demonica.dev.script";
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDevHarness");
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
                mc.gameSettings.hideGUI = true;
                this.shotPending = true;
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
            case "reload" -> {
                mc.refreshResources();
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

    /** Where screenshots land, for steps that want to report them. */
    public static Path screenshotDir() {
        return Minecraft.getMinecraft().gameDir.toPath().resolve("screenshots");
    }
}
