package net.coderbot.iris.celeritas.vertices;

import net.coderbot.iris.debug.IrisGlDebug;
import net.coderbot.iris.vertices.NormalHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockStaticLiquid;
import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.joml.Vector3f;
import com.demonica.celeritas.api.shader.ShaderProvider;
import com.demonica.celeritas.api.shader.ShaderProviderHolder;
import com.demonica.celeritas.api.shader.vertex.BlockRenderContext;
import com.demonica.celeritas.api.shader.vertex.ContextAwareChunkVertexEncoder;
import net.coderbot.iris.vertices.ExtendedDataHelper;

import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

public class ExtendedChunkVertexEncoder implements ContextAwareChunkVertexEncoder {
    private static final float TEX_CENTROID_BIAS = 1.0f / 32768.0f;
    /** Where the extended attributes start (see {@link #writeBase}). */
    private static final int BASE_STRIDE = 28;

    private final ExtendedChunkVertexType vertexType;
    private final LwjglQuadView quad = new LwjglQuadView();
    private final Vector3f normal = new Vector3f();
    private final int midTexOffset;
    private final int tangentOffset;
    private final int normalOffset;
    private final int mcEntityOffset;
    private final int midBlockOffset;
    private final int texCoordOffset;
    private final int stride;

    private BlockRenderContext context;
    private int vertexCount;
    private float uSum;
    private float vSum;

    public ExtendedChunkVertexEncoder(ExtendedChunkVertexType vertexType) {
        this.vertexType = vertexType;
        this.midTexOffset = getOffset(TerrainVertexFormatRequirements.Attribute.MID_TEX_COORD, "mc_midTexCoord");
        this.tangentOffset = getOffset(TerrainVertexFormatRequirements.Attribute.TANGENT, "at_tangent");
        this.normalOffset = getOffset(TerrainVertexFormatRequirements.Attribute.NORMAL, "iris_Normal");
        this.mcEntityOffset = getOffset(TerrainVertexFormatRequirements.Attribute.MC_ENTITY, "mc_Entity");
        this.midBlockOffset = getOffset(TerrainVertexFormatRequirements.Attribute.MID_BLOCK, "at_midBlock");
        this.texCoordOffset = vertexType.getVertexFormat().getAttribute("a_TexCoord").getPointer();
        this.stride = vertexType.getVertexFormat().getStride();
    }

    @Override
    public boolean supportsBilinearCorrection() {
        return false;
    }

    @Override
    public void prepareToRenderBlock(BlockRenderContext ctx, Block block, int metadata, short renderType, byte lightValue) {
        if (!requiresBlockContext()) {
            return;
        }
        this.context = ctx;
        if (this.mcEntityOffset >= 0) {
            ctx.blockId = resolveBlockStateId(block, metadata);
            ctx.renderType = renderType;
        }
        if (this.midBlockOffset >= 0) {
            ctx.lightValue = lightValue;
        }
    }

    @Override
    public void prepareToRenderFluid(BlockRenderContext ctx, Block block, int metadata, byte lightValue) {
        if (!requiresBlockContext()) {
            return;
        }
        this.context = ctx;
        if (this.mcEntityOffset >= 0) {
            ctx.blockId = resolveFluidBlockStateId(block, metadata);
            ctx.renderType = ExtendedDataHelper.FLUID_RENDER_TYPE;
        }
        if (this.midBlockOffset >= 0) {
            ctx.lightValue = lightValue;
        }
    }

    @Override
    public void prepareToRenderVanilla(BlockRenderContext ctx) {
        if (requiresBlockContext()) {
            this.context = ctx;
        }
    }

    @Override
    public void finishRenderingBlock() {
        if (this.context != null) {
            this.context.reset();
            this.context = null;
        }
        this.vertexCount = 0;
        this.uSum = 0.0f;
        this.vSum = 0.0f;
    }

