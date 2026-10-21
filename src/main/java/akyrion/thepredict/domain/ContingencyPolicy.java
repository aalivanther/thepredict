package akyrion.thepredict.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rule that fires the contingencies prepared in an intent. Stateless, reads no clock, safe on
 * any thread.
 *
 * <p>{@code IF_OWN_HEALTH_CRITICAL} holds when the probe reads a {@code CRITICAL} health band and
 * has precedence; {@code IF_TARGET_IN_MELEE} holds when the probe reads a {@code MELEE} distance.
 * Each contingency is used once: {@link #fire} removes it from the intent it returns.
 */
public final class ContingencyPolicy {
    private static final List<Situation> ORDER =
            List.of(Situation.IF_OWN_HEALTH_CRITICAL, Situation.IF_TARGET_IN_MELEE);

    private ContingencyPolicy() {
    }

    /**
     * Returns the tactic of the first contingency whose situation holds.
     *
     * @param intent current intent
     * @param probe latest probe of the same mob session
     * @return the contingency tactic, or empty when no prepared situation holds
     * @throws IllegalArgumentException if the probe belongs to another session
     */
    public static Optional<TacticId> triggered(TacticalIntent intent, MobProbe probe) {
        requireSameMob(intent, probe);
        for (var situation : ORDER) {
            var tactic = intent.contingencies().get(situation);
            if (tactic != null && holds(situation, probe)) {
                return Optional.of(tactic);
            }
        }
        return Optional.empty();
    }

    /**
     * Fires the first contingency whose situation holds and whose tactic is eligible, producing an
     * {@code ALTERNATIVE} intent with the same deadline and without that contingency.
     *
     * @param intent current intent
     * @param probe latest probe of the same mob session
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick, the start of the new intent
     * @return the new intent, or empty when nothing fires
     * @throws IllegalArgumentException if the probe belongs to another session
     */
    public static Optional<TacticalIntent> fire(TacticalIntent intent, MobProbe probe, Set<TacticId> eligibleNow,
                                                TacticalProfile profile, long tick) {
        requireSameMob(intent, probe);
        Objects.requireNonNull(eligibleNow, "eligibleNow");
        Objects.requireNonNull(profile, "profile");
        for (var situation : ORDER) {
            var planned = intent.contingencies().get(situation);
            if (planned == null || !holds(situation, probe) || !eligibleNow.contains(planned)) {
                continue;
            }
            var tactic = profile.tactic(planned);
            if (tactic.isPresent()) {
                return Optional.of(fired(intent, situation, tactic.orElseThrow(), tick));
            }
        }
        return Optional.empty();
    }

    private static TacticalIntent fired(TacticalIntent intent, Situation used, Tactic tactic, long tick) {
        var remaining = new EnumMap<Situation, TacticId>(Situation.class);
        intent.contingencies().forEach((situation, planned) -> {
            if (situation != used && !planned.equals(tactic.id())) {
                remaining.put(situation, planned);
            }
        });
        var alternatives = intent.alternatives().stream().filter(candidate -> !candidate.equals(tactic.id())).toList();
        return new TacticalIntent(intent.mob(), tactic.id(), tactic.movement(), tactic.choice(), intent.confidence(),
                alternatives, remaining, ControlLevel.ALTERNATIVE, tick, intent.expiresAtNanos(), false);
    }

    private static boolean holds(Situation situation, MobProbe probe) {
        return switch (situation) {
            case IF_OWN_HEALTH_CRITICAL -> probe.health() == HealthBand.CRITICAL;
            case IF_TARGET_IN_MELEE -> probe.targetDistance().filter(DistanceBand.MELEE::equals).isPresent();
            case NOW -> false;
        };
    }

    private static void requireSameMob(TacticalIntent intent, MobProbe probe) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(probe, "probe");
        if (!intent.mob().equals(probe.mob())) {
            throw new IllegalArgumentException("probe of " + probe.mob() + " does not match intent of " + intent.mob());
        }
    }
}
