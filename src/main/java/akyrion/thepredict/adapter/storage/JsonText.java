package akyrion.thepredict.adapter.storage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * JSON text for the local records. The escape is the one of the duel writer, copied because an adapter cannot import
 * another adapter (D7). {@code NaN} and infinities become {@code null}.
 */
public final class JsonText {
    private static final int DECIMALS = 6;

    private JsonText() {
    }

    /**
     * Quotes and escapes a string.
     *
     * @param value the text
     * @return the JSON string
     */
    public static String quote(String value) {
        var out = new StringBuilder(value.length() + 2).append('"');
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20 || character == '\u2028' || character == '\u2029') {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * A finite number, or {@code null}.
     *
     * @param value the number
     * @return the JSON token
     */
    public static String number(double value) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        var scaled = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.signum() == 0 ? "0" : scaled.toPlainString();
    }

    /**
     * An object whose values are already JSON tokens.
     *
     * @param members keys and tokens
     * @return the object, without spaces
     */
    public static String object(Map<String, String> members) {
        var out = new StringBuilder("{");
        members.forEach((key, value) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(quote(key)).append(':').append(value);
        });
        return out.append('}').toString();
    }
}
