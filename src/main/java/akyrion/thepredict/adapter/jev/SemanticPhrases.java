package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.BaseStance;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.OptionContext.FitReason;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;

/**
 * Fixed English phrases for every enum that reaches Jev (plan §6, "semantic state"). Exhaustive switches
 * make a new enum value without a phrase a compile error. Phrases with a subject take it as a parameter,
 * so {@code subject = "target"} gives the plan's wording ({@code target is inside melee reach}) and a
 * participant alias gives {@code player_1 is blocking} or {@code entity_1 is blocking}; an entity is introduced by its
 * species ({@code entity_1 is a zombie}, M17.9). Since M16.2 it also holds the short forms of the {@code COMPACT}
 * detail, the phrases of what fits an option now and the instructions of each detail; a single table of fixed phrases
 * keeps the wording of the state and of the options in one place, so Jev can match them literally.
 */
final class SemanticPhrases {
    /** Margin over {@code meleeReachBlocks} within which a skill still reaches "only inside melee reach" (M15.7). */
    static final double MELEE_REACH_MARGIN_BLOCKS = 0.5;
    /** Largest radius of a small area, in blocks. */
    static final double SMALL_AREA_BLOCKS = 3.0;
    /** Largest radius of a medium area, in blocks. */
    static final double MEDIUM_AREA_BLOCKS = 6.0;
    /** Longest short wind-up, in ticks. */
    static final int SHORT_WIND_UP_TICKS = 10;

    private SemanticPhrases() {
    }

    static String health(HealthBand band, String subject) {
        return subject + switch (band) {
            case CRITICAL -> "'s health is critical";
            case WOUNDED -> " is wounded";
            case HEALTHY -> " is healthy";
        };
    }

    static String distance(DistanceBand band, String subject) {
        return subject + switch (band) {
            case MELEE -> " is inside melee reach";
            case RANGED -> " is outside melee reach but within ranged reach";
            case BEYOND -> " is beyond every attack range";
        };
    }

    static String trend(Trend trend, String subject) {
        return subject + switch (trend) {
            case CLOSING -> " is closing in";
            case STEADY -> " keeps the same distance";
            case BACKING_AWAY -> " is backing away";
            case STRAFING -> " is strafing";
        };
    }

    static String armor(ArmorBand band, String subject) {
        return subject + switch (band) {
            case LIGHT -> " is lightly armored";
            case MEDIUM -> " is moderately armored";
            case HEAVY -> " is heavily armored";
        };
    }

    static String mobCue(MobCue cue, String subject) {
        return subject + switch (cue) {
            case IN_WATER -> " is standing in water";
            case IN_LAVA -> " is standing in lava";
            case CLIMBING -> " is climbing";
            case NO_LINE_OF_SIGHT -> " has no line of sight to its target";
        };
    }

    static String targetCue(TargetCue cue, String subject) {
        return switch (cue) {
            case RAISING_SHIELD -> subject + " is raising a shield but not blocking yet";
            case BLOCKING -> subject + " is blocking";
            case SHIELD_DISABLED -> subject + "'s shield is disabled";
            case ATTACK_RECHARGING -> subject + "'s attack is recharging";
            case DRAWING_BOW -> subject + " is drawing a bow";
            case BOW_FULLY_DRAWN -> subject + " has a fully drawn bow";
            case CROSSBOW_LOADED -> subject + " has a loaded crossbow";
            case CONSUMING -> subject + " is drinking or eating";
            case HOLDS_TOTEM -> subject + " holds a totem of undying";
            case PEARL_ON_COOLDOWN -> subject + " cannot throw ender pearls for a while";
            case GLIDING -> subject + " is gliding";
            case LONG_REACH_WEAPON -> subject + " wields a long-reach weapon";
            case MACE_DIVE -> subject + " is diving for a mace smash";
            case INVISIBLE -> subject + " is invisible";
        };
    }

