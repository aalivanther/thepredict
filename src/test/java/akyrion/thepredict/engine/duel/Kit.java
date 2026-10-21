package akyrion.thepredict.engine.duel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The equipment both duelists of a scenario share (§7.4 of the simulator audit): the skills and the damage of the
 * vanilla hit. {@link #K1} is the duel pack; the others are built here so both sides and the generated ThePredict
 * profile read the same numbers. A value the audit does not give (the {@code delay} of a strike, the reach of a leap)
 * is the one of the same role in {@link #K1}.
 *
 * @param id short id, {@code K0} to {@code K4}
 * @param label name in the report
 * @param skills skills in slot order
 * @param vanillaDamage damage of the vanilla hit every 20 ticks, the {@code Damage} of the mob
 */
record Kit(String id, String label, List<DuelSkill> skills, double vanillaDamage) {
    /** Role of a skill, derived from what it does. */
    enum Role {
        /** Damage at melee reach. */
        STRIKE,
        /** Damage at range that needs line of sight. */
        BOLT,
        /** Damage that moves the caster to its target. */
        LEAP,
        /** Absorption on the caster. */
        GUARD,
        /** Health on the caster. */
        HEAL
    }

    /** No skills: a zombie with a vanilla hit of 3. */
    static final Kit K0 = new Kit("K0", "vanilla", List.of(), 3);
    /** The five skills of the duel pack and {@code Damage: 0}. */
    static final Kit K1 = new Kit("K1", "pack", List.of(DuelSkill.values()), 0);
    /** Bolt 4 (2 s, {@code delay} 10, below 16 blocks), strike 3 (1.5 s), guard 4 (8 s). */
    static final Kit K2 = new Kit("K2", "ranged", List.of(
            DuelSkill.of("RangedBolt", 4, 10, 40, 16, 1.0, 0, 0, DuelSkill.Effect.SIGHT, 0),
            DuelSkill.of("RangedStrike", 3, 5, 30, 3, 1.0, 0, 0, DuelSkill.Effect.NONE, 1),
            DuelSkill.of("RangedGuard", 0, 0, 160, 0, 1.0, 4, 0, DuelSkill.Effect.NONE, 2)), 0);
    /** Strike 8 (4 s, {@code delay} 8), leap 4 (6 s), guard 10 (12 s). */
    static final Kit K3 = new Kit("K3", "burst", List.of(
            DuelSkill.of("BurstStrike", 8, 8, 80, 3, 1.0, 0, 0, DuelSkill.Effect.NONE, 0),
            DuelSkill.of("BurstLeap", 4, 10, 120, 8, 1.0, 0, 0, DuelSkill.Effect.LEAP, 1),
            DuelSkill.of("BurstGuard", 0, 0, 240, 0, 1.0, 10, 0, DuelSkill.Effect.NONE, 2)), 0);
    /**
     * Heal 8 (8 s, below 60 %), guard 6 (6 s), strike 6 (2 s): K4', registered on 2026-10-14 before any further
     * measurement. The audit's strike of 4 dealt 2 health per second against 2 of heal and guard, so no rule set
     * could win and every round was a draw by construction (finding P7 of the bench).
     */
    static final Kit K4 = new Kit("K4", "sustain", List.of(
            DuelSkill.of("SustainHeal", 0, 0, 160, 0, 0.6, 0, 8, DuelSkill.Effect.NONE, 0),
            DuelSkill.of("SustainGuard", 0, 0, 120, 0, 1.0, 6, 0, DuelSkill.Effect.NONE, 1),
            DuelSkill.of("SustainStrike", 6, 5, 40, 3, 1.0, 0, 0, DuelSkill.Effect.NONE, 2)), 0);

    /** Every kit, in report order. */
    static final List<Kit> ALL = List.of(K0, K1, K2, K3, K4);

    /**
     * Validates the kit.
     *
     * @throws IllegalArgumentException when a slot is repeated or out of order
     */
    Kit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        skills = List.copyOf(skills);
        for (var index = 0; index < skills.size(); index++) {
            if (skills.get(index).ordinal() != index) {
                throw new IllegalArgumentException("skill " + skills.get(index).id + " is not in slot " + index);
            }
        }
    }

    /**
     * A skill of this kit by MythicMobs id.
     *
     * @param mythicId the id
     * @return the skill, empty for another id
     */
    Optional<DuelSkill> skill(String mythicId) {
        for (var skill : skills) {
            if (skill.id.equals(mythicId)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    /**
     * The skill of a role.
     *
     * @param role the role
     * @return the first skill with it, empty when the kit lacks it
     */
    Optional<DuelSkill> skill(Role role) {
        for (var skill : skills) {
            if (role(skill) == role) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    /**
     * The role of a skill.
     *
     * @param skill a skill
     * @return its role
     */
    static Role role(DuelSkill skill) {
        if (skill.heal > 0) {
            return Role.HEAL;
        }
        if (skill.shield > 0) {
            return Role.GUARD;
        }
        if (skill.leaps()) {
            return Role.LEAP;
        }
        return skill.needsSight() ? Role.BOLT : Role.STRIKE;
    }
}
