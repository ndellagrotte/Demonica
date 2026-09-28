package net.coderbot.iris.celeritas.vertices;

import net.coderbot.iris.Iris;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** Determines which Iris terrain-only vertex attributes are read by transformed shader programs. */
public final class TerrainVertexFormatRequirements {
    /** Optional attributes appended after Celeritas' Vanilla-like base terrain format. */
    public enum Attribute {
        MC_ENTITY("mc_Entity"),
        MID_TEX_COORD("mc_midTexCoord"),
        TANGENT("at_tangent"),
        MID_BLOCK("at_midBlock"),
        NORMAL("iris_Normal");

        private final String shaderName;

        Attribute(String shaderName) {
            this.shaderName = shaderName;
        }

        /** The attribute's name in shader source. */
        String shaderName() {
            return shaderName;
        }
    }

    private static final int ALL_ATTRIBUTES = (1 << Attribute.values().length) - 1;

    private final int flags;

    private TerrainVertexFormatRequirements(int flags) {
        this.flags = flags;
    }

    /** Returns the conservative layout used before transformed terrain sources are available. */
    public static TerrainVertexFormatRequirements all() {
        return new TerrainVertexFormatRequirements(ALL_ATTRIBUTES);
    }

    /** Returns a requirement set containing exactly the supplied optional attributes. */
    public static TerrainVertexFormatRequirements of(Attribute... attributes) {
        Objects.requireNonNull(attributes, "Attributes must not be null");

        int flags = 0;
        for (Attribute attribute : attributes) {
            flags |= bit(Objects.requireNonNull(attribute, "Attribute must not be null"));
        }
        return new TerrainVertexFormatRequirements(flags);
    }

    /**
     * Analyzes every active terrain and shadow vertex shader as one union because section VBOs are shared by passes.
     * A source that cannot be inspected is treated as requiring the complete format.
     */
    public static TerrainVertexFormatRequirements analyze(Collection<String> transformedVertexSources) {
        Objects.requireNonNull(transformedVertexSources, "Transformed vertex sources must not be null");

        int flags = 0;
        for (String source : transformedVertexSources) {
            if (source == null || source.isBlank()) {
                Iris.logger.warn("Celeritas terrain vertex format analysis received an unavailable transformed vertex shader; using the complete format");
                return all();
            }

            try {
                final int[] occurrences = countIdentifiers(source);
                for (Attribute attribute : Attribute.values()) {
                    // Celeritas always declares iris_Normal. A declaration without a second reference does not need storage.
                    if (occurrences[attribute.ordinal()] > 1) {
                        flags |= bit(attribute);
                    }
                }
            } catch (RuntimeException exception) {
                Iris.logger.warn("Celeritas terrain vertex format analysis failed; using the complete format", exception);
                return all();
            }
        }

        return new TerrainVertexFormatRequirements(flags);
    }

    /** Returns whether the shared terrain VBO must contain an optional attribute. */
    public boolean requires(Attribute attribute) {
        return (this.flags & bit(Objects.requireNonNull(attribute, "Attribute must not be null"))) != 0;
    }

    private static int bit(Attribute attribute) {
        return 1 << attribute.ordinal();
    }

    /** The directive names a GLSL lexer knows; any other after {@code #} is a lexer error. */
    private static final Set<String> DIRECTIVES = Set.of("define", "elif", "else", "endif", "error", "extension", "if",
        "ifdef", "ifndef", "line", "pragma", "undef", "version");

