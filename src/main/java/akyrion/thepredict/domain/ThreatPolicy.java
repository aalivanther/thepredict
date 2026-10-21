package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Enriched threat (M11): turns an observed fact into a change of MythicMobs threat, in the points of its threat
 * table, and limits the gain of one participant per window. Pure and stateless; time enters as parameters.
 *
 * <p>The facts are {@code ALLY_ASSISTED} (magnitude: other participants assisted in one event, weight
 * {@code assistsOthers}), {@code MELEE_BLOCKED} (magnitude: blocked hits, weight {@code blockedHits}) and
 * {@code RANGED_HIT_ON_MOB} by a player who is not the mob's target (magnitude: final damage, weight
 * {@code rangedHarass}, on top of the threat MythicMobs already adds for damage taken). Every other event type
 * changes nothing. A target that stays unreachable loses {@code unreachableDecayPerSecond} points per second, a
 * negative change that the window does not limit, because MythicMobs never lowers a threat below zero.
 */
public final class ThreatPolicy {
    /** Length of a threat window in ticks, the {@code RESET_DAMAGE_STATUS_TIME} of the vanilla combat tracker. */
    public static final int WINDOW_TICKS = 100;
    /** Server ticks per second at the nominal rate. */
    public static final int TICKS_PER_SECOND = 20;

    private ThreatPolicy() {
    }

    /**
     * Computes the threat a fact adds: {@code weight × magnitude}, capped at {@code maxPerWindow} because the window
     * never admits more, so a huge damage or weight never overflows to infinity and a valid product below the cap is
     * exact.
     *
     * @param type kind of event
     * @param magnitude assisted participants, blocked hits or final damage; a negative or non-number magnitude is no
     *                  observation and changes nothing, and an infinite one gives the cap
     * @param weights weights of the mob's profile
     * @return the change, finite and within {@code [0, maxPerWindow]}; {@code 0} for another event type, a zero weight
     *         or a zero magnitude
     */
    public static double change(EventType type, double magnitude, ThreatWeights weights) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(weights, "weights");
        var weight = switch (type) {
            case ALLY_ASSISTED -> weights.assistsOthers();
            case MELEE_BLOCKED -> weights.blockedHits();
            case RANGED_HIT_ON_MOB -> weights.rangedHarass();
            case GROUP_ASSIGNMENT -> 1.0;
            default -> 0.0;
        };
        if (weight == 0.0 || !(magnitude > 0.0)) {
            return 0.0;
        }
        return Math.min(weight * magnitude, weights.maxPerWindow());
    }

    /**
     * Computes the threat an unreachable target loses between two consecutive decisions.
     *
     * @param elapsedTicks ticks since the previous decision that saw it unreachable, {@code >= 0}
     * @param weights weights of the mob's profile
     * @return the change, finite and {@code <= 0}; {@code 0} when the decay weight is zero
     * @throws IllegalArgumentException if {@code elapsedTicks < 0}
     */
    public static double unreachableDecay(long elapsedTicks, ThreatWeights weights) {
        Objects.requireNonNull(weights, "weights");
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be >= 0, was " + elapsedTicks);
        }
        var loss = Math.min(weights.unreachableDecayPerSecond() * elapsedTicks / TICKS_PER_SECOND, Double.MAX_VALUE);
        return loss == 0.0 ? 0.0 : -loss;
    }

    /**
     * Limits a gain to what is left of the window: {@code min(change, maxPerWindow - accumulated)}.
     *
     * @param change gain the fact would add, {@code >= 0}
     * @param accumulated points already added in the window, {@code >= 0}
     * @param maxPerWindow cap of the window
     * @return the admitted gain; {@code 0} when nothing is left, which counts as limited
     * @throws IllegalArgumentException if a value is negative or not finite
     */
    public static double admitted(double change, double accumulated, double maxPerWindow) {
        DomainChecks.requireNonNegative(change, "change");
        DomainChecks.requireNonNegative(accumulated, "accumulated");
        DomainChecks.requireNonNegative(maxPerWindow, "maxPerWindow");
        var left = Math.min(change, maxPerWindow - accumulated);
        return left > 0.0 ? left : 0.0;
    }
}
