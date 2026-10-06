package net.coderbot.iris.shadows;

import net.irisshaders.iris.api.v0.IrisShadowRenderCallback;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan item 4.1: the registry behind {@code IrisApi.registerShadowRenderCallback}, ported from upstream's
 * {@code shadows/ShadowRenderCallbacks}. Callbacks run in registration order with the pass's arguments, and one that
 * throws is logged and does not stop the rest (upstream catches {@code Throwable} per callback).
 */
class ShadowRenderCallbacksTest {
    @BeforeEach
    @AfterEach
    void clear() {
        ShadowRenderCallbacks.clear();
    }

    @Test
    void isEmptyUntilACallbackIsRegistered() {
        assertTrue(ShadowRenderCallbacks.isEmpty());

        ShadowRenderCallbacks.register((modelView, projection, x, y, z, tickDelta) -> { });

        assertFalse(ShadowRenderCallbacks.isEmpty());
    }

    @Test
    void invokesCallbacksInRegistrationOrderWithThePassArguments() {
        Matrix4f modelView = new Matrix4f().translate(1, 2, 3);
        Matrix4f projection = new Matrix4f().ortho(-8, 8, -8, 8, 0.05f, 256);
        List<String> calls = new ArrayList<>();

        for (String name : List.of("first", "second", "third")) {
            ShadowRenderCallbacks.register((mv, proj, x, y, z, tickDelta) -> {
                assertSame(modelView, mv);
                assertSame(projection, proj);
                assertEquals(10.5, x);
                assertEquals(64.0, y);
                assertEquals(-3.25, z);
                assertEquals(0.75f, tickDelta);
                calls.add(name);
            });
        }

        ShadowRenderCallbacks.invoke(modelView, projection, 10.5, 64.0, -3.25, 0.75f);

        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test
    void aThrowingCallbackDoesNotStopTheOthers() {
        List<String> calls = new ArrayList<>();
        IrisShadowRenderCallback failing = (mv, proj, x, y, z, tickDelta) -> {
            calls.add("failing");
            throw new IllegalStateException("callback failed");
        };
        IrisShadowRenderCallback linkageError = (mv, proj, x, y, z, tickDelta) -> {
            calls.add("error");
            throw new NoSuchMethodError("a Throwable that is not an Exception");
        };

        ShadowRenderCallbacks.register(failing);
        ShadowRenderCallbacks.register((mv, proj, x, y, z, tickDelta) -> calls.add("after-failing"));
        ShadowRenderCallbacks.register(linkageError);
        ShadowRenderCallbacks.register((mv, proj, x, y, z, tickDelta) -> calls.add("after-error"));

        ShadowRenderCallbacks.invoke(new Matrix4f(), new Matrix4f(), 0, 0, 0, 0);

        assertEquals(List.of("failing", "after-failing", "error", "after-error"), calls);
    }

    @Test
    void theSameCallbackRegisteredTwiceRunsTwice() {
        List<String> calls = new ArrayList<>();
        IrisShadowRenderCallback callback = (mv, proj, x, y, z, tickDelta) -> calls.add("call");

        ShadowRenderCallbacks.register(callback);
        ShadowRenderCallbacks.register(callback);
        ShadowRenderCallbacks.invoke(new Matrix4f(), new Matrix4f(), 0, 0, 0, 0);

        assertEquals(List.of("call", "call"), calls);
    }
}