    static String event(EventType type, String participant, String mob, boolean entity) {
        return switch (type) {
            case MELEE_HIT -> mob + " hit " + participant + " in melee";
            case MELEE_BLOCKED -> participant + " blocked a melee attack from " + mob;
            case RANGED_HIT_ON_MOB -> participant + " hit " + mob + " with a projectile";
            case RANGED_MISS -> participant + " missed a projectile shot";
            case TOTEM_USED -> participant + " used a totem of undying";
            case PEARL_THROWN -> participant + " threw an ender pearl";
            case POTION_CONSUMED -> participant + " drank a potion";
            case SHIELD_DISABLED -> participant + "'s shield was disabled";
            case ALLY_ASSISTED -> participant + (entity ? " helped an ally" : " helped another player");
            case GROUP_ASSIGNMENT -> participant + " drew the group's attention";
        };
    }

    /**
     * The first fact of an entity participant (M17.9): its species, the key of its entity type with spaces.
     *
     * @param subject alias of the participant, or {@code target}
     * @param species key of the entity type without namespace, such as {@code wither_skeleton}
     * @return for example {@code entity_1 is a wither skeleton} or {@code entity_2 is an enderman}
     */
    static String species(String subject, String species) {
        var words = species.replace('_', ' ');
        return subject + (words.isEmpty() || "aeiou".indexOf(words.charAt(0)) < 0 ? " is a " : " is an ") + words;
    }

    static String stance(BaseStance stance) {
        return switch (stance) {
            case NEUTRAL -> "while keeping a neutral stance";
            case PRESSING -> "while keeping a pressing stance";
            case GUARDED -> "while keeping a guarded stance";
            case EVASIVE -> "while keeping an evasive stance";
        };
    }

    static String movement(Movement movement) {
        return switch (movement) {
            case APPROACH -> "move toward the target and fight up close";
            case KEEP_DISTANCE -> "stay between the inner and outer distance band";
            case RETREAT -> "retreat away from the target";
            case HOLD -> "hold position";
            case FLANK -> "circle to the side of the target before striking";
            case NONE -> "keep the native movement of the mob";
        };
    }

    static String delivery(Delivery delivery) {
        return switch (delivery) {
            case MELEE -> "strikes the target up close";
            case PROJECTILE -> "fires a projectile at the target";
            case HITSCAN -> "hits the target instantly from a distance";
            case AREA_AROUND_SELF -> "hits every enemy around the caster";
            case AREA_AT_TARGET -> "hits an area around the target";
            case CONE -> "sweeps a cone in front of the caster";
            case SELF -> "acts only on the caster";
        };
    }

    static String role(SkillRole role) {
        return switch (role) {
            case DAMAGE -> "to deal damage";
            case CONTROL -> "to stun, slow or push the target";
            case GAP_CLOSE -> "to close the distance quickly";
            case ESCAPE -> "to get the caster out of danger quickly";
            case DEFENSE -> "to protect the caster from damage";
            case SUSTAIN -> "to restore the caster's health";
            case BUFF -> "to strengthen the caster";
            case DEBUFF -> "to weaken the target";
            case SUMMON -> "to summon allies";
            case ZONE -> "to deny an area to enemies for a while";
            case INTERRUPT -> "to interrupt what the target is doing";
            case FINISHER -> "to finish off a weakened target";
            case OPENER -> "to open the fight with an advantage";
        };
    }

    /**
     * Reach band of a skill with the thresholds of the profile, the same ones as the distance facts of the state, so
     * a skill that works "only inside melee reach" and a target "outside melee reach" contradict each other literally.
     */
    static String reach(double reachBlocks, MovementSettings movement) {
        if (reachBlocks <= movement.meleeReachBlocks() + MELEE_REACH_MARGIN_BLOCKS) {
            return "only inside melee reach";
        }
        if (reachBlocks <= movement.bandOuterBlocks()) {
            return "a short distance beyond melee reach";
        }
        return reachBlocks <= movement.rangedReachBlocks() ? "within ranged reach" : "beyond ranged reach";
    }

    static String area(double radiusBlocks) {
        if (radiusBlocks <= SMALL_AREA_BLOCKS) {
            return "in a small area";
        }
        return radiusBlocks <= MEDIUM_AREA_BLOCKS ? "in a medium area" : "in a large area";
    }

    static String windUp(int windUpTicks) {
        if (windUpTicks == 0) {
            return "at once";
        }
        return windUpTicks <= SHORT_WIND_UP_TICKS ? "after a short wind-up" : "after a long, visible wind-up";
    }

