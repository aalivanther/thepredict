package akyrion.thepredict.domain;

/**
 * Facts a group may send after the zone facts (M25). At most {@code group.maxFacts}.
 */
public enum GroupFact {
    /** The leader died or left. */
    LEADER_LOST,
    /** An ally is critical, staggered, or facing two token holders. */
    ALLY_IN_TROUBLE,
    /** The other side has more weight. */
    OUTNUMBERED,
    /** The group has more weight. */
    ADVANTAGE,
    /** Nobody sees the target. */
    TARGET_UNSEEN,
    /** More than one participant is in the fight. */
    SEVERAL_TARGETS,
    /** The group lost members. */
    GROUP_THINNED
}
