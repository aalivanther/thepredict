package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CoordinationPolicy;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.RegroupPolicy;
import akyrion.thepredict.domain.SessionState;

import java.util.List;
import java.util.Objects;

/**
 * Applies the intents of one answer after coordinating them in their encounter (M10.7). Package-private; server
 * thread only.
 *
 * <p>Members that already run an unexpired {@code RETREAT} intent and were not answered count against the cap of
 * {@link CoordinationPolicy#limitRetreats}. Each proposed intent, or its replacement, is then applied in encounter
 * order; a retreat left without a replacement plans locally without retreating. Applying schedules the ready step
 * on this tick, so the choice is tried through the one cast path of M19.4 with the bands that go ahead of it (M20.9):
 * an answer drained before the tick layer casts in this same tick, and one accepted after it, on the next tick
 * (M20.7). An exception while applying one session drops only that session to level 4.
 */
final class IntentCoordination {
    private final IntentControl control;
    private final FallbackStep fallback;

    IntentCoordination(IntentControl control, FallbackStep fallback) {
        this.control = Objects.requireNonNull(control, "control");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** Limits the simultaneous retreats, then applies each intent; the ready step casts it (M19.4, M20.9). */
    void apply(Encounter encounter, List<CoordinationPolicy.Candidate> candidates, List<MobSession> proposing,
               long tick, long nowNanos) {
        var retreatingOthers = 0;
        for (var member : encounter.memberSessions()) {
            if (!proposing.contains(member) && member.intent().filter(intent -> !intent.expired(nowNanos)
                    && intent.movement() == Movement.RETREAT).isPresent()) {
                retreatingOthers++;
            }
        }
        var keep = regroupKeeps(candidates, proposing);
        var adjusted = CoordinationPolicy.limitRetreats(candidates, encounter.memberCount(), retreatingOthers, tick,
                nowNanos, keep);
        for (var index = 0; index < proposing.size(); index++) {
            var session = proposing.get(index);
            var intent = adjusted.get(index);
            try {
                intent.ifPresentOrElse(chosen -> control.applyAnswered(session, chosen, tick),
                        () -> fallback.withoutRetreat(session, tick, nowNanos));
            } catch (RuntimeException exception) {
                if (session.state() == SessionState.ACTIVE) {
                    control.fail(session, tick);
                }
            }
        }
    }

    private static boolean[] regroupKeeps(List<CoordinationPolicy.Candidate> candidates, List<MobSession> proposing) {
        var keep = new boolean[candidates.size()];
        var health = new int[proposing.size()];
        for (var index = 0; index < proposing.size(); index++) {
            health[index] = healthRank(proposing.get(index));
        }
        for (var index = 0; index < proposing.size() && index < candidates.size(); index++) {
            var session = proposing.get(index);
            var plan = session.groupCopy().plan();
            if (plan.isPresent() && plan.get() == GroupPlan.FALL_BACK_AND_REGROUP
                    && candidates.get(index).proposed().movement() == Movement.RETREAT
                    && RegroupPolicy.retreats(index, health, session.groupCopy().regroupElapsed())) {
                keep[index] = true;
            }
        }
        return keep;
    }

    private static int healthRank(MobSession session) {
        var facts = session.lastMobFacts();
        if (facts.isEmpty()) {
            return 1;
        }
        var band = facts.get().health();
        if (band == HealthBand.HEALTHY) {
            return 3;
        }
        if (band == HealthBand.WOUNDED) {
            return 2;
        }
        return 1;
    }
}
