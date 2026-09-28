package net.coderbot.iris.pipeline.transform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlslTokensTest {
    private static List<String> tokens(String glsl) {
        return GlslTokens.of(glsl).tokens();
    }

    @Test
    void commentsAreRemoved() {
        assertEquals(List.of("float", "a", "=", "1.0", ";", "float", "b", ";"), tokens("""
            // a line comment
            float a = /* inline */ 1.0; // trailing
            /* a block
               comment over lines */ float b;
            """));
        // A comment between two tokens still separates them.
        assertEquals(List.of("a", "b"), tokens("a/**/b"));
        // A backslash-newline continues a line comment.
        assertEquals(List.of("x", ";"), tokens("// comment \\\ncontinued\nx;"));
    }

    @Test
    void aPreprocessorLineIsOneTokenWithCanonicalSpacing() {
        assertEquals(List.of("#version 330 core", "#extension GL_ARB_shader_image_load_store : enable", "void",
                "main", "(", ")", "{", "}"),
            tokens("""
                #version   330\tcore
                  #  extension GL_ARB_shader_image_load_store:enable // comment
                void main() {}
                """));
        // glsl-transformer prints '#extension X: enable', TauMC's header '#extension X : enable'.
        assertEquals(GlslTokens.of("#extension GL_X: enable\n"), GlslTokens.of("#extension GL_X : enable\n"));
        // A backslash-newline continues a directive; a block comment before it leaves it a directive.
        assertEquals(List.of("#define A ( 1 + 2 )", "A"), tokens("#define A (1 + \\\n 2)\nA"));
        assertEquals(List.of("#define B 1"), tokens("/* x */ #define B 1"));
        assertEquals(List.of("#"), tokens("#\n"));
        // Only a '#' that starts a line starts a directive.
        assertEquals(List.of("a", ";", "#", "define", "X"), tokens("a; #define X"));
    }

    @Test
    void multiCharacterOperatorsStayWhole() {
        assertEquals(List.of("a", "<<=", "b", ">>=", "c", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||",
                "^^", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="),
            tokens("a<<=b>>=c++ -- << >> <= >= == != && || ^^ += -= *= /= %= &= |= ^="));
        // Longest operator first, as a C lexer does: 'a+++b' is 'a ++ + b'.
        assertEquals(List.of("a", "++", "+", "b"), tokens("a+++b"));
        assertEquals(List.of("v", ".", "xy", "[", "0", "]", "?", "x", ":", "y", ","), tokens("v.xy[0]?x:y,"));
    }

    @Test
    void floatLiteralsCompareByValue() {
        // glsl-transformer reprints every float as Double.toString(value) + "f".
        assertEquals(GlslTokens.of("x = 0.0;"), GlslTokens.of("x = 0.0f;"));
        assertEquals(tokens("1.0"), tokens("1."));
        assertEquals(tokens("1.0"), tokens("1.0e0"));
        assertEquals(tokens("1.0"), tokens("1.0F"));
        assertEquals(tokens("0.5"), tokens(".5"));
        assertEquals(tokens("1e-3"), tokens("0.001f"));
        assertEquals(tokens("1e10"), tokens("1.0E10f"));
        assertEquals(List.of("vec4", "(", "1.0", ")"), tokens("vec4(1.0f)"));
        assertNotEquals(tokens("0.1"), tokens("0.10000001"));
    }

    @Test
    void integerUnsignedAndDoubleLiteralsStayDistinct() {
        assertNotEquals(tokens("1"), tokens("1.0"));
        assertNotEquals(tokens("1u"), tokens("1"));
        assertEquals(tokens("1u"), tokens("1U"));
        assertNotEquals(tokens("1.0lf"), tokens("1.0"));
        assertEquals(tokens("1.0lf"), tokens("1.LF"));
        assertNotEquals(tokens("1.0hf"), tokens("1.0lf"));
        // Integers compare by value, whatever the radix (glsl-transformer keeps the radix, lower-case hex).
        assertEquals(tokens("16"), tokens("0x10"));
        assertEquals(tokens("0xff"), tokens("0XFF"));
        assertEquals(tokens("8"), tokens("010"));
        assertEquals(List.of("0"), tokens("0"));
        assertEquals(List.of("255u"), tokens("0xFFu"));
        // Digits inside an identifier are not a number.
        assertEquals(List.of("sampler2D", "tex1"), tokens("sampler2D tex1"));
    }

    @Test
    void textBreaksLinesAfterStatementsAndBraces() {
        final GlslTokens tokens = GlslTokens.of("""
            #version 330 core
            uniform   float a;   void main(){ if(a>0.0){gl_FragColor=vec4(a);} }
            """);
        assertEquals("""
            #version 330 core
            uniform float a ;
            void main ( ) {
            if ( a > 0.0 ) {
            gl_FragColor = vec4 ( a ) ;
            }
            }
            """, tokens.text());
        assertEquals("", GlslTokens.of("").text());
        assertEquals("a ;\n#define X\nb\n", GlslTokens.of("a; \n#define X\nb").text());
    }

    @Test
    void diffIsEmptyForTheSameTokensAndShowsChangedLines() {
        final String taumc = "#version 330 core\nuniform float a ; void main ( ) { x = 0.0 ; y = 1 ; }";
        final String douira = """
            #version 330 core
            uniform float a;
            void main() {
            \tx = 0.0f;
            \ty = 1;
            }
            """;
        assertEquals("", GlslTokens.diff(taumc, douira));

        final String changed = douira.replace("y = 1;", "y = 2;");
        assertEquals("""
            @@ -3,4 +3,4 @@
              void main ( ) {
              x = 0.0 ;
            - y = 1 ;
            + y = 2 ;
              }
            """, GlslTokens.diff(taumc, changed));

        // An inserted line and a removed line far apart make two hunks with their own line numbers.
        final String a = "a;\nb;\nc;\nd;\ne;\nf;\ng;\nh;\ni;\nj;\n";
        final String b = "a;\nnew;\nb;\nc;\nd;\ne;\nf;\ng;\nh;\nj;\n";
        assertEquals("""
            @@ -1,3 +1,4 @@
              a ;
            + new ;
              b ;
              c ;
            @@ -7,4 +8,3 @@
              g ;
              h ;
            - i ;
              j ;
            """, GlslTokens.diff(a, b));
    }

    @Test
    void containsMatchesAContiguousRunOfTokens() {
        final GlslTokens tokens = GlslTokens.of("""
            void main() {
                color = iris_FrontColor;   // comment
                gl_FragData[0] = vec4(1.0f);
            }
            """);
        assertTrue(tokens.contains("color = iris_FrontColor ;"));
        assertTrue(GlslTokens.contains(tokens, "color=iris_FrontColor;"));
        assertTrue(tokens.contains("vec4(1.0)"), "float canonicalization applies to the snippet too");
        assertTrue(GlslTokens.contains("a  +  b", "a+b"));
        assertFalse(tokens.contains("color = gl_FragData"));
        assertFalse(tokens.contains("iris_Front"), "a snippet matches whole tokens only");
        assertFalse(tokens.contains(""));
        assertEquals(1, tokens.count("1.0"));
        assertEquals(2, GlslTokens.of("a; b; c").count(";"));
        assertThrows(IllegalArgumentException.class, () -> tokens.count("a b"));
    }
}
