package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M11.3: windows of the limiter with a controllable tick, their bound and the unreachable marks (rule P2). */
class ThreatWindowsTest {
    /** The facts {@code ThreatEnrichment} sends through the limiter, {@code assignGroup} included. */
    private static final EventType[] LIMITED = {EventType.ALLY_ASSISTED, EventType.MELEE_BLOCKED,
            EventType.RANGED_HIT_ON_MOB, EventType.GROUP_ASSIGNMENT};

    private static final MobKey MOB = new MobKey(id(1), 0);
    private final ThreatWindows windows = new ThreatWindows();

    @Test
    void aWindowAdmitsUpToItsCapAndOpensTheNextOneExactlyAtOneHundredTicks() {
        assertEquals(8.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 8, 20, 0));
        assertEquals(8.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 8, 20, 50));
        assertEquals(4.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 8, 20, 60));
        assertEquals(0.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 8, 20, 99));
        assertEquals(8.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 8, 20, 100));
        assertEquals(12.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 15, 20, 199));
        assertEquals(15.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 15, 20, 200));
    }

    @Test
    void factsParticipantsAndMemberTablesHaveIndependentWindows() {
        assertEquals(20.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 30, 20, 0));
        assertEquals(0.0, windows.admit(MOB, player(1), EventType.RANGED_HIT_ON_MOB, 1, 20, 1));
        assertEquals(1.5, windows.admit(MOB, player(1), EventType.ALLY_ASSISTED, 1.5, 20, 1));
        assertEquals(0.25, windows.admit(MOB, player(1), EventType.MELEE_BLOCKED, 0.25, 20, 1));
        assertEquals(20.0, windows.admit(MOB, player(2), EventType.RANGED_HIT_ON_MOB, 30, 20, 1));
        assertEquals(20.0, windows.admit(new MobKey(id(2), 0), player(1), EventType.RANGED_HIT_ON_MOB, 30, 20, 1));
        assertEquals(20.0, windows.admit(new MobKey(id(1), 1), player(1), EventType.RANGED_HIT_ON_MOB, 30, 20, 1),
                "a new generation is a new table");
        assertEquals(6, windows.windowCount());
    }

    @Test
    void theWindowsNeverExceedTheirCapacityAndExpiredOnesMakeRoom() {
        var filled = fill(0);
        assertEquals(EncounterRegistry.MAX_MEMBERS * EncounterRegistry.MAX_PARTICIPANTS * LIMITED.length, filled,
                "every member, participant and limited fact, group assignments included, has its own window");
        assertEquals(ThreatWindows.CAPACITY, filled);
        assertEquals(ThreatWindows.CAPACITY, windows.windowCount());
        assertEquals(0.0, windows.admit(new MobKey(id(99), 0), player(1), EventType.MELEE_BLOCKED, 1, 20, 99),
                "a full encounter refuses a new key while every window is still open");
        assertEquals(ThreatWindows.CAPACITY, windows.windowCount());
        assertEquals(1.0, windows.admit(new MobKey(id(99), 0), player(1), EventType.MELEE_BLOCKED, 1, 20, 100));
        assertEquals(1, windows.windowCount(), "the expired windows were dropped to make room");
    }

    @Test
    void anUnreachableMarkReturnsThePreviousDecisionOnlyForTheSameTargetAndGeneration() {
        assertEquals(OptionalLong.empty(), windows.markUnreachable(MOB, player(1), facts(), 20));
        assertEquals(OptionalLong.of(20), windows.markUnreachable(MOB, player(1), facts(), 40));
        assertEquals(OptionalLong.of(40), windows.markUnreachable(MOB, player(1), facts(), 60));
        assertEquals(OptionalLong.empty(), windows.markUnreachable(MOB, player(2), facts(), 80),
                "another target starts over");
        assertEquals(OptionalLong.empty(), windows.markUnreachable(new MobKey(id(1), 1), player(2), facts(), 100),
                "a new generation starts over");
        windows.forgetUnreachable(id(1));
        assertEquals(OptionalLong.empty(), windows.markUnreachable(new MobKey(id(1), 1), player(2), facts(), 120));
        assertEquals(1, windows.unreachableCount());
    }

    @Test
    void aMarkRecognisesTheVeryFactsOfItsCaptureOnly() {
        var captured = facts();
        windows.markUnreachable(MOB, player(1), captured, 0);
        assertTrue(windows.sameCapture(id(1), captured));
        assertFalse(windows.sameCapture(id(1), facts()), "an equal record of a new capture is another capture");
        assertFalse(windows.sameCapture(id(2), captured));
    }

    @Test
    void clearingDropsEveryWindowAndMark() {
        windows.admit(MOB, player(1), EventType.MELEE_BLOCKED, 1, 20, 0);
        windows.markUnreachable(MOB, player(1), facts(), 0);
        windows.clear();
        assertEquals(0, windows.windowCount());
        assertEquals(0, windows.unreachableCount());
    }

    private int fill(long tick) {
        var facts = LIMITED;
        var admitted = 0;
        for (var mob = 1; mob <= EncounterRegistry.MAX_MEMBERS; mob++) {
            for (var participant = 1; participant <= EncounterRegistry.MAX_PARTICIPANTS; participant++) {
                for (var fact : facts) {
                    if (windows.admit(new MobKey(id(mob), 0), player(participant), fact, 1, 20, tick) > 0.0) {
                        admitted++;
                    }
                }
            }
        }
        return admitted;
    }

    /** Facts of a new capture: each capture stores a new instance. */
    private static MobFacts facts() {
        return new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
