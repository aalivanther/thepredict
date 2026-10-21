package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ExploitPolicy;

/**
 * Joins the exploit policy to one cast (M26.5). Observe never arms {@code EXPLOIT_PATTERN}. Entropy lifts the share
 * only with {@code PASS}.
 */
public final class PatternCast {
    private PatternCast() {
    }

    /**
     * Whether this session may arm {@link akyrion.thepredict.domain.Reaction#EXPLOIT_PATTERN}.
     *
     * @param session the session
     * @return {@code true} only in bias or full, with a live counter, and with {@code PASS} when entropy is on
     */
    public static boolean exploitReady(MobSession session) {
        return ExploitPolicy.allowsBias(session.patternMode(), session.entropyActive(), session.patternPass())
                && session.patternCounterActive();
    }

    /**
     * Whether the live cast may arm {@code EXPLOIT_PATTERN}. A share of zero never arms, even when
     * {@link #exploitReady} is true. The budget and a rest decide as well.
     *
     * @param ready {@link #exploitReady}
     * @param share the share already written on the session
     * @param budget the session budget
     * @param tick the server tick
     * @param windowTicks the quota window
     * @param resting whether a rest is open
     * @return whether the reaction may arm
     */
    public static boolean liveExploit(boolean ready, double share, ExploitBudget budget, int tick, int windowTicks,
                                      boolean resting) {
        return ready && share > 0.0 && budget.allows(tick, windowTicks, share) && !resting;
    }

    /**
     * The live arming check. The caller writes {@code patternShare} first.
     *
     * @param session the session
     * @param tick the server tick
     * @return whether {@code EXPLOIT_PATTERN} may arm
     */
    public static boolean liveExploit(MobSession session, int tick) {
        return liveExploit(exploitReady(session), session.patternShare(), session.exploitBudget(), tick,
                session.patternWindow(), session.patternResting(tick));
    }

    /**
     * The share this cast consults. Entropy with {@code PASS} uses the whole window. The gate is not lifted. The quota
     * is lifted against an entity only when the profile's {@code Patterns.AgainstEntities} is {@code free} (M26.6);
     * against a player it always holds.
     *
     * @param session the session
     * @return the share in {@code [0, 1]}
     */
    public static double share(MobSession session) {
        var lifted = ExploitPolicy.liftsExploitShare(session.entropyActive(), session.patternPass());
        var entityFree = session.profile().patterns().entityFree(session.playerTarget());
        return ExploitPolicy.share(session.patternQuota(), session.levers().exploitShare(), entityFree, lifted);
    }
}
