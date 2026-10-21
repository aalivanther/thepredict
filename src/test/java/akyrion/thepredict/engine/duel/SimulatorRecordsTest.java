package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.storage.RecordEvent;
import akyrion.thepredict.adapter.storage.RecordTap;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.DecisionTap;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatorRecordsTest {
    @Test
    void theRoundLineAndTheEncounterCloseShareTheTimeToKill() {
        var events = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.FULL, RecordBodies.NONE, "20261006T120000Z-0a1b", Clock.systemUTC(),
                () -> 1L, System::nanoTime, events::add);
        var simulator = new DuelSimulator(new DuelSimulator.Mode.None());
        simulator.records(tap);
        simulator.round(DuelSimulator.Arm.D, 1L, false, DuelSimulator.Options.PLAIN);
        var round = events.stream().filter(event -> event.type().equals("round")).findFirst().orElseThrow();
        var closed = events.stream().filter(event -> event.type().equals("encounter_close")).findFirst().orElseThrow();
        assertEquals(round.fields().get("ttk"), closed.fields().get("ttk"));
    }

    @Test
    void dodgeAfterTelegraphMatchesTheRoundAndTheLines() {
        var events = new ArrayList<RecordEvent>();
        var fight = SwordPlayerDuelist.fight("sword", 0.5, 8, 23L, tap(events), false);
        assertEquals(fight.metrics().telegraphs(), events.size());
        assertEquals(fight.metrics().dodgeAfterTelegraphRatio(), dodge(events), 0.0);
    }

    @Test
    void aPlayerWhoAlwaysRetreatsWhiffsAndOneWhoStaysDoesNot() {
        var retreated = new ArrayList<RecordEvent>();
        SwordPlayerDuelist.fight("kiter-archer", 0.5, 4, 11L, tap(retreated), true);
        assertEquals(1.0, whiff(retreated), 0.0);
        var stayed = new ArrayList<RecordEvent>();
        SwordPlayerDuelist.fight("kiter-archer", 0.5, 4, 11L, tap(stayed), false);
        assertEquals(0.0, whiff(stayed), 0.0);
    }

    private static RecordTap tap(ArrayList<RecordEvent> events) {
        return new RecordTap(RecordLevel.DECISIONS, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L,
                () -> 2L, events::add);
    }

    private static double dodge(ArrayList<RecordEvent> events) {
        var landed = 0;
        var avoided = 0;
        var blocked = 0;
        for (var event : events) {
            if (!"telegraph".equals(event.type()) || String.valueOf(event.fields().get("end")).contains("FEINTED")) {
                continue;
            }
            var result = String.valueOf(event.fields().get("result"));
            if (result.contains("LANDED")) {
                landed++;
            } else if (result.contains("AVOIDED")) {
                avoided++;
            } else if (result.contains("BLOCKED")) {
                blocked++;
            }
        }
        var denominator = landed + avoided + blocked;
        return denominator == 0 ? 0.0 : (avoided + blocked) / (double) denominator;
    }

    private static double whiff(ArrayList<RecordEvent> events) {
        var whiffed = 0;
        var released = 0;
        for (var event : events) {
            var end = String.valueOf(event.fields().get("end"));
            if (end.contains("WHIFFED")) {
                whiffed++;
            } else if (end.contains("RELEASED")) {
                released++;
            }
        }
        var denominator = whiffed + released;
        return denominator == 0 ? 0.0 : whiffed / (double) denominator;
    }
}
