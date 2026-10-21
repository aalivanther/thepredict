package akyrion.thepredict.engine.duel;

/**
 * How a round resolves one tick and how faithfully the doubles of the engine ports behave (§7.2 of the simulator
 * audit, SIM-03, SIM-07, SIM-08 a, SIM-10, SIM-11). {@link #LEGACY} keeps the bytes of the golden files: the engine casts
 * first, effects land in cast order and a dead caster's effect is skipped, so a double death cannot happen. {@link #FAIR}
 * is the default of the bench: both sides decide on the same picture of the world, the movements apply together, the
 * casts commit in an order drawn per tick from the seed, the effects of one tick land together (two lethal hits are a
 * draw), every classic timer shares one global phase, and the ports of the engine read and act as on a server: the
 * trend of the rival, its line of sight and its reach, the hits as outcome events, real movement progress, and the
 * wait for invulnerability through {@code ImpactTiming}.
 *
 * @param simultaneous whether movements and effects of one tick apply together
 * @param seededOrder whether the commit order of the casts of one tick is drawn from the seed
 * @param serverPorts whether the reader, actions and outcome events of the engine behave as on a server
 * @param mobAttack whether a vanilla choice of ThePredict hits as {@code Mob.attack} on a server (the reach of the
 *     profile, no attack cooldown of its own) instead of the zombie's own hit (1.43 blocks, every 20 ticks); it only
 *     matters with the server ports
 */
record TickRules(boolean simultaneous, boolean seededOrder, boolean serverPorts, boolean mobAttack) {
    /** The round of M18.7. */
    static final TickRules LEGACY = new TickRules(false, false, false, false);
    /** The fair round of the bench. */
    static final TickRules FAIR = new TickRules(true, true, true, true);
    /** The fair round with the vanilla hit of ThePredict held to the zombie's own reach and cadence. */
    static final TickRules FAIR_ZOMBIE_HIT = new TickRules(true, true, true, false);

    /**
     * The rules without the vanilla hit of a server.
     *
     * @param simultaneous whether movements and effects of one tick apply together
     * @param seededOrder whether the commit order is drawn
     * @param serverPorts whether the ports behave as on a server
     */
    TickRules(boolean simultaneous, boolean seededOrder, boolean serverPorts) {
        this(simultaneous, seededOrder, serverPorts, serverPorts);
    }

    /** Whether this is the round of M18.7, whose bytes the golden files keep. */
    boolean legacy() {
        return equals(LEGACY);
    }
}
