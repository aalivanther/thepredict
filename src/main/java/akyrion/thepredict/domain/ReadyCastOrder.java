package akyrion.thepredict.domain;

import java.util.Set;

/**
 * Pure order and direction of the skills that may fire as soon as they are ready (M19.5). The choice of the intent
 * is always first and is exempt from the direction rule; this class orders the other candidates. Stateless.
 */
public final class ReadyCastOrder {
    private ReadyCastOrder() {
    }

    /**
     * Whether a candidate may fire in the current stance (M23.6). Wind-up and stagger block every cast. Recovery
     * allows only a non-damaging escape or defense. The intent choice is exempt from the role filter, not from those
     * blocks. An empty cast map allows every role.
     *
     * @param choice the candidate
     * @param intentChoice whether this is the choice of the intent
     * @param action current action
     * @param base current base
     * @param settings stance section
     * @return whether it may fire
     */
    public static boolean stanceAllows(SkillChoice choice, boolean intentChoice, ActionStance action, BaseStance base,
                                       StanceSettings settings) {
        if (action == ActionStance.WINDUP || action == ActionStance.STAGGERED) {
            return false;
        }
        if (action == ActionStance.RECOVERY) {
            return recoveryAllows(choice);
        }
        if (intentChoice || settings.casts().isEmpty()) {
            return true;
        }
        var purpose = choice.purpose();
        return purpose.isEmpty() || settings.allows(base, purpose.get().primary());
    }

    /**
     * Whether a candidate may start before {@code busyUntilTick} (M23.6, decision 6 of the user). The busy deadline
     * also covers the recovery that follows a tell, so every path that does not ask the stance stays blocked: the mob
     * does not attack. Only during {@link ActionStance#RECOVERY}, and only a non-damaging choice with an
     * {@code ESCAPE} or {@code DEFENSE} role, may start early, so the mob can still evade. Wind-up, stagger and an
     * idle action never start early.
     *
     * @param choice the candidate
     * @param action current action
     * @return whether the busy deadline may be skipped for this choice
     */
    public static boolean startsWhileBusy(SkillChoice choice, ActionStance action) {
        return action == ActionStance.RECOVERY && recoveryAllows(choice);
    }

    private static boolean recoveryAllows(SkillChoice choice) {
        var purpose = choice.purpose();
        if (purpose.isEmpty() || purpose.get().damage() != DamageTier.NONE) {
            return false;
        }
        var roles = purpose.get().roles();
        return roles.contains(SkillRole.ESCAPE) || roles.contains(SkillRole.DEFENSE);
    }

    /**
     * Tells whether a candidate whose purpose has {@code GAP_CLOSE} or {@code ESCAPE} may fire with the movement of the
     * intent. A purpose with neither role is always allowed. A purpose with both must satisfy both rows, so it never
     * fires as a candidate. The choice of the intent does not ask.
     *
     * @param purpose purpose of the candidate
     * @param movement movement of the current intent
     * @param distanceBlocks distance to the target; ignored unless the movement is {@code KEEP_DISTANCE}
     * @param settings movement settings of the profile, for the keep-distance band
     * @return whether the candidate may fire in that direction
     */
    public static boolean directionAllows(SkillPurpose purpose, Movement movement, double distanceBlocks,
                                          MovementSettings settings) {
        return directionAllows(purpose, movement, distanceBlocks, settings, false, false);
    }

    /**
     * Direction rule plus the two reaction exceptions (M20.4): an escape may leave while evading with any movement
     * except approach, and a gap-close may punish a consuming target with any movement except retreat.
     *
     * @param purpose purpose of the candidate
     * @param movement movement of the current intent
     * @param distanceBlocks distance to the target; ignored unless the movement is {@code KEEP_DISTANCE}
     * @param settings movement settings of the profile, for the keep-distance band
     * @param evadeCritical whether {@code EVADE_CRITICAL} is armed
     * @param punishConsuming whether {@code PUNISH_CONSUMING} is armed
     * @return whether the candidate may fire in that direction
     */
    public static boolean directionAllows(SkillPurpose purpose, Movement movement, double distanceBlocks,
                                          MovementSettings settings, boolean evadeCritical, boolean punishConsuming) {
        return directionAllows(purpose, movement, distanceBlocks, settings, evadeCritical, punishConsuming, false,
                false);
    }

    /**
     * Direction rule, its two reaction exceptions, and the damage of a gap-close in melee (fase de efectividad E-2): a
     * gap-close that also deals damage, with its target inside its reach and already in melee, moves the mob nowhere new
     * and hits as any damaging skill does, so it may fire with any movement except retreat and while no escape is armed.
     * The classic casts such a leap whenever its target is within its {@code targetwithin}; holding it back in melee
     * only throws its damage away. Away from melee the old rows stand: a leap there would break a band that holds.
     *
     * @param purpose purpose of the candidate
     * @param movement movement of the current intent
     * @param distanceBlocks distance to the target; ignored unless the movement is {@code KEEP_DISTANCE}
     * @param settings movement settings of the profile, for the keep-distance band
     * @param evadeCritical whether {@code EVADE_CRITICAL} is armed
     * @param punishConsuming whether {@code PUNISH_CONSUMING} is armed
     * @param inReach whether the target is inside the reach of the candidate now
     * @param inMelee whether the target is in the melee band of the mob now
     * @return whether the candidate may fire in that direction
     */
    public static boolean directionAllows(SkillPurpose purpose, Movement movement, double distanceBlocks,
                                          MovementSettings settings, boolean evadeCritical, boolean punishConsuming,
                                          boolean inReach, boolean inMelee) {
        var gap = purpose.roles().contains(SkillRole.GAP_CLOSE);
        var escape = purpose.roles().contains(SkillRole.ESCAPE);
        if (!gap && !escape) {
            return true;
        }
        if (escape && evadeCritical && movement != Movement.APPROACH && !gap) {
            return true;
        }
        if (gap && punishConsuming && movement != Movement.RETREAT && !escape) {
            return true;
        }
        if (gap && !escape && inReach && inMelee && !evadeCritical && purpose.roles().contains(SkillRole.DAMAGE)
                && movement != Movement.RETREAT) {
            return true;
        }
        if (movement == Movement.HOLD || movement == Movement.NONE) {
            return false;
        }
        var gapOk = !gap || gapAllows(movement, distanceBlocks, settings);
        var escapeOk = !escape || escapeAllows(movement, distanceBlocks, settings);
        return gapOk && escapeOk;
    }

