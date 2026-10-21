package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One prepared intention (M20.6).
 *
 * @param tactic tactic
 * @param movement movement of that tactic
 * @param lead lead choice, empty when the tactic has none
 * @param scoreMillis deterministic score in 0–1000
 * @param readyInTicks ticks until the lead choice is ready; 0 when there is no choice
 * @param damage damage tier used only by the local penalty; {@link DamageTier#NONE} keeps M20
 */
public record CandidateIntent(TacticId tactic, Movement movement, Optional<ChoiceId> lead, int scoreMillis,
                              int readyInTicks, DamageTier damage) {
    /**
     * Validates the ranges.
     *
     * @throws IllegalArgumentException if the score or the ticks are out of range
     */
    public CandidateIntent {
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(lead, "lead");
        Objects.requireNonNull(damage, "damage");
        if (scoreMillis < 0 || scoreMillis > 1000) {
            throw new IllegalArgumentException("scoreMillis must be within [0, 1000], was " + scoreMillis);
        }
        if (readyInTicks < 0) {
            throw new IllegalArgumentException("readyInTicks must be >= 0, was " + readyInTicks);
        }
    }

    /**
     * Intention of M20: no damage tier, so a local penalty never moves it.
     *
     * @param tactic tactic
     * @param movement movement of that tactic
     * @param lead lead choice, empty when the tactic has none
     * @param scoreMillis deterministic score in 0–1000
     * @param readyInTicks ticks until the lead choice is ready
     */
    public CandidateIntent(TacticId tactic, Movement movement, Optional<ChoiceId> lead, int scoreMillis,
                           int readyInTicks) {
        this(tactic, movement, lead, scoreMillis, readyInTicks, DamageTier.NONE);
    }
}
