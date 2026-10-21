package akyrion.thepredict.domain;

/**
 * What kind of living entity a participant is (M17.1): a player, or any other living entity the participant policy
 * admits. Each kind has its own alias prefix and counter in an encounter (rule I6).
 */
public enum ParticipantKind {
    /** A player, whose alias is {@code player_N}. */
    PLAYER,
    /** Any other living entity, whose alias is {@code entity_N} and whose species travels to Jev. */
    ENTITY
}
