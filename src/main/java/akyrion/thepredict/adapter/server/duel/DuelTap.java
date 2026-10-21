package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.Preparation;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * What a duel recording writes from the {@link DecisionTap} of the engine (M18.4): the typed lines of
 * {@code decisions.jsonl}, {@code intents.csv}, {@code threat.csv}, {@code outcomes.csv} and the ThePredict rows of
 * {@code casts.csv}. A request is written only when a mob of its capture is relevant to the recording; its encounter is
 * then remembered (at most {@value #MAX_ENCOUNTERS}, the oldest forgotten first) so that its body, result and
 * acceptance lines follow. An intent event with a question takes the join key of the result being accepted, which
 * {@link #result} announced in the same call of the server thread. Package-private; server thread only.
 */
final class DuelTap {
    /** Encounters remembered as relevant. */
    static final int MAX_ENCOUNTERS = 4096;
    private static final Set<DecisionTap.IntentEventKind> MOVES = EnumSet.of(DecisionTap.IntentEventKind.ADOPTED,
            DecisionTap.IntentEventKind.RENEWED, DecisionTap.IntentEventKind.CONTINGENCY_FIRED,
            DecisionTap.IntentEventKind.LOCAL_PLANNED, DecisionTap.IntentEventKind.LEVEL_CHANGED);

    private final DuelSession session;
    private final DuelCombat combat;
    private final LinkedHashSet<Long> encounters = new LinkedHashSet<>();
    private Optional<long[]> accepting = Optional.empty();

    DuelTap(DuelSession session, DuelCombat combat) {
        this.session = Objects.requireNonNull(session, "session");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    void planned(EncounterId encounter, long sequence, List<QuestionSpec> planned, Preparation preparation,
                 DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
        var mobs = facts.mobs().stream().map(MobFacts::mob).map(MobKey::entityId).toList();
        if (mobs.stream().noneMatch(session::relevant)) {
            return;
        }
        remember(encounter.value());
        var details = new TreeSet<String>();
        planned.forEach(question -> session.scene().profile(question.mob().entityId())
                .ifPresent(profile -> details.add(profile.timing().questionDetail().name())));
        var refs = mobs.stream().map(id -> new DuelDecisionLines.MobRef(id, session.sideOf(id)
                .map(DuelRounds.Side::text).orElse(""), session.kindOf(id).text())).toList();
        session.row(DuelFile.DECISIONS, DuelDecisionLines.planned(encounter.value(), sequence, session.stamp(),
                String.join(",", details), refs, planned, preparation, dispatch));
        if (dispatch == DecisionDispatcher.Outcome.SENT) {
            session.rounds().request();
            session.summary().request(session.rounds().arm());
        }
    }

    void body(EncounterId encounter, long sequence, String body) {
        if (encounters.contains(encounter.value())) {
            session.row(DuelFile.DECISIONS, DuelDecisionLines.body(encounter.value(), sequence, session.stamp(), body));
        }
    }

    void result(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
        if (!encounters.contains(encounter.value())) {
            accepting = Optional.empty();
            return;
        }
        accepting = Optional.of(new long[] {encounter.value(), sequence});
        session.row(DuelFile.DECISIONS, DuelDecisionLines.result(encounter.value(), sequence, session.stamp(), result,
                roundTripNanos));
        if (result instanceof DecisionResult.Answered answered) {
            session.rounds().tokens(answered.inputTokens());
            session.summary().tokens(session.rounds().arm(), answered.inputTokens());
        }
    }

    void discarded(EncounterId encounter, long sequence, Optional<QuestionKey> question, Optional<UUID> mob,
                   DiscardReason reason) {
        if (encounters.contains(encounter.value())) {
            session.row(DuelFile.DECISIONS, DuelDecisionLines.acceptance(encounter.value(), sequence, session.stamp(),
                    question, mob, "DISCARDED", Optional.empty(), Optional.empty(), Optional.of(reason.name())));
        }
    }

    void intent(MobKey mob, DecisionTap.IntentEvent event) {
        var id = mob.entityId();
        if (!session.relevant(id)) {
            return;
        }
        var stamp = session.stamp();
        var reason = event.reason().map(Enum::name);
        session.row(DuelFile.INTENTS, DuelRows.csv(stamp.tick(), stamp.round(), stamp.arm(), id, event.kind(),
                event.from(), event.to(), event.tactic().value(), event.movement(), event.confidence(),
                event.question().map(QuestionKey::value), reason));
        if (event.question().isPresent() && accepting.isPresent()) {
            session.row(DuelFile.DECISIONS, DuelDecisionLines.acceptance(accepting.get()[0], accepting.get()[1], stamp,
                    event.question(), Optional.of(id), event.kind().name(), Optional.of(event.to().name()),
                    Optional.of(event.tactic().value()), reason));
        }
        if (event.kind() == DecisionTap.IntentEventKind.LOCAL_PLANNED) {
            session.row(DuelFile.DECISIONS, DuelDecisionLines.local(stamp, id, event.tactic().value(),
                    event.to().name(), reason));
        }
        if (event.movement() == Movement.RETREAT && event.to() != ControlLevel.BASE && MOVES.contains(event.kind())) {
            session.sampler().intentRetreat(id, stamp.tick());
        }
    }

    void cast(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
        if (session.relevant(mob.entityId())) {
            combat.attempted(mob, tactic, choice, outcome, session.stamp().tick());
        }
    }

    void threat(MobKey mob, UUID participant, double amount, String cause) {
        if (session.relevant(mob.entityId())) {
            var stamp = session.stamp();
            session.row(DuelFile.THREAT, DuelRows.csv(stamp.tick(), stamp.round(), stamp.arm(), mob.entityId(),
                    participant, amount, cause));
        }
    }

    void outcome(MobKey mob, OutcomeWindow.Summary summary) {
        if (!session.relevant(mob.entityId())) {
            return;
        }
        var stamp = session.stamp();
        session.row(DuelFile.OUTCOMES, DuelRows.csv(stamp.tick(), stamp.round(), stamp.arm(), mob.entityId(),
                summary.level(), summary.tactic().value(), summary.movement(), summary.dealt(), summary.taken(),
                summary.reached(), summary.casts(), summary.hits(), summary.mobDied(), summary.targetDied(),
                summary.effect()));
        session.summary().outcome(stamp.arm(), summary.tactic().value(), summary.effect());
    }

    private void remember(long encounter) {
        encounters.remove(encounter);
        encounters.add(encounter);
        if (encounters.size() > MAX_ENCOUNTERS) {
            encounters.remove(encounters.getFirst());
        }
    }
}
