package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.distill.DistillHold;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.OodCause;
import akyrion.thepredict.domain.learn.CaptureDecision;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.VoiArm;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assist audit uses {@code StableRandom} salt 7 (errata E29-1 applied). Seed 1 audits delegated index 1,
 * which is not a multiple of 10.
 */
class DistillAssistTest {
    @Test
    void seedOneAuditsTheFirstDelegatedIndexAndNotTheNext() {
        var audited = DistillStep.consider(request(DistillMode.ASSIST, 0, false, false));
        assertTrue(audited.audit());
        assertFalse(audited.delegate());
        assertTrue(audited.held().isEmpty());
        var kept = DistillStep.consider(request(DistillMode.ASSIST, 1, false, false));
        assertFalse(kept.audit());
        assertTrue(kept.delegate());
        assertEquals(BehaviorPolicy.DISTILLED, kept.held().orElseThrow().behavior());
        assertEquals("press", kept.held().orElseThrow().tactic().value());
    }

    @Test
    void theFirstAssistCaptureAndAGroupQuestionAreNotDelegated() {
        var first = DistillStep.consider(request(DistillMode.ASSIST, 0, true, false));
        assertFalse(first.delegate());
        assertFalse(first.audit());
        var group = DistillStep.consider(request(DistillMode.ASSIST, 1, false, true));
        assertFalse(group.delegate());
        assertTrue(group.held().isEmpty());
        var uncovered = DistillStep.consider(new DistillStep.Request(DistillMode.ASSIST, false,
                Optional.of(OodCause.OOD_CELL_INFERIOR), DistillSource.TABLE, "press", 1L, 1, 10, false, false, 4,
                10L, 30));
        assertEquals(Optional.of(OodCause.OOD_CELL_INFERIOR), uncovered.ood());
        assertFalse(uncovered.delegate());
    }

    @Test
    void autonomousMayDelegateOnTheFirstCaptureAndDoesNotAudit() {
        var held = DistillStep.consider(request(DistillMode.AUTONOMOUS, 0, true, false));
        assertTrue(held.delegate());
        assertFalse(held.audit());
        var audit = new DistillHold(false, true, Optional.empty(), Optional.empty(), Optional.empty());
        var arm = new VoiArm(true, "off", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false,
                new akyrion.thepredict.domain.TacticId("press"), 4, 30L);
        var asked = CaptureDecision.decide(ExploreArm.NONE, arm, 10L, audit);
        assertFalse(asked.dropQuestions());
        assertTrue(asked.held().isEmpty());
        assertTrue(asked.explore().isEmpty());
    }

    private static DistillStep.Request request(DistillMode mode, int delegated, boolean first, boolean group) {
        return new DistillStep.Request(mode, true, Optional.empty(), DistillSource.TABLE, "press", 1L, delegated, 10,
                first, group, 4, 10L, 30);
    }
}
