package akyrion.thepredict.engine.duel;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * A duelist of MythicMobs alone (M18.7). A ThePredict duelist at level 4 plays exactly this, because its rules start
 * with {@code predictorcontrol false}.
 *
 * <p>The constructor with a {@link Random} is the duelist of M18.7, whose bytes the golden files keep: the
 * {@code meleeAttack} goal of {@code DuelBase} and its rules {@code DuelRule*}, each timer with its own phase drawn from
 * the seed of the round. The constructor with a {@link ClassicConfig} is the duelist of the bench (§7.3): its rules
 * fire on one global phase shared by every mob of the round, as {@code MythicClock.skillClockIteration} does (SIM-10),
 * or on {@code ~onDamaged} at the first decision after a hit; it decides on the picture of the tick and moves with its
 * native goals through {@link Motion}.
 */
final class ClassicDuelist {
    private final DuelWorld world;
    private final DuelWorld.Body body;
    private final ClassicConfig config;
    private final int[] phases;
    private final long spawnTick;
    private final int globalPhase;
    private long windowEnd = Long.MIN_VALUE;
    private long seenDamage = Long.MIN_VALUE;
    private boolean melee;
    private boolean meleeWanted;

    ClassicDuelist(DuelWorld world, DuelWorld.Body body, Random random, long spawnTick) {
        this.world = world;
        this.body = body;
        this.spawnTick = spawnTick;
        this.config = ClassicConfig.pack(Kit.K1);
        this.globalPhase = -1;
        this.phases = new int[DuelSkill.RULES.size()];
        for (var index = 0; index < phases.length; index++) {
            phases[index] = random.nextInt(DuelSkill.RULES.get(index).timerTicks());
        }
    }

    /**
     * The duelist of the bench.
     *
     * @param world the world
     * @param body its body
     * @param config its rules and goals
     * @param globalPhase the phase of the MythicMobs clock at the first tick of the round, shared by both sides
     */
    ClassicDuelist(DuelWorld world, DuelWorld.Body body, ClassicConfig config, int globalPhase) {
        this.world = world;
        this.body = body;
        this.config = config;
        this.globalPhase = globalPhase;
        this.spawnTick = 0;
        this.phases = new int[0];
    }

    /** The configuration this duelist plays. */
    ClassicConfig config() {
        return config;
    }

    /** One tick of M18.7: the base goal moves and hits, then each rule whose timer fires and whose conditions hold. */
    void tick(long tick, BiConsumer<DuelWorld.Body, DuelSkill> casts) {
        world.meleeAttack(body, tick);
        for (var index = 0; index < phases.length; index++) {
            var rule = DuelSkill.RULES.get(index);
            if ((tick - spawnTick + phases[index]) % rule.timerTicks() == 0 && holds(rule) && body.ready(rule.skill(), tick)
                    && world.conditions(body, rule.skill())) {
                world.cast(body, rule.skill(), tick);
                casts.accept(body, rule.skill());
            }
        }
    }

    private boolean holds(DuelSkill.Rule rule) {
        var distance = world.distance();
        return (rule.withinBlocks() == 0 || distance < rule.withinBlocks())
                && (rule.notWithinBlocks() == 0 || distance >= rule.notWithinBlocks())
                && (rule.healthBelow() >= 1.0 || body.healthRatio() < rule.healthBelow());
    }

    /**
     * One decision of the bench on the picture of this tick: the goal it runs, the vanilla hit of its melee goal, then
     * every rule that fires and holds. Casts queue in the world; nothing moves here.
     *
     * @param index tick of the round
     * @param tick tick of the clock
     * @param casts hears every cast
     */
    void decide(int index, long tick, BiConsumer<DuelWorld.Body, DuelSkill> casts) {
        var wanted = goal(tick);
        if (Motion.selects(world, body, index)) {
            body.goal = wanted;
            melee = meleeWanted;
        }
        if (melee) {
            world.punch(body, tick);
        }
        var damaged = body.damagedTick > seenDamage;
        seenDamage = body.damagedTick;
        for (var rule : config.rules()) {
            var fires = rule.trigger() == ClassicConfig.Trigger.TIMER
                    ? (index + globalPhase) % rule.timerTicks() == 0 : damaged;
            if (fires && holds(rule, tick) && body.ready(rule.skill(), tick) && world.conditions(body, rule.skill())) {
                cast(rule.skill(), tick, casts);
            }
        }
    }

    private Motion.Steer goal(long tick) {
        var goals = config.goals();
        var distance = world.distance();
        meleeWanted = false;
        if (goals.fleeBelowHealth() > 0 && body.healthRatio() < goals.fleeBelowHealth()
                && distance < goals.fleeDistance()) {
            return Motion.Steer.away(goals.fleeSpeed(), goals.fleeDistance());
        }
        var strike = config.strike();
        if (goals.band() && strike.isPresent() && !body.ready(strike.get(), tick)) {
            if (distance < goals.bandInner()) {
                return Motion.Steer.away(goals.bandSpeed(), goals.bandInner());
            }
            return distance > goals.bandOuter() ? Motion.Steer.toward(goals.bandSpeed(), goals.bandOuter())
                    : Motion.Steer.HOLD;
        }
        meleeWanted = true;
        return Motion.Steer.toward(goals.meleeSpeed(), DuelWorld.STOP_DISTANCE);
    }

    private boolean holds(ClassicConfig.Rule rule, long tick) {
        var distance = world.distance();
        var ratio = body.healthRatio();
        return (rule.withinBlocks() == 0 || distance < rule.withinBlocks())
                && (rule.notWithinBlocks() == 0 || distance >= rule.notWithinBlocks())
                && (rule.healthBelow() >= 1.0 || ratio < rule.healthBelow())
                && (rule.healthAbove() <= 0 || ratio > rule.healthAbove())
                && (!rule.awaitWindow() || rule.skill().damage <= 0 || tick + rule.skill().delayTicks >= windowEnd);
    }

    private void cast(DuelSkill skill, long tick, BiConsumer<DuelWorld.Body, DuelSkill> casts) {
        world.cast(body, skill, tick);
        casts.accept(body, skill);
        if (skill.damage > 0) {
            windowEnd = Math.max(windowEnd, tick + skill.delayTicks + DuelWorld.INVULNERABLE_TICKS);
        }
    }

    /**
     * The same duelist on a copy of its world, with its memory of the aura and of the last hit (§7.5).
     *
     * @param twin the copied world
     * @return the copy
     */
    ClassicDuelist copy(DuelWorld twin) {
        var copy = new ClassicDuelist(twin, twin.body(body.side), config, globalPhase);
        copy.windowEnd = windowEnd;
        copy.seenDamage = seenDamage;
        copy.melee = melee;
        return copy;
    }
}
