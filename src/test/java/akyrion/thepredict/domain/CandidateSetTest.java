package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The prepared set of M20.6 and the local pick under the damage penalty of M22.7 (rule A10). */
class CandidateSetTest {
    private static final PressureLevers NO_PENALTY = new PressureLevers(0, ReactionMask.ALL, 0, true, 0);

    @Test
    void ofMovesTheBestToIndexZeroAndKeepsTheQuestionOrder() {
        var hold = candidate("hold_ground", 300, DamageTier.NONE);
        var press = candidate("press_attack", 800, DamageTier.MEDIUM);
        var lunge = candidate("lunge_in", 800, DamageTier.HIGH);
        var order = List.of(hold.tactic(), press.tactic(), lunge.tactic());
        var set = CandidateSet.of(4, 7, List.of(hold, press, lunge), Map.of(), order);
        assertEquals(List.of(press, hold, lunge), set.candidates(), "a tie keeps the earlier candidate first");
        assertEquals(order, set.questionOrder());
        assertEquals(0, set.localPick(NO_PENALTY));
    }

    @Test
    void ofRejectsAnEmptyListAsTheConstructorDoes() {
        assertThrows(IllegalArgumentException.class,
                () -> CandidateSet.of(0, 0, List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateSet(0, 0, List.of(), Map.of(), List.of()));
    }

    @Test
    void theConstructorRejectsARepeatedTacticAndALowerFirstScore() {
        var press = candidate("press_attack", 500, DamageTier.LOW);
        var hold = candidate("hold_ground", 600, DamageTier.NONE);
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateSet(0, 0, List.of(press, press), Map.of(), List.of(press.tactic())));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateSet(0, 0, List.of(press, hold), Map.of(), List.of(press.tactic(), hold.tactic())));
    }

    @Test
    void localPickMovesOnlyWhenThePenaltyOutweighsTheGapOfAHighTactic() {
        var heavy = candidate("heavy_hit", 800, DamageTier.HIGH);
        var medium = candidate("medium_hit", 700, DamageTier.MEDIUM);
        var light = candidate("light_hit", 650, DamageTier.LOW);
        var set = new CandidateSet(0, 1, List.of(heavy, medium, light), Map.of(),
                List.of(heavy.tactic(), medium.tactic(), light.tactic()));
        var gap = heavy.scoreMillis() - medium.scoreMillis();
        assertEquals(0, set.localPick(penalty(gap - 1)));
        assertEquals(0, set.localPick(penalty(gap)), "a tie keeps the earlier candidate");
        assertEquals(1, set.localPick(penalty(gap + 1)));
        assertEquals(1, set.localPick(penalty(1000)), "only HIGH pays: the medium hit never drops below the light one");
        var calm = new CandidateSet(0, 1, List.of(medium, light), Map.of(), List.of(medium.tactic(), light.tactic()));
        assertEquals(0, calm.localPick(penalty(1000)));
    }

    @Test
    void anIntentionChecksItsRanges() {
        assertThrows(IllegalArgumentException.class, () -> candidate("press_attack", 1001, DamageTier.LOW));
        assertThrows(IllegalArgumentException.class, () -> candidate("press_attack", -1, DamageTier.LOW));
        assertThrows(IllegalArgumentException.class, () -> new CandidateIntent(new TacticId("press_attack"),
                Movement.APPROACH, Optional.empty(), 10, -1, DamageTier.LOW));
        assertEquals(DamageTier.NONE, new CandidateIntent(new TacticId("press_attack"), Movement.APPROACH,
                Optional.empty(), 10, 0).damage());
    }

    private static PressureLevers penalty(int millis) {
        return new PressureLevers(0, ReactionMask.ALL, 0, true, millis);
    }

    private static CandidateIntent candidate(String tactic, int score, DamageTier damage) {
        return new CandidateIntent(new TacticId(tactic), Movement.APPROACH, Optional.empty(), score, 0, damage);
    }
}
