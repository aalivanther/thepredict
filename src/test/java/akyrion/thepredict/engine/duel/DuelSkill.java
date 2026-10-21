package akyrion.thepredict.engine.duel;

import java.util.List;
import java.util.Optional;

/**
 * One skill of a duel kit, as the simulator plays it (M18.7). The five constants are the skills of the duel pack
 * (kit {@code K1}): the table replicates {@code ThePredictDuelSkills.yml} and the timers of {@code DuelBase}, and
 * {@code DuelMatrixTest} reads the pack and fails when they differ. Other kits of the effectiveness bench build their
 * own skills with {@link #of(String, double, int, int, double, double, double, double, Effect, int)}. A skill is
 * compared by identity; its {@link #ordinal()} is its slot in its kit.
 */
final class DuelSkill {
    /** Slots a body keeps cooldowns for. */
    static final int MAX_SLOTS = 8;

    /** What a skill does besides damage, shield and heal. */
    enum Effect {
        /** Nothing else. */
        NONE,
        /** {@code leap @target}: the caster lands {@value DuelWorld#LEAP_LANDING} blocks from its target. */
        LEAP,
        /** Needs line of sight to the target, as {@code TargetConditions: lineofsight true}. */
        SIGHT
    }

    static final DuelSkill STRIKE = new DuelSkill("DuelStrike", 5, 5, 30, 3, 1.0, 0, 0, Effect.NONE, 0);
    static final DuelSkill BOLT = new DuelSkill("DuelBolt", 3, 10, 60, 14, 1.0, 0, 0, Effect.SIGHT, 1);
    static final DuelSkill LEAP = new DuelSkill("DuelLeap", 2, 10, 120, 8, 1.0, 0, 0, Effect.LEAP, 2);
    static final DuelSkill GUARD = new DuelSkill("DuelGuard", 0, 0, 160, 0, 1.0, 6, 0, Effect.NONE, 3);
    static final DuelSkill HEAL = new DuelSkill("DuelHeal", 0, 0, 200, 0, 0.5, 0, 6, Effect.NONE, 4);

    /** The vanilla hit, only as a name in the counts of a round; it is never cast through a world. */
    static final DuelSkill VANILLA_HIT = new DuelSkill("vanilla", 0, 0, 0, 0, 1.0, 0, 0, Effect.NONE,
            MAX_SLOTS - 1);

    private static final DuelSkill[] PACK = {STRIKE, BOLT, LEAP, GUARD, HEAL};

    /** A classic rule: the skill, its timer and the conditions after {@code predictorcontrol false}. */
    record Rule(DuelSkill skill, int timerTicks, double notWithinBlocks, double withinBlocks, double healthBelow) {
    }

    /** The rules in the order of the {@code Skills} of {@code DuelBase}. */
    static final List<Rule> RULES = List.of(new Rule(HEAL, 20, 0, 0, 0.5), new Rule(GUARD, 20, 0, 3, 0.6),
            new Rule(STRIKE, 10, 0, 3, 1.0), new Rule(LEAP, 20, 4, 8, 1.0), new Rule(BOLT, 20, 4, 14, 1.0));

    final String id;
    final double damage;
    final int delayTicks;
    final int cooldownTicks;
    final double withinBlocks;
    final double healthBelow;
    final double shield;
    final double heal;
    final Effect effect;
    private final int slot;

    private DuelSkill(String id, double damage, int delayTicks, int cooldownTicks, double withinBlocks,
                      double healthBelow, double shield, double heal, Effect effect, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            throw new IllegalArgumentException("slot must be within [0, " + MAX_SLOTS + "), was " + slot);
        }
        this.id = id;
        this.damage = damage;
        this.delayTicks = delayTicks;
        this.cooldownTicks = cooldownTicks;
        this.withinBlocks = withinBlocks;
        this.healthBelow = healthBelow;
        this.shield = shield;
        this.heal = heal;
        this.effect = effect;
        this.slot = slot;
    }

    /**
     * A skill of another kit.
     *
     * @param id MythicMobs id
     * @param damage damage of {@code damage{a=…}} at the target, 0 for none
     * @param delayTicks the {@code delay} before the effect lands
     * @param cooldownTicks the native cooldown, in ticks
     * @param withinBlocks the {@code targetwithin{d=…} true} of the skill, 0 for none
     * @param healthBelow the {@code healthpercent{p=<…}} of the skill as a ratio, 1 for none
     * @param shield absorption of {@code shield{amount=…}}, 0 for none
     * @param heal health of {@code heal{a=…}}, 0 for none
     * @param effect the extra effect
     * @param slot the slot in its kit, within {@code [0, MAX_SLOTS)}
     * @return the skill
     */
    static DuelSkill of(String id, double damage, int delayTicks, int cooldownTicks, double withinBlocks,
                        double healthBelow, double shield, double heal, Effect effect, int slot) {
        return new DuelSkill(id, damage, delayTicks, cooldownTicks, withinBlocks, healthBelow, shield, heal, effect,
                slot);
    }

    /**
     * The five skills of the duel pack, in slot order.
     *
     * @return a new array
     */
    static DuelSkill[] values() {
        return PACK.clone();
    }

    /**
     * The slot of the skill in its kit.
     *
     * @return the slot
     */
    int ordinal() {
        return slot;
    }

    /** Whether the skill moves its caster to its target. */
    boolean leaps() {
        return effect == Effect.LEAP;
    }

    /** Whether the skill needs line of sight to its target. */
    boolean needsSight() {
        return effect == Effect.SIGHT;
    }

    /**
     * A skill of the duel pack by MythicMobs id.
     *
     * @param id the id
     * @return the skill, empty for any other id
     */
    static Optional<DuelSkill> of(String id) {
        for (var skill : PACK) {
            if (skill.id.equals(id)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }
}
