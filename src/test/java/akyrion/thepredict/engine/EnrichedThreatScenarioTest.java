package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enriched threat inside the coordinator of M09 and M10 (plan §8 and the A/B protocol of plan §12): the same facts
 * give the same threat at every control level, with or without Jev, the unreachable decay runs from the scheduled
 * evaluations at zero cost, and zero weights leave the native table untouched for the baseline arm (rule P2).
 */
class EnrichedThreatScenarioTest {
    private static final MovementProgress FALLS_SHORT = new MovementProgress(true, true, false,
            OptionalDouble.of(6.0));

    private static ExecutionFixture withoutJev() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        return fixture;
    }

    private static final MovementProgress NO_PATH = new MovementProgress(false, false, false,
            OptionalDouble.of(1.0));

    private static ExecutionFixture withJev() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        return fixture;
    }

    /** Leaves the target unreachable for the capture and the given path, then runs 100 ticks, answering Jev. */
    private static List<ExecutionFixture.Threat> unreachableFor100Ticks(ExecutionFixture fixture,
                                                                        MovementProgress path) {
        fixture.reader.unreachable.add(id(1));
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(path)));
        fixture.runAnswering(100, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        return List.copyOf(fixture.actions.threats);
    }

    private static List<ExecutionFixture.Threat> sameFacts(ExecutionFixture fixture) {
        fixture.threat.blockedHit(id(1), player(1));
        fixture.threat.rangedHit(id(1), player(1), 4.0, false);
        fixture.threat.rangedHit(id(1), player(1), 4.0, true);
        return List.copyOf(fixture.actions.threats);
    }

    private static TacticalProfile withThreat(ThreatWeights threat) {
        var profile = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), threat, profile.baseAi(),
                profile.timing());
    }

    @Test
    void theSameFactsGiveTheSameThreatWithJevWithoutJevAndUnderTheBaseAi() {
        var local = withoutJev();
        assertEquals(ControlLevel.LOCAL, local.view(1).level());
        var base = withoutJev();
        base.reader.eligible.put(id(1), Set.of());
        base.run(40);
        assertEquals(ControlLevel.BASE, base.view(1).level());
        var jev = withJev();
        assertEquals(ControlLevel.JEV, jev.view(1).level());
        var expected = List.of(new ExecutionFixture.Threat(new MobKey(id(1), 0), player(1), 1.0),
                new ExecutionFixture.Threat(new MobKey(id(1), 0), player(1), 4.0));
        assertEquals(expected, sameFacts(local));
        assertEquals(expected, sameFacts(base));
        assertEquals(expected, sameFacts(jev));
    }

    @Test
    void anUnreachableTargetLosesThreatAtEachScheduledEvaluationWithoutJevAndAtZeroCost() {
        var fixture = withoutJev();
        fixture.reader.unreachable.add(id(1));
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(FALLS_SHORT)));
        fixture.run(100);
        var decays = fixture.actions.threats;
        assertTrue(decays.size() >= 3, "one decay per evaluation after the first unreachable one");
        assertTrue(decays.stream().allMatch(change -> change.equals(new ExecutionFixture.Threat(new MobKey(id(1), 0),
                player(1), -0.5))), "0.5 points per second, one heartbeat of 20 ticks apart");
        assertTrue(fixture.client.requests.isEmpty());
        assertEquals(decays.size(), fixture.threat.metrics().decays());
    }

    @Test
    void zeroWeightsLeaveTheNativeTableUntouchedForTheBaselineArm() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1, withThreat(new ThreatWeights(0, 0, 0, 0, 20)));
        fixture.target(1, 1);
        fixture.reader.unreachable.add(id(1));
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(FALLS_SHORT)));
        fixture.run(100);
        assertTrue(sameFacts(fixture).isEmpty());
        assertEquals(0L, fixture.actions.calls.stream().filter(call -> call.startsWith("threat")).count());
    }

    @Test
    void theUnreachableDecayIsTheSameWithJevAndWithoutJev() {
        var local = unreachableFor100Ticks(withoutJev(), FALLS_SHORT);
        var jev = withJev();
        var answered = unreachableFor100Ticks(jev, FALLS_SHORT);
        assertEquals(ControlLevel.JEV, jev.view(1).level());
        assertFalse(local.isEmpty());
        assertEquals(local, answered, "the same losses at the same pace whatever the level");
    }

    @Test
    void aMobNextToItsTargetWithoutAPathNeverDrainsItsThreatAtAnyLevel() {
        assertTrue(unreachableFor100Ticks(withoutJev(), NO_PATH).isEmpty(),
                "without Jev no path is computed and the last known value stays unreachable");
        assertTrue(unreachableFor100Ticks(withJev(), NO_PATH).isEmpty());
    }
}
