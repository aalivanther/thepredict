package akyrion.thepredict.domain;

/**
 * Which reaction arms on this tick (M20.4). Pure: the caller stores cooldowns and the arming tick. At most one new
 * reaction, the first of the table whose trigger, delay, cooldown, scope and action stance all hold. Dodge and
 * space-out are skipped when the layer does not move the mob.
 */
public final class ReactionPolicy {
    /** No reaction. */
    public static final int NONE = -1;

    private static final Reaction[] REACTIONS = Reaction.values();

    private ReactionPolicy() {
    }

    /**
     * Preference mask of a reaction. Empty for movement-only reactions.
     *
     * @param reaction the reaction
     * @return bits of {@link CastPreference}
     */
    public static long mask(Reaction reaction) {
        return switch (reaction) {
            case EVADE_CRITICAL -> CastPreference.role(SkillRole.ESCAPE) | CastPreference.role(SkillRole.DEFENSE)
                    | CastPreference.role(SkillRole.SUSTAIN);
            case PUNISH_CONSUMING -> CastPreference.role(SkillRole.INTERRUPT)
                    | CastPreference.role(SkillRole.GAP_CLOSE) | CastPreference.role(SkillRole.DAMAGE);
            case PUNISH_RECHARGE -> CastPreference.delivery(Delivery.MELEE) | CastPreference.role(SkillRole.DAMAGE)
                    | CastPreference.role(SkillRole.FINISHER);
            case RESPECT_SHIELD -> CastPreference.role(SkillRole.CONTROL) | CastPreference.ignoresShield();
            case FINISH -> CastPreference.role(SkillRole.FINISHER);
            case DODGE_SHOT, SPACE_OUT, EDGE_DANGER -> 0L;
            case HAZARD_PUSH -> CastPreference.role(SkillRole.CONTROL) | CastPreference.displacesTarget();
            case EXPLOIT_PATTERN -> 0L;
        };
    }

    /**
     * Whether the incoming reaction may replace the occupant of its slot.
     *
     * @param occupant ordinal of the reaction in the slot, or {@link #NONE}
     * @param untilTick tick until which the slot holds
     * @param now current tick
     * @param incoming the new reaction
     * @return whether the slot takes the new reaction
     */
    public static boolean replaces(int occupant, long untilTick, long now, Reaction incoming) {
        return occupant < 0 || untilTick <= now || incoming.ordinal() < occupant;
    }

    /**
     * Whether an action stance lets a reaction arm (M23.6, decision 6 of the user). Wind-up and stagger silence every
     * reaction. Recovery keeps only evade, dodge and space-out: the mob does not attack, but it may still move away.
     *
     * @param action current action stance
     * @param reaction the reaction
     * @return whether the reaction may arm in that stance
     */
    public static boolean allowedIn(ActionStance action, Reaction reaction) {
        return switch (action) {
            case NONE -> true;
            case WINDUP, STAGGERED -> false;
            case RECOVERY -> reaction == Reaction.EVADE_CRITICAL || reaction == Reaction.DODGE_SHOT
                    || reaction == Reaction.SPACE_OUT;
        };
    }

