package akyrion.thepredict.domain;

/**
 * One remembered counter of a profile, at most two (M27.1).
 *
 * @param pattern pattern ordinal
 * @param landed weighted hits
 * @param tries weighted attempts
 */
public record CounterMemory(int pattern, float landed, float tries) {
    /**
     * Checks the ordinal.
     *
     * @throws IllegalArgumentException if the pattern is outside 0 to 24
     */
    public CounterMemory {
        if (pattern < 0 || pattern > 24) {
            throw new IllegalArgumentException("counter pattern " + pattern);
        }
    }
}
