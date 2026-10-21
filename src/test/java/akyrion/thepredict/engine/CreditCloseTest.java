package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.domain.learn.ZoneBand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditCloseTest {
    @Test
    void closingTheEncounterDistributesTheClosedWindow() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        fixture.encounters.assign(session, WORLD, player(1), near());
        var outcomes = new OutcomeEvents(fixture.registry, fixture.encounters, new OutcomeMetrics(),
                new DecisionMetrics(() -> 0L));
        for (var tick = 0; tick < 60; tick++) {
            session.learning().damage().add(1000);
        }
        session.noteProbe(new MobProbe(session.key(), WORLD, HealthBand.WOUNDED, Optional.of(player(1)),
                Optional.of(DistanceBand.RANGED)), 1L);
        var expected = ContextKey.of(ContextKey.distanceCode(DistanceBand.RANGED), HealthBand3.MID, HealthBand3.HIGH,
                false, ZoneBand.NA, GroupBand.SOLO, 0).text();
        outcomes.adopted(session, intent(session, 1L), 1L, false);
        outcomes.dealt(session.key().entityId(), player(1), 0.4, 2L);
        outcomes.taken(session.key().entityId(), 0.05, 20.0, 3L);
        outcomes.close(session, 10L);
        var rows = new ArrayList<ValueStatDelta>();
        fixture.encounters.useLearning(LearningSettings.DEFAULT, rows::addAll, () -> 5L);
        fixture.encounters.leave(session);
        assertEquals(1, rows.size());
        var effect = OutcomeWindow.Summary.effect(0.4, 0.05, false, false, false);
        assertEquals("retreat", rows.getFirst().key().tactic());
        assertEquals(expected, rows.getFirst().key().context());
        assertEquals(CreditMath.effectTerm(effect), rows.getFirst().effectSum(), 1.0e-9);
        assertTrue(rows.getFirst().effectSum() != 0.0);
        assertTrue(rows.getFirst().avoidedSum() > 0.0);
        outcomes.adopted(session, intent(session, 20L), 20L, false);
        outcomes.dealt(session.key().entityId(), player(1), 0.4, 21L);
        outcomes.close(session, 30L);
        var drained = CreditClose.shutdown(session, 9L);
        var bench = TrainingSource.BENCH.token();
        assertEquals(bench, drained.getFirst().key().source(),
                "an encounter without a player participant is bench play, never a close cause nor unknown");
        assertEquals(ValueStatKey.week(9L), drained.getFirst().key().week());
        assertEquals(bench, rows.getFirst().key().source());
        assertEquals(ValueStatKey.week(5L), rows.getFirst().key().week());
        assertEquals("retreat", drained.getFirst().key().tactic());
        assertEquals(expected, drained.getFirst().key().context());
        var cache = new AggregateCache(ready(), new StorageMetrics(), 32, 100, 4, new Inbox<>(4));
        cache.acceptValues(rows);
        assertEquals(1, cache.finalBatch().orElseThrow().values().size());
        cache.acceptValues(drained);
        assertEquals(bench, cache.finalBatch().orElseThrow().values().getFirst().key().source());
        assertFalse(cache.finalBatch().isPresent());
    }

    @Test
    void aWindowOfAnEncounterWithAPlayerCarriesThePlayerSourceAndTheAdapterClock() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, WORLD, player(1), near());
        var alias = new ParticipantAlias(ParticipantKind.PLAYER, 1);
        encounter.lastFacts(new CombatFacts(encounter.id(), 4L, 0L, List.of(),
                List.of(new ParticipantFacts(alias, HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 1));
        var outcomes = new OutcomeEvents(fixture.registry, fixture.encounters, new OutcomeMetrics(),
                new DecisionMetrics(() -> 0L));
        var heard = new ArrayList<Long>();
        fixture.encounters.useLearning(LearningSettings.DEFAULT, rows -> {
        }, () -> {
            heard.add(7L);
            return 7L * ValueStatKey.WEEK_MS;
        });
        outcomes.adopted(session, intent(session, 1L), 1L, false);
        outcomes.close(session, 10L);
        assertEquals(1, heard.size(), "the window close reads the clock the adapter installed");
        var row = CreditClose.shutdown(session, 7L * ValueStatKey.WEEK_MS).getFirst();
        assertEquals(TrainingSource.of(true, false, session.difficulty().play(), session.difficulty().rung()).token(),
                row.key().source());
        assertNotEquals(bench(), row.key().source(), "a player participant is never bench play");
    }

    private static String bench() {
        return TrainingSource.BENCH.token();
    }

    private static TacticalIntent intent(MobSession session, long tick) {
        return new TacticalIntent(session.key(), new TacticId("retreat"), Movement.RETREAT, Optional.empty(), 0.8,
                List.of(), Map.of(), ControlLevel.LOCAL, tick, Long.MAX_VALUE, false);
    }

    private static AggregateStore ready() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0L);
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
