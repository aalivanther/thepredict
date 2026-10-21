package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Distances and speeds of the movement goals of one profile. Immutable.
 *
 * <p>Invariant: {@code 0 < meleeReachBlocks < bandInnerBlocks < bandOuterBlocks <= rangedReachBlocks};
 * every value finite; speeds and the retreat distance positive; the arena radius, if present,
 * greater than {@code bandOuterBlocks}.
 *
 * @param meleeReachBlocks distance up to which the target counts as in melee
 * @param bandInnerBlocks inner edge of the keep-distance band
 * @param bandOuterBlocks outer edge of the keep-distance band
 * @param rangedReachBlocks distance up to which the target counts as in ranged reach
 * @param retreatDistanceBlocks distance a retreat tries to open
 * @param retreatSpeed speed multiplier while retreating
 * @param approachSpeed speed multiplier while approaching
 * @param arenaRadiusBlocks optional radius the mob must not leave
 */
public record MovementSettings(double meleeReachBlocks, double bandInnerBlocks, double bandOuterBlocks,
                               double rangedReachBlocks, double retreatDistanceBlocks,
                               double retreatSpeed, double approachSpeed,
                               OptionalDouble arenaRadiusBlocks) {
    /**
     * The defaults of 01 §3 (2.5; 5; 9; 16; 10; 1.2; 1.15; no arena), the single source of the internal profile
     * defaults ({@code config.ProfileDefaults.standard()}) and of the inference of a purpose without a profile (M15.3).
     */
    public static final MovementSettings DEFAULTS = new MovementSettings(2.5, 5.0, 9.0, 16.0, 10.0, 1.2, 1.15,
            OptionalDouble.empty());

    /**
     * Validates the settings.
     *
     * @throws NullPointerException if {@code arenaRadiusBlocks} is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public MovementSettings {
        DomainChecks.requirePositive(meleeReachBlocks, "meleeReachBlocks");
        DomainChecks.requireFinite(bandInnerBlocks, "bandInnerBlocks");
        DomainChecks.requireFinite(bandOuterBlocks, "bandOuterBlocks");
        DomainChecks.requireFinite(rangedReachBlocks, "rangedReachBlocks");
        DomainChecks.requirePositive(retreatDistanceBlocks, "retreatDistanceBlocks");
        DomainChecks.requirePositive(retreatSpeed, "retreatSpeed");
        DomainChecks.requirePositive(approachSpeed, "approachSpeed");
        Objects.requireNonNull(arenaRadiusBlocks, "arenaRadiusBlocks");
        requireLess(meleeReachBlocks, bandInnerBlocks, "meleeReachBlocks", "bandInnerBlocks");
        requireLess(bandInnerBlocks, bandOuterBlocks, "bandInnerBlocks", "bandOuterBlocks");
        if (!(bandOuterBlocks <= rangedReachBlocks)) {
            throw new IllegalArgumentException("bandOuterBlocks must be <= rangedReachBlocks, was "
                    + bandOuterBlocks + " > " + rangedReachBlocks);
        }
        if (arenaRadiusBlocks.isPresent()) {
            var radius = DomainChecks.requireFinite(arenaRadiusBlocks.getAsDouble(), "arenaRadiusBlocks");
            requireLess(bandOuterBlocks, radius, "bandOuterBlocks", "arenaRadiusBlocks");
        }
    }

    private static void requireLess(double lower, double upper, String lowerField, String upperField) {
        if (!(lower < upper)) {
            throw new IllegalArgumentException(lowerField + " must be < " + upperField + ", was "
                    + lower + " >= " + upper);
        }
    }
}
