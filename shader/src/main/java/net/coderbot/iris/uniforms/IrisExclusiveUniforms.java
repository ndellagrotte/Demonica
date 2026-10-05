package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.math.Vec3d;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;

import java.util.List;

public class IrisExclusiveUniforms {
	// Reusable vectors to avoid allocations every frame
	private static final Vector3d eyePositionCache = new Vector3d();
	private static final Vector3d relativeEyePositionCache = new Vector3d();
	private static final Vector4f lightningBoltPositionCache = new Vector4f();
	private static final Vector4f ZERO_VECTOR_4f = new Vector4f(0, 0, 0, 0);
	private static final Vector3d ZERO = new Vector3d(0);
	private static final Vector3d playerLookVectorCache = new Vector3d();
	private static final Vector3d playerBodyVectorCache = new Vector3d();
	private static final Vector3d vehicleLookVectorCache = new Vector3d();
	private static final Vector3d relativeVehiclePositionCache = new Vector3d();

	public static void addIrisExclusiveUniforms(UniformHolder uniforms) {
		WorldInfoUniforms.addWorldInfoUniforms(uniforms);

		// Demonica: PER_FRAME upstream, read from the chunk fade-in time and texture filtering options; 1.12.2 has
		// neither (chunks do not fade in, textures are not filtered), so both are the constant 0. They back the
		// FADE_VARIABLE and TEXTURE_FILTERING feature flags.
		uniforms.uniform1f(UniformUpdateFrequency.ONCE, "chunkFadeTimeInv", () -> 0.0F);
		uniforms.uniform1i(UniformUpdateFrequency.ONCE, "textureFilteringMode", () -> 0);

		//All Iris-exclusive uniforms (uniforms which do not exist in either OptiFine or ShadersMod) should be registered here.
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", IrisExclusiveUniforms::getThunderStrength);
		// Demonica: 1.12.2 has no End sky flash (upstream EndFlashStorage); the uniforms read 0 so packs' custom uniforms resolve.
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "endFlashIntensity", () -> 0.0F);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "previousEndFlashIntensity", () -> 0.0F);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", IrisExclusiveUniforms::getCurrentHealth);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", IrisExclusiveUniforms::getMaxHealth);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", IrisExclusiveUniforms::getCurrentHunger);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", IrisExclusiveUniforms::getCurrentAir);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", IrisExclusiveUniforms::getMaxAir);
		uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", IrisExclusiveUniforms::isFirstPersonCamera);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", IrisExclusiveUniforms::isSpectator);
		// Demonica: 1.12.2 has no colour-space pathway (upstream IrisVideoSettings.colorSpace), so this is always 0.
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentColorSpace", () -> 0);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "feetInWater", IrisExclusiveUniforms::getIsInShallowWater);
		// Demonica: 1.12.2 has no swimming pose (added in 1.13), so the player is never in a swimming animation.
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "inSwimmingAnimation", () -> false);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "vehicleInWater", IrisExclusiveUniforms::getVehicleInShallowWater);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleId", IrisExclusiveUniforms::getVehicleId);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "vehicleLookVector", IrisExclusiveUniforms::getVehicleLookVector);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeVehiclePosition", IrisExclusiveUniforms::getRelativeVehiclePosition);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerLookVector", IrisExclusiveUniforms::getPlayerLookVector);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerBodyVector", IrisExclusiveUniforms::getPlayerBodyVector);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isRiding", IrisExclusiveUniforms::getIsPassenger);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isElytraFlying", IrisExclusiveUniforms::isElytraFlying);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "heavyFog", IrisExclusiveUniforms::isHeavyFog);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerArmor", IrisExclusiveUniforms::getCurrentArmor);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerArmor", () -> 50);
		uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "seaLevel", () -> {
			final WorldClient world = Minecraft.getMinecraft().world;
			return world == null ? 0 : world.getSeaLevel();
		});
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition", IrisExclusiveUniforms::getRelativeEyePosition);
		uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", IrisExclusiveUniforms::getLightningBoltPosition);
	}

	private static float getThunderStrength() {
		// Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
		return Math.clamp(0.0F, 1.0F, Minecraft.getMinecraft().world.thunderingStrength);
	}

	private static float getCurrentHealth() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().player.getHealth() / Minecraft.getMinecraft().player.getMaxHealth();
	}

	private static float getCurrentHunger() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().player.getFoodStats().getFoodLevel() / 20f;
	}

	private static float getCurrentAir() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return (float) Minecraft.getMinecraft().player.getAir() / (float) Minecraft.getMinecraft().player.getAir();
	}

	private static float getMaxAir() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

