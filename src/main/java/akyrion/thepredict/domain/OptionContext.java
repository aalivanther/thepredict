package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rule of M16.2: what of the moment favours an option and what advises against it, computed in code from facts
 * that are already in the state (G5: the arithmetic stays in code). Stateless, reads no clock, safe on any thread.
 *
 * <p>The reasons are checked in the order of {@link FitReason} and each list is cut at {@value #MAX_REASONS}. A
 * contingency is judged under its own hypothesis ({@link #forSituation}), the same one its instruction names (G6).
 */
public final class OptionContext {
    /** Most reasons in each list. */
    public static final int MAX_REASONS = 3;

    private OptionContext() {
    }

    /** One reason why an option fits or fits poorly now; its phrase lives in {@code adapter.jev} (01 §9). */
    public enum FitReason {
        /** Fits: the skill works best when the target's health is critical, and it is. */
        BEST_WHEN_TARGET_LOW_HEALTH,
        /** Fits: the skill works best against a shielding target, and it shields. */
        BEST_WHEN_TARGET_SHIELDING,
        /** Fits: the skill works best against a target backing away, and it backs away. */
        BEST_WHEN_TARGET_FLEEING,
        /** Fits: the skill works best when the caster's health is critical, and it is. */
        BEST_WHEN_SELF_LOW_HEALTH,
        /** Fits: the skill works best against grouped enemies, and they are. */
        BEST_WHEN_ENEMIES_GROUPED,
        /** Fits: the skill works best with the target outside melee reach, and it is. */
        BEST_WHEN_TARGET_AT_RANGE,
        /** Fits: the skill works best with the target inside melee reach, and it is. */
        BEST_WHEN_TARGET_IN_MELEE,
        /** Fits: a flank reaches around the target's shield. */
        FLANK_BEATS_SHIELD,
        /** Fits: the skill goes through the target's shield. */
        GOES_THROUGH_SHIELD,
        /** Poor: the declared reach does not get to the target and the movement does not close the distance. */
        OUT_OF_REACH,
        /** Poor: the skill needs a line of sight and there is none. */
        NO_LINE_OF_SIGHT,
        /** Poor: a damaging skill aimed at a shielding target, without a flank. */
        SHIELD_BLOCKS_IT,
        /** Poor: the skill exposes or roots the caster while its health is critical. */
        EXPOSED_WHILE_CRITICAL,
        /** Poor: the tactic needs to reach the target on foot and it cannot be reached. */
        CANNOT_REACH_ON_FOOT,
        /** Poor: a healing skill while the caster is healthy. */
        CASTER_ALREADY_HEALTHY,
        /** Poor: a slow projectile against a target backing away. */
        TARGET_CAN_DODGE;

        /**
         * The reason of one use condition that holds.
         *
         * @param condition a condition of the skill's {@code BestWhen}
         * @return its {@code BEST_WHEN_*} reason
         */
        public static FitReason bestWhen(UseCondition condition) {
            return switch (Objects.requireNonNull(condition, "condition")) {
                case TARGET_LOW_HEALTH -> BEST_WHEN_TARGET_LOW_HEALTH;
                case TARGET_SHIELDING -> BEST_WHEN_TARGET_SHIELDING;
                case TARGET_FLEEING -> BEST_WHEN_TARGET_FLEEING;
                case SELF_LOW_HEALTH -> BEST_WHEN_SELF_LOW_HEALTH;
                case ENEMIES_GROUPED -> BEST_WHEN_ENEMIES_GROUPED;
                case TARGET_AT_RANGE -> BEST_WHEN_TARGET_AT_RANGE;
                case TARGET_IN_MELEE -> BEST_WHEN_TARGET_IN_MELEE;
            };
        }
    }

    /**
     * What favours and what advises against one option, each in the order of {@link FitReason}.
     *
     * @param fits reasons that favour it, at most {@value #MAX_REASONS}
     * @param poorFits reasons against it, at most {@value #MAX_REASONS}
     */
    public record OptionFit(List<FitReason> fits, List<FitReason> poorFits) {
        /** Nothing favours nor advises against the option. */
        public static final OptionFit NONE = new OptionFit(List.of(), List.of());

        /**
         * Validates and copies the lists.
         *
         * @throws NullPointerException if a list or element is {@code null}
         * @throws IllegalArgumentException if a list is too long
         */
        public OptionFit {
            fits = List.copyOf(Objects.requireNonNull(fits, "fits"));
            poorFits = List.copyOf(Objects.requireNonNull(poorFits, "poorFits"));
            DomainChecks.requireMaximumSize(fits, MAX_REASONS, "fits");
            DomainChecks.requireMaximumSize(poorFits, MAX_REASONS, "poorFits");
        }
    }

    /**
     * Judges one option now.
     *
     * @param tactic option, declared by {@code profile}
     * @param profile profile of the mob
     * @param mob facts of the mob, with or without a target
     * @param now use conditions that hold ({@link ActiveUseConditions})
     * @return the reasons
     */
    public static OptionFit of(Tactic tactic, TacticalProfile profile, MobFacts mob, Set<UseCondition> now) {
        return judge(tactic, profile, mob, now, Situation.NOW);
    }

    /**
     * Judges one option under the hypothesis of a situation (M16.6): {@code IF_TARGET_IN_MELEE} replaces
     * {@code TARGET_AT_RANGE} and {@code TARGET_FLEEING} with {@code TARGET_IN_MELEE} and skips the reach measured at
     * the real distance; {@code IF_OWN_HEALTH_CRITICAL} adds {@code SELF_LOW_HEALTH} and treats the caster as critical.
     *
     * @param tactic option, declared by {@code profile}
     * @param profile profile of the mob
     * @param mob facts of the mob
     * @param now use conditions that hold now
     * @param situation situation of the question
     * @return the reasons
     */
    public static OptionFit forSituation(Tactic tactic, TacticalProfile profile, MobFacts mob, Set<UseCondition> now,
                                         Situation situation) {
        return judge(tactic, profile, mob, hypothetical(now, situation), situation);
    }

    /**
     * The use conditions under the hypothesis of a situation, as {@link #forSituation} applies them.
     *
     * @param now use conditions that hold now
     * @param situation situation of the question
     * @return a new set
     */
    public static Set<UseCondition> hypothetical(Set<UseCondition> now, Situation situation) {
        var conditions = now.isEmpty() ? EnumSet.noneOf(UseCondition.class) : EnumSet.copyOf(now);
        switch (Objects.requireNonNull(situation, "situation")) {
            case NOW -> {
            }
            case IF_TARGET_IN_MELEE -> {
                conditions.remove(UseCondition.TARGET_AT_RANGE);
                conditions.remove(UseCondition.TARGET_FLEEING);
                conditions.add(UseCondition.TARGET_IN_MELEE);
            }
            case IF_OWN_HEALTH_CRITICAL -> conditions.add(UseCondition.SELF_LOW_HEALTH);
        }
        return conditions;
    }

    private static OptionFit judge(Tactic tactic, TacticalProfile profile, MobFacts mob, Set<UseCondition> now,
                                   Situation situation) {
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(now, "now");
        var choice = tactic.choice().map(profile.choices()::get);
        var fits = fits(tactic, choice, now);
        var poor = new ArrayList<FitReason>(MAX_REASONS);
        choice.filter(SkillChoice::isTyped).ifPresent(found -> poorSkill(poor, tactic, found, profile, mob, now,
                situation));
        var relation = mob.target();
        var needsFoot = tactic.movement() == Movement.APPROACH || tactic.movement() == Movement.FLANK
                || choice.flatMap(SkillChoice::purpose).filter(found -> found.delivery() == Delivery.MELEE).isPresent();
        if (needsFoot && relation.filter(found -> !found.reachable()).isPresent()) {
            add(poor, FitReason.CANNOT_REACH_ON_FOOT);
        }
        var healthy = mob.health() == HealthBand.HEALTHY && !now.contains(UseCondition.SELF_LOW_HEALTH);
        if (healthy && choice.flatMap(SkillChoice::purpose).filter(found -> found.primary() == SkillRole.SUSTAIN)
                .isPresent()) {
            add(poor, FitReason.CASTER_ALREADY_HEALTHY);
        }
        if (now.contains(UseCondition.TARGET_FLEEING)
                && choice.flatMap(SkillChoice::purpose).filter(found -> found.risks().contains(SkillRisk.SLOW_PROJECTILE))
                .isPresent()) {
            add(poor, FitReason.TARGET_CAN_DODGE);
        }
        return new OptionFit(fits, poor);
    }

    private static List<FitReason> fits(Tactic tactic, Optional<SkillChoice> choice, Set<UseCondition> now) {
        var fits = new ArrayList<FitReason>(MAX_REASONS);
        var shielding = now.contains(UseCondition.TARGET_SHIELDING);
        choice.flatMap(SkillChoice::purpose).ifPresent(found -> EnumSet.allOf(UseCondition.class).stream()
                .filter(condition -> found.bestWhen().contains(condition) && now.contains(condition))
                .forEach(condition -> add(fits, FitReason.bestWhen(condition))));
        if (tactic.movement() == Movement.FLANK && shielding) {
            add(fits, FitReason.FLANK_BEATS_SHIELD);
        }
        choice.flatMap(SkillChoice::purpose).filter(found -> found.ignoresShield() && shielding)
                .ifPresent(found -> add(fits, FitReason.GOES_THROUGH_SHIELD));
        return fits;
    }

    private static void poorSkill(List<FitReason> poor, Tactic tactic, SkillChoice choice, TacticalProfile profile,
                                  MobFacts mob, Set<UseCondition> now, Situation situation) {
        var purpose = choice.typedPurpose();
        if (situation != Situation.IF_TARGET_IN_MELEE && mob.choicesOutOfReach().contains(choice.id())
                && !PurposeRules.closesDistance(tactic.movement(), purpose, profile.movement())) {
            add(poor, FitReason.OUT_OF_REACH);
        }
        if (purpose.risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT)
                && mob.target().filter(relation -> !relation.visible()).isPresent()) {
            add(poor, FitReason.NO_LINE_OF_SIGHT);
        }
        if (purpose.damaging() && purpose.aimedAtTarget() && !purpose.ignoresShield()
                && now.contains(UseCondition.TARGET_SHIELDING) && tactic.movement() != Movement.FLANK) {
            add(poor, FitReason.SHIELD_BLOCKS_IT);
        }
        var exposing = purpose.risks().contains(SkillRisk.LEAVES_CASTER_EXPOSED)
                || purpose.risks().contains(SkillRisk.ROOTS_CASTER);
        if (exposing && now.contains(UseCondition.SELF_LOW_HEALTH)) {
            add(poor, FitReason.EXPOSED_WHILE_CRITICAL);
        }
    }

    private static void add(List<FitReason> reasons, FitReason reason) {
        if (reasons.size() < MAX_REASONS) {
            reasons.add(reason);
        }
    }
}
