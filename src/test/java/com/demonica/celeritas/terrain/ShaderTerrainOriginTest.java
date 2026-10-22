package com.demonica.celeritas.terrain;

import com.demonica.celeritas.api.shader.BlockRenderLayer;
import com.demonica.celeritas.api.shader.ShaderProvider;
import com.demonica.celeritas.api.shader.ShaderProviderHolder;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The S8/S6m origin handshake: S8 moves the camera of one terrain draw by the camera's render origin, on all three
 * axes, and S6m translates that draw's model-view by the same vector, once. A Y-only translate, a stale origin left for
 * a later createChunkRenderMatrices call, or a handshake without a pack would put terrain off gbufferModelView and
 * cameraPosition by up to the third-person camera distance.
 */
class ShaderTerrainOriginTest {
    private static final Matrix4f MODEL_VIEW = new Matrix4f()
        .rotateX(0.4f).rotateY(2.1f).translate(0.3f, -1.62f, -4f);

    private final Matrix4f savedModelView = new Matrix4f();
    private final Vector3f savedOrigin = new Vector3f();

    @BeforeEach
    void setUp() {
        savedModelView.set(RenderingState.INSTANCE.getModelViewMatrix());
        savedOrigin.set(Camera.INSTANCE.getOrigin());
        RenderingState.INSTANCE.setModelViewMatrix(MODEL_VIEW);
        Camera.INSTANCE.getOrigin().set(1f, 2f, 3f);
    }

    @AfterEach
    void tearDown() {
        ShaderTerrain.takeOriginMatrices();
        ShaderProviderHolder.setProvider(null);
        RenderingState.INSTANCE.setModelViewMatrix(savedModelView);
        Camera.INSTANCE.getOrigin().set(savedOrigin);
    }

    @Test
    void noPackLeavesTheDrawAlone() {
        ShaderProviderHolder.setProvider(null);

        assertNull(ShaderTerrain.terrainOrigin());
        assertNull(ShaderTerrain.takeOriginMatrices());
    }

    @Test
    void originIsTakenOnceOnAllThreeAxes() {
        ShaderProviderHolder.setProvider(new ActiveProvider());

        Vector3fc origin = ShaderTerrain.terrainOrigin();
        assertNotNull(origin);
        assertEquals(new Vector3f(1f, 2f, 3f), new Vector3f(origin));

        ChunkRenderMatrices matrices = ShaderTerrain.takeOriginMatrices();
        assertNotNull(matrices);
        float[] expected = new Matrix4f(MODEL_VIEW).translate(1f, 2f, 3f).get(new float[16]);
        assertArrayEquals(expected, matrices.modelView().get(new float[16]), 1e-6f);

        assertNull(ShaderTerrain.takeOriginMatrices(), "a second createChunkRenderMatrices call must keep its own matrices");
    }

    @Test
    void handedOverOriginIsUnaffectedByLaterCameraUpdates() {
        ShaderProviderHolder.setProvider(new ActiveProvider());

        ShaderTerrain.terrainOrigin();
        Camera.INSTANCE.getOrigin().set(9f, 9f, 9f);

        ChunkRenderMatrices matrices = ShaderTerrain.takeOriginMatrices();
        assertNotNull(matrices);
        float[] expected = new Matrix4f(MODEL_VIEW).translate(1f, 2f, 3f).get(new float[16]);
        assertArrayEquals(expected, matrices.modelView().get(new float[16]), 1e-6f);
    }

    @Test
    void leavingTheLayerDropsAnUntakenOrigin() {
        ShaderProviderHolder.setProvider(new ActiveProvider());
        ShaderTerrain.resetLayer();

        ShaderTerrain.terrainOrigin();
        ShaderTerrain.endLayer(true);

        assertNull(ShaderTerrain.takeOriginMatrices());
    }

    private static final class ActiveProvider implements ShaderProvider {
        @Override
        public boolean isShadersEnabled() {
            return true;
        }

        @Override
        public boolean isShadowPass() {
            return false;
        }

        @Override
        public boolean shouldUseFaceCulling() {
            return true;
        }

        @Override
        public GlProgram<? extends ChunkShaderInterface> getShaderOverride(TerrainRenderPass pass) {
            return null;
        }

        @Override
        public ChunkVertexType getVertexType(ChunkVertexType defaultType) {
            return defaultType;
        }

        @Override
        public void setRenderPassConfiguration(RenderPassConfiguration<?> configuration) {
        }

        @Override
        public Map<Block, BlockRenderLayer> getBlockTypeIds() {
            return null;
        }
    }
}
