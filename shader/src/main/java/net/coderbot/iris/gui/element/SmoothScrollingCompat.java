package net.coderbot.iris.gui.element;

import net.coderbot.iris.Iris;
import net.minecraft.client.gui.GuiSlot;

import java.lang.reflect.Field;

/**
 * Co-existence with Universal Tweaks' smooth scrolling (its {@code UTGuiSlotMixin}, on by default), for the
 * lists built on {@link IrisGuiSlot} (ndellagrotte/Demonica#41).
 *
 * <p>That mixin adds a {@code float target} field to vanilla {@code GuiSlot} and, at the head of every
 * {@code drawScreen} while mouse button 0 is up, overwrites {@code amountScrolled} by easing it towards
 * {@code target}. It moves {@code target} only inside {@code GuiSlot.handleMouseInput()}: from the wheel, and
 * from {@code amountScrolled} while the button is held. Iris's lists never call that method: the screen reads
 * the wheel itself and {@link IrisGuiSlot#mouseScrolled} writes {@code amountScrolled} directly, and the
 * scrollbar drag runs in {@link IrisGuiSlot#drawScreen}. So {@code target} stayed at 0, and each frame the mixin
 * pulled the list back to the top: the wheel did nothing, and a drag snapped back on release.
 *
 * <p>The fix is to publish Iris's scroll position into {@code target} before every {@code super.drawScreen},
 * so the easing has nothing to undo. Vanilla {@code GuiSlot} has no field named {@code target}, so one of type
 * {@code float} can only come from such a mixin; no mod-id check is needed, and without it this class does
 * nothing. The lookup happens once, on first use, and never throws.
 */
public final class SmoothScrollingCompat {
    private static final String TARGET_FIELD = "target";

    private static boolean resolved;
    private static Field target;

    private SmoothScrollingCompat() {
    }

    /**
     * Makes the smooth-scrolling target, when a mixin added one, equal to {@code amountScrolled}. A no-op
     * otherwise.
     */
    public static void syncTarget(GuiSlot list, float amountScrolled) {
        final Field field = resolve();
        if (field == null) {
            return;
        }
        try {
            field.setFloat(list, amountScrolled);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            Iris.logger.warn("Cannot sync GuiSlot.{} with the list's scroll position; smooth scrolling may fight Iris's lists", TARGET_FIELD, e);
            target = null;
        }
    }

    /**
     * Returns whether a smooth-scrolling target field was found on {@code GuiSlot}.
     */
    public static boolean isActive() {
        return resolve() != null;
    }

    private static Field resolve() {
        if (!resolved) {
            resolved = true;
            target = lookUp();
        }
        return target;
    }

    private static Field lookUp() {
        try {
            final Field field = GuiSlot.class.getDeclaredField(TARGET_FIELD);
            if (field.getType() != float.class) {
                Iris.logger.debug("GuiSlot.{} is a {}, not a smooth-scrolling target; leaving it alone", TARGET_FIELD, field.getType());
                return null;
            }
            field.setAccessible(true);
            Iris.logger.info("Universal Tweaks' smooth scrolling detected: syncing its scroll target with Iris's lists");
            return field;
        } catch (NoSuchFieldException e) {
            Iris.logger.debug("GuiSlot has no smooth-scrolling target field; nothing to sync");
            return null;
        } catch (RuntimeException e) {
            Iris.logger.warn("Cannot inspect GuiSlot.{}; smooth scrolling may fight Iris's lists", TARGET_FIELD, e);
            return null;
        }
    }
}
