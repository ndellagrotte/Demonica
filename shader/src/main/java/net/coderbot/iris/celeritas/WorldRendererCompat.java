package net.coderbot.iris.celeritas;

import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;

import java.util.Collection;

/**
 * World-terrain rendering surface needed by the shadow pass.
 *
 * <p>Implemented by the host mod's world renderer and registered via
 * {@link WorldRendererCompatBridge}, so the shader module does not depend on
 * the host's rendering implementation.
 */
public interface WorldRendererCompat {

    int getVisibleChunkCount();

    String getChunksDebugString();

    void setCurrentViewport(Viewport viewport);

    /**
     * @return the viewport the renderer last set up or drew with: the player camera's, or during the shadow pass the
     * shadow frustum's.
     */
    Viewport getLastViewport();

    /**
     * @return the player camera's viewport for the frame being drawn. The shadow pass's terrain search starts from it,
     * and the shadow pass restores it when it ends. The shadow pass runs before the terrain pass has set up the
     * frame, so this is not {@link #getLastViewport()}.
     */
    Viewport getPlayerViewport();

    void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z);

    void drawChunkLayersDeduplicated(Collection<BlockRenderLayer> renderLayers, double x, double y, double z);

    /** Marks the terrain section graph dirty for re-culling (used by the shadow pass). */
    void markSectionGraphDirty();

    void setupTerrain(Viewport viewport, SimpleWorldRenderer.CameraState cameraState, int frame, boolean spectator, boolean updateChunksImmediately);

    /**
     * Shadow-pass counterpart of {@link #setupTerrain}. The shadow pass precedes the terrain pass in a frame, so the
     * terrain search it runs first needs the player camera's viewport ({@link #getPlayerViewport()}).
     *
     * @param playerViewport the player camera's viewport for this frame
     * @param shadowViewport the shadow frustum, centred on the player camera
     * @param spectator      whether the player is a spectator, as for {@link #setupTerrain}: the terrain search does not
     *                       cull by occlusion from a spectator camera inside a solid block
     */
    void setupShadowTerrain(Viewport playerViewport, Viewport shadowViewport, SimpleWorldRenderer.CameraState cameraState, int frame, boolean spectator);
}
