package akyrion.thepredict.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An author context: native MythicMobs conditions that, while true, restrict the tactics a mob may
 * use. The conditions are opaque text evaluated by the adapter. Immutable.
 *
 * @param id context name, local to the profile
 * @param nativeConditions MythicMobs condition lines, none blank
 * @param priority the active context with the highest priority wins
 * @param allowedTactics tactics allowed while the context is active; not empty
 * @param directTactic tactic used without asking the model; must be among {@code allowedTactics}
 */
public record ContextRule(ContextId id, List<String> nativeConditions, int priority,
                          Set<TacticId> allowedTactics, Optional<TacticId> directTactic) {
    /**
     * Compatible shortcut contexts without native lines, computed in Java from the bands (M03.3, M07):
     * {@code idle} (no target), {@code attacking_phase} (target in {@link DistanceBand#MELEE}),
     * {@code tracking_enemy} (target in {@link DistanceBand#RANGED} or {@link DistanceBand#BEYOND}) and
     * {@code low_health_phase} (own health {@link HealthBand#CRITICAL}).
     */
    public static final Set<ContextId> SHORTCUTS = Set.of(new ContextId("idle"), new ContextId("attacking_phase"),
            new ContextId("tracking_enemy"), new ContextId("low_health_phase"));

    /**
     * Validates the rule and copies its collections.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a condition is blank, no tactic is allowed or the direct
     *         tactic is not allowed
     */
    public ContextRule {
        Objects.requireNonNull(id, "id");
        nativeConditions = List.copyOf(Objects.requireNonNull(nativeConditions, "nativeConditions"));
        nativeConditions.forEach(line -> DomainChecks.requireText(line, "nativeConditions"));
        allowedTactics = Set.copyOf(Objects.requireNonNull(allowedTactics, "allowedTactics"));
        Objects.requireNonNull(directTactic, "directTactic");
        if (allowedTactics.isEmpty()) {
            throw new IllegalArgumentException("allowedTactics must not be empty in context " + id);
        }
        if (directTactic.isPresent() && !allowedTactics.contains(directTactic.orElseThrow())) {
            throw new IllegalArgumentException("directTactic " + directTactic.orElseThrow()
                    + " must be in allowedTactics of context " + id);
        }
    }

    /**
     * Tells whether this rule is one of the {@link #SHORTCUTS}, which carry no native lines.
     *
     * @return {@code true} for a shortcut context
     */
    public boolean isShortcut() {
        return SHORTCUTS.contains(id);
    }
}
