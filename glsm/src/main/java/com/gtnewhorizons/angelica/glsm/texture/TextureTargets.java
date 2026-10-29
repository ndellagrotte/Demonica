package com.gtnewhorizons.angelica.glsm.texture;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.function.IntConsumer;
import org.lwjgl.opengl.GL11;

/**
 * The target each texture name was last bound to through GLSM, so that a delete knows what kind of texture it is
 * dealing with.
 *
 * <p>GLSM only caches GL_TEXTURE_2D bindings, and its deferred delete (shrink the texture to 1x1, keep the name valid
 * until the next glGenTextures) binds and re-specifies the name as a GL_TEXTURE_2D. A name whose target is anything
 * else (Kirino's GL_TEXTURE_2D_ARRAY glyph atlas, a 3D or cube map texture) cannot take that path: the bind is
 * GL_INVALID_OPERATION and the texImage2D then lands on whatever 2D texture really is bound. The same goes for a name
 * GLSM never saw bound at all: DSA code (glCreateTextures + glBindTextureUnit) never passes through glBindTexture, so
 * the delete is the first call GLSM sees for it.
 *
 * <p>Also holds the deferred-delete set: the names shrunk to 1x1 (or reserved fresh) that the next glGenTextures
 * really deletes. While FML's splash runs, the splash thread binds textures and the main thread generates and
 * deletes them on another context (issue #57), so every method is synchronized.
 */
public final class TextureTargets {

    /** Returned by {@link #remove} for a name GLSM never saw bound. */
    public static final int UNKNOWN = 0;

    /**
     * A name from glGenTextures that no bind has turned into a texture object yet (glIsTexture is false). Not a
     * target: GLSM keeps such a name reserved instead of deleting or shrinking it.
     */
    public static final int FRESH = -1;

    private final Int2IntOpenHashMap targets = new Int2IntOpenHashMap();
    private final IntOpenHashSet deferred = new IntOpenHashSet();

    public TextureTargets() {
        targets.defaultReturnValue(UNKNOWN);
    }

    /** Records that {@code texture} was bound to {@code target}. Name 0 is never a texture object. */
    public synchronized void record(int texture, int target) {
        if (texture == 0) return;
        targets.put(texture, target);
    }

    /** Forgets {@code texture} and returns the target it was last bound to, or {@link #UNKNOWN}. */
    public synchronized int remove(int texture) {
        return targets.remove(texture);
    }

    /** Keeps {@code texture} valid until {@link #drainDeferred} deletes it, unless a bind rescues it first. */
    public synchronized void defer(int texture) {
        deferred.add(texture);
    }

    /** A bind of a deferred name keeps it alive: it leaves the deferred set. */
    public synchronized void rescue(int texture) {
        deferred.remove(texture);
    }

    public synchronized int deferredCount() {
        return deferred.size();
    }

    /** The deferred names, for logging. */
    public synchronized int[] deferredNames() {
        return deferred.toIntArray();
    }

    /** Hands every deferred name to {@code delete} exactly once and empties the set. */
    public synchronized void drainDeferred(IntConsumer delete) {
        if (deferred.isEmpty()) return;
        final IntIterator it = deferred.iterator();
        while (it.hasNext()) {
            delete.accept(it.nextInt());
        }
        deferred.clear();
    }

    /**
     * Whether a deleted texture of this target may take GLSM's shrink-and-defer path, which only handles
     * GL_TEXTURE_2D. Everything else, {@link #UNKNOWN} included, is deleted outright as the GL spec says.
     */
    public static boolean isDeferrable(int target) {
        return target == GL11.GL_TEXTURE_2D;
    }

    /** Whether {@code target} names a kind of texture at all, rather than {@link #UNKNOWN} or {@link #FRESH}. */
    public static boolean isKnown(int target) {
        return target > 0;
    }
}
