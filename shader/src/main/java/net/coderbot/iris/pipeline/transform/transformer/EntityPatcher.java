package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;

/**
 * Declares the entity uniforms a pack reads without declaring them. Ported from the TauMC engine's class of the same
 * name (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md); its caller, the ATTRIBUTES transformer, comes in
 * Step 6.
 */
public final class EntityPatcher {
	private EntityPatcher() {
	}

	public static void patchEntityId(ShaderAst transformer, AttributeParameters parameters) {
		injectUniformIfUndeclared(transformer, "entityId", "uniform int entityId;");
		injectUniformIfUndeclared(transformer, "blockEntityId", "uniform int blockEntityId;");
		injectUniformIfUndeclared(transformer, "currentRenderedItemId", "uniform int currentRenderedItemId;");
	}

	public static void patchOverlayColor(ShaderAst transformer, AttributeParameters parameters) {
		injectUniformIfUndeclared(transformer, "entityColor", "uniform vec4 entityColor;");
	}

	private static void injectUniformIfUndeclared(ShaderAst transformer, String name, String declaration) {
		if (transformer.containsCall(name) && !transformer.hasVariable(name)) {
			transformer.injectVariable(declaration);
		}
	}
}
