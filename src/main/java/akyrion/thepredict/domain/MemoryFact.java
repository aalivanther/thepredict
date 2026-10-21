package akyrion.thepredict.domain;

/**
 * One fixed history phrase (M27.6). The alias replaces the subject. The phrase carries no number, enum name or UUID.
 */
public enum MemoryFact {
    /** Three or more losses, and the last result was a loss. */
    LOST_MANY,
    /** The last result was a loss. */
    LOST_LAST,
    /** Three or more wins. */
    BEAT_MANY,
    /** At least one win. */
    BEAT_BEFORE;

    /**
     * The English phrase for one participant.
     *
     * @param alias alias text such as {@code player_1}
     * @return the phrase
     */
    public String phrase(String alias) {
        return alias + switch (this) {
            case LOST_MANY -> " has lost to this foe many times";
            case LOST_LAST -> " lost to this foe last time";
            case BEAT_MANY -> " has beaten this foe many times";
            case BEAT_BEFORE -> " has beaten this foe before";
        };
    }
}
