package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tell with a lead of 0 casts with its interrupt, and a poise break cuts the skill in flight (SA-02, M23.4). */
class ToldCastInterruptTest {
    @Test
    void aLeadZeroTellCastsWithItsFlagAndABreakAtTenTicksCutsIt() {
        long start = 900L;
        var session = session();
        var actions = new Recording();
        assertFalse(TellOpening.hold(session, slam(), Movement.NONE, start, actions, new long[5]));
        assertTrue(session.tellOpen(), "the lead is 0, so the tell opens and the cast goes out now");
        assertEquals(ExecutionOutcome.CAST, TellOpening.castTold(session, slam(), Movement.NONE, actions));
        assertSame(session.tellInterrupt(), actions.flag);
        session.busyUntil(start + 40);
        PoiseWatch.onDamage(session, session.lastTargetId().orElseThrow(), 0.5, start + 10, true, actions,
                new long[5]);
        assertTrue(actions.flag.interrupted());
    }

    @Test
    void aBreakAfterTheTellClosedStillCutsTheSkillWhileItsWindUpLasts() {
        long start = 900L;
        var session = session();
        var actions = new Recording();
        TellOpening.hold(session, slam(), Movement.NONE, start, actions, new long[5]);
        TellOpening.castTold(session, slam(), Movement.NONE, actions);
        session.busyUntil(start + 40);
        session.closeTell();
        assertFalse(session.tellOpen());
        PoiseWatch.onDamage(session, session.lastTargetId().orElseThrow(), 0.5, start + 30, true, actions,
                new long[5]);
        assertTrue(actions.flag.interrupted(), "closeTell keeps the in-flight flag until busyUntilTick");
    }

    @Test
    void aBreakAfterTheWindUpEndedCutsNothing() {
        long start = 900L;
        var session = session();
        var actions = new Recording();
        TellOpening.hold(session, slam(), Movement.NONE, start, actions, new long[5]);
        TellOpening.castTold(session, slam(), Movement.NONE, actions);
        session.busyUntil(start + 40);
        session.closeTell();
        PoiseWatch.onDamage(session, session.lastTargetId().orElseThrow(), 0.5, start + 40, true, actions,
                new long[5]);
        assertFalse(actions.flag.interrupted());
    }

    private static MobSession session() {
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(slam().id(), slam()), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing(), base.participants(), base.difficulty(), base.stances(),
                TelegraphSettings.DEFAULT, Optional.of(PoiseSettings.defaults()));
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        session.playerTarget(true);
        session.recordTarget(UUID.randomUUID(), 3);
        session.baseSinceTick(0L);
        return session;
    }

    private static SkillChoice slam() {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 40, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId("slam"), "Slam", Set.of(), true, 0, purpose).withTelegraph(
                new ChoiceTelegraph(Optional.empty(), Optional.of(TellAnswer.BACK_OFF), OptionalInt.of(12)));
    }

    private static final class Recording implements CombatActions {
        CastInterrupt flag = new CastInterrupt();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.CAST;
        }

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                     CastInterrupt interrupt) {
            flag = interrupt;
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
    }
}
