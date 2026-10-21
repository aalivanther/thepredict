package akyrion.thepredict.eval;

/**
 * Bradley–Terry Elo and a minorization–maximization fit (M22.3). {@code 400·log10(wins/losses)} anchors classic at 0.
 */
public final class BradleyTerry {
    /** Wins a player without wins is credited, so its strength and Elo stay finite (Haldane, SIM-16). */
    static final double HALDANE = 0.5;

    private BradleyTerry() {
    }

    /**
     * Strengths after minorization–maximization. They sum to the player count. A player who played but never won
     * counts {@value #HALDANE} wins.
     *
     * @param wins {@code wins[i][j]} is how often {@code i} beat {@code j}
     * @param iterations iterations, at least 1
     * @return one strength per player
     */
    public static double[] strengths(int[][] wins, int iterations) {
        var count = wins.length;
        var strength = new double[count];
        for (var player = 0; player < count; player++) {
            strength[player] = 1.0;
        }
        for (var step = 0; step < iterations; step++) {
            var next = new double[count];
            for (var player = 0; player < count; player++) {
                next[player] = update(wins, strength, player);
            }
            var sum = 0.0;
            for (var value : next) {
                sum += value;
            }
            for (var player = 0; player < count; player++) {
                strength[player] = next[player] * count / sum;
            }
        }
        return strength;
    }

    /** One minorization–maximization step of a player. */
    private static double update(int[][] wins, double[] strength, int player) {
        var score = 0.0;
        var games = 0.0;
        for (var other = 0; other < wins.length; other++) {
            var played = player == other ? 0 : wins[player][other] + wins[other][player];
            if (played == 0) {
                continue;
            }
            score += wins[player][other];
            games += played / (strength[player] + strength[other]);
        }
        return games == 0.0 ? strength[player] : Math.max(score, HALDANE) / games;
    }

    /**
     * Elo of one strength against another.
     *
     * @param mine strength of the player
     * @param other strength of the anchor
     * @return {@code 400·log10(mine/other)}
     */
    public static double elo(double mine, double other) {
        return 400.0 * StrictMath.log10(mine / other);
    }

    /**
     * Elo of a rival that beat classic {@code wins} times and lost {@code losses} times.
     *
     * @param wins wins against classic
     * @param losses losses against classic
     * @return the Elo, finite with zero wins or losses; classic itself is 0
     */
    public static double elo(int wins, int losses) {
        if (wins == losses) {
            return 0.0;
        }
        return EvalStats.elo(wins, losses);
    }
}
