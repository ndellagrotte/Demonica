package net.coderbot.iris.celeritas;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestFunction;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shaderpack.ProgramFallbackResolver;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.coderbot.iris.celeritas.vertices.TerrainVertexFormatRequirements;
import com.demonica.celeritas.api.shader.ShaderProvider;
import com.demonica.celeritas.api.shader.ShaderProviderHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntFunction;

public class CeleritasTerrainPipeline {
    // Demonica: the order the passes' transforms are submitted and their results read in: gbuffers before shadow, as
    // the four per-source futures this replaced were, so a pack whose passes share sources does the same work in the
    // same order as before the pass split.
    private static final IrisTerrainPass[] TRANSFORM_ORDER = {
            IrisTerrainPass.GBUFFER_SOLID, IrisTerrainPass.GBUFFER_CUTOUT, IrisTerrainPass.GBUFFER_TRANSLUCENT,
            IrisTerrainPass.SHADOW, IrisTerrainPass.SHADOW_CUTOUT, IrisTerrainPass.SHADOW_TRANSLUCENT
    };

    private final EnumMap<IrisTerrainPass, PassInfo> passInfoMap = new EnumMap<>(IrisTerrainPass.class);

    @Getter
    private final CustomUniforms customUniforms;

    @Getter
    private final TerrainVertexFormatRequirements vertexFormatRequirements;

    private final IntFunction<ProgramSamplers> createTerrainSamplers;
    private final IntFunction<ProgramSamplers> createShadowSamplers;

    private final IntFunction<ProgramImages> createTerrainImages;
    private final IntFunction<ProgramImages> createShadowImages;

    @Getter
    @Accessors(fluent = true)
    public static final class PassInfo {
        private final EnumMap<PatchShaderType, Optional<String>> sources = new EnumMap<>(PatchShaderType.class);
        private GlFramebuffer framebuffer;
        private AlphaTestOverride alphaTestOverride;
        private BlendModeOverride blendModeOverride;
        private List<BufferBlendOverride> bufferBlendOverrides;
        private float alphaReference;

        private PassInfo() {
            for (PatchShaderType type : PatchShaderType.VALUES) {
                sources.put(type, Optional.empty());
            }
        }

        private void setSource(PatchShaderType type, @Nullable String source) {
            sources.put(type, Optional.ofNullable(source));
        }
    }

    public CeleritasTerrainPipeline(
            IntFunction<ProgramSamplers> createTerrainSamplers,
            IntFunction<ProgramSamplers> createShadowSamplers,
            IntFunction<ProgramImages> createTerrainImages,
            IntFunction<ProgramImages> createShadowImages,
            CustomUniforms customUniforms,
            Map<IrisTerrainPass, Optional<ProgramSource>> passSources,
            Map<IrisTerrainPass, CompletableFuture<Map<PatchShaderType, String>>> passTransformFutures,
            RenderTargets renderTargets,
            ImmutableSet<Integer> flippedAfterPrepare,
            ImmutableSet<Integer> flippedAfterTranslucent,
            @Nullable GlFramebuffer shadowFramebuffer
    ) {
        this.customUniforms = customUniforms;
        this.createTerrainSamplers = createTerrainSamplers;
        this.createShadowSamplers = createShadowSamplers;
        this.createTerrainImages = createTerrainImages;
        this.createShadowImages = createShadowImages;

        // Each pass's source is already resolved through the fallback chain (resolveSources)
        final EnumMap<IrisTerrainPass, Optional<ProgramSource>> gbufferProgramSource = new EnumMap<>(IrisTerrainPass.class);
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            gbufferProgramSource.put(pass, passSources.getOrDefault(pass, Optional.empty()));
        }

        // Initialize PassInfo, framebuffers, blend modes, and alpha in single pass
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            PassInfo passInfo = new PassInfo();
            passInfoMap.put(pass, passInfo);

            // Set up framebuffer, blend mode, and buffer blend overrides
            // Demonica: the shadow passes keep taking their blend from the id's default and not from the source's
            // directives (upstream SodiumPrograms uses the directive). ShadowSolid and ShadowCutout have no default
            // here, as Shadow had, so the split leaves shadow terrain blending unchanged.
            final ProgramId programId = pass.getProgramId();

