package net.coderbot.iris.shadows.frustum.fallback;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Optional;
import com.demonica.celeritas.CeleritasJoml;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;

@Optional.Interface(modid = "distanthorizons", iface = "com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum")
public class BoxCullingFrustum extends Frustum implements ViewportProvider, org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum, IDhApiShadowCullingFrustum {
	private final BoxCuller boxCuller;
	private double x;
	private double y;
	private double z;
	private int worldMinYDH;
	private int worldMaxYDH;

	public BoxCullingFrustum(BoxCuller boxCuller) {
		this.boxCuller = boxCuller;
	}

	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		super.setPosition(cameraX, cameraY, cameraZ);
		this.x = cameraX;
		this.y = cameraY;
		this.z = cameraZ;
		boxCuller.setPosition(cameraX, cameraY, cameraZ);
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return !boxCuller.isCulled(aabb);
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return !boxCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** Exposes the distance-box containment state used by region-level culling. */
	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (boxCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ)) {
			return OUTSIDE;
		}

		return boxCuller.isFullyInsideSodium(minX, minY, minZ, maxX, maxY, maxZ)
				? FULLY_INSIDE
				: PARTIALLY_INSIDE;
	}

	@Override
	public Viewport sodium$createViewport() {
		return CeleritasJoml.viewport(this, x, y, z);
	}

	@Optional.Method(modid = "distanthorizons")
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {
		this.worldMinYDH = worldMinBlockY;
		this.worldMaxYDH = worldMaxBlockY;
	}

	@Optional.Method(modid = "distanthorizons")
	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
		return !boxCuller.isCulled(lodBlockPosMinX, this.worldMinYDH, lodBlockPosMinZ, lodBlockPosMinX + lodBlockWidth, this.worldMaxYDH, lodBlockPosMinZ + lodBlockWidth);
	}
}
