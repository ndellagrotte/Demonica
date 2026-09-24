package com.demonica.celeritas.terrain;

import com.demonica.celeritas.api.shader.PassSemantics;
import com.demonica.celeritas.api.shader.ShaderProvider;
import com.demonica.celeritas.api.shader.ShaderProviderHolder;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.gl.framebuffer.MinecraftFramebufferHelper;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.nio.FloatBuffer;
import java.util.Collection;

/**
 * What the quarantined terrain patches share: whether a shader pack drives terrain, the pack's programs, the
 * eye-height camera it needs, the Iris phases of the terrain layers, and the check that the renderer was built for
 * the current pack.
 */
public final class ShaderTerrain {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");

    // Render thread only.
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f SCRATCH = new Matrix4f();

    // The eye height S8 added to the camera of the terrain draw it is about to make, or NaN. S6m takes it for that
    // draw's matrices, so the camera and the model-view move together: if either patch is missing, neither does.
    private static float pendingEyeHeight = Float.NaN;

    private ShaderTerrain() {
    }

    /** Whether a shader pack is loaded and Iris supplies the terrain programs. */
    public static boolean isPackActive() {
        return ShaderProviderHolder.isActive();
    }

    /** S3, S6s, S16: whether terrain is being set up or drawn for the shadow map. */
    public static boolean isShadowPass() {
        return ShaderProviderHolder.isShadowPass();
    }

    /**
     * S1: whether a section manager built now gets a shadow pass (a second render list and search, for the shadow
     * map). As on upstream's modern loaders ({@code ShaderModBridge.areShadersEnabled()}), that is whenever a pack is
     * active, even one without shadows, whose shadow lists then stay unused. The answer changes exactly when the pass
     * configuration does (S14), so {@link #reloadIfStale} needs no check of its own.
     */
    public static boolean needsShadowPass() {
        return isPackActive();
    }

    /** S6s: the matrices of the shadow pass's terrain draws, which ShadowRenderer sets for the frame. */
    public static ChunkRenderMatrices shadowMatrices() {
        return matrices(ShadowRenderer.PROJECTION, ShadowRenderer.MODELVIEW);
    }

    /**
     * The vertex type the loaded pack needs (Iris's extended format), or null when Celeritas's own choice stands.
     */
    public static ChunkVertexType packVertexType() {
        ShaderProvider provider = ShaderProviderHolder.getProvider();
        if (provider == null || !provider.isShadersEnabled()) {
            return null;
        }
        ChunkVertexType type = provider.getVertexType(ChunkMeshFormats.VANILLA_LIKE);
        return type != null && type != ChunkMeshFormats.VANILLA_LIKE ? type : null;
    }

    /** Whether the pass belongs to the configuration built for a pack, so its program comes from Iris. */
    public static boolean isShaderPass(TerrainRenderPass pass) {
        return PassSemantics.isTagged(pass);
    }

    /**
     * S2: the pack's program for a terrain pass, or null when Celeritas's own program stands: the pass was built by
     * upstream's builder, no pack is active, or the pack has no program for the pass.
     */
    public static @Nullable GlProgram<? extends ChunkShaderInterface> packProgram(TerrainRenderPass pass,
                                                                                    RenderPassConfiguration<?> configuration) {
        if (!isShaderPass(pass)) {
            return null;
        }
        ShaderProvider provider = ShaderProviderHolder.getProvider();
        if (provider == null || !provider.isShadersEnabled()) {
            return null;
        }
        // Iris links its programs against this configuration's vertex format.
        provider.setRenderPassConfiguration(configuration);
        return provider.getShaderOverride(pass);
    }

