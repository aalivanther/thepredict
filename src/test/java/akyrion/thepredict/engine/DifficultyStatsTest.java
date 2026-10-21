package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultyMode;
import akyrion.thepredict.domain.DifficultyResolution;
import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PlayMode;
import akyrion.thepredict.domain.TtkEstimator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS point 7: every fight the slow loop of M22 judges writes its {@code m22_difficulty_stats} row (M22.6). */
class DifficultyStatsTest {
    private static final long EPOCH = 1_700_000_000_000L;
    private static final String CONTEXT = "v1:player:1:NORMAL";

    private final List<DifficultyStatDelta> rows = new ArrayList<>();
    private final DifficultyStats stats = new DifficultyStats(rows::addAll, () -> EPOCH);

    @Test
    void aTargetDeathCountsWithItsLogTimeAndTheEstimateTheSlowLoopLeft() {
        var target = UUID.randomUUID();
        var killed = player();
        killed.clock().dealt(0, target, target, 0.2, 0.8);
        killed.closeAs(FightEventKind.TARGET_DIED, 20, 0, 8, true, "guardian_ai");
        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("guardian_ai", row.profile());
        assertEquals(CONTEXT, row.context());
        assertEquals(8, row.rung(), "the fight was played at the opening rung");
        assertEquals(1, row.fights());
        assertEquals(0, row.censored());
        assertEquals(0, row.excluded());
        assertEquals(StrictMath.log(20 * TtkEstimator.SECONDS_PER_TICK), row.logTtkMean(), 1.0e-12);
        assertEquals(0.0, row.logTtkM2());
        assertEquals(killed.prior(), row.startRungEstimate());
        assertTrue(row.startRungEstimate() < 8.0, "a fight far under the target time lowers the estimate");
        assertEquals(EPOCH, row.startRungEpochMs());
        assertEquals(EPOCH, row.firstEpochMs());
        assertEquals(EPOCH, row.lastEpochMs());
    }

    @Test
    void aMobDeathAfterAValidHitIsCensoredAndOneWithoutEvidenceIsExcluded() {
        var target = UUID.randomUUID();
        var censored = player();
        censored.clock().dealt(0, target, target, 0.9, 0.1);
        censored.noteHit(60, 0.9);
        censored.closeAs(FightEventKind.MOB_DIED, 60, 0, 8, true, "guardian_ai");
        var missed = player();
        missed.clock().dealt(0, target, target, 0.2, 0.8);
        missed.closeAs(FightEventKind.MOB_DIED, 10, 0, 8, true, "guardian_ai");
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).fights());
        assertEquals(1, rows.get(0).censored(), "the time is projected from the last valid hit");
        assertEquals(0, rows.get(1).fights());
        assertEquals(1, rows.get(1).excluded());
        assertEquals(0L, rows.get(1).startRungEpochMs(), "an excluded fight never replaces a stored estimate");
        assertEquals(EPOCH, rows.get(1).lastEpochMs());
    }

    @Test
    void benchPlayAndAProfileWithoutLearnWriteNothing() {
        var target = UUID.randomUUID();
        var bench = new SessionDifficulty();
        bench.stats(stats);
        bench.resolve(0, false, 0, 0, 8, DifficultyMode.AUTO, DifficultyResolution.ABSENT, null, null, null, null,
                false, DifficultyResolution.ABSENT);
        assertEquals(PlayMode.BENCH, bench.play());
        bench.clock().dealt(0, target, target, 0.2, 0.8);
        bench.closeAs(FightEventKind.TARGET_DIED, 20, 0, 8, true, "guardian_ai");
        var fixed = player();
        fixed.clock().dealt(0, target, target, 0.2, 0.8);
        fixed.closeAs(FightEventKind.TARGET_DIED, 20, 0, 8, false, "guardian_ai");
        assertEquals(List.of(), rows);
    }

    @Test
    void aFightThatGoesQuietIsExcludedUnderTheProfileOfTheSession() {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.recordTarget(EngineFixtures.player(1), 3.0);
        session.difficulty().stats(stats);
        var reader = new QuietReader(Map.of(), true);
        var board = new IntentBoard();
        board.publish(session.key(), IntentView.base(0));
        session.difficulty().touch(session, 0L, reader, DecisionTap.NONE, board);
        assertEquals(PlayMode.PLAYER, session.difficulty().play());
        session.difficulty().clock().dealt(1, EngineFixtures.player(1), EngineFixtures.player(1), 0.1, 0.9);
        var quiet = DifficultyGlobals.DEFAULT.disengageTicks();
        for (var tick = 2L; tick <= quiet + 2L; tick++) {
            session.difficulty().touch(session, tick, reader, DecisionTap.NONE, board);
        }
        assertEquals(1, rows.size());
        assertEquals(session.profile().id(), rows.getFirst().profile());
        assertEquals(1, rows.getFirst().excluded());
        assertEquals(0, rows.getFirst().fights());
    }

    private SessionDifficulty player() {
        var difficulty = new SessionDifficulty();
        difficulty.stats(stats);
        difficulty.resolve(0, true, 0, 0, 8, DifficultyMode.PLAYER, DifficultyResolution.ABSENT, null, null, null,
                null, false, DifficultyResolution.ABSENT);
        assertEquals(PlayMode.PLAYER, difficulty.play());
        return difficulty;
    }
}
