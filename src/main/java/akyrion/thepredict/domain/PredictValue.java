package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Closed set of labels that describe what a MythicMobs skill does, declared by the author in the
 * skill's {@code PredictValues} list. Immutable and safe on any thread.
 *
 * <p>The English texts sent to Jev are written in {@code adapter.jev} (01 §9).
 */
public enum PredictValue {
    /** Close-range offensive action. */
    MELEE_ATTACK,
    /** Long-range offensive action. */
    RANGED_ATTACK,
    /** Protective action. */
    DEFENSE,
    /** Affects one target. */
    SINGLE_TARGET,
    /** Affects several targets. */
    AREA_TARGET,
    /** Small direct damage. */
    LOW_DAMAGE,
    /** Moderate direct damage. */
    MEDIUM_DAMAGE,
    /** Large direct damage. */
    HIGH_DAMAGE,
    /** Applies poison. */
    APPLIES_POISON,
    /** Applies the Bad Omen status. */
    APPLIES_BAD_OMEN,
    /** Restores the caster's health. */
    HEALS_CASTER,
    /** Creates an allied mob. */
    SUMMONS_ALLY,
    /** Damage that a raised shield does not block. */
    IGNORES_SHIELD;

    /**
     * Converts skill metadata into labels without distinguishing case.
     *
     * @param values label names from the MythicMobs skill configuration
     * @return immutable non-empty set of labels
     * @throws IllegalArgumentException if an entry is unknown or the list is empty
     */
    public static Set<PredictValue> parse(List<String> values) {
        Objects.requireNonNull(values, "values");
        var parsed = EnumSet.noneOf(PredictValue.class);
        for (var value : values) {
            try {
                parsed.add(PredictValue.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown PredictValues entry: " + value, exception);
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("PredictValues requires at least one entry");
        }
        return Set.copyOf(parsed);
    }
}
