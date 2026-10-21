package akyrion.thepredict.config;

import java.util.Locale;

/** The lower-case names of an enumeration joined with {@code |}, for the message of a mode key. */
final class ModeNames {
    private ModeNames() {
    }

    /**
     * Joins the constants.
     *
     * @param type the enumeration
     * @return {@code a|b|c}
     */
    static String of(Class<? extends Enum<?>> type) {
        var text = new StringBuilder();
        for (var value : type.getEnumConstants()) {
            if (!text.isEmpty()) {
                text.append('|');
            }
            text.append(value.name().toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }
}
