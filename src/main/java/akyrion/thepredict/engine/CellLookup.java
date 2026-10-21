package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.ValueEstimate;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one way the engine reads the learned value table (M28.3; LD-04, LD-13, LD-15). Every reader of the table
 * ({@code LearnedOrder}, {@code FallbackStep}, {@code VoiFill}, {@code DistillCapture} and the shadow hint of
 * {@code DecisionDispatcher}) asks here, so they all see the same cell: the one of the profile id, the tactic id and
 * the live context of the session ({@link WindowCredit#live}), with and without the pattern group a counter may
 * exploit now.
 *
 * <p>A table loaded from an artifact carries its name tables. A table frozen without them ({@code
 * ValueTableBuilder.build()}, which the legacy entries of {@code SessionLearning.stage} still install) can only be
 * read by code and by the packed context installed with it, so that path is kept for it (rule W5) and nothing else.
 */
final class CellLookup {
    private static final String NO_PATTERN = "NONE";

    private CellLookup() {
    }

    /**
     * The estimate of one tactic in the live cell of the session.
     *
     * @param session the member
     * @param tactic tactic id
     * @param minimum usable windows, compared by the caller with {@link ValueEstimate#weak(int)}
     * @return the estimate, {@link ValueEstimate#NONE} when the table has no cell
     */
    static ValueEstimate estimate(MobSession session, String tactic, int minimum) {
        var learning = session.learning();
        var table = learning.table();
        if (named(session, table)) {
            return table.lookup(session.profile().id(), tactic, live(session, true));
        }
        return table.lookup(learning.profileCode(), LearnedLocalPick.tacticCode(tactic), learning.contextFine(), 0L,
                minimum);
    }

    /**
     * The learned leader among tactics, read in the live cell with or without the pattern group.
     *
     * @param session the member
     * @param tactics candidate tactics, in order
     * @param withPattern whether the cell carries the pattern group a counter may exploit now
     * @param minimum usable windows
     * @param minSpreadMillis spread that may reorder
     * @param weights in-fight weight of each tactic, by position
     * @return the leader, or empty when no cell separates the tactics
     */
    static Optional<TacticId> lead(MobSession session, List<Tactic> tactics, boolean withPattern, int minimum,
                                   int minSpreadMillis, double[] weights) {
        var learning = session.learning();
        var table = learning.table();
        if (named(session, table)) {
            return LearnedLocalPick.lead(table, session.profile().id(), tactics, live(session, withPattern), minimum,
                    minSpreadMillis, learning.baseScoring(), Set.of(), weights);
        }
        var fine = withPattern ? learning.patternContext(learning.contextFine()) : learning.contextFine();
        return LearnedLocalPick.lead(table, learning.profileCode(), tactics, fine, minSpreadMillis,
                learning.baseScoring(), Set.of(), weights);
    }

    /**
     * The learned score of each tactic in the live cell, without the pattern group.
     *
     * @param session the member
     * @param tactics tactics, in order
     * @param minimum usable windows
     * @param minSpreadMillis spread that may reorder
     * @param weights in-fight weight of each tactic, by position
     * @return one score per tactic, in thousandths
     */
    static int[] scores(MobSession session, List<Tactic> tactics, int minimum, int minSpreadMillis,
                        double[] weights) {
        var learning = session.learning();
        var table = learning.table();
        if (named(session, table)) {
            return LearnedLocalPick.scoreMillis(table, session.profile().id(), tactics, live(session, false), minimum,
                    minSpreadMillis, learning.baseScoring(), Set.of(), weights);
        }
        return LearnedLocalPick.scoreMillis(table, learning.profileCode(), tactics, learning.contextFine(),
                minSpreadMillis, learning.baseScoring(), Set.of(), weights);
    }

    /**
     * Whether the cell with the pattern group differs from the cell without it, so a second read may move the pick.
     *
     * @param session the member
     * @return {@code true} when a pattern group is in the live cell
     */
    static boolean patternContextDiffers(MobSession session) {
        var learning = session.learning();
        if (named(session, learning.table())) {
            return !live(session, true).equals(live(session, false));
        }
        return learning.patternContext(learning.contextFine()) != learning.contextFine();
    }

    /**
     * The live cell of the session. Without the pattern the last segment of the v2 key is {@code NONE}, the key of
     * pattern code 0.
     *
     * @param session the member
     * @param withPattern whether the pattern group stays in the key
     * @return the key
     */
    static ContextKey live(MobSession session, boolean withPattern) {
        var key = WindowCredit.live(session);
        if (withPattern) {
            return key;
        }
        var text = key.text();
        var plain = text.substring(0, text.lastIndexOf(':') + 1) + NO_PATTERN;
        return ContextKey.parse(plain).orElse(key);
    }

    private static boolean named(MobSession session, ValueTable table) {
        return table.profileIndex(session.profile().id()) >= 0;
    }
}