    /**
     * Compares two candidates that are not the choice of the intent: more {@code BestWhen} conditions holding now, then
     * higher damage ({@code HIGH}, {@code MEDIUM}, {@code LOW}, {@code UNRATED}, {@code NONE}), then the declaration
     * index.
     *
     * @param leftBest how many {@code BestWhen} conditions of the left candidate hold now
     * @param leftDamage damage tier of the left candidate
     * @param leftIndex declaration index of the left candidate
     * @param rightBest how many {@code BestWhen} conditions of the right candidate hold now
     * @param rightDamage damage tier of the right candidate
     * @param rightIndex declaration index of the right candidate
     * @return negative when the left candidate goes first
     */
    public static int compare(int leftBest, DamageTier leftDamage, int leftIndex, int rightBest,
                              DamageTier rightDamage, int rightIndex) {
        return compare(leftBest, leftDamage, leftIndex, rightBest, rightDamage, rightIndex, true);
    }

    /**
     * The same order with the damage step optional (M22.7). {@code damagePreference} false skips the damage rank, so
     * a tie of {@code BestWhen} falls through to the declaration index.
     *
     * @param leftBest how many {@code BestWhen} conditions of the left candidate hold now
     * @param leftDamage damage tier of the left candidate
     * @param leftIndex declaration index of the left candidate
     * @param rightBest how many {@code BestWhen} conditions of the right candidate hold now
     * @param rightDamage damage tier of the right candidate
     * @param rightIndex declaration index of the right candidate
     * @param damagePreference whether a higher damage tier goes first
     * @return negative when the left candidate goes first
     */
    public static int compare(int leftBest, DamageTier leftDamage, int leftIndex, int rightBest,
                              DamageTier rightDamage, int rightIndex, boolean damagePreference) {
        if (leftBest != rightBest) {
            return Integer.compare(rightBest, leftBest);
        }
        if (!damagePreference) {
            return Integer.compare(leftIndex, rightIndex);
        }
        var damage = Integer.compare(rank(rightDamage), rank(leftDamage));
        return damage != 0 ? damage : Integer.compare(leftIndex, rightIndex);
    }

    /**
     * Rank of a damage tier for the candidate order. {@code UNRATED} follows {@code LOW}.
     *
     * @param damage damage tier
     * @return higher when the tier goes first
     */
    public static int rank(DamageTier damage) {
        return switch (damage) {
            case HIGH -> 4;
            case MEDIUM -> 3;
            case LOW -> 2;
            case UNRATED -> 1;
            case NONE -> 0;
        };
    }

    private static boolean gapAllows(Movement movement, double distanceBlocks, MovementSettings settings) {
        return movement == Movement.APPROACH || movement == Movement.FLANK
                || movement == Movement.KEEP_DISTANCE && distanceBlocks > settings.bandOuterBlocks();
    }

    private static boolean escapeAllows(Movement movement, double distanceBlocks, MovementSettings settings) {
        return movement == Movement.RETREAT
                || movement == Movement.KEEP_DISTANCE && distanceBlocks < settings.bandInnerBlocks();
    }

    /**
     * Whether a candidate keeps the mob alive now and goes before the damage of the same tick (fase de efectividad
     * E-2): a skill that restores or protects the caster ({@code SUSTAIN} or {@code DEFENSE}), deals no damage, and has
     * a declared {@code BestWhen} that holds now, as an author's rule on health or distance would fire it first.
     *
     * @param purpose purpose of the candidate
     * @param now conditions that hold
     * @return whether it goes first
     */
    public static boolean keepsAlive(SkillPurpose purpose, Set<UseCondition> now) {
        var roles = purpose.roles();
        return purpose.damage() == DamageTier.NONE && (roles.contains(SkillRole.SUSTAIN)
                || roles.contains(SkillRole.DEFENSE)) && bestWhenNow(purpose.bestWhen(), now) > 0;
    }

    /**
     * How many declared conditions hold in the set captured now.
     *
     * @param declared conditions of the purpose
     * @param now conditions that hold
     * @return the size of the intersection
     */
    public static int bestWhenNow(Set<UseCondition> declared, Set<UseCondition> now) {
        var count = 0;
        for (var condition : declared) {
            if (now.contains(condition)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Ends a pattern reservation. The token comes back as {@link TokenEnd#YIELDED}, never {@link TokenEnd#RELEASED}
     * ({@link ReservationHold#giveBack} never returns it), and the cast time is left where it was.
     *
     * @param heldTicks ticks the reservation has been held
     * @param idleTimeoutTicks group idle timeout
     * @return a yield, or empty while the hold may continue
     */
    public static java.util.Optional<TokenEnd> yieldReservation(int heldTicks, int idleTimeoutTicks) {
        return ReservationHold.giveBack(heldTicks, idleTimeoutTicks);
    }
}
