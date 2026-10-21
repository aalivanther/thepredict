package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.AuditSampler;
import akyrion.thepredict.domain.distill.DistillHold;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.OodCause;

import java.util.Optional;

/**
 * Distilled capture, before the value-of-information policy and the budget (M29.4). It does not wait and it
 * does not call a decision client. {@code off} returns {@link DistillHold#NONE}.
 */
public final class DistillStep {
    private DistillStep() {
    }

    /**
     * Inputs of one mob at a capture.
     *
     * @param mode effective mode
     * @param covered whether the fine cell is covered
     * @param ood the cause when not covered
     * @param source the student's source
     * @param tactic the student's tactic
     * @param seed session seed
     * @param delegatedIndex delegated decisions already counted, the next one is this plus one
     * @param auditEvery audit period
     * @param firstCapture whether this session has not captured yet
     * @param groupQuestion whether the question is {@code g0} or a contingency
     * @param signature live signature
     * @param tick server tick
     * @param holdTicks maximum intent ticks
     */
    public record Request(DistillMode mode, boolean covered, Optional<OodCause> ood, DistillSource source,
                          String tactic, long seed, int delegatedIndex, int auditEvery, boolean firstCapture,
                          boolean groupQuestion, int signature, long tick, int holdTicks) {
    }

    /**
     * Decides whether the student holds the intent.
     *
     * @param request the capture
     * @return the hold
     */
    public static DistillHold consider(Request request) {
        if (request.mode() == DistillMode.OFF || request.mode() == DistillMode.SHADOW) {
            return shadow(request);
        }
        if (request.groupQuestion() || !request.covered()) {
            return new DistillHold(false, false, Optional.empty(), Optional.empty(), request.ood());
        }
        if (request.mode() == DistillMode.ASSIST && request.firstCapture()) {
            return DistillHold.NONE;
        }
        var index = request.delegatedIndex() + 1;
        if (request.mode() == DistillMode.ASSIST && AuditSampler.audit(request.seed(), index, request.auditEvery())) {
            return new DistillHold(false, true, Optional.empty(), Optional.empty(), Optional.empty());
        }
        var cause = request.source() == DistillSource.VALUE_OVERRIDE ? "DISTILLED_VALUE_OVERRIDE" : "DISTILLED_CELL";
        return DistillHold.delegated(request.tactic(), request.signature(), request.tick(), request.holdTicks(), cause);
    }

    private static DistillHold shadow(Request request) {
        if (request.mode() != DistillMode.SHADOW) {
            return DistillHold.NONE;
        }
        return new DistillHold(false, false, Optional.empty(), Optional.empty(), request.ood());
    }
}
