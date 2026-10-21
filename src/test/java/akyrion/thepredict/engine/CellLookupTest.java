package akyrion.thepredict.engine;

import akyrion.thepredict.domain.learn.ValueArtifact;
import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTable;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LD-04, LD-13, LD-15: every reader of the learned table reads the cell of the profile id and the live context. */
class CellLookupTest {
    @Test
    void aNamedTableIsReadByProfileAndTacticIdInTheLiveCell() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var profile = session.profile().id();
        var cell = new ArtifactFormat.ValueCell(0, 0, 1, 0L, 900, 10, 40, 0, 0, 0);
        var artifact = new ValueArtifact(0, 1, "spec", 2, List.of(), 30, 8, List.of(0, 0, 0, 0), List.of(profile),
                List.of("keep_range"), List.of(cell));
        var table = ValueTable.of(artifact);
        assertTrue(table.profileIndex(profile) >= 0);
        session.learning().stage(table, LearnedArtifacts.State.ACTIVE, "PASS", "all", 7, 99L);
        assertEquals(900, CellLookup.estimate(session, "keep_range", 20).valueMillis());
        assertTrue(CellLookup.estimate(session, "press_attack", 20).none());
        assertEquals(table.lookup(profile, "keep_range", WindowCredit.live(session)),
                CellLookup.estimate(session, "keep_range", 20));
        var live = CellLookup.live(session, false);
        assertTrue(live.text().endsWith(":NONE"));
        assertFalse(CellLookup.patternContextDiffers(session));
    }

    @Test
    void aTableWithoutNamesKeepsTheLookupByCodeAndInstalledContext() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var builder = new ValueTableBuilder();
        builder.add(7, LearnedLocalPick.tacticCode("keep_range"), 3, 99L, 0.9, 0.01, 40, 1.0);
        var table = builder.build();
        session.learning().stage(table, LearnedArtifacts.State.ACTIVE, "PASS", "all", 7, 99L);
        assertEquals(900, CellLookup.estimate(session, "keep_range", 20).valueMillis(), 1,
                "the legacy entry still finds its cell");
        assertTrue(CellLookup.estimate(session, "press_attack", 20).none());
    }
}
