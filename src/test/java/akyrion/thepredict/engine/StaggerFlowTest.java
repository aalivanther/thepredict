package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphSettings;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A full meter staggers, cuts the tell and rewrites the busy deadline (M23.7). */
class StaggerFlowTest {
    @Test
    void aBreakDuringWindUpStaggersForThirtyTicksAndInterrupts() {
        var attacker = UUID.randomUUID();
        var session = session();
        session.openTell(0, 10L, 28, 18, 20, false, attacker);
        session.tellResults().capture(session, session.listedChoices()[0], 10L, 28, 18, 20, false, attacker);
        session.stanceState(StanceState.withAction(StanceState.INITIAL, ActionStance.WINDUP.ordinal()));
        session.busyUntil(10L + 48);
        var flag = session.tellInterrupt();
        PoiseWatch.onDamage(session, attacker, 0.20, 15L, true, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.STAGGERED, session.actionStance());
        assertEquals(0.0, session.poiseDamage(), 0.0);
        assertEquals(15L + PoiseSettings.defaults().staggerTicks(), session.busyUntilTick());
        assertTrue(flag.interrupted());
        assertFalse(session.tellOpen());
        assertEquals(0, session.tellResults().drain(44L));
        assertEquals(1, session.tellResults().drain(45L));
        assertEquals(TelegraphEnd.INTERRUPTED, session.tellResults().drained(0).end());
    }

    @Test
    void anInterruptedTellLeavesTheCooldownOfItsChoiceLikeAnyTellThatDoesNotComplete() {
        var attacker = UUID.randomUUID();
        var session = session();
        session.openTell(0, 10L, 28, 18, 20, false, attacker);
        session.stanceState(StanceState.withAction(StanceState.INITIAL, ActionStance.WINDUP.ordinal()));
        PoiseWatch.onDamage(session, attacker, 0.20, 15L, true, QuietActions.INSTANCE, new long[5]);
        assertFalse(session.tellOpen());
        assertEquals(15L + session.profile().telegraph().whiffCooldownTicks(), session.whiffUntil(0),
                "the stagger is the recovery and the choice also cools down (A11)");
    }

    private static MobSession session() {
        var base = EngineFixtures.profile("Guardian");
        var choice = akyrion.thepredict.domain.SkillChoice.untyped(new akyrion.thepredict.domain.ChoiceId("slam"),
                "Slam", java.util.Set.of(), true, 0);
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                java.util.Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing(), ParticipantRules.DEFAULT, DifficultySettings.DEFAULT, base.stances(),
                TelegraphSettings.DEFAULT, Optional.of(PoiseSettings.defaults()));
        return new MobSession(new akyrion.thepredict.domain.MobKey(UUID.randomUUID(), 0), profile);
    }
}
