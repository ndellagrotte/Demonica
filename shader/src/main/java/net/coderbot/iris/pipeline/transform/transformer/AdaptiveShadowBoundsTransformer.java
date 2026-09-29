package net.coderbot.iris.pipeline.transform.transformer;

import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebug;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds an early return around the two PCF helper shapes used by common shader packs, on {@link ShaderAst}: a bounds
 * guard at the top of {@code texture2DShadow2x2(sampler2D, vec3 shadowPos)} (returns {@code 1.0}) and
 * {@code SampleFilteredShadow(vec3 shadowPos, float, float)} (returns {@code vec3(1.0)}) that skips the samples for a
 * shadow coordinate outside the shadow map; with the runtime stats on ({@link AdaptiveShadowBoundsStats}), counters in
 * an SSBO for the calls, the rejections and the samples saved.
 *
 * <p>Ported from the TauMC engine's {@code net.coderbot.iris.pipeline.transform.AdaptiveShadowBoundsTransformer} (Step 7
 * of docs/glsl-transformer_adoption/ADOPTION_PLAN.md). The candidate detection (name, return type, parameter types and
 * names, the {@code shadowMapResolution} declaration, an existing guard) and the patched source are the same string
 * logic; the function list comes from {@link ShaderAst#functions()} instead of a parse-tree walk, and the definition is
 * replaced with {@link ShaderAst#replaceFunctionDefinition(String, String)}, which keeps its position. Functions with
 * an unknown name, shape or body are left untouched.</p>
 *
 * <p>The source the guard is inserted into is the definition as glsl-transformer prints it
 * ({@link ShaderAst#source}), where TauMC's was its token-spaced print; the guard goes after the first opening brace
 * in both, which is the body's (a prototype has none). The body searches read {@link ShaderAst.FunctionInfo#bodyText()},
 * TauMC's {@code getText()} form; {@code ShaderAstParityTest.boundsNeedles} checks that they answer as TauMC's did.</p>
 */
public final class AdaptiveShadowBoundsTransformer {
    /** The helper names the transformer recognizes; nothing else is rewritten. */
    public static final List<String> PCF_FUNCTION_NAMES = List.of(
        "texture2DShadow2x2",
        "SampleFilteredShadow"
    );

    private AdaptiveShadowBoundsTransformer() {
    }

    /** Rewrites the fragment shader {@code ast} with the active pipeline's instrumentation state. */
    public static void transform(ShaderAst ast, ShaderType shaderType) {
        final boolean instrumentationEnabled = AdaptiveShadowBoundsStats.isInstrumentationEnabled();
        final int instrumentationBinding = instrumentationEnabled ? AdaptiveShadowBoundsStats.getActiveBinding() : -1;
        transform(ast, shaderType, instrumentationEnabled, instrumentationBinding);
    }

    /**
     * Whether a source may get the runtime-stats block, a text pre-check the orchestrators run before parsing: with
     * the instrumentation on, such a fragment shader's version is hoisted for {@code std430}
     * ({@link AdaptiveShadowBoundsStats#shaderVersionMarker()}). Engine-neutral; both engines call it.
     */
    public static boolean mayInjectRuntimeStats(String source) {
        return source.contains("shadowMapResolution")
            && (source.contains("texture2DShadow2x2") || source.contains("SampleFilteredShadow"));
    }

    /**
     * Rewrites every recognized PCF helper of a fragment shader that declares {@code shadowMapResolution}; a vertex
     * or other stage, or a shader without that declaration, is left alone. With {@code instrumentationEnabled}, the
     * guard counts into the SSBO block at {@code instrumentationBinding}, which is declared once if any helper was
     * rewritten.
     *
     * @throws IllegalStateException if replacing a helper does not replace exactly one definition
     */
    public static void transform(ShaderAst ast, ShaderType shaderType, boolean instrumentationEnabled, int instrumentationBinding) {
        if (shaderType != ShaderType.FRAGMENT || !ast.hasVariable("shadowMapResolution")) {
            return;
        }

        final List<FunctionCandidate> candidates = new ArrayList<>();
        for (ShaderAst.FunctionInfo function : ast.functions()) {
            if (!PCF_FUNCTION_NAMES.contains(function.name())) {
                continue;
            }
            final FunctionCandidate candidate = FunctionCandidate.from(function);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        int injected = 0;
        int alreadyGuarded = 0;
        int unsupported = 0;
        for (FunctionCandidate candidate : candidates) {
            if (candidate.hasBoundsGuard()) {
                alreadyGuarded++;
                continue;
            }
            if (!candidate.isSupportedPcfBody()) {
                unsupported++;
                continue;
            }

            final String patched = candidate.patchedSource(instrumentationEnabled);
            final int replaced = ast.replaceFunctionDefinition(candidate.name(), patched);
            if (replaced != 1) {
                throw new IllegalStateException("[AdaptiveShadowBounds] replacing " + candidate.name() + " replaced "
                    + replaced + " definitions, expected 1: " + patched);
            }
            injected++;
            debug(candidate.name());
        }

        if (injected > 0 && instrumentationEnabled) {
            ast.injectVariable(AdaptiveShadowBoundsStats.declarationForBinding(instrumentationBinding));
        }

        if (GLSMPerfDebug.isEnabled()) {
            Iris.logger.info(
                "[AdaptiveShadowBounds] inspected={} injected={} alreadyGuarded={} unsupported={}",
                candidates.size(), injected, alreadyGuarded, unsupported
            );
        }
    }

    private static void debug(String functionName) {
        if (GLSMPerfDebug.isEnabled()) {
            Iris.logger.info("[AdaptiveShadowBounds] injected function={}", functionName);
        }
    }

    private record Parameter(String name, String type) {
    }

    private record FunctionCandidate(
        String source,
        String name,
        String returnType,
        List<Parameter> parameters,
        String body
    ) {
        /**
         * The candidate for a helper definition, or null where TauMC's walk skipped it: a definition without
         * parameters ({@code f()}; TauMC's grammar has no {@code function_parameters} there) or with a parameter that
         * has no name or no type ({@code f(void)}, {@code f(vec3)}).
         */
        private static FunctionCandidate from(ShaderAst.FunctionInfo function) {
            if (function.parameters().isEmpty()) {
                return null;
            }

            final List<Parameter> parameters = new ArrayList<>();
            for (ShaderAst.Parameter parameter : function.parameters()) {
                final String type = parameter.type() == null ? "" : parameter.type();
                if (parameter.name() == null || type.isEmpty()) {
                    return null;
                }
                parameters.add(new Parameter(parameter.name(), type));
            }

            return new FunctionCandidate(
                ShaderAst.source(function.node()),
                function.name(),
                function.returnType(),
                List.copyOf(parameters),
                function.bodyText()
            );
        }

        private String patchedSource(boolean instrumentationEnabled) {
            final int bodyStart = source.indexOf('{');
            if (bodyStart < 0) {
                throw new IllegalStateException("PCF helper has no function body: " + name);
            }
            final String coordinate = coordinateName();
            final String defaultValue = "float".equals(returnType) ? "1.0" : "vec3(1.0)";
            final String margin = "1.5 / shadowMapResolution";
            final String bounds = coordinate + ".x > " + margin
                + " && " + coordinate + ".x < 1.0 - " + margin
                + " && " + coordinate + ".y > " + margin
                + " && " + coordinate + ".y < 1.0 - " + margin
                + " && " + coordinate + ".z > 0.0"
                + " && " + coordinate + ".z < 1.0";
            if (!instrumentationEnabled) {
                final String guard = "if (!(" + bounds + ")) return " + defaultValue + ";";
                return source.substring(0, bodyStart + 1) + guard + source.substring(bodyStart + 1);
            }

            final String guard = "if (!(" + bounds + ")) {"
                + AdaptiveShadowBoundsStats.rejectedCounter(name)
                + "return " + defaultValue + ";}";
            return source.substring(0, bodyStart + 1)
                + AdaptiveShadowBoundsStats.callCounter(name)
                + guard
                + source.substring(bodyStart + 1);
        }

        private boolean hasBoundsGuard() {
            if (!hasCoordinateParameter()) {
                return false;
            }
            final String normalized = body.toLowerCase(Locale.ROOT);
            final String coordinate = coordinateName().toLowerCase(Locale.ROOT);
            return normalized.contains("shadowbounds")
                || normalized.contains("issampleinshadowmap")
                || normalized.contains("abs(" + coordinate + ".x)")
                || (normalized.contains(coordinate + ".x>")
                    && normalized.contains(coordinate + ".x<")
                    && normalized.contains(coordinate + ".y>")
                    && normalized.contains(coordinate + ".y<"));
        }

        private boolean isSupportedPcfBody() {
            if (!List.of("float", "vec3").contains(returnType)) {
                return false;
            }
            if ("texture2DShadow2x2".equals(name)) {
                return parameters.size() == 2
                    && "sampler2D".equals(parameters.get(0).type())
                    && "vec3".equals(parameters.get(1).type())
                    && "shadowPos".equals(coordinateName())
                    && "float".equals(returnType)
                    && body.contains("shadowMapResolution")
                    && (body.contains("texture") || body.contains("shadow2D") || body.contains("getShadow"));
            }
            return parameters.size() == 3
                && "vec3".equals(parameters.get(0).type())
                && "float".equals(parameters.get(1).type())
                && "float".equals(parameters.get(2).type())
                && "shadowPos".equals(coordinateName())
                && "vec3".equals(returnType)
                && (body.contains("shadowtex") || body.contains("shadow2D") || body.contains("texture"));
        }

        private String coordinateName() {
            if (hasCoordinateParameter()) {
                return parameters.get(coordinateIndex()).name();
            }
            throw new IllegalStateException("PCF helper has no shadow coordinate parameter: " + name);
        }

        private boolean hasCoordinateParameter() {
            return parameters.size() > coordinateIndex();
        }

        private int coordinateIndex() {
            return "texture2DShadow2x2".equals(name) ? 1 : 0;
        }
    }
}
