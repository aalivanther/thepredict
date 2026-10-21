package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerCacheEvent;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.TacticId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The {@link DecisionTap} the composition root hands to the engine (M18.4, M21.1). It forwards to two destinations:
 * the records tap, fixed at startup, and the duel tap, attached while a duel records. Each destination has its own
 * failure path. A records failure clears only the records tap. A duel failure detaches only the duel, as in M18.
 * {@link #detail()} is {@link TapDetail#FULL} while a duel is attached, and otherwise the records tap's detail.
 */
public final class SwitchableDecisionTap implements DecisionTap {
    private volatile DecisionTap records = DecisionTap.NONE;
    private volatile DecisionTap target = DecisionTap.NONE;
    private volatile Runnable onFailure = () -> {
    };
    private volatile Consumer<RuntimeException> recordsFailure = exception -> {
    };

    /** Creates a tap with nothing attached. */
    public SwitchableDecisionTap() {
    }

    /**
     * Fixes the records destination.
     *
     * @param recording the records tap
     * @param failure called once when that tap throws; the duel stays attached
     */
    public void fixRecords(DecisionTap recording, Consumer<RuntimeException> failure) {
        recordsFailure = Objects.requireNonNull(failure, "failure");
        records = Objects.requireNonNull(recording, "recording");
    }

    /**
     * Forwards every call to a duel recording from now on.
     *
     * @param recording the tap of the recording
     * @param failure called once, after the recording was detached, when one of its calls threw
     */
    public void attach(DecisionTap recording, Runnable failure) {
        onFailure = Objects.requireNonNull(failure, "failure");
        target = Objects.requireNonNull(recording, "recording");
    }

    /** Stops forwarding to the duel. The records tap stays. */
    public void detach() {
        target = DecisionTap.NONE;
    }

    @Override
    public TapDetail detail() {
        var duel = target == DecisionTap.NONE ? TapDetail.NONE : TapDetail.FULL;
        return TapDetail.max(records.detail(), duel);
    }

    @Override
    public void explored(MobKey mob, akyrion.thepredict.domain.BehaviorPolicy policy, int chosen) {
        hear(tap -> tap.explored(mob, policy, chosen));
    }

    @Override
    public void credited(MobKey mob, String windowId, double reward) {
        hear(tap -> tap.credited(mob, windowId, reward));
    }

    @Override
    public void learnEvent(String sha256, String event, akyrion.thepredict.domain.learn.LearnCause cause) {
        hear(tap -> tap.learnEvent(sha256, event, cause));
    }

    @Override
    public void noteAnnotation(String voi, String jevHint) {
        hear(tap -> tap.noteAnnotation(voi, jevHint));
    }

    @Override
    public void noteDistill(String distill, String patternFacts) {
        hear(tap -> tap.noteDistill(distill, patternFacts));
    }

    @Override
    public void regimeChanged(MobKey mob, String from, String to, String cause, int dwellTicks, String blockedBy) {
        hear(tap -> tap.regimeChanged(mob, from, to, cause, dwellTicks, blockedBy));
    }

    @Override
    public void distillEvent(String kind, String event, String detail) {
        hear(tap -> tap.distillEvent(kind, event, detail));
    }

    @Override
    public void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                 Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
        hear(tap -> tap.questionsPlanned(encounter, sequence, planned, preparation, dispatch, facts));
    }

    @Override
    public void requestBody(EncounterId encounter, long sequence, String body) {
        hear(tap -> tap.requestBody(encounter, sequence, body));
    }

    @Override
    public void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
        hear(tap -> tap.resultReceived(encounter, sequence, result, roundTripNanos));
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
        hear(tap -> tap.answerDiscarded(encounter, sequence, reason));
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                DiscardReason reason) {
        hear(tap -> tap.answerDiscarded(encounter, sequence, question, mob, reason));
    }

    @Override
    public void intentEvent(MobKey mob, IntentEvent event) {
        hear(tap -> tap.intentEvent(mob, event));
    }

    @Override
    public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
        castAttempted(mob, tactic, choice, outcome, false);
    }

    @Override
    public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome,
                              boolean readyCast) {
        hear(tap -> tap.castAttempted(mob, tactic, choice, outcome, readyCast));
    }

    @Override
    public void optionsPruned(EncounterId encounter, long sequence, MobKey mob,
                              akyrion.thepredict.domain.OptionPruning.PruneResult pruning) {
        hear(tap -> tap.optionsPruned(encounter, sequence, mob, pruning));
    }

    @Override
    public void reactionFired(MobKey mob, Reaction reaction) {
        hear(tap -> tap.reactionFired(mob, reaction));
    }

    @Override
    public void microChanged(MobKey mob, MicroBehaviour from, MicroBehaviour to) {
        hear(tap -> tap.microChanged(mob, from, to));
    }

    @Override
    public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
        hear(tap -> tap.threatChanged(mob, participant, amount, cause));
    }

    @Override
    public void stanceChanged(MobKey mob, akyrion.thepredict.domain.StanceChange change) {
        hear(tap -> tap.stanceChanged(mob, change));
    }

    @Override
    public void telegraphClosed(MobKey mob, akyrion.thepredict.domain.TelegraphSummary summary) {
        hear(tap -> tap.telegraphClosed(mob, summary));
    }

    @Override
    public void zoneBuilt(EncounterId encounter, int epoch) {
        hear(tap -> tap.zoneBuilt(encounter, epoch));
    }

    @Override
    public void hazardEvent(MobKey mob, akyrion.thepredict.domain.HazardRecord record) {
        hear(tap -> tap.hazardEvent(mob, record));
    }

    @Override
    public void zoneGuard(MobKey mob, akyrion.thepredict.domain.ZoneGuardKind kind) {
        hear(tap -> tap.zoneGuard(mob, kind));
    }

    @Override
    public void zoneGuard(MobKey mob, akyrion.thepredict.domain.ZoneGuardKind kind,
                          akyrion.thepredict.domain.ChoiceId choice) {
        hear(tap -> tap.zoneGuard(mob, kind, choice));
    }

    @Override
    public void zoneFactChanged(MobKey mob, akyrion.thepredict.domain.ZoneFact fact, boolean active) {
        hear(tap -> tap.zoneFactChanged(mob, fact, active));
    }

    @Override
    public void fightEvent(MobKey mob, akyrion.thepredict.domain.FightEventKind event, long elapsedTicks, int rung,
                           String mode, int targetSeconds, FightFacts facts) {
        hear(tap -> tap.fightEvent(mob, event, elapsedTicks, rung, mode, targetSeconds, facts));
    }

    @Override
    public void pressureChanged(MobKey mob, int from, int to, akyrion.thepredict.domain.PressureCause cause,
                                double projectedSeconds, int targetSeconds, int deferredTicks) {
        hear(tap -> tap.pressureChanged(mob, from, to, cause, projectedSeconds, targetSeconds, deferredTicks));
    }

    @Override
    public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
        hear(tap -> tap.outcomeClosed(mob, summary));
    }

    @Override
    public void encounterClosed(EncounterId encounter, EncounterClosed closed) {
        hear(tap -> tap.encounterClosed(encounter, closed));
    }

    @Override
    public void patternChanged(EncounterId encounter, UUID participant,
                               akyrion.thepredict.domain.PatternChange change) {
        hear(tap -> tap.patternChanged(encounter, participant, change));
    }

    @Override
    public void patternExploited(MobKey mob, akyrion.thepredict.domain.PatternExploit exploit) {
        hear(tap -> tap.patternExploited(mob, exploit));
    }

    @Override
    public void patternExploited(MobKey mob, UUID participant, akyrion.thepredict.domain.PatternExploit exploit,
                                 String choice, String tactic, int quotaUsed) {
        hear(tap -> tap.patternExploited(mob, participant, exploit, choice, tactic, quotaUsed));
    }

    @Override
    public void patternDiag(UUID participant, String weaponClass, int chargeMillis, boolean leftClickAir,
                            String blockedBy, boolean wouldToken) {
        hear(tap -> tap.patternDiag(participant, weaponClass, chargeMillis, leftClickAir, blockedBy, wouldToken));
    }

    @Override
    public void patternSummary(EncounterId encounter, UUID participant,
                               akyrion.thepredict.domain.EnemyProfileSummary summary) {
        hear(tap -> tap.patternSummary(encounter, participant, summary));
    }

    @Override
    public void answerCache(EncounterId encounter, long sequence, AnswerCacheEvent event) {
        hear(tap -> tap.answerCache(encounter, sequence, event));
    }

    @Override
    public void memoryAdmitted(EncounterId encounter, UUID subject,
                               akyrion.thepredict.domain.MemoryAdmission admission) {
        hear(tap -> tap.memoryAdmitted(encounter, subject, admission));
    }

    @Override
    public void memoryApplied(UUID subject, akyrion.thepredict.domain.MemoryUpdate update) {
        hear(tap -> tap.memoryApplied(subject, update));
    }

    private void hear(Consumer<DecisionTap> call) {
        var recordsNow = records;
        if (recordsNow != DecisionTap.NONE) {
            try {
                call.accept(recordsNow);
            } catch (RuntimeException exception) {
                if (records == recordsNow) {
                    records = DecisionTap.NONE;
                    try {
                        recordsFailure.accept(exception);
                    } catch (RuntimeException ignored) {
                        recordsFailure = exceptionAgain -> {
                        };
                    }
                }
            }
        }
        var duel = target;
        if (duel != DecisionTap.NONE) {
            try {
                call.accept(duel);
            } catch (RuntimeException exception) {
                failed(duel);
            }
        }
    }

    /** Detaches a duel whose call threw and tells it once. */
    private void failed(DecisionTap failing) {
        if (target != failing) {
            return;
        }
        detach();
        try {
            onFailure.run();
        } catch (RuntimeException ignored) {
            onFailure = () -> {
            };
        }
    }
}
