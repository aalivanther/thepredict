package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.CounterBias;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyProfileSummary;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternCause;
import akyrion.thepredict.domain.PatternChange;
import akyrion.thepredict.domain.PatternExploit;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.EncounterClosed;
import akyrion.thepredict.engine.SessionMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternRecordLinesTest {
    @TempDir
    Path directory;

    @Test
    void theTapWritesTheSourceOfAPatternExploitAndNoMobAsItsParticipant() {
        var events = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.FULL, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L, () -> 2L,
                events::add);
        tap.patternExploited(new MobKey(new UUID(1, 2), 1), new PatternExploit(EnemyPattern.SPAMS_CLICKS,
                CounterBias.OPPORTUNITY, true));
        var tapped = RecordLine.format(events.getFirst());
        assertTrue(tapped.contains("\"source\":\"counter_map\""), tapped);
        assertTrue(tapped.contains("\"participant\":null"), "a participant that is not named is not the mob");
    }

    @Test
    void recordReaderReadsThePatternLines() throws Exception {
        var fields = new LinkedHashMap<String, String>();
        fields.put("pattern", JsonText.quote("spams_clicks"));
        fields.put("mode", JsonText.quote("observe"));
        var line = RecordLine.format(new RecordEvent("pattern_exploit", "run", 1L, 2L, 3L, "m26", fields));
        var file = directory.resolve("patterns.jsonl");
        Files.writeString(file, line + "\n");
        var read = RecordReader.read(file);
        assertEquals(1, read.size());
        assertEquals("pattern_exploit", read.getFirst().type());
        assertEquals("spams_clicks", read.getFirst().object().get("pattern").getAsString());
        var summary = RecordLine.format(new RecordEvent("pattern_summary", "run", 1L, 2L, 4L, "m26",
                java.util.Map.of("summary_b64", JsonText.quote(""))));
        var diag = RecordLine.format(new RecordEvent("pattern_diag", "run", 1L, 2L, 5L, "m26",
                java.util.Map.of("signal", JsonText.quote("AIR_SWING"))));
        var close = RecordLine.format(new RecordEvent("encounter_close", "run", 1L, 2L, 6L, "m26",
                java.util.Map.of("pattern_summaries", "0")));
        var parsed = RecordReader.parse(List.of(summary, diag, close));
        assertEquals(3, parsed.size());
        assertEquals("pattern_summary", parsed.get(0).type());
        assertEquals("pattern_diag", parsed.get(1).type());
        assertEquals("encounter_close", parsed.get(2).type());
    }

    @Test
    void recordTapEmitsPatternLinesAndCloseCountsSummariesWithoutASource() {
        var decisions = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.DECISIONS, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L,
                () -> 2L, decisions::add);
        var encounter = new EncounterId(9);
        var player = new UUID(1, 2);
        tap.patternChanged(encounter, player, new PatternChange(player, EnemyPattern.SPAMS_CLICKS, PatternCause.ENTER,
                PatternState.ACTIVE));
        tap.patternSummary(encounter, player, EnemyProfileSummary.empty());
        tap.patternExploited(new MobKey(player, 1), new PatternExploit(EnemyPattern.SPAMS_CLICKS,
                CounterBias.OPPORTUNITY, true));
        tap.patternDiag(player, "sword", 0, true, "", true);
        tap.encounterClosed(encounter, new EncounterClosed(encounter, new UUID(3, 4), 1L, 2L,
                SessionMetrics.EncounterEnd.COMBAT_ENDED, List.of(), Map.of(), Set.of(), 0, 0));
        assertEquals(List.of("pattern", "pattern_summary", "encounter_close"),
                decisions.stream().map(RecordEvent::type).toList());
        var pattern = RecordLine.format(decisions.get(0));
        assertTrue(pattern.contains("\"pattern\""));
        assertTrue(pattern.contains("\"cause\""));
        assertTrue(pattern.contains("\"trials\""));
        var summary = RecordLine.format(decisions.get(1));
        assertTrue(summary.contains("\"summary_bytes\""));
        assertTrue(summary.contains("\"summary_v\""));
        assertTrue(summary.contains("\"summary\""));
        var close = RecordLine.format(decisions.get(2));
        assertTrue(close.contains("\"pattern_summaries\":1"), close);
        var fullEvents = new ArrayList<RecordEvent>();
        var full = new RecordTap(RecordLevel.FULL, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L, () -> 2L,
                fullEvents::add);
        full.patternExploited(new MobKey(player, 1), new PatternExploit(EnemyPattern.SPAMS_CLICKS,
                CounterBias.OPPORTUNITY, false));
        full.patternDiag(player, "sword", 40, true, "shield", false);
        assertEquals(List.of("pattern_exploit", "pattern_diag"), fullEvents.stream().map(RecordEvent::type).toList());
        var exploit = RecordLine.format(fullEvents.get(0));
        assertTrue(exploit.contains("\"source\":\"counter_map\""), exploit);
        assertTrue(exploit.contains("\"quota_used\""));
        assertTrue(exploit.contains("\"result\""));
        var diag = RecordLine.format(fullEvents.get(1));
        assertTrue(diag.contains("AIR_SWING"));
        assertTrue(diag.contains("\"would_token\""));
    }
}
