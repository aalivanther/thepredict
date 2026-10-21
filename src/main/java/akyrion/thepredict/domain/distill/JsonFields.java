package akyrion.thepredict.domain.distill;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level scalar fields of one JSON object (M29.6 and M29.7 verdict files). A nested object or array is skipped
 * whole, so a field of the same name inside it is never taken for the top-level one. A string comes back decoded;
 * a number, {@code true}, {@code false} or {@code null} comes back as its literal text. Malformed text and a repeated
 * top-level name are refused.
 */
public final class JsonFields {
    private final String text;
    private int at;

    private JsonFields(String text) {
        this.text = text;
    }

    /**
     * Reads the top-level scalar fields.
     *
     * @param text one JSON object
     * @return name to value, nested values left out
     * @throws IllegalArgumentException when the text is not one JSON object or repeats a top-level name
     */
    public static Map<String, String> topLevel(String text) {
        var reader = new JsonFields(text);
        var fields = new LinkedHashMap<String, String>();
        reader.space();
        reader.expect('{');
        reader.space();
        if (reader.peek() == '}') {
            reader.at++;
        } else {
            reader.members(fields);
        }
        reader.space();
        if (reader.at != text.length()) {
            throw new IllegalArgumentException("json: text after the object");
        }
        return Map.copyOf(fields);
    }

    private void members(Map<String, String> fields) {
        var names = new HashSet<String>();
        while (true) {
            space();
            var name = string();
            space();
            expect(':');
            space();
            var nested = peek() == '{' || peek() == '[';
            var value = nested ? skipNested() : scalar();
            if (!names.add(name)) {
                throw new IllegalArgumentException("json: repeated name " + name);
            }
            if (!nested) {
                fields.put(name, value);
            }
            space();
            var separator = next();
            if (separator == '}') {
                return;
            }
            if (separator != ',') {
                throw new IllegalArgumentException("json: expected , or }");
            }
        }
    }

    private String scalar() {
        if (peek() == '"') {
            return string();
        }
        var start = at;
        while (at < text.length() && "+-.0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                .indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        var literal = text.substring(start, at);
        if (literal.isEmpty() || !(literal.equals("true") || literal.equals("false") || literal.equals("null")
                || literal.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))) {
            throw new IllegalArgumentException("json: bad literal");
        }
        return literal;
    }

    private String string() {
        expect('"');
        var out = new StringBuilder();
        while (true) {
            var c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw new IllegalArgumentException("json: control character in a string");
            }
            out.append(c == '\\' ? escape() : c);
        }
    }

    private char escape() {
        var c = next();
        return switch (c) {
            case '"', '\\', '/' -> c;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicode();
            default -> throw new IllegalArgumentException("json: bad escape");
        };
    }

    private char unicode() {
        if (at + 4 > text.length()) {
            throw new IllegalArgumentException("json: short unicode escape");
        }
        try {
            var code = Integer.parseInt(text.substring(at, at + 4), 16);
            at += 4;
            return (char) code;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("json: bad unicode escape", exception);
        }
    }

    private String skipNested() {
        var depth = 0;
        do {
            var c = peek();
            if (c == '"') {
                string();
                continue;
            }
            at++;
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        } while (depth > 0);
        return "";
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private void expect(char wanted) {
        if (next() != wanted) {
            throw new IllegalArgumentException("json: expected " + wanted);
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw new IllegalArgumentException("json: unexpected end");
        }
        return text.charAt(at);
    }

    private char next() {
        var c = peek();
        at++;
        return c;
    }
}
