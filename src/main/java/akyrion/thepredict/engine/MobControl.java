package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.List;

/**
 * Port through which {@code engine} owns the movement AI of a bound mob (00 §4, M05). Every method runs on
 * the server thread only (rule T1); implementations reject other threads. Changing a tactic never calls this
 * port: only the published {@code IntentView} changes.
 */
public interface MobControl {
    /**
     * Installs the goal set generated from the profile unless the mob already carries the set of the current
     * catalog revision and of this session generation. Never throws for a live-game failure.
     *
     * @param key session identity
     * @param profile tactical profile of the mob
     * @return {@code Installed} with the goal count; {@code Deferred} only for a transient cause (the mob is
     *         not loaded or not valid), to retry later while the mob keeps the MythicMobs AI; or
     *         {@code Unsupported} for a species with a {@code Brain}, a mob that is not a {@code Creature}, a
     *         set that MythicMobs installed incompletely or an unexpected failure. After {@code Unsupported}
     *         the mob has its MythicMobs AI back and a repeated call with the same revision and generation
     *         changes nothing; a new generation or catalog revision tries again
     */
    InstallResult ensureInstalled(MobKey key, TacticalProfile profile);

    /**
     * Reads how the controlled movement is going without computing new paths (M05.5).
     *
     * @param key session identity
     * @return progress; all false and no distance when the mob is unavailable
     */
    MovementProgress progress(MobKey key);

    /**
     * Describes the goals of the mob for inspection, bounded in size.
     *
     * @param key session identity
     * @return goal summaries, empty when the mob is unavailable
     */
    List<GoalSummary> describeGoals(MobKey key);

    /**
     * Gives the movement AI back to MythicMobs: removes ThePredict's own goals and reapplies the mob's
     * {@code AIGoalSelectors}. The caller clears the intent view first. Does nothing when the mob is gone.
     *
     * @param key session identity
     */
    void release(MobKey key);
}
