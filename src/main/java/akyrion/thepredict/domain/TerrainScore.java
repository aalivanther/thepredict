package akyrion.thepredict.domain;

/**
 * The terrain consideration (M24.7). It is 1 without a zone. It never reads a target fact against a player.
 */
public final class TerrainScore {
    private TerrainScore() {
    }

    /**
     * The factor for one tactic.
     *
     * @param movement movement of the tactic
     * @param zoneFacts filtered facts; zero without a zone
     * @param ranged whether the tactic has a ranged choice
     * @param player whether the target is a player
     * @return the factor, 0 to 1
     */
    public static double factor(Movement movement, int zoneFacts, boolean ranged, boolean player) {
        if (zoneFacts == 0) {
            return 1.0;
        }
        var noRetreat = bit(zoneFacts, ZoneFact.NO_SAFE_RETREAT);
        var high = !player && bit(zoneFacts, ZoneFact.TARGET_HIGH_GROUND);
        var ownHigh = bit(zoneFacts, ZoneFact.HIGH_GROUND);
        var cover = bit(zoneFacts, ZoneFact.COVER_NEAR);
        var edge = bit(zoneFacts, ZoneFact.EDGE_BEHIND);
        if (movement == Movement.RETREAT && noRetreat) {
            return 0.0;
        }
        if ((movement == Movement.APPROACH || movement == Movement.FLANK) && !ranged && high) {
            return 0.5;
        }
        if ((movement == Movement.KEEP_DISTANCE || movement == Movement.HOLD) && ranged && (ownHigh || cover)) {
            return 1.0;
        }
        if ((ownHigh || cover) && ranged && movement != Movement.KEEP_DISTANCE && movement != Movement.HOLD) {
            return 0.9;
        }
        if (movement == Movement.HOLD && edge) {
            return 0.6;
        }
        return 1.0;
    }

    private static boolean bit(int facts, ZoneFact fact) {
        return (facts & (1 << fact.ordinal())) != 0;
    }
}
