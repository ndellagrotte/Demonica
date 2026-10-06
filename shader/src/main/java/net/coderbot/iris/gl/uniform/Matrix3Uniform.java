package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class Matrix3Uniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(9);
	private final Supplier<Matrix3fc> value;
	private final Matrix3f cachedValue;
	// Demonica: upstream seeds the cache with identity and compares against it, so a supplier whose first value is
	// identity never uploads and the shader keeps the linker's zero matrix. Upload the first value unconditionally,
	// as MatrixUniform does with its null cache.
	private boolean uploaded;

	Matrix3Uniform(int location, Supplier<Matrix3fc> value) {
		super(location);

		this.cachedValue = new Matrix3f();
		this.value = value;
	}

	Matrix3Uniform(int location, Supplier<Matrix3fc> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Matrix3f();
		this.value = value;
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(this::updateValue);
		}
	}

	public void updateValue() {
		Matrix3fc newValue = value.get();

		if (!uploaded || !cachedValue.equals(newValue)) {
			uploaded = true;
			cachedValue.set(newValue);

			cachedValue.get(buffer);
			buffer.rewind();

			// Demonica: GLSM's uniform entry point in place of IrisRenderSystem.uniformMatrix3fv.
			GLStateManager.glUniformMatrix3(location, false, buffer);
		}
	}
}
