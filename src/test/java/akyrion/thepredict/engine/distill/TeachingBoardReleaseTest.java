package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.LabelRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeachingBoardReleaseTest {
    @Test
    void aThousandMobsThatCaptureAndLeaveLeaveNothingBehind() {
        var board = new TeachingBoard();
        for (var mob = 0; mob < 1000; mob++) {
            note(board, "mob-" + mob);
            board.release("mob-" + mob);
        }
        assertFalse(board.noted());
        assertEquals(0, board.snapshots());
    }

    @Test
    void snapshotsThatNeverCloseStayUnderTheCapOldestFirst() {
        var board = new TeachingBoard();
        for (var mob = 0; mob < TeachingBoard.MAX_SNAPSHOTS + 40; mob++) {
            note(board, "mob-" + mob);
        }
        assertEquals(TeachingBoard.MAX_SNAPSHOTS, board.snapshots());
        assertFalse(board.noted("mob-0"));
        assertFalse(board.noted("mob-39"));
        assertTrue(board.noted("mob-40"));
        note(board, "mob-40");
        note(board, "mob-" + (TeachingBoard.MAX_SNAPSHOTS + 40));
        assertTrue(board.noted("mob-40"));
        assertFalse(board.noted("mob-41"));
    }

    private static void note(TeachingBoard board, String mob) {
        board.note(mob, "Guardian", "r1:ALL", "v2:MELEE:HIGH:HIGH:MOB:NA:SOLO:NONE", "outcome",
                List.of("alpha", "bravo"), List.of(new LabelRule.Estimate("alpha", 0.0, 0.0, 0),
                        new LabelRule.Estimate("bravo", 0.0, 0.0, 0)), 0, true);
    }
}