    /**
     * Counts, per {@link Attribute}, the identifier tokens of {@code source} that spell its shader name. A library-free
     * scan with the token rules of the GLSL lexer this replaced (TauMC's {@code GLSLLexer}, until Step 6 of
     * docs/glsl-transformer_adoption/ADOPTION_PLAN.md): comments are skipped, a preprocessor line (from {@code #} to
     * the end of the line, backslash continuations included) is skipped whole, so its macro text and string literals
     * ({@code #error "..."}) count nothing, and identifiers are {@code [A-Za-z_][A-Za-z0-9_]*} outside numbers.
     *
     * <p>What that lexer reported as an error throws {@link IllegalArgumentException}, and {@link #analyze} then keeps
     * the complete format, as before: a character no GLSL token starts with outside comments and preprocessor lines
     * ({@code @}, {@code $}, a backquote, a quote, a backslash not before a line break, a form feed, anything outside
     * ASCII), and an unknown directive name ({@code #include}). Two differences, both toward the complete format and
     * neither reachable from transformed sources: an unterminated block comment is an error here (the lexer read the
     * rest as code), and the text inside {@code #if}/{@code #ifdef} blocks is scanned as code (the lexer read it as one
     * opaque token, so an attribute used only there did not count; the Celeritas header, the only conditional text in
     * a transformed source, names none of the attributes).</p>
     */
    static int[] countIdentifiers(String source) {
        final Attribute[] attributes = Attribute.values();
        final int[] occurrences = new int[attributes.length];
        final int length = source.length();
        int i = 0;
        while (i < length) {
            final char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                final int close = source.indexOf("*/", i + 2);
                if (close < 0) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: unterminated block comment");
                }
                i = close + 2;
            } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                i = endOfLine(source, i + 2);
            } else if (c == '#') {
                int name = i + 1;
                while (name < length && (source.charAt(name) == ' ' || source.charAt(name) == '\t')) {
                    name++;
                }
                int nameEnd = name;
                while (nameEnd < length && Character.isLetter(source.charAt(nameEnd)) && source.charAt(nameEnd) < 128) {
                    nameEnd++;
                }
                if (nameEnd > name && !DIRECTIVES.contains(source.substring(name, nameEnd))) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: unknown directive #"
                        + source.substring(name, nameEnd));
                }
                i = endOfLine(source, nameEnd);
            } else if (isIdentifierStart(c)) {
                int end = i + 1;
                while (end < length && isIdentifierPart(source.charAt(end))) {
                    end++;
                }
                for (Attribute attribute : attributes) {
                    if (attribute.shaderName.length() == end - i && source.startsWith(attribute.shaderName, i)) {
                        occurrences[attribute.ordinal()]++;
                    }
                }
                i = end;
            } else if (isDigit(c) || (c == '.' && i + 1 < length && isDigit(source.charAt(i + 1)))) {
                // A number with its suffix (1.0f, 0x1Fu, 1.0e-3): its letters are not identifiers.
                int end = i + 1;
                while (end < length) {
                    final char d = source.charAt(end);
                    if (isIdentifierPart(d) || d == '.') {
                        end++;
                    } else if ((d == '+' || d == '-') && (source.charAt(end - 1) == 'e' || source.charAt(end - 1) == 'E')
                        && !(end - i > 1 && source.charAt(i) == '0' && (source.charAt(i + 1) == 'x' || source.charAt(i + 1) == 'X'))) {
                        end++;
                    } else {
                        break;
                    }
                }
                i = end;
            } else if (c == '\\') {
                // A line continuation; any other backslash is not a GLSL token.
                if (i + 1 < length && source.charAt(i + 1) == '\n') {
                    i += 2;
                } else if (i + 2 < length && source.charAt(i + 1) == '\r' && source.charAt(i + 2) == '\n') {
                    i += 3;
                } else {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: stray backslash");
                }
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || "+-*/%<>=!&|^~?:;,.(){}[]".indexOf(c) >= 0) {
                i++;
            } else {
                throw new IllegalArgumentException("Invalid transformed GLSL source: character U+"
                    + String.format("%04X", (int) c));
            }
        }
        return occurrences;
    }

    /** The index after the line starting before {@code from}: past its line break, a backslash before one continuing it. */
    private static int endOfLine(String source, int from) {
        final int length = source.length();
        int i = from;
        while (i < length) {
            final char c = source.charAt(i);
            if (c == '\\' && i + 1 < length) {
                // An escaped character, a line break included (a continuation).
                i += source.charAt(i + 1) == '\r' && i + 2 < length && source.charAt(i + 2) == '\n' ? 3 : 2;
            } else if (c == '\n') {
                return i + 1;
            } else {
                i++;
            }
        }
        return length;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof TerrainVertexFormatRequirements other && this.flags == other.flags;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.flags);
    }
}