    /**
     * First reaction that may arm.
     *
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param movementOnly whether dodge and space-out are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again, indexed by ordinal
     * @param armedSince tick at which the trigger became true, or {@code -1} while false, indexed by ordinal
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(long signals, long now, int delayTicks, ReactionScope scope, boolean movementOnly,
                             boolean controlled, Movement movement, double criticalRatio,
                             MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince) {
        return select(ActionStance.NONE, signals, now, delayTicks, scope, ReactionMask.ALL, movementOnly, controlled,
                movement, criticalRatio, movementSettings, seen, busyUntilTick, cooldownReady, armedSince, false, false,
                false);
    }

    /**
     * Selects a reaction under an action stance (M23.6), without the zone and pattern triggers.
     *
     * @param action current action stance
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param reactionMask reactions the rung still allows
     * @param movementOnly whether dodge and space-out are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again
     * @param armedSince tick at which the trigger became true
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(ActionStance action, long signals, long now, int delayTicks, ReactionScope scope,
                             long reactionMask, boolean movementOnly, boolean controlled, Movement movement,
                             double criticalRatio, MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince) {
        return select(action, signals, now, delayTicks, scope, reactionMask, movementOnly, controlled, movement,
                criticalRatio, movementSettings, seen, busyUntilTick, cooldownReady, armedSince, false, false, false);
    }

    /**
     * Selects a reaction under a lever mask (M22.7). {@link ReactionMask#ALL} is the M20 selection.
     *
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param reactionMask reactions the rung still allows
     * @param movementOnly whether dodge and space-out are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again, indexed by ordinal
     * @param armedSince tick at which the trigger became true, or {@code -1} while false, indexed by ordinal
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(long signals, long now, int delayTicks, ReactionScope scope, long reactionMask,
                             boolean movementOnly, boolean controlled, Movement movement, double criticalRatio,
                             MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince) {
        return select(ActionStance.NONE, signals, now, delayTicks, scope, reactionMask, movementOnly, controlled,
                movement, criticalRatio, movementSettings, seen, busyUntilTick, cooldownReady, armedSince, false, false,
                false);
    }

    /**
     * Selects a reaction. {@code zoneGuard} admits the edge step and {@code displaceReady} admits a hazard push.
     * Both default to false on the overloads that predate the zone.
     *
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param reactionMask reactions the rung still allows
     * @param movementOnly whether movement-only reactions are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again
     * @param armedSince tick at which the trigger became true
     * @param zoneGuard whether the effective mode is guard or full
     * @param displaceReady whether a ready choice pushes an entity and the profile exploits it
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(long signals, long now, int delayTicks, ReactionScope scope, long reactionMask,
                             boolean movementOnly, boolean controlled, Movement movement, double criticalRatio,
                             MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince, boolean zoneGuard, boolean displaceReady) {
        return select(ActionStance.NONE, signals, now, delayTicks, scope, reactionMask, movementOnly, controlled,
                movement, criticalRatio, movementSettings, seen, busyUntilTick, cooldownReady, armedSince, zoneGuard,
                displaceReady, false);
    }

    /**
     * Selects a reaction with no action stance. {@code exploitReady} may arm {@link Reaction#EXPLOIT_PATTERN}, which
     * stays last because the enum order is the walk order. Existing callers pass {@code false}.
     *
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param reactionMask reactions the rung still allows
     * @param movementOnly whether movement-only reactions are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again
     * @param armedSince tick at which the trigger became true
     * @param zoneGuard whether the effective mode is guard or full
     * @param displaceReady whether a ready choice pushes an entity and the profile exploits it
     * @param exploitReady whether a pattern counter may arm
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(long signals, long now, int delayTicks, ReactionScope scope, long reactionMask,
                             boolean movementOnly, boolean controlled, Movement movement, double criticalRatio,
                             MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince, boolean zoneGuard, boolean displaceReady,
                             boolean exploitReady) {
        return select(ActionStance.NONE, signals, now, delayTicks, scope, reactionMask, movementOnly, controlled,
                movement, criticalRatio, movementSettings, seen, busyUntilTick, cooldownReady, armedSince, zoneGuard,
                displaceReady, exploitReady);
    }

    /**
     * Selects a reaction. The action stance filters inside the walk ({@link #allowedIn}), so a reaction that the
     * stance forbids never hides a later one that it allows. {@link ActionStance#NONE} is the selection without a
     * stance.
     *
     * @param action current action stance
     * @param signals packed tick signals
     * @param now current tick
     * @param delayTicks uninterrupted ticks the trigger must already have held
     * @param scope profile scope
     * @param reactionMask reactions the rung still allows
     * @param movementOnly whether movement-only reactions are evaluated
     * @param controlled whether the level admits reactions
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param movementSettings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param cooldownReady tick at which each reaction may arm again
     * @param armedSince tick at which the trigger became true
     * @param zoneGuard whether the effective mode is guard or full
     * @param displaceReady whether a ready choice pushes an entity and the profile exploits it
     * @param exploitReady whether a pattern counter may arm
     * @return the ordinal, or {@link #NONE}
     */
    public static int select(ActionStance action, long signals, long now, int delayTicks, ReactionScope scope,
                             long reactionMask, boolean movementOnly, boolean controlled, Movement movement,
                             double criticalRatio, MovementSettings movementSettings, boolean seen, long busyUntilTick,
                             long[] cooldownReady, long[] armedSince, boolean zoneGuard, boolean displaceReady,
                             boolean exploitReady) {
        if (!controlled || scope == ReactionScope.NONE || !TickSignals.bit(signals, TickSignals.VALID)) {
            return NONE;
        }
        for (var reaction : REACTIONS) {
            if (!walked(action, reaction, scope, reactionMask, movementOnly, zoneGuard, displaceReady)
                    || now < cooldownReady[reaction.ordinal()]) {
                continue;
            }
            if (!trigger(reaction, signals, movement, criticalRatio, movementSettings, seen, busyUntilTick, now,
                    movementSettings.meleeReachBlocks(), displaceReady, exploitReady)) {
                continue;
            }
            var since = armedSince[reaction.ordinal()];
            if (since >= 0 && now - since >= delayTicks) {
                return reaction.ordinal();
            }
        }
        return NONE;
    }

    private static boolean walked(ActionStance action, Reaction reaction, ReactionScope scope, long reactionMask,
                                  boolean movementOnly, boolean zoneGuard, boolean displaceReady) {
        if (reaction == Reaction.EDGE_DANGER && !zoneGuard || reaction == Reaction.HAZARD_PUSH && !displaceReady) {
            return false;
        }
        return allowedIn(action, reaction) && ReactionMask.admits(reactionMask, reaction)
                && (movementOnly || reaction.evaluatedWithoutMicro()) && scope.admits(reaction.band());
    }

    /**
     * Whether the trigger holds now, ignoring delay and cooldown.
     *
     * @param reaction the reaction
     * @param signals packed tick signals
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param settings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param now current tick
     * @return whether the trigger holds
     */
    public static boolean trigger(Reaction reaction, long signals, Movement movement, double criticalRatio,
                                  MovementSettings settings, boolean seen, long busyUntilTick, long now) {
        return trigger(reaction, signals, movement, criticalRatio, settings, seen, busyUntilTick, now,
                settings.meleeReachBlocks(), false);
    }

