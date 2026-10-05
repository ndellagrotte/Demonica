package net.coderbot.iris.gbuffer_overrides.matching;

public enum SpecialCondition {
	ENTITY_EYES,
	BEACON_BEAM,
	GLINT,
	// Demonica: upstream's LIGHTNING, DRAGON_RAYS and DRAGON_RAYS_DEPTH render pipelines (I pipeline/IrisPipelines.java);
	// set around lightning bolts (RenderManagerIrisMixin) and the dragon death ray (LayerEnderDragonDeathIrisMixin)
	LIGHTNING,
	// Demonica: upstream's END_PORTAL and END_GATEWAY render pipelines, the only draws its shadow pass gives
	// SHADOW_BLOCK; set around the end portal and gateway surface (TileEntityEndPortalRendererIrisMixin). Outside
	// the shadow pass it changes nothing: the draw keeps its block-entity program
	END_PORTAL
}
