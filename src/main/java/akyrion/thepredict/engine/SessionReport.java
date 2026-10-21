package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ThreatTableState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only report of one session for {@code /thepredict inspect} (01 §8, M12.2), built by {@link Diagnostics} on the
 * server thread. It explains what the mob does and why: its level with the last fallback reason and cast result, its
 * intent, its goals, its movement and its threat table. No player name or UUID (rule I6).
 *
 * @param key session identity (rule I1)
 * @param mythicMobId MythicMobs type of the mob
 * @param profileId ID of its tactical profile
 * @param state lifecycle state
 * @param level control level, {@code BASE} without a current intent
 * @param encounter encounter of the session, if any
 * @param intent current intent, empty at level 4
 * @param lowConfidenceStreak consecutive low-confidence answers
 * @param noProgressStreak consecutive evaluations without progress
 * @param installPending whether the goal set waits to be installed
 * @param installedRevision catalog revision of the installed goal set, empty when none is installed
 * @param goals installed goals, at most 40, with their running flag
 * @param progress movement progress
 * @param perception what the mob perceived at the last capture: health band, target relation, contexts, eligible
 *                   tactics and cues; empty before its first capture
 * @param targetFacts the facts of its target at that capture, by alias only
 * @param perceivedPlayers participants perceived by the last capture of its encounter: players and, since M17, any
 *                         admitted living entity (the label of {@code inspect} keeps its 0.0.1 wording)
 * @param recentEvents last events of that capture, oldest first, at most {@link #LISTED_EVENTS}
 * @param combatSeconds longest fight of the members at that capture, in whole seconds
 * @param threatTable native threat table, empty when the mob could not be read
 * @param threatAddedInWindowPoints threat ThePredict gave this mob's table in the windows still open (M11)
 * @param lastFallbackReason reason of the last drop of level
 * @param lastOutcome last cast result
 * @param purposes purpose of each choice of the profile, in declaration order; empty when the choice is untyped (M19)
 * @param outcomes last closed outcome windows of the session, oldest first, at most eight (M16.8)
 * @param pruned options the last pruned question dropped, and why; empty when nothing was pruned (M19.6)
 * @param aggregate the {@code outcome_stats} row of the current tactic, empty when none is loaded (M21.9)
 */
public record SessionReport(MobKey key, String mythicMobId, String profileId, SessionState state, ControlLevel level,
                            Optional<EncounterId> encounter, Optional<Intent> intent, int lowConfidenceStreak,
                            int noProgressStreak, boolean installPending, OptionalLong installedRevision,
                            List<GoalSummary> goals, MovementProgress progress, Optional<MobFacts> perception,
                            Optional<ParticipantFacts> targetFacts, int perceivedPlayers,
                            List<EncounterEvent> recentEvents, int combatSeconds,
                            Optional<ThreatTableState> threatTable,
                            double threatAddedInWindowPoints, Optional<FallbackReason> lastFallbackReason,
                            Optional<ExecutionOutcome> lastOutcome, Map<ChoiceId, Optional<SkillPurpose>> purposes,
                            List<OutcomeWindow.Summary> outcomes, Map<TacticId, PruneReason> pruned,
                            Optional<String> aggregate) {
    /** Most events of the last capture kept in a report. */
    public static final int LISTED_EVENTS = 8;

    /**
     * Validates the components and copies the goals and events.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public SessionReport {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mythicMobId, "mythicMobId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(installedRevision, "installedRevision");
        goals = List.copyOf(goals);
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(perception, "perception");
        Objects.requireNonNull(targetFacts, "targetFacts");
        recentEvents = List.copyOf(recentEvents.subList(Math.max(0, recentEvents.size() - LISTED_EVENTS),
                recentEvents.size()));
        Objects.requireNonNull(threatTable, "threatTable");
        Objects.requireNonNull(lastFallbackReason, "lastFallbackReason");
        Objects.requireNonNull(lastOutcome, "lastOutcome");
        purposes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(purposes, "purposes")));
        outcomes = List.copyOf(outcomes);
        pruned = Map.copyOf(Objects.requireNonNull(pruned, "pruned"));
        Objects.requireNonNull(aggregate, "aggregate");
    }

    /**
     * A report from before the aggregate line (rule W5). The line is absent.
     *
     * @param key session key
     * @param mythicMobId MythicMobs mob id
     * @param profileId profile id
     * @param state session state
     * @param level control level
     * @param encounter encounter, if any
     * @param intent current intent, if any
     * @param lowConfidenceStreak consecutive low-confidence answers
     * @param noProgressStreak consecutive windows without progress
     * @param installPending whether a goal install is pending
     * @param installedRevision catalog revision of the installed goals
     * @param goals installed goals
     * @param progress movement progress
     * @param perception last facts of the mob
     * @param targetFacts facts of the target participant
     * @param perceivedPlayers participants of the last capture
     * @param recentEvents last events
     * @param combatSeconds longest fight, in seconds
     * @param threatTable native threat table
     * @param threatAddedInWindowPoints threat added in the open windows
     * @param lastFallbackReason last drop of level
     * @param lastOutcome last cast result
     * @param purposes purpose of each choice
     * @param outcomes last closed windows
     * @param pruned options the last pruned question dropped
     */
    public SessionReport(MobKey key, String mythicMobId, String profileId, SessionState state, ControlLevel level,
                         Optional<EncounterId> encounter, Optional<Intent> intent, int lowConfidenceStreak,
                         int noProgressStreak, boolean installPending, OptionalLong installedRevision,
                         List<GoalSummary> goals, MovementProgress progress, Optional<MobFacts> perception,
                         Optional<ParticipantFacts> targetFacts, int perceivedPlayers,
                         List<EncounterEvent> recentEvents, int combatSeconds,
                         Optional<ThreatTableState> threatTable, double threatAddedInWindowPoints,
                         Optional<FallbackReason> lastFallbackReason, Optional<ExecutionOutcome> lastOutcome,
                         Map<ChoiceId, Optional<SkillPurpose>> purposes, List<OutcomeWindow.Summary> outcomes,
                         Map<TacticId, PruneReason> pruned) {
        this(key, mythicMobId, profileId, state, level, encounter, intent, lowConfidenceStreak, noProgressStreak,
                installPending, installedRevision, goals, progress, perception, targetFacts, perceivedPlayers,
                recentEvents, combatSeconds, threatTable, threatAddedInWindowPoints, lastFallbackReason, lastOutcome,
                purposes, outcomes, pruned, Optional.empty());
    }

    /**
     * A report from before pruning was shown on {@code inspect} (rule W5). The dropped options are empty.
     *
     * @param key session key
     * @param mythicMobId MythicMobs mob id
     * @param profileId profile id
     * @param state session state
     * @param level control level
     * @param encounter encounter, if any
     * @param intent current intent, if any
     * @param lowConfidenceStreak consecutive low-confidence answers
     * @param noProgressStreak consecutive windows without progress
     * @param installPending whether a goal install is pending
     * @param installedRevision catalog revision of the installed goals
     * @param goals installed goals
     * @param progress movement progress
     * @param perception last facts of the mob
     * @param targetFacts facts of the target participant
     * @param perceivedPlayers participants of the last capture
     * @param recentEvents last events
     * @param combatSeconds longest fight, in seconds
     * @param threatTable native threat table
     * @param threatAddedInWindowPoints threat added in the open windows
     * @param lastFallbackReason last drop of level
     * @param lastOutcome last cast result
     * @param purposes purpose of each choice
     * @param outcomes last closed windows
     */
    public SessionReport(MobKey key, String mythicMobId, String profileId, SessionState state, ControlLevel level,
                         Optional<EncounterId> encounter, Optional<Intent> intent, int lowConfidenceStreak,
                         int noProgressStreak, boolean installPending, OptionalLong installedRevision,
                         List<GoalSummary> goals, MovementProgress progress, Optional<MobFacts> perception,
                         Optional<ParticipantFacts> targetFacts, int perceivedPlayers,
                         List<EncounterEvent> recentEvents, int combatSeconds,
                         Optional<ThreatTableState> threatTable, double threatAddedInWindowPoints,
                         Optional<FallbackReason> lastFallbackReason, Optional<ExecutionOutcome> lastOutcome,
                         Map<ChoiceId, Optional<SkillPurpose>> purposes, List<OutcomeWindow.Summary> outcomes) {
        this(key, mythicMobId, profileId, state, level, encounter, intent, lowConfidenceStreak, noProgressStreak,
                installPending, installedRevision, goals, progress, perception, targetFacts, perceivedPlayers,
                recentEvents, combatSeconds, threatTable, threatAddedInWindowPoints, lastFallbackReason, lastOutcome,
                purposes, outcomes, Map.of(), Optional.empty());
    }

    /**
     * Returns this report with the aggregate line of the current tactic.
     *
     * @param line the line
     * @return a copy that differs only in {@link #aggregate()}
     */
    public SessionReport withAggregate(String line) {
        return new SessionReport(key, mythicMobId, profileId, state, level, encounter, intent, lowConfidenceStreak,
                noProgressStreak, installPending, installedRevision, goals, progress, perception, targetFacts,
                perceivedPlayers, recentEvents, combatSeconds, threatTable, threatAddedInWindowPoints,
                lastFallbackReason, lastOutcome, purposes, outcomes, pruned, Optional.of(line));
    }

    /**
     * The current intent of the session.
     *
     * @param tactic current tactic
     * @param movement movement of the tactic
     * @param choice choice of the tactic, if any
     * @param confidence confidence of the answer that produced it; {@code 0} for a local intent
     * @param remainingTicks ticks until it expires, {@code 0} once expired
     * @param skillConsumed whether its choice was already tried
     * @param alternatives alternatives of the last answer, best first
     * @param contingencies contingencies of the last answer
     * @param debug whether {@code /thepredict debug intent} set it
     */
    public record Intent(TacticId tactic, Movement movement, Optional<ChoiceId> choice, double confidence,
                         long remainingTicks, boolean skillConsumed, List<TacticId> alternatives,
                         Map<Situation, TacticId> contingencies, boolean debug) {
        /**
         * Validates the components and copies the collections.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Intent {
            Objects.requireNonNull(tactic, "tactic");
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(choice, "choice");
            alternatives = List.copyOf(alternatives);
            contingencies = StatusReport.ordered(contingencies);
        }
    }
}
