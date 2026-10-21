package akyrion.thepredict.domain;

/**
 * Which questions a group sends (M25). One member always uses {@link #MEMBERS}.
 */
public enum GroupQuestions {
    /** Each member's own question. Bodies stay as they are today. */
    MEMBERS,
    /** Only the group plan. */
    PLAN_ONLY,
    /** The group plan and the leader's tactic. */
    PLAN_AND_LEADER,
    /** Only the leader's tactic. */
    LEADER_ONLY;

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the set
     * @throws IllegalArgumentException when the token is not a set
     */
    public static GroupQuestions parse(String token) {
        return switch (token) {
            case "members" -> MEMBERS;
            case "plan_only" -> PLAN_ONLY;
            case "plan_and_leader" -> PLAN_AND_LEADER;
            case "leader_only" -> LEADER_ONLY;
            default -> throw new IllegalArgumentException("Group.Questions is unknown: " + token);
        };
    }

    /**
     * The set a mode uses when the profile does not name one.
     *
     * @param mode effective mode
     * @param leader whether the encounter has a leader
     * @param members member count
     * @return the set
     */
    public static GroupQuestions delivered(GroupMode mode, boolean leader, int members) {
        if (members < 2 || mode != GroupMode.FULL) {
            return MEMBERS;
        }
        return leader ? PLAN_AND_LEADER : PLAN_ONLY;
    }

    /**
     * Whether this set sends {@code g0}.
     *
     * @return whether a plan question is sent
     */
    public boolean asksPlan() {
        return this == PLAN_ONLY || this == PLAN_AND_LEADER;
    }
}
