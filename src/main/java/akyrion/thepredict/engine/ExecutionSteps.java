package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.SkillChoice;

/**
 * The steps of the coordinator that M10 owns: acceptance of a finished request ({@code DecisionAcceptor}, M10.1 to
 * M10.3, M10.7), the reactive cycle of a probed session ({@code ReactiveLayer}, M10.4 to M10.6) and the reading of
 * movement progress once per evaluation of an encounter (the {@code NO_PROGRESS} rule of M10.4). The coordinator only
 * calls them, in its steps 1, 4 and 6 (M09.1). Implemented by {@link TacticalExecution}; tests use doubles. Server
 * thread only (rule T1); none may block.
 */
public interface ExecutionSteps {
    /**
     * Handles a finished request after the coordinator freed its budget and informed the service tracker. It also
     * receives late results, which M10.1 discards by identity.
     *
     * @param completed request and its answers or failure
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     */
    void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos);

    /**
     * Runs the reactive cycle of one active session whose probe falls on this tick, after the coordinator placed it
     * by its target.
     *
     * @param session active session of the probed mob
     * @param probe cheap reading of this tick
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     */
    void react(MobSession session, MobProbe probe, long tick, long nowNanos);

    /**
     * Runs once per evaluation of an encounter, after its capture and its request (sent or skipped) and before the
     * closing check, so M10 can read {@code MobControl.progress} of each member for {@code NO_PROGRESS} and M11 can
     * apply the unreachable-target decay of the enriched threat.
     *
     * @param encounter encounter evaluated in this tick
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     */
    void evaluated(Encounter encounter, long tick, long nowNanos);

    /**
     * Tries a due ready cast of one session (M19.4). The default does nothing, so a test double that only records the
     * other steps keeps compiling (W5).
     *
     * @param session active session whose ready tick is due
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     */
    default void castReady(MobSession session, long tick, long nowNanos) {
    }

    /**
     * Whether a listed choice is off cooldown and not waiting on the extra cooldown (M20.4). The tick layer asks this
     * before a backstep, so an evade whose escape is ready does not step back. The default is false: a double that
     * only records casts keeps the backstep.
     *
     * @param session session whose profile lists the choice
     * @param choice choice that fits the evade mask
     * @param tick current tick
     * @return whether that choice could be cast now
     */
    default boolean choiceReady(MobSession session, SkillChoice choice, long tick) {
        return false;
    }

    /**
     * Releases a due tell outside {@link #castReady} (M23.4). The default does nothing, so a test double keeps
     * compiling (W5).
     *
     * @param session session whose release tick is due
     * @param tick current server tick
     */
    default void releaseTell(MobSession session, long tick) {
    }

    /**
     * Advances a due action stance (M23.1). The default does nothing.
     *
     * @param session session whose stance deadline is due
     * @param tick current server tick
     */
    default void advanceStance(MobSession session, long tick) {
    }
}
