package akyrion.thepredict.domain;

/**
 * Plan a group may adopt (M25). Names are the option keys of {@code g0}. None starts with {@code plan_}.
 */
public enum GroupPlan {
    /** Everyone presses the main target. */
    PRESS_MAIN_TARGET(Family.ATTACK),
    /** Members take surround slots. */
    SURROUND_TARGET(Family.ATTACK),
    /** Members split targets. */
    SPLIT_TARGETS(Family.ATTACK),
    /** The squad screens the leader. */
    SCREEN_THE_LEADER(Family.PROTECT),
    /** Ranged members harass. */
    HARASS_FROM_RANGE(Family.ATTACK),
    /** Two waves fall back toward the leader. */
    FALL_BACK_AND_REGROUP(Family.RETREAT),
    /** Search the last known cell. */
    SEARCH_LAST_KNOWN(Family.SEARCH);

    private final Family family;

    GroupPlan(Family family) {
        this.family = family;
    }

    /**
     * Diversity family. At most one candidate of each family is offered.
     *
     * @return the family
     */
    public Family family() {
        return family;
    }

    /**
     * Lower-case option name.
     *
     * @return the name sent to Jev
     */
    public String option() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Parses an option name.
     *
     * @param token lower-case name
     * @return the plan, or empty when the token is not a plan
     */
    public static java.util.Optional<GroupPlan> parse(String token) {
        for (var plan : values()) {
            if (plan.option().equals(token)) {
                return java.util.Optional.of(plan);
            }
        }
        return java.util.Optional.empty();
    }

    /** Diversity bucket of a plan. */
    public enum Family {
        /** Press, surround, split or harass. */
        ATTACK,
        /** Screen the leader. */
        PROTECT,
        /** Fall back. */
        RETREAT,
        /** Search the last known position. */
        SEARCH
    }
}
