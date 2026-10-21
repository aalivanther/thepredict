package akyrion.thepredict.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What one mob perceives at capture time. Immutable.
 *
 * @param mob mob session
 * @param health health band of the mob
 * @param target relation to the current target, if any
 * @param activeContexts contexts whose native conditions hold
 * @param eligibleTactics tactics eligible at capture time
 * @param cues conditions of the mob itself
 * @param choicesOutOfReach choices with a purpose written by the author whose reach does not get to the target now;
 *        empty without a target (M15.5)
 * @param contingencyEligible tactics that would be eligible under each contingency hypothesis (M16.6); only the keys
 *        {@code IF_TARGET_IN_MELEE} and {@code IF_OWN_HEALTH_CRITICAL}; a missing key offers the tactics eligible now
 * @param eligibleWhenReady tactics that would be eligible at capture time with every skill off its native cooldown
 *        (M10, real-time readiness); it contains {@code eligibleTactics} and never travels to Jev
 * @param eligibleSoon tactics that become eligible within the readiness horizon, for the question only (M19.6);
 *        {@code eligibleTactics ⊆ eligibleSoon ⊆ eligibleWhenReady}; the local planner never reads it
 * @param zoneFacts filtered zone facts; empty outside full (M24.7)
 * @param rawNoRetreat whether the raw retreat query was none; the cornered veto reads this, not the filtered fact
 * @param rawZoneBits raw fact bits; the sign bit lets entropy name a drop beside a player
 */
