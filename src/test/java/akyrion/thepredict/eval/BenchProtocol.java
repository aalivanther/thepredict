package akyrion.thepredict.eval;

/**
 * Lexicographic bench order: wins, then the CIF median, then damage taken, then USD (M22.10). A faster variant that
 * wins less never wins.
 */
public final class BenchProtocol {
    private BenchProtocol() {
    }

    /**
     * Compares two rivals. Positive means the left one ranks higher.
     *
     * @param leftWins wins
     * @param rightWins wins
     * @param leftCif CIF median, smaller is better; a dash is worse
     * @param rightCif CIF median
     * @param leftDamage damage taken
     * @param rightDamage damage taken
     * @param leftUsd spend
     * @param rightUsd spend
     * @return the comparison
     */
    public static int compare(int leftWins, int rightWins, int leftCif, int rightCif, double leftDamage,
                              double rightDamage, double leftUsd, double rightUsd) {
        if (leftWins != rightWins) {
            return Integer.compare(leftWins, rightWins);
        }
        if (leftCif != rightCif) {
            return Integer.compare(rightCif, leftCif);
        }
        if (leftDamage != rightDamage) {
            return leftDamage < rightDamage ? 1 : -1;
        }
        return leftUsd == rightUsd ? 0 : leftUsd < rightUsd ? 1 : -1;
    }
}
