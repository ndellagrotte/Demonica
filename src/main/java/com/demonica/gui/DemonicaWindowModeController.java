package com.demonica.gui;

import com.demonica.mixin.core.MinecraftAccessor;
import com.demonica.runtime.DemonicaRuntime;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import com.demonica.config.DemonicaOptions;
import org.lwjgl.opengl.Display;

public final class DemonicaWindowModeController {
    private static final Logger LOGGER = DemonicaRuntime.logger();
    private static boolean synchronizing;
    private static String loggedInvalidFullscreenMode;
    private static final boolean borderlessSupported;
    private static boolean borderlessActive;
    private static boolean loggedBorderlessUnsupported;

    static {
        // FCL's Android runtime ships a stripped GLFW whose glfwGetWindowPos lacks the IntBuffer
        // overload, so Display.isBorderless() throws NoSuchMethodError there. Probe once and degrade
        // borderless to exclusive instead of crashing on every tick.
        boolean supported = false;
        try {
            borderlessActive = Display.isBorderless();
            supported = true;
        } catch (Throwable t) {
            LOGGER.warn(
                "Borderless fullscreen is not supported on this runtime, treating it as exclusive fullscreen: {}",
                t
            );
        }
        borderlessSupported = supported;
    }

    private DemonicaWindowModeController() {
    }

    public static FullscreenMode resolveConfiguredMode(DemonicaOptions options) {
        String configuredModeName = options.window.fullscreenMode;
        if (configuredModeName != null) {
            if ("FULLSCREEN".equals(configuredModeName)) {
                options.window.fullscreenMode = FullscreenMode.EXCLUSIVE.name();
                return FullscreenMode.EXCLUSIVE;
            }
            try {
                return FullscreenMode.valueOf(configuredModeName);
            } catch (IllegalArgumentException e) {
                if (!configuredModeName.equals(loggedInvalidFullscreenMode)) {
                    loggedInvalidFullscreenMode = configuredModeName;
                    LOGGER.warn(
                        "Unknown fullscreen mode '{}' in the options file, falling back to {}",
                        configuredModeName,
                        FullscreenMode.OFF
                    );
                }
            }
        }

        return FullscreenMode.OFF;
    }

    public static void applyMode(Minecraft client, DemonicaOptions options, FullscreenMode mode) {
        FullscreenMode modeToApply = effectiveMode(mode, borderlessSupported);
        if (modeToApply != mode) {
            warnBorderlessUnsupported();
        }
        options.window.fullscreenMode = modeToApply.name();
        if (modeToApply != FullscreenMode.OFF) {
            options.window.lastFullscreenMode = modeToApply.name();
        }
        client.gameSettings.fullScreen = modeToApply != FullscreenMode.OFF;
        synchronize(client);
    }

    public static void toggleFullscreen(Minecraft client) {
        DemonicaOptions options = DemonicaRuntime.options();
        FullscreenMode current = resolveConfiguredMode(options);
        if (current != FullscreenMode.OFF && options.window.lastFullscreenMode == null) {
            options.window.lastFullscreenMode = current.name();
        }
        applyMode(client, options, nextMode(current, resolveLastFullscreenMode(options)));
    }

    static FullscreenMode nextMode(FullscreenMode current, FullscreenMode lastFullscreenMode) {
        return current == FullscreenMode.OFF ? lastFullscreenMode : FullscreenMode.OFF;
    }

    static FullscreenMode resolveLastFullscreenMode(DemonicaOptions options) {
        String configuredModeName = options.window.lastFullscreenMode;
        if (configuredModeName == null) {
            FullscreenMode current = resolveConfiguredMode(options);
            if (current != FullscreenMode.OFF) {
                return current;
            }
        }
        if (configuredModeName != null) {
            try {
                FullscreenMode mode = FullscreenMode.valueOf(configuredModeName);
                if (mode != FullscreenMode.OFF) {
                    return mode;
                }
            } catch (IllegalArgumentException e) {
                if (!configuredModeName.equals(loggedInvalidFullscreenMode)) {
                    loggedInvalidFullscreenMode = configuredModeName;
                    LOGGER.warn(
                        "Unknown last fullscreen mode '{}', falling back to {}",
                        configuredModeName,
                        FullscreenMode.EXCLUSIVE
                    );
                }
            }
        }

        return FullscreenMode.EXCLUSIVE;
    }

