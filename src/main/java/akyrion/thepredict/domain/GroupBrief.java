package akyrion.thepredict.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a capture may attach as {@code g0} and which member questions go with it (M25.10). {@link #NONE} leaves the
 * request body unchanged.
 *
 * @param questions the question set already resolved
 * @param candidates plan option names, candidate 0 first
 * @param membershipEpoch epoch stamped on the request
 * @param leaderAlive whether the encounter has a leader
 * @param leader the entity of the leader, empty when it is not known
 */
public record GroupBrief(GroupQuestions questions, List<String> candidates, long membershipEpoch, boolean leaderAlive,
                         Optional<UUID> leader) {
    /** A capture that does not ask a group question. */
    public static final GroupBrief NONE = new GroupBrief(GroupQuestions.MEMBERS, List.of(), 1L, false);

    /**
     * Copies the candidates.
     *
     * @throws IllegalArgumentException when the epoch is below 1
     */
    public GroupBrief {
        Objects.requireNonNull(questions, "questions");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(leader, "leader");
        if (membershipEpoch < 1) {
            throw new IllegalArgumentException("membershipEpoch must be >= 1, was " + membershipEpoch);
        }
    }

    /**
     * A brief whose leader is not known (rule W5): every member question stays, as before the question sets.
     *
     * @param questions the question set already resolved
     * @param candidates plan option names, candidate 0 first
     * @param membershipEpoch epoch stamped on the request
     * @param leaderAlive whether the encounter has a leader
     */
    public GroupBrief(GroupQuestions questions, List<String> candidates, long membershipEpoch, boolean leaderAlive) {
        this(questions, candidates, membershipEpoch, leaderAlive, Optional.empty());
    }

    /**
     * Whether the encoder should add {@code g0}. Fair and coordinate never do.
     *
     * @return whether a plan question has at least two candidates
     */
    public boolean asksPlan() {
        return questions.asksPlan() && candidates.size() >= 2;
    }

    /**
     * Whether the request asks this member its own questions (M25.10, DOC-02): every member with {@code members};
     * only the leader with {@code plan_and_leader} and {@code leader_only}; nobody with {@code plan_only}. A member
     * that is not asked plays the local plan, and its line stays in the state. Without a known leader every member
     * is asked, except with {@code plan_only}.
     *
     * @param mob the entity of the member
     * @return whether its questions go in the request
     */
    public boolean asksMember(UUID mob) {
        Objects.requireNonNull(mob, "mob");
        return switch (questions) {
            case MEMBERS -> true;
            case PLAN_ONLY -> false;
            case PLAN_AND_LEADER, LEADER_ONLY -> leader.isEmpty() || leader.get().equals(mob);
        };
    }
}