public record MobFacts(MobKey mob, HealthBand health, Optional<TargetRelation> target,
                       Set<ContextId> activeContexts, Set<TacticId> eligibleTactics, Set<MobCue> cues,
                       Set<ChoiceId> choicesOutOfReach, Map<Situation, Set<TacticId>> contingencyEligible,
                       Set<TacticId> eligibleWhenReady, Set<TacticId> eligibleSoon, Set<ZoneFact> zoneFacts,
                       boolean rawNoRetreat, long rawZoneBits) {
    /**
     * Validates the facts and copies the sets.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a choice is out of reach without a target, a contingency key is {@code NOW},
     *         or a tactic eligible now is not eligible when ready
     */
    public MobFacts {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(target, "target");
        activeContexts = Set.copyOf(Objects.requireNonNull(activeContexts, "activeContexts"));
        eligibleTactics = Set.copyOf(Objects.requireNonNull(eligibleTactics, "eligibleTactics"));
        cues = Set.copyOf(Objects.requireNonNull(cues, "cues"));
        choicesOutOfReach = Set.copyOf(Objects.requireNonNull(choicesOutOfReach, "choicesOutOfReach"));
        if (target.isEmpty() && !choicesOutOfReach.isEmpty()) {
            throw new IllegalArgumentException("choicesOutOfReach must be empty without a target");
        }
        var copy = new EnumMap<Situation, Set<TacticId>>(Situation.class);
        Objects.requireNonNull(contingencyEligible, "contingencyEligible").forEach((situation, tactics) -> {
            if (Objects.requireNonNull(situation, "contingencyEligible") == Situation.NOW) {
                throw new IllegalArgumentException("contingencyEligible must not use situation NOW");
            }
            copy.put(situation, Set.copyOf(Objects.requireNonNull(tactics, "contingencyEligible")));
        });
        contingencyEligible = Map.copyOf(copy);
        eligibleWhenReady = Set.copyOf(Objects.requireNonNull(eligibleWhenReady, "eligibleWhenReady"));
        eligibleSoon = Set.copyOf(Objects.requireNonNull(eligibleSoon, "eligibleSoon"));
        if (!eligibleWhenReady.containsAll(eligibleTactics)) {
            throw new IllegalArgumentException("eligibleWhenReady must contain every eligible tactic");
        }
        if (!eligibleSoon.containsAll(eligibleTactics) || !eligibleWhenReady.containsAll(eligibleSoon)) {
            throw new IllegalArgumentException("eligibleTactics must be inside eligibleSoon, inside eligibleWhenReady");
        }
        zoneFacts = Set.copyOf(Objects.requireNonNull(zoneFacts, "zoneFacts"));
    }

    /**
     * Facts from before the zone (rule W5). Zone facts are empty and the raw retreat is not none.
     *
     * @param mob mob session
     * @param health health band
     * @param target relation to the current target
     * @param activeContexts contexts that hold
     * @param eligibleTactics tactics eligible now
     * @param cues mob cues
     * @param choicesOutOfReach choices beyond reach
     * @param contingencyEligible hypothetical tactics
     * @param eligibleWhenReady tactics off cooldown
     * @param eligibleSoon tactics inside the horizon
     */
    public MobFacts(MobKey mob, HealthBand health, Optional<TargetRelation> target, Set<ContextId> activeContexts,
                    Set<TacticId> eligibleTactics, Set<MobCue> cues, Set<ChoiceId> choicesOutOfReach,
                    Map<Situation, Set<TacticId>> contingencyEligible, Set<TacticId> eligibleWhenReady,
                    Set<TacticId> eligibleSoon) {
        this(mob, health, target, activeContexts, eligibleTactics, cues, choicesOutOfReach, contingencyEligible,
                eligibleWhenReady, eligibleSoon, Set.of(), false, 0L);
    }

    /**
     * Creates the facts whose soon-set is the tactics eligible now (rule W5): the form of every capture before the
     * readiness horizon of M19. Production code passes {@code eligibleSoon}; this constructor stays while callers that
     * have no horizon use it.
     *
     * @param mob mob session
     * @param health health band of the mob
     * @param target relation to the current target, if any
     * @param activeContexts contexts whose native conditions hold
     * @param eligibleTactics tactics eligible at capture time
     * @param cues conditions of the mob itself
     * @param choicesOutOfReach choices with a purpose written by the author whose reach does not get to the target now
     * @param contingencyEligible tactics eligible under each contingency hypothesis
     * @param eligibleWhenReady tactics eligible with every skill off its native cooldown
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public MobFacts(MobKey mob, HealthBand health, Optional<TargetRelation> target, Set<ContextId> activeContexts,
                    Set<TacticId> eligibleTactics, Set<MobCue> cues, Set<ChoiceId> choicesOutOfReach,
                    Map<Situation, Set<TacticId>> contingencyEligible, Set<TacticId> eligibleWhenReady) {
        this(mob, health, target, activeContexts, eligibleTactics, cues, choicesOutOfReach, contingencyEligible,
                eligibleWhenReady, eligibleTactics, Set.of(), false, 0L);
    }

    /**
     * Same facts with a zone reading. A negative bit set means an entropy cast may name the target drop.
     *
     * @param zoneFacts facts that passed the filter
     * @param rawNoRetreat whether the raw retreat query found nothing
     * @param rawZoneBits raw fact bits before hysteresis
     * @return the copy
     */
    public MobFacts withZone(Set<ZoneFact> zoneFacts, boolean rawNoRetreat, long rawZoneBits) {
        return new MobFacts(mob, health, target, activeContexts, eligibleTactics, cues, choicesOutOfReach,
                contingencyEligible, eligibleWhenReady, eligibleSoon, zoneFacts, rawNoRetreat, rawZoneBits);
    }

    /**
     * Creates the facts whose tactics eligible when ready are those eligible now: the form of every capture before the
     * real-time readiness of M10. Production code passes the tactics eligible when ready; this constructor stays only
     * while tests and doubles use it (W5).
     *
     * @param mob mob session
     * @param health health band of the mob
     * @param target relation to the current target, if any
     * @param activeContexts contexts whose native conditions hold
     * @param eligibleTactics tactics eligible at capture time
     * @param cues conditions of the mob itself
     * @param choicesOutOfReach choices whose declared reach does not get to the target now
     * @param contingencyEligible tactics eligible under each contingency hypothesis
     * @throws IllegalArgumentException if a choice is out of reach without a target, or a contingency key is
     *         {@code NOW}
     */
    public MobFacts(MobKey mob, HealthBand health, Optional<TargetRelation> target, Set<ContextId> activeContexts,
                    Set<TacticId> eligibleTactics, Set<MobCue> cues, Set<ChoiceId> choicesOutOfReach,
                    Map<Situation, Set<TacticId>> contingencyEligible) {
        this(mob, health, target, activeContexts, eligibleTactics, cues, choicesOutOfReach, contingencyEligible,
                eligibleTactics);
    }

    /**
     * Creates the facts without hypothetical eligibility, so every contingency offers the tactics eligible now (rule W5:
     * the form of every capture before M16). Production code passes the hypothetical eligibility; this constructor
     * stays only while tests use it.
     *
     * @param mob mob session
     * @param health health band of the mob
     * @param target relation to the current target, if any
     * @param activeContexts contexts whose native conditions hold
     * @param eligibleTactics tactics eligible at capture time
     * @param cues conditions of the mob itself
     * @param choicesOutOfReach choices whose declared reach does not get to the target now
     * @throws IllegalArgumentException if a choice is out of reach without a target
     */
    public MobFacts(MobKey mob, HealthBand health, Optional<TargetRelation> target, Set<ContextId> activeContexts,
                    Set<TacticId> eligibleTactics, Set<MobCue> cues, Set<ChoiceId> choicesOutOfReach) {
        this(mob, health, target, activeContexts, eligibleTactics, cues, choicesOutOfReach, Map.of(), eligibleTactics);
    }

    /**
     * The tactics a contingency may offer: those eligible under its hypothesis when the capture computed them, and
     * otherwise those eligible now.
     *
     * @param situation a contingency situation
     * @return the eligible tactics of that hypothesis
     * @throws IllegalArgumentException if {@code situation} is {@code NOW}
     */
    public Set<TacticId> eligibleIn(Situation situation) {
        if (Objects.requireNonNull(situation, "situation") == Situation.NOW) {
            throw new IllegalArgumentException("eligibleIn takes a contingency situation");
        }
        return contingencyEligible.getOrDefault(situation, eligibleTactics);
    }
}
