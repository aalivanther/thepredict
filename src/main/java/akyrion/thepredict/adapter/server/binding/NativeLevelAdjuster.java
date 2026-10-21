package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.NativeLevelMode;
import io.lumine.mythic.core.mobs.ActiveMob;

/**
 * Moves the MythicMobs level of the next spawn by one (M22.12). Only when the global switch is on, the profile asks
 * for {@link NativeLevelMode#SPAWN}, and the slow loop reported that the target time is unreachable. The caller keeps
 * the level inside its own band; one call moves it by one at most. Never during a fight.
 */
public final class NativeLevelAdjuster {
    private NativeLevelAdjuster() {
    }

    /**
     * The level to spawn next.
     *
     * @param level the author's level
     * @param delta sign of the requested step; zero leaves the level
     * @param allowed {@code difficulty.nativeLevelAllowed}
     * @param mode the profile mode
     * @param unreachable whether the last fights were unreachable
     * @return the level, unchanged when the adjuster must not act
     */
    public static int nextLevel(int level, int delta, boolean allowed, NativeLevelMode mode, boolean unreachable) {
        if (!allowed || mode != NativeLevelMode.SPAWN || !unreachable || delta == 0) {
            return level;
        }
        return delta > 0 ? level + 1 : level - 1;
    }

    /**
     * Applies a level to the next spawn. The only {@code setLevel} in the plugin.
     *
     * @param mob the mob instance
     * @param level the level from {@link #nextLevel}
     */
    public static void apply(ActiveMob mob, int level) {
        mob.setLevel(level);
    }
}
