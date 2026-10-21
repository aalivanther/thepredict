package akyrion.thepredict.domain;

/**
 * Hysteresis of the pattern phrases sent to Jev (M26.7). A fact turns on after {@code factOnTicks} of
 * {@code ACTIVE} and off after {@code factOffTicks} without it. {@code RESTING} clears it at once. The same fact
 * changes at most once per {@code max(factOnTicks, 80)} ticks, and one participant changes its facts at most once per
 * {@code factOnTicks}.
 */
public final class PatternFactFilter {
    private static final int SAME_FACT_GAP = 80;
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();
    private int shown;
    private int participantChangedAt = Integer.MIN_VALUE / 2;
    private final int[] activeFor = new int[EnemyPattern.values().length];
    private final int[] absentFor = new int[EnemyPattern.values().length];
    private final int[] changedAt = new int[EnemyPattern.values().length];

    /** A filter with no phrase on. */
    public PatternFactFilter() {
        for (var index = 0; index < changedAt.length; index++) {
            changedAt[index] = Integer.MIN_VALUE / 2;
        }
    }

    /**
     * Advances one tick.
     *
     * @param activeMask patterns in {@code ACTIVE}
     * @param tick the server tick
     * @param onTicks ticks required to publish
     * @param offTicks ticks required to withdraw
     * @param restingMask patterns in {@code RESTING}
     * @return the mask that may be sent
     */
    public int update(int activeMask, int tick, int onTicks, int offTicks, int restingMask) {
        for (var pattern : PATTERNS) {
            if (pattern == EnemyPattern.FIXED_CADENCE) {
                continue;
            }
            var bit = pattern.bit();
            if ((restingMask & bit) != 0) {
                activeFor[pattern.ordinal()] = 0;
                forceHide(pattern, tick);
                continue;
            }
            if ((activeMask & bit) != 0) {
                activeFor[pattern.ordinal()]++;
                absentFor[pattern.ordinal()] = 0;
                if (activeFor[pattern.ordinal()] >= onTicks) {
                    show(pattern, tick, onTicks);
                }
            } else {
                absentFor[pattern.ordinal()]++;
                activeFor[pattern.ordinal()] = 0;
                if (absentFor[pattern.ordinal()] >= offTicks) {
                    hide(pattern, tick, onTicks);
                }
            }
        }
        return shown;
    }

    /**
     * Facts that are currently sent.
     *
     * @return the bit mask
     */
    public int shown() {
        return shown;
    }

    private void show(EnemyPattern pattern, int tick, int gap) {
        var bit = pattern.bit();
        if ((shown & bit) != 0 || !gapOpen(pattern, tick, gap)) {
            return;
        }
        shown |= bit;
        stamp(pattern, tick);
    }

    private void forceHide(EnemyPattern pattern, int tick) {
        var bit = pattern.bit();
        if ((shown & bit) == 0) {
            return;
        }
        shown &= ~bit;
        changedAt[pattern.ordinal()] = tick;
    }

    private void hide(EnemyPattern pattern, int tick, int gap) {
        var bit = pattern.bit();
        if ((shown & bit) == 0 || !gapOpen(pattern, tick, gap)) {
            return;
        }
        shown &= ~bit;
        stamp(pattern, tick);
    }

    private boolean gapOpen(EnemyPattern pattern, int tick, int gap) {
        return tick - changedAt[pattern.ordinal()] >= Math.max(gap, SAME_FACT_GAP)
                && tick - participantChangedAt >= gap;
    }

    private void stamp(EnemyPattern pattern, int tick) {
        changedAt[pattern.ordinal()] = tick;
        participantChangedAt = tick;
    }
}
