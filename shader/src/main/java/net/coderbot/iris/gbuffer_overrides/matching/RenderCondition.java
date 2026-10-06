package net.coderbot.iris.gbuffer_overrides.matching;

public enum RenderCondition {
	DEFAULT,
	SKY,
	TERRAIN_OPAQUE,
	TERRAIN_TRANSLUCENT,
	CLOUDS,
	DESTROY,
	BLOCK_ENTITIES,
	BLOCK_ENTITIES_TRANSLUCENT,
	BEACON_BEAM,
	ENTITIES,
	ENTITIES_TRANSLUCENT,
	GLINT,
	ENTITY_EYES,
	HAND_OPAQUE,
	HAND_TRANSLUCENT,
	RAIN_SNOW,
	WORLD_BORDER,
	// Demonica: upstream's ShaderKey.PARTICLES (I pipeline/programs/ShaderKey.java); one condition for every
	// particle draw, see DeferredWorldRenderingPipeline.getCondition
	PARTICLES,
	// Demonica: upstream's ShaderKey.LIGHTNING (lightning bolts and the dragon death ray)
	LIGHTNING,
	// NB: Must be last due to implementation details of DeferredWorldRenderingPipeline
	// Demonica: all five shadow conditions, not just SHADOW: the program table builds them after every gbuffer pass,
	// so a gbuffer program that samples the shadow map has already created the shadow targets they check for.
	// SHADOW_ENTITIES, SHADOW_LIGHTNING and SHADOW_BLOCK are upstream's ShaderKey.SHADOW_ENTITIES_CUTOUT,
	// SHADOW_LIGHTNING and SHADOW_BLOCK
	SHADOW_ENTITIES,
	SHADOW_LIGHTNING,
	SHADOW_BLOCK,
	SHADOW_TRANSLUCENT,
	SHADOW;

	// Demonica: upstream's ShaderKey.isShadow(), by condition instead of by program id
	public boolean isShadow() {
		return this == SHADOW
			|| this == SHADOW_TRANSLUCENT
			|| this == SHADOW_ENTITIES
			|| this == SHADOW_LIGHTNING
			|| this == SHADOW_BLOCK;
	}
}
