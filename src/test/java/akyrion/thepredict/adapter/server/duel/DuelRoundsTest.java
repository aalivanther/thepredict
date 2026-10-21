package akyrion.thepredict.adapter.server.duel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelRoundsTest {
    private static final UUID RED = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BLUE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aRoundStartsWithBothSidesAndADeathGivesItToTheOtherSide() {
        var rounds = new DuelRounds(0);
        rounds.follow(RED, DuelRounds.Side.RED, DuelRounds.Kind.TP, -9.5);
        assertTrue(rounds.tryStart(10).isEmpty(), "one side alone does not start a round");
        rounds.follow(BLUE, DuelRounds.Side.BLUE, DuelRounds.Kind.CLASSIC, 10.5);
        var round = rounds.tryStart(15).orElseThrow();
        assertEquals(1, round.number());
        assertEquals("A", round.arm());
        assertFalse(round.spawnSwapped());
        rounds.hit(DuelRounds.Side.RED, 0.25);
        rounds.request();
        rounds.tokens(900);
        var ended = rounds.gone(BLUE, true, 415).orElseThrow();
        assertEquals("red", ended.winner());
        assertEquals("death", ended.endReason());
        assertEquals(400, ended.ticks());
        assertEquals("1,A,false," + RED + ",tp," + BLUE + ",classic,15,415,red,death,0.25,0,1,0,1,900", ended.row());
        assertFalse(rounds.running());
        assertEquals(1, rounds.round());
    }

    @Test
    void aRemovalIsADrawAndTheArmAndSideComeFromTheKindsAndTheSpawn() {
        var rounds = new DuelRounds(0);
        rounds.follow(RED, DuelRounds.Side.RED, DuelRounds.Kind.CLASSIC, 10.5);
        rounds.follow(BLUE, DuelRounds.Side.BLUE, DuelRounds.Kind.TP, -9.5);
        var round = rounds.tryStart(0).orElseThrow();
        assertEquals("B", round.arm());
        assertTrue(round.spawnSwapped(), "red appeared on the spawn point of blue");
        var ended = rounds.gone(RED, false, 1800).orElseThrow();
        assertEquals("draw", ended.winner());
        assertEquals("removed", ended.endReason());
        assertTrue(rounds.gone(BLUE, false, 1801).isEmpty(), "the round already ended");
    }

    @Test
    void theFourArmsAndTheLimitOfFollowedDuelists() {
        assertEquals("A", DuelRounds.arm(DuelRounds.Kind.TP, DuelRounds.Kind.CLASSIC));
        assertEquals("B", DuelRounds.arm(DuelRounds.Kind.CLASSIC, DuelRounds.Kind.TP));
        assertEquals("C", DuelRounds.arm(DuelRounds.Kind.TP, DuelRounds.Kind.TP));
        assertEquals("D", DuelRounds.arm(DuelRounds.Kind.CLASSIC, DuelRounds.Kind.CLASSIC));
        var rounds = new DuelRounds(0);
        for (var index = 0; index < DuelRounds.MAX_TRACKED; index++) {
            assertTrue(rounds.follow(new UUID(0, index + 100), DuelRounds.Side.RED, DuelRounds.Kind.TP, 0));
        }
        assertFalse(rounds.follow(RED, DuelRounds.Side.RED, DuelRounds.Kind.TP, 0));
        assertEquals(8, rounds.tracked());
        assertEquals(DuelRounds.NO_ARM, rounds.arm());
        assertEquals(0, rounds.round());
    }

    @Test
    void aDuelistOfASideWithAnotherAliveDoesNotEndTheRound() {
        var rounds = new DuelRounds(0);
        var second = new UUID(0, 77);
        rounds.follow(RED, DuelRounds.Side.RED, DuelRounds.Kind.TP, -9);
        rounds.follow(second, DuelRounds.Side.RED, DuelRounds.Kind.TP, -8);
        rounds.follow(BLUE, DuelRounds.Side.BLUE, DuelRounds.Kind.TP, 9);
        assertEquals("C", rounds.tryStart(0).orElseThrow().arm());
        assertTrue(rounds.gone(RED, true, 5).isEmpty());
        assertEquals("blue", rounds.gone(second, true, 9).orElseThrow().winner());
    }
}
