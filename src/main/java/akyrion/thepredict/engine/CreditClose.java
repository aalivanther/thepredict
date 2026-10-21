package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.FightSummary;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.CreditSnapshot;
import akyrion.thepredict.domain.learn.ValueStatDelta;

import java.util.List;

/**
 * Closes credit windows from the fight clocks (M28.2). The clocks are read once into a snapshot.
 */
public final class CreditClose {
    private CreditClose() {
    }

    /**
     * Closes the open windows and the encounter: the dropped windows first with credit 0, then one row per open
     * window with its deferred share of the terminal credit. Every row keeps the source its window recorded
     * ({@code TrainingSource}, M28.2) and the week of {@code epochMs}; a paced fight against a player is stored and
     * never trains (A10).
     *
     * @param session the member
     * @param settings learning rates
     * @param mode group mode of the encounter
     * @param epochMs wall time supplied by the adapter
     * @return rows for {@code m28_value_stats}
     */
    public static List<ValueStatDelta> finish(MobSession session, LearningSettings settings, GroupMode mode,
                                              long epochMs) {
        session.learning().forgetWeights(session.learning().training());
        var ledger = session.learning().credit();
        ledger.useRates(settings.creditShare(), settings.creditDecay());
        var terminal = CreditMath.terminal(snapshot(session, settings, mode));
        return ledger.onEncounterClosed(session.profile().id(), terminal, epochMs);
    }

    /**
     * Drains a session into the shutdown batch. Terminal credit is 0 and every window keeps its own source.
     *
     * @param session the session
     * @param epochMs wall time supplied by the adapter
     * @return the rows
     */
    public static List<ValueStatDelta> shutdown(MobSession session, long epochMs) {
        return session.learning().credit().onShutdown(session.profile().id(), epochMs);
    }

    private static CreditSnapshot snapshot(MobSession session, LearningSettings settings, GroupMode mode) {
        var clock = session.difficulty().clock();
        var elapsed = clock.summary().map(FightSummary::elapsedTicks).orElse(0);
        if (!clock.fought()) {
            elapsed = 0;
        }
        var reference = session.difficulty().targetSeconds() * 20;
        var grouped = false;
        var allDead = false;
        var won = false;
        var groupElapsed = 0;
        var group = session.groupClock();
        if (group.isPresent() && group.get().members() >= 2 && mode != GroupMode.OFF) {
            grouped = true;
            var summary = group.get().summary();
            groupElapsed = summary.map(FightSummary::elapsedTicks).orElse(0);
            var kind = summary.map(FightSummary::kind).orElse(null);
            allDead = kind == FightEventKind.GROUP_WIPED;
            won = kind == FightEventKind.TARGET_DIED;
        }
        return new CreditSnapshot(clock.mobHasDied(), clock.targetDied(), elapsed, reference, grouped, allDead, won,
                groupElapsed, settings.groupCreditShare());
    }
}
