package akyrion.thepredict.domain;

/**
 * One group assignment per shared threat table (M25). A mob without a table, and a child that inherits the
 * summoner's table, is not reassigned.
 */
public final class SharedTableAssignment {
    private SharedTableAssignment() {
    }

    /**
     * Writer of each member. {@code -1} when this member must not receive {@code GROUP_ASSIGNMENT}.
     *
     * @param table identity of the threat table, {@code 0} when the mob has none
     * @param inherits whether this mob is a child on the summoner's table
     * @return the writer index, or -1
     */
    public static int[] writers(int[] table, boolean[] inherits) {
        var result = new int[table.length];
        for (var index = 0; index < result.length; index++) {
            result[index] = -1;
        }
        var seen = new int[table.length];
        var seenCount = 0;
        for (var index = 0; index < table.length; index++) {
            if (table[index] == 0 || inherits[index]) {
                continue;
            }
            var already = false;
            for (var prior = 0; prior < seenCount; prior++) {
                if (seen[prior] == table[index]) {
                    already = true;
                    break;
                }
            }
            if (already) {
                continue;
            }
            seen[seenCount++] = table[index];
            result[index] = index;
        }
        return result;
    }

    /**
     * How many members of this pass are not reassigned.
     *
     * @param table identity of the threat table, {@code 0} when the mob has none
     * @param inherits whether this mob is a child on the summoner's table
     * @return the count
     */
    public static int unavailable(int[] table, boolean[] inherits) {
        var writers = writers(table, inherits);
        var count = 0;
        for (var writer : writers) {
            if (writer < 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Magnitude clipped by the window cap. The raw value is how far the target sits under the table leader, plus
     * the assignment margin.
     *
     * @param top threat of the table's current leader
     * @param onTarget threat of the assigned participant
     * @param margin {@code group.assignmentThreatMargin}
     * @param maxPerWindow cap of M11
     * @return the clipped magnitude
     */
    public static double magnitude(double top, double onTarget, int margin, double maxPerWindow) {
        var raw = Math.max(0.0, top - onTarget) + Math.max(0, margin);
        return Math.min(raw, Math.max(0.0, maxPerWindow));
    }
}
