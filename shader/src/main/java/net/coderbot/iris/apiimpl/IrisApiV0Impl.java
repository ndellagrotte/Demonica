package net.coderbot.iris.apiimpl;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisApiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;


public class IrisApiV0Impl implements IrisApi {
	public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();
	private static final IrisApiV0ConfigImpl CONFIG = new IrisApiV0ConfigImpl();

	@Override
	public int getMinorApiRevision() {
		return 1;
	}

	@Override
	public boolean isShaderPackInUse() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline == null) {
			return false;
		}

		return !(pipeline instanceof FixedFunctionWorldRenderingPipeline);
	}

	@Override
	public boolean isRenderingShadowPass() {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	/**
	 * Celeritas's video settings screen adds a "Shader Packs" tab when it finds this API and opens whatever screen
	 * this returns. The tab passes its options controller, not the screen (its lambda's {@code this}, at the pinned
	 * 06999aab), so a parent that is not a screen is taken to be the open screen, the one "Done" returns to.
	 */
	@Override
	public Object openMainIrisScreenObj(Object parent) {
		if (parent != null && !(parent instanceof GuiScreen)) {
			parent = Minecraft.getMinecraft().currentScreen;
		}
		return ShaderPackScreen.create((GuiScreen) parent);
	}

	@Override
	public String getMainScreenLanguageKey() {
		return "options.iris.shaderPackSelection";
	}

	@Override
	public IrisApiConfig getConfig() {
		return CONFIG;
	}
}
