package net.coderbot.iris.pipeline.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A formatting-blind view of GLSL for comparing transform outputs (docs/glsl-transformer_adoption/ADOPTION_PLAN.md,
 * 3.5). TauMC's token-spaced serializer and glsl-transformer's indented printer write the same program differently;
 * compared as {@code GlslTokens}, they are equal exactly when their tokens are.
 *
 * <ul>
 *   <li>{@code //} and {@code /* *}{@code /} comments are removed; a backslash-newline joins two lines.</li>
 *   <li>A preprocessor line is one token: {@code #}, then the directive's own tokens one space apart, so
 *   {@code #  extension GL_X:enable} and {@code #extension GL_X : enable} are the same token.</li>
 *   <li>Everything else splits into identifiers, numbers, multi-character operators and single characters.</li>
 *   <li>Numbers are canonical. A floating-point literal becomes its value ({@link Double#toString(double)}), without a
 *   {@code f}/{@code F} suffix: {@code 1.}, {@code 1.0}, {@code 1.0f} and {@code 1e0} are one token, because
 *   glsl-transformer reprints every float as {@code Double.toString(value) + "f"}. A double ({@code lf}) or half
 *   ({@code hf}) literal keeps its suffix, lower-cased. An integer literal becomes its decimal value, with {@code u}
 *   if it is unsigned ({@code 0x10}, {@code 020} and {@code 16} are one token; {@code 16u} is another). Integers and
 *   floats stay distinct: {@code 1} is not {@code 1.0}.</li>
 * </ul>
 *
 * <p>{@link #text()} writes the tokens one space apart with a line break after {@code ;}, <code>{</code> and
 * <code>}</code> and around each preprocessor token; {@link #diff} compares two such texts line by line.</p>
 */
public final class GlslTokens {
    private static final Pattern TOKEN = Pattern.compile(
        "(?<id>[A-Za-z_][A-Za-z0-9_]*)"
            + "|(?<float>(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?(?:lf|LF|hf|HF|[fF])?"
            + "|\\d+[eE][+-]?\\d+(?:lf|LF|hf|HF|[fF])?)"
            + "|(?<int>0[xX][0-9a-fA-F]+[uU]?|\\d+[uU]?)"
            + "|(?<op><<=|>>=|\\+\\+|--|<<|>>|<=|>=|==|!=|&&|\\|\\||\\^\\^|[-+*/%&|^]=)"
            + "|(?<other>\\S)");
    private static final Set<String> LINE_ENDS = Set.of(";", "{", "}");

    private final List<String> tokens;
    private String text;

    private GlslTokens(List<String> tokens) {
        this.tokens = Collections.unmodifiableList(tokens);
    }

    /** Tokenizes {@code glsl}. */
    public static GlslTokens of(String glsl) {
        final List<String> tokens = new ArrayList<>();
        final String source = stripComments(glsl).replaceAll("\\\\\\r?\\n", "");
        for (String line : source.split("\\r?\\n", -1)) {
            final String trimmed = line.strip();
            if (trimmed.startsWith("#")) {
                final List<String> directive = new ArrayList<>();
                lex(trimmed.substring(1), directive);
                tokens.add(directive.isEmpty() ? "#" : "#" + String.join(" ", directive));
            } else {
                lex(line, tokens);
            }
        }
        return new GlslTokens(tokens);
    }

    /** The tokens, in order. */
    public List<String> tokens() {
        return tokens;
    }

    /** How many tokens equal {@code token} (after canonicalization, so {@code count("1.0")} also counts {@code 1.0f}). */
    public int count(String token) {
        final List<String> wanted = of(token).tokens;
        if (wanted.size() != 1) {
            throw new IllegalArgumentException("Not a single token: " + token);
        }
        return Collections.frequency(tokens, wanted.get(0));
    }

    /** Whether {@code snippet}'s tokens occur here as a contiguous run. */
    public boolean contains(String snippet) {
        final List<String> wanted = of(snippet).tokens;
        return !wanted.isEmpty() && Collections.indexOfSubList(tokens, wanted) >= 0;
    }

    /** Whether {@code snippet}'s tokens occur in {@code tokens} as a contiguous run. */
    public static boolean contains(GlslTokens tokens, String snippet) {
        return tokens.contains(snippet);
    }

    /** Whether {@code snippet}'s tokens occur in {@code glsl}'s as a contiguous run. */
    public static boolean contains(String glsl, String snippet) {
        return of(glsl).contains(snippet);
    }

    /** The tokens one space apart, a line break after {@code ;}, <code>{</code>, <code>}</code> and each directive. */
    public String text() {
        if (text == null) {
            final StringBuilder out = new StringBuilder();
            boolean lineStart = true;
            for (String token : tokens) {
                final boolean directive = token.startsWith("#");
                if (directive && !lineStart) {
                    out.append('\n');
                    lineStart = true;
                }
                if (!lineStart) {
                    out.append(' ');
                }
                out.append(token);
                lineStart = false;
                if (directive || LINE_ENDS.contains(token)) {
                    out.append('\n');
                    lineStart = true;
                }
            }
            if (!lineStart) {
                out.append('\n');
            }
            text = out.toString();
        }
        return text;
    }

    /**
     * A line-based LCS diff of {@code a.text()} and {@code b.text()}: empty when the token streams are equal, otherwise
     * hunks of {@code -} lines (only in {@code a}) and {@code +} lines (only in {@code b}) with two lines of context,
     * each hunk headed {@code @@ -<line>,<count> +<line>,<count> @@} (1-based lines of the texts).
     */
    public static String diff(GlslTokens a, GlslTokens b) {
        if (a.tokens.equals(b.tokens)) {
            return "";
        }
        return diffLines(lines(a.text()), lines(b.text()));
    }

    /** {@link #diff(GlslTokens, GlslTokens)} of two sources. */
    public static String diff(String a, String b) {
        return diff(of(a), of(b));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof GlslTokens other && tokens.equals(other.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    @Override
    public String toString() {
        return text();
    }

    private static List<String> lines(String text) {
        final List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static final int CONTEXT = 2;

    private static String diffLines(List<String> a, List<String> b) {
        // The common prefix and suffix need no LCS table; transform diffs are usually a few lines in a long shader.
        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
            && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }
        final List<String> midA = a.subList(prefix, a.size() - suffix);
        final List<String> midB = b.subList(prefix, b.size() - suffix);
        final int n = midA.size();
        final int m = midB.size();
        final int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = midA.get(i).equals(midB.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        // The edit script, one entry per line: ' ' common, '-' only in a, '+' only in b.
        final StringBuilder ops = new StringBuilder();
        final List<String> texts = new ArrayList<>();
        for (int k = 0; k < prefix; k++) {
            ops.append(' ');
            texts.add(a.get(k));
        }
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && midA.get(i).equals(midB.get(j))) {
                ops.append(' ');
                texts.add(midA.get(i));
                i++;
                j++;
            } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                ops.append('-');
                texts.add(midA.get(i++));
            } else {
                ops.append('+');
                texts.add(midB.get(j++));
            }
        }
        for (int k = a.size() - suffix; k < a.size(); k++) {
            ops.append(' ');
            texts.add(a.get(k));
        }

        // Line numbers (1-based) in a and in b before each entry.
        final int[] lineA = new int[ops.length() + 1];
        final int[] lineB = new int[ops.length() + 1];
        lineA[0] = 1;
        lineB[0] = 1;
        for (int k = 0; k < ops.length(); k++) {
            lineA[k + 1] = lineA[k] + (ops.charAt(k) != '+' ? 1 : 0);
            lineB[k + 1] = lineB[k] + (ops.charAt(k) != '-' ? 1 : 0);
        }

        // Hunks: changes closer than 2 * CONTEXT common lines share one; each has CONTEXT lines around it.
        final StringBuilder out = new StringBuilder();
        int k = 0;
        while (k < ops.length()) {
            if (ops.charAt(k) == ' ') {
                k++;
                continue;
            }
            int last = k;
            for (int probe = k + 1; probe < ops.length() && probe <= last + 2 * CONTEXT + 1; probe++) {
                if (ops.charAt(probe) != ' ') {
                    last = probe;
                }
            }
            final int from = Math.max(0, k - CONTEXT);
            final int to = Math.min(ops.length(), last + CONTEXT + 1);
            out.append("@@ -").append(lineA[from]).append(',').append(lineA[to] - lineA[from])
                .append(" +").append(lineB[from]).append(',').append(lineB[to] - lineB[from]).append(" @@\n");
            for (int h = from; h < to; h++) {
                out.append(ops.charAt(h)).append(' ').append(texts.get(h)).append('\n');
            }
            k = last + 1;
        }
        return out.toString();
    }

    private static void lex(String code, List<String> out) {
        final Matcher matcher = TOKEN.matcher(code);
        while (matcher.find()) {
            if (matcher.group("float") != null) {
                out.add(canonicalFloat(matcher.group()));
            } else if (matcher.group("int") != null) {
                out.add(canonicalInt(matcher.group()));
            } else {
                out.add(matcher.group());
            }
        }
    }

    static String canonicalFloat(String literal) {
        final String lower = literal.toLowerCase(Locale.ROOT);
        String suffix = "";
        String digits = lower;
        if (lower.endsWith("lf") || lower.endsWith("hf")) {
            suffix = lower.substring(lower.length() - 2);
            digits = lower.substring(0, lower.length() - 2);
        } else if (lower.endsWith("f")) {
            digits = lower.substring(0, lower.length() - 1);
        }
        try {
            return Double.toString(Double.parseDouble(digits)) + suffix;
        } catch (NumberFormatException e) {
            return literal;
        }
    }

    static String canonicalInt(String literal) {
        final boolean unsigned = literal.endsWith("u") || literal.endsWith("U");
        final String digits = unsigned ? literal.substring(0, literal.length() - 1) : literal;
        try {
            final long value;
            if (digits.startsWith("0x") || digits.startsWith("0X")) {
                value = Long.parseLong(digits.substring(2), 16);
            } else if (digits.length() > 1 && digits.startsWith("0")) {
                value = Long.parseLong(digits.substring(1), 8);
            } else {
                value = Long.parseLong(digits);
            }
            return value + (unsigned ? "u" : "");
        } catch (NumberFormatException e) {
            return literal;
        }
    }

    /** Replaces comments with a space, keeping the newlines of a block comment so that lines stay lines. */
    private static String stripComments(String glsl) {
        final StringBuilder out = new StringBuilder(glsl.length());
        int i = 0;
        final int length = glsl.length();
        while (i < length) {
            final char ch = glsl.charAt(i);
            if (ch == '/' && i + 1 < length && glsl.charAt(i + 1) == '/') {
                i += 2;
                while (i < length && glsl.charAt(i) != '\n') {
                    // A backslash-newline continues a line comment onto the next line.
                    if (glsl.charAt(i) == '\\' && i + 1 < length && glsl.charAt(i + 1) == '\n') {
                        i++;
                    }
                    i++;
                }
                out.append(' ');
            } else if (ch == '/' && i + 1 < length && glsl.charAt(i + 1) == '*') {
                i += 2;
                out.append(' ');
                while (i < length && !(glsl.charAt(i) == '*' && i + 1 < length && glsl.charAt(i + 1) == '/')) {
                    if (glsl.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(length, i + 2);
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }
}
