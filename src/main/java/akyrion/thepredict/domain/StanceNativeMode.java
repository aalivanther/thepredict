package akyrion.thepredict.domain;

import java.util.Locale;

/**
 * How ThePredict shares a stance with MythicMobs (M23.8). {@link #HYBRID} is the installation default.
 */
public enum StanceNativeMode {
    /** Writes {@code predictor_stance} and reads the author's stance. Never calls {@code setStance}. */
    HYBRID,
    /** Reads the author's stance and writes nothing. */
    READ,
    /** Calls {@code setStance}. The author's stance wins a conflict until the next action change. */
    WRITE,
    /** Neither reads nor writes. */
    OFF;

    /** Installation default. */
    public static final StanceNativeMode DEFAULT = HYBRID;

    /**
     * Reads a mode name, ignoring case.
     *
     * @param text the yaml text
     * @return the mode
     * @throws IllegalArgumentException when the name is unknown
     */
    public static StanceNativeMode parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown stance native mode '" + text + "'");
        }
    }
}
