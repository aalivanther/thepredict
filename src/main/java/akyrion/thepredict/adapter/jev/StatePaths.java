package akyrion.thepredict.adapter.jev;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the facts of one asked mob and of its target live in the {@code state} actually sent, after trimming (M16.3):
 * the paths of a structured instruction are generated from it, never written by hand, and a path that does not exist
 * is left out. Immutable.
 *
 * @param mobIndex index of the mob in {@code mobs}
 * @param hasTarget whether the mob entry has a {@code target}
 * @param participantIndex index of its target in {@code participants}, {@code -1} when it is not there
 * @param participantCues whether that participant entry still has cues
 */
record StatePaths(int mobIndex, boolean hasTarget, int participantIndex, boolean participantCues) {
    /** The path of the author objective of the mob. */
    String objective() {
        return "`mobs[" + mobIndex + "].objective`";
    }

    /** The paths to judge a tactic of the mob by: its facts, its target facts and the facts and cues of its target. */
    List<String> judgeBy() {
        var paths = new ArrayList<String>(4);
        paths.add("`mobs[" + mobIndex + "].facts`");
        if (hasTarget) {
            paths.add("`mobs[" + mobIndex + "].target.facts`");
        }
        if (participantIndex >= 0) {
            paths.add("`participants[" + participantIndex + "].facts`");
            if (participantCues) {
                paths.add("`participants[" + participantIndex + "].cues`");
            }
        }
        return paths;
    }

    /** The paths of a fit check: only the facts of the mob and of its target (M16.5). */
    List<String> fitJudgeBy() {
        var paths = new ArrayList<String>(2);
        paths.add("`mobs[" + mobIndex + "].facts`");
        if (hasTarget) {
            paths.add("`mobs[" + mobIndex + "].target.facts`");
        }
        return paths;
    }
}
