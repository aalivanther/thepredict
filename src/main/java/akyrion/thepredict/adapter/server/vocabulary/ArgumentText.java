package akyrion.thepredict.adapter.server.vocabulary;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Describes an author's argument in load warnings without echoing arbitrary text (rules C8 and W6).
 * Only plain names are quoted; anything else is described generically. Pure and stateless.
 */
final class ArgumentText {
    /** Longest argument that is quoted back in a warning. */
    static final int MAX_QUOTED_LENGTH = 40;

    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z0-9_]{1," + MAX_QUOTED_LENGTH + "}");

    private ArgumentText() {
    }

    /**
     * Quotes a plain name, or describes the argument when it is empty or not a plain name.
     *
     * @param raw argument as read from the MythicMobs line
     * @return text safe to write in the console
     */
    static String describe(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) {
            return "no value";
        }
        return PLAIN_NAME.matcher(raw).matches() ? "'" + raw + "'" : "a value that is not a plain name";
    }
}
