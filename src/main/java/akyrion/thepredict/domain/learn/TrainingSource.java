package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.PlayMode;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Where a window came from (M28.2). {@link #PLAYER_TRAINING} is last (errata E28-3 applied). The default training
 * list does not include it and does not include paced play. The stored token is {@link #token()}, the same text
 * that {@code learning.trainingSources} accepts, so a build can filter rows without a translation table.
 */
public enum TrainingSource {
    /** The simulator. */
    SIM,
    /** The labelled bank. */
    BENCH,
    /** A player fight on rung 8. */
    PLAYER_FULL,
    /** A player fight below rung 8. Recorded, not trained by default. */
    PLAYER_PACED,
    /** A mob marked {@code PredictorTraining: true}. */
    PLAYER_TRAINING;

    /** Default {@code learning.trainingSources}. */
    public static final List<TrainingSource> DEFAULTS = List.of(SIM, BENCH, PLAYER_FULL);

    /**
     * Parses a config token.
     *
     * @param token the token
     * @return the source
     * @throws IllegalArgumentException when the token is unknown
     */
    public static TrainingSource parse(String token) {
        return switch (token) {
            case "sim" -> SIM;
            case "bench" -> BENCH;
            case "player_full" -> PLAYER_FULL;
            case "player_paced" -> PLAYER_PACED;
            case "player_training" -> PLAYER_TRAINING;
            default -> throw new IllegalArgumentException("unknown training source " + token);
        };
    }

    /**
     * The token stored in {@code m28_value_stats.source}. {@link #parse} reads it back.
     *
     * @return the lower-case name
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Source of a window of a live fight (M28.2). The simulator does not use this: it records {@link #SIM} itself.
     * Without a player participant the fight is bench play. With one, a training mob is {@link #PLAYER_TRAINING};
     * otherwise bench mode or the top rung is {@link #PLAYER_FULL} and any lower rung is {@link #PLAYER_PACED}.
     *
     * @param playersPresent whether a player is a participant of the encounter
     * @param training whether the mob file sets {@code PredictorTraining: true}
     * @param play the mode the fight runs after {@code auto} is resolved
     * @param rung the pressure rung when the window closed
     * @return the source
     */
    public static TrainingSource of(boolean playersPresent, boolean training, PlayMode play, int rung) {
        Objects.requireNonNull(play, "play");
        if (!playersPresent) {
            return BENCH;
        }
        if (training) {
            return PLAYER_TRAINING;
        }
        if (play == PlayMode.BENCH || rung >= DifficultyPolicy.MAX_RUNG) {
            return PLAYER_FULL;
        }
        return PLAYER_PACED;
    }
}
