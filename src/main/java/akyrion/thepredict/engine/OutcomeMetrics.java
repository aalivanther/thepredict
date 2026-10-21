package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Totals of the closed outcome windows (M16.8) by {@link ControlLevel} × {@link Movement}: 4 × 6 fixed cells that count
 * windows and sum damage dealt and taken, effect, reached goals, casts and hits (rule A5). Server thread only.
 */
public final class OutcomeMetrics {
    private static final int LEVELS = ControlLevel.values().length;
    private static final int MOVEMENTS = Movement.values().length;

    private final long[] windows = new long[LEVELS * MOVEMENTS];
    private final long[] reached = new long[LEVELS * MOVEMENTS];
    private final long[] casts = new long[LEVELS * MOVEMENTS];
    private final long[] hits = new long[LEVELS * MOVEMENTS];
    private final double[] dealt = new double[LEVELS * MOVEMENTS];
    private final double[] taken = new double[LEVELS * MOVEMENTS];
    private final double[] effect = new double[LEVELS * MOVEMENTS];

    /** Creates empty totals. */
    public OutcomeMetrics() {
    }

    /**
     * Totals of the windows of one control level, for {@code /thepredict status}.
     *
     * @param level control level
     * @param windows closed windows
     * @param dealt mean damage dealt per window, in fractions of maximum health
     * @param taken mean damage taken per window
     * @param reachRate share of windows whose movement reached its goal
     * @param hitRate confirmed hits per cast, {@code 0} without casts
     * @param effect mean effect per window
     * @param casts casts of every window of the level
     * @param hits confirmed hits of every window of the level
     */
    public record LevelOutcomes(ControlLevel level, long windows, double dealt, double taken, double reachRate,
                                double hitRate, double effect, long casts, long hits) {
        /**
         * Validates the totals.
         *
         * @throws NullPointerException if the level is {@code null}
         */
        public LevelOutcomes {
            Objects.requireNonNull(level, "level");
        }
    }

    /**
     * Adds one closed window to its cell.
     *
     * @param summary closed window
     */
    public void record(OutcomeWindow.Summary summary) {
        var cell = summary.level().ordinal() * MOVEMENTS + summary.movement().ordinal();
        windows[cell]++;
        reached[cell] += summary.reached() ? 1 : 0;
        casts[cell] += summary.casts();
        hits[cell] += summary.hits();
        dealt[cell] += summary.dealt();
        taken[cell] += summary.taken();
        effect[cell] += summary.effect();
    }

    /**
     * Counts the windows of one cell.
     *
     * @param level control level
     * @param movement movement
     * @return closed windows
     */
    public long windows(ControlLevel level, Movement movement) {
        return windows[level.ordinal() * MOVEMENTS + movement.ordinal()];
    }

    /**
     * Returns the totals of every control level with at least one window, in the order of {@link ControlLevel}.
     *
     * @return one entry per level with windows
     */
    public List<LevelOutcomes> byLevel() {
        var levels = new ArrayList<LevelOutcomes>(LEVELS);
        for (var level : ControlLevel.values()) {
            long count = 0;
            long reach = 0;
            long cast = 0;
            long hit = 0;
            double dealtSum = 0;
            double takenSum = 0;
            double effectSum = 0;
            for (var movement = 0; movement < MOVEMENTS; movement++) {
                var cell = level.ordinal() * MOVEMENTS + movement;
                count += windows[cell];
                reach += reached[cell];
                cast += casts[cell];
                hit += hits[cell];
                dealtSum += dealt[cell];
                takenSum += taken[cell];
                effectSum += effect[cell];
            }
            if (count > 0) {
                levels.add(new LevelOutcomes(level, count, dealtSum / count, takenSum / count, (double) reach / count,
                        cast == 0 ? 0.0 : (double) hit / cast, effectSum / count, cast, hit));
            }
        }
        return levels;
    }
}
