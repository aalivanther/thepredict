package akyrion.thepredict.engine.duel;

/**
 * Fidelity knobs of the duel world (§7.2 of the simulator audit, SIM-08, SIM-09). {@link #LEGACY} keeps the bytes of
 * the golden files of M20 to M26; {@link #VANILLA} is the default of the effectiveness bench. The vanilla figures are
 * PLAUSIBLE, not verified with {@code javap} (the Paper jar is a patch): a mob input of {@code 0.98·(m·A)²} per tick
 * with ground friction 0.546 gives a terminal step of {@code 0.98·(m·A)²/(1 − 0.546)}, 0.1142 blocks for a zombie
 * ({@code A = 0.23}, {@code m = 1}), so a speed modifier scales the step by its square; {@code MoveControl} turns at
 * most 90° per tick; the goal selector re-selects every 2 ticks; vanilla applies only the excess of a stronger hit
 * inside the window of invulnerability; a damaging hit knocks back; a leap flies.
 *
 * @param walkBlocksPerTick terminal step of a walk at modifier 1
 * @param quadraticModifiers whether a speed modifier scales the step by its square instead of linearly
 * @param groundFriction the part of the velocity a body keeps from one tick to the next, 0 for none
 * @param maxTurnDegreesPerTick the largest turn of the heading in one tick
 * @param goalSwitchTicks ticks between two selections of the movement goal
 * @param vanillaInvulnerability whether a hit inside the window deals the excess over the last one
 * @param knockbackBlocks how far a damaging hit pushes its victim away from the attacker
 * @param leapFlightTicks ticks a leap takes to land, 0 for a jump at once
 */
record DuelPhysics(double walkBlocksPerTick, boolean quadraticModifiers, double groundFriction,
                   double maxTurnDegreesPerTick, int goalSwitchTicks, boolean vanillaInvulnerability,
                   double knockbackBlocks, int leapFlightTicks) {
    /** The world of M18.7: 0.23 blocks per tick, linear modifiers, no inertia, a jump at once. */
    static final DuelPhysics LEGACY = new DuelPhysics(DuelWorld.SPEED, false, 0.0, 360.0, 1, false, 0.0, 0);
    /** The vanilla world of §7.2, the default of the bench. */
    static final DuelPhysics VANILLA = new DuelPhysics(0.1142, true, 0.546, 90.0, 2, true, 0.4, 8);

    /**
     * Validates the knobs.
     *
     * @throws IllegalArgumentException when a value is out of its range
     */
    DuelPhysics {
        if (!(walkBlocksPerTick > 0) || groundFriction < 0 || groundFriction >= 1 || !(maxTurnDegreesPerTick > 0)
                || goalSwitchTicks < 1 || knockbackBlocks < 0 || leapFlightTicks < 0) {
            throw new IllegalArgumentException("duel physics out of range");
        }
    }

    /** Whether this is the world of M18.7, whose bytes the golden files keep. */
    boolean legacy() {
        return equals(LEGACY);
    }

    /**
     * The terminal step of a walk at a speed modifier.
     *
     * @param modifier the {@code speed} of the goal
     * @return blocks per tick once the body runs at full speed
     */
    double step(double modifier) {
        return walkBlocksPerTick * (quadraticModifiers ? modifier * modifier : modifier);
    }

    /**
     * The input of one tick at a speed modifier: the step a body at rest gains, so that friction leaves it at
     * {@link #step(double)} once it runs.
     *
     * @param modifier the {@code speed} of the goal
     * @return blocks per tick added to the velocity
     */
    double input(double modifier) {
        return step(modifier) * (1 - groundFriction);
    }
}
