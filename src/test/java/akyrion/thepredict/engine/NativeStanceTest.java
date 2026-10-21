package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.StanceMachine;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hybrid writes the variable and never {@code setStance}. Write uses the written stance. Off writes nothing. */
class NativeStanceTest {
    @Test
    void hybridWritesLowerCaseAndNoneAndNeverSetStance() {
        var variable = new ArrayList<String>();
        var written = new ArrayList<String>();
        var actions = actions(variable, written);
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile(StanceNativeMode.HYBRID));
        StanceClock.apply(session, 1, 0, false, true, false, StanceMachine.EVENT_TELEGRAPH, 10, 0, actions,
                new long[5]);
        assertEquals(List.of("windup"), variable);
        assertTrue(written.isEmpty());
        StanceClock.invalidated(session, actions);
        assertEquals(List.of("windup", "none"), variable);
        assertTrue(written.isEmpty());
    }

    @Test
    void writeCallsSetStanceAndReadOrOffWriteNothing() {
        var variable = new ArrayList<String>();
        var written = new ArrayList<String>();
        var hands = actions(variable, written);
        var writing = new MobSession(new MobKey(UUID.randomUUID(), 0), profile(StanceNativeMode.WRITE));
        StanceClock.apply(writing, 1, 0, false, true, false, StanceMachine.EVENT_TELEGRAPH, 10, 0, hands, new long[5]);
        assertEquals(List.of("predictor_" + CombatStance.WINDUP.name().toLowerCase(java.util.Locale.ROOT)), written);
        assertTrue(variable.isEmpty());
        variable.clear();
        written.clear();
        for (var mode : List.of(StanceNativeMode.READ, StanceNativeMode.OFF)) {
            var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile(mode));
            StanceClock.apply(session, 1, 0, false, true, false, StanceMachine.EVENT_TELEGRAPH, 10, 0, hands,
                    new long[5]);
            StanceClock.invalidated(session, hands);
        }
        assertTrue(variable.isEmpty());
        assertTrue(written.isEmpty());
    }

    private static CombatActions actions(List<String> variable, List<String> written) {
        return new CombatActions() {
            @Override
            public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }

            @Override
            public boolean ready(MobKey mob, SkillChoice choice) {
                return false;
            }

            @Override
            public void signalTactic(MobKey mob, TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public void writeStanceVariable(MobKey mob, String stance) {
                variable.add(stance);
            }

            @Override
            public void writeNativeStance(MobKey mob, String stance) {
                written.add(stance);
            }
        };
    }

    private static TacticalProfile profile(StanceNativeMode mode) {
        var base = EngineFixtures.profile("Guardian");
        var stances = new StanceSettings(mode, 20, 2, java.util.Map.of(), java.util.Map.of(), List.of(), List.of(),
                false);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                ParticipantRules.DEFAULT, DifficultySettings.DEFAULT, stances, TelegraphSettings.DEFAULT,
                Optional.empty());
    }
}
