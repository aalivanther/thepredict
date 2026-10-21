package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classic duelist of the bench (§7.3): one global phase for every timer (SIM-10), {@code ~onDamaged}, the aura
 * that waits for the window of its own hits, and the native goals.
 */
class ClassicDuelistTest {
    private static DuelWorld world(double distance) {
        var red = new DuelWorld.Body(new UUID(4, 0), DuelWorld.Side.RED, 0, -distance / 2);
        var blue = new DuelWorld.Body(new UUID(4, 1), DuelWorld.Side.BLUE, 0, distance / 2);
        return new DuelWorld(red, blue, hit -> {
        }, DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, 0);
    }

    private static ClassicConfig rules(ClassicConfig.Rule... rules) {
        return new ClassicConfig("test", Kit.K1, List.of(rules), ClassicConfig.Goals.PACK);
    }

    @Test
    void rulesWithTheSameTimerFireOnTheSameTick() {
        var world = world(6);
        world.blue.health = 20;
        var config = rules(new ClassicConfig.Rule(DuelSkill.HEAL, ClassicConfig.Trigger.TIMER, 20, 0, 0, 1.0, 0, false),
                new ClassicConfig.Rule(DuelSkill.BOLT, ClassicConfig.Trigger.TIMER, 20, 14, 0, 1.0, 0, false));
        var duelist = new ClassicDuelist(world, world.blue, config, 7);
        var casts = new ArrayList<String>();
        for (var index = 0; index < 40; index++) {
            var at = index;
            duelist.decide(index, index, (body, skill) -> casts.add(at + ":" + skill.id));
        }
        assertEquals(List.of("13:DuelHeal", "13:DuelBolt"), casts, "(13 + 7) % 20 == 0 for both rules");
    }

    @Test
    void aGuardOnDamagedFiresAtTheFirstDecisionAfterAHit() {
        var world = world(2);
        var config = rules(new ClassicConfig.Rule(DuelSkill.GUARD, ClassicConfig.Trigger.ON_DAMAGED, 1, 0, 0, 1.0, 0,
                false));
        var duelist = new ClassicDuelist(world, world.blue, config, 0);
        var casts = new ArrayList<Integer>();
        duelist.decide(0, 0, (body, skill) -> casts.add(0));
        assertTrue(casts.isEmpty(), "no hit, no guard");
        world.cast(world.red, DuelSkill.STRIKE, 0);
        for (var tick = 0L; tick <= 5; tick++) {
            world.commit(tick, true);
            world.step(tick);
        }
        duelist.decide(6, 6, (body, skill) -> casts.add(6));
        duelist.decide(7, 7, (body, skill) -> casts.add(7));
        assertEquals(List.of(6), casts);
    }

    @Test
    void theAuraWaitsUntilTheImpactLandsOutsideTheOwnWindow() {
        var world = world(2);
        var config = rules(new ClassicConfig.Rule(DuelSkill.STRIKE, ClassicConfig.Trigger.TIMER, 1, 3, 0, 1.0, 0, true),
                new ClassicConfig.Rule(DuelSkill.BOLT, ClassicConfig.Trigger.TIMER, 1, 14, 0, 1.0, 0, true));
        var duelist = new ClassicDuelist(world, world.blue, config, 0);
        var casts = new ArrayList<String>();
        for (var index = 0; index < 12; index++) {
            var at = index;
            duelist.decide(index, index, (body, skill) -> casts.add(at + ":" + skill.id));
        }
        assertEquals(List.of("0:DuelStrike", "5:DuelBolt"), casts,
                "the strike lands at 5 and opens 10 ticks; the bolt waits until its own impact lands at 15");
    }

    @Test
    void theMeleeGoalHitsAndTheBandHoldsWhileTheStrikeCools() {
        var world = world(1);
        var band = new ClassicConfig("band", Kit.K1, List.of(), new ClassicConfig.Goals(1.15, 0, 0, 0, 5, 9, 1.15));
        var duelist = new ClassicDuelist(world, world.blue, band, 0);
        duelist.decide(0, 0, (body, skill) -> {
        });
        assertEquals(Motion.Steer.Kind.TOWARD, world.blue.goal.kind(), "the strike is ready: melee attack");
        world.blue.cooldown(DuelSkill.STRIKE, 30);
        duelist.decide(2, 2, (body, skill) -> {
        });
        assertEquals(Motion.Steer.away(1.15, 5), world.blue.goal, "the strike cools: flee to the inner edge");
        world.blue.z = 7;
        duelist.decide(4, 4, (body, skill) -> {
        });
        assertEquals(Motion.Steer.HOLD, world.blue.goal, "inside the band nothing moves it");
        assertFalse(world.blue.goal.active(world.distance()));
    }
}