    static FullscreenMode effectiveMode(FullscreenMode mode, boolean borderlessSupported) {
        return mode == FullscreenMode.BORDERLESS && !borderlessSupported ? FullscreenMode.EXCLUSIVE : mode;
    }

    private static void warnBorderlessUnsupported() {
        if (!loggedBorderlessUnsupported) {
            loggedBorderlessUnsupported = true;
            LOGGER.warn("Borderless fullscreen is not supported on this runtime, using exclusive fullscreen instead");
        }
    }

    public static void synchronize(Minecraft client) {
        if (synchronizing) {
            return;
        }

        DemonicaOptions options = DemonicaRuntime.options();
        FullscreenMode desiredMode = resolveConfiguredMode(options);
        if (options.window.fullscreenMode == null && client.gameSettings.fullScreen) {
            desiredMode = FullscreenMode.EXCLUSIVE;
            options.window.fullscreenMode = desiredMode.name();
        }
        FullscreenMode modeToApply = effectiveMode(desiredMode, borderlessSupported);
        if (modeToApply != desiredMode) {
            options.window.fullscreenMode = modeToApply.name();
            warnBorderlessUnsupported();
        }
        if (isWindowStateCompatible(client, modeToApply)) {
            return;
        }

        synchronizing = true;

        try {
            switch (modeToApply) {
                case OFF -> applyWindowed(client);
                case EXCLUSIVE -> applyExclusiveFullscreen(client);
                case BORDERLESS -> applyBorderlessFullscreen(client);
            }
        } finally {
            synchronizing = false;
        }
    }

    private static boolean isWindowStateCompatible(Minecraft client, FullscreenMode desiredMode) {
        return switch (desiredMode) {
            case OFF -> !client.isFullScreen() && !Display.isFullscreen() && !borderlessActive;
            case EXCLUSIVE -> client.isFullScreen() && Display.isFullscreen() && !borderlessActive;
            case BORDERLESS -> client.isFullScreen() && borderlessActive;
        };
    }

    private static void applyWindowed(Minecraft client) {
        if (borderlessActive) {
            Display.setBorderless(false);
            borderlessActive = false;
        }
        if (Display.isFullscreen()) {
            Display.setFullscreen(false);
        }
        setFullscreenState(client, false);
        updateClientDisplaySize(client);
    }

    private static void applyExclusiveFullscreen(Minecraft client) {
        if (borderlessActive) {
            Display.setBorderless(false);
            borderlessActive = false;
        }
        Display.setFullscreen(true);
        setFullscreenState(client, true);
        updateClientDisplaySize(client);
    }

    private static void applyBorderlessFullscreen(Minecraft client) {
        if (Display.isFullscreen()) {
            Display.setFullscreen(false);
        }
        Display.setBorderless(true);
        borderlessActive = true;
        setFullscreenState(client, true);
        updateClientDisplaySize(client);
    }

    private static void setFullscreenState(Minecraft client, boolean fullscreen) {
        ((MinecraftAccessor) client).demonica$setFullscreen(fullscreen);
        client.gameSettings.fullScreen = fullscreen;
    }

    private static void updateClientDisplaySize(Minecraft client) {
        int width = Display.getFramebufferWidth();
        int height = Display.getFramebufferHeight();

        if (width > 0 && height > 0) {
            client.displayWidth = width;
            client.displayHeight = height;
            client.resize(width, height);
        }
    }
}
