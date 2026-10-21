package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.distill.DistillSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PZ-12: only a tell in flight holds the regime (M29.5); a closed one leaves its length but not the hold. */
class RegimeCaptureTest {
    private static final DistillSettings SETTINGS = DistillSettings.DEFAULT;

    @Test
    void aClosedTellNoLongerHoldsTheRegimeAndItChangesAfterTheDwell() {
        var session = session();
        session.openTell(0, 0L, 20, 10, 10, false, UUID.randomUUID());
        session.closeTell();
        assertTrue(session.telegraphTicks() > 0, "the shown length stays until the next generation");
        var blocked = consider(session, 0L, SETTINGS.dwellTicks());
        assertEquals(List.of(""), blocked, "no TELEGRAPH hold, one change once the dwell passed");
    }

    @Test
    void aTellInFlightStillHoldsTheRegime() {
        var session = session();
        session.openTell(0, 0L, 20, 10, 10, false, UUID.randomUUID());
        assertEquals(List.of("TELEGRAPH", "TELEGRAPH"), consider(session, 0L, SETTINGS.dwellTicks()));
    }

    private static List<String> consider(MobSession session, long... ticks) {
        var encounter = new Encounter(new EncounterId(3), new UUID(0, 1), 0);
        encounter.addMember(session);
        var lines = new ArrayList<String>();
        var tap = new DecisionTap() {
            @Override
            public void regimeChanged(MobKey mob, String from, String to, String cause, int dwellTicks,
                                      String blockedBy) {
                lines.add(blockedBy);
            }
        };
        for (var tick : ticks) {
            RegimeCapture.consider(session, encounter, tick, SETTINGS, tap);
        }
        return lines;
    }

    private static MobSession session() {
        return new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
    }
}