    /** Cooldown phrase; empty for {@code UNKNOWN}, which says nothing. */
    static String cooldown(CooldownClass cooldown) {
        return switch (cooldown) {
            case NONE, SHORT -> "can be used again soon";
            case MEDIUM -> "has a moderate cooldown";
            case LONG -> "has a long cooldown";
            case UNKNOWN -> "";
        };
    }

    /** Damage phrase; empty for {@code NONE}, which says nothing. */
    static String damage(DamageTier damage) {
        return switch (damage) {
            case UNRATED -> "deals damage";
            case LOW -> "deals low damage";
            case MEDIUM -> "deals medium damage";
            case HIGH -> "deals high damage";
            case NONE -> "";
        };
    }

    static String useCondition(UseCondition condition) {
        return switch (condition) {
            case TARGET_LOW_HEALTH -> "the target's health is critical";
            case TARGET_SHIELDING -> "the target is blocking or raising a shield";
            case TARGET_FLEEING -> "the target is backing away";
            case SELF_LOW_HEALTH -> "the caster's health is critical";
            case ENEMIES_GROUPED -> "the target stands close to other enemies";
            case TARGET_AT_RANGE -> "the target is outside melee reach";
            case TARGET_IN_MELEE -> "the target is inside melee reach";
        };
    }

    static String risk(SkillRisk risk) {
        return switch (risk) {
            case LEAVES_CASTER_EXPOSED -> "leaves the caster exposed afterwards";
            case ROOTS_CASTER -> "the caster cannot move while using it";
            case SELF_DAMAGE -> "hurts the caster";
            case NEEDS_LINE_OF_SIGHT -> "needs a clear line of sight";
            case SLOW_PROJECTILE -> "a moving target can dodge it";
            case BLOCKED_BY_SHIELD -> "a raised shield blocks it";
            case HITS_ALLIES -> "can also hit allies";
        };
    }

    static String ignoresShield() {
        return "goes through shields";
    }

    static String grouped() {
        return "target stands close to other enemies";
    }

    /** Short movement of the {@code COMPACT} detail (M16.2). */
    static String movementShort(Movement movement) {
        return switch (movement) {
            case APPROACH -> "close in";
            case KEEP_DISTANCE -> "hold mid range";
            case RETREAT -> "retreat";
            case HOLD -> "hold position";
            case FLANK -> "circle to the target's side";
            case NONE -> "native movement";
        };
    }

    /** Short delivery of the {@code COMPACT} detail. */
    static String deliveryShort(Delivery delivery) {
        return switch (delivery) {
            case MELEE -> "melee hit";
            case PROJECTILE -> "projectile";
            case HITSCAN -> "instant ranged hit";
            case AREA_AROUND_SELF -> "area hit around self";
            case AREA_AT_TARGET -> "area hit at target";
            case CONE -> "cone sweep";
            case SELF -> "self";
        };
    }

    /** Short main role of the {@code COMPACT} detail; empty for {@code DAMAGE}, which the damage piece already says. */
    static String roleShort(SkillRole role) {
        return switch (role) {
            case DAMAGE -> "";
            case CONTROL -> "stun or slow";
            case GAP_CLOSE -> "gap closer";
            case ESCAPE -> "escape";
            case DEFENSE -> "self shield";
            case SUSTAIN -> "self heal";
            case BUFF -> "self buff";
            case DEBUFF -> "weakens target";
            case SUMMON -> "summons allies";
            case ZONE -> "area denial";
            case INTERRUPT -> "interrupt";
            case FINISHER -> "finisher";
            case OPENER -> "opener";
        };
    }

    /** Short damage of the {@code COMPACT} detail; empty for {@code NONE}. */
    static String damageShort(DamageTier damage) {
        return switch (damage) {
            case UNRATED -> "damage";
            case LOW -> "low damage";
            case MEDIUM -> "medium damage";
            case HIGH -> "high damage";
            case NONE -> "";
        };
    }

    /** Short wind-up of the {@code COMPACT} detail: only a long wind-up written by the author is said. */
    static String windUpShort(int windUpTicks) {
        return windUpTicks > SHORT_WIND_UP_TICKS ? "slow wind-up" : "";
    }

