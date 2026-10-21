package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Tick a cast is expected to land (M25). Pure.
 */
public final class ImpactForecast {
    private ImpactForecast() {
    }

    /**
     * Forecast of an untyped damaging cast. The flight is zero and marked assumed.
     *
     * @param startTick tick the cast starts
     * @param telegraphTicks tell length
     * @return the impact tick
     */
    public static Result at(long startTick, int telegraphTicks) {
        return new Result(startTick + telegraphTicks, true);
    }

    /**
     * Forecast tick.
     *
     * @param startTick tick the cast starts
     * @param telegraphTicks tell length
     * @param delivery delivery of the cast
     * @param distanceBlocks distance to the target
     * @param speedBlocksPerTick projectile speed, or 0 to use 3
     * @param declaredAreaDelay ticks an area-at-target waits, or 0
     * @return the impact tick and whether the flight was assumed
     * @throws NullPointerException if {@code delivery} is null
     */
    public static Result at(long startTick, int telegraphTicks, Delivery delivery, double distanceBlocks,
                            double speedBlocksPerTick, int declaredAreaDelay) {
        var flight = flight(Objects.requireNonNull(delivery, "delivery"), distanceBlocks, speedBlocksPerTick,
                declaredAreaDelay);
        return new Result(startTick + telegraphTicks + flight.ticks(), flight.assumed());
    }

    private static Flight flight(Delivery delivery, double distanceBlocks, double speedBlocksPerTick, int delay) {
        return switch (delivery) {
            case MELEE, CONE, HITSCAN, AREA_AROUND_SELF, SELF -> new Flight(0, false);
            case AREA_AT_TARGET -> new Flight(Math.max(0, delay), false);
            case PROJECTILE -> {
                var known = speedBlocksPerTick > 0.0 && Double.isFinite(speedBlocksPerTick);
                var speed = known ? speedBlocksPerTick : 3.0;
                var ticks = (int) StrictMath.ceil(Math.max(0.0, distanceBlocks) / speed);
                yield new Flight(ticks, !known);
            }
        };
    }

    /**
     * Forecast of one impact.
     *
     * @param tick forecast tick
     * @param assumed whether an unread branch supplied the flight
     */
    public record Result(long tick, boolean assumed) {
    }

    private record Flight(int ticks, boolean assumed) {
    }
}
