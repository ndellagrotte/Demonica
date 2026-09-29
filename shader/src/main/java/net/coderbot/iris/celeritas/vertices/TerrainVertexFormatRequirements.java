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
     * Directives whose line TauMC's lexer read with a strict mode: a comment, a backslash continuation or a lone
     * carriage return on the line was a lexer error.
     */
    private static final Set<String> STRICT_DIRECTIVES = Set.of("undef", "ifdef", "ifndef", "pragma", "extension", "version");

    /** Directives whose line a backslash before the line break continues, as TauMC's lexer read them. */
    private static final Set<String> CONTINUED_DIRECTIVES = Set.of("define", "if", "elif", "line");

    /**
     * Counts, per {@link Attribute}, the identifier tokens of {@code source} that spell its shader name. A library-free
     * scan with the token rules of the GLSL lexer this replaced (TauMC's {@code GLSLLexer}, until Step 6 of
     * docs/glsl-transformer_adoption/ADOPTION_PLAN.md): comments are skipped (a line comment ends at a line feed or a
     * lone carriage return, and a backslash before the line break continues it), a preprocessor line is skipped whole,
     * so its macro text and string literals ({@code #error "..."}) count nothing, and identifiers are
     * {@code [A-Za-z_][A-Za-z0-9_]*} outside numbers. A directive line follows TauMC's directive modes (Step 7b):
     * {@code #define}, {@code #if}, {@code #elif} and {@code #line} continue over a backslash before the line break,
     * {@code #else}, {@code #endif} and {@code #error} do not; a block comment on a directive line may span lines and
     * belongs to the directive; {@code #else} and {@code #endif} also end at a lone carriage return.
     *
     * <p>What that lexer reported as an error throws {@link IllegalArgumentException}, and {@link #analyze} then keeps
     * the complete format, as before: a character no GLSL token starts with outside comments and preprocessor lines
     * ({@code @}, {@code $}, a backquote, a quote, a backslash not before a line break, a form feed, anything outside
     * ASCII); a {@code #} that is not the first character of its line other than blanks; an unknown directive name
     * ({@code #include}); a comment, a backslash continuation or a lone carriage return on a {@code #undef},
     * {@code #ifdef}, {@code #ifndef}, {@code #pragma}, {@code #extension} or {@code #version} line; a lone carriage
     * return on any other directive line but {@code #else} and {@code #endif}; and an {@code #endif} without an open
     * {@code #if}, {@code #ifdef} or {@code #ifndef} (TauMC's lexer threw {@code EmptyStackException}, which
     * {@code analyze} caught as it catches this exception).</p>
     *
     * <p>Differences from that lexer that remain, found by probing it (TerrainVertexFormatScanParityTest), none
     * reachable from a transformed source (its only directives are {@code #version}, the {@code #extension} lines and
     * the Celeritas header's {@code #define}, {@code #if}, {@code #ifdef}, {@code #elif}, {@code #else}, {@code #error}
     * and {@code #endif} lines, one per line, without comments):</p>
     * <ul>
     *   <li>The scan counts attributes that the lexer did not, so the format it asks for is larger, never smaller. Text
     *   inside {@code #if}/{@code #ifdef}/{@code #ifndef} blocks is scanned as code (the lexer read it as one opaque
     *   token; the Celeritas header, the only conditional text in a transformed source, names none of the
     *   attributes). After a {@code #line} directive, after an {@code #else} or {@code #elif} without an open block,
     *   and after a directive name followed by a letter-less suffix ({@code #ifdef_X}), the lexer counted nothing more
     *   in the source; the scan goes on counting. Matching those would drop attributes the program reads.</li>
     *   <li>The scan throws where the lexer counted: an unterminated block comment (the lexer read the rest as
     *   code).</li>
     *   <li>The scan counts where the lexer reported an error (and {@code analyze} kept the complete format): the
     *   contents of {@code #version} and {@code #pragma} lines are not checked ({@code #version abc},
     *   {@code #version 330 foo}, {@code #pragma @@}, {@code #pragma "x"} were errors).</li>
     * </ul>
     */
    static int[] countIdentifiers(String source) {
        final Attribute[] attributes = Attribute.values();
        final int[] occurrences = new int[attributes.length];
        final int length = source.length();
        // Open #if, #ifdef and #ifndef blocks.
        final int[] depth = new int[1];
        // Only blanks since the last line break: a # here starts a directive.
        boolean lineStart = true;
        int i = 0;
        while (i < length) {
            final char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                i = endOfBlockComment(source, i + 2);
            } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                i = endOfLineComment(source, i + 2);
                lineStart = true;
            } else if (c == '#') {
                if (!lineStart) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: # inside a line");
                }
                i = endOfDirective(source, i, depth);
                lineStart = true;
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
                lineStart = false;
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
                lineStart = false;
            } else if (c == '\\') {
                // A line continuation; any other backslash is not a GLSL token.
                final int afterBreak = afterLineBreak(source, i + 1);
                if (afterBreak < 0) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: stray backslash");
                }
                i = afterBreak;
            } else if (c == '\n' || c == '\r') {
                i++;
                lineStart = true;
            } else if (c == ' ' || c == '\t') {
                i++;
            } else if ("+-*/%<>=!&|^~?:;,.(){}[]".indexOf(c) >= 0) {
                i++;
                lineStart = false;
            } else {
                throw new IllegalArgumentException("Invalid transformed GLSL source: character U+"
                    + String.format("%04X", (int) c));
            }
        }
        return occurrences;
    }

    /** The index after {@code "\n"} or {@code "\r\n"} at {@code at}, or -1 if no such line break is there. */
    private static int afterLineBreak(String source, int at) {
        if (at < source.length() && source.charAt(at) == '\n') {
            return at + 1;
        }
        if (at + 1 < source.length() && source.charAt(at) == '\r' && source.charAt(at + 1) == '\n') {
            return at + 2;
        }
        return -1;
    }

    /** The index after the block comment whose text starts at {@code from}. */
    private static int endOfBlockComment(String source, int from) {
        final int close = source.indexOf("*/", from);
        if (close < 0) {
            throw new IllegalArgumentException("Invalid transformed GLSL source: unterminated block comment");
        }
        return close + 2;
    }

    /**
     * The index after the line comment whose text starts at {@code from}: past its line break (a line feed, a carriage
     * return and line feed, or a lone carriage return, as TauMC's lexer ended one); an escaped character, a line break
     * included, continues it.
     */
    private static int endOfLineComment(String source, int from) {
        final int length = source.length();
        int i = from;
        while (i < length) {
            final char c = source.charAt(i);
            if (c == '\\' && i + 1 < length) {
                i += source.charAt(i + 1) == '\r' && i + 2 < length && source.charAt(i + 2) == '\n' ? 3 : 2;
            } else if (c == '\n') {
                return i + 1;
            } else if (c == '\r') {
                return i + 1 < length && source.charAt(i + 1) == '\n' ? i + 2 : i + 1;
            } else {
                i++;
            }
        }
        return length;
    }

    /**
     * The index after the directive line whose {@code #} is at {@code hash}, read as TauMC's directive lexer modes read
     * it (see {@link #countIdentifiers}); {@code depth} counts the open conditional blocks.
     */
    private static int endOfDirective(String source, int hash, int[] depth) {
        final int length = source.length();
        int name = hash + 1;
        while (name < length && (source.charAt(name) == ' ' || source.charAt(name) == '\t')) {
            name++;
        }
        int nameEnd = name;
        while (nameEnd < length && Character.isLetter(source.charAt(nameEnd)) && source.charAt(nameEnd) < 128) {
            nameEnd++;
        }
        final String directive = source.substring(name, nameEnd);
        if (!directive.isEmpty() && !DIRECTIVES.contains(directive)) {
            throw new IllegalArgumentException("Invalid transformed GLSL source: unknown directive #" + directive);
        }
        switch (directive) {
            case "if", "ifdef", "ifndef" -> depth[0]++;
            case "endif" -> {
                if (depth[0] == 0) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: #endif without #if");
                }
                depth[0]--;
            }
            default -> {
            }
        }
        final boolean strict = STRICT_DIRECTIVES.contains(directive);
        final boolean continued = CONTINUED_DIRECTIVES.contains(directive);
        final boolean message = directive.equals("error");
        final boolean carriageReturnEnds = directive.isEmpty() || directive.equals("else") || directive.equals("endif");
        int i = nameEnd;
        while (i < length) {
            final char c = source.charAt(i);
            if (c == '\n') {
                return i + 1;
            } else if (c == '\r') {
                if (i + 1 < length && source.charAt(i + 1) == '\n') {
                    return i + 2;
                }
                if (carriageReturnEnds) {
                    return i + 1;
                }
                throw new IllegalArgumentException("Invalid transformed GLSL source: a lone carriage return ends #" + directive);
            } else if (c == '\\' && afterLineBreak(source, i + 1) >= 0) {
                if (strict) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: a continued #" + directive + " line");
                }
                // A continuation, or (#else, #endif, #error) a backslash in the line's text.
                i = continued ? afterLineBreak(source, i + 1) : i + 1;
            } else if (!message && c == '/' && i + 1 < length && (source.charAt(i + 1) == '/' || source.charAt(i + 1) == '*')) {
                if (strict) {
                    throw new IllegalArgumentException("Invalid transformed GLSL source: a comment on a #" + directive + " line");
                }
                if (source.charAt(i + 1) == '/') {
                    return endOfLineComment(source, i + 2);
                }
                i = endOfBlockComment(source, i + 2);
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
