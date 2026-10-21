package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerCacheEvent;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.TacticId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the engine tells a listener about each decision and action, for the duel recorder of M18.4 (00 §4, amendment of
 * M18). The engine and the coordinator call it; {@code adapter.server.duel.DuelRecorder} listens. Every method runs on
 * the server thread (rule T1), including {@link #requestBody}, because since M16.1 the body is prepared there. Every
 * method has an empty default, so {@link #NONE} costs nothing; the composition root wires a
 * {@link SwitchableDecisionTap} that forwards only while a recording runs.
 *
 * <p>Join key: every call about a request carries its encounter and the sequence taken before {@code prepare}
 * (M16.1). An {@link IntentEvent} with a question is only sent while the result announced by the last
 * {@link #resultReceived} is being accepted, in the same call of the server thread, so its request is that result's.
 * A listener must never throw; the engine does not guard these calls.
 */
public interface DecisionTap {
    /** A tap that ignores everything; the engine uses it when nothing records. */
    DecisionTap NONE = new DecisionTap() {
    };

    /**
     * Tells whether anything records now. The engine checks it before building an {@link IntentEvent}, so no event is
     * allocated while nothing records.
     *
     * @return {@code false} by default
     */
    default boolean recording() {
        return detail() != TapDetail.NONE;
    }

    /**
     * How much this tap wants built. {@link #recording()} is {@code detail() != NONE}.
     *
     * @return {@link TapDetail#NONE} by default
     */
    default TapDetail detail() {
        return TapDetail.NONE;
    }

    /**
     * An encounter closed (M21.1, errata E21-2). The reason reuses {@link SessionMetrics.EncounterEnd}.
     *
     * @param encounter the encounter
     * @param closed the close
     */
    default void encounterClosed(EncounterId encounter, EncounterClosed closed) {
    }

    /**
     * A cache lookup was decided (M21.7).
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param event what the lookup did
     */
    default void answerCache(EncounterId encounter, long sequence, AnswerCacheEvent event) {
    }

    /**
     * Stores a shadow annotation for the next planned line. Empty strings add no field.
     *
     * @param voi shadow value-of-information note
     * @param jevHint shadow hint that is never sent to Jev
     */
    default void noteAnnotation(String voi, String jevHint) {
    }

    /**
     * The dispatch of a request was decided: after {@code prepare} and the reservation attempt, before {@code decide}.
     *
     * @param encounter encounter of the request
     * @param sequence sequence taken before {@code prepare}, also when nothing is sent
     * @param planned questions the engine planned, before the deduplication of M16.1
     * @param preparation what {@code prepare} returned
     * @param dispatch {@code SENT}, {@code NO_BUDGET}, {@code NOTHING_TO_ASK} or {@code PREPARE_FAILED}
     * @param facts the capture of the request
     */
    default void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                  Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
    }

    /**
     * A prepared body was handed to {@code decide}.
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param body the exact body sent, which only holds aliases (rule I6)
     */
    default void requestBody(EncounterId encounter, long sequence, String body) {
    }

    /**
     * A result reached the intake: an answer, a failure or the synthetic {@code TIMEOUT} of an expired request. Called
     * right before the result is handed to the acceptance.
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param result the result
     * @param roundTripNanos time from the send to the completion or to the expiry
     */
    default void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
    }

    /**
     * A whole result was discarded by the acceptance (M10.1).
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param reason why
     */
    default void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
    }

    /**
     * The answer about one mob was discarded by the identity check of the acceptance (M10.1).
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param question the {@code NOW} question of the mob
     * @param mob the mob, with the generation of the request
     * @param reason why
     */
    default void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                 DiscardReason reason) {
    }

    /**
     * An intent was adopted, renewed, rejected, stored as contingency, fired, planned locally or changed level.
     *
     * @param mob the session
     * @param event what happened
     */
    default void intentEvent(MobKey mob, IntentEvent event) {
    }

    /**
     * The choice of an intent was tried, once per outcome and intent (the count of M10.3).
     *
     * @param mob the session
     * @param tactic tactic of the intent
     * @param choice choice tried
     * @param outcome what the execution answered
     */
    default void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
    }

    /**
     * A cast was tried. {@code readyCast} is true when the choice was not the one of the intent (M19.5). The default
     * delegates to the four-argument form, so a listener from before M19 still hears it (W5).
     *
     * @param mob the session
     * @param tactic tactic of the intent
     * @param choice choice tried
     * @param outcome what the execution answered
     * @param readyCast whether the choice was a candidate of {@code ANY_READY} rather than the intent's own
     */
    default void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome,
                               boolean readyCast) {
        castAttempted(mob, tactic, choice, outcome);
    }

    /**
     * Options a question dropped, after {@link #questionsPlanned}. The duel recorder of M21 reads it; the default
     * ignores it (M19.6).
     *
     * @param encounter the encounter
     * @param sequence sequence of the request, {@code 0} when pruning left nothing to ask
     * @param mob the mob
     * @param pruning what was kept and why the rest left
     */
    default void optionsPruned(EncounterId encounter, long sequence, MobKey mob,
                               akyrion.thepredict.domain.OptionPruning.PruneResult pruning) {
    }

    /**
     * A reaction armed (M20.4). Empty by default.
     *
     * @param mob the session
     * @param reaction the reaction
     */
    default void reactionFired(MobKey mob, Reaction reaction) {
    }

    /**
     * The published micro changed (M20.5). Empty by default.
     *
     * @param mob the session
     * @param from the previous micro
     * @param to the new micro
     */
    default void microChanged(MobKey mob, MicroBehaviour from, MicroBehaviour to) {
    }

    /**
     * An enriched threat change was handed to the port (M11.4).
     *
     * @param mob table of this mob
     * @param participant participant whose threat changed
     * @param amount change, positive or negative
     * @param cause the enumerated cause of the change
     */
    default void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
    }

    /**
     * An outcome window closed (M16.8).
     *
     * @param mob the session
     * @param summary the closed window
     */
    default void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
    }

    /**
     * A fight clock moved (M22.8). The default records nothing.
     *
     * @param mob the session
     * @param event the kind
     * @param elapsedTicks ticks since the fight engaged
     * @param rung rung at the event
     * @param mode play mode name
     * @param targetSeconds T, in seconds
     * @param facts encounter, profile, target and world fields of the fight line
     */
    default void fightEvent(MobKey mob, akyrion.thepredict.domain.FightEventKind event, long elapsedTicks, int rung,
                            String mode, int targetSeconds, FightFacts facts) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(event, "event");
        java.util.Objects.requireNonNull(mode, "mode");
        java.util.Objects.requireNonNull(facts, "facts");
    }

    /**
     * The rung changed (M22.8). The default records nothing.
     *
     * @param mob the session
     * @param from previous rung
     * @param to next rung
     * @param cause why it changed
     * @param projectedSeconds estimated time to kill, or 0
     * @param targetSeconds T
     * @param deferredTicks ticks the step waited because the caster was busy
     */
    default void pressureChanged(MobKey mob, int from, int to, akyrion.thepredict.domain.PressureCause cause,
                                 double projectedSeconds, int targetSeconds, int deferredTicks) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(cause, "cause");
    }

    /**
     * The visible stance changed (M23.10). Recorded at {@link TapDetail#FULL}. The default records nothing.
     *
     * @param mob the session
     * @param change the change
     */
    default void stanceChanged(MobKey mob, akyrion.thepredict.domain.StanceChange change) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(change, "change");
    }

    /**
     * A tell closed (M23.10). Recorded at {@link TapDetail#DECISIONS}. The default records nothing.
     *
     * @param mob the session
     * @param summary the closed tell
     */
    default void telegraphClosed(MobKey mob, akyrion.thepredict.domain.TelegraphSummary summary) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(summary, "summary");
    }

    /**
     * A pattern changed state (M26.9). Recorded at {@link TapDetail#DECISIONS}. The default records nothing.
     *
     * @param encounter the encounter
     * @param participant the enemy
     * @param change the change
     */
    default void patternChanged(EncounterId encounter, java.util.UUID participant,
                                akyrion.thepredict.domain.PatternChange change) {
        java.util.Objects.requireNonNull(encounter, "encounter");
        java.util.Objects.requireNonNull(participant, "participant");
        java.util.Objects.requireNonNull(change, "change");
    }

    /**
     * A motivated cast closed (M26.9). Recorded at {@link TapDetail#FULL}. There is no source field (errata E26-6).
     *
     * @param mob the session
     * @param exploit the exploitation
     */
    default void patternExploited(MobKey mob, akyrion.thepredict.domain.PatternExploit exploit) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(exploit, "exploit");
    }

    /**
     * A motivated cast closed (M26.9). The participant is the enemy, not the mob. There is no source field
     * (errata E26-6).
     *
     * @param mob the session
     * @param participant the enemy
     * @param exploit the exploitation
     * @param choice the skill choice
     * @param tactic the tactic
     * @param quotaUsed motivated casts in the window
     */
    default void patternExploited(MobKey mob, java.util.UUID participant,
                                  akyrion.thepredict.domain.PatternExploit exploit, String choice, String tactic,
                                  int quotaUsed) {
        java.util.Objects.requireNonNull(participant, "participant");
        java.util.Objects.requireNonNull(choice, "choice");
        java.util.Objects.requireNonNull(tactic, "tactic");
        patternExploited(mob, exploit);
    }

    /**
     * An air swing kept as a diagnosis (M26.9). Recorded at {@link TapDetail#FULL}. The default records nothing.
     *
     * @param participant the enemy
     * @param weaponClass weapon in the main hand, or empty
     * @param chargeMillis attack charge
     * @param leftClickAir whether the click was in the air
     * @param blockedBy the event that blocked a token, or empty
     * @param wouldToken whether {@code airSwings: on} would have emitted a token
     */
    default void patternDiag(java.util.UUID participant, String weaponClass, int chargeMillis, boolean leftClickAir,
                             String blockedBy, boolean wouldToken) {
        java.util.Objects.requireNonNull(participant, "participant");
        java.util.Objects.requireNonNull(weaponClass, "weaponClass");
        java.util.Objects.requireNonNull(blockedBy, "blockedBy");
    }

    /**
     * A profile summary was written at close (M26.9). Recorded at {@link TapDetail#DECISIONS}.
     *
     * @param encounter the encounter
     * @param participant the enemy
     * @param summary the summary
     */
    default void patternSummary(EncounterId encounter, java.util.UUID participant,
                                akyrion.thepredict.domain.EnemyProfileSummary summary) {
        java.util.Objects.requireNonNull(encounter, "encounter");
        java.util.Objects.requireNonNull(participant, "participant");
        java.util.Objects.requireNonNull(summary, "summary");
    }

    /**
     * A zone build was published (M24.10). The default records nothing.
     *
     * @param encounter the encounter
     * @param epoch the epoch
     */
    default void zoneBuilt(EncounterId encounter, int epoch) {
        java.util.Objects.requireNonNull(encounter, "encounter");
    }

    /**
     * A hazard was attributed (M24.10). The default records nothing.
     *
     * @param mob the session
     * @param record the hazard
     */
    default void hazardEvent(MobKey mob, akyrion.thepredict.domain.HazardRecord record) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(record, "record");
    }

    /**
     * A zone guard changed (M24.10). The default records nothing.
     *
     * @param mob the session
     * @param kind the guard
     */
    default void zoneGuard(MobKey mob, akyrion.thepredict.domain.ZoneGuardKind kind) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(kind, "kind");
    }

    /**
     * A zone guard tied to one choice (M24.10). The default records nothing.
     *
     * @param mob the session
     * @param kind the guard
     * @param choice the choice that was deferred, skipped or blocked
     */
    default void zoneGuard(MobKey mob, akyrion.thepredict.domain.ZoneGuardKind kind,
                           akyrion.thepredict.domain.ChoiceId choice) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(choice, "choice");
    }

    /**
     * A filtered zone fact changed (M24.10). The default records nothing.
     *
     * @param mob the session
     * @param fact the fact
     * @param active whether it is now on
     */
    default void zoneFactChanged(MobKey mob, akyrion.thepredict.domain.ZoneFact fact, boolean active) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(fact, "fact");
    }

    /**
     * A player's memory was admitted (M27). Recorded at {@link TapDetail#DECISIONS}. The default records nothing.
     *
     * @param encounter the encounter
     * @param subject the player
     * @param admission what the fight may use
     */
    default void memoryAdmitted(EncounterId encounter, UUID subject,
                                akyrion.thepredict.domain.MemoryAdmission admission) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(admission, "admission");
    }

    /**
     * A player's memory was written (M27). Recorded at {@link TapDetail#FULL}. The default records nothing.
     *
     * @param subject the player
     * @param update what was written
     */
    default void memoryApplied(UUID subject, akyrion.thepredict.domain.MemoryUpdate update) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(update, "update");
    }

    /**
     * A window received credit (M28). The default records nothing.
     *
     * @param mob the mob
     * @param windowId window id
     * @param reward reward r1
     */
    default void credited(MobKey mob, String windowId, double reward) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(windowId, "windowId");
    }

    /**
     * An exploratory adoption was recorded (M28). The default records nothing.
     *
     * @param mob the mob
     * @param policy the behavior
     * @param chosen chosen index
     */
    default void explored(MobKey mob, akyrion.thepredict.domain.BehaviorPolicy policy, int chosen) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(policy, "policy");
    }

    /**
     * A learned artifact changed (M28). The default records nothing.
     *
     * @param sha256 artifact fingerprint
     * @param event event name
     * @param cause cause
     */
    default void learnEvent(String sha256, String event, akyrion.thepredict.domain.learn.LearnCause cause) {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(cause, "cause");
    }

    /**
     * Shadow fields of the next planned line (M29.1). Empty strings add no field. {@code delegated} is false
     * in shadow. Pattern facts are present only when {@code distill.collect} is true.
     *
     * @param distill JSON object token, or empty
     * @param patternFacts fact count token, or empty
     */
    default void noteDistill(String distill, String patternFacts) {
    }

    /**
     * A regime changed (M29.5). The default records nothing.
     *
     * @param mob the session
     * @param from previous key
     * @param to next key
     * @param cause why it changed
     * @param dwellTicks ticks the candidate waited
     * @param blockedBy {@code TELEGRAPH}, {@code PLAN_HOLD} or empty
     */
    default void regimeChanged(MobKey mob, String from, String to, String cause, int dwellTicks, String blockedBy) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(blockedBy, "blockedBy");
    }

    /**
     * A distill lifecycle event (M29.6). The default records nothing.
     *
     * @param kind {@code DISTILLED}, {@code REGIME} or {@code OVERLAY}
     * @param event {@code MODE_CHANGED}, {@code DEMOTED}, {@code KILLED}, {@code OVERLAY_APPLIED} or
     * {@code OVERLAY_REVERTED}
     * @param detail short detail, with no player name
     */
    default void distillEvent(String kind, String event, String detail) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(detail, "detail");
    }

    /** What happened to an intent. */
    enum IntentEventKind {
        /**
         * An answer was adopted as a new intent (with its question), or an alternative changed the tactic without
         * changing the level (without a question).
         */
        ADOPTED,
        /** An answer renewed the current intent. */
        RENEWED,
        /** An answer was rejected by the intent policy, with its fallback reason. */
        REJECTED,
        /** A contingency answer was stored with an adopted or renewed intent. */
        CONTINGENCY_STORED,
        /** A stored contingency fired (M10.4). */
        CONTINGENCY_FIRED,
        /** The fallback chain planned a local intent (level 3), with the reason of the drop if there was one. */
        LOCAL_PLANNED,
        /** The level changed without an answer: an alternative, a drop to level 4 or a release. */
        LEVEL_CHANGED
    }

    /**
     * One intent event.
     *
     * @param kind what happened
     * @param tactic tactic of the intent, or the best tactic of a rejected answer
     * @param movement movement of that tactic
     * @param confidence confidence of the answer, 0 for a local intent
     * @param from level before
     * @param to level after
     * @param question the question of the answer, present only while its result is accepted
     * @param reason fallback reason of a rejection or a drop
     */
    record IntentEvent(IntentEventKind kind, TacticId tactic, Movement movement, double confidence, ControlLevel from,
                       ControlLevel to, Optional<QuestionKey> question, Optional<FallbackReason> reason) {
        /**
         * Validates the event.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public IntentEvent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(tactic, "tactic");
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
