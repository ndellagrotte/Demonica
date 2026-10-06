package net.coderbot.iris.gl.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan item 4.2: the exception GlShader and ProgramCreator throw for a failed compile or link, and the one-line log
 * entry Iris.createPipeline writes from it.
 */
class ShaderCompileExceptionTest {
	private static final String NVIDIA_LOG = "0(5) : error C0000: syntax error, unexpected '}', expecting ',' or ';' at token \"}\"\n"
		+ "0(7) : error C1503: undefined variable \"colortex9\"\n";

	@Test
	void messageNamesTheFileOnce() {
		ShaderCompileException e = new ShaderCompileException("composite.fsh", "0(5) : error C0000: syntax error");

		assertEquals("composite.fsh: 0(5) : error C0000: syntax error", e.getMessage());
		assertEquals("composite.fsh", e.getFilename());
		assertEquals("0(5) : error C0000: syntax error", e.getError());
	}

	@Test
	void wrappedExceptionKeepsItsMessageAndCause() {
		IllegalStateException cause = new IllegalStateException("transform failed");
		ShaderCompileException e = new ShaderCompileException("gbuffers_terrain", cause);

		assertEquals("gbuffers_terrain: java.lang.IllegalStateException: transform failed", e.getMessage());
		assertEquals("transform failed", e.getError());
		assertSame(cause, e.getCause());
	}

	@Test
	void logLineIsOneLineWithFileAndDriverMessage() {
		ShaderCompileException e = new ShaderCompileException("composite.fsh", NVIDIA_LOG);

		assertEquals("Shader compilation failed for composite.fsh: 0(5) : error C0000: syntax error, unexpected '}', "
			+ "expecting ',' or ';' at token \"}\" | 0(7) : error C1503: undefined variable \"colortex9\"", e.toLogLine());
	}

	@Test
	void logLineHandlesCrLfBlankAndMissingLogs() {
		assertEquals("Shader compilation failed for final: ERROR: 0:3: 'x' : undeclared identifier | ERROR: 1 compilation errors.",
			new ShaderCompileException("final", "ERROR: 0:3: 'x' : undeclared identifier\r\n\r\n  ERROR: 1 compilation errors.  \r\n").toLogLine());
		assertEquals("Shader compilation failed for final: (no driver message)", new ShaderCompileException("final", "\n").toLogLine());
		assertEquals("Shader compilation failed for final: (no driver message)", new ShaderCompileException("final", (String) null).toLogLine());
	}

	@Test
	void findInWalksTheCauseChain() {
		ShaderCompileException compile = new ShaderCompileException("gbuffers_terrain.fsh", "error");
		RuntimeException wrapped = new RuntimeException("Failed to create pass for gbuffers_terrain",
			new RuntimeException("Shader compilation failed!", compile));

		assertSame(compile, ShaderCompileException.findIn(compile));
		assertSame(compile, ShaderCompileException.findIn(wrapped));
		assertNull(ShaderCompileException.findIn(new RuntimeException("other", new IllegalStateException())));
		assertNull(ShaderCompileException.findIn(null));
	}
}
