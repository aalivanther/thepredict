package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Trend;

/**
 * Retreat only from a pursuer (segunda ronda de efectividad, E2-2). A retreat gives up the mob's damage to take less,
 * and it takes less only while it keeps a rival that comes at it out of its reach: at the same speed, a chaser never
 * closes the gap, and its melee hits and close guard stay out of play. A rival that holds its distance or backs away
 * loses nothing when the mob runs: it keeps hitting with what reaches the retreat distance and gets free time to heal
 * and recover, so the race is lost while running. Out of melee, the local chain therefore retreats only from a target
 * that the last capture saw closing in; in melee, and without a capture, the author's retreat stands. Pure apart from
 * the session it reads; no allocation.
 */
final class Pursuit {
    private Pursuit() {
    }

    /**
     * Whether a retreat would run from a target that is not pursuing: the last capture has a target that is out of the
     * melee band and not closing in.
     *
     * @param session the mob
     * @return whether the local chain should not retreat now
     */
    static boolean absent(MobSession session) {
        var facts = session.lastMobFacts().orElse(null);
        if (facts == null || facts.target().isEmpty()) {
            return false;
        }
        var target = facts.target().get();
        return target.distance() != DistanceBand.MELEE && target.trend() != Trend.CLOSING;
    }
}