    @Override
    public long write(long ptr, Material material, Vertex vertex, int sectionIndex) {
        if (this.midTexOffset >= 0) {
            this.uSum += vertex.u;
            this.vSum += vertex.v;
        }
        if (needsQuadData()) {
            this.vertexCount++;
        }

        vertex.rdhFactor = 0;
        writeBase(ptr, material, vertex, sectionIndex);

        BlockRenderContext ctx = this.context;
        if (this.mcEntityOffset >= 0 || this.midBlockOffset >= 0) {
            if (ctx == null) {
                ctx = new BlockRenderContext();
            }
        }

        if (this.mcEntityOffset >= 0) {
            LWJGL.memPutInt(ptr + this.mcEntityOffset, ((ctx.blockId + 1) << 1) | (ctx.renderType & 1));
            if (this.vertexCount == 1) {
                IrisGlDebug.logTerrainMaterialSample(
                        "encoder",
                        ctx.blockId,
                        ctx.renderType,
                        ctx.lightValue,
                        ctx.localPosX,
                        ctx.localPosY,
                        ctx.localPosZ,
                        vertex.u,
                        vertex.v
                );
            }
        }

        if (this.midBlockOffset >= 0) {
            int midBlock = ExtendedDataHelper.computeMidBlock(vertex.x, vertex.y, vertex.z, ctx.localPosX, ctx.localPosY, ctx.localPosZ);
            LWJGL.memPutInt(ptr + this.midBlockOffset, midBlock);
            LWJGL.memPutByte(ptr + this.midBlockOffset + 3L, ctx.lightValue);
        }

        if (needsQuadData() && this.vertexCount == 4) {
            this.vertexCount = 0;

            if (this.midTexOffset >= 0) {
                int midUV = ExtendedChunkVertexType.encodeMidTexture(this.uSum * 0.25f, this.vSum * 0.25f);
                writeQuadInt(ptr, this.midTexOffset, midUV);

                float midU = this.uSum * 0.25f;
                float midV = this.vSum * 0.25f;
                for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                    long uvBase = ptr - (long) (3 - vertexIndex) * this.stride + this.texCoordOffset;
                    float vertexU = LWJGL.memGetFloat(uvBase);
                    float vertexV = LWJGL.memGetFloat(uvBase + 4L);

                    LWJGL.memPutFloat(uvBase, vertexU + (vertexU < midU ? TEX_CENTROID_BIAS : -TEX_CENTROID_BIAS));
                    LWJGL.memPutFloat(uvBase + 4L, vertexV + (vertexV < midV ? TEX_CENTROID_BIAS : -TEX_CENTROID_BIAS));
                }
                this.uSum = 0.0f;
                this.vSum = 0.0f;
            }

            if (this.normalOffset >= 0 || this.tangentOffset >= 0) {
                this.quad.setup(ptr, this.stride);
                NormalHelper.computeFaceNormal(this.normal, this.quad);
                if (this.normalOffset >= 0) {
                    writeQuadInt(ptr, this.normalOffset, NormI8.pack(this.normal.x(), this.normal.y(), this.normal.z()));
                }
                if (this.tangentOffset >= 0) {
                    int tangent = NormalHelper.computeTangent(this.normal.x(), this.normal.y(), this.normal.z(), this.quad);
                    writeQuadInt(ptr, this.tangentOffset, tangent);
                }
            }
        }

        return ptr + this.stride;
    }

    /**
     * The first {@value #BASE_STRIDE} bytes of every extended vertex: Celeritas's vanilla-like layout (position,
     * colour, texture, light and draw parameters) without its trailing {@code a_RdhFactor}, where the extended
     * attributes begin. Upstream's {@code VanillaLikeChunkVertex} always writes that trailing int, so this repeats
     * its encoding.
     */
    private static void writeBase(long ptr, Material material, Vertex vertex, int sectionIndex) {
        LWJGL.memPutFloat(ptr, vertex.x);
        LWJGL.memPutFloat(ptr + 4, vertex.y);
        LWJGL.memPutFloat(ptr + 8, vertex.z);
        LWJGL.memPutInt(ptr + 12, vertex.color);
        LWJGL.memPutFloat(ptr + 16, Math.min(0.99999997F, vertex.u));
        LWJGL.memPutFloat(ptr + 20, Math.min(0.99999997F, vertex.v));
        int drawParameters = ((sectionIndex & 0xFF) << 8) | (material.bits() & 0xFF);
        int light = (vertex.light & 0xFF) | (((vertex.light >> 16) & 0xFF) << 8);
        LWJGL.memPutInt(ptr + 24, drawParameters | (light << 16));
    }

    private int getOffset(TerrainVertexFormatRequirements.Attribute attribute, String name) {
        return this.vertexType.requires(attribute) ? this.vertexType.getVertexFormat().getAttribute(name).getPointer() : -1;
    }

    private boolean requiresBlockContext() {
        return this.mcEntityOffset >= 0 || this.midBlockOffset >= 0;
    }

    private boolean needsQuadData() {
        return this.midTexOffset >= 0 || this.normalOffset >= 0 || this.tangentOffset >= 0;
    }

    private void writeQuadInt(long ptr, int offset, int value) {
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            LWJGL.memPutInt(ptr - (long) vertexIndex * this.stride + offset, value);
        }
    }

    private static int resolveBlockStateId(Block block, int metadata) {
        ShaderProvider provider = ShaderProviderHolder.getProvider();
        return provider != null ? provider.getBlockStateId(block, metadata) : Block.getIdFromBlock(block);
    }

    private static int resolveFluidBlockStateId(Block block, int metadata) {
        int blockStateId = resolveBlockStateId(block, metadata);
        if (blockStateId != -1) {
            return blockStateId;
        }

        Block counterpart = liquidCounterpart(block);
        return counterpart != null ? resolveBlockStateId(counterpart, metadata) : -1;
    }

    private static Block liquidCounterpart(Block block) {
        if (block instanceof BlockStaticLiquid) {
            return Block.getBlockById(Block.getIdFromBlock(block) - 1);
        }

        if (block instanceof BlockDynamicLiquid) {
            return Block.getBlockById(Block.getIdFromBlock(block) + 1);
        }

        return null;
    }
}

