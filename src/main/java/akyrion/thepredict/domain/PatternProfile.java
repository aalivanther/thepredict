package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * The {@code Patterns} section of a profile (M26.11). The mode may only lower the global mode. Facts default to
 * true and are sent only in {@link PatternMode#FULL}.
 *
 * @param mode empty when the profile inherits
 * @param againstEntities quota policy against entities
 * @param facts whether phrases may be sent
 */
public record PatternProfile(Optional<PatternMode> mode, BenchPolicy againstEntities, boolean facts) {
    /** Inherits the global mode, free against entities, facts on. */
    public static final PatternProfile DEFAULT = new PatternProfile(Optional.empty(), BenchPolicy.FREE, true);

    /**
     * Checks the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PatternProfile {
        if (mode == null || againstEntities == null) {
            throw new NullPointerException("pattern profile");
        }
    }

    /**
     * The mode that actually applies.
     *
     * @param global the global mode
     * @return the minimum
     */
    public PatternMode effective(PatternMode global) {
        return mode.map(global::min).orElse(global);
    }

    /**
     * Whether the profile asked for a mode above the global.
     *
     * @param global the global mode
     * @return {@code true} when the loader must warn and use the minimum
     */
    public boolean above(PatternMode global) {
        return mode.filter(asked -> asked.rank() > global.rank()).isPresent();
    }

    /**
     * Whether pattern facts of this profile may reach Jev and the signature (M26.7): only when the effective mode is
     * {@code full} and {@code Patterns.Facts} is on. Passing a mode that is already effective gives the same answer.
     *
     * @param global the global mode, or the effective one
     * @return whether facts and the pattern signature bits may be published
     */
    public boolean factsShown(PatternMode global) {
        return facts && effective(global) == PatternMode.FULL;
    }

    /**
     * Whether the exploit quota is lifted for this target (M26.6): only against an entity and only with
     * {@code Patterns.AgainstEntities: free}. Against a player the quota always holds.
     *
     * @param playerTarget whether the target is a player
     * @return whether {@code ExploitPolicy.share} may treat the target as entity-free
     */
    public boolean entityFree(boolean playerTarget) {
        return !playerTarget && againstEntities == BenchPolicy.FREE;
    }
}
