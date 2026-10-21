package akyrion.thepredict.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Movement a tactic asks for; the native goal that performs it is chosen by the adapter. */
public enum Movement {
    /** Close in on the target up to melee reach. */
    APPROACH,
    /** Stay inside the distance band around the target. */
    KEEP_DISTANCE,
    /** Increase the distance to the target. */
    RETREAT,
    /** Stop moving while still looking at the target. */
    HOLD,
    /** Go around a shield-blocking target. */
    FLANK,
    /** No controlled movement; the only option for species with a {@code Brain}. */
    NONE;

    /**
     * Parses a movement name without distinguishing case, as MythicMobs arguments are written.
     * Safe on any thread; does not throw on unknown text.
     *
     * @param text name such as {@code keep_distance} or {@code HOLD}
     * @return the movement, or empty when the text is not a movement name
     */
    public static Optional<Movement> parse(String text) {
        Objects.requireNonNull(text, "text");
        var normalized = text.trim().toUpperCase(Locale.ROOT);
        for (var movement : values()) {
            if (movement.name().equals(normalized)) {
                return Optional.of(movement);
            }
        }
        return Optional.empty();
    }
}
