package akyrion.thepredict.domain;

/**
 * Which short movement to publish (M20.5). Pure. Survival reactions may break the minimum hold; the engine enforces
 * the hold. Hover is only against a player who is not recharging.
 */
public final class MicroPolicy {
    private static final Reaction[] REACTIONS = Reaction.values();

    private MicroPolicy() {
    }

    /**
     * Chooses the micro-behaviour under an action stance (M23.6). {@link ActionStance#NONE} is the M20 choice.
     * Wind-up and stagger stay still. Recovery keeps sidestep, backstep and orbit.
     *
     * @param action current action
     * @param movement movement of the intent
     * @param signals packed tick signals
     * @param reaction armed reaction, or {@link ReactionPolicy#NONE}
     * @param evadeReady whether a choice that fits the evade mask is ready
     * @param player whether the expected target is a player
     * @param targetReachBlocks reach of the target
     * @param settings band of the profile
     * @return the behaviour
     */
    public static MicroBehaviour choose(ActionStance action, Movement movement, long signals, int reaction,
                                        boolean evadeReady, boolean player, double targetReachBlocks,
                                        MovementSettings settings) {
        if (action == ActionStance.NONE) {
            return choose(movement, signals, reaction, evadeReady, player, targetReachBlocks, settings);
        }
        if (action == ActionStance.WINDUP || action == ActionStance.STAGGERED) {
            return MicroBehaviour.STILL;
        }
        var chosen = choose(movement, signals, reaction, evadeReady, player, targetReachBlocks, settings);
        return switch (chosen) {
            case SIDESTEP, BACKSTEP, ORBIT -> chosen;
            default -> MicroBehaviour.NATIVE;
        };
    }

    /**
     * Chooses the micro-behaviour.
     *
     * @param movement movement of the intent
     * @param signals packed tick signals
     * @param reaction armed reaction, or {@link ReactionPolicy#NONE}
     * @param evadeReady whether a choice that fits the evade mask is ready
     * @param player whether the expected target is a player
     * @param targetReachBlocks reach of the target
     * @param settings band of the profile
     * @return the behaviour
     */
    public static MicroBehaviour choose(Movement movement, long signals, int reaction, boolean evadeReady,
                                        boolean player, double targetReachBlocks, MovementSettings settings) {
        var active = reaction >= 0 ? REACTIONS[reaction] : null;
        if (active == Reaction.DODGE_SHOT && movement != Movement.FLANK) {
            return MicroBehaviour.SIDESTEP;
        }
        if (active == Reaction.RESPECT_SHIELD
                && (movement == Movement.APPROACH || movement == Movement.HOLD)) {
            return MicroBehaviour.ORBIT;
        }
        if (active == Reaction.EDGE_DANGER) {
            return MicroBehaviour.REPOSITION;
        }
        if ((active == Reaction.SPACE_OUT || active == Reaction.EVADE_CRITICAL && !evadeReady)
                && (movement == Movement.KEEP_DISTANCE || movement == Movement.HOLD)) {
            return MicroBehaviour.BACKSTEP;
        }
        if ((active == Reaction.PUNISH_RECHARGE || active == Reaction.PUNISH_CONSUMING)
                && movement == Movement.APPROACH) {
            return MicroBehaviour.ENTER;
        }
        return byMovement(movement, signals, player, targetReachBlocks, settings);
    }

    /**
     * Replaces a backstep that would enter a harmful or lethal near push. Unknown and a mode below guard do not.
     *
     * @param chosen the behaviour already chosen
     * @param guard whether the effective mode is guard or full
     * @param mobPushNear {@link TickSignals} code of {@code MOB_PUSH_NEAR}
     * @return sidestep when the backstep is unsafe, otherwise {@code chosen}
     */
    public static MicroBehaviour awayFromEdge(MicroBehaviour chosen, boolean guard, int mobPushNear) {
        if (!guard || chosen != MicroBehaviour.BACKSTEP) {
            return chosen;
        }
        if (mobPushNear == PushDanger.HARM.ordinal() || mobPushNear == PushDanger.LETHAL.ordinal()) {
            return MicroBehaviour.SIDESTEP;
        }
        return chosen;
    }

    /**
     * Ticks a behaviour keeps itself before returning to native, not counting the minimum hold.
     *
     * @param behaviour the behaviour
     * @return the duration; {@code 0} means the movement table decides again
     */
    public static int durationTicks(MicroBehaviour behaviour) {
        return switch (behaviour) {
            case SIDESTEP -> 6;
            case BACKSTEP -> 4;
            case ENTER -> 20;
            case REPOSITION, SLOT -> 8;
            default -> 0;
        };
    }

    /**
     * Side of a sidestep or an orbit.
     *
     * @param behaviour the behaviour being activated
     * @param reaction ordinal of the reaction that armed the behaviour
     * @param signals packed tick signals
     * @param seed session seed
     * @param tick current tick
     * @param lastSidestep previous sidestep side
     * @param firstSidestep whether this mob has never sidestepped
     * @return the side; {@link MicroSide#LEFT} when the behaviour has no side
     */
    public static MicroSide side(MicroBehaviour behaviour, int reaction, long signals, long seed, long tick,
                                 MicroSide lastSidestep, boolean firstSidestep) {
        if (behaviour == MicroBehaviour.ORBIT && reaction == Reaction.RESPECT_SHIELD.ordinal()) {
            return TickSignals.bit(signals, TickSignals.MOB_LEFT_OF_TARGET) ? MicroSide.LEFT : MicroSide.RIGHT;
        }
        return switch (behaviour) {
            case ORBIT -> StableRandom.below(StableRandom.draw(seed, tick, StableRandom.SALT_SIDE), 2) == 0
                    ? MicroSide.LEFT : MicroSide.RIGHT;
            case SIDESTEP -> firstSidestep
                    ? (StableRandom.below(StableRandom.draw(seed, tick, StableRandom.SALT_SIDE), 2) == 0
                    ? MicroSide.LEFT : MicroSide.RIGHT)
                    : (lastSidestep == MicroSide.LEFT ? MicroSide.RIGHT : MicroSide.LEFT);
            default -> MicroSide.LEFT;
        };
    }

    /**
     * Whether an orbit flips side this tick.
     *
     * @param seed session seed
     * @param tick current tick
     * @param sinceTick tick the orbit started
     * @param pathFailed whether the last path failed
     * @return whether to flip
     */
    public static boolean flipOrbit(long seed, long tick, long sinceTick, boolean pathFailed) {
        if (pathFailed) {
            return true;
        }
        var span = 40 + StableRandom.below(StableRandom.draw(seed, sinceTick, StableRandom.SALT_FLIP), 41);
        return tick - sinceTick >= span;
    }

    private static MicroBehaviour byMovement(Movement movement, long signals, boolean player, double targetReach,
                                             MovementSettings settings) {
        var gap = TickSignals.gapQuarters(signals) / 4.0;
        if (movement == Movement.APPROACH) {
            if (gap > settings.bandInnerBlocks()) {
                return MicroBehaviour.NATIVE;
            }
            if (player && !TickSignals.bit(signals, TickSignals.TARGET_ATTACK_RECHARGING)
                    && gap <= targetReach + 1.0) {
                return MicroBehaviour.HOVER;
            }
            return MicroBehaviour.NATIVE;
        }
        if (movement == Movement.KEEP_DISTANCE) {
            return gap >= settings.bandInnerBlocks() && gap <= settings.bandOuterBlocks()
                    ? MicroBehaviour.ORBIT : MicroBehaviour.NATIVE;
        }
        return MicroBehaviour.NATIVE;
    }
}
