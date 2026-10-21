package akyrion.thepredict.domain.distill;

import java.util.List;
import java.util.Objects;

/**
 * Regime identity (M29.5, rule I22). The {@code r1} prefix is the regime version, not the reward.
 *
 * @param text canonical text
 */
public record RegimeKey(String text) {
    /** The single regime used when a profile has no partition. */
    public static final RegimeKey ALL = new RegimeKey("r1:ALL");

    /**
     * Checks the text.
     */
    public RegimeKey {
        Objects.requireNonNull(text, "text");
        if (text.isBlank() || text.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("regime key");
        }
    }

    /**
     * A key from band, phase and optional extra tokens. Extras never include memory.
     *
     * @param band enemy band
     * @param phase fight phase
     * @param extras zone, group or pattern tokens
     * @return the key
     */
    public static RegimeKey of(EnemyBand band, FightPhase phase, List<String> extras) {
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(phase, "phase");
        var body = new StringBuilder("r1:").append(band.name()).append(':').append(phase.name());
        for (var extra : extras) {
            if ("memory".equals(extra)) {
                throw new IllegalArgumentException("regime memory");
            }
            body.append(':').append(extra);
        }
        return new RegimeKey(body.toString());
    }
}