    /** Short reach band of the {@code REACH_BAND} discriminator, with the thresholds of {@link #reach}. */
    static String reachShort(double reachBlocks, MovementSettings movement) {
        if (reachBlocks <= movement.meleeReachBlocks() + MELEE_REACH_MARGIN_BLOCKS) {
            return "melee";
        }
        if (reachBlocks <= movement.bandOuterBlocks()) {
            return "short";
        }
        return reachBlocks <= movement.rangedReachBlocks() ? "ranged" : "far";
    }

    /** Short use condition of the {@code BEST_WHEN} discriminator. */
    static String useConditionShort(UseCondition condition) {
        return switch (condition) {
            case TARGET_LOW_HEALTH -> "target nearly dead";
            case TARGET_SHIELDING -> "target shielding";
            case TARGET_FLEEING -> "target fleeing";
            case SELF_LOW_HEALTH -> "caster nearly dead";
            case ENEMIES_GROUPED -> "enemies grouped";
            case TARGET_AT_RANGE -> "target at range";
            case TARGET_IN_MELEE -> "target in melee";
        };
    }

    /** Phrase of one reason of {@code fits_now} or {@code poor_fit_now} in the {@code FULL} detail. */
    static String fitReason(FitReason reason) {
        return switch (reason) {
            case BEST_WHEN_TARGET_LOW_HEALTH -> useCondition(UseCondition.TARGET_LOW_HEALTH);
            case BEST_WHEN_TARGET_SHIELDING -> useCondition(UseCondition.TARGET_SHIELDING);
            case BEST_WHEN_TARGET_FLEEING -> useCondition(UseCondition.TARGET_FLEEING);
            case BEST_WHEN_SELF_LOW_HEALTH -> useCondition(UseCondition.SELF_LOW_HEALTH);
            case BEST_WHEN_ENEMIES_GROUPED -> useCondition(UseCondition.ENEMIES_GROUPED);
            case BEST_WHEN_TARGET_AT_RANGE -> useCondition(UseCondition.TARGET_AT_RANGE);
            case BEST_WHEN_TARGET_IN_MELEE -> useCondition(UseCondition.TARGET_IN_MELEE);
            case FLANK_BEATS_SHIELD -> "the target's shield does not cover its sides";
            case GOES_THROUGH_SHIELD -> "it goes through the target's shield";
            case OUT_OF_REACH -> "the target is out of this skill's reach";
            case NO_LINE_OF_SIGHT -> "there is no line of sight to the target";
            case SHIELD_BLOCKS_IT -> "the target's shield would block it";
            case EXPOSED_WHILE_CRITICAL -> "the caster's health is critical and this leaves it exposed";
            case CANNOT_REACH_ON_FOOT -> "the target cannot be reached on foot";
            case CASTER_ALREADY_HEALTHY -> "the caster is healthy";
            case TARGET_CAN_DODGE -> "a target backing away can dodge it";
        };
    }

    /** Short phrase of a poor fit in the {@code COMPACT} detail; empty for a reason that favours the option. */
    static String poorFitShort(FitReason reason) {
        return switch (reason) {
            case OUT_OF_REACH -> "out of reach now";
            case NO_LINE_OF_SIGHT -> "no line of sight now";
            case SHIELD_BLOCKS_IT -> "shield would block it";
            case EXPOSED_WHILE_CRITICAL -> "too risky at critical health";
            case CANNOT_REACH_ON_FOOT -> "target unreachable on foot";
            case CASTER_ALREADY_HEALTHY -> "caster already healthy";
            case TARGET_CAN_DODGE -> "target can dodge it";
            case BEST_WHEN_TARGET_LOW_HEALTH, BEST_WHEN_TARGET_SHIELDING, BEST_WHEN_TARGET_FLEEING,
                 BEST_WHEN_SELF_LOW_HEALTH, BEST_WHEN_ENEMIES_GROUPED, BEST_WHEN_TARGET_AT_RANGE,
                 BEST_WHEN_TARGET_IN_MELEE, FLANK_BEATS_SHIELD, GOES_THROUGH_SHIELD -> "";
        };
    }

