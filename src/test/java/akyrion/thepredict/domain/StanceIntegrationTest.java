package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every cell of the action table, and a stagger that does not shrink the question (M23.6, M23.9). */
class StanceIntegrationTest {
    @Test
    void eachActionAllowsOnlyItsCasts() {
        var damage = choice(SkillRole.DAMAGE, DamageTier.MEDIUM, Delivery.MELEE);
        var escape = choice(SkillRole.ESCAPE, DamageTier.NONE, Delivery.SELF);
        for (var action : List.of(ActionStance.WINDUP, ActionStance.STAGGERED)) {
            assertFalse(ReadyCastOrder.stanceAllows(damage, true, action, BaseStance.NEUTRAL, StanceSettings.DEFAULT));
            assertFalse(ReadyCastOrder.stanceAllows(escape, true, action, BaseStance.NEUTRAL, StanceSettings.DEFAULT));
        }
        assertFalse(ReadyCastOrder.stanceAllows(damage, true, ActionStance.RECOVERY, BaseStance.PRESSING,
                StanceSettings.DEFAULT));
        assertTrue(ReadyCastOrder.stanceAllows(escape, false, ActionStance.RECOVERY, BaseStance.EVASIVE,
                StanceSettings.DEFAULT));
        assertTrue(ReadyCastOrder.stanceAllows(damage, false, ActionStance.NONE, BaseStance.NEUTRAL,
                StanceSettings.DEFAULT));
    }

    @Test
    void aThirtyTickStaggerDoesNotReduceTheQuestionToOneOption() {
        var hold = new CandidateIntent(new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), 10, 0);
        var press = new CandidateIntent(new TacticId("press_attack"), Movement.APPROACH, Optional.empty(), 40, 0);
        var retreat = new CandidateIntent(new TacticId("retreat_and_defend"), Movement.RETREAT, Optional.empty(), 20, 0);
        var now = 100L;
        var busy = now + PoiseSettings.defaults().staggerTicks();
        assertEquals(30, PoiseSettings.defaults().staggerTicks());
        assertEquals(30, CandidatePolicy.readyInTicks(0, busy, now, ActionStance.STAGGERED));
        var set = CandidatePolicy.select(now, 0, List.of(hold, press, retreat), 8, false,
                List.of(hold.tactic(), press.tactic(), retreat.tactic()));
        assertEquals(3, set.candidates().size());
        assertEquals(3, set.questionOrder().size());
    }

    private static SkillChoice choice(SkillRole role, DamageTier damage, Delivery delivery) {
        var reach = delivery == Delivery.MELEE ? OptionalDouble.of(3) : OptionalDouble.empty();
        var purpose = new SkillPurpose(role, Set.of(role), delivery, reach, OptionalDouble.empty(), 0,
                CooldownClass.SHORT, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId(role.name().toLowerCase()), role.name(), Set.of(), false, 0, purpose);
    }
}