            if (pass.isShadow()) {
                passInfo.framebuffer = shadowFramebuffer;
                passInfo.blendModeOverride = programId.getBlendModeOverride();
                passInfo.bufferBlendOverrides = Collections.emptyList();
            } else if (renderTargets != null) {
                ImmutableSet<Integer> flipped = pass == IrisTerrainPass.GBUFFER_TRANSLUCENT ? flippedAfterTranslucent : flippedAfterPrepare;

                Optional<ProgramSource> programSource = gbufferProgramSource.get(pass);
                programSource.ifPresentOrElse(source -> {
                    passInfo.framebuffer = renderTargets.createGbufferFramebuffer(flipped, source.getDirectives().getDrawBuffers());
                    passInfo.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());

                    passInfo.bufferBlendOverrides = new ArrayList<>();
                    source.getDirectives().getBufferBlendOverrides().forEach(information -> {
                        int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.getIndex());
                        if (index > -1) {
                            passInfo.bufferBlendOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
                        }
                    });
                }, () -> {
                    passInfo.framebuffer = renderTargets.createGbufferFramebuffer(flipped, new int[] {0});
                    passInfo.blendModeOverride = programId.getBlendModeOverride();
                    passInfo.bufferBlendOverrides = Collections.emptyList();
                });
            } else {
                passInfo.blendModeOverride = programId.getBlendModeOverride();
                passInfo.bufferBlendOverrides = Collections.emptyList();
            }

            // Set alpha reference. The shader pack directive wins; otherwise match Iris/Sodium defaults
            // for terrain passes so translucent water does not inherit a stale vanilla alpha test.
            // Demonica: upstream's Sodium terrain path (SodiumPrograms.getAlphaTest) tests both cutout passes at
            // HALF_ALPHA (0.5), TRANSLUCENT at NON_ZERO_ALPHA and SHADOW_TRANS not at all; these 0.1/0.0001 defaults
            // come from Angelica and are kept so the pass split changes no pack's output.
            passInfo.alphaReference = switch (pass) {
                case GBUFFER_CUTOUT, SHADOW_CUTOUT -> 0.1f;
                case GBUFFER_TRANSLUCENT, SHADOW_TRANSLUCENT -> 0.0001f;
                default -> 0.0f;
            };
            passInfo.alphaTestOverride = resolveAlphaTestOverride(
                    gbufferProgramSource.get(pass).flatMap(source -> source.getDirectives().getAlphaTestOverride()),
                    pass);
        }

        // Process and apply shader sources: once per distinct transform (submitTransforms gives passes that share a
        // source the same future), applied to every pass that shares it
        final Map<CompletableFuture<Map<PatchShaderType, String>>, List<IrisTerrainPass>> passesByFuture = new LinkedHashMap<>();
        for (IrisTerrainPass pass : TRANSFORM_ORDER) {
            final CompletableFuture<Map<PatchShaderType, String>> future = passTransformFutures.get(pass);
            if (future != null && gbufferProgramSource.get(pass).isPresent()) {
                passesByFuture.computeIfAbsent(future, f -> new ArrayList<>()).add(pass);
            }
        }
        List<String> transformedVertexSources = new ArrayList<>();
        passesByFuture.forEach((future, passes) -> processShaderFuture(future, gbufferProgramSource.get(passes.get(0)),
                transformedVertexSources, passes.stream().map(passInfoMap::get).toArray(PassInfo[]::new)));
        this.vertexFormatRequirements = TerrainVertexFormatRequirements.analyze(transformedVertexSources);
        updateVertexFormatRequirements(this.vertexFormatRequirements);
    }

    /**
     * Each pass's program source, resolved through the fallback chain from {@link IrisTerrainPass#getProgramId()}.
     */
    public static EnumMap<IrisTerrainPass, Optional<ProgramSource>> resolveSources(ProgramFallbackResolver resolver) {
        final EnumMap<IrisTerrainPass, Optional<ProgramSource>> sources = new EnumMap<>(IrisTerrainPass.class);
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            sources.put(pass, resolver.resolve(pass.getProgramId()));
        }
        return sources;
    }

    /**
     * Submits one transform per distinct source and gives every pass that resolves to it the same future.
     * Demonica: upstream transforms each pass on its own, in turn (SodiumPrograms.transformShaders, whose transform
     * also takes the pass's alpha test and shadow flag). The CELERITAS_TERRAIN transform takes no per-pass input, so
     * passes that share a source share its output; the transforms here run concurrently, where two submissions of one
     * source would both miss TransformPatcher's cache; and every corpus pack resolves the solid and cutout passes
     * (gbuffer and shadow alike) to one source.
     */
    public static <F> EnumMap<IrisTerrainPass, F> submitTransforms(Map<IrisTerrainPass, Optional<ProgramSource>> sources,
                                                                   Function<ProgramSource, F> submit) {
        final Map<ProgramSource, F> bySource = new IdentityHashMap<>();
        final EnumMap<IrisTerrainPass, F> futures = new EnumMap<>(IrisTerrainPass.class);
        for (IrisTerrainPass pass : TRANSFORM_ORDER) {
            sources.getOrDefault(pass, Optional.empty())
                    .ifPresent(source -> futures.put(pass, bySource.computeIfAbsent(source, submit)));
        }
        return futures;
    }

    private void processShaderFuture(@Nullable CompletableFuture<Map<PatchShaderType, String>> future, Optional<ProgramSource> source,
                                     List<String> transformedVertexSources, PassInfo... targets) {
        if (future == null || source.isEmpty()) {
            return;
        }
        final String sourceName = source.get().getName();
        try {
            final Map<PatchShaderType, String> result = future.join();
            final String vertexSource = result.get(PatchShaderType.VERTEX);
            transformedVertexSources.add(vertexSource == null ? "" : vertexSource);
            for (PassInfo target : targets) {
                target.setSource(PatchShaderType.VERTEX, vertexSource);
                target.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                target.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));
            }
            PatchedShaderPrinter.debugPatchedShaders(sourceName + "_celeritas",
                result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
        } catch (Exception e) {
            Iris.logger.error("Failed to transform shader for Celeritas: {}", sourceName, e);
            throw new RuntimeException("Shader transformation failed for " + sourceName, e);
        }
    }

    private static void updateVertexFormatRequirements(TerrainVertexFormatRequirements requirements) {
        ShaderProvider provider = ShaderProviderHolder.getProvider();
        if (provider instanceof IrisCeleritasShaderProvider celeritasProvider) {
            celeritasProvider.setVertexFormatRequirements(requirements);
        }
    }

    public PassInfo getPassInfo(IrisTerrainPass pass) {
        return passInfoMap.get(pass);
    }

    public ProgramUniforms.Builder initUniforms(int programId) {
        final ProgramUniforms.Builder uniforms = ProgramUniforms.builder("<celeritas shaders>", programId);

        CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
        customUniforms.assignTo(uniforms);

        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
        return uniforms;
    }

    public boolean hasShadowPass() {
        return createShadowSamplers != null;
    }

    public ProgramSamplers initTerrainSamplers(int programId) {
        return createTerrainSamplers.apply(programId);
    }

    public ProgramSamplers initShadowSamplers(int programId) {
        return createShadowSamplers.apply(programId);
    }

    public ProgramImages initTerrainImages(int programId) {
        return createTerrainImages.apply(programId);
    }

    public ProgramImages initShadowImages(int programId) {
        return createShadowImages.apply(programId);
    }

    static AlphaTestOverride resolveAlphaTestOverride(Optional<AlphaTestOverride> shaderPackOverride, IrisTerrainPass pass) {
        return shaderPackOverride.orElseGet(() -> switch (pass) {
            case GBUFFER_CUTOUT, SHADOW_CUTOUT -> new AlphaTestOverride(new AlphaTest(AlphaTestFunction.GREATER, 0.1f));
            case GBUFFER_TRANSLUCENT, SHADOW_TRANSLUCENT -> new AlphaTestOverride(new AlphaTest(AlphaTestFunction.GREATER, 0.0001f));
            default -> AlphaTestOverride.OFF;
        });
    }
}
