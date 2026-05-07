package com.example.salesai.adapters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON parser. Supports:
 * <ul>
 *   <li>objects, arrays, strings (with all standard escapes including
 *       backslash-u-XXXX style), numbers (returned as {@link Long} when
 *       integral and as {@link Double} otherwise), booleans, and
 *       {@code null}.</li>
 *   <li>UTF-8 input. The caller is expected to have already decoded
 *       bytes into a {@link String} using UTF-8.</li>
 * </ul>
 *
 * <p>Why hand-rolled? The MVP must ship without external dependencies,
 * so we can't pull in Jackson / Gson. The parser is intentionally
 * small; it is NOT a fully spec-compliant validator and rejects
 * unknown control characters in strings.
 */
public final class MiniJson {

    private MiniJson() {}

    /** Parse the given text into a tree of Map / List / String / Long / Double / Boolean / null. */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text == null");
        }
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object result = p.readValue();
        p.skipWhitespace();
        if (!p.eof()) {
            throw new IllegalStateException(
                    "Unexpected trailing content at offset " + p.offset);
        }
        return result;
    }

    // ---------------------------------------------------------------
    //  Public typed accessors
    // ---------------------------------------------------------------

    public static String asString(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        return String.valueOf(o);
    }

    public static long asLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Long l) {
            return l;
        }
        if (o instanceof Double d) {
            return d.longValue();
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            return Long.parseLong(s.trim());
        }
        throw new IllegalArgumentException("Cannot convert to long: " + o);
    }

    public static boolean asBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        if (o == null) {
            return Map.of();
        }
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("Not a JSON object: " + o);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw new IllegalArgumentException("Not a JSON array: " + o);
    }

    /** Convenience: list of objects (the common case). */
    public static List<Map<String, Object>> asObjectList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : asList(o)) {
            out.add(asObject(item));
        }
        return out;
    }

    /** Convenience: list of strings. */
    public static List<String> asStringList(Object o) {
        List<String> out = new ArrayList<>();
        for (Object item : asList(o)) {
            out.add(asString(item));
        }
        return out;
    }

    // ---------------------------------------------------------------
    //  Writer
    // ---------------------------------------------------------------

    /** Serialize a tree of Map / List / String / Number / Boolean / null to JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (value instanceof Number n) {
            if (n instanceof Double d && !Double.isFinite(d)) {
                sb.append("null");
            } else {
                sb.append(n.toString());
            }
        } else if (value instanceof Map<?, ?> m) {
            writeObject(sb, m);
        } else if (value instanceof List<?> l) {
            writeArray(sb, l);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> l) {
        sb.append('[');
        boolean first = true;
        for (Object item : l) {
            if (!first) sb.append(',');
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------
    //  Parser
    // ---------------------------------------------------------------

    private static final class Parser {
        private final String src;
        private int offset;

        Parser(String src) {
            this.src = src;
            this.offset = 0;
        }

        boolean eof() {
            return offset >= src.length();
        }

        char peek() {
            return src.charAt(offset);
        }

        char read() {
            return src.charAt(offset++);
        }

        void expect(char c) {
            if (eof() || peek() != c) {
                throw new IllegalStateException(
                        "Expected '" + c + "' at offset " + offset
                                + " but found "
                                + (eof() ? "EOF" : "'" + peek() + "'"));
            }
            offset++;
        }

        void skipWhitespace() {
            while (!eof()) {
                char c = peek();
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    offset++;
                } else {
                    return;
                }
            }
        }

        Object readValue() {
            skipWhitespace();
            if (eof()) {
                throw new IllegalStateException("Unexpected EOF");
            }
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw new IllegalStateException(
                            "Unexpected character '" + c + "' at offset "
                                    + offset);
                }
            };
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> obj = new LinkedHashMap<>();
            skipWhitespace();
            if (!eof() && peek() == '}') {
                offset++;
                return obj;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                obj.put(key, value);
                skipWhitespace();
                if (eof()) {
                    throw new IllegalStateException("Unterminated object");
                }
                char next = read();
                if (next == ',') {
                    continue;
                }
                if (next == '}') {
                    return obj;
                }
                throw new IllegalStateException(
                        "Expected ',' or '}' at offset " + (offset - 1));
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> arr = new ArrayList<>();
            skipWhitespace();
            if (!eof() && peek() == ']') {
                offset++;
                return arr;
            }
            while (true) {
                Object value = readValue();
                arr.add(value);
                skipWhitespace();
                if (eof()) {
                    throw new IllegalStateException("Unterminated array");
                }
                char next = read();
                if (next == ',') {
                    continue;
                }
                if (next == ']') {
                    return arr;
                }
                throw new IllegalStateException(
                        "Expected ',' or ']' at offset " + (offset - 1));
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw new IllegalStateException("Unterminated string");
                }
                char c = read();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (eof()) {
                        throw new IllegalStateException(
                                "Dangling escape at end of input");
                    }
                    char esc = read();
                    switch (esc) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'u'  -> {
                            if (offset + 4 > src.length()) {
                                throw new IllegalStateException(
                                        "Truncated \\u escape at offset "
                                                + offset);
                            }
                            String hex = src.substring(offset, offset + 4);
                            offset += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw new IllegalStateException(
                                        "Bad \\u escape: " + hex, nfe);
                            }
                        }
                        default -> throw new IllegalStateException(
                                "Unknown escape '\\" + esc + "' at offset "
                                        + (offset - 1));
                    }
                    continue;
                }
                if (c < 0x20) {
                    throw new IllegalStateException(
                            "Unescaped control character 0x"
                                    + Integer.toHexString(c)
                                    + " in string at offset " + (offset - 1));
                }
                sb.append(c);
            }
        }

        Object readNumber() {
            int start = offset;
            if (peek() == '-') {
                offset++;
            }
            while (!eof()) {
                char c = peek();
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                        || c == '+' || c == '-') {
                    offset++;
                } else {
                    break;
                }
            }
            String literal = src.substring(start, offset);
            boolean fractional = literal.indexOf('.') >= 0
                    || literal.indexOf('e') >= 0
                    || literal.indexOf('E') >= 0;
            try {
                if (fractional) {
                    return Double.parseDouble(literal);
                }
                return Long.parseLong(literal);
            } catch (NumberFormatException nfe) {
                throw new IllegalStateException(
                        "Bad number literal '" + literal + "'", nfe);
            }
        }

        Object readBoolean() {
            if (src.startsWith("true", offset)) {
                offset += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", offset)) {
                offset += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException(
                    "Expected boolean at offset " + offset);
        }

        Object readNull() {
            if (src.startsWith("null", offset)) {
                offset += 4;
                return null;
            }
            throw new IllegalStateException(
                    "Expected null at offset " + offset);
        }
    }
}
