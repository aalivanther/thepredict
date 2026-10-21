package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;

import java.util.UUID;

/**
 * The own hits of one mob still in flight toward its target, so a damaging cast does not land inside the window of
 * invulnerability its own earlier hits open (fase de efectividad E-1, finding P1 of the bench v2). The window follows
 * vanilla ({@code LivingEntity.hurt}): a hit that lands outside a window deals its damage and opens one for
 * {@value #WINDOW_TICKS} ticks; a hit inside it deals only what exceeds the strongest hit of that window, becomes the
 * strongest, and does not reopen it; a hit no stronger deals nothing. Damage is compared by the tier of the purpose
 * ({@link #weight}), because the engine never reads a raw amount. Two hits inside one window always deal less than
 * apart, so {@link #freeLanding} gives the first landing at which a new hit takes nothing from itself or from a hit in
 * flight.
 *
 * <p>At most {@value #SLOTS} hits are kept (rule A5), sorted by landing; a fifth forgets the earliest. The ring belongs
 * to one target and empties when the target changes. It also keeps, for the same target, the largest share of health
 * one own hit took ({@link KillWindow}), and, per choice, since when it waits for another hit ({@link StrongerFirst}).
 * Server thread only; the per-tick path allocates nothing (rule C12): only the wait marks grow, once, to the size of
 * the profile.
 */
final class OwnImpacts {
    /** Hits in flight kept per mob (A5). */
    static final int SLOTS = 4;
    /** Ticks a hit that deals damage keeps a weaker or equal hit out: half of the vanilla 20 of a hurt mob. */
    static final int WINDOW_TICKS = 10;
    /** Weight of a hit whose amount is unknown: an untyped choice, a vanilla hit, an unrated tier. */
    static final int UNKNOWN_WEIGHT = 2;

    private final long[] landing = new long[SLOTS];
    private final int[] weight = new int[SLOTS];
    private final long[] simTick = new long[SLOTS + 1];
    private final int[] simWeight = new int[SLOTS + 1];
    private long[] waitSince = new long[0];
    private double strongest;
    private int count;
    private boolean bound;
    private long targetHigh;
    private long targetLow;

    /** Forgets every hit, the target, the strongest share and the waits for stronger hits. */
    void clear() {
        count = 0;
        bound = false;
        strongest = 0.0;
        java.util.Arrays.fill(waitSince, Long.MIN_VALUE);
    }

    /**
     * Notes the share of its maximum health one own hit took from the target ({@code OutcomeEvents.dealt}), for the
     * kill window. A hit on another target first moves the ring to it.
     *
     * @param target the victim, the current target
     * @param share the health it lost, as a fraction of its maximum
     */
    void noteDealt(UUID target, double share) {
        retarget(target);
        if (share > strongest && share <= 1.0) {
            strongest = share;
        }
    }

    /**
     * The largest share of the target's maximum health one own hit took from it, 0 before the first hit.
     *
     * @return the share, within {@code [0, 1]}
     */
    double strongestShare() {
        return strongest;
    }

    /**
     * Whether a choice may still wait for a stronger own hit ({@link StrongerFirst}): its wait in a row has lasted
     * less than {@code maxTicks}. A wait that reached it ends here, so the choice goes now and may wait again later.
     *
     * @param index declaration index of the choice, {@code -1} never waits
     * @param tick the current tick
     * @param maxTicks the longest wait in a row
     * @return whether it may wait
     */
    boolean mayWait(int index, long tick, int maxTicks) {
        if (index < 0) {
            return false;
        }
        if (index >= waitSince.length) {
            var grown = java.util.Arrays.copyOf(waitSince, index + 1);
            java.util.Arrays.fill(grown, waitSince.length, grown.length, Long.MIN_VALUE);
            waitSince = grown;
        }
        var since = waitSince[index];
        if (since != Long.MIN_VALUE && tick - since >= maxTicks) {
            waitSince[index] = Long.MIN_VALUE;
            return false;
        }
        return true;
    }

    /**
     * Notes whether a choice waits for a stronger own hit on this tick: a wait starts its run, a go ends it.
     *
     * @param index declaration index of the choice, already seen by {@link #mayWait}
     * @param tick the current tick
     * @param waits whether it waits
     */
    void waiting(int index, long tick, boolean waits) {
        if (!waits) {
            waitSince[index] = Long.MIN_VALUE;
        } else if (waitSince[index] == Long.MIN_VALUE) {
            waitSince[index] = tick;
        }
    }

    /** How many hits are kept now, for tests and {@code inspect}. */
    int size() {
        return count;
    }

    /**
     * Keeps the ring for this target, emptying it when the target changes.
     *
     * @param target the target of the mob now
     */
    void retarget(UUID target) {
        if (!bound || !same(target)) {
            count = 0;
            strongest = 0.0;
            bound = true;
            targetHigh = target.getMostSignificantBits();
            targetLow = target.getLeastSignificantBits();
        }
    }

