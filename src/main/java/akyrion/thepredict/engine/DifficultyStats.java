package akyrion.thepredict.engine;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Where the fights the slow loop of M22 judges go (M22.6, JS point 7): one {@code m22_difficulty_stats} delta per
 * fight in player play with {@code Learn} on. A fight that counts adds its log time to kill, and a mob death with a
 * valid hit counts as censored; a fight that went quiet, or a mob death without evidence, is excluded. Only a counted
 * fight carries the epoch of its start-rung estimate, so an excluded one never replaces the estimate a counted fight
 * stored: the merge keeps the pair of the later epoch. {@link #NONE} drops every row. Server thread only.
 */
public final class DifficultyStats {
    /** Drops every row, the behaviour before the store is wired. */
    public static final DifficultyStats NONE = new DifficultyStats(rows -> {
    }, () -> 0L);

    private final Consumer<List<DifficultyStatDelta>> sink;
    private final LongSupplier epochMs;

    /**
     * Rows go to {@code sink}, the aggregate cache in production, stamped with the wall time of the adapter.
     *
     * @param sink where the rows go, for example {@code AggregateCache::acceptDifficulty}
     * @param epochMs wall time supplied by the adapter
     */
    public DifficultyStats(Consumer<List<DifficultyStatDelta>> sink, LongSupplier epochMs) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.epochMs = Objects.requireNonNull(epochMs, "epochMs");
    }

    /**
     * One fight the slow loop learned from.
     *
     * @param profile profile id
     * @param context {@code v1:} context of the fight
     * @param rung rung the fight was played at, its time-weighted mean
     * @param logTtk log of the time to kill, observed or censored
     * @param censored whether the mob died and the time is projected from its last valid hit
     * @param startRung the start-rung estimate after the fight
     */
    void counted(String profile, String context, int rung, double logTtk, boolean censored, double startRung) {
        var epoch = epochMs.getAsLong();
        sink.accept(List.of(new DifficultyStatDelta(profile, context, rung, 1, logTtk, 0.0, censored ? 1 : 0, 0,
                startRung, epoch, epoch, epoch)));
    }

    /**
     * One fight the slow loop does not learn from.
     *
     * @param profile profile id
     * @param context {@code v1:} context of the fight
     * @param rung rung the fight was played at, its time-weighted mean
     * @param startRung the start-rung estimate, unchanged by this fight
     */
    void excluded(String profile, String context, int rung, double startRung) {
        var epoch = epochMs.getAsLong();
        sink.accept(List.of(new DifficultyStatDelta(profile, context, rung, 0, 0.0, 0.0, 0, 1, startRung, 0L, epoch,
                epoch)));
    }
}
