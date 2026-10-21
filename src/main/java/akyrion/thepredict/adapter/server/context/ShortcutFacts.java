package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;

import java.util.Objects;
import java.util.Optional;

/**
 * The bands the legacy shortcut contexts of {@link ContextRule#SHORTCUTS} depend on (M03.3, M07.7). They are
 * computed in Java from the bands, with the meaning they had in schema 1 profiles: {@code idle}
 * without target, {@code attacking_phase} with the target in melee, {@code tracking_enemy} with the target
 * farther, {@code low_health_phase} with critical own health. Immutable.
 *
 * @param health health band of the mob
 * @param targetDistance distance band to the target; empty without a target
 */
public record ShortcutFacts(HealthBand health, Optional<DistanceBand> targetDistance) {
    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public ShortcutFacts {
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(targetDistance, "targetDistance");
    }

    /**
     * Tells whether a shortcut context holds.
     *
     * @param shortcut one of {@link ContextRule#SHORTCUTS}
     * @return {@code true} if the bands match its meaning; {@code false} for any other context
     */
    public boolean holds(ContextId shortcut) {
        return switch (shortcut.value()) {
            case "idle" -> targetDistance.isEmpty();
            case "attacking_phase" -> targetDistance.filter(band -> band == DistanceBand.MELEE).isPresent();
            case "tracking_enemy" -> targetDistance.filter(band -> band != DistanceBand.MELEE).isPresent();
            case "low_health_phase" -> health == HealthBand.CRITICAL;
            default -> false;
        };
    }
}
