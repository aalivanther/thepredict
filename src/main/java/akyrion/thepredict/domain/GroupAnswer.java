package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * The plan Jev chose for {@code g0}, the membership epoch of the capture that asked and the confidence of the
 * answer (M25, M25.10). The acceptor compares the epoch with the live one and the confidence with the leader's
 * {@code minimumConfidence}: below it the group keeps its local plan, candidate 0.
 *
 * @param plan the chosen plan
 * @param membershipEpoch epoch of the capture, at least 1
 * @param confidence confidence of the answer, from 0 to 1
 */
public record GroupAnswer(GroupPlan plan, long membershipEpoch, double confidence) {
    /**
     * Checks the answer.
     *
     * @throws IllegalArgumentException when the epoch is below 1 or the confidence leaves {@code [0, 1]}
     */
    public GroupAnswer {
        Objects.requireNonNull(plan, "plan");
        if (membershipEpoch < 1) {
            throw new IllegalArgumentException("membershipEpoch must be >= 1, was " + membershipEpoch);
        }
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("confidence must be within [0, 1], was " + confidence);
        }
    }

    /**
     * An answer whose confidence was not parsed: full confidence, as before M25.10 (rule W5).
     *
     * @param plan the chosen plan
     * @param membershipEpoch epoch of the capture, at least 1
     */
    public GroupAnswer(GroupPlan plan, long membershipEpoch) {
        this(plan, membershipEpoch, 1.0);
    }
}
