package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TellAnswer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A severe tell with 18 ticks of lead casts at t0 + 18 and hits at t0 + 28 (M23.4). */
class TelegraphFlowTest {
    @Test
    void eighteenTicksOfLeadCastAtEighteenAndHitAtTwentyEight() {
        long start = 2_000L;
        var target = UUID.randomUUID();
        var choice = severeBackOff();
        assertEquals(DangerClass.SEVERE, akyrion.thepredict.domain.DangerRules.classify(choice));
        assertEquals(TellAnswer.BACK_OFF, akyrion.thepredict.domain.DangerRules.answer(choice));
        var session = session(choice);
        session.playerTarget(true);
        session.recordTarget(target, 3);
        assertTrue(TellOpening.hold(session, choice, Movement.NONE, start, actions(), new long[5]));
        assertEquals(start + 18, session.releaseTick());
        assertEquals(28, session.telegraphTicks());
        var outcome = TelegraphRelease.release(session, start + 18, actions(), new long[5]);
        assertEquals(ReleaseOutcome.RELEASED, outcome);
        assertEquals(start + 18, session.lastCastStartTick());
        assertEquals(0, session.tellResults().drain(start + 37));
        assertEquals(1, session.tellResults().drain(start + 38));
        var line = session.tellResults().drained(0);
        assertEquals(TelegraphEnd.RELEASED, line.end());
        assertEquals(start, line.startTick());
        assertEquals(18, line.leadTicks());
        assertEquals(28, line.telegraphTicks());
    }

    private static SkillChoice severeBackOff() {
        var id = new ChoiceId("slam");
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 10, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), true,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "Slam", Set.of(), true, 0, purpose);
    }

    private static MobSession session(SkillChoice choice) {
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing());
        return new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
    }

    private static CombatActions actions() {
        return new CombatActions() {
            @Override
            public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
                return ExecutionOutcome.CAST;
            }

            @Override
            public boolean ready(MobKey mob, SkillChoice choice) {
                return true;
            }

            @Override
            public void signalTactic(MobKey mob, TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                          CastInterrupt interrupt) {
                return ReleaseOutcome.RELEASED;
            }
        };
    }
}
