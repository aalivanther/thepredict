package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.AgreementStatDelta;
import akyrion.thepredict.domain.distill.DistillMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Distill state of one session (M29). Held beside the learning fields so the session class does not grow.
 */
public final class DistillSession {
    private final DistillGovernor governor = new DistillGovernor();
    private final AgreementBook agreements = new AgreementBook();
    private RegimeSelector selector;
    private long lastReplan;
    private long planStarted;
    private int samples;
    private int cusumH = 5;
    private final LinkedHashMap<String, Integer> tactics = new LinkedHashMap<>();
    private DistillTrace trace = DistillTrace.EMPTY;
    private String patternFactsJson = "";
    private TeachingBoard board = TeachingBoard.NONE;
    private Consumer<List<AgreementStatDelta>> agreementSink = rows -> {
    };
    private DistillMode verdict = DistillMode.OFF;

    /**
     * Starts unpublished and off.
     */
    public DistillSession() {
    }

    /**
     * The governor of this session.
     *
     * @return the governor
     */
    public DistillGovernor governor() {
        return governor;
    }

    /**
     * The agreement book.
     *
     * @return the book
     */
    public AgreementBook agreements() {
        return agreements;
    }

    /**
     * The last capture trace.
     *
     * @return the trace
     */
    public DistillTrace trace() {
        return trace;
    }

    /**
     * Stores the capture trace.
     *
     * @param next the trace
     */
    public void trace(DistillTrace next) {
        trace = next;
    }

    /**
     * Pattern facts JSON of the last capture.
     *
     * @return the array, or empty
     */
    public String patternFactsJson() {
        return patternFactsJson;
    }

    /**
     * Stores the pattern facts JSON.
     *
     * @param json the array, or empty
     */
    public void patternFactsJson(String json) {
        patternFactsJson = json;
    }

    /**
     * The regime selector, created on the first capture.
     *
     * @param dwellTicks dwell
     * @param maxSwitches switch cap
     * @return the selector
     */
    public RegimeSelector selector(int dwellTicks, int maxSwitches) {
        if (selector == null) {
            selector = new RegimeSelector(dwellTicks, maxSwitches);
        }
        return selector;
    }

    /**
     * The selector, if a capture has created it.
     *
     * @return the selector, or null
     */
    public RegimeSelector selectorOrNull() {
        return selector;
    }

    /**
     * Tick of the previous regime offer that was applied.
     *
     * @return the tick
     */
    public long lastReplan() {
        return lastReplan;
    }

    /**
     * Stores the replan tick. It does not touch {@code planEpoch}.
     *
     * @param tick the tick
     */
    public void lastReplan(long tick) {
        lastReplan = tick;
    }

    /**
     * Tick the current plan started.
     *
     * @return the tick
     */
    public long planStarted() {
        return planStarted;
    }

    /**
     * Stores the tick the current group plan started. It does not touch {@code planEpoch}.
     *
     * @param tick the adoption tick
     */
    public void notePlan(long tick) {
        planStarted = tick;
    }

    /**
     * Samples already offered to the audit draw.
     *
     * @return the count
     */
    public int samples() {
        return samples;
    }

    /**
     * Counts one audit or delegated capture so the next draw advances.
     */
    public void noteSample() {
        samples++;
    }

    /**
     * Stores the CUSUM threshold from the distill section.
     *
     * @param threshold {@code distill.cusumH}
     */
    public void cusumH(int threshold) {
        cusumH = threshold;
    }

    /**
     * Counts one adopted student tactic for the entropy ratio.
     *
     * @param tactic tactic id
     */
    public void noteTactic(String tactic) {
        if (!tactic.isEmpty()) {
            tactics.merge(tactic, 1, Integer::sum);
        }
    }

    /**
     * Share of the most adopted tactic, the input of the governor's entropy alarm (M22 {@code TACTIC_ENTROPY_RATIO}:
     * the alarm fires when one tactic passes 80 percent). No adoption is zero.
     *
     * @return the share, from 0 to 1
     */
    public double dominantShare() {
        var total = 0;
        var top = 0;
        for (var count : tactics.values()) {
            total += count;
            top = Math.max(top, count);
        }
        return total <= 0 ? 0.0 : top / (double) total;
    }

    /**
     * Normalised Shannon entropy of the adopted tactics, the reported metric. One tactic is zero. The alarm reads
     * {@link #dominantShare()}, not this ratio.
     *
     * @return the ratio, from 0 to 1
     */
    public double tacticEntropy() {
        var total = 0;
        for (var count : tactics.values()) {
            total += count;
        }
        if (total <= 0 || tactics.size() <= 1) {
            return 0.0;
        }
        var entropy = 0.0;
        for (var count : tactics.values()) {
            var share = count / (double) total;
            entropy -= share * StrictMath.log(share);
        }
        var widest = StrictMath.log(tactics.size());
        return widest == 0.0 ? 0.0 : entropy / widest;
    }

    /**
     * Binds the teaching board of the plugin.
     *
     * @param teaching the board
     */
    public void bind(TeachingBoard teaching) {
        board = java.util.Objects.requireNonNull(teaching, "teaching");
    }

    /**
     * Where agreement rows go. Production passes {@code AggregateCache.acceptAgreements}.
     *
     * @param sink the sink
     */
    public void useAgreements(Consumer<List<AgreementStatDelta>> sink) {
        agreementSink = sink;
    }

    /**
     * The verdict ceiling loaded for this session.
     *
     * @return the ceiling
     */
    public DistillMode verdict() {
        return verdict;
    }

    /**
     * Stores the verdict ceiling.
     *
     * @param ceiling the ceiling
     */
    public void verdict(DistillMode ceiling) {
        verdict = ceiling;
    }

    /**
     * Closes one teaching window and emits a shadow agreement row.
     *
     * @param profile profile id
     * @param mob mob id
     * @param effect window effect
     * @param epochMs wall time supplied by the adapter
     */
    public void emitWindow(String profile, String mob, double effect, long epochMs) {
        board.closeMob(mob, epochMs);
        var rows = agreements.onWindow(profile, effect, epochMs);
        if (!rows.isEmpty()) {
            agreementSink.accept(rows);
        }
    }

    /**
     * Emits one outcome delta per paired audit window.
     *
     * @param profile profile id
     * @param epochMs wall time supplied by the adapter
     */
    public void emitAudit(String profile, long epochMs) {
        var rows = agreements.auditClose(profile, epochMs);
        if (!rows.isEmpty()) {
            agreementSink.accept(rows);
            for (var row : rows) {
                if (row.n() > 0) {
                    governor.audit(row.sumDelta(), cusumH);
                }
            }
        }
    }
}
