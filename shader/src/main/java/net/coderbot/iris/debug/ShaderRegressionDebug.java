package net.coderbot.iris.debug;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Logs of entity colour and phase, item state and block-metadata maps. Every method is a no-op, and
 * {@link #isEnabled()} false, until the diagnostics jar installs its implementation ({@link Diagnostics}),
 * com.demonica.diagnostics.iris.RegressionDiagnostics.
 */
public final class ShaderRegressionDebug {
    /** The implementation's side. */
    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default boolean isEnabled() {
            return false;
        }

        default void logEntityColor(String stage, EntityLivingBase entity, float r, float g, float b, float a) {
        }

        default void logEntityPhase(String stage, Entity entity, Render<?> renderer, String previousPhase,
                                    boolean beganEntityPhase) {
        }

        default void logItemState(String stage, ItemStack stack) {
        }

        default void logBlockMetaMap(String label, Block block, int... metadataKeys) {
        }
    }

    private static Hooks hooks = Hooks.NOOP;

    private ShaderRegressionDebug() {
    }

    /** Installs the implementation. Called once, on the render thread, by the diagnostics jar. */
    public static void install(Hooks implementation) {
        hooks = implementation != null ? implementation : Hooks.NOOP;
    }

    public static boolean isEnabled() {
        return hooks.isEnabled();
    }

    public static void logEntityColor(String stage, EntityLivingBase entity, float r, float g, float b, float a) {
        hooks.logEntityColor(stage, entity, r, g, b, a);
    }

    public static void logEntityPhase(String stage, Entity entity, Render<?> renderer, String previousPhase,
                                      boolean beganEntityPhase) {
        hooks.logEntityPhase(stage, entity, renderer, previousPhase, beganEntityPhase);
    }

    public static void logItemState(String stage, ItemStack stack) {
        hooks.logItemState(stage, stack);
    }

    public static void logBlockMetaMap(String label, Block block, int... metadataKeys) {
        hooks.logBlockMetaMap(label, block, metadataKeys);
    }
}