    /**
     * S8: enters the Iris phase of a terrain layer. Before the translucent layer this also runs Iris's translucent
     * prelude: the hand's solid parts are drawn and the pipeline switches to its translucent programs.
     */
    public static void beginLayer(BlockRenderLayer layer, float partialTicks) {
        WorldRenderingPipeline pipeline = pipeline();
        if (pipeline == null) {
            return;
        }
        if (layer == BlockRenderLayer.SOLID) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        } else if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        } else if (layer == BlockRenderLayer.CUTOUT) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() && IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
                beginTranslucents(pipeline, partialTicks);
            }
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        }
    }

    /** S8: leaves the terrain phase once the layer is drawn. */
    public static void endLayer() {
        pendingEyeHeight = Float.NaN;
        WorldRenderingPipeline pipeline = pipeline();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    private static @Nullable WorldRenderingPipeline pipeline() {
        return Iris.enabled ? Iris.getPipelineManager().getPipelineNullable() : null;
    }

    private static void beginTranslucents(WorldRenderingPipeline pipeline, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        MinecraftFramebufferHelper.restoreMinecraftFramebufferBuffers();
        pipeline.beginHand();
        HandRenderer.INSTANCE.renderSolid(partialTicks, Camera.INSTANCE, mc.renderGlobal, pipeline);
        mc.profiler.endStartSection("iris_pre_translucent");
        pipeline.beginTranslucents();
    }

    /**
     * S8: the camera Y of a terrain draw. Upstream draws terrain relative to the view entity's feet, the origin of
     * vanilla's model-view. Shader packs reconstruct world positions from gbufferModelView, whose origin is the eye,
     * so while a pack is active terrain is drawn relative to the eye, and S6m restores the eye height in the
     * model-view of the same draw ({@link #takeEyeMatrices}).
     */
    public static double eyeCameraY(double feetY, Entity view) {
        if (!isPackActive()) {
            return feetY;
        }
        float eyeHeight = view.getEyeHeight();
        pendingEyeHeight = eyeHeight;
        return feetY + eyeHeight;
    }

    /** S6m: the eye-anchored matrices for the draw S8 just moved to the eye, or null if it did not. */
    public static @Nullable ChunkRenderMatrices takeEyeMatrices() {
        float eyeHeight = pendingEyeHeight;
        if (Float.isNaN(eyeHeight)) {
            return null;
        }
        pendingEyeHeight = Float.NaN;
        Matrix4fc projection = RenderingState.INSTANCE.getProjectionMatrix();
        SCRATCH.set(RenderingState.INSTANCE.getModelViewMatrix()).translate(0f, eyeHeight, 0f);
        return matrices(projection, SCRATCH);
    }

    /** Matrices for a terrain draw; ChunkRenderMatrices copies the buffers, so they are reused. */
    public static ChunkRenderMatrices matrices(Matrix4fc projection, Matrix4fc modelView) {
        projection.get(0, PROJECTION);
        modelView.get(0, MODEL_VIEW);
        return new ChunkRenderMatrices(PROJECTION, MODEL_VIEW);
    }

    /**
     * The reload trigger: the section manager fixes its vertex type and pass configuration when it is built, and the
     * pack can change after that (it loads on the first rendered frame, after the world). Iris reloads the renderer
     * only when the pack's block settings change, so a pack without an ID map can be loaded or unloaded without one.
     * Returns true, and reloads, when the renderer does not match the pack.
     */
    public static boolean reloadIfStale() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        if (renderer == null || renderer.getRenderSectionManager() == null) {
            return false;
        }
        RenderPassConfiguration<?> configuration = renderer.getRenderPassConfiguration();
        boolean packActive = isPackActive();
        boolean shaderPasses = ShaderPassConfigurations.isShaderConfiguration(configuration);
        ChunkVertexType expected = packVertexType();
        boolean vertexTypeMatches = expected == null || usesVertexType(configuration, expected);
        if (packActive == shaderPasses && vertexTypeMatches) {
            return false;
        }
        LOGGER.info("Rebuilding Celeritas's renderer for the {} (shader passes {}, vertex type {})",
            packActive ? "loaded shader pack" : "unloaded shader pack", shaderPasses, vertexTypeMatches ? "ok" : "outdated");
        Minecraft.getMinecraft().renderGlobal.loadRenderers();
        return true;
    }

    private static boolean usesVertexType(RenderPassConfiguration<?> configuration, ChunkVertexType expected) {
        Collection<TerrainRenderPass> solid = configuration.vanillaRenderStages().get(BlockRenderLayer.SOLID);
        if (solid == null || solid.isEmpty()) {
            return true;
        }
        return solid.iterator().next().vertexType() == expected;
    }
}
