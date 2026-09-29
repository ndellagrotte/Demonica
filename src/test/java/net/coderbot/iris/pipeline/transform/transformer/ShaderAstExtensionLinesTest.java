package net.coderbot.iris.pipeline.transform.transformer;

import net.coderbot.iris.pipeline.transform.GlslTokens;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShaderAst.ExtensionLines}, the text pre-pass that takes the {@code #extension} lines out of a program before
 * the parse (Step 7b of docs/glsl-transformer_adoption/ADOPTION_PLAN.md, report S7b-hardening.md), on the comments of
 * a directive line. The pre-pass must blank the {@code #extension} line and nothing else: what it blanks by mistake is
 * program text that silently disappears. Its verification found that a {@code "/*"} inside a line comment on an
 * {@code #extension} line was read as the start of a block comment, and everything up to the next block comment's end,
 * or to the end of the source, was blanked with the line.
 */
class ShaderAstExtensionLinesTest {
    private static final String EXTENSION = "#extension GL_ARB_gpu_shader5 : enable";

    /** {@code source} with its line {@code line} (0-based) blanked as the pre-pass blanks it: spaces, line break kept. */
    private static String blanked(String source, int line) {
        final String[] lines = source.split("\n", -1);
        lines[line] = " ".repeat(lines[line].length());
        return String.join("\n", lines);
    }

    @Test
    void aLineCommentHoldingABlockCommentStartInTheLeadingBlock() {
        final String source = "#version 330 core\n" + EXTENSION + " // see /* note\nuniform sampler2D colortex0;\n"
            + "void main() { }\n";
        final ShaderAst.ExtensionLines lines = ShaderAst.ExtensionLines.of(source);
        assertEquals(blanked(source, 1), lines.text());
        assertEquals(List.of(EXTENSION), lines.leading());
        assertEquals(List.of(), lines.later());
        assertEquals(List.of(EXTENSION), ShaderAst.parse(source).extensionDirectives());
    }

    @Test
    void aLineCommentHoldingABlockCommentStartBeforeALaterBlockComment() {
        final String source = "#version 330 core\n" + EXTENSION + " // see /* note\nuniform sampler2D colortex0;\n"
            + "/* a comment */\nvoid main() { }\n";
        final ShaderAst.ExtensionLines lines = ShaderAst.ExtensionLines.of(source);
        assertEquals(blanked(source, 1), lines.text());
        assertEquals(List.of(EXTENSION), lines.leading());
    }

    @Test
    void aLineCommentHoldingABlockCommentStartInAFunctionBody() {
        final String source = "#version 330 core\nout vec4 color;\nvoid main() {\n" + EXTENSION + " // old /* code\n"
            + "    color = vec4(1.0);\n}\n";
        final ShaderAst.ExtensionLines lines = ShaderAst.ExtensionLines.of(source);
        assertEquals(blanked(source, 3), lines.text());
        assertEquals(List.of(), lines.leading());
        assertEquals(List.of(EXTENSION), lines.later());
        final String printed = ShaderAst.parse(source).print("#version 330 core");
        assertTrue(GlslTokens.contains(printed, "void main ( ) { color = vec4 ( 1.0 ) ; }"), printed);
    }

    /** A block comment that starts on the directive line is part of the directive, up to its end on a later line. */
    @Test
    void aBlockCommentOnTheLineStillSpansLines() {
        final String source = "#version 330 core\n" + EXTENSION + " /* a\nb */\nuniform float u;\nvoid main() { }\n";
        final ShaderAst.ExtensionLines lines = ShaderAst.ExtensionLines.of(source);
        assertEquals(blanked(blanked(source, 1), 2), lines.text());
        assertEquals(List.of(EXTENSION), lines.leading());
    }

    /**
     * A line comment ends the directive at its line break, which a backslash continues, as the pre-pass reads a line
     * comment anywhere else in the source.
     */
    @Test
    void aContinuedLineCommentEndsAtTheNextLineBreak() {
        final String source = "#version 330 core\n" + EXTENSION + " // c /* \\\ncontinued\nuniform float u;\n"
            + "void main() { }\n";
        final ShaderAst.ExtensionLines lines = ShaderAst.ExtensionLines.of(source);
        assertEquals(blanked(blanked(source, 1), 2), lines.text());
        assertEquals(List.of(EXTENSION), lines.leading());
    }
}
