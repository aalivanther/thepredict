package akyrion.thepredict.domain;

/**
 * The fixed English phrase of a pattern sent to Jev (M26.7). {@link EnemyPattern#FIXED_CADENCE} has none.
 */
public final class EnemyPatternFact {
    private static final String[] PHRASES = phrases();

    private EnemyPatternFact() {
    }

    /**
     * The phrase, or empty for cadence.
     *
     * @param pattern the pattern
     * @return the text
     */
    public static String phrase(EnemyPattern pattern) {
        return pattern == EnemyPattern.FIXED_CADENCE ? "" : PHRASES[pattern.ordinal()];
    }

    private static String[] phrases() {
        var text = new String[EnemyPattern.values().length];
        text[0] = "swings before the attack recharges";
        text[1] = "jumps in for critical hits";
        text[2] = "resets sprint between hits";
        text[3] = "strafes left on a readable rhythm";
        text[4] = "strafes right on a readable rhythm";
        text[5] = "chases a step back";
        text[6] = "keeps the shield up";
        text[7] = "raises the shield late";
        text[8] = "strikes just after a block";
        text[9] = "fights without a shield";
        text[10] = "fails to block a telegraph";
        text[11] = "fails to dodge a telegraph";
        text[12] = "fails to back off a telegraph";
        text[13] = "fails to break sight of a telegraph";
        text[14] = "fails to interrupt a telegraph";
        text[15] = "is hit by projectiles";
        text[16] = "is caught by area attacks";
        text[17] = "heals when low";
        text[18] = "throws a pearl when low";
        text[19] = "retreats when low";
        text[20] = "relies on a totem";
        text[21] = "kites at range";
        text[22] = "seeks high ground";
        text[23] = "dives with a mace";
        text[24] = "";
        return text;
    }
}
