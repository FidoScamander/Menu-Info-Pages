package newt.infopages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader used for page/config files so the plugin has no JSON dependency. */
final class SimpleJson {
    private final String source;
    private int index;

    private SimpleJson(String source) {
        this.source = source == null ? "" : source;
    }

    static Object parse(String source) {
        SimpleJson parser = new SimpleJson(source);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != parser.source.length()) {
            throw parser.error("Unexpected trailing data");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= source.length()) throw error("Unexpected end of input");
        return switch (source.charAt(index)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (take('}')) return map;
        while (true) {
            skipWhitespace();
            if (index >= source.length() || source.charAt(index) != '"') {
                throw error("Expected object key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            map.put(key, readValue());
            skipWhitespace();
            if (take('}')) return map;
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (take(']')) return values;
        while (true) {
            values.add(readValue());
            skipWhitespace();
            if (take(']')) return values;
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (index < source.length()) {
            char c = source.charAt(index++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (index >= source.length()) throw error("Unterminated escape");
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (index + 4 > source.length()) throw error("Incomplete unicode escape");
                    String hex = source.substring(index, index + 4);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException ex) {
                        throw error("Invalid unicode escape: " + hex);
                    }
                    index += 4;
                }
                default -> throw error("Unknown escape: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private Object readNumber() {
        int start = index;
        if (take('-')) { /* sign */ }
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            decimal = true;
            index++;
            if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        if (start == index) throw error("Expected JSON value");
        String token = source.substring(start, index);
        try {
            return decimal ? Double.parseDouble(token) : Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw error("Invalid number: " + token);
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!source.regionMatches(index, literal, 0, literal.length())) {
            throw error("Expected " + literal);
        }
        index += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
    }

    private boolean take(char expected) {
        if (index < source.length() && source.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) throw error("Expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + index);
    }
}
