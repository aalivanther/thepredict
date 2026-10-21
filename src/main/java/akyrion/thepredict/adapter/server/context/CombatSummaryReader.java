package akyrion.thepredict.adapter.server.context;

/**
 * Summary of a fight from the vanilla {@code CombatTracker} (M07.8), instead of a history of our own: its duration in
 * whole seconds. Since {@code CombatEntry} has no time, recent facts come from the event history (M07.5). Pure; any
 * thread.
 *
 * <p>Evidence (javap of the locally patched Paper 26.2 server, not started): {@code CombatTracker.getCombatDuration}
 * returns {@code tickCount - combatStartTime} while in combat and {@code combatEndTime - combatStartTime} after, so
 * it is in ticks; {@code PaperCombatTrackerWrapper} forwards it and {@code isInCombat} unchanged.
 */
public final class CombatSummaryReader {
    /** Server ticks per second at the nominal rate. */
    public static final int TICKS_PER_SECOND = 20;

    private CombatSummaryReader() {
    }

    /**
     * Converts the longest combat duration of the members into whole seconds.
     *
     * @param durationTicks {@code getCombatDuration()} of the member with the longest fight
     * @return whole seconds, {@code 0} for a negative duration
     */
    public static int combatSeconds(int durationTicks) {
        return Math.max(0, durationTicks) / TICKS_PER_SECOND;
    }
}
