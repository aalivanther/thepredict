package akyrion.thepredict.engine;

import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;

import java.util.Objects;

/**
 * One key's contribution to a batch (M21.6).
 *
 * @param key the row
 * @param stat windows merged since the key last left the pending map
 * @param epochMs wall time of the batch, milliseconds since the epoch
 */
public record OutcomeStatDelta(OutcomeStatKey key, OutcomeStat stat, long epochMs) {
    /**
     * Validates the delta.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public OutcomeStatDelta {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(stat, "stat");
    }
}
