package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** E-4 and PZ-15/16: the stance and the band slots filter inside the walk, and only selectable triggers are kept. */
class TickReactionsTest {
    private static final long TICK = 50L;

    @Test
    void inRecoverySpaceOutArmsAlthoughFinishComesFirstInTheTable() {
        var session = session(TickLayerMode.FULL, Movement.HOLD);
        session.stanceState(StanceState.withAction(StanceState.INITIAL, ActionStance.RECOVERY.ordinal()));
        session.busyUntil(TICK - 1);
        TickReactions.arm(session, finishAndSpaceOut(), TICK, DecisionTap.NONE);
        assertEquals(Reaction.SPACE_OUT.ordinal(), session.slot(ReactionBand.SPACING),
                "recovery forbids the finisher, so the step back of the next band arms");
        assertEquals(-1, session.slot(ReactionBand.OPPORTUNITY));
    }

    @Test
    void aBandWhoseSlotHoldsABetterReactionLetsTheNextBandArm() {
        var session = session(TickLayerMode.FULL, Movement.HOLD);
        session.armReaction(Reaction.PUNISH_CONSUMING.ordinal(), TICK + 15, TICK + 15);
        session.busyUntil(TICK - 1);
        TickReactions.arm(session, finishAndSpaceOut(), TICK, DecisionTap.NONE);
        assertEquals(Reaction.PUNISH_CONSUMING.ordinal(), session.slot(ReactionBand.OPPORTUNITY),
                "the finisher cannot replace the better punish in its own slot");
        assertEquals(Reaction.SPACE_OUT.ordinal(), session.slot(ReactionBand.SPACING),
                "the blocked finisher does not use up the tick");
        assertEquals(TICK, session.reactionArmedSince()[Reaction.FINISH.ordinal()],
                "the trigger hidden for the second walk is put back and keeps its delay");
    }

    @Test
    void aTriggerTheLayerCanNeverSelectIsNotKeptAsArmed() {
        var session = session(TickLayerMode.REACTIONS, Movement.HOLD);
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1),
                akyrion.thepredict.domain.DistanceBand.RANGED, Trend.STEADY, true, true, true, false, false);
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(),
                Set.of(), Set.of()));
        var signals = TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET)
                | (1L << TickSignals.TARGET_SHOT_READY), 40, 0, 0, 0, 100, 100);
        TickReactions.arm(session, signals, TICK, DecisionTap.NONE);
        assertEquals(-1L, session.reactionArmedSince()[Reaction.DODGE_SHOT.ordinal()],
                "without micro a dodge can never arm, so its trigger does not make the session hot");
        var full = session(TickLayerMode.FULL, Movement.HOLD);
        full.facts(new MobFacts(full.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(), Set.of(),
                Set.of()));
        TickReactions.arm(full, signals, TICK, DecisionTap.NONE);
        assertEquals(Reaction.DODGE_SHOT.ordinal(), full.slot(ReactionBand.SURVIVAL), "with micro it arms");
    }

    /** Target at five percent within melee reach and no recharge: the finisher and the step back both hold. */
    private static long finishAndSpaceOut() {
        return TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0, 100, 5);
    }

    private static MobSession session(TickLayerMode mode, Movement movement) {
        var hold = new TacticId("hold_ground");
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1).withLayer(mode, ReactionScope.ALL, Set.of(), 0,
                DecisionCadence.STANDARD, 6, 40);
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, movement, Optional.empty(), Optional.empty(), 50)), Map.of(), List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), timing);
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        session.adoptIntent(new TacticalIntent(session.key(), hold, movement, Optional.empty(), 0.9, List.of(),
                Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false));
        session.recordTarget(EngineFixtures.player(1), 1.0);
        return session;
    }
}
