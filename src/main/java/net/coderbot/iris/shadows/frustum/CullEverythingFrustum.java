package net.coderbot.iris.shadows.frustum;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Optional;
import com.demonica.celeritas.CeleritasJoml;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;

@Optional.Interface(modid = "distanthorizons", iface = "com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum")
public class CullEverythingFrustum extends Frustum implements ViewportProvider, org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum, IDhApiShadowCullingFrustum {
	private double x;
	private double y;
	private double z;

	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		super.setPosition(cameraX, cameraY, cameraZ);
		this.x = cameraX;
		this.y = cameraY;
		this.z = cameraZ;
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return false;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return false;
	}

	/** Reports every camera-relative box as outside for region-level culling. */
	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return OUTSIDE;
	}

	@Override
	public Viewport sodium$createViewport() {
		return CeleritasJoml.viewport(this, x, y, z);
	}

	@Optional.Method(modid = "distanthorizons")
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {

	}

	@Optional.Method(modid = "distanthorizons")
	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
		return false;
	}
}
