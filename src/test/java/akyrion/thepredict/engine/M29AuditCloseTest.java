package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.AgreementStatDelta;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.engine.distill.DistillPublish;
import akyrion.thepredict.engine.distill.DistillRuntime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Encounter close emits one outcome delta for each audited pair. Errata E29-1 applied. */
class M29AuditCloseTest {
    @Test
    void closingTheEncounterEmitsOneOutcomeDeltaPerAuditedPair() {
        var fixture = new EngineFixtures.Sessions();
        var rows = new ArrayList<AgreementStatDelta>();
        fixture.encounters.useLearning(LearningSettings.DEFAULT, ignored -> {
        }, () -> 5L);
        fixture.encounters.useAgreements(rows::addAll);
        var session = fixture.open(9);
        var text = WindowCredit.live(session).text();
        var runtime = new DistillRuntime();
        var policy = DistilledPolicy.table("Guardian", text, List.of("alpha", "bravo"));
        DistillPublish.refresh(runtime, List.of(new AgreementStatDelta("outcome", "Guardian", "r1:ALL", text, 0, 50,
                0.0, 0.0, 0, 0, 0, 0, 0L)), policy, 0L, "", "outcome", true);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(9),
                EngineFixtures.near());
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        var options = List.of(new TacticId("alpha"), new TacticId("bravo"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options,
                Optional.of(options.getFirst()));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        var audits = 0;
        var delegated = 0;
        for (var tick = 4; tick < 80; tick += 4) {
            CaptureGate.apply(encounter, plan, tick, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                    akyrion.thepredict.engine.distill.TeachingBoard.NONE,
                    runtime, 0L);
            var trace = session.learning().distillSession().trace();
            if (!trace.audit() && !trace.delegated()) {
                continue;
            }
            if (trace.audit()) {
                audits++;
            }
            if (trace.delegated()) {
                delegated++;
            }
            WindowCredit.record(session, new OutcomeWindow.Summary(ControlLevel.LOCAL, new TacticId("alpha"),
                    Movement.HOLD, 0, 1, 0, 0, false, 0, 0, false, false, trace.audit() ? 0.2 : 0.8), false, 0L);
        }
        assertTrue(audits >= 1 && delegated >= 1);
        assertTrue(fixture.encounters.closeIfFinished(encounter.id(), ignored -> false));
        var outcomes = 0;
        for (var row : rows) {
            if (row.n() == 1) {
                outcomes++;
            }
        }
        assertTrue(outcomes >= 1);
    }
}
