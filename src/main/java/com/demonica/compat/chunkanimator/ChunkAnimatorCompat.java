package com.demonica.compat.chunkanimator;

import com.demonica.celeritas.api.animation.ChunkAnimationProvider;
import com.demonica.celeritas.api.animation.ChunkAnimationProviderHolder;
import com.demonica.compat.Mods;
import lumien.chunkanimator.ChunkAnimator;
import lumien.chunkanimator.handler.AnimationHandler;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.vector.Vector3f;

import java.util.Objects;

/**
 * Bridges Chunk Animator (mod id {@code chunkanimator}, 1.12.2-1.2.1) into Celeritas's terrain pipeline.
 *
 * <p>Chunk Animator is an ASM coremod that hooks {@code RenderChunk#setOrigin} to start a chunk's animation clock and
 * {@code ChunkRenderContainer#preRenderChunk} to translate the chunk while it is drawn. Celeritas draws no vanilla
 * {@code RenderChunk} and never calls {@code preRenderChunk}, so both hooks are inert and the animations never play.
 * This class re-attaches the same two calls to Celeritas's section lifecycle through {@link ChunkAnimationProvider}
 * (patch C2):</p>
 * <ul>
 *   <li>{@link #onSectionAdded} stands in for {@code RenderChunk#setOrigin}: the section has entered the render
 *   distance, so Chunk Animator records it (its clock starts lazily, at the first {@code getOffset}).</li>
 *   <li>{@link #getSectionOffset} stands in for {@code ChunkRenderContainer#preRenderChunk}: the section is drawn
 *   translated by Chunk Animator's current offset.</li>
 * </ul>
 *
 * <p>Chunk Animator's {@link AnimationHandler} takes plain {@code Object} tokens and keys its state by identity in a
 * {@code WeakHashMap}, so the {@link RenderSection} itself is the token: disposed sections are reclaimed without any
 * cleanup here, and the handler drops a section's entry itself once its animation has run. Animation mode, easing,
 * duration and "disable around player" stay Chunk Animator's own settings.</p>
 *
 * <p>Ported from Actinium's {@code ChunkAnimatorCompat} (Actinium PR #44, at {@code actinium@4a19c959}). Only
 * {@link #install()} may be called without Chunk Animator present, and only behind {@link Mods#CHUNKANIMATOR}: every
 * other member references the mod's classes.</p>
 */
public final class ChunkAnimatorCompat implements ChunkAnimationProvider {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");

    private final AnimationHandler animationHandler;

    private ChunkAnimatorCompat(AnimationHandler animationHandler) {
        this.animationHandler = Objects.requireNonNull(animationHandler, "animationHandler");
    }

    /**
     * Installs the provider when Chunk Animator is loaded. Call it from the host's init phase: FML runs every mod's
     * pre-init before any mod's init, and Chunk Animator builds its animation handler in pre-init.
     */
    public static void install() {
        if (!Mods.CHUNKANIMATOR) {
            return;
        }
        ChunkAnimator instance = ChunkAnimator.INSTANCE;
        if (instance == null || instance.animationHandler == null) {
            LOGGER.error("Chunk Animator is loaded but its animation handler is unavailable; chunk animations will not be applied");
            return;
        }
        ChunkAnimationProviderHolder.setProvider(new ChunkAnimatorCompat(instance.animationHandler));
        LOGGER.info("Chunk Animator compatibility layer enabled");
    }

    @Override
    public void onSectionAdded(@NotNull RenderSection section) {
        this.animationHandler.setOrigin(section, origin(section));
    }

    @Override
    public boolean getSectionOffset(@NotNull RenderSection section, @NotNull float[] out) {
        Vector3f offset = this.animationHandler.getOffset(section, origin(section));
        if (offset == null) {
            return false;
        }
        out[0] = offset.x;
        out[1] = offset.y;
        out[2] = offset.z;
        return true;
    }

    private static BlockPos origin(RenderSection section) {
        return new BlockPos(section.getOriginX(), section.getOriginY(), section.getOriginZ());
    }
}
