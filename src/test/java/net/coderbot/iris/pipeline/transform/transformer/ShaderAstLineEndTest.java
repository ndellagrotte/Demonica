package net.coderbot.iris.pipeline.transform.transformer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ShaderAst#leadingExtensionCount}'s line ends (Step 6 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
 * an S5 leftover): {@code \r\n}, {@code \n} and a lone {@code \r} each end a line; before, only {@code \n} did, so a
 * CR-only source was one line and its leading {@code #extension} lines were not counted.
 */
class ShaderAstLineEndTest {
    private static final String A = "#extension GL_ARB_shader_texture_lod : enable";
    private static final String B = "#extension GL_EXT_gpu_shader4 : require";

    @Test
    void lineFeeds() {
        assertEquals(2, ShaderAst.leadingExtensionCount("#version 330 core\n" + A + "\n" + B + "\nvoid main() { }\n"));
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\n" + A + "\n\n" + B + "\nvoid main() { }\n"));
    }

    @Test
    void carriageReturnLineFeeds() {
        assertEquals(2, ShaderAst.leadingExtensionCount("#version 330 core\r\n" + A + "\r\n" + B + "\r\nvoid main() { }\r\n"));
        // A blank CRLF line ends the block.
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\r\n" + A + "\r\n\r\n" + B + "\r\n"));
        // A backslash before CRLF continues the line.
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\r\n#define X \\\r\n  1\r\n" + A + "\r\n"));
    }

    @Test
    void loneCarriageReturns() {
        assertEquals(2, ShaderAst.leadingExtensionCount("#version 330 core\r" + A + "\r" + B + "\rvoid main() { }\r"));
        // A blank line of a lone CR ends the block; so does an indented line after one.
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\r" + A + "\r\r" + B + "\r"));
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\r" + A + "\r  " + B + "\r"));
        // A backslash before a lone CR continues the line.
        assertEquals(1, ShaderAst.leadingExtensionCount("#version 330 core\r#define X \\\r  1\r" + A + "\r"));
        // Mixed line ends.
        assertEquals(2, ShaderAst.leadingExtensionCount("#version 330 core\r\n" + A + "\r" + B + "\nvoid main() { }\n"));
    }

    /**
     * glsl-transformer's lexer ends a directive only at {@code \n} ({@code NEWLINE: '\r'? '\n'}), as TauMC's did, so a
     * source whose directives end in lone {@code \r}s does not parse; the count above matters only where the parse
     * succeeds.
     */
    @Test
    void aSourceWithLoneCarriageReturnsDoesNotParse() {
        final String source = "#version 330 core\r" + A + "\r" + "uniform float u;\rvoid main() { }\r";
        assertThrows(ShaderAst.SyntaxException.class, () -> ShaderAst.parse(source));
        // CRLF parses, and both leading extensions reach the header.
        assertEquals(List.of(A, B), ShaderAst.parse("#version 330 core\r\n" + A + "\r\n" + B + "\r\nvoid main() { }\r\n")
            .extensionDirectives());
    }
}
