package com.demonica.celeritas;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The members of the pinned Celeritas jar that Demonica's quarantined patches bind to (the seam anchors of
 * docs/celeritas/SPIKE.md and LEDGER.md), and the shape of upstream's own mixins that the patches are layered on.
 * When the pin moves, this test says exactly which anchor or upstream mixin changed.
 */
class AnchorInventoryTest {
    static final String CWR = "org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer";
    static final String VRSM = "org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager";
    static final String VRSM_RENDERER = VRSM + "$ChunkRenderer";
    static final String PASS_BUILDER = "org/taumc/celeritas/impl/render/terrain/VintageRenderPassConfigurationBuilder";
    static final String MESHING_TASK = "org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask";
    static final String BUILD_CONTEXT = "org/taumc/celeritas/impl/render/terrain/compile/VintageChunkBuildContext";
    static final String BLOCK_RENDERER = "org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer";
    static final String FOG_SERVICE = "org/taumc/celeritas/impl/render/terrain/fog/GLStateManagerFogService";
    static final String RENDER_GLOBAL_MIXIN = "org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin";
    static final String RSM = "org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager";
    static final String SHADER_RENDERER = "org/embeddedt/embeddium/impl/render/chunk/ShaderChunkRenderer";
    static final String DEFAULT_RENDERER = "org/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer";
    static final String SWR = "org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer";
    static final String TRACKER = "org/embeddedt/embeddium/impl/render/chunk/map/ChunkTracker";
    static final String GL_PROGRAM = "org/embeddedt/embeddium/impl/gl/shader/GlProgram";

    static final String PASS = "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;";
    static final String VIEWPORT = "Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;";
    static final String CAMERA_STATE = "Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;";
    static final String RSM_CTOR_PREFIX = "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;Ljava/util/function/Supplier;"
        + "Ljava/util/function/BiFunction;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;III";
    static final String RSM_CTOR_8 = RSM_CTOR_PREFIX + ")V";
    static final String RSM_CTOR_9 = RSM_CTOR_PREFIX + "Z)V";
    static final String MESHING_EXECUTE = "(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
        + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;";
    static final String BUFFERS = "org/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers";
    static final String MATERIAL = "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;";
    static final String COPY_BLOCK_DATA = "(Ljava/nio/ByteBuffer;L" + BUFFERS + ";" + MATERIAL + ")V";
    static final String RENDER_BLOCK = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
        + "Lorg/taumc/celeritas/impl/world/cloned/CeleritasBlockAccess;Lnet/minecraft/util/BlockRenderLayer;)V";
    static final String ANALYZER = "org/embeddedt/embeddium/impl/render/chunk/compile/pipeline/BakedQuadGroupAnalyzer";
    static final String SLIDER = "org/taumc/celeritas/api/options/control/SliderControl";
    static final String CYCLING = "org/taumc/celeritas/api/options/control/CyclingControl";
    static final String OPTION_PAGES = "org/taumc/celeritas/impl/gui/SodiumGameOptionPages";
    static final String PAGE = "()Lorg/taumc/celeritas/api/options/structure/OptionPage;";
    static final String OPTION_ID = "Lorg/taumc/celeritas/api/options/OptionIdentifier;";
    static final String STANDARD_GROUP = "org/taumc/celeritas/api/options/structure/StandardOptions$Group";
    static final String STANDARD_OPTION = "org/taumc/celeritas/api/options/structure/StandardOptions$Option";
    static final String VIDEO_SCREEN = "org/taumc/celeritas/impl/gui/CeleritasVideoOptionsScreen";

    /** A member the patches bind to. {@code call} anchors additionally require an invocation or field read in the body. */
    record Anchor(String id, String owner, String name, String desc, Kind kind, String callOwner, String callName, String callDesc) {
        enum Kind { METHOD, FIELD, CALL, READ }

        static Anchor method(String id, String owner, String name, String desc) {
            return new Anchor(id, owner, name, desc, Kind.METHOD, null, null, null);
        }

        static Anchor field(String id, String owner, String name, String desc) {
            return new Anchor(id, owner, name, desc, Kind.FIELD, null, null, null);
        }

        static Anchor call(String id, String owner, String name, String desc, String callOwner, String callName, String callDesc) {
            return new Anchor(id, owner, name, desc, Kind.CALL, callOwner, callName, callDesc);
        }

