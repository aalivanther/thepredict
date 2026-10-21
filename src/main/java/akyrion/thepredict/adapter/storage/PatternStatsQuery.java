package akyrion.thepredict.adapter.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters loaded pattern rows off the tick (M26.9).
 */
public final class PatternStatsQuery {
    private PatternStatsQuery() {
    }

    /**
     * Rows of one profile.
     *
     * @param rows the load
     * @param profile the profile
     * @return the matches
     */
    public static List<PatternStatsLoaded> ofProfile(List<PatternStatsLoaded> rows, String profile) {
        var matches = new ArrayList<PatternStatsLoaded>();
        for (var row : rows) {
            if (row.profile().equals(profile)) {
                matches.add(row);
            }
        }
        return matches;
    }
}
