package akyrion.thepredict.admin;

import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DebugIntents;
import akyrion.thepredict.engine.SessionReport;
import akyrion.thepredict.engine.StatusReport;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What {@link ThePredictCommand} reads and changes in the engine. The composition root adapts the coordinator to it;
 * when the coordinator did not start every read is empty and every change is refused. Server thread only (rule T1).
 */
public interface CommandServices {
    /** How a request for a service check was received. */
    enum CheckStart {
        /** A new check starts on the next tick; its result arrives through the callback. */
        STARTED,
        /** Too many operators already wait for a check; nothing was started. */
        BUSY,
        /** The coordinator did not start; nothing was started. */
        UNAVAILABLE
    }

    /**
     * Reports the engine for the console.
     *
     * @return the report, empty when the coordinator did not start
     */
    Optional<StatusReport> status();

    /**
     * Reports the engine with the budget of the world of a player.
     *
     * @param worldId world of the player
     * @return the report, empty when the coordinator did not start
     */
    Optional<StatusReport> status(UUID worldId);

    /**
     * Reports one session.
     *
     * @param entityId UUID of the mob
     * @return the report, empty when the mob has no session
     */
    Optional<SessionReport> session(UUID entityId);

    /**
     * Packed tick signals for {@code inspect <mob> tick} (M20.2, errata E20-5). Empty when the coordinator did not
     * start or the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the last packed word
     */
    default OptionalLong tickSignals(UUID entityId) {
        return OptionalLong.empty();
    }

    /**
     * Two-line difficulty block of one session (M22.11). Empty when the coordinator did not start or the mob has no
     * session.
     *
     * @param entityId UUID of the mob
     * @return the block
     */
    default Optional<String> difficultyView(UUID entityId) {
        return Optional.empty();
    }

    /**
     * Fairness warnings for {@code status} (M23.4). Empty when the coordinator did not start or nothing is wrong.
     *
     * @return the lines
     */
    default List<String> fairnessLines() {
        return List.of();
    }

    /**
     * YAML stance block of one session for {@code inspect stance}. Empty when the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the lines
     */
    default List<String> inspectStance(UUID entityId) {
        return List.of();
    }

    /**
     * YAML telegraph block of one session for {@code inspect telegraph}. Empty when the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the lines
     */
    default List<String> inspectTelegraph(UUID entityId) {
        return List.of();
    }

    /**
     * Group block of one session for {@code inspect group}. Empty when the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the lines
     */
    default List<String> inspectGroup(UUID entityId) {
        return List.of();
    }

    /**
     * Pattern block of one session for {@code inspect patterns}. Empty when the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the lines
     */
    default List<String> inspectPatterns(UUID entityId) {
        return List.of();
    }

    /**
     * Forces a debug rung. {@code -1} returns the session to auto. Not stored.
     *
     * @param entityId UUID of the mob
     * @param rung rung 0 to 8, or {@code -1}
     * @return whether the mob has a session
     */
    default boolean forcePressure(UUID entityId, int rung) {
        return false;
    }

    /**
     * Tells whether an entity is a mob with a session.
     *
     * @param entityId UUID of the entity
     * @return {@code true} for a bound mob with a session
     */
    boolean bound(UUID entityId);

    /**
     * Lists the goals of a mob, with or without a session.
     *
     * @param entityId UUID of the mob
     * @return at most 40 goals
     */
    List<GoalSummary> goals(UUID entityId);

    /**
     * Reads the movement progress of a mob, with or without a session.
     *
     * @param entityId UUID of the mob
     * @return the progress
     */
    MovementProgress progress(UUID entityId);

    /**
     * Sets a debug intent for the {@code maximumIntentTicks} of the mob's profile.
     *
     * @param entityId UUID of the mob
     * @param tactic tactic of its profile
     * @return the applied intent or why it was refused
     */
    DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic);

    /**
     * Sets a debug intent for the given ticks.
     *
     * @param entityId UUID of the mob
     * @param tactic tactic of its profile
     * @param durationTicks validity, from 1 to {@link DebugIntents#MAX_TICKS}
     * @return the applied intent or why it was refused
     */
    DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic, int durationTicks);

    /**
     * Drops the intent of a mob to level 4.
     *
     * @param entityId UUID of the mob
     * @return {@code false} when the mob has no active session
     */
    boolean release(UUID entityId);

    /**
     * Starts a new check of the service without waiting for it (rule T4).
     *
     * @param result called once on the server thread with the check as the client reported it
     * @return whether the check started
     */
    CheckStart requestCheck(Consumer<ServiceCheck> result);
}
