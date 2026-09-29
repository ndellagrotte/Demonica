package net.coderbot.iris.pipeline.transform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text patches for specific shader packs that run on a stage's source before it is parsed, in
 * {@link ShaderTransformer}: {@link #patchCaveSkyholeClouds} and {@link #patchVolumetricCloudReferenceDistance} for
 * COMPOSITE fragment shaders, then {@link #patchCloudMovementTime} for every fragment shader. Library-free; moved
 * unchanged out of the TauMC engine's {@code CompatibilityTransformer} in Step 5 of
 * docs/glsl-transformer_adoption/ADOPTION_PLAN.md.
 */
public final class CompatibilityPatches {
    private CompatibilityPatches() {
    }

    private static final Pattern CLOUD_RGB_SKYHOLE = Pattern.compile(
        "VolumetricClouds\\.rgb\\s*\\*=\\s*1\\.0\\s*-\\s*skyhole\\s*;"
    );
    private static final Pattern CLOUD_ALPHA_SKYHOLE = Pattern.compile(
        "VolumetricClouds\\.a\\s*=\\s*mix\\s*\\(\\s*VolumetricClouds\\.a\\s*,\\s*1\\.0\\s*,\\s*skyhole\\s*\\)\\s*;"
    );
    private static final Pattern SKY_COLOR_SKYHOLE = Pattern.compile(
        "isSky\\s*\\?\\s*skyhole\\s*\\*\\s*caveDetection\\s*\\*\\s*caveFactor\\s*:\\s*0\\.0"
    );
    private static final Pattern VLC_REFERENCE_DISTANCE = Pattern.compile(
        "float\\s+lViewPosM\\s*=\\s*length\\s*\\(\\s*(viewPos|FragPosition)\\s*\\)\\s*<\\s*maxdist"
            + "\\s*\\?\\s*length\\s*\\(\\s*(?:viewPos|FragPosition)\\s*\\)\\s*-\\s*1\\.0\\s*:\\s*100000000\\.0\\s*;"
    );
    private static final Pattern DEPTHTEX0_DECLARATION = Pattern.compile(
        "uniform\\s+sampler2D\\s+depthtex0"
    );
    private static final Pattern DEPTHTEX1_DECLARATION = Pattern.compile(
        "uniform\\s+sampler2D\\s+depthtex1"
    );
    private static final Pattern DEPTHTEX1_USAGE = Pattern.compile(
        "texelFetch(?:2D)?\\s*\\(\\s*depthtex1"
    );
    private static final Pattern DH_DEPTHTEX_DECLARATION = Pattern.compile(
        "uniform\\s+sampler2D\\s+dhDepthTex"
    );
    private static final Pattern DH_DEPTHTEX_USAGE = Pattern.compile(
        "texelFetch(?:2D)?\\s*\\(\\s*(?:dhVoxyDepthTex|dhDepthTex)"
    );
    private static final Pattern DH_DEPTHTEX1_DECLARATION = Pattern.compile(
        "uniform\\s+sampler2D\\s+dhDepthTex1"
    );
    private static final Pattern DH_DEPTHTEX1_USAGE = Pattern.compile(
        "texelFetch(?:2D)?\\s*\\(\\s*(?:dhVoxyDepthTex1|dhDepthTex1)"
    );
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile(
        "(?m)^\\s*#version\\s+\\d+(?:\\s+\\w+)?"
    );
    private static final Pattern CLOUD_MOVEMENT_TIME = Pattern.compile(
        "float\\s+cloud_movement\\s*=\\s*\\(\\s*(?:worldTime|worldTimeSmooth)"
            + "\\s*\\+\\s*mod\\s*\\(\\s*worldDay\\s*,\\s*100\\s*\\)\\s*\\*\\s*24000(?:\\.0)?"
            + "\\s*\\)\\s*/\\s*24(?:\\.0)?\\s*\\*\\s*Cloud_Speed\\s*;"
    );
    private static final String CLOUD_MOVEMENT_TIME_REPLACEMENT =
        "float cloud_movement = (iris_worldTimeSmooth + mod(worldDay,100)*24000.0) / 24.0 * Cloud_Speed;";

    public static String patchCaveSkyholeClouds(String fragment) {
        String patched = CLOUD_RGB_SKYHOLE.matcher(fragment).replaceAll("VolumetricClouds.rgb *= 1.0;");
        patched = CLOUD_ALPHA_SKYHOLE.matcher(patched).replaceAll("VolumetricClouds.a = mix(VolumetricClouds.a, 1.0, 0.0);");
        return SKY_COLOR_SKYHOLE.matcher(patched).replaceAll("isSky ? 0.0 : 0.0");
    }

    public static String patchVolumetricCloudReferenceDistance(String fragment) {
        Matcher referenceMatcher = VLC_REFERENCE_DISTANCE.matcher(fragment);
        if (!referenceMatcher.find()) {
            return fragment;
        }

        String position = referenceMatcher.group(1);
        String replacement;
        if (DEPTHTEX0_DECLARATION.matcher(fragment).find()) {
            final StringBuilder depthSamples = new StringBuilder();
            if (DEPTHTEX1_DECLARATION.matcher(fragment).find() && DEPTHTEX1_USAGE.matcher(fragment).find()) {
                depthSamples.append("_irisCloudDepth = min(_irisCloudDepth, texelFetch(depthtex1, _irisCloudTexel, 0).x);\n");
            }
            if (DH_DEPTHTEX_DECLARATION.matcher(fragment).find() && DH_DEPTHTEX_USAGE.matcher(fragment).find()) {
                depthSamples.append("_irisCloudDepth = min(_irisCloudDepth, texelFetch(dhDepthTex, _irisCloudTexel, 0).x);\n");
            }
            if (DH_DEPTHTEX1_DECLARATION.matcher(fragment).find() && DH_DEPTHTEX1_USAGE.matcher(fragment).find()) {
                depthSamples.append("_irisCloudDepth = min(_irisCloudDepth, texelFetch(dhDepthTex1, _irisCloudTexel, 0).x);\n");
            }
            String depthReplacement = """
                float lViewPosM = length(%s) < maxdist ? length(%s) - 1.0 : 100000000.0;
                ivec2 _irisCloudTexel = ivec2(floor(gl_FragCoord.xy) * 2.0 + 0.5);
                float _irisCloudDepth = texelFetch(depthtex0, _irisCloudTexel, 0).x;
                """ + depthSamples + """
                if (_irisCloudDepth < 1.0 - 1e-5) {
                    lViewPosM = length(%s) - 1.0;
                } else if (length(%s) >= far - 1.0) {
                    lViewPosM = 100000000.0;
                }
                """;
            replacement = depthReplacement.formatted(position, position, position, position);
        } else {
            replacement = """
                float lViewPosM = length(%s) >= far - 1.0 ? 100000000.0
                    : length(%s) < maxdist ? length(%s) - 1.0 : 100000000.0;
                """.formatted(position, position, position);
        }
        return referenceMatcher.replaceAll(matchResult -> replacement);
    }

    public static String patchCloudMovementTime(String fragment) {
        Matcher cloudMatcher = CLOUD_MOVEMENT_TIME.matcher(fragment);
        if (!cloudMatcher.find()) {
            return fragment;
        }

        String patched = cloudMatcher.replaceAll(CLOUD_MOVEMENT_TIME_REPLACEMENT);
        if (patched.contains("uniform float iris_worldTimeSmooth;")) {
            return patched;
        }

        Matcher versionMatcher = VERSION_DIRECTIVE.matcher(patched);
        if (versionMatcher.find()) {
            int versionEnd = versionMatcher.end();
            return patched.substring(0, versionEnd)
                + "\nuniform float iris_worldTimeSmooth;"
                + patched.substring(versionEnd);
        }

        return "uniform float iris_worldTimeSmooth;\n" + patched;
    }

}
