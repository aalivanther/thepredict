package akyrion.thepredict.domain;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The zone section of one profile (M24.1). An empty mode or radius inherits the global value. Facts start enabled
 * and act only in {@link ZoneMode#FULL}. The hazard push starts off.
 *
 * @param mode requested mode; empty inherits
 * @param radius requested radius; empty inherits, and an arena uses its own radius plus four
 * @param facts whether facts may be published when the effective mode is full
 * @param hazardPush whether an entity may be pushed into a hazard
 */
public record ZoneSettings(Optional<ZoneMode> mode, OptionalInt radius, boolean facts, HazardPush hazardPush) {
    /** Inherit the global mode and radius, publish facts, and do not exploit hazards. */
    public static final ZoneSettings DEFAULT = new ZoneSettings(Optional.empty(), OptionalInt.empty(), true,
            HazardPush.OFF);

    /**
     * Checks the components.
     *
     * @throws NullPointerException if the mode or the radius is {@code null}; an inherited value is an empty optional
     * @throws IllegalArgumentException when a present radius is outside 8–64 or the hazard push is missing
     */
    public ZoneSettings {
        java.util.Objects.requireNonNull(mode, "mode");
        java.util.Objects.requireNonNull(radius, "radius");
        if (hazardPush == null) {
            throw new IllegalArgumentException("Zone.HazardPush is required");
        }
        if (radius.isPresent()) {
            var blocks = radius.getAsInt();
            if (blocks < 8 || blocks > 64) {
                throw new IllegalArgumentException("Zone.Radius must be within [8, 64], was " + blocks);
            }
        }
    }

    /**
     * The mode that actually applies: the minimum of the global mode and the requested mode (G11).
     *
     * @param global the master {@code zone.mode}
     * @return the effective mode
     */
    public ZoneMode effective(ZoneMode global) {
        return global.min(mode.orElse(global));
    }

    /**
     * Whether the profile asks for a mode above the global one.
     *
     * @param global the master mode
     * @return whether the request must be lowered
     */
    public boolean above(ZoneMode global) {
        return mode.isPresent() && mode.get().rank() > global.rank();
    }
}
