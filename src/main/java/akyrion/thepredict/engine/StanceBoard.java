package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;

/**
 * Publishes posture, fury, the open answer and poise on the intent board (M23.8), and the fields other steps own: the
 * group copy (M25.11), the patterns of the target (M26.11, only in {@code bias} and {@code full}) and the zone facts
 * with the push danger under the mob (M24.8). Placeholders and conditions read only that board (T3). A republished
 * intent keeps what the same generation already showed ({@link IntentView#carrying}). A generation that does not
 * match, and has no intent, is left alone. Unchanged views are not replaced, and the checks allocate nothing while the
 * session is in no group.
 */
final class StanceBoard {
    private static final ZoneFact[] FACTS = ZoneFact.values();

    private StanceBoard() {
    }

    /**
     * Publishes when the visible stance, the enraged mark, the answer, poise, the hit tick, the group, the patterns or
     * the zone differs, and when the open intent's level, tactic, expiry or confidence differs.
     *
     * @param board the board
     * @param session the mob
     */
    static void sync(IntentBoard board, MobSession session) {
        var current = board.current(session.key().entityId());
        var intent = session.intent().orElse(null);
        if (current.generation() != session.key().generation()) {
            if (intent == null) {
                return;
            }
            board.publish(session.key(), compose(session, current, intent));
            return;
        }
        if (differs(session, current, intent)) {
            board.publish(session.key(), compose(session, current, intent));
        }
    }

    /**
     * Republishes only the group, pattern and zone fields when one of them changed (DAL-01). The tick layer calls it
     * once per visit, so a new token, role, plan, pattern or zone fact reaches the vocabulary within the tick. A view of
     * another generation is left alone.
     *
     * @param board the board
     * @param session the mob
     */
    static void syncShared(IntentBoard board, MobSession session) {
        var current = board.current(session.key().entityId());
        if (current.generation() != session.key().generation() || showsShared(session, current)) {
            return;
        }
        board.publish(session.key(), shared(session, current));
    }

    private static IntentView compose(MobSession session, IntentView current, TacticalIntent intent) {
        var basis = intent == null ? current : intent.toView().carrying(current);
        var source = intent == null ? current.movement() : intent.movement();
        var visible = CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
        return shared(session, basis).withPosture(StanceClock.published(session, source), visible,
                StanceState.enraged(session.stanceState()), (byte) session.poiseShown(),
                session.windupAnswerOrdinal(), impact(session));
    }

    private static IntentView shared(MobSession session, IntentView view) {
        var copy = session.groupCopy();
        return view.withGroup(roleOrdinal(copy), planOrdinal(copy), copy.tokenMask(), copy.squadSize(),
                        copy.slotIndex())
                .withPatterns(session.patternMode(), session.patternMask(), session.patternForecast())
                .withZone(zoneFacts(session), pushDanger(session));
    }

    private static boolean showsShared(MobSession session, IntentView view) {
        var copy = session.groupCopy();
        return view.showsGroup(roleOrdinal(copy), planOrdinal(copy), copy.tokenMask(), copy.squadSize(),
                copy.slotIndex())
                && view.showsPatterns(session.patternMode(), session.patternMask(), session.patternForecast())
                && view.zoneFacts() == zoneFacts(session) && view.pushDanger() == pushDanger(session);
    }

    private static boolean differs(MobSession session, IntentView current, TacticalIntent intent) {
        if (!showsShared(session, current)) {
            return true;
        }
        var source = intent == null ? current.movement() : intent.movement();
        if (StanceClock.published(session, source) != current.movement()) {
            return true;
        }
        var visible = CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
        if (visible != current.stance() || StanceState.enraged(session.stanceState()) != current.enraged()) {
            return true;
        }
        if ((byte) session.poiseShown() != current.poisePercent() || impact(session) != current.impactAtTick()) {
            return true;
        }
        if (!sameAnswer(current, session.windupAnswerOrdinal())) {
            return true;
        }
        return intent != null && (current.level() != intent.level() || !current.tactic().equals(intent.tactic())
                || current.expiresAtNanos() != intent.expiresAtNanos()
                || Float.compare(current.confidence(), (float) intent.confidence()) != 0);
    }

    /** Role ordinal of the copy, {@code -1} without one. Allocates only while a role is assigned. */
    private static int roleOrdinal(GroupCopy copy) {
        var role = copy.role();
        return role.isPresent() ? role.get().ordinal() : -1;
    }

    /** Plan ordinal of the copy, {@code -1} without one. Allocates only while a plan is published. */
    private static int planOrdinal(GroupCopy copy) {
        var plan = copy.plan();
        return plan.isPresent() ? plan.get().ordinal() : -1;
    }

    /**
     * Bits of the filtered zone facts the vocabulary may name (M24.8). They exist only in {@code full} with
     * {@code Zone.Facts}; a fact that stays inside the engine is never published (errata E24-1).
     */
    private static int zoneFacts(MobSession session) {
        var facts = session.lastMobFacts();
        if (facts.isEmpty() || facts.get().zoneFacts().isEmpty()) {
            return 0;
        }
        var held = facts.get().zoneFacts();
        var bits = 0;
        for (var fact : FACTS) {
            if (fact.exposed() && held.contains(fact)) {
                bits |= 1 << fact.ordinal();
            }
        }
        return bits;
    }

    /** The push danger under the mob from the signals of its last visit, {@code 0} without a zone. */
    private static byte pushDanger(MobSession session) {
        if (session.zoneMode() == ZoneMode.OFF) {
            return 0;
        }
        return (byte) TickSignals.push(session.tickSignals(), TickSignals.MOB_PUSH_NEAR);
    }

    private static boolean sameAnswer(IntentView current, int ordinal) {
        var published = current.answer();
        if (published.isEmpty()) {
            return ordinal < 0;
        }
        return ordinal == published.get().ordinal();
    }

    /** Hit tick while the tell is in wind-up. {@code -1} outside that action and when the deadline is unset. */
    private static long impact(MobSession session) {
        if (session.actionStance() != ActionStance.WINDUP) {
            return -1L;
        }
        var until = session.actionUntilTick();
        return until == Long.MAX_VALUE ? -1L : until;
    }
}
