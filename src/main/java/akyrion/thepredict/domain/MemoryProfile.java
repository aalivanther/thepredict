package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * The {@code Memory} section of a profile (M27). The mode may only lower the global mode. Facts default to true and
 * are sent only in {@link MemoryMode#FULL}.
 *
 * @param mode empty when the profile inherits
 * @param facts whether a history phrase may be sent
 */
public record MemoryProfile(Optional<MemoryMode> mode, boolean facts) {
    /** Inherits the global mode and allows facts. */
    public static final MemoryProfile DEFAULT = new MemoryProfile(Optional.empty(), true);

    /**
     * Checks the components.
     *
     * @throws NullPointerException if the mode is {@code null}
     */
    public MemoryProfile {
        if (mode == null) {
            throw new NullPointerException("memory profile");
        }
    }

    /**
     * The mode that actually applies.
     *
     * @param global the global mode
     * @return the minimum
     */
    public MemoryMode effective(MemoryMode global) {
        return mode.map(global::min).orElse(global);
    }

    /**
     * Whether the profile asked for a mode above the global.
     *
     * @param global the global mode
     * @return {@code true} when the loader must warn and use the minimum
     */
    public boolean above(MemoryMode global) {
        return mode.filter(asked -> asked.rank() > global.rank()).isPresent();
    }

    /**
     * Whether the history phrase of this profile may be sent (M27): only when the effective mode is {@code full} and
     * {@code Memory.Facts} is on. Passing a mode that is already effective gives the same answer.
     *
     * @param global the global mode, or the effective one
     * @return whether the admission may keep its fact
     */
    public boolean factShown(MemoryMode global) {
        return facts && effective(global) == MemoryMode.FULL;
    }
}
