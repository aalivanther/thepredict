package akyrion.thepredict.engine;

/**
 * Sends a waiting stance line and the tell results that closed (M23.10). Nothing is built when the tap is off.
 */
final class PostureRecords {
    private PostureRecords() {
    }

    /**
     * Flushes one session.
     *
     * @param session the mob
     * @param tick server tick
     * @param tap records tap
     */
    static void flush(MobSession session, long tick, DecisionTap tap) {
        var ring = session.tellResults();
        if (tap.detail() == TapDetail.FULL && ring.stancePending()) {
            tap.stanceChanged(session.key(), ring.takeStance());
        } else if (ring.stancePending()) {
            ring.dropStance();
        }
        if (tap.detail() == TapDetail.NONE) {
            ring.discardDue(tick);
            return;
        }
        var closed = ring.drain(tick);
        for (var index = 0; index < closed; index++) {
            tap.telegraphClosed(session.key(), ring.drained(index));
        }
    }
}
