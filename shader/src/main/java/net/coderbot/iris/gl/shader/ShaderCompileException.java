package net.coderbot.iris.gl.shader;

public class ShaderCompileException extends RuntimeException {
	private final String filename;
	private final String error;

	public ShaderCompileException(String filename, String error) {
		// Demonica: upstream passes filename + ": " + error here, and getMessage() below prefixes the file name again,
		// so its message reads "composite.fsh: composite.fsh: <log>". The message is shown in chat, so name the file once.
		super(error);

		this.filename = filename;
		this.error = error;
	}

	public ShaderCompileException(String filename, Exception error) {
		super(error);

		this.filename = filename;
		this.error = error.getMessage();
	}

	@Override
	public String getMessage() {
		return filename + ": " + super.getMessage();
	}

	public String getError() {
		return error;
	}

	public String getFilename() {
		return filename;
	}

	// Demonica: the one-line log entry Iris.createPipeline writes. Upstream shows the driver log in its debug screen
	// (gui/debug/DebugTextWidget), which Demonica does not port; one line keeps the file name and the driver's
	// message together for log greps. It keeps the "Shader compilation failed" text the old RuntimeException carried.
	public String toLogLine() {
		return "Shader compilation failed for " + filename + ": " + flatten(error);
	}

	// Demonica: driver info logs are multi-line ("0(5) : error C0000: ...\n0(6) : ..."); join the non-blank lines.
	static String flatten(String log) {
		if (log == null) {
			return "(no driver message)";
		}

		StringBuilder builder = new StringBuilder();

		for (String line : log.split("\\R")) {
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				continue;
			}

			if (builder.length() > 0) {
				builder.append(" | ");
			}

			builder.append(trimmed);
		}

		return builder.length() == 0 ? "(no driver message)" : builder.toString();
	}

	// Demonica: Demonica's pass creation wraps program errors in RuntimeExceptions that name the pass
	// (DeferredWorldRenderingPipeline's "Failed to create pass for ..."), so Iris finds the compile error in the
	// cause chain. Upstream's DHCompat looks one level down for the same reason.
	public static ShaderCompileException findIn(Throwable throwable) {
		Throwable t = throwable;

		// Bounded, so a cyclic cause chain cannot loop forever.
		for (int depth = 0; t != null && depth < 32; depth++, t = t.getCause()) {
			if (t instanceof ShaderCompileException e) {
				return e;
			}
		}

		return null;
	}
}
