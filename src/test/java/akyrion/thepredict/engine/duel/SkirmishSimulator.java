package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PressureLadder;

/**
 * Skirmish of M25.12. {@code T} is 1200 ticks (60 s). The horizon is {@code 1.25·T} so a fight inside the upper band
 * is observable. The ladder of a player fight is the production group section. A cell outside its band is published
 * and does not change a default.
 */
public final class SkirmishSimulator {
    /** Target length, 60 seconds. */
    public static final int TICKS = 1200;
    /** Upper band {@code 1.25·T}. */
    public static final int HORIZON = 1500;

    private SkirmishSimulator() {
    }

    /** Group arm. {@code FULL} asks {@link PlanOracle} once. */
    public enum Arm {
        OFF, FAIR, COORDINATE, FULL
    }

    /**
     * One scenario.
     *
     * @param name label
     * @param mobs red bodies
     * @param players blue bodies
     * @param healer whether the last mob is the healer
     * @param ticks fight length
     */
    public record Fight(String name, int mobs, int players, boolean healer, int ticks) {
    }

    /**
     * One measured fight. Counts come from the casts of that fight.
     *
     * @param transcript cast and hit lines, without coordinates
     * @param impacts damaging hits that landed
     * @param denials fairness refusals
     * @param fairnessViolations landed hits of two casters on one victim closer than the gap
     * @param triples impacts with two neighbours inside five ticks
     * @param impactsPerSecondP99 busiest 20-tick window, which is the p99 while the fight stays under 100 windows
     * @param clumpPairTicks pairs of mobs closer than one block
     * @param sectorFreeTicks mob-ticks outside the exit sector
     * @param sectorTicks mob-ticks counted for the sector
     * @param supportTicks healer ticks spent as support
     * @param healerTicks ticks the healer was alive
     * @param heals heals that were cast
     * @param healChances ticks an ally was under half health and in reach
     * @param roleChanges most role changes in one sample after the first
     * @param roleSamples role samples
     * @param nanos wall time of the fight
     * @param secondHand medium-or-harder refusals for second-hand sight
     * @param answerConflict incompatible open tells
     * @param deathTick tick of the player's death, or -1 when the player lived
     * @param healsOnWounded healer casts that landed on an ally under half health
     * @param squadWounds damaging hits of the player side on the squad
     */
    public record Measured(String transcript, int impacts, int denials, int fairnessViolations, int triples,
                           int impactsPerSecondP99, int clumpPairTicks, int sectorFreeTicks, int sectorTicks,
                           int supportTicks, int healerTicks, int heals, int healChances, int roleChanges,
                           int roleSamples, long nanos, int secondHand, int answerConflict, int deathTick,
                           int healsOnWounded, int squadWounds) {
    }

    /**
     * Plays with the classic rival and the delivered melee cap and gap.
     *
     * @param fight scenario
     * @param arm group arm
     * @param seed fight seed
     * @return the measurement
     */
    public static Measured run(Fight fight, Arm arm, long seed) {
        return run(fight, arm, seed, false);
    }

    /**
     * One fight against a named entity rival. Memory does not change the rival.
     *
     * @param fight scenario
     * @param name {@code classic}, {@code classic-tuned} or {@code local-best}
     * @param seed fight seed
     * @return the measurement
     */
    public static Measured rival(Fight fight, String name, long seed) {
        return SkirmishFight.play(fight, Arm.FAIR, seed, false, SquadOpponent.of(name),
                GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.minImpactGapTicks(), fight.ticks(),
                true);
    }

    /**
     * Record arm of one E3 bank fight. The transcript flag stays off on both arms: it does not move the death tick.
     *
     * @param fight scenario
     * @param rival {@code none}, {@code classic}, {@code classic-tuned} or {@code local-best}
     * @param seed fight seed, shared by the two arms of one pair
     * @return the measurement
     */
    public static Measured bank(Fight fight, String rival, long seed) {
        return SkirmishFight.play(fight, Arm.FAIR, seed, false, SquadOpponent.of(rival),
                GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.minImpactGapTicks(), fight.ticks(),
                false, ExploitWatch.idle());
    }

    /**
     * Seed arm of one E3 bank fight. {@code tracker} already carries earlier fights and keeps observing the scripted
     * habit, but no part of the fight reads it (SIM-05): against an entity rival the seed arm plays the record arm's
     * fight. Melee, gap and the rival stay the delivered ones.
     *
     * @param fight scenario
     * @param rival {@code none}, {@code classic}, {@code classic-tuned} or {@code local-best}
     * @param seed fight seed, shared with {@link #bank(Fight, String, long)}
     * @param tracker seeded pattern
     * @param probability scripted show rate of this fight
     * @return the measurement
     */
    public static Measured bank(Fight fight, String rival, long seed, PatternTracker tracker, double probability) {
        return SkirmishFight.play(fight, Arm.FAIR, seed, false, SquadOpponent.of(rival),
                GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.minImpactGapTicks(), fight.ticks(),
                false, ExploitWatch.seeded(tracker, probability));
    }

    /**
     * Plays one reflected or straight arena.
     *
     * @param fight scenario
     * @param arm group arm
     * @param seed fight seed
     * @param flip whether positions are reflected
     * @return the measurement
     */
    static Measured run(Fight fight, Arm arm, long seed, boolean flip) {
        return SkirmishFight.play(fight, arm, seed, flip, SquadOpponent.of("classic"),
                GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.minImpactGapTicks(), fight.ticks(),
                true);
    }

    /**
     * Death tick of fights 11 to 40, four archetypes, ladder carried across the forty fights of one archetype.
     * A timeout is {@code -1}. The prior starts at 8.
     *
     * @return 120 ticks, archetype-major
     */
    public static int[] playerDeaths() {
        var deaths = new int[120];
        var cursor = 0;
        var globals = DifficultyGlobals.DEFAULT;
        for (var name : SwordPlayerDuelist.ARCHETYPES) {
            var prior = 8.0;
            var rival = SquadOpponent.of(name);
            for (var fight = 1; fight <= 40; fight++) {
                var rung = (int) Math.max(0, Math.min(8, Math.round(prior)));
                var levers = PressureLadder.groupLevers(rung, 0, 0, true);
                var played = SkirmishFight.play(new Fight("5v1", 5, 1, false, HORIZON), Arm.FAIR, fight, false, rival,
                        levers.maxMeleeAttackers(), levers.impactGapTicks(), HORIZON, false);
                if (fight >= 11) {
                    deaths[cursor++] = played.deathTick();
                }
                var seconds = (played.deathTick() > 0 ? played.deathTick() : HORIZON) * 0.05;
                var delta = DifficultyPolicy.deltaU(Math.log(seconds), Math.log(60.0), globals.deadbandRatio(),
                        globals.effectiveGain(), DifficultyGlobals.LADDER_K, true);
                prior = DifficultyPolicy.nextU(prior, delta, 0, 8);
            }
        }
        return deaths;
    }

    /**
     * The plan the oracle would open on a fresh arena.
     *
     * @param fight scenario
     * @param seed fight seed
     * @return the chosen plan
     */
    static GroupPlan oracle(Fight fight, long seed) {
        var world = new SkirmishWorld(SkirmishWorld.place(fight.mobs(), fight.players(), fight.healer(), false),
                hit -> {
                });
        return PlanOracle.choose(world, SquadOpponent.of("classic"), seed, fight.mobs());
    }
}