    /**
     * Notes a damaging hit that started now.
     *
     * @param target the target of the cast
     * @param landingTick the tick its damage lands
     * @param hitWeight its {@link #weight}, positive
     * @param now the current tick
     */
    void note(UUID target, long landingTick, int hitWeight, long now) {
        retarget(target);
        forget(now);
        if (count == SLOTS) {
            System.arraycopy(landing, 1, landing, 0, SLOTS - 1);
            System.arraycopy(weight, 1, weight, 0, SLOTS - 1);
            count--;
        }
        var at = count;
        while (at > 0 && landing[at - 1] > landingTick) {
            landing[at] = landing[at - 1];
            weight[at] = weight[at - 1];
            at--;
        }
        landing[at] = landingTick;
        weight[at] = hitWeight;
        count++;
    }

    /**
     * The first landing, from {@code landingTick} on, at which a new hit of this weight deals all of it and takes
     * nothing from a hit in flight: {@code landingTick} itself when nothing overlaps, otherwise the end of a window
     * of the ring. A ring of another target never delays.
     *
     * @param target the target of the cast
     * @param landingTick the tick the hit would land if cast now
     * @param hitWeight its {@link #weight}
     * @param now the current tick
     * @return the free landing, never before {@code landingTick}
     */
    long freeLanding(UUID target, long landingTick, int hitWeight, long now) {
        if (!bound || !same(target) || hitWeight <= 0) {
            return landingTick;
        }
        forget(now);
        if (count == 0) {
            return landingTick;
        }
        var alone = dealt(Long.MIN_VALUE, 0);
        if (dealt(landingTick, hitWeight) == alone + hitWeight) {
            return landingTick;
        }
        for (var index = 0; index < count; index++) {
            var end = landing[index] + WINDOW_TICKS;
            if (end > landingTick && dealt(end, hitWeight) == alone + hitWeight) {
                return end;
            }
        }
        return landing[count - 1] + WINDOW_TICKS;
    }

    /**
     * What the hits of the ring deal under the vanilla rule, with one more hit when {@code extraWeight} is positive;
     * a hit that lands on the tick of a kept one comes after it.
     */
    private int dealt(long extraTick, int extraWeight) {
        var size = 0;
        var placed = extraWeight <= 0;
        for (var index = 0; index < count; index++) {
            if (!placed && landing[index] > extraTick) {
                simTick[size] = extraTick;
                simWeight[size++] = extraWeight;
                placed = true;
            }
            simTick[size] = landing[index];
            simWeight[size++] = weight[index];
        }
        if (!placed) {
            simTick[size] = extraTick;
            simWeight[size++] = extraWeight;
        }
        return vanilla(size);
    }

    private int vanilla(int size) {
        var total = 0;
        var windowEnd = Long.MIN_VALUE;
        var strongest = 0;
        for (var index = 0; index < size; index++) {
            var at = simTick[index];
            var amount = simWeight[index];
            if (at >= windowEnd) {
                total += amount;
                windowEnd = at + WINDOW_TICKS;
                strongest = amount;
            } else if (amount > strongest) {
                total += amount - strongest;
                strongest = amount;
            }
        }
        return total;
    }

    /** Drops the hits whose window has passed. */
    private void forget(long now) {
        var kept = 0;
        for (var index = 0; index < count; index++) {
            if (landing[index] + WINDOW_TICKS > now) {
                landing[kept] = landing[index];
                weight[kept++] = weight[index];
            }
        }
        count = kept;
    }

    private boolean same(UUID target) {
        return target.getMostSignificantBits() == targetHigh && target.getLeastSignificantBits() == targetLow;
    }

    /**
     * Whether a cast of this choice is a hit the window can take: a vanilla hit, or a choice that needs a target, deals
     * damage by its purpose or the untyped default, and does not ignore invulnerability.
     *
     * @param choice the choice
     * @return whether the ring counts it
     */
    static boolean hits(SkillChoice choice) {
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        if (!choice.requiresTarget() || choice.nativeFacts().damageIgnoresInvulnerability()) {
            return false;
        }
        var purpose = choice.purpose();
        return purpose.isPresent() ? purpose.get().damaging() : PurposeRules.UNTYPED_DEFAULTS.damaging();
    }

    /**
     * The weight of a hit for the vanilla comparison: {@code LOW} 1, {@code MEDIUM} 2, {@code HIGH} 3, and
     * {@value #UNKNOWN_WEIGHT} when the amount is unknown (no purpose or {@code UNRATED}); {@code NONE} is 0.
     *
     * @param choice the choice
     * @return the weight
     */
    static int weight(SkillChoice choice) {
        var purpose = choice.purpose();
        return purpose.isPresent() ? weight(purpose.get()) : UNKNOWN_WEIGHT;
    }

    private static int weight(SkillPurpose purpose) {
        return switch (purpose.damage()) {
            case NONE -> 0;
            case LOW -> 1;
            case UNRATED, MEDIUM -> UNKNOWN_WEIGHT;
            case HIGH -> 3;
        };
    }
}
