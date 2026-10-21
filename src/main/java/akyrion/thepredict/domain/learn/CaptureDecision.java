package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.HeldIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * What one capture does before a question is sent (M28.5, M28.8). Exploration is decided here. An accepted Jev
 * answer is never replaced. A live value-of-information skip becomes a {@link HeldIntent}.
 */
public final class CaptureDecision {
    private CaptureDecision() {
    }

    /**
     * The capture outcome.
     *
     * @param dropQuestions whether this mob's questions are not sent
     * @param held a skip to hold, or empty when the question still goes out
     * @param explore the exploration choice, or empty when the arm did not run
     * @param wouldSkip whether shadow would skip
     */
    public record Outcome(boolean dropQuestions, Optional<HeldIntent> held, Optional<ExploreChoice> explore,
                          boolean wouldSkip) {
        /**
         * An outcome without an exploration choice.
         *
         * @param dropQuestions whether the questions are dropped
         * @param held a skip to hold
         */
        public Outcome(boolean dropQuestions, Optional<HeldIntent> held) {
            this(dropQuestions, held, Optional.empty(), false);
        }

        /**
         * Requires the optionals.
         */
        public Outcome {
            Objects.requireNonNull(held, "held");
            Objects.requireNonNull(explore, "explore");
        }
    }

    /**
     * Decides exploration and a skip for one armed session.
     *
     * @param explore the explore arm
     * @param voi the skip arm
     * @param tick the server tick of the capture
     * @return the outcome
     */
    public static Outcome decide(ExploreArm explore, VoiArm voi, long tick) {
        return decide(explore, voi, tick, akyrion.thepredict.domain.distill.DistillHold.NONE);
    }

    /**
     * Decides a distilled hold before exploration and the value-of-information skip. {@link
     * akyrion.thepredict.domain.distill.DistillHold#NONE} is the M28 path.
     *
     * @param explore the explore arm
     * @param voi the skip arm
     * @param tick the server tick of the capture
     * @param distill the distilled hold
     * @return the outcome
     */
    public static Outcome decide(ExploreArm explore, VoiArm voi, long tick,
                                 akyrion.thepredict.domain.distill.DistillHold distill) {
        Objects.requireNonNull(explore, "explore");
        Objects.requireNonNull(voi, "voi");
        Objects.requireNonNull(distill, "distill");
        if (distill.delegate() && distill.held().isPresent()) {
            return new Outcome(true, distill.held(), Optional.empty(), false);
        }
        var choice = exploreChoice(explore, tick);
        var drop = choice.isPresent() && choice.get().inStratum() && !explore.acceptedJev();
        if ("off".equals(voi.mode())) {
            return new Outcome(drop, Optional.empty(), choice, false);
        }
        var decision = VoiPolicy.evaluate(voi.pass(), voi.mode(), voi.usable(), voi.gap(), voi.sdBest(),
                voi.sdSecond(), voi.jevSeen(), voi.jevAgree(), voi.minAgreement(), voi.disagreementDelta(),
                voi.maxGain(), voi.consecutiveSkips(), voi.maxSkips(), voi.forbidden());
        if (!"on".equals(voi.mode()) || !decision.skip()) {
            return new Outcome(drop, Optional.empty(), choice, decision.wouldSkip());
        }
        var held = new HeldIntent(voi.tactic(), BehaviorPolicy.VOI_SKIP, voi.captureSignature(), tick,
                tick + voi.holdTicks());
        return new Outcome(true, Optional.of(held), choice, true);
    }

    private static Optional<ExploreChoice> exploreChoice(ExploreArm explore, long tick) {
        if (explore.blocked() && explore.acceptedJev() && explore.epsilon() == 0.0) {
            return Optional.empty();
        }
        return Optional.of(ExplorePolicy.choose(explore.seed(), tick, explore.greedy(), explore.candidates(),
                explore.epsilon(), explore.explored(), explore.adoptions(), explore.maxShare(), explore.blocked(),
                explore.base()));
    }
}
