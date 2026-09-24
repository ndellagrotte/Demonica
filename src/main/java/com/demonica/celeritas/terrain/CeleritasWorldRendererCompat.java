package com.demonica.celeritas.terrain;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.celeritas.WorldRendererCompat;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * S7 (docs/celeritas/patches/S7.md): Celeritas's terrain for Iris's shadow pass. ShadowRenderer draws terrain through
 * {@link WorldRendererCompat}; this adapts Celeritas's world renderer to it. It is offered only while the renderer's
 * section manager has a shadow pass (S1), since {@code updateForShadowPass} throws without one, and only while S7's
 * access patch applied. Otherwise the shadow map holds no terrain.
 *
 * <p>The shadow pass runs first in a frame, just before the terrain pass's {@code setupTerrain}
 * (EntityRendererIrisMixin). That is the order upstream's shadow protocol expects: {@code setupShadowTerrain} runs
 * the frame's terrain search along with the shadow search, and {@code setupTerrain} reuses it. The terrain search
 * starts from the player camera's viewport for this frame, which {@link #beginFrame} takes from the camera
 * {@code setupTerrain} is about to receive.
 */
public final class CeleritasWorldRendererCompat implements WorldRendererCompat {
    private static final CeleritasWorldRendererCompat INSTANCE = new CeleritasWorldRendererCompat();

    // Render thread only: the player viewport of the frame being drawn, between beginFrame and endFrame.
    private @Nullable Viewport framePlayerViewport;

    // The sections of the shadow render lists at the last shadow pass, or -1 before the first.
    private int shadowSections = -1;

    private CeleritasWorldRendererCompat() {
    }

    /** The provider of WorldRendererCompatBridge: the adapter, or null when the renderer cannot draw shadow terrain. */
    public static @Nullable WorldRendererCompat current() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        if (!(renderer instanceof SimpleWorldRendererAccess)) {
            return null;
        }
        RenderSectionManager manager = renderer.getRenderSectionManager();
        return manager != null && manager.hasShadowPass() ? INSTANCE : null;
    }

    /** Before the frame's shadow pass: the player viewport, from the camera the terrain pass is about to set up with. */
    public static void beginFrame(ICamera camera) {
        INSTANCE.framePlayerViewport = camera instanceof ViewportProvider provider ? provider.sodium$createViewport() : null;
    }

    public static void endFrame() {
        INSTANCE.framePlayerViewport = null;
    }

    /** The sections with geometry the last shadow pass drew from, or -1 if none has drawn terrain yet. */
    public static int shadowSections() {
        return INSTANCE.shadowSections;
    }

    private static CeleritasWorldRenderer renderer() {
        return CeleritasWorldRenderer.instance();
    }

    @Override
    public int getVisibleChunkCount() {
        return renderer().getVisibleChunkCount();
    }

    @Override
    public String getChunksDebugString() {
        return renderer().getChunksDebugString();
    }

    @Override
    public void setCurrentViewport(Viewport viewport) {
        ((SimpleWorldRendererAccess) renderer()).demonica$setCurrentViewport(viewport);
    }

    @Override
    public Viewport getLastViewport() {
        return renderer().getLastViewport();
    }

    @Override
    public Viewport getPlayerViewport() {
        // Without beginFrame (a shadow pass run after setupTerrain), the terrain pass has already set up the frame.
        return this.framePlayerViewport != null ? this.framePlayerViewport : renderer().getLastViewport();
    }

    @Override
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        renderer().drawChunkLayer(renderLayer, x, y, z);
    }

    /** Draws each pass of the layers once: under pass consolidation, cutout and cutout-mipped share one pass. */
    @Override
    public void drawChunkLayersDeduplicated(Collection<BlockRenderLayer> renderLayers, double x, double y, double z) {
        CeleritasWorldRenderer renderer = renderer();
        RenderSectionManager manager = renderer.getRenderSectionManager();
        Set<TerrainRenderPass> passes = new LinkedHashSet<>();
        for (BlockRenderLayer renderLayer : renderLayers) {
            Collection<TerrainRenderPass> layerPasses = manager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);
            if (layerPasses != null) {
                passes.addAll(layerPasses);
            }
        }
        if (passes.isEmpty()) {
            return;
        }
        ChunkRenderMatrices matrices = ((SimpleWorldRendererAccess) renderer).demonica$createChunkRenderMatrices();
        CameraTransform occlusionCamera = renderer.getLastViewport().getTransform();
        CameraTransform camera = new CameraTransform(x, y, z);
        for (TerrainRenderPass pass : passes) {
            manager.renderLayer(matrices, pass, occlusionCamera, camera);
        }
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void markSectionGraphDirty() {
        renderer().scheduleTerrainUpdate();
    }

    @Override
    public void setupTerrain(Viewport viewport, SimpleWorldRenderer.CameraState cameraState, int frame, boolean spectator,
                             boolean updateChunksImmediately) {
        renderer().setupTerrain(viewport, cameraState, frame, spectator, updateChunksImmediately);
    }

    @Override
    public void setupShadowTerrain(Viewport playerViewport, Viewport shadowViewport, SimpleWorldRenderer.CameraState cameraState,
                                   int frame, boolean spectator) {
        CeleritasWorldRenderer renderer = renderer();
        if (renderer.getRenderSectionManager() == null || !renderer.getRenderSectionManager().hasShadowPass()) {
            return;
        }
        renderer.setupShadowTerrain(playerViewport, shadowViewport, cameraState, frame, spectator);
        this.shadowSections = renderer.getVisibleChunkCount();
        // S18, without a patch: the block entities of the shadow render lists (S3 has the manager in its shadow pass),
        // through the renderer's public iterator.
        renderer.forEachVisibleBlockEntity(ShadowRenderer.visibleTileEntities::add);
    }
}
