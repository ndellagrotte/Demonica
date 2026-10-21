package com.demonica.diagnostics.dev;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mouse input for dev scripts on Cleanroom's lwjgly, whose {@code org.lwjgl.input.Mouse} reads everything from the
 * {@code com.cleanroommc.lwjgly.spi.WindowBridge} that {@code LWJGLY.windowBridge()} holds (Cleanroom's SDL window).
 * The first scripted step wraps that bridge in a proxy which hands out the script's events before the window's own,
 * reports the script's cursor position, and reports a scripted button as held, so {@code Mouse.isButtonDown} agrees
 * with the events, as it does for a real drag. Everything else is delegated. All through reflection: lwjgly is not
 * on the compile classpath, and the classes are Cleanroom's own.
 *
 * <p>Units are the bridge's: window pixels, y downwards, SDL button numbers (1 left, 2 middle, 3 right), wheel in
 * notches ({@code Mouse.getEventDWheel} multiplies by 120).
 */
final class ScriptedMouse {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDevHarness");
    private static final int[] SDL_BUTTONS = {1, 3, 2};

    private static ScriptedMouse installed;

    private final Object real;
    private final Constructor<?> event;
    private final Deque<Object> queued = new ArrayDeque<>();
    private final boolean[] held = new boolean[9];
    private float x = Float.NaN;
    private float y = Float.NaN;

    private ScriptedMouse(Object real, Constructor<?> event) {
        this.real = real;
        this.event = event;
    }

    static ScriptedMouse get() {
        if (installed == null) {
            installed = install();
        }
        return installed;
    }

    private static ScriptedMouse install() {
        try {
            Class<?> lwjgly = Class.forName("com.cleanroommc.lwjgly.LWJGLY");
            Class<?> bridge = Class.forName("com.cleanroommc.lwjgly.spi.WindowBridge");
            Constructor<?> event = Class.forName("com.cleanroommc.lwjgly.spi.WindowBridge$MouseEvent").getConstructor(
                int.class, boolean.class, float.class, float.class, float.class, float.class, float.class, long.class);
            Object real = lwjgly.getMethod("windowBridge").invoke(null);
            ScriptedMouse mouse = new ScriptedMouse(real, event);
            Object proxy = Proxy.newProxyInstance(bridge.getClassLoader(), new Class<?>[] {bridge}, mouse.new Handler());
            lwjgly.getMethod("setWindowBridge", bridge).invoke(null, proxy);
            LOGGER.info("Dev mouse: wrapped the window bridge {} for scripted input", real.getClass().getName());
            return mouse;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot wrap lwjgly's window bridge for scripted mouse input", e);
        }
    }

    /** Moves the cursor to window pixels {@code (x, y)}, y downwards. */
    void move(float toX, float toY) {
        float dx = Float.isNaN(this.x) ? 0 : toX - this.x;
        float dy = Float.isNaN(this.y) ? 0 : toY - this.y;
        this.x = toX;
        this.y = toY;
        this.queue(0, false, dx, dy, 0);
    }

    /** Presses or releases an LWJGL button (0 left, 1 right, 2 middle) at the cursor. */
    void button(int lwjglButton, boolean pressed) {
        if (lwjglButton < 0 || lwjglButton >= SDL_BUTTONS.length) {
            throw new IllegalArgumentException("Mouse button " + lwjglButton + " is not 0, 1 or 2");
        }
        int sdl = SDL_BUTTONS[lwjglButton];
        this.held[sdl] = pressed;
        this.queue(sdl, pressed, 0, 0, 0);
    }

    /** Turns the wheel by {@code notches} at the cursor; positive is up. */
    void wheel(float notches) {
        this.queue(0, false, 0, 0, notches);
    }

    private void queue(int sdlButton, boolean pressed, float dx, float dy, float wheel) {
        if (Float.isNaN(this.x)) {
            throw new IllegalStateException("No scripted cursor position yet: run a 'mouse <x> <y>' step first");
        }
        try {
            this.queued.add(this.event.newInstance(sdlButton, pressed, this.x, this.y, dx, dy, wheel, System.nanoTime()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build a WindowBridge.MouseEvent", e);
        }
    }

    private final class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "nextMouseEvent" -> {
                    Object next = queued.poll();
                    if (next != null) {
                        return next;
                    }
                }
                case "queuedMouseEvents" -> {
                    return (Integer) delegate(method, args) + queued.size();
                }
                case "mouseButtonDown" -> {
                    int sdl = (Integer) args[0];
                    if (sdl >= 0 && sdl < held.length && held[sdl]) {
                        return true;
                    }
                }
                case "mouseX" -> {
                    if (!Float.isNaN(x)) {
                        return x;
                    }
                }
                case "mouseY" -> {
                    if (!Float.isNaN(y)) {
                        return y;
                    }
                }
                default -> {
                }
            }
            return delegate(method, args);
        }

        private Object delegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
