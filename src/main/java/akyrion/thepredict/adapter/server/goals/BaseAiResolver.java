package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.TacticalProfile;
import io.lumine.mythic.api.mobs.MythicMob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chooses what the mob does when ThePredict does not control it: fallback level 4 (M05.6). Stateless and
 * pure; it never reads the server.
 *
 * <p>Order: the profile's {@code BaseAI}, else the mob's {@code AIGoalSelectors}, else {@link #GENERIC}. A
 * {@code clear} line is dropped from every source, because at priority 10 or more it would wipe the tactical
 * goals; a source left with no line counts as absent. The returned lines carry the priority MythicMobs would
 * give them, written out.
 */
public final class BaseAiResolver {
    /** Base AI of a mob with neither {@code BaseAI} nor {@code AIGoalSelectors}: chase and attack, then wander. */
    public static final List<String> GENERIC = List.of("0 meleeAttack", "1 randomStroll");

    /** Creates the resolver; it holds no state. */
    public BaseAiResolver() {
    }

    /**
     * Resolves the base AI of a mob type.
     *
     * @param profile tactical profile of the mob
     * @param type MythicMobs mob type, read for {@code AIGoalSelectors}
     * @return lines and origin
     */
    public BaseAiSource resolve(TacticalProfile profile, MythicMob type) {
        var selectors = Objects.requireNonNull(type, "type").getAIGoalSelectors();
        return resolve(profile, selectors == null ? List.of() : selectors);
    }

    /**
     * Resolves the base AI from the profile and the mob's goal selector lines.
     *
     * @param profile tactical profile of the mob
     * @param mobGoalSelectors the mob's {@code AIGoalSelectors} lines, possibly empty
     * @return lines and origin
     */
    public BaseAiSource resolve(TacticalProfile profile, List<String> mobGoalSelectors) {
        var fromProfile = usable(Objects.requireNonNull(profile, "profile").baseAi());
        if (!fromProfile.isEmpty()) {
            return new BaseAiSource(fromProfile, BaseAiOrigin.PROFILE);
        }
        var fromMob = usable(mobGoalSelectors);
        if (!fromMob.isEmpty()) {
            return new BaseAiSource(fromMob, BaseAiOrigin.MOB_CONFIG);
        }
        return new BaseAiSource(GENERIC, BaseAiOrigin.GENERIC);
    }

    /**
     * Keeps the lines MythicMobs can read, without {@code clear}, each with its native priority written out,
     * so dropping a line never changes the position-based priority of the others.
     */
    private static List<String> usable(List<String> lines) {
        var kept = new ArrayList<String>(lines.size());
        for (var line : BaseAiLines.parse(lines)) {
            if (!BaseAiLines.isClear(line.goal())) {
                kept.add(line.priority() + " " + line.goal());
            }
        }
        return kept;
    }
}
