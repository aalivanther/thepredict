package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 70/100 record is about 147 Elo and stays inside 20 of that figure (M22.3). */
class BradleyTerryTest {
    @Test
    void seventyWinsStayWithinTwentyElo() {
        var elo = BradleyTerry.elo(70, 30);
        assertEquals(400.0 * Math.log10(70.0 / 30.0), elo, 1e-9);
        assertTrue(Math.abs(elo - 147.0) <= 20.0, "elo " + elo);
        assertEquals(0.0, BradleyTerry.elo(50, 50));
        var wins = new int[][] {{0, 70}, {30, 0}};
        var strength = BradleyTerry.strengths(wins, 40);
        var fitted = BradleyTerry.elo(strength[0], strength[1]);
        assertTrue(Math.abs(fitted - elo) <= 20.0, "fitted " + fitted);
    }

    @Test
    void aPlayerWithoutWinsHasAFiniteElo() {
        assertTrue(Double.isFinite(BradleyTerry.elo(0, 40)), "no wins");
        assertTrue(Double.isFinite(BradleyTerry.elo(40, 0)), "no losses");
        assertTrue(BradleyTerry.elo(0, 40) < BradleyTerry.elo(1, 39));
        var strength = BradleyTerry.strengths(new int[][] {{0, 40}, {0, 0}}, 40);
        assertTrue(strength[1] > 0.0, "a player that only lost keeps a positive strength");
        assertTrue(Double.isFinite(BradleyTerry.elo(strength[1], strength[0])));
    }
}