        static Anchor read(String id, String owner, String name, String desc, String fieldOwner, String fieldName, String fieldDesc) {
            return new Anchor(id, owner, name, desc, Kind.READ, fieldOwner, fieldName, fieldDesc);
        }

        @Override
        public String toString() {
            String member = this.owner + "." + this.name + (this.kind == Kind.FIELD ? ":" : "") + this.desc;
            return switch (this.kind) {
                case METHOD, FIELD -> this.id + " " + member;
                case CALL -> this.id + " " + member + " calls " + this.callOwner + "." + this.callName + this.callDesc;
                case READ -> this.id + " " + member + " reads " + this.callOwner + "." + this.callName + ":" + this.callDesc;
            };
        }
    }

    static final List<Anchor> ANCHORS = List.of(
        // S1: hasShadowPass is the 9th argument of the delegate call in the deprecated constructor, which forge122 uses.
        Anchor.call("S1", RSM, "<init>", RSM_CTOR_8, RSM, "<init>", RSM_CTOR_9),
        Anchor.call("S1", VRSM, "<init>", "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"
            + "Lnet/minecraft/client/multiplayer/WorldClient;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V", RSM, "<init>", RSM_CTOR_8),
        // S2: the program hooks every pass goes through.
        Anchor.method("S2", SHADER_RENDERER, "begin", "(" + PASS + ")V"),
        Anchor.method("S2", SHADER_RENDERER, "end", "(" + PASS + ")V"),
        Anchor.field("S2", SHADER_RENDERER, "activeProgram", "L" + GL_PROGRAM + ";"),
        Anchor.call("S2", DEFAULT_RENDERER, "render", "(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"
            + "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;"
            + PASS + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
            DEFAULT_RENDERER, "begin", "(" + PASS + ")V"),
        // S3: the shadow-pass flag.
        Anchor.method("S3", RSM, "isInShadowPass", "()Z"),
        Anchor.method("S3", RSM, "hasShadowPass", "()Z"),
        // S4: fog occlusion.
        Anchor.method("S4", VRSM, "useFogOcclusion", "()Z"),
        // S5, S6: vertex type and matrices.
        Anchor.method("S5", CWR, "chooseVertexType", "()Lorg/embeddedt/embeddium/impl/render/chunk/vertex/format/ChunkVertexType;"),
        Anchor.method("S6", CWR, "createChunkRenderMatrices", "()Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"),
        // S7: the world-renderer adapter.
        Anchor.field("S7", SWR, "currentViewport", VIEWPORT),
        Anchor.method("S7", SWR, "getRenderSectionManager", "()L" + RSM + ";"),
        Anchor.method("S7", SWR, "getLastViewport", "()" + VIEWPORT),
        Anchor.method("S7", SWR, "scheduleTerrainUpdate", "()V"),
        Anchor.method("S7", SWR, "drawChunkLayer", "(Ljava/lang/Object;DDD)V"),
        Anchor.method("S7", SWR, "createChunkRenderMatrices", "()Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"),
        Anchor.method("S7", RSM, "renderLayer", "(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;" + PASS
            + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V"),
        // S8: upstream's renderBlockLayer overwrite and the terrain draw inside it.
        Anchor.call("S8", RENDER_GLOBAL_MIXIN, "renderBlockLayer", "(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            CWR, "drawChunkLayer", "(Lnet/minecraft/util/BlockRenderLayer;DDD)V"),
        Anchor.call("S8", RENDER_GLOBAL_MIXIN, "renderBlockLayer", "(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            "net/minecraft/client/renderer/EntityRenderer", "enableLightmap", "()V"),
        // S9: the block-entity phase, by its full descriptor (not the erased bridge).
        Anchor.method("S9", CWR, "renderBlockEntities", "(L" + CWR + "$TileEntityRenderContext;)I"),
        // S10: the meshing loop's layer test and vanilla fallback, and the build's pass configuration.
        Anchor.field("S10", "org/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext", "buffers", "L" + BUFFERS + ";"),
        Anchor.call("S10", MESHING_TASK, "execute", MESHING_EXECUTE,
            "net/minecraft/block/Block", "canRenderInLayer", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z"),
        Anchor.call("S10", MESHING_TASK, "execute", MESHING_EXECUTE, "net/minecraft/client/renderer/BlockRendererDispatcher", "renderBlock",
            "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;"
                + "Lnet/minecraft/client/renderer/BufferBuilder;)Z"),
        // S11: vanilla-path quads become Celeritas vertices here, one push per quad.
        Anchor.call("S11", BUILD_CONTEXT, "convertVanillaDataToCeleritasData", "(L" + BUFFERS + ";)V", BUILD_CONTEXT, "copyBlockData", COPY_BLOCK_DATA),
        Anchor.call("S11", BUILD_CONTEXT, "copyBlockData", COPY_BLOCK_DATA, BUILD_CONTEXT, "selectMaterial",
            "(" + MATERIAL + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)" + MATERIAL),
        Anchor.call("S11", BUILD_CONTEXT, "copyBlockData", COPY_BLOCK_DATA, "org/embeddedt/embeddium/impl/render/chunk/vertex/builder/ChunkMeshBufferBuilder",
            "push", "([Lorg/embeddedt/embeddium/impl/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;" + MATERIAL + ")V"),
        // S13: the fast block renderer, its switch, and what it asks of the pass configuration and the light pipeline.
        Anchor.field("S13", MESHING_TASK, "USE_NEW_BLOCK_RENDERER", "Z"),
        Anchor.read("S13", MESHING_TASK, "execute", MESHING_EXECUTE, MESHING_TASK, "USE_NEW_BLOCK_RENDERER", "Z"),
        Anchor.call("S13", MESHING_TASK, "execute", MESHING_EXECUTE, BLOCK_RENDERER, "renderBlock", RENDER_BLOCK),
        Anchor.field("S13", BLOCK_RENDERER, "context", "L" + BUILD_CONTEXT + ";"),
        Anchor.call("S13", BLOCK_RENDERER, "renderBlock", RENDER_BLOCK, ANALYZER, "setDefaultRenderingFlags", "(I)V"),
        Anchor.call("S13", BLOCK_RENDERER, "renderBlock", RENDER_BLOCK, "org/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration",
            "getMaterialForRenderType", "(Ljava/lang/Object;)" + MATERIAL),
        Anchor.call("S13", BLOCK_RENDERER, "renderQuadList", null, ANALYZER, "chooseOptimalMaterial",
            "(I" + MATERIAL + "Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;)"
                + MATERIAL),
        Anchor.call("S13", BLOCK_RENDERER, "renderQuadList", null, BLOCK_RENDERER, "writeGeometry",
            "(IIILorg/embeddedt/embeddium/impl/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/minecraft/util/math/Vec3d;" + MATERIAL
                + "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;[ILorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;"
                + "Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadOrientation;)V"),
        Anchor.call("S13", BLOCK_RENDERER, "getVertexLight", null, "org/embeddedt/embeddium/impl/model/light/LightPipeline", "calculate",
            "(Lorg/embeddedt/embeddium/impl/model/quad/ModelQuadView;IIILorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;"
                + "Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;ZZ)V"),
        Anchor.read("S13", BLOCK_RENDERER, "writeGeometry", null, "org/embeddedt/embeddium/impl/render/chunk/ChunkColorWriter", "EMBEDDIUM",
            "Lorg/embeddedt/embeddium/impl/render/chunk/ChunkColorWriter;"),
        // S14: the pass configuration.
        Anchor.method("S14", PASS_BUILDER, "build", "(Lorg/embeddedt/embeddium/impl/render/chunk/vertex/format/ChunkVertexType;)"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;"),
        // S15: upstream's fog service, which reads vanilla's stale GlStateManager.fogState.
        Anchor.method("S15", FOG_SERVICE, "getFogEnd", "()F"),
        Anchor.method("S15", FOG_SERVICE, "getFogStart", "()F"),
        Anchor.method("S15", FOG_SERVICE, "getFogDensity", "()F"),
        Anchor.method("S15", FOG_SERVICE, "getFogCutoff", "()F"),
        Anchor.method("S15", FOG_SERVICE, "getFogColor", "()[F"),
        Anchor.method("S15", FOG_SERVICE, "getFogMode", "()Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderComponent$Factory;"),
        // Face culling during the shadow pass.
        Anchor.call("S16", DEFAULT_RENDERER, "render", "(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;"
            + "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;"
            + PASS + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
            DEFAULT_RENDERER, "useBlockFaceCulling", "()Z"),
        // S17: every linked program is registered with Iris for as long as it lives.
        Anchor.method("S17", GL_PROGRAM, "<init>", "(ILjava/util/function/Function;)V"),
        Anchor.method("S17", GL_PROGRAM, "destroyInternal", "()V"),
        // S18: shadow block entities come from the public iterator.
        Anchor.method("S18", SWR, "forEachVisibleBlockEntity", "(Ljava/util/function/Consumer;)V"),
        // S19: DH's neighbour radius.
        Anchor.field("S19", TRACKER, "requiredNeighborRadius", "I"),
        // C1: mods that hook vanilla's chunk rebuild. The whole build (Component Model Hider's build gate), the layer
        // test (its hidden positions, shared with S10) and the conversion of the vanilla buffers (LittleTiles).
        Anchor.method("C1", MESHING_TASK, "execute", MESHING_EXECUTE),
        Anchor.call("C1", MESHING_TASK, "execute", MESHING_EXECUTE,
            "net/minecraft/block/Block", "canRenderInLayer", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z"),
        Anchor.call("C1", MESHING_TASK, "execute", MESHING_EXECUTE, BUILD_CONTEXT, "convertVanillaDataToCeleritasData", "(L" + BUFFERS + ";)V"),
        Anchor.method("C1", BUILD_CONTEXT, "getWorldSlice", "()Lorg/taumc/celeritas/impl/world/WorldSlice;"),
        Anchor.method("C1", BUILD_CONTEXT, "getOffX", "()I"),
        Anchor.method("C1", SWR, "scheduleRebuildForChunk", "(IIIZ)V"),
        // S20: the fast renderer's block quads, sided and unassigned.
        Anchor.call("S20", BLOCK_RENDERER, "renderBlock", RENDER_BLOCK, "net/minecraft/client/renderer/block/model/IBakedModel", "getQuads",
            "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;"),
        Anchor.call("S20", BLOCK_RENDERER, "renderBlock", RENDER_BLOCK, "net/minecraft/block/state/IBlockState", "shouldSideBeRendered",
            "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"),
        // O1: what Reese's Sodium Options reads from the option controls.
        Anchor.field("O1", SLIDER, "min", "I"),
        Anchor.field("O1", SLIDER, "max", "I"),
        Anchor.field("O1", SLIDER, "interval", "I"),
        Anchor.field("O1", SLIDER, "mode", "Lorg/taumc/celeritas/api/options/control/ControlValueFormatter;"),
        Anchor.field("O1", CYCLING, "allowedValues", "[Ljava/lang/Object;"),
        // Not patches: the video settings that DemonicaOptionPages extends and OptionsScreens replaces. Video Settings
        // builds Celeritas's screen, and its pages hold the groups and options Demonica's settings are placed by.
        Anchor.call("options", "org/taumc/celeritas/mixin/features/options/MixinGuiOptions", "open",
            "(Lnet/minecraft/client/gui/GuiButton;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
            VIDEO_SCREEN, "<init>", "(Lnet/minecraft/client/gui/GuiScreen;)V"),
        Anchor.read("options", OPTION_PAGES, "general", PAGE, STANDARD_GROUP, "WINDOW", OPTION_ID),
        Anchor.read("options", OPTION_PAGES, "general", PAGE, STANDARD_OPTION, "FULLSCREEN", OPTION_ID),
        Anchor.read("options", OPTION_PAGES, "general", PAGE, STANDARD_OPTION, "MAX_FRAMERATE", OPTION_ID),
        Anchor.read("options", OPTION_PAGES, "quality", PAGE, STANDARD_GROUP, "SORTING", OPTION_ID),
        Anchor.read("options", OPTION_PAGES, "advanced", PAGE, STANDARD_GROUP, "CPU_SAVING", OPTION_ID),
        // I1: the async-occlusion clamp.
        Anchor.method("I1", VRSM, "getAsyncOcclusionMode", "()Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/AsyncOcclusionMode;"),
        // I2: the frame stamps of both searches.
        Anchor.method("I2", SWR, "setupTerrain", "(" + VIEWPORT + CAMERA_STATE + "IZZ)V"),
        Anchor.method("I2", SWR, "setupShadowTerrain", "(" + VIEWPORT + VIEWPORT + CAMERA_STATE + "IZ)V")
    );

    /** What upstream's mixins @Overwrite, in MCP names. A quarantine patch must never overwrite these again. */
    static final Set<String> UPSTREAM_OVERWRITES = Set.of(
        "net/minecraft/client/renderer/RenderGlobal#getDebugInfoRenders()Ljava/lang/String;",
        "net/minecraft/client/renderer/RenderGlobal#getRenderedChunks()I",
        "net/minecraft/client/renderer/RenderGlobal#hasNoChunkUpdates()Z",
        "net/minecraft/client/renderer/RenderGlobal#markBlocksForUpdate(IIIIIIZ)V",
        "net/minecraft/client/renderer/RenderGlobal#renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
        "net/minecraft/client/renderer/RenderGlobal#setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
        "net/minecraft/client/renderer/culling/Frustum#isBoxInFrustum(DDDDDD)Z",
        "net/minecraft/client/renderer/texture/TextureUtil#blendColors(IIIIZ)I",
        "net/minecraft/util/EnumFacing#getFacingFromVector(FFF)Lnet/minecraft/util/EnumFacing;",
        "net/minecraft/world/biome/BiomeColorHelper#getColorAtPos(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/world/biome/BiomeColorHelper$ColorResolver;)I"
    );

    /** Upstream's non-default mixin priorities by target. Every other upstream mixin runs at the default, 1000. */
    static final Map<String, Integer> UPSTREAM_PRIORITIES = Map.of(
        "net/minecraft/client/renderer/RenderGlobal", 1000,
        "net/minecraft/world/biome/BiomeColorHelper", 1200,
        "net/minecraft/client/renderer/texture/TextureUtil", 900
    );

    @Test
    void everySeamAnchorExists() {
        CeleritasJar jar = CeleritasJar.get();
        List<String> missing = new ArrayList<>();
        for (Anchor anchor : ANCHORS) {
            String problem = check(jar, anchor);
            if (problem != null) {
                missing.add(anchor + ": " + problem);
            }
        }
        assertTrue(missing.isEmpty(), "seam anchors missing from " + jar.file().getName() + ":\n  " + String.join("\n  ", missing));
    }

    @Test
    void anchorMethodsAreNotFinal() {
        CeleritasJar jar = CeleritasJar.get();
        List<String> finals = new ArrayList<>();
        for (Anchor anchor : ANCHORS) {
            if (anchor.kind() == Anchor.Kind.FIELD || anchor.name().equals("<init>") || anchor.desc() == null || !jar.contains(anchor.owner())) {
                continue;
            }
            MethodNode method = method(jar.node(anchor.owner()), anchor.name(), anchor.desc());
            if (method != null && (method.access & Opcodes.ACC_FINAL) != 0) {
                finals.add(anchor.toString());
            }
        }
        assertTrue(finals.isEmpty(), "anchor methods became final (a soft override or an overwrite would break):\n  " + String.join("\n  ", finals));
    }

    @Test
    void forge122DoesNotOverrideTheSeamHooks() {
        CeleritasJar jar = CeleritasJar.get();
        ClassNode renderer = jar.node(VRSM_RENDERER);
        // S2: the patch lives on ShaderChunkRenderer; an override in forge122's renderer would bypass it.
        assertTrue(method(renderer, "begin", "(" + PASS + ")V") == null, VRSM_RENDERER + " now overrides begin");
        assertTrue(method(renderer, "end", "(" + PASS + ")V") == null, VRSM_RENDERER + " now overrides end");
        // S3: Demonica adds the override; upstream adding its own would make the patch a duplicate.
        assertTrue(method(jar.node(VRSM), "isInShadowPass", "()Z") == null, VRSM + " now overrides isInShadowPass");
    }

    @Test
    void upstreamOverwritesAreAsRecorded() throws IOException {
        Set<String> overwrites = new TreeSet<>();
        for (UpstreamMixinInventory.MixinClass mixin : UpstreamMixinInventory.read(CeleritasJar.get())) {
            for (String target : mixin.targets()) {
                mixin.overwrites().forEach(o -> overwrites.add(target + "#" + o));
            }
        }
        assertEquals(new TreeSet<>(UPSTREAM_OVERWRITES), overwrites, "upstream's set of @Overwrite members changed");
    }

    @Test
    void upstreamPrioritiesAreAsRecorded() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (UpstreamMixinInventory.MixinClass mixin : UpstreamMixinInventory.read(CeleritasJar.get())) {
            for (String target : mixin.targets()) {
                int expected = UPSTREAM_PRIORITIES.getOrDefault(target, UpstreamMixinInventory.DEFAULT_PRIORITY);
                if (mixin.priority() != expected) {
                    wrong.add(mixin.name() + " -> " + target + ": priority " + mixin.priority() + ", expected " + expected);
                }
            }
        }
        assertTrue(wrong.isEmpty(), "upstream mixin priorities changed:\n  " + String.join("\n  ", wrong));
    }

    /**
     * The full inventory of upstream's 27 mixins (targets, priorities, overwrites, injection points), compared with
     * the checked-in snapshot. A new pin that changes any of it fails here with the new inventory written next to
     * the build output, so the change can be reviewed and the snapshot updated deliberately.
     */
    @Test
    void upstreamMixinInventoryMatchesSnapshot() throws IOException {
        List<UpstreamMixinInventory.MixinClass> mixins = UpstreamMixinInventory.read(CeleritasJar.get());
        assertEquals(27, mixins.size(), "upstream's mixin count changed");

        List<String> actual = UpstreamMixinInventory.render(mixins);
        List<String> expected;
        try (InputStream in = AnchorInventoryTest.class.getResourceAsStream("upstream-mixin-inventory.txt")) {
            expected = in == null ? List.of() : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).collect(Collectors.toList());
        }
        if (!actual.equals(expected)) {
            Path out = Path.of("upstream-mixin-inventory.actual.txt").toAbsolutePath();
            Files.write(out, actual, StandardCharsets.UTF_8);
            assertEquals(String.join("\n", expected), String.join("\n", actual),
                "upstream mixin inventory differs from src/test/resources/com/demonica/celeritas/upstream-mixin-inventory.txt; actual written to " + out);
        }
    }

    @Test
    void upstreamFogServiceIsTheOnlyRegisteredOne() throws IOException {
        try (JarFile file = new JarFile(CeleritasJar.get().file())) {
            var entry = file.getEntry("META-INF/services/org.embeddedt.embeddium.impl.render.chunk.fog.FogService");
            assertTrue(entry != null, "Celeritas no longer registers a FogService");
            String services = new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8).trim();
            // S15 patches this class in place, so it must stay the one ServiceLoader.findFirst() picks.
            assertEquals(FOG_SERVICE.replace('/', '.'), services);
        }
    }

    private static String check(CeleritasJar jar, Anchor anchor) {
        if (!jar.contains(anchor.owner())) {
            return "class missing";
        }
        ClassNode owner = jar.node(anchor.owner());
        if (anchor.kind() == Anchor.Kind.FIELD) {
            for (FieldNode field : owner.fields) {
                if (field.name.equals(anchor.name()) && field.desc.equals(anchor.desc())) {
                    // S13 wraps the read of the fast-renderer flag, which a final flag could turn into a constant.
                    return (field.access & Opcodes.ACC_FINAL) != 0 && field.name.equals("USE_NEW_BLOCK_RENDERER") ? "field is final" : null;
                }
            }
            return "field missing";
        }
        MethodNode method = method(owner, anchor.name(), anchor.desc());
        if (method == null) {
            return "method missing";
        }
        if (anchor.kind() == Anchor.Kind.METHOD) {
            return null;
        }
        for (AbstractInsnNode insn : method.instructions) {
            if (anchor.kind() == Anchor.Kind.CALL && insn instanceof MethodInsnNode call
                && call.owner.equals(anchor.callOwner()) && call.name.equals(anchor.callName()) && call.desc.equals(anchor.callDesc())) {
                return null;
            }
            if (anchor.kind() == Anchor.Kind.READ && insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETSTATIC
                && read.owner.equals(anchor.callOwner()) && read.name.equals(anchor.callName()) && read.desc.equals(anchor.callDesc())) {
                return null;
            }
        }
        return anchor.kind() == Anchor.Kind.CALL ? "call missing from the body" : "read missing from the body";
    }

    /** Finds a method by name and descriptor; a null descriptor matches the only method of that name. */
    static MethodNode method(ClassNode owner, String name, String desc) {
        List<MethodNode> byName = owner.methods.stream().filter(m -> m.name.equals(name)).toList();
        if (desc == null) {
            return byName.size() == 1 ? byName.get(0) : null;
        }
        return byName.stream().filter(m -> Objects.equals(m.desc, desc)).findFirst().orElse(null);
    }
}
