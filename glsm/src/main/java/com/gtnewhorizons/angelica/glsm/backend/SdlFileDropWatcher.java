package com.gtnewhorizons.angelica.glsm.backend;

import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_DropEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SDL 3 implementation of window-level file drag & drop watching (issue #122).
 *
 * Motivation: the shader pack selection screen lets users drop pack or settings files
 * onto the window instead of manually copying them into the shaderpacks directory.
 * The window event plumbing is kept out of
 * {@link com.gtnewhorizons.angelica.lwjgl3.Lwjgl3GLRenderBackend} so the GL backend
 * classes stay pure call forwarders.
 *
 * Mechanism: an SDL event watch ({@code SDL_AddEventWatch}). Watches are additive, so the
 * loader's own event handling (Cleanroom dispatches drops itself) keeps working and nothing
 * has to be chained or restored. The watch sees every event pushed while it is installed;
 * it copies the path of each {@code SDL_EVENT_DROP_FILE} into a queue.
 *
 * Lifecycle: start/stop around screen visibility (must be idempotent), poll each frame
 * on the main thread. SDL runs watches synchronously inside {@code SDL_PushEvent}, which
 * the platform backends call while the window's events are pumped; Cleanroom pumps on the
 * client main thread. SDL documents that a watch may run on any thread, hence the
 * concurrent queue.
 *
 * The native callback is created once with {@link SDL_EventFilter#create} and kept in a
 * field: the same object has to be passed to {@code SDL_RemoveEventWatch} (a bare lambda
 * would allocate a new native stub on every call, which neither matches the registered
 * watch nor gets freed).
 */
public final class SdlFileDropWatcher {
    private static SDL_EventFilter filter;
    private static final ConcurrentLinkedQueue<String> DROPPED_FILES = new ConcurrentLinkedQueue<>();

    private SdlFileDropWatcher() {}

    /** Starts queueing paths of files dropped onto any SDL window. */
    public static void start() {
        if (filter != null) {
            return;
        }

        final SDL_EventFilter created = SDL_EventFilter.create((userdata, event) -> {
            // Read only the type before touching the union's drop member.
            if (SDL_Event.ntype(event) == SDLEvents.SDL_EVENT_DROP_FILE) {
                // Copies the UTF-8 path; SDL frees the event's temporary string after dispatch.
                final String path = SDL_DropEvent.ndataString(event);
                if (path != null && !path.isEmpty()) {
                    DROPPED_FILES.add(path);
                }
            }
            // Ignored for event watches; true keeps the event either way.
            return true;
        });
        if (!SDLEvents.SDL_AddEventWatch(created, 0L)) {
            created.free();
            throw new IllegalStateException("Cannot watch file drops: SDL_AddEventWatch failed: " + SDLError.SDL_GetError());
        }
        filter = created;
    }

    /** Stops queueing, removes the event watch and releases its native callback. */
    public static void stop() {
        if (filter == null) {
            return;
        }

        SDLEvents.SDL_RemoveEventWatch(filter, 0L);
        filter.free();
        filter = null;
        DROPPED_FILES.clear();
    }

    /** Returns the file paths dropped since the last call. */
    public static List<String> pollDroppedFiles() {
        if (DROPPED_FILES.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> out = new ArrayList<>();
        String path;
        while ((path = DROPPED_FILES.poll()) != null) {
            out.add(path);
        }
        return out;
    }
}
