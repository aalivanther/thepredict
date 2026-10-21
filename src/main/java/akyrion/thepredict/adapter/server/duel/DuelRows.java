package akyrion.thepredict.adapter.server.duel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Text of the rows of a duel recording (M18.4): CSV cells with RFC 4180 quoting and JSON values for the typed lines of
 * {@code decisions.jsonl}, written by hand because {@code adapter.server} does not use Gson (rule D5). Pure and
 * stateless; the rows are built on the server thread and handed to {@link DuelWriter} as immutable text.
 */
public final class DuelRows {
    /** Decimals kept in numbers. */
    public static final int DECIMALS = 4;

    private DuelRows() {
    }

    /**
     * One CSV row: the cells joined by commas, each quoted when it holds a comma, a quote or a line break.
     *
     * @param cells values; {@code null}, an empty {@code Optional} and a non-finite number give an empty cell
     * @return the row without its line break
     */
    public static String csv(Object... cells) {
        var row = new StringBuilder();
        for (var index = 0; index < cells.length; index++) {
            if (index > 0) {
                row.append(',');
            }
            row.append(cell(text(cells[index])));
        }
        return row.toString();
    }

    /**
     * One CSV cell with RFC 4180 quoting.
     *
     * @param value text of the cell
     * @return the cell, quoted with doubled quotes when needed
     */
    public static String cell(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * A number as written in every file: at most {@link #DECIMALS} decimals, a dot, no exponent and no trailing zeros.
     *
     * @param value the number
     * @return its text, empty when it is not finite
     */
    public static String number(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        var scaled = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.signum() == 0 ? "0" : scaled.toPlainString();
    }

    /**
     * A JSON string literal.
     *
     * @param value the text
     * @return the quoted and escaped text
     */
    public static String json(String value) {
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
                    if (character < 0x20 || character == ' ' || character == ' ') {
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
     * A JSON array of strings.
     *
     * @param values the texts
     * @return the array
     */
    public static String jsonArray(Collection<String> values) {
        var out = new StringBuilder("[");
        for (var value : values) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(json(value));
        }
        return out.append(']').toString();
    }

    /**
     * A JSON object whose values are already JSON.
     *
     * @param members keys and JSON values, in their iteration order
     * @return the object
     */
    public static String jsonObject(Map<String, String> members) {
        var out = new StringBuilder("{");
        members.forEach((key, value) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(json(key)).append(':').append(value);
        });
        return out.append('}').toString();
    }

    /**
     * A JSON value of a number, or {@code null} when it is not finite.
     *
     * @param value the number
     * @return the JSON value
     */
    public static String jsonNumber(double value) {
        return Double.isFinite(value) ? number(value) : "null";
    }

    /**
     * A JSON value of an optional text.
     *
     * @param value the text
     * @return the quoted text or {@code null}
     */
    public static String jsonText(Optional<String> value) {
        return value.map(DuelRows::json).orElse("null");
    }

    /** The text of one CSV cell before quoting. */
    private static String text(Object value) {
        return switch (value) {
            case null -> "";
            case Optional<?> optional -> optional.map(DuelRows::text).orElse("");
            case OptionalDouble optional -> optional.isPresent() ? number(optional.getAsDouble()) : "";
            case Double number -> number(number);
            case Float number -> number(number);
            case Enum<?> constant -> constant.name();
            default -> value.toString();
        };
    }
}
