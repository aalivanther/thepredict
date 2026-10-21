package akyrion.thepredict.domain;

import java.util.Locale;

/**
 * What happens when the author's wind-up is shorter than the minimum (M23.4). {@link #LEAD} is the default.
 */
public enum ShortWindUp {
    /** The cast waits until the minimum has been shown. */
    LEAD,
    /** The tell stays the author's wind-up. Counted as an opt-out. */
    IMMEDIATE;

    /** Installation default. */
    public static final ShortWindUp DEFAULT = LEAD;

    /**
     * Reads a name, ignoring case.
     *
     * @param text yaml text
     * @return the mode
     * @throws IllegalArgumentException when the name is unknown
     */
    public static ShortWindUp parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown short wind-up '" + text + "'");
        }
    }
}