    /**
     * Whether the trigger holds, with the target's reach and whether a displacing choice is ready.
     *
     * @param reaction the reaction
     * @param signals packed tick signals
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param settings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param now current tick
     * @param targetReachBlocks reach of the target
     * @param displaceReady whether a ready choice pushes the target; existing callers pass {@code false}
     * @return whether the trigger holds
     */
    public static boolean trigger(Reaction reaction, long signals, Movement movement, double criticalRatio,
                                  MovementSettings settings, boolean seen, long busyUntilTick, long now,
                                  double targetReachBlocks, boolean displaceReady) {
        return trigger(reaction, signals, movement, criticalRatio, settings, seen, busyUntilTick, now,
                targetReachBlocks, displaceReady, false);
    }

    /**
     * Whether the trigger holds. {@code exploitReady} arms {@link Reaction#EXPLOIT_PATTERN} only. Existing callers
     * pass {@code false}, so a fight that does not exploit stays on the previous triggers. Every reaction but
     * {@code EDGE_DANGER}, which reads the mob's own footing, reads the target, so it needs
     * {@link TickSignals#HAS_TARGET} (MZ-04, SP-15): without a target the reader leaves the gap at 0 and the target
     * bits off, and no reaction may arm on that reading, nor on target bits that a reader set against its contract.
     * {@code EVADE_CRITICAL} keeps the check because its trigger is the gap to the target (M20.4).
     *
     * @param reaction the reaction
     * @param signals packed tick signals
     * @param movement movement of the intent
     * @param criticalRatio critical health ratio
     * @param settings reach and band
     * @param seen whether the last probe saw the target
     * @param busyUntilTick tick the last wind-up ended
     * @param now current tick
     * @param targetReachBlocks reach of the target
     * @param displaceReady whether a ready choice pushes the target
     * @param exploitReady whether a pattern counter may arm, already filtered by the exploit policy
     * @return whether the trigger holds
     */
    public static boolean trigger(Reaction reaction, long signals, Movement movement, double criticalRatio,
                                  MovementSettings settings, boolean seen, long busyUntilTick, long now,
                                  double targetReachBlocks, boolean displaceReady, boolean exploitReady) {
        if (readsTarget(reaction) && !TickSignals.bit(signals, TickSignals.HAS_TARGET)) {
            return false;
        }
        return holds(reaction, signals, movement, criticalRatio * 100.0, settings, seen, busyUntilTick, now,
                targetReachBlocks, displaceReady, exploitReady);
    }

    private static boolean holds(Reaction reaction, long signals, Movement movement, double critical,
                                 MovementSettings settings, boolean seen, long busyUntilTick, long now,
                                 double targetReachBlocks, boolean displaceReady, boolean exploitReady) {
        var gap = TickSignals.gapQuarters(signals) / 4.0;
        return switch (reaction) {
            case EVADE_CRITICAL -> TickSignals.mobHealthPercent(signals) <= critical
                    && gap <= settings.meleeReachBlocks();
            case DODGE_SHOT -> TickSignals.bit(signals, TickSignals.INCOMING_PROJECTILE)
                    || TickSignals.bit(signals, TickSignals.TARGET_SHOT_READY) && gap > settings.meleeReachBlocks()
                    && seen;
            case PUNISH_CONSUMING -> TickSignals.bit(signals, TickSignals.TARGET_CONSUMING);
            case PUNISH_RECHARGE -> TickSignals.bit(signals, TickSignals.TARGET_ATTACK_RECHARGING)
                    && gap <= settings.bandInnerBlocks();
            case RESPECT_SHIELD -> TickSignals.bit(signals, TickSignals.TARGET_BLOCKING)
                    && movement != Movement.FLANK;
            case FINISH -> TickSignals.targetHealthPercent(signals) <= critical;
            case SPACE_OUT -> (movement == Movement.KEEP_DISTANCE || movement == Movement.HOLD)
                    && !TickSignals.bit(signals, TickSignals.TARGET_ATTACK_RECHARGING)
                    && gap <= settings.meleeReachBlocks() + 0.5 && now >= busyUntilTick && now - busyUntilTick <= 2;
            case EDGE_DANGER -> TickSignals.push(signals, TickSignals.MOB_PUSH_NEAR) == PushDanger.LETHAL.ordinal()
                    && TickSignals.bit(signals, TickSignals.MOB_ON_GROUND)
                    && TickSignals.gapQuarters(signals) <= TickSignals.quartersOf(targetReachBlocks + 1.0);
            case HAZARD_PUSH -> displaceReady
                    && TickSignals.push(signals, TickSignals.TARGET_PUSH_FAR) == PushDanger.LETHAL.ordinal();
            case EXPLOIT_PATTERN -> exploitReady;
        };
    }

    private static boolean readsTarget(Reaction reaction) {
        return reaction != Reaction.EDGE_DANGER;
    }
}
