package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationBinBatchTest {
    @Test
    void aPositiveJevCloseFeedsABinThroughBatchApplied() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var outcomes = new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics);
        var bins = new ArrayList<CalibrationBinDelta>();
        outcomes.hearBins((profile, delta) -> bins.add(delta));
        var session = sessions.open(1);
        var target = UUID.randomUUID();
        session.recordTarget(target, 1.0);
        outcomes.adopted(session, intent(session, ControlLevel.JEV, 0.55), 0, false);
        outcomes.dealt(session.key().entityId(), target, 0.4, 1);
        outcomes.close(session, 2);
        assertEquals(1, bins.size());
        assertEquals(CalibrationBins.bin(0.55), bins.get(0).bin());
        assertEquals(1L, bins.get(0).n());
        assertEquals(1.0, bins.get(0).successSum());

        bins.clear();
        outcomes.adopted(session, intent(session, ControlLevel.LOCAL, 0.55), 3, false);
        outcomes.dealt(session.key().entityId(), target, 0.4, 4);
        outcomes.close(session, 5);
        outcomes.adopted(session, intent(session, ControlLevel.ALTERNATIVE, 0.55), 6, false);
        outcomes.dealt(session.key().entityId(), target, 0.4, 7);
        outcomes.close(session, 8);
        outcomes.adopted(session, intent(session, ControlLevel.JEV, 0.55), 9, false);
        outcomes.close(session, 10);
        assertTrue(bins.isEmpty());

        var inbox = new Inbox<InboxEntry>(8);
        var cache = new AggregateCache(accepting(), new StorageMetrics(), 4096, 100, 4, inbox);
        cache.noteBin(new CalibrationBinDelta("jev", "choice", "effect_positive", CalibrationBins.bin(0.55), 1L, 0.55,
                1.0, 0L));
        var sent = cache.flush(0, 10).orElseThrow();
        assertEquals(1, sent.calibration().size());
        assertEquals(1, cache.inFlight());
        inbox.offerStorage(new StorageReply.BatchApplied(sent.batchId(), 1));
        cache.drain(4);
        assertEquals(0, cache.inFlight());
    }

    private static TacticalIntent intent(MobSession session, ControlLevel level, double confidence) {
        return new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD, Optional.empty(),
                confidence, List.of(), Map.of(), level, 0, Long.MAX_VALUE, false);
    }

    private static AggregateStore accepting() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        };
    }
}