//		return Minecraft.getMinecraft().thePlayer.getMaxAirSupply();
		return 300.0F;
	}

	private static float getMaxHealth() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().player.getMaxHealth();
	}

	private static float getCurrentArmor() {
		if (Minecraft.getMinecraft().player == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().player.getTotalArmorValue() / 50.0f;
	}

	private static Entity getVehicle() {
		final EntityPlayerSP player = Minecraft.getMinecraft().player;
		return player == null ? null : player.getRidingEntity();
	}

	private static int getVehicleId() {
		final Entity vehicle = getVehicle();
		if (vehicle == null || BlockRenderingSettings.INSTANCE.getEntityIds() == null) return 0;
		return EntityIdHelper.getEntityId(vehicle);
	}

	private static Vector3d getVehicleLookVector() {
		final Entity vehicle = getVehicle();
		if (vehicle == null) return ZERO;
		final Vec3d forward = vehicle.getForward();
		return vehicleLookVectorCache.set(forward.x, forward.y, forward.z);
	}

	private static Vector3d getRelativeVehiclePosition() {
		final Entity vehicle = getVehicle();
		if (vehicle == null) return ZERO;
		// Demonica: 1.12.2 has no Entity.getPosition(partialTick); lerp prevPos -> pos by hand, as upstream's lerps xo -> x.
		final float t = CapturedRenderingState.INSTANCE.getTickDelta();
		final double x = vehicle.prevPosX + (vehicle.posX - vehicle.prevPosX) * t;
		final double y = vehicle.prevPosY + (vehicle.posY - vehicle.prevPosY) * t;
		final double z = vehicle.prevPosZ + (vehicle.posZ - vehicle.prevPosZ) * t;
		return relativeVehiclePositionCache.set(CameraUniforms.getUnshiftedCameraPosition()).sub(x, y, z);
	}

	private static boolean getVehicleInShallowWater() {
		return isInShallowWater(getVehicle());
	}

	private static boolean getIsInShallowWater() {
		return isInShallowWater(Minecraft.getMinecraft().player);
	}

	// Demonica: 1.12.2 has no Entity.isInShallowWater() (upstream's isInWater() && !isUnderWater()); the
	// equivalent is isInWater() with the eyes out of water, which 1.12.2 tests with isInsideOfMaterial(WATER).
	private static boolean isInShallowWater(Entity entity) {
		return entity != null && entity.isInWater() && !entity.isInsideOfMaterial(Material.WATER);
	}

	private static Vector3d getPlayerLookVector() {
		if (Minecraft.getMinecraft().getRenderViewEntity() instanceof EntityLivingBase living) {
			final Vec3d look = living.getLook(CapturedRenderingState.INSTANCE.getTickDelta());
			return playerLookVectorCache.set(look.x, look.y, look.z);
		}
		return ZERO;
	}

	private static Vector3d getPlayerBodyVector() {
		// Demonica: upstream dereferences the camera entity unguarded; this returns zero without one.
		final Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
		if (camera == null) return ZERO;
		final Vec3d forward = camera.getForward();
		return playerBodyVectorCache.set(forward.x, forward.y, forward.z);
	}

	private static boolean getIsPassenger() {
		return Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.isRiding();
	}

	private static boolean isElytraFlying() {
		return Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.isElytraFlying();
	}

	private static boolean isHeavyFog() {
		// Demonica: null-checks ingameGUI, which upstream's gui never needs. In 1.12.2 only the dragon fight's boss bar
		// sets the fog flag (the wither only darkens the sky), as upstream.
		final Minecraft mc = Minecraft.getMinecraft();
		return mc.world != null && mc.ingameGUI != null && mc.ingameGUI.getBossOverlay().shouldCreateFog();
	}

	private static boolean isFirstPersonCamera() {
		// If camera type is not explicitly third-person, assume it's first-person.
        return !Camera.INSTANCE.isThirdPerson();
	}

	private static boolean isSpectator() {
		return GameModeUtil.isSpectator();
	}

	private static Vector3d getEyePosition() {
        final EntityLivingBase eye = (EntityLivingBase) Minecraft.getMinecraft().getRenderViewEntity();
        return eyePositionCache.set(eye.posX, eye.posY + eye.getEyeHeight(), eye.posZ);
	}

	private static Vector3d getRelativeEyePosition() {
		final Vector3dc cameraPos = CameraUniforms.getUnshiftedCameraPosition();
		final Vector3d eyePos = getEyePosition();
		return relativeEyePositionCache.set(cameraPos).sub(eyePos);
	}

	private static Vector4f getLightningBoltPosition() {
		if (Minecraft.getMinecraft().world != null) {
			final List<Entity> weatherEffects = Minecraft.getMinecraft().world.weatherEffects;
			for (Entity entity : weatherEffects) {
				if (entity instanceof EntityLightningBolt bolt) {
                    final Vector3dc cameraPos = CameraUniforms.getUnshiftedCameraPosition();
					return lightningBoltPositionCache.set(
						(float)(bolt.posX - cameraPos.x()),
						(float)(bolt.posY - cameraPos.y()),
						(float)(bolt.posZ - cameraPos.z()),
						1.0f
					);
				}
			}
		}
		return ZERO_VECTOR_4f;
	}

	public static class WorldInfoUniforms {
		public static void addWorldInfoUniforms(UniformHolder uniforms) {
			final WorldClient level = Minecraft.getMinecraft().world;
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> 0);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", () -> {
                if (level != null && level.provider != null) {
                    return level.provider.getCloudHeight();
                } else {
                    return 192.0;
                }
            });
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", () -> {
                if (level != null && level.provider != null) {
                    return level.provider.getHeight();
				} else {
					return 256;
				}
			});
			// Demonica: upstream reads dimensionType().logicalHeight(). 1.12.2's analogue is Forge's
			// WorldProvider.getActualHeight() (128 in the Nether, else 256); getHeight() is always 256. The world is
			// read per call, not captured like `level` above, so it follows dimension changes.
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit", () -> {
				final WorldClient world = Minecraft.getMinecraft().world;
				return world == null ? 256 : world.provider.getActualHeight();
			});
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> {
				if (level != null && level.provider != null) {
					return !level.provider.hasSkyLight();
				} else {
					return false;
				}
			});
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> {
				if (level != null && level.provider != null) {
					return level.provider.hasSkyLight();
				} else {
					return true;
				}
			});
			uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> {
				if (level != null && level.provider != null) {
                    return level.provider.getLightBrightnessTable()[0];
				} else {
					return 0f;
				}
			});

		}
	}
}
