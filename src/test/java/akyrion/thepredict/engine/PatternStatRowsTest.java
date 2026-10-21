package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.TickSignals;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.far;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS point 7: a closing encounter writes its rows of {@code m26_pattern_stats} (M26.9) with what the step measured. */
class PatternStatRowsTest {
    private static final long EPOCH = 1_700_000_000_000L;

    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final PatternInbox inbox = new PatternInbox();

    @Test
    void theCloseWritesOneRowPerPatternWithTrialsAndTheMotivatedCastsOfTheMember() {
        var rows = new ArrayList<PatternStatDelta>();
        var director = director(PatternMode.OBSERVE);
        director.useStats(rows::addAll, () -> EPOCH);
        fixture.encounters.hearCloses(director::close);
        var enemy = player(5);
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, WORLD, enemy, far());
        encounter.aliasFor(enemy, ParticipantKind.PLAYER);
        session.recordTarget(enemy, 3.0);
        session.playerTarget(true);
        session.tickSignals(TickSignals.PRESENT);
        inbox.admit(enemy);
        for (var tick = 0; tick < 12; tick++) {
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), tick);
        }
        director.visit(session, 12);
        var model = director.modelOf(enemy).orElseThrow();
        assertNotEquals(0, model.activeMask(), "the strikes activated a pattern");
        var expected = new LinkedHashMap<String, Integer>();
        for (var pattern : EnemyPattern.values()) {
            if (model.ownTrials(pattern) > 0) {
                expected.put(pattern.name().toLowerCase(Locale.ROOT), model.activations(pattern));
            }
        }
        var exploited = EnemyPattern.SPAMS_CLICKS;
        session.noteCastQuota(20, true);
        session.notePatternExploit(exploited.ordinal());
        session.noteMotivatedHit(22);
        fixture.encounters.leave(session);
        assertFalse(expected.isEmpty());
        for (var row : rows) {
            assertEquals(session.profile().id(), row.profile());
            assertEquals("player", row.targetKind());
            assertEquals("observe", row.mode());
            assertEquals(0, row.counterWindows(), "no counter effect is measured yet");
            assertEquals(EPOCH, row.firstEpochMs());
        }
        for (var entry : expected.entrySet()) {
            var row = rows.stream().filter(found -> found.pattern().equals(entry.getKey())).findFirst().orElseThrow();
            assertEquals(1, row.encounters(), entry.getKey());
            assertEquals((long) entry.getValue(), row.activations(), entry.getKey());
        }
        assertTrue(rows.stream().mapToLong(PatternStatDelta::activations).sum() > 0);
        var counter = rows.stream().filter(found -> found.pattern().equals(exploited.name().toLowerCase(Locale.ROOT)))
                .findFirst().orElseThrow();
        assertEquals(1, counter.exploitedCasts());
        assertEquals(1, counter.exploitedLanded(), "a hit within the window settled the motivated cast");
    }

    @Test
    void aMemberWhosePatternsAreOffWritesNoRowWhateverItsModelSaw() {
        var rows = new ArrayList<PatternStatDelta>();
        var director = director(PatternMode.OFF);
        director.useStats(rows::addAll, () -> EPOCH);
        fixture.encounters.hearCloses(director::close);
        var enemy = player(6);
        var session = fixture.open(2);
        var encounter = fixture.encounters.assign(session, WORLD, enemy, far());
        encounter.aliasFor(enemy, ParticipantKind.PLAYER);
        session.recordTarget(enemy, 3.0);
        session.tickSignals(TickSignals.PRESENT);
        inbox.admit(enemy);
        for (var tick = 0; tick < 12; tick++) {
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), tick);
        }
        director.visit(session, 12);
        assertEquals(PatternMode.OFF, session.patternMode());
        fixture.encounters.leave(session);
        assertEquals(List.of(), rows);
    }

    @Test
    void theMotivatedCastsOfOneEncounterNeverReachTheNext() {
        var session = fixture.open(3);
        session.joinEncounter(new EncounterId(41));
        session.noteCastQuota(5, true);
        session.notePatternExploit(EnemyPattern.SPAMS_CLICKS.ordinal());
        assertEquals(1, session.exploitCasts(EnemyPattern.SPAMS_CLICKS.ordinal()));
        assertFalse(session.exploitCounters().isEmpty());
        session.joinEncounter(new EncounterId(41));
        assertEquals(1, session.exploitCasts(EnemyPattern.SPAMS_CLICKS.ordinal()), "the same encounter keeps them");
        session.joinEncounter(new EncounterId(42));
        assertEquals(0, session.exploitCasts(EnemyPattern.SPAMS_CLICKS.ordinal()));
        assertTrue(session.exploitCounters().isEmpty(), "the memory of the next fight starts without them");
    }

    private PatternDirector director(PatternMode mode) {
        var gate = new PatternModeGate();
        gate.offer(mode, true);
        var director = new PatternDirector(inbox, new KinematicBook(), PatternSettings.DEFAULT, gate, () -> 0L);
        director.bind(fixture.registry, fixture.encounters, new DecisionMetrics(() -> 0L));
        return director;
    }
}
