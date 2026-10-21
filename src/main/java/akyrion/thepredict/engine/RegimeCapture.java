package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightSummary;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.FightRegime;
import akyrion.thepredict.engine.distill.GroupRegimeClock;

/**
 * Regime of one capture (M29.5). A group changes only on the replan tick. {@code planEpoch} is not touched. A tell in
 * flight holds the regime; a closed tell does not, although its shown length stays on the session until the next
 * generation.
 */
public final class RegimeCapture {
    private static final int LATE_SECONDS = 60;

    private RegimeCapture() {
    }

    /**
     * Offers the observed regime and applies it when the dwell and the group clock allow.
     *
     * @param session the member
     * @param encounter the encounter
     * @param tick the capture tick
     * @param settings the distill section
     * @param tap where the regime line is written
     */
    public static void consider(MobSession session, Encounter encounter, long tick, DistillSettings settings,
                                DecisionTap tap) {
        var state = session.learning().distillSession();
        var selector = state.selector(settings.dwellTicks(), settings.maxSwitchesPerEncounter());
        var regime = FightRegime.of(Math.max(1, encounter.memberCount()), elapsed(session), settings.openingSeconds(),
                LATE_SECONDS);
        selector.offer(regime.key(), tick);
        selector.telegraph(session.tellOpen());
        var group = encounter.memberCount() >= 2;
        var age = (int) Math.max(0L, tick - state.planStarted());
        var books = session.groupBooks();
        var due = !group || books != null && GroupRegimeClock.due(tick, books.caps().replanPeriodTicks(), age,
                books.caps().minPlanTicks());
        var from = selector.current().text();
        var to = regime.key().text();
        if (!from.equals(to) && session.tellOpen()) {
            tap.regimeChanged(session.key(), from, to, "CAPTURE", settings.dwellTicks(), "TELEGRAPH");
            return;
        }
        if (!from.equals(to) && group && !due) {
            tap.regimeChanged(session.key(), from, to, "CAPTURE", settings.dwellTicks(), "PLAN_HOLD");
            return;
        }
        if (selector.onCapture(tick)) {
            state.lastReplan(tick);
            tap.regimeChanged(session.key(), from, selector.current().text(), "CAPTURE", settings.dwellTicks(), "");
        }
    }

    private static int elapsed(MobSession session) {
        var clock = session.difficulty().clock();
        if (!clock.fought()) {
            return 0;
        }
        return clock.summary().map(FightSummary::elapsedTicks).orElse(0) / 20;
    }
}
