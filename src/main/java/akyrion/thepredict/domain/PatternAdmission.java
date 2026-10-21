package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which pattern facts may enter a body or a signature (M26.7). {@code off} and {@code observe} drop every fact.
 * {@code full} keeps the active set, without cadence.
 */
public final class PatternAdmission {
    private PatternAdmission() {
    }

    /**
     * Facts admitted for one mode.
     *
     * @param mode effective mode
     * @param activeMask active pattern bits
     * @return an empty set unless the mode is {@code full} and a pattern is active
     */
    public static Set<EnemyPattern> forMode(PatternMode mode, int activeMask) {
        if (mode != PatternMode.FULL || activeMask == 0) {
            return Set.of();
        }
        var found = EnumSet.noneOf(EnemyPattern.class);
        for (var pattern : EnemyPattern.values()) {
            if (pattern != EnemyPattern.FIXED_CADENCE && (activeMask & pattern.bit()) != 0) {
                found.add(pattern);
            }
        }
        return Set.copyOf(found);
    }
}
