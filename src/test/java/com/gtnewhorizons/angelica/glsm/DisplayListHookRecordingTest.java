package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.events.TextureUnitStateEvent;
import com.gtnewhorizons.angelica.glsm.recording.CommandBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import net.minecraftforge.eventbus.api.bus.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A GLSM hook posted while a GL_COMPILE_AND_EXECUTE list records: the listener's GL calls are its live reaction, not
 * part of the list. OpenComputers compiles its screen text that way during the world pass, and Iris's pass switch on
 * glDisable(GL_TEXTURE_2D) (USE_PROGRAM, VIEWPORT, ...) landed in the list, so the screen's GUI drew with a shader
 * pack's gbuffers program and came out black.
 */
class DisplayListHookRecordingTest {
    private CommandRecorder recorder;

    @BeforeEach
    void startCompileAndExecute() {
        this.recorder = new CommandRecorder();
        set("glListMode", GL11.GL_COMPILE_AND_EXECUTE);
        set("glListId", 1);
        set("currentRecorder", this.recorder);
        set("recordingThread", Thread.currentThread());
    }

    @AfterEach
    void stopRecording() {
        set("currentRecorder", null);
        set("recordingThread", null);
        set("glListMode", 0);
        set("glListId", -1);
        this.recorder.free();
    }

    @Test
    void listenerGlCallsStayOutOfTheList() {
        EventBus<TextureUnitStateEvent> bus = EventBus.create(TextureUnitStateEvent.class);
        // Iris's pass switch binds the pass's program: GLStateManager.glUseProgram records it while a list records.
        bus.addListener(event -> DisplayListManager.recordUseProgram(42));

        DisplayListManager.recordDisable(GL11.GL_TEXTURE_2D);
        DisplayListManager.postHook(bus, new TextureUnitStateEvent());

        CommandBuffer buffer = this.recorder.getBuffer();
        buffer.resetRead();
        assertEquals(GLCommand.DISABLE, buffer.readInt());
        assertEquals(GL11.GL_TEXTURE_2D, buffer.readInt());
        assertFalse(buffer.hasRemaining(), "the listener's USE_PROGRAM was recorded");
        assertTrue(DisplayListManager.isRecording());
    }

    @Test
    void listenerRunsAgainstLiveState() {
        EventBus<TextureUnitStateEvent> bus = EventBus.create(TextureUnitStateEvent.class);
        DisplayListManager.RecordMode[] seen = new DisplayListManager.RecordMode[1];
        bus.addListener(event -> seen[0] = DisplayListManager.getRecordMode());

        DisplayListManager.postHook(bus, new TextureUnitStateEvent());

        assertSame(DisplayListManager.RecordMode.NONE, seen[0]);
        assertSame(DisplayListManager.RecordMode.COMPILE_AND_EXECUTE, DisplayListManager.getRecordMode());
    }

    @Test
    void recordingResumesWhenAListenerThrows() {
        EventBus<TextureUnitStateEvent> bus = EventBus.create(TextureUnitStateEvent.class);
        bus.addListener(event -> {
            throw new IllegalStateException("listener failed");
        });

        assertThrows(IllegalStateException.class, () -> DisplayListManager.postHook(bus, new TextureUnitStateEvent()));

        assertTrue(DisplayListManager.isRecording());
    }

    private static void set(String name, Object value) {
        try {
            Field field = DisplayListManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
