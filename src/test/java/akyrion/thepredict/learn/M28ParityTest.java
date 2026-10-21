package akyrion.thepredict.learn;

import akyrion.thepredict.adapter.storage.RecordEvent;
import akyrion.thepredict.adapter.storage.RecordLine;
import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.ZoneBand;
import akyrion.thepredict.engine.duel.SkirmishSimulator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Parity against golden/m28/parity. A hash of the tree against itself is not the check. */
class M28ParityTest {
    @Test
    void collectOffMatchesTheGoldenFiles() throws Exception {
        var root = Path.of("src/test/resources/golden/m28/parity");
        var key = ContextKey.of(0, HealthBand3.HIGH, HealthBand3.MID, false, ZoneBand.OPEN, GroupBand.SOLO, 0);
        assertEquals(Files.readString(root.resolve("context-key.txt")).trim(), key.text());
        var press = new CandidateIntent(new TacticId("press"), Movement.APPROACH, Optional.empty(), 100, 0);
        var keep = new CandidateIntent(new TacticId("keep"), Movement.HOLD, Optional.empty(), 900, 0);
        var set = CandidateSet.of(1L, 0, List.of(press, keep), Map.of(), List.of(press.tactic(), keep.tactic()));
        assertEquals(Files.readString(root.resolve("question-order.txt")).trim(),
                set.questionOrder().get(0).value() + "," + set.questionOrder().get(1).value());
        assertNotEquals(set.questionOrder().get(0), set.candidates().getFirst().tactic());
        var planned = RecordLine.format(new RecordEvent("planned", "m28", 1L, 2L, 3L, "", Map.of()));
        assertEquals(Files.readString(root.resolve("planned-line.json")).trim(), planned);
        var measured = SkirmishSimulator.run(new SkirmishSimulator.Fight("m28-seed", 5, 1, false,
                SkirmishSimulator.TICKS), SkirmishSimulator.Arm.OFF, 28L);
        var line = measured.deathTick() + " " + measured.impacts() + " " + measured.squadWounds() + " "
                + measured.denials();
        assertEquals(Files.readString(root.resolve("skirmish-seed-28.txt")).trim(), line);
    }
}
