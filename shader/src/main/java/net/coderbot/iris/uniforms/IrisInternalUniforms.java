package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.gl.MatrixStack;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shadow.ShadowMatrices;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class IrisInternalUniforms {
    private static final Vector4f FOG_COLOR = new Vector4f();
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);

    private IrisInternalUniforms() {
    }

    private static Vector4f getColorModulator() {
        return COLOR_MODULATOR.set(
            GLStateManager.getShaderColorR(),
            GLStateManager.getShaderColorG(),
            GLStateManager.getShaderColorB(),
            GLStateManager.getShaderColorA());
    }

    private static float getEffectiveAlphaRef() {
        if (!GLStateManager.getAlphaTest().isEnabled() || GLStateManager.getAlphaState().getFunction() == GL11.GL_ALWAYS) {
            return -1.0f;
        }
        return GLStateManager.getAlphaState().getReference();
    }

    private static int getEffectiveAlphaFunc() {
        if (!GLStateManager.getAlphaTest().isEnabled()) return 7;
        final int func = GLStateManager.getAlphaState().getFunction();
        return func & 0x7;
    }

    public static void addFogUniforms(DynamicUniformHolder uniforms, FogMode fogMode) {
        uniforms.uniform4f(PER_FRAME, "iris_FogColor", () -> {
            final Vector3d color = GLStateManager.getFogState().getFogColor();
            return FOG_COLOR.set((float) color.x, (float) color.y, (float) color.z, GLStateManager.getFogState().getFogAlpha());
        });

        uniforms
            .uniform1f(PER_FRAME, "iris_FogStart", () -> GLStateManager.getFogState().getStart())
            .uniform1f(PER_FRAME, "iris_FogEnd", () -> GLStateManager.getFogState().getEnd())
            .uniform1f(PER_FRAME, "iris_FogDensity", () -> Math.max(0.0F, GLStateManager.getFogState().getDensity()));

        uniforms
            .uniform1f("iris_currentAlphaTest", IrisInternalUniforms::getEffectiveAlphaRef, StateUpdateNotifiers.alphaTestNotifier)
            .uniform1f("alphaTestRef", IrisInternalUniforms::getEffectiveAlphaRef, StateUpdateNotifiers.alphaTestNotifier)
            .uniform1i("iris_currentAlphaFunc", IrisInternalUniforms::getEffectiveAlphaFunc, StateUpdateNotifiers.alphaFuncNotifier);

        uniforms.uniform4f("iris_ColorModulator", IrisInternalUniforms::getColorModulator, StateUpdateNotifiers.colorModulatorNotifier);
    }

    public static void addOtherUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier, PackDirectives directives) {
        // Demonica: upstream allocates fresh matrices in every supplier, every frame. These scratch matrices belong to
        // this registration (one program's holder) and the uniforms copy what a supplier returns, so returning them
        // is safe and the suppliers allocate nothing per frame.
        final Matrix4f normalMatScratch = new Matrix4f();
        final Matrix3f normalMat = new Matrix3f();
        final Matrix4f defaultProjectionInverse = new Matrix4f();
        final Matrix4f defaultModelViewInverse = new Matrix4f();
        final MatrixStack shadowModelView = new MatrixStack();
        final Matrix4f shadowModelViewInverse = new Matrix4f();
        final Matrix4f shadowProjectionInverse = new Matrix4f();

        // Demonica: D has no CapturedRenderingState.getGbufferModelView()/getGbufferProjection(); the default
        // matrices invert what gbufferModelView and gbufferProjection upload (MatrixUniforms: RenderingState plus
        // the eye-height translate for the modelview).
        uniforms.uniformMatrix3(PER_FRAME, "iris_DefaultNormalMat", () -> {
            return normalMatScratch.set(MatrixUniforms.getGbufferModelView()).invert().transpose3x3(normalMat);
        });

        uniforms.uniformMatrix(PER_FRAME, "iris_DefaultProjectionMatrixInverse", () -> {
            return defaultProjectionInverse.set(RenderingState.INSTANCE.getProjectionMatrix()).invert();
        });

        uniforms.uniformMatrix(PER_FRAME, "iris_DefaultModelViewMatrixInverse", () -> {
            return defaultModelViewInverse.set(MatrixUniforms.getGbufferModelView()).invert();
        });

        // Demonica: D's createShadowModelView takes no near/far planes (its baseline pulls the camera back by a fixed
        // offset instead), so the modelview inverse passes only rotation and interval, as shadowModelView does.
        uniforms.uniformMatrix(PER_FRAME, "iris_ShadowModelViewMatrixInverse", () -> {
            return shadowModelViewInverse.set(ShadowRenderer.createShadowModelView(shadowModelView, directives.getSunPathRotation(),
                directives.getShadowDirectives().getIntervalSize()).peek().getModel()).invert();
        });

        // Demonica: near/far follow D's shadow pass (ShadowRenderer's ortho projection and shadowProjection), not
        // upstream's Mth.equal(plane, -1.0f) with `* 16`: D substitutes the DH distance for any negative plane, and
        // DHCompat.getRenderDistance() already returns blocks here. An inverse must invert the matrix the pass uses.
        uniforms.uniformMatrix(PER_FRAME, "iris_ShadowProjectionMatrixInverse", () -> {
            return ShadowMatrices.createOrthoMatrix(directives.getShadowDirectives().getDistance(),
                directives.getShadowDirectives().getNearPlane() < 0 ? -DHCompat.getRenderDistance() : directives.getShadowDirectives().getNearPlane(),
                directives.getShadowDirectives().getFarPlane() < 0 ? DHCompat.getRenderDistance() : directives.getShadowDirectives().getFarPlane(),
                shadowProjectionInverse).invert();
        });
    }
}
