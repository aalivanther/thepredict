package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.RewardSpec;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * At most 32 open windows of one session (M28.2). Overflow closes the oldest with credit 0
 * ({@link LearnCause#CREDIT_TRUNCATED}); the dropped window keeps its effect, its avoided damage and its source
 * ({@link CreditOverflow}). Terminal credit is applied only when the encounter closes. Each window carries its own
 * {@link TrainingSource}, and the week of every row of the overloads with a source is {@link ValueStatKey#week(long)}
 * of the wall time, never a caller constant.
 *
 * <p>The overloads without a source are legacy entry points for callers that do not pass one yet. Their windows take
 * the token the legacy close supplies, a dropped one keeps {@link LearnCause#CREDIT_TRUNCATED} as its token, and
 * every legacy row keeps the week the caller passes. A legacy caller passes week 0 with a source it does not know,
 * and the value build ignores week 0, so those rows stay out of training until the caller moves to the overloads
 * with a source.
 */
public final class CreditLedger {
    /** Cap from the default of {@code learning.maxOpenWindowsPerSession}. */
    public static final int CAP = 32;
    /** Token of a legacy window closed through {@link #onEncounterClosed(String, double, long)}. Never trained. */
    public static final String UNKNOWN_SOURCE = "unknown";

    private final String[] tactics = new String[CAP];
    private final String[] contexts = new String[CAP];
    private final double[] effects = new double[CAP];
    private final double[] avoided = new double[CAP];
    private final boolean[] ask = new boolean[CAP];
    private final boolean[] agree = new boolean[CAP];
    private final TrainingSource[] sources = new TrainingSource[CAP];
    private final CreditOverflow overflow = new CreditOverflow();
    private int size;
    private boolean truncated;
    private double creditShare = LearningSettings.DEFAULT.creditShare();
    private double decay = LearningSettings.DEFAULT.creditDecay();

    /**
     * Creates an empty ledger.
     */
    public CreditLedger() {
    }

    /**
     * Installs {@code learning.creditShare} and {@code learning.creditDecayPerWindow}.
     *
     * @param share weight of deferred credit
     * @param perWindow decay per window
     */
    public void useRates(double share, double perWindow) {
        creditShare = share;
        decay = perWindow;
    }

    /**
     * Legacy entry point: records a closed window without its source.
     *
     * @param tactic tactic id
     * @param context context text
     * @param effect window effect
     * @param avoidedDamage avoided-damage term
     * @param askedJev whether Jev was asked
     * @param agreed whether Jev matched the learned leader
     */
    public void onWindowClosed(String tactic, String context, int effect, double avoidedDamage, boolean askedJev,
                               boolean agreed) {
        record(tactic, context, effect, avoidedDamage, askedJev, agreed, null);
    }

    /**
     * Legacy entry point: records a closed window without its source. The effect stays fractional.
     *
     * @param tactic tactic id
     * @param context context text
     * @param effect window effect
     * @param avoidedDamage avoided-damage term
     * @param askedJev whether Jev was asked
     * @param agreed whether Jev matched the learned leader
     */
    public void onWindowClosed(String tactic, String context, double effect, double avoidedDamage, boolean askedJev,
                               boolean agreed) {
        record(tactic, context, effect, avoidedDamage, askedJev, agreed, null);
    }

    /**
     * Records a closed window with its source. The oldest is dropped when the cap is full.
     *
     * @param tactic tactic id
     * @param context context text
     * @param effect window effect
     * @param avoidedDamage avoided-damage term
     * @param askedJev whether the decision asked Jev
     * @param agreed whether Jev matched the learned leader; ignored when Jev was not asked
     * @param source where the window came from
     */
    public void onWindowClosed(String tactic, String context, double effect, double avoidedDamage, boolean askedJev,
                               boolean agreed, TrainingSource source) {
        record(tactic, context, effect, avoidedDamage, askedJev, agreed, Objects.requireNonNull(source, "source"));
    }

    /**
     * Closes the encounter: the dropped windows first, with credit 0, then one row per open window with its
     * deferred share of {@code terminal}. Clears the ledger.
     *
     * @param profile profile id
     * @param terminal mixed terminal credit
     * @param epochMs wall time supplied by the adapter
     * @return the deltas
     */
    public List<ValueStatDelta> onEncounterClosed(String profile, double terminal, long epochMs) {
        var week = ValueStatKey.week(epochMs);
        var rows = new ArrayList<ValueStatDelta>(size + overflow.keys());
        rows.addAll(overflow.drain(profile, UNKNOWN_SOURCE, week, creditShare, epochMs));
        rows.addAll(openRows(profile, UNKNOWN_SOURCE, week, terminal, epochMs));
        return List.copyOf(rows);
    }

    /**
     * Legacy entry point: one delta per open window, then clears the open windows. A window without a source takes
     * {@code source}, and every row keeps the caller's {@code week}.
     *
     * @param profile profile id
     * @param source token of the windows recorded without a source
     * @param week week index the caller supplies
     * @param terminal mixed terminal credit
     * @param epochMs wall time supplied by the adapter
     * @return the deltas
     */
    public List<ValueStatDelta> onEncounterClosed(String profile, String source, int week, double terminal,
                                                  long epochMs) {
        return List.copyOf(openRows(profile, source, week, terminal, epochMs));
    }

    /**
     * Whether a window was dropped since the last close.
     *
     * @return {@code true} after an overflow
     */
    public boolean truncated() {
        return truncated;
    }

    /**
     * Legacy entry point: closes every dropped window with credit 0. A window without a source keeps
     * {@link LearnCause#CREDIT_TRUNCATED} as its token, and every row keeps the caller's {@code week}.
     *
     * @param profile profile id
     * @param week week index the caller supplies
     * @param epochMs wall time supplied by the adapter
     * @return the truncated rows
     */
    public List<ValueStatDelta> truncatedRows(String profile, int week, long epochMs) {
        return List.copyOf(overflow.drain(profile, LearnCause.CREDIT_TRUNCATED.name(), week, creditShare, epochMs));
    }

    /**
     * Drains the session into the final batch with terminal credit 0. Every window keeps its own source.
     *
     * @param profile profile id
     * @param epochMs wall time supplied by the adapter
     * @return truncated rows, then the open windows
     */
    public List<ValueStatDelta> onShutdown(String profile, long epochMs) {
        return onEncounterClosed(profile, 0.0, epochMs);
    }

    /**
     * Legacy entry point: drains the session into the final batch with terminal credit 0. A window without a source
     * keeps {@code SHUTDOWN} as its token, and every row keeps the caller's {@code week}.
     *
     * @param profile profile id
     * @param week week index the caller supplies
     * @param epochMs wall time supplied by the adapter
     * @return truncated rows, then the open windows
     */
    public List<ValueStatDelta> onShutdown(String profile, int week, long epochMs) {
        var rows = new ArrayList<ValueStatDelta>();
        rows.addAll(truncatedRows(profile, week, epochMs));
        rows.addAll(onEncounterClosed(profile, "SHUTDOWN", week, 0.0, epochMs));
        return List.copyOf(rows);
    }

    private void record(String tactic, String context, double effect, double avoidedDamage, boolean askedJev,
                        boolean agreed, TrainingSource source) {
        if (size == CAP) {
            dropOldest();
        }
        var slot = size++;
        tactics[slot] = tactic;
        contexts[slot] = context;
        effects[slot] = effect;
        avoided[slot] = avoidedDamage;
        ask[slot] = askedJev;
        agree[slot] = askedJev && agreed;
        sources[slot] = source;
    }

    private List<ValueStatDelta> openRows(String profile, String legacyToken, int week, double terminal,
                                          long epochMs) {
        var rows = new ArrayList<ValueStatDelta>(size);
        for (var index = 0; index < size; index++) {
            var credit = CreditMath.deferred(terminal, size - 1 - index, decay);
            var effect = CreditMath.effectTerm(effects[index]);
            var reward = CreditMath.reward(effect, credit, avoided[index], creditShare);
            var token = sources[index] == null ? legacyToken : sources[index].token();
            var key = new ValueStatKey(profile, tactics[index], contexts[index], token, RewardSpec.ID, week);
            rows.add(new ValueStatDelta(key, 1, reward, 0.0, effect, credit, avoided[index], ask[index] ? 1 : 0,
                    agree[index] ? 1 : 0, epochMs));
            sources[index] = null;
        }
        size = 0;
        truncated = false;
        return rows;
    }

    private void dropOldest() {
        truncated = true;
        overflow.add(tactics[0], contexts[0], sources[0], CreditMath.effectTerm(effects[0]), avoided[0], ask[0],
                agree[0]);
        for (var index = 1; index < size; index++) {
            tactics[index - 1] = tactics[index];
            contexts[index - 1] = contexts[index];
            effects[index - 1] = effects[index];
            avoided[index - 1] = avoided[index];
            ask[index - 1] = ask[index];
            agree[index - 1] = agree[index];
            sources[index - 1] = sources[index];
        }
        size--;
    }
}