    /** Short text instruction of the {@code COMPACT} detail (M16.3). */
    static String compactInstructions(Situation situation, String mobRef) {
        return switch (situation) {
            case NOW -> "Which tactic best serves " + mobRef + "'s objective now?";
            case IF_TARGET_IN_MELEE -> "If " + mobRef + "'s target comes inside melee reach soon, which tactic then?";
            case IF_OWN_HEALTH_CRITICAL -> "If " + mobRef + "'s health becomes critical soon, which tactic then?";
        };
    }

    /** The {@code question} of a structured instruction ({@code STANDARD} and {@code FULL}, M16.3). */
    static String question(Situation situation, String mobRef) {
        return switch (situation) {
            case NOW -> "Which tactic should " + mobRef + " use now against its current target?";
            case IF_TARGET_IN_MELEE -> "Suppose the target of " + mobRef + " comes inside melee reach in the next seconds."
                    + " Which tactic should " + mobRef + " switch to then?";
            case IF_OWN_HEALTH_CRITICAL -> "Suppose the health of " + mobRef + " becomes critical in the next seconds."
                    + " Which tactic should " + mobRef + " switch to then?";
        };
    }

    /** The {@code assume} of a contingency: its hypothesis in the words of the state; empty for {@code NOW}. */
    static String assume(Situation situation, String mobRef) {
        return switch (situation) {
            case NOW -> "";
            case IF_TARGET_IN_MELEE -> "the target is inside melee reach";
            case IF_OWN_HEALTH_CRITICAL -> health(HealthBand.CRITICAL, mobRef);
        };
    }

    /** The fixed {@code focus} of the {@code FULL} detail, the same in every question. */
    static String focus() {
        return "Choose the tactic that best serves the objective with only these facts. Options whose poor_fit_now is"
                + " not empty are usually worse right now.";
    }

    /** The question of one {@code noul} fit check (M16.5). */
    static String fitQuestion(String tactic, String mobRef) {
        return "Is `" + tactic + "` a sound tactic for " + mobRef + " right now?";
    }

    /** The {@code true} criterion of a fit check. */
    static String fitTrue(String mobRef) {
        return "The facts make this tactic useful for the objective of " + mobRef + " right now.";
    }

    /** The {@code false} criterion of a fit check. */
    static String fitFalse() {
        return "The facts make this tactic useless, risky or impossible right now.";
    }

    /** Relative health of a mob and its target when their bands differ (M16.4), compared in code (G5). */
    static String relativeHealth(boolean better, String subject) {
        return subject + (better ? " is in better shape than its target" : " is in worse shape than its target");
    }

    static String threat(int rank, String subject) {
        return switch (rank) {
            case 1 -> subject + " is the highest threat";
            case 2 -> subject + " is the second highest threat";
            case 3 -> subject + " is the third highest threat";
            default -> subject + " is a lesser threat";
        };
    }

    static String targetIs(String alias) {
        return "target is " + alias;
    }

    static String noTarget(String subject) {
        return subject + " has no current target";
    }

    static String unreachable() {
        return "target cannot be reached on foot";
    }

    static String notVisible() {
        return "no line of sight to target";
    }

    static String outsideTargetReach(String subject) {
        return subject + " is outside the target's reach";
    }

    static String flankable() {
        return "target can be flanked";
    }

    static String duration(int combatSeconds) {
        if (combatSeconds < 10) {
            return "the fight has just started";
        }
        if (combatSeconds < 45) {
            return "the fight has lasted about half a minute";
        }
        if (combatSeconds < 90) {
            return "the fight has lasted about one minute";
        }
        return combatSeconds < 180 ? "the fight has lasted about two minutes" : "the fight has lasted several minutes";
    }

    static String headcount(int players, int entities, int mobs) {
        if (entities == 0) {
            return count(players, "player", "players") + " and " + count(mobs, "mob", "mobs") + " are involved";
        }
        return count(players, "player", "players") + ", " + count(entities, "other creature", "other creatures")
                + " and " + count(mobs, "mob", "mobs") + " are involved";
    }

    private static String count(int amount, String singular, String plural) {
        return switch (amount) {
            case 0 -> "no " + plural;
            case 1 -> "one " + singular;
            case 2 -> "two " + plural;
            case 3 -> "three " + plural;
            case 4 -> "four " + plural;
            default -> amount <= 8 ? "several " + plural : "many " + plural;
        };
    }
}
