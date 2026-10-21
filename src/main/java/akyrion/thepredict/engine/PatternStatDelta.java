package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * One row of {@code m26_pattern_stats} inside an aggregate batch (M26.9). Chan fields use the same merge as
 * outcome stats. Nothing here is read on the tick.
 *
 * @param profile profile that observed the pattern
 * @param pattern pattern name
 * @param targetKind {@code player} or {@code entity}
 * @param mode pattern mode that was in force
 * @param encounters encounters with trials
 * @param activations transitions into {@code ACTIVE}
 * @param exploitedCasts motivated casts
 * @param exploitedLanded motivated casts that landed
 * @param counterWindows result windows of motivated casts
 * @param counterEffectMean Chan mean of the counter effect
 * @param counterEffectM2 Chan second moment
 * @param firstEpochMs first window
 * @param lastEpochMs last window
 */
public record PatternStatDelta(String profile, String pattern, String targetKind, String mode, long encounters,
                               long activations, long exploitedCasts, long exploitedLanded, long counterWindows,
                               double counterEffectMean, double counterEffectM2, long firstEpochMs, long lastEpochMs) {
    /**
     * Checks the identity fields.
     *
     * @throws NullPointerException if a name is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public PatternStatDelta {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(mode, "mode");
        if (encounters < 0 || activations < 0 || exploitedCasts < 0 || exploitedLanded < 0 || counterWindows < 0) {
            throw new IllegalArgumentException("pattern counts must be >= 0");
        }
    }
}
