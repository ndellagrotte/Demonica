package net.coderbot.iris.uniforms.custom.cached;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.parsing.MatrixType;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class Float3MatrixCachedUniform extends VectorCachedUniform<Matrix3fc> {
	// Demonica: a direct FloatBuffer, as Float4MatrixCachedUniform has, because GLSM's upload takes a buffer, not a
	// float[].
	final private FloatBuffer buffer = ByteBuffer.allocateDirect(9 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

	public Float3MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix3fc> supplier) {
		super(name, updateFrequency, new Matrix3f(), supplier);
	}

	@Override
	protected void setFrom(Matrix3fc other) {
		((Matrix3f) this.cached).set(other);
	}

	@Override
	public void push(int location) {
		// `gets` the values from the matrix and put's them into a buffer
		this.cached.get(buffer);
		// Demonica: GLSM's uniform entry point in place of IrisRenderSystem.uniformMatrix3fv.
		GLStateManager.glUniformMatrix3(location, false, buffer);
	}

	@Override
	public MatrixType<Matrix3f> getType() {
		return MatrixType.MAT3;
	}
}
