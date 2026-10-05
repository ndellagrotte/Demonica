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
	SHADOW_TRANSLUCENT,
	// NB: Must be last due to implementation details of DeferredWorldRenderingPipeline
	SHADOW
}
