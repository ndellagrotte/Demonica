package net.irisshaders.iris.api.v0;

import org.joml.Matrix4f;

/**
 * A callback invoked during the shadow pass, after opaque terrain has been
 * drawn.
 *
 * <p>The shadow pass runs with backface culling disabled and the terrain-cutout
 * phase active.
 *
 * <p>Demonica: 1.12.2 has no render pipelines or render passes, so upstream's
 * paragraph on assigning a pipeline and its note that any render target may be
 * passed are gone; the callback draws as vanilla code does, through
 * {@code Tessellator}/{@code BufferBuilder} or raw GL. When it is called:
 * <ul>
 *   <li>the shadow framebuffer is bound and the viewport covers the whole shadow
 *   map; nothing redirects another framebuffer bind to it, so a callback that
 *   binds one must bind the shadow framebuffer back before returning;</li>
 *   <li>the pack's {@code shadow} program is in use (the phase is
 *   {@code TERRAIN_CUTOUT}, which picks {@code shadow} in the shadow pass) and
 *   follows the vertex format of each draw, as for any other draw;</li>
 *   <li>GL's (GlStateManager's) projection matrix is {@code projection} and its
 *   model-view matrix is {@code modelView}, both loaded in the matrix stacks, so
 *   {@code ftransform()} and {@code gl_ModelViewMatrix} in the pack's shadow
 *   program see the shadow matrices; the callback may push and pop the
 *   model-view stack but must leave both stacks as it found them;</li>
 *   <li>vertex positions are relative to the render origin: a world position
 *   {@code (x, y, z)} is drawn at {@code (x - cameraX, y - cameraY, z - cameraZ)};</li>
 *   <li>texture unit 0 holds whatever terrain left bound (the block atlas when
 *   the pack draws terrain into the shadow map): bind the texture the geometry
 *   needs.</li>
 * </ul>
 *
 * @since Demonica API v0.2 (upstream Iris: API v0.4)
 */

@FunctionalInterface
public interface IrisShadowRenderCallback {
	/**
	 * @param modelView  the shadow pass model-view matrix
	 * @param projection the shadow pass projection matrix
	 * @param cameraX    the player camera X position the shadow pass is centered on
	 * @param cameraY    the player camera Y position the shadow pass is centered on
	 * @param cameraZ    the player camera Z position the shadow pass is centered on
	 * @param tickDelta  the partial tick the frame is being rendered at
	 */
	void renderShadow(Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY, double cameraZ, float tickDelta);
}
