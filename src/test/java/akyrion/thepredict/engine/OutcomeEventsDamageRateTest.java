package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.engine.learn.DamageRateRing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage a member takes goes on the ring of the last 60 server ticks (entry C of learn-distill, M28.2), so the
 * rate a window snapshots when it opens is a fraction of health per tick, as the damage taken during the window is.
 */
class OutcomeEventsDamageRateTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    private MobSession engaged() {
        fixture.ready();
        var session = fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.run(100);
        return session;
    }

    @Test
    void damageTakenCountsOnTheTickItHappens() {
        var session = engaged();
        var tick = fixture.clock.tick;
        fixture.outcomes.taken(session.key().entityId(), 0.3, tick);
        fixture.outcomes.takenFrom(session.key().entityId(), EngineFixtures.player(1), 0.15, tick + 10);
        var ring = session.learning().damage();
        assertEquals(0.45 / DamageRateRing.TICKS, ring.rate(tick + 10), 1e-12, "both hits are inside the 60 ticks");
        assertEquals(0.15 / DamageRateRing.TICKS, ring.rate(tick + DamageRateRing.TICKS), 1e-12,
                "the first hit left the span, the second did not");
    }

    @Test
    void aNewWindowSnapshotsTheRatePerTickOfTheTicksBeforeIt() {
        var session = engaged();
        var tick = fixture.clock.tick;
        fixture.outcomes.taken(session.key().entityId(), 0.6, tick);
        fixture.outcomes.adopted(session, new TacticalIntent(session.key(), GUARD, Movement.HOLD, Optional.empty(), 0.8,
                List.of(), Map.of(), ControlLevel.JEV, tick + 1, Long.MAX_VALUE, false), tick + 1, false);
        assertTrue(session.outcomes().open().isPresent(), "the adopted intent opened a window");
        assertEquals(0.6 / DamageRateRing.TICKS, session.learning().rateBefore(), 1e-12);
    }

    @Test
    void aHitOnTheTargetConfirmsAPendingMotivatedCast() {
        var session = engaged();
        var tick = fixture.clock.tick;
        session.noteCastQuota((int) tick, true);
        fixture.outcomes.dealt(session.key().entityId(), EngineFixtures.player(1), 0.1, tick + 3);
        session.noteCastQuota((int) tick + 40, false);
        assertTrue(session.lastMotivatedLanded(), "the hit that the listener reported confirmed the cast");
    }
}
