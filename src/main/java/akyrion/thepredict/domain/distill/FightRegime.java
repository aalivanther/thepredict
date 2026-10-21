package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * The regime of one capture (M29.5).
 *
 * @param band living opponents
 * @param phase time band
 * @param extras extra dimension tokens, never memory
 */
public record FightRegime(EnemyBand band, FightPhase phase, List<String> extras) {
    /**
     * Copies the extras.
     */
    public FightRegime {
        extras = List.copyOf(extras);
    }

    /**
     * A regime with no extra dimensions.
     *
     * @param living living opponents
     * @param elapsedSeconds seconds since the fight opened
     * @param openingSeconds opening length
     * @param lateSeconds late threshold
     * @return the regime
     */
    public static FightRegime of(int living, int elapsedSeconds, int openingSeconds, int lateSeconds) {
        return new FightRegime(EnemyBand.of(living), FightPhase.of(elapsedSeconds, openingSeconds, lateSeconds),
                List.of());
    }

    /**
     * The canonical key.
     *
     * @return the key
     */
    public RegimeKey key() {
        return RegimeKey.of(band, phase, extras);
    }
}
