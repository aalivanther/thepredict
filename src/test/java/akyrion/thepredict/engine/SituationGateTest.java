package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SituationGateTest {
    @Test
    void standardWithPruningOffDoesNotAskTheVeto() {
        var session = session(DecisionCadence.STANDARD);
        session.noteCaptureSignature(1);
        session.noteLiveSignature(2);
        assertEquals(SituationGate.Mark.INACTIVE, SituationGate.classify(session, PruningMode.OFF, null));
    }

    @Test
    void aChangedSignatureIsAgedAndAnUnchangedOneIsNot() {
        var session = session(DecisionCadence.REACTIVE);
        session.noteCaptureSignature(4);
        session.noteLiveSignature(4);
        assertEquals(SituationGate.Mark.UNCHANGED, SituationGate.classify(session, PruningMode.OFF, null));
        session.noteLiveSignature(5);
        assertEquals(SituationGate.Mark.AGED, SituationGate.classify(session, PruningMode.OFF, null));
    }

    @Test
    void pruningOnAsksEvenWhenTheCadenceIsStandard() {
        var session = session(DecisionCadence.STANDARD);
        session.noteCaptureSignature(1);
        session.noteLiveSignature(2);
        assertEquals(SituationGate.Mark.AGED, SituationGate.classify(session, PruningMode.SAFE, null));
    }

    @Test
    void aTacticOutOfReachOfItsMeleeChoiceIsVetoedThroughTheOverloadWithoutOptional() {
        var slam = akyrion.thepredict.domain.SkillChoice.typed(new akyrion.thepredict.domain.ChoiceId("slam"),
                "TPV_Slam", Set.of(), true, 0, new akyrion.thepredict.domain.SkillPurpose(
                        akyrion.thepredict.domain.SkillRole.DAMAGE, Set.of(akyrion.thepredict.domain.SkillRole.DAMAGE),
                        akyrion.thepredict.domain.Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), 0,
                        akyrion.thepredict.domain.CooldownClass.SHORT, akyrion.thepredict.domain.DamageTier.MEDIUM,
                        Set.of(), Set.of(), false, akyrion.thepredict.domain.PurposeOrigin.DECLARED));
        var hold = new TacticId("hold_ground");
        var base = session(DecisionCadence.REACTIVE).profile();
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(),
                Map.of(hold, new akyrion.thepredict.domain.Tactic(hold, Movement.HOLD, Optional.of(slam.id()),
                        Optional.empty(), 50)), Map.of(slam.id(), slam), base.contexts(), base.movement(),
                base.health(), base.threat(), base.baseAi(), base.timing());
        var session = new MobSession(new MobKey(UUID.randomUUID(), 1), profile);
        session.noteProbe(new akyrion.thepredict.domain.MobProbe(session.key(), new UUID(0, 1),
                akyrion.thepredict.domain.HealthBand.HEALTHY, Optional.of(new UUID(0, 2)),
                Optional.of(akyrion.thepredict.domain.DistanceBand.RANGED)), 1);
        session.noteCaptureSignature(1);
        session.noteLiveSignature(2);
        var tactic = profile.tactic(hold).orElseThrow();
        assertEquals(SituationGate.Mark.VETO, SituationGate.classify(session, tactic, PruningMode.OFF, control()));
        assertEquals(SituationGate.Mark.AGED, SituationGate.classify(session, PruningMode.OFF, control()),
                "an answer whose tactic the profile lacks has nothing out of reach to veto (AE-13)");
    }

    private static IntentControl control() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(() -> 0L);
        return new IntentControl(sessions.board, QuietActions.INSTANCE, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
    }

    private static MobSession session(DecisionCadence cadence) {
        var hold = new TacticId("hold_ground");
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1).withLayer(TickLayerMode.REACTIONS,
                ReactionScope.ALL, Set.of(), 0, cadence, 6, 40);
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new akyrion.thepredict.domain.Tactic(hold, Movement.HOLD, Optional.empty(),
                        Optional.empty(), 50)),
                Map.of(), List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
        return new MobSession(new MobKey(UUID.randomUUID(), 1), profile);
    }
}
