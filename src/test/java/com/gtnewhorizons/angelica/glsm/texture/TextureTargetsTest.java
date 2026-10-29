package com.gtnewhorizons.angelica.glsm.texture;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM's deferred texture delete shrinks the texture as a GL_TEXTURE_2D, so only a name GLSM saw bound to that target
 * may take it. Kirino's glyph atlas is a GL_TEXTURE_2D_ARRAY created with DSA, which GLSM never sees bound: shrinking
 * it as 2D corrupted the vanilla font texture that was really bound.
 */
class TextureTargetsTest {

    @Test
    void only2DTexturesAreDeferrable() {
        TextureTargets targets = new TextureTargets();
        targets.record(1, GL11.GL_TEXTURE_2D);
        targets.record(2, GL30.GL_TEXTURE_2D_ARRAY);
        targets.record(3, GL12.GL_TEXTURE_3D);
        targets.record(4, GL13.GL_TEXTURE_CUBE_MAP);

        assertTrue(TextureTargets.isDeferrable(targets.remove(1)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(2)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(3)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(4)));
    }

    @Test
    void aNameNeverSeenBoundIsUnknownAndNotDeferrable() {
        TextureTargets targets = new TextureTargets();
        // Kirino on a 4.5+ context: glCreateTextures + glBindTextureUnit, then glDeleteTextures through GLSM.
        assertEquals(TextureTargets.UNKNOWN, targets.remove(42));
        assertFalse(TextureTargets.isDeferrable(TextureTargets.UNKNOWN));
    }

    @Test
    void removeForgetsAndTheLastBindWins() {
        TextureTargets targets = new TextureTargets();
        targets.record(7, GL11.GL_TEXTURE_2D);
        assertEquals(GL11.GL_TEXTURE_2D, targets.remove(7));
        assertEquals(TextureTargets.UNKNOWN, targets.remove(7));

        targets.record(8, GL11.GL_TEXTURE_2D);
        targets.record(8, GL30.GL_TEXTURE_2D_ARRAY);
        assertEquals(GL30.GL_TEXTURE_2D_ARRAY, targets.remove(8));
    }

    @Test
    void freshAndUnknownNamesAreNeitherDeferrableNorKnown() {
        // FRESH: a glGenTextures name no bind has turned into an object yet (GLSM reserves it instead).
        assertFalse(TextureTargets.isDeferrable(TextureTargets.FRESH));
        assertFalse(TextureTargets.isKnown(TextureTargets.FRESH));
        assertFalse(TextureTargets.isKnown(TextureTargets.UNKNOWN));
        assertTrue(TextureTargets.isKnown(GL11.GL_TEXTURE_2D));
        assertTrue(TextureTargets.isKnown(GL30.GL_TEXTURE_2D_ARRAY));
    }

    @Test
    void nameZeroIsNeverRecorded() {
        TextureTargets targets = new TextureTargets();
        targets.record(0, GL11.GL_TEXTURE_2D);
        assertEquals(TextureTargets.UNKNOWN, targets.remove(0));
    }

    @Test
    void aDeferredNameIsDeletedOnceByTheDrainUnlessABindRescuedIt() {
        TextureTargets targets = new TextureTargets();
        targets.defer(5);
        targets.defer(6);
        targets.defer(6);
        targets.rescue(5);
        assertEquals(1, targets.deferredCount());

        List<Integer> deleted = new ArrayList<>();
        targets.drainDeferred(deleted::add);
        assertEquals(List.of(6), deleted);
        assertEquals(0, targets.deferredCount());

        targets.drainDeferred(deleted::add);
        assertEquals(List.of(6), deleted, "a drained set is empty");
    }

    /**
     * Issue #57: while FML's splash runs, the splash thread binds textures (record/rescue) while the main thread, on
     * the shared off-screen context, generates and deletes them (defer/drain). The fastutil structures underneath are
     * not safe for that on their own.
     */
    @Test
    void twoThreadsCanRecordAndDeferAtOnce() throws InterruptedException {
        TextureTargets targets = new TextureTargets();
        final int rounds = 100_000;
        Throwable[] failure = new Throwable[1];
        Thread binder = new Thread(() -> {
            try {
                for (int i = 1; i <= rounds; i++) {
                    targets.record(i, GL11.GL_TEXTURE_2D);
                    targets.rescue(i);
                }
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "binder");
        Thread deleter = new Thread(() -> {
            try {
                for (int i = 1; i <= rounds; i++) {
                    targets.defer(i);
                    if ((i & 1023) == 0) targets.drainDeferred(name -> {});
                }
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "deleter");
        binder.start();
        deleter.start();
        binder.join(30_000);
        deleter.join(30_000);
        assertFalse(binder.isAlive() || deleter.isAlive(), "a corrupted hash table spins forever");
        assertNull(failure[0]);

        // Whatever survived is drained exactly once and the map is still a working map.
        List<Integer> drained = new ArrayList<>();
        targets.drainDeferred(drained::add);
        assertEquals(drained.size(), drained.stream().distinct().count());
        assertEquals(0, targets.deferredCount());
        targets.record(1, GL30.GL_TEXTURE_2D_ARRAY);
        assertEquals(GL30.GL_TEXTURE_2D_ARRAY, targets.remove(1));
    }
}
